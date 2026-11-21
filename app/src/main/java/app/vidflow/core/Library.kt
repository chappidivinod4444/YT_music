package app.vidflow.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Subscription(val url: String, val name: String, val avatar: String?)

data class Watched(val video: Video, val at: Long, val positionMs: Long)

/** Subscriptions and watch history, stored only on this phone. */
object Library {
    private lateinit var dir: File
    val subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val history = MutableStateFlow<List<Watched>>(emptyList())

    fun init(context: Context) {
        dir = context.filesDir
        subscriptions.value = load("subs.json") { o -> Subscription(o.getString("url"), o.getString("name"), o.optString("avatar").ifEmpty { null }) }
        history.value = load("history.json") { o -> Watched(videoFrom(o.getJSONObject("v")), o.getLong("at"), o.optLong("pos")) }
    }

    private fun <T> load(name: String, parse: (JSONObject) -> T): List<T> = try {
        val arr = JSONArray(File(dir, name).readText())
        (0 until arr.length()).mapNotNull { runCatching { parse(arr.getJSONObject(it)) }.getOrNull() }
    } catch (_: Throwable) {
        emptyList()
    }

    private fun save(name: String, arr: JSONArray) {
        val text = arr.toString()
        Thread {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(dir, name))
        }.start()
    }

    // ---- subscriptions

    fun isSubscribed(url: String?): Boolean = url != null && subscriptions.value.any { same(it.url, url) }

    fun toggle(url: String, name: String, avatar: String?) {
        subscriptions.value = if (isSubscribed(url)) subscriptions.value.filterNot { same(it.url, url) }
        else listOf(Subscription(url, name, avatar)) + subscriptions.value
        save("subs.json", JSONArray().apply {
            subscriptions.value.forEach { put(JSONObject().put("url", it.url).put("name", it.name).put("avatar", it.avatar ?: "")) }
        })
        Feed.invalidate()
    }

    private fun same(a: String, b: String) = a.trimEnd('/').substringAfterLast('/') == b.trimEnd('/').substringAfterLast('/')

    // ---- history

    fun watched(v: Video, positionMs: Long = 0) {
        val list = history.value.filterNot { it.video.url == v.url }
        history.value = (listOf(Watched(v, System.currentTimeMillis(), positionMs)) + list).take(300)
        saveHistory()
    }

    fun savePosition(url: String, positionMs: Long) {
        val list = history.value
        val i = list.indexOfFirst { it.video.url == url }
        if (i < 0) return
        history.value = list.toMutableList().also { it[i] = it[i].copy(positionMs = positionMs) }
        saveHistory()
    }

    fun position(url: String): Long = history.value.firstOrNull { it.video.url == url }?.positionMs ?: 0

    fun clearHistory() {
        history.value = emptyList()
        saveHistory()
    }

    fun removeFromHistory(url: String) {
        history.value = history.value.filterNot { it.video.url == url }
        saveHistory()
    }

    private fun saveHistory() {
        save("history.json", JSONArray().apply {
            history.value.forEach { put(JSONObject().put("v", videoJson(it.video)).put("at", it.at).put("pos", it.positionMs)) }
        })
    }

    fun videoJson(v: Video): JSONObject = JSONObject()
        .put("url", v.url).put("title", v.title).put("thumb", v.thumbnail ?: "")
        .put("channel", v.channel).put("channelUrl", v.channelUrl ?: "").put("avatar", v.channelAvatar ?: "")
        .put("dur", v.durationSec).put("views", v.views).put("uploaded", v.uploaded ?: "").put("live", v.live)

    fun videoFrom(o: JSONObject) = Video(
        url = o.getString("url"), title = o.getString("title"), thumbnail = o.optString("thumb").ifEmpty { null },
        channel = o.optString("channel"), channelUrl = o.optString("channelUrl").ifEmpty { null },
        channelAvatar = o.optString("avatar").ifEmpty { null }, durationSec = o.optLong("dur"),
        views = o.optLong("views", -1), uploaded = o.optString("uploaded").ifEmpty { null },
        uploadedAtMillis = null, live = o.optBoolean("live"), short = false,
    )
}

/** Latest videos from subscribed channels. */
object Feed {
    val videos = MutableStateFlow<List<Video>?>(null)
    val loading = MutableStateFlow(false)
    private var loadedAt = 0L

    fun invalidate() {
        loadedAt = 0
    }

    fun stale(): Boolean = System.currentTimeMillis() - loadedAt > 30 * 60 * 1000

    /** Blocking; call from Dispatchers.IO. */
    fun refresh() {
        val subs = Library.subscriptions.value.take(60)
        if (subs.isEmpty()) {
            videos.value = emptyList()
            return
        }
        loading.value = true
        try {
            val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
            val futures = subs.map { s -> pool.submit<List<Video>> { Yt.latestUploads(s.url) } }
            val all = futures.flatMap { runCatching { it.get() }.getOrDefault(emptyList()) }
            pool.shutdown()
            videos.value = all.distinctBy { it.url }.sortedByDescending { it.uploadedAtMillis ?: 0L }
            loadedAt = System.currentTimeMillis()
        } finally {
            loading.value = false
        }
    }
}

/** Home feed saved from last time, so Home shows instantly when the app opens. */
object HomeCache {
    private lateinit var file: File

    fun init(context: Context) {
        file = File(context.filesDir, "home.json")
    }

    fun load(): List<Video> = try {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { runCatching { Library.videoFrom(arr.getJSONObject(it)) }.getOrNull() }
    } catch (_: Throwable) {
        emptyList()
    }

    fun save(items: List<Item>) {
        val arr = JSONArray()
        items.filterIsInstance<Video>().take(30).forEach { arr.put(Library.videoJson(it)) }
        val text = arr.toString()
        Thread { runCatching { file.writeText(text) } }.start()
    }
}

/** The topic chips on Home. You can add and remove your own. */
object Topics {
    private lateinit var app: Context
    val list = MutableStateFlow<List<String>>(emptyList())
    private val defaults = listOf("Music", "Mixes", "Telugu cinema", "Gaming", "News", "Live", "Comedy", "Cricket", "Podcasts")

    fun init(context: Context) {
        app = context.applicationContext
        val saved = app.getSharedPreferences("topics", Context.MODE_PRIVATE).getString("list", null)
        list.value = saved?.split('\n')?.filter { it.isNotBlank() } ?: defaults
    }

    fun set(items: List<String>) {
        list.value = items.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        app.getSharedPreferences("topics", Context.MODE_PRIVATE).edit().putString("list", list.value.joinToString("\n")).apply()
    }
}

/** Remembers when you last opened the notifications list, to show a dot for new uploads. */
object Notifications {
    private lateinit var app: Context
    val lastSeen = MutableStateFlow(0L)

    fun init(context: Context) {
        app = context.applicationContext
        lastSeen.value = app.getSharedPreferences("notif", Context.MODE_PRIVATE).getLong("seen", System.currentTimeMillis())
    }

    fun markSeen() {
        lastSeen.value = System.currentTimeMillis()
        app.getSharedPreferences("notif", Context.MODE_PRIVATE).edit().putLong("seen", lastSeen.value).apply()
    }
}
