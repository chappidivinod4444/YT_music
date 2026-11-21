@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.vidflow.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import app.vidflow.core.HttpDownloader
import okhttp3.OkHttpClient
import okhttp3.Response
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.IOException
import java.io.InputStream

/**
 * Reads a video file in small byte ranges, one HTTP request per range.
 * The video servers slow down or cut long single downloads, so asking for
 * pieces keeps playback smooth. To the player it looks like one continuous stream.
 */
class ChunkedHttpDataSource(private val client: OkHttpClient) : BaseDataSource(true) {

    class Factory(private val client: OkHttpClient) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedHttpDataSource(client)
    }

    private var spec: DataSpec? = null
    private var response: Response? = null
    private var body: InputStream? = null

    /** Absolute position of the next byte to return. */
    private var position = 0L

    /** Absolute end (exclusive) of what the player asked for, or -1 if unknown. */
    private var end = -1L
    private var chunkLeft = 0L
    private var serverIgnoresRanges = false
    private var requestNumber = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        transferInitializing(dataSpec)
        position = dataSpec.position
        end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.position + dataSpec.length else -1L
        serverIgnoresRanges = false
        requestNumber = 0
        val total = openChunk()
        if (end < 0 && total > 0) end = total
        opened = true
        transferStarted(dataSpec)
        return if (end > 0) end - dataSpec.position else C.LENGTH_UNSET.toLong()
    }

    /** Opens the next range request. Returns the full file size when the server says it. */
    private fun openChunk(): Long {
        closeResponse()
        val s = spec ?: throw IOException("Not opened")
        val url = s.uri.toString()
        val last = if (end > 0) minOf(position + CHUNK, end) - 1 else position + CHUNK - 1
        val isVideoServer = url.contains("/videoplayback")
        val requestUrl = if (isVideoServer) url + (if (url.contains('?')) "&" else "?") + "rn=" + (++requestNumber) else url

        val builder = okhttp3.Request.Builder().url(requestUrl).header("User-Agent", userAgentFor(url))
        if (!serverIgnoresRanges) builder.header("Range", "bytes=$position-$last")
        for ((k, v) in s.httpRequestHeaders) builder.header(k, v)

        val r = client.newCall(builder.build()).execute()
        if (r.code !in 200..299) {
            val code = r.code
            r.close()
            throw IOException("Video server answered HTTP $code")
        }
        response = r
        val stream = r.body?.byteStream() ?: throw IOException("Empty response")
        body = stream

        var total = -1L
        val contentRange = r.header("Content-Range")
        if (r.code == 206 && contentRange != null) {
            // "bytes start-end/total"
            val m = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""").find(contentRange)
            if (m != null) {
                val endIncl = m.groupValues[2].toLong()
                chunkLeft = endIncl - position + 1
                total = m.groupValues[3].toLongOrNull() ?: -1L
            } else {
                chunkLeft = r.body?.contentLength() ?: -1L
            }
        } else {
            // The server sent the whole file: skip to where we want to be.
            serverIgnoresRanges = true
            val length = r.body?.contentLength() ?: -1L
            total = length
            var toSkip = position
            while (toSkip > 0) {
                val skipped = stream.skip(toSkip)
                if (skipped <= 0) {
                    if (stream.read() < 0) throw IOException("Unexpected end of stream")
                    toSkip--
                } else {
                    toSkip -= skipped
                }
            }
            chunkLeft = if (length > 0) length - position else Long.MAX_VALUE
        }
        return total
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (end > 0 && position >= end) return C.RESULT_END_OF_INPUT
        if (chunkLeft <= 0) {
            if (end < 0 && serverIgnoresRanges) return C.RESULT_END_OF_INPUT
            openChunk()
        }
        val want = if (chunkLeft == Long.MAX_VALUE || chunkLeft < 0) length else minOf(length.toLong(), chunkLeft).toInt()
        val n = body?.read(buffer, offset, want) ?: -1
        if (n < 0) {
            // This piece ended early: ask for the rest if there is more.
            if (end > 0 && position < end && !serverIgnoresRanges) {
                chunkLeft = 0
                return read(buffer, offset, length)
            }
            return C.RESULT_END_OF_INPUT
        }
        position += n
        if (chunkLeft != Long.MAX_VALUE) chunkLeft -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = spec?.uri

    override fun close() {
        closeResponse()
        if (opened) {
            opened = false
            transferEnded()
        }
        spec = null
    }

    private fun closeResponse() {
        try {
            body?.close()
        } catch (_: Throwable) {
        }
        try {
            response?.close()
        } catch (_: Throwable) {
        }
        body = null
        response = null
    }

    companion object {
        private const val CHUNK = 2L * 1024 * 1024

        fun userAgentFor(url: String): String = try {
            if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) YoutubeParsingHelper.getVisionOsUserAgent(null)
            else HttpDownloader.USER_AGENT
        } catch (_: Throwable) {
            HttpDownloader.USER_AGENT
        }
    }
}
