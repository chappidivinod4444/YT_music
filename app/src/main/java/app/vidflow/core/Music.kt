package app.vidflow.core

import android.content.Context
import android.content.SharedPreferences
import app.vidflow.player.PlayerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** "Artist - Topic" channels are YouTube's auto-made artist pages: show just the name. */
fun artistName(name: String): String = name.removeSuffix(" - Topic").removeSuffix(" - topic")

/** A row on the music Home screen. */
data class Shelf(val title: String, val items: List<Item>, val style: Style, val subtitle: String? = null) {
    enum class Style { QuickPicks, Squares, Wide }
}

/** YouTube Music lookups: songs, albums, playlists and artists. */
object Music {
    const val SONGS = "music_songs"
    const val VIDEOS = "music_videos"
    const val ALBUMS = "music_albums"
    const val PLAYLISTS = "music_playlists"
    const val ARTISTS = "music_artists"

    val moods = listOf("Relax", "Energize", "Workout", "Commute", "Focus", "Party", "Romance", "Sad", "Sleep", "Feel good")

    fun songs(query: String): List<Video> =
        runCatching { Yt.search(query, SONGS).items.filterIsInstance<Video>() }.getOrDefault(emptyList())

    fun search(query: String, filter: String): Paged = Yt.search(query, filter)

    private fun lists(query: String, filter: String): List<Item> =
        runCatching { Yt.search(query, filter).items.filter { it is Playlist || it is Channel } }.getOrDefault(emptyList())

    /** Songs that go with this one: YouTube's "related" list, songs only. */
    fun related(v: Video): List<Video> = runCatching {
        Yt.stream(v.url).relatedItems.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
            .mapNotNull { it.toItem() as? Video }
            .filter { !it.live && it.durationSec in 60..900 }
    }.getOrDefault(emptyList())

