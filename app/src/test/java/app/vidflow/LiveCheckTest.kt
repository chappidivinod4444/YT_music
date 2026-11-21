package app.vidflow

import app.vidflow.core.HttpDownloader
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo

class LiveCheckTest {
    @Test
    fun probe() {
        val dl = HttpDownloader()
        NewPipe.init(dl)
        val yt = ServiceList.YouTube
        try {
            val s = SearchInfo.getInfo(yt, yt.searchQHFactory.fromQuery("lofi music"))
            println("SEARCH ok items=${s.relatedItems.size} first=${s.relatedItems.firstOrNull()?.name}")
        } catch (t: Throwable) { println("SEARCH fail $t") }
        try {
            val k = yt.kioskList.defaultKioskExtractor
            k.fetchPage()
            println("KIOSK ok id=${k.id} items=${k.initialPage.items.size}")
        } catch (t: Throwable) { println("KIOSK fail $t") }
        for (vid in listOf("jNQXAC9IVRw", "dQw4w9WgXcQ")) {
            try {
                val info = StreamInfo.getInfo(yt, "https://www.youtube.com/watch?v=$vid")
                println("INFO $vid ok title=${info.name} dur=${info.duration} related=${info.relatedItems.size} dash=${info.dashMpdUrl.take(40)} hls=${info.hlsUrl.take(40)}")
                for (a in info.audioStreams.take(3)) println("  AUDIO itag=${a.itag} fmt=${a.format} dm=${a.deliveryMethod} br=${a.averageBitrate} url=${a.content.take(160)}")
                for (v in info.videoOnlyStreams.take(4)) println("  VONLY itag=${v.itag} res=${v.resolution} dm=${v.deliveryMethod} url=${v.content.take(160)}")
                for (v in info.videoStreams.take(2)) println("  MUXED itag=${v.itag} res=${v.resolution} dm=${v.deliveryMethod}")
                val a = info.audioStreams.firstOrNull() ?: continue
                val url = a.content
                val client = dl.client
                fun tryReq(label: String, u: String, post: Boolean, rangeHeader: Boolean, ua: String) {
                    try {
                        val b = okhttp3.Request.Builder().url(u).header("User-Agent", ua)
                        if (rangeHeader) b.header("Range", "bytes=0-1023")
                        if (post) b.post(byteArrayOf(0x78, 0).toRequestBody()) else b.get()
                        client.newCall(b.build()).execute().use { r -> println("  FETCH $label -> ${r.code} len=${r.body?.bytes()?.size}") }
                    } catch (t: Throwable) { println("  FETCH $label -> $t") }
                }
                tryReq("GET Range header desktopUA", url, false, true, HttpDownloader.USER_AGENT)
                tryReq("GET range param", "$url&range=0-1023&rn=1", false, false, HttpDownloader.USER_AGENT)
                tryReq("POST range param", "$url&range=0-1023&rn=1", true, false, HttpDownloader.USER_AGENT)
            } catch (t: Throwable) {
                println("INFO $vid fail $t")
                t.cause?.let { println("  cause $it") }
            }
        }
    }
}
