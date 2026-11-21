package app.vidflow.core

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.TimeUnit

data class LocalPlaylist(val id: String, val name: String, val videos: List<Video>)

/** Liked videos and your own playlists, kept on this phone. */
object Saved {
    private lateinit var file: File
    val liked = MutableStateFlow<List<Video>>(emptyList())
    val playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())

    fun init(context: Context) {
        file = File(context.filesDir, "saved.json")
        try {
            val o = JSONObject(file.readText())
            liked.value = o.optJSONArray("liked").videos()
            val arr = o.optJSONArray("playlists") ?: JSONArray()
            playlists.value = (0 until arr.length()).map { i ->
                val p = arr.getJSONObject(i)
                LocalPlaylist(p.getString("id"), p.getString("name"), p.optJSONArray("videos").videos())
            }
        } catch (_: Throwable) {
        }
        // Built-in lists, like YouTube's.
        val have = playlists.value.map { it.id }.toSet()
        val defaults = listOf(LocalPlaylist(WATCH_LATER, "Watch later", emptyList()), LocalPlaylist(SAVED_SHORTS, "Saved Shorts", emptyList()))
            .filter { it.id !in have }
        if (defaults.isNotEmpty()) {
            playlists.value = defaults + playlists.value
            persist()
        }
    }

    const val WATCH_LATER = "watch-later"
    const val SAVED_SHORTS = "saved-shorts"

    fun isSaved(url: String) = playlists.value.any { p -> p.videos.any { it.url == url } }

    private fun JSONArray?.videos(): List<Video> = if (this == null) emptyList()
    else (0 until length()).mapNotNull { runCatching { Library.videoFrom(getJSONObject(it)) }.getOrNull() }

    private fun persist() {
        val o = JSONObject()
        o.put("liked", JSONArray().apply { liked.value.forEach { put(Library.videoJson(it)) } })
        o.put("playlists", JSONArray().apply {
            playlists.value.forEach { p ->
                put(JSONObject().put("id", p.id).put("name", p.name)
                    .put("videos", JSONArray().apply { p.videos.forEach { put(Library.videoJson(it)) } }))
            }
        })
        val text = o.toString()
        Thread { runCatching { file.writeText(text) } }.start()
    }

    fun isLiked(url: String) = liked.value.any { it.url == url }

    fun toggleLike(v: Video) {
        liked.value = if (isLiked(v.url)) liked.value.filterNot { it.url == v.url } else listOf(v) + liked.value
        persist()
    }

    fun createPlaylist(name: String): LocalPlaylist {
        val p = LocalPlaylist(UUID.randomUUID().toString(), name.trim().ifEmpty { "My playlist" }, emptyList())
        playlists.value = playlists.value + p
        persist()
        return p
    }

    fun addTo(id: String, v: Video) {
        playlists.value = playlists.value.map { p ->
            // Newest first, like YouTube.
            if (p.id == id && p.videos.none { it.url == v.url }) p.copy(videos = listOf(v) + p.videos) else p
        }
        persist()
    }

    fun removeFrom(id: String, url: String) {
        playlists.value = playlists.value.map { p -> if (p.id == id) p.copy(videos = p.videos.filterNot { it.url == url }) else p }
        persist()
    }

    fun deletePlaylist(id: String) {
        playlists.value = playlists.value.filterNot { it.id == id }
        persist()
    }
}

/** Saves videos to the phone's Downloads/Vidflow folder. */
object Downloads {
    data class Job(val id: String, val title: String, val progress: Float, val status: String, val done: Boolean = false, val failed: Boolean = false)

    val jobs = MutableStateFlow<List<Job>>(emptyList())
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    private fun update(id: String, change: (Job) -> Job) {
        jobs.value = jobs.value.map { if (it.id == id) change(it) else it }
    }