    /** Builds Home: quick picks from what you play, listen again, mixes and playlists. */
    fun home(mood: String?): List<Shelf> {
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val recent = MusicHistory.list.value
            val region = java.util.Locale.getDefault().country
            val local = when (region) {
                "IN" -> listOf("Telugu", "Hindi", "Tamil")
                else -> emptyList()
            }
            if (mood != null) {
                val songs = pool.submit<List<Video>> { songs("$mood songs") }
                val more = pool.submit<List<Video>> { songs("$mood music ${local.firstOrNull().orEmpty()}") }
                val playlists = pool.submit<List<Item>> { lists("$mood playlist", PLAYLISTS) }
                return listOfNotNull(
                    Shelf("$mood songs", (songs.get() + more.get()).distinctBy { it.url }, Shelf.Style.QuickPicks),
                    playlists.get().takeIf { it.isNotEmpty() }?.let { Shelf("$mood playlists", it, Shelf.Style.Squares) },
                )
            }
            val quick = pool.submit<List<Video>> {
                val seeds = recent.take(3)
                val fromSeeds = seeds.flatMap { related(it).take(12) }
                (fromSeeds.shuffled() + if (fromSeeds.size < 12) songs("top songs ${local.firstOrNull().orEmpty()}") else emptyList())
                    .distinctBy { it.url }.filter { s -> recent.none { it.url == s.url } }.take(20)
            }
            val trending = pool.submit<List<Video>> { songs("trending songs ${local.firstOrNull().orEmpty()} 2026") }
            val mixes = pool.submit<List<Item>> {
                val artists = recent.map { it.channel }.filter { it.isNotBlank() }.distinct().take(3)
                artists.flatMap { lists("$it mix", PLAYLISTS).take(3) }.ifEmpty { lists("top hits mix", PLAYLISTS) }
            }
            val charts = pool.submit<List<Item>> { lists("top 100 ${local.firstOrNull() ?: "global"} songs", PLAYLISTS) }
            val albums = pool.submit<List<Item>> {
                val artist = recent.firstOrNull()?.channel?.takeIf { it.isNotBlank() }
                lists(if (artist != null) "$artist album" else "new albums ${local.firstOrNull().orEmpty()}", ALBUMS)
            }
            val localPicks = local.drop(1).map { lang -> lang to pool.submit<List<Video>> { songs("$lang hit songs") } }
            return buildList {
                quick.get().takeIf { it.isNotEmpty() }?.let { add(Shelf("Quick picks", it, Shelf.Style.QuickPicks, "Start radio from a song")) }
                if (recent.isNotEmpty()) add(Shelf("Listen again", recent.take(16), Shelf.Style.Squares))
                mixes.get().takeIf { it.isNotEmpty() }?.let { add(Shelf("Mixed for you", it, Shelf.Style.Squares)) }
                trending.get().takeIf { it.isNotEmpty() }?.let { add(Shelf("Trending songs", it, Shelf.Style.QuickPicks)) }
                albums.get().takeIf { it.isNotEmpty() }?.let { add(Shelf("Albums for you", it, Shelf.Style.Squares)) }
                localPicks.forEach { (lang, f) -> f.get().takeIf { it.isNotEmpty() }?.let { add(Shelf("$lang hits", it, Shelf.Style.Wide)) } }
                charts.get().takeIf { it.isNotEmpty() }?.let { add(Shelf("Charts", it, Shelf.Style.Squares)) }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    /** An artist's page: top songs, albums, singles/playlists, videos. */
    class ArtistPage(val name: String, val avatar: String?, val banner: String?, val subscribers: Long,
                     val songs: List<Video>, val albums: List<Item>, val playlists: List<Item>, val videos: List<Video>)

    fun artist(url: String): ArtistPage {
        val page = Yt.channel(url)
        val name = page.info.name
        val pool = java.util.concurrent.Executors.newFixedThreadPool(3)
        try {
            val songs = pool.submit<List<Video>> { songs(name).filter { it.channel.equals(name, true) || it.channel.contains(name, true) }.ifEmpty { songs(name) } }
            val albums = pool.submit<List<Item>> { lists("$name", ALBUMS) }
            val playlists = pool.submit<List<Item>> { lists("$name songs", PLAYLISTS) }
            return ArtistPage(
                name = name,
                avatar = page.info.avatars.maxByOrNull { it.width }?.url,
                banner = page.info.banners.maxByOrNull { it.width }?.url,
                subscribers = page.info.subscriberCount,
                songs = songs.get().take(20),
                albums = albums.get(),
                playlists = playlists.get(),
                videos = page.videos.items.filterIsInstance<Video>().filter { !it.short },
            )
        } finally {
            pool.shutdownNow()
        }
    }
}

/** Songs you played, newest first (kept on the phone). */
object MusicHistory {
    private lateinit var prefs: SharedPreferences
    val list = MutableStateFlow<List<Video>>(emptyList())

    fun init(c: Context) {
        prefs = c.getSharedPreferences("music", Context.MODE_PRIVATE)
        list.value = runCatching {
            val a = JSONArray(prefs.getString("history", "[]"))
            (0 until a.length()).mapNotNull { Library.videoFrom(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun add(v: Video) {
        val l = (listOf(v) + list.value.filter { it.url != v.url }).take(200)
        list.value = l
        prefs.edit().putString("history", JSONArray().apply { l.forEach { put(Library.videoJson(it)) } }.toString()).apply()
    }

    fun clear() {
        list.value = emptyList()
        prefs.edit().remove("history").apply()
    }
}

/** What's playing next: the queue, shuffle, repeat and endless radio. */
object MusicQueue {
    enum class Repeat { Off, All, One }

    val queue = MutableStateFlow<List<Video>>(emptyList())
    val index = MutableStateFlow(0)
    val shuffle = MutableStateFlow(false)
    val repeat = MutableStateFlow(Repeat.Off)
    val loading = MutableStateFlow(false)
    /** Keep adding similar songs when the queue runs out. */
    val radio = MutableStateFlow(true)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var radioJob: Job? = null

    val current: Video? get() = queue.value.getOrNull(index.value)

    fun start() {
        scope.launch {
            var last = PlayerHolder.endedCount.value
            PlayerHolder.endedCount.collect { n ->
                if (n != last) {
                    last = n
                    onEnded()
                }
            }
        }
    }

    /** Play [list] starting at [at]. With [radio] on, similar songs follow when it ends. */
    fun play(list: List<Video>, at: Int = 0) {
        if (list.isEmpty()) return
        queue.value = list
        index.value = at.coerceIn(0, list.lastIndex)
        load()
    }

    /** "Start radio": this song, then songs like it. */
    fun startRadio(v: Video) {
        play(listOf(v))
        topUp(force = true)
    }

    fun playNext(v: Video) {
        val q = queue.value.toMutableList()
        if (q.isEmpty()) return play(listOf(v))
        q.removeAll { it.url == v.url && it != current }
        q.add((index.value + 1).coerceAtMost(q.size), v)
        queue.value = q
    }

    fun addToQueue(v: Video) {
        if (queue.value.isEmpty()) return play(listOf(v))
        queue.value = queue.value.filter { it.url != v.url || it == current } + v
    }

    fun jumpTo(i: Int) {
        if (i !in queue.value.indices) return
        index.value = i
        load()
    }

    fun remove(i: Int) {
        if (i == index.value || i !in queue.value.indices) return
        queue.value = queue.value.toMutableList().also { it.removeAt(i) }
        if (i < index.value) index.value = index.value - 1
    }

    fun next() {
        val q = queue.value
        if (q.isEmpty()) return
        val nextIndex = when {
            shuffle.value && q.size > 1 -> (q.indices - index.value).random()
            index.value < q.lastIndex -> index.value + 1
            repeat.value == Repeat.All -> 0
            else -> {
                topUp(force = true, thenPlay = true)
                return
            }
        }
        index.value = nextIndex
        load()
    }

    fun previous() {
        if (PlayerHolder.player.currentPosition > 3_000 || index.value == 0) {
            PlayerHolder.player.seekTo(0)
            PlayerHolder.player.play()
            return
        }
        index.value = index.value - 1
        load()
    }

    fun toggleShuffle() { shuffle.value = !shuffle.value }

    fun cycleRepeat() {
        repeat.value = when (repeat.value) {
            Repeat.Off -> Repeat.All
            Repeat.All -> Repeat.One
            Repeat.One -> Repeat.Off
        }
        PlayerHolder.player.repeatMode = if (repeat.value == Repeat.One) androidx.media3.common.Player.REPEAT_MODE_ONE
        else androidx.media3.common.Player.REPEAT_MODE_OFF
    }

    private fun onEnded() {
        if (repeat.value == Repeat.One) return
        next()
    }

    private fun load() {
        val v = current ?: return
        job?.cancel()
        loading.value = true
        job = scope.launch {
            try {
                val info = withContext(Dispatchers.IO) { Yt.stream(v.url) }
                val full = v.copy(
                    title = info.name ?: v.title,
                    channel = info.uploaderName ?: v.channel,
                    channelUrl = info.uploaderUrl ?: v.channelUrl,
                    channelAvatar = info.uploaderAvatars.best(200) ?: v.channelAvatar,
                    thumbnail = v.thumbnail ?: info.thumbnails.best(),
                    durationSec = if (info.duration > 0) info.duration else v.durationSec,
                )
                if (PlayerHolder.audioOnly.value) PlayerHolder.playAudio(full, info) else PlayerHolder.play(full, info, 480, 0)
                PlayerHolder.player.repeatMode = if (repeat.value == Repeat.One) androidx.media3.common.Player.REPEAT_MODE_ONE
                else androidx.media3.common.Player.REPEAT_MODE_OFF
                MusicHistory.add(full)
            } catch (t: Throwable) {
                PlayerHolder.error.value = friendlyError(t)
            } finally {
                loading.value = false
            }
            // Warm up the next song so it starts instantly.
            queue.value.getOrNull(index.value + 1)?.let { n ->
                launch(Dispatchers.IO) { runCatching { PlayerHolder.prefetch(Yt.stream(n.url), 144) } }
            }
            if (index.value >= queue.value.size - 2) topUp()
        }
    }

    /** Adds songs similar to the current one to the end of the queue. */
    private fun topUp(force: Boolean = false, thenPlay: Boolean = false) {
        if (!radio.value && !force) return
        if (radioJob?.isActive == true) return
        val seed = current ?: return
        radioJob = scope.launch {
            val more = withContext(Dispatchers.IO) { Music.related(seed) }
            val have = queue.value.map { it.url }.toSet()
            val fresh = more.filter { it.url !in have }.take(20)
            if (fresh.isNotEmpty()) {
                queue.value = queue.value + fresh
                if (thenPlay) {
                    index.value = index.value + 1
                    load()
                }
            }
        }
    }
}