    /**
     * Downloads and saves. Blocking: runs inside the download service.
     * [height] 0 means sound only (M4A).
     */
    fun run(context: Context, video: Video, height: Int, id: String) {
        val title = video.title.ifBlank { "video" }
        jobs.value = jobs.value + Job(id, title, 0f, "Starting…")
        val tmp = File(context.cacheDir, "dl-$id").apply { mkdirs() }
        try {
            val info = Yt.stream(video.url)
            val audio = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.M4A }
                .maxByOrNull { it.averageBitrate } ?: throw IllegalStateException("No downloadable sound for this video")
            val safe = title.replace(Regex("""[\\/:*?"<>|]"""), "_").take(80)
            if (height == 0) {
                val a = File(tmp, "a.m4a")
                fetch(audio.content, a) { p -> update(id) { it.copy(progress = p, status = "Downloading sound") } }
                save(context, a, "$safe.m4a", "audio/mp4", music = true)
            } else {
                val videos = info.videoOnlyStreams.filter {
                    it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.MPEG_4
                }
                fun h(v: org.schabi.newpipe.extractor.stream.VideoStream) = v.resolution.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: v.height
                val chosen = videos.filter { h(it) <= height }.maxByOrNull { h(it) } ?: videos.minByOrNull { h(it) }
                    ?: throw IllegalStateException("No downloadable video for this one")
                val v = File(tmp, "v.mp4")
                val a = File(tmp, "a.m4a")
                fetch(chosen.content, v) { p -> update(id) { it.copy(progress = p * 0.8f, status = "Downloading video") } }
                fetch(audio.content, a) { p -> update(id) { it.copy(progress = 0.8f + p * 0.15f, status = "Downloading sound") } }
                update(id) { it.copy(progress = 0.96f, status = "Saving") }
                val out = File(tmp, "out.mp4")
                merge(v, a, out)
                save(context, out, "$safe (${h(chosen)}p).mp4", "video/mp4", music = false)
            }
            update(id) { it.copy(progress = 1f, status = "Saved to Downloads/Vidflow", done = true) }
        } catch (t: Throwable) {
            update(id) { it.copy(status = friendlyError(t), failed = true) }
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** Downloads in 4 MB pieces (the servers slow down long single requests). */
    private fun fetch(url: String, to: File, progress: (Float) -> Unit) {
        val ua = app.vidflow.player.ChunkedHttpDataSource.userAgentFor(url)
        var pos = 0L
        var total = -1L
        val chunk = 4L * 1024 * 1024
        var n = 0
        to.outputStream().buffered().use { out ->
            while (total < 0 || pos < total) {
                val req = okhttp3.Request.Builder().url("$url&rn=${++n}").header("User-Agent", ua)
                    .header("Range", "bytes=$pos-${pos + chunk - 1}").build()
                client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw java.io.IOException("Server answered HTTP ${r.code}")
                    val cr = r.header("Content-Range")
                    if (total < 0) total = cr?.substringAfter('/')?.toLongOrNull() ?: (r.body?.contentLength() ?: -1)
                    val bytes = r.body?.bytes() ?: ByteArray(0)
                    if (bytes.isEmpty()) return
                    out.write(bytes)
                    pos += bytes.size
                    if (r.code == 200) total = pos // the server sent everything at once
                }
                if (total > 0) progress((pos.toFloat() / total).coerceIn(0f, 1f))
            }
        }
    }

    /** Puts the video and sound tracks together into one MP4 file. */
    private fun merge(video: File, audio: File, out: File) {
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val sources = listOf(video, audio).map { f ->
            MediaExtractor().apply { setDataSource(f.absolutePath) }
        }
        try {
            val tracks = sources.map { ex ->
                val i = (0 until ex.trackCount).first { t ->
                    val mime = ex.getTrackFormat(t).getString(android.media.MediaFormat.KEY_MIME) ?: ""
                    mime.startsWith("video/") || mime.startsWith("audio/")
                }
                ex.selectTrack(i)
                muxer.addTrack(ex.getTrackFormat(i))
            }
            muxer.start()
            val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            sources.forEachIndexed { k, ex ->
                while (true) {
                    val size = ex.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.set(0, size, ex.sampleTime, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(tracks[k], buffer, info)
                    ex.advance()
                }
            }
            muxer.stop()
        } finally {
            sources.forEach { runCatching { it.release() } }
            runCatching { muxer.release() }
        }
    }

    private fun save(context: Context, file: File, name: String, mime: String, music: Boolean) {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Vidflow")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw java.io.IOException("Couldn't create the file")
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            val dir = File(context.getExternalFilesDir(if (music) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES), "Vidflow")
            dir.mkdirs()
            file.copyTo(File(dir, name), overwrite = true)
        }
    }
}
