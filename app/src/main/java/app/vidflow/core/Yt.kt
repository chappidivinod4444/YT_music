package app.vidflow.core

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// ------------------------------------------------------------------ models

sealed interface Item {
    val url: String
    val title: String
    val thumbnail: String?
}

data class Video(
    override val url: String,
    override val title: String,
    override val thumbnail: String?,
    val channel: String,
    val channelUrl: String?,
    val channelAvatar: String?,
    val durationSec: Long,
    val views: Long,
    val uploaded: String?,
    val uploadedAtMillis: Long?,
    val live: Boolean,
    val short: Boolean,
) : Item

data class Channel(
    override val url: String,
    override val title: String,
    override val thumbnail: String?,
    val subscribers: Long,
    val videos: Long,
) : Item

data class Playlist(
    override val url: String,
    override val title: String,
    override val thumbnail: String?,
    val owner: String,
    val count: Long,
) : Item

/** A page of results plus a handle to load more. */
class Paged(val items: List<Item>, val next: (suspend () -> Paged)?)

// ---------------------------------------------------------------- helpers

fun List<Image>.best(maxHeight: Int = 720): String? {
    if (isEmpty()) return null
    val known = filter { it.height > 0 }
    if (known.isEmpty()) return last().url
    return (known.filter { it.height <= maxHeight }.maxByOrNull { it.height } ?: known.minByOrNull { it.height })?.url
}

fun InfoItem.toItem(): Item? = when (this) {
    is StreamInfoItem -> Video(
        url = url,
        title = name,
        thumbnail = thumbnails.best(),
        channel = uploaderName ?: "",
        channelUrl = uploaderUrl,
        channelAvatar = uploaderAvatars.best(200),
        durationSec = duration,
        views = viewCount,
        uploaded = textualUploadDate,
        uploadedAtMillis = try {
            uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()
        } catch (_: Throwable) {
            null
        },
        live = streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM,
        short = try {
            isShortFormContent
        } catch (_: Throwable) {
            false
        },
    )
    is ChannelInfoItem -> Channel(url, name, thumbnails.best(200), subscriberCount, streamCount)
    is PlaylistInfoItem -> Playlist(url, name, thumbnails.best(), uploaderName ?: "", streamCount)
    else -> null
}

private fun List<InfoItem>.items(): List<Item> = mapNotNull { runCatching { it.toItem() }.getOrNull() }

fun friendlyError(t: Throwable): String {
    var e: Throwable? = t
    while (e != null) {
        when (e) {
            is SignInConfirmNotBotException ->
                return "YouTube asked to confirm you're not a bot. Wait a few minutes or switch between Wi-Fi and mobile data."
            is ReCaptchaException -> return "YouTube is limiting requests right now. Try again in a little while."
            is AgeRestrictedContentException -> return "This video is age-restricted and needs a signed-in account."
            is GeographicRestrictionException -> return "This video isn't available in your country."
            is ContentNotAvailableException -> return e.message?.takeIf { it.isNotBlank() } ?: "This content isn't available."
            is IOException -> return "Network problem. Check your connection."
        }
        val m = e.message.orEmpty()
        if (m.contains("HTML document") || m.contains("expected JSON") || m.contains("HTTP 429")) {
            return "YouTube is busy right now. Tap Retry in a few seconds."
        }
        e = e.cause
    }
    return t.message ?: "Something went wrong"
}

// --------------------------------------------------------------------- api

/** All calls block on the network: call them from Dispatchers.IO. */
object Yt {
    val service: StreamingService get() = ServiceList.YouTube
    private val streamCache = ConcurrentHashMap<String, Pair<Long, StreamInfo>>()

    fun init() {
        val locale = Locale.getDefault()
        NewPipe.init(
            HttpDownloader(),
            Localization.fromLocale(locale),
            ContentCountry(locale.country.ifEmpty { "US" }),
        )
    }

    // ---- home

    fun kiosks(): List<String> = try {
        service.kioskList.availableKiosks.toList().sortedBy { if (it.contains("music", true)) 0 else 1 }
    } catch (_: Throwable) {
        emptyList()
    }

    fun kioskName(id: String): String = id.replace("_", " ").replace("trending", "Trending").trim()
        .replaceFirstChar { it.uppercase() }.let { if (it.equals("live", true)) "Live now" else it }

    fun kiosk(id: String): Paged {
        val ex = service.kioskList.getExtractorById(id, null)
        ex.fetchPage()
        val first = ex.initialPage
        fun page(p: Page?): (suspend () -> Paged)? = p?.let { pg ->
            {
                val next = ex.getPage(pg)
                Paged(next.items.items(), page(next.nextPage))
            }
        }
        return Paged(first.items.items(), page(first.nextPage))
    }

    // ---- search

    fun suggestions(query: String): List<String> = try {
        service.suggestionExtractor.suggestionList(query).take(8)
    } catch (_: Throwable) {
        emptyList()
    }

    fun search(query: String, filter: String? = null): Paged {
        val handler: SearchQueryHandler = if (filter == null) service.searchQHFactory.fromQuery(query)
        else service.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = SearchInfo.getInfo(service, handler)
        fun page(p: Page?): (suspend () -> Paged)? = p?.let { pg ->
            {
                val more = SearchInfo.getMoreItems(service, handler, pg)
                Paged(more.items.items(), page(more.nextPage))
            }
        }
        return Paged(info.relatedItems.items(), page(info.nextPage))
    }

    // ---- video

    /** At most two video lookups at a time, so pre-loading never floods YouTube. */
    private val streamGate = java.util.concurrent.Semaphore(2, true)
    private val streamLocks = ConcurrentHashMap<String, Any>()

    fun stream(url: String, fresh: Boolean = false): StreamInfo {
        // One lookup per video: a second caller waits and then reuses the result.
        synchronized(streamLocks.getOrPut(url) { Any() }) {
            val now = System.currentTimeMillis()
            if (!fresh) streamCache[url]?.let { (at, info) -> if (now - at < 2 * 60 * 60 * 1000) return info }
            var attempt = 0
            while (true) {
                streamGate.acquire()
                try {
                    val info = StreamInfo.getInfo(service, url)
                    streamCache[url] = System.currentTimeMillis() to info
                    return info
                } catch (t: Throwable) {
                    // YouTube sometimes answers with a web page instead of data when busy:
                    // wait a moment and ask again before giving up.
                    if (!isBusyPage(t) || attempt >= 2) throw t
                } finally {
                    streamGate.release()
                }
                attempt++
                Thread.sleep(1500L * attempt)
            }
        }
    }

    fun isBusyPage(t: Throwable): Boolean {
        var e: Throwable? = t
        while (e != null) {
            val m = e.message.orEmpty()
            if (m.contains("HTML document") || m.contains("expected JSON") || m.contains("HTTP 429")) return true
            e = e.cause
        }
        return false
    }

    fun related(info: StreamInfo): List<Item> = info.relatedItems.items()

    // ---- home

    /**
     * A home feed like a recommendation page: videos related to what you watched recently,
     * new uploads from your channels, and popular videos, mixed together.
     */
    fun recommended(): Paged {
        val history = Library.history.value
        val watched = history.map { it.video.url }.toHashSet()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        val related = try {
            // Different recent videos each time, so pulling to refresh gives a new Home.
            (history.take(4) + history.drop(4).take(30).shuffled().take(3)).map { it.video.url }.map { url ->
                pool.submit<List<Video>> {
                    try {
                        related(stream(url)).filterIsInstance<Video>().filter { !it.short }.shuffled().take(8)
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }
            }.map { runCatching { it.get() }.getOrDefault(emptyList()) }
        } finally {
            pool.shutdown()
        }
        val fromSubs = Feed.videos.value.orEmpty().filter { !it.short }.take(12)
        val kioskId = kiosks().firstOrNull()
        val popular = kioskId?.let { runCatching { kiosk(it) }.getOrNull() }
        val topicVideos = Topics.list.value.randomOrNull()?.let { t ->
            runCatching { topicPage(t).filterIsInstance<Video>() }.getOrDefault(emptyList())
        }.orEmpty()
        val sources = (related + listOf(fromSubs.shuffled(), popular?.items?.filterIsInstance<Video>().orEmpty().shuffled(), topicVideos.shuffled()))
            .map { it.toMutableList() }.filter { it.isNotEmpty() }
        val mixed = ArrayList<Video>()
        var i = 0
        while (sources.any { it.isNotEmpty() }) {
            val l = sources[i % sources.size]
            if (l.isNotEmpty()) mixed.add(l.removeAt(0))
            i++
        }
        val items = mixed.distinctBy { it.url }.filter { it.url !in watched }
        val shown = java.util.Collections.synchronizedSet(HashSet<String>(items.map { it.url } + watched))
        return Paged(items, moreRecommended(items.shuffled().take(12).toMutableList(), shown, popular?.next, 1))
    }

    /**
     * Endless Home: every page grows from videos already shown (their related videos),
     * mixed with one of your topic chips and more popular videos.
     */
    private fun moreRecommended(
        seeds: MutableList<Video>,
        shown: MutableSet<String>,
        popularNext: (suspend () -> Paged)?,
        round: Int,
    ): suspend () -> Paged = {
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        // Keep a few seeds around so the feed never runs dry.
        val picked = if (seeds.size > 8) List(4) { seeds.removeAt(seeds.indices.random()) } else seeds.shuffled().take(4)
        val lists = try {
            val related = picked.map { v ->
                pool.submit<List<Video>> {
                    runCatching { related(stream(v.url)).filterIsInstance<Video>().filter { !it.short } }.getOrDefault(emptyList())
                }
            }
            val topic = Topics.list.value.randomOrNull()?.let { t ->
                pool.submit<List<Video>> {
                    runCatching { topicPage(t).filterIsInstance<Video>() }.getOrDefault(emptyList())
                }
            }
            related.map { runCatching { it.get() }.getOrDefault(emptyList()).shuffled().take(8) } +
                listOfNotNull(topic?.let { runCatching { it.get() }.getOrDefault(emptyList()).shuffled().take(8) })
        } finally {
            pool.shutdown()
        }
        var nextPopular = popularNext
        val popular = try {
            nextPopular?.invoke()?.also { nextPopular = it.next }?.items?.filterIsInstance<Video>().orEmpty()
        } catch (_: Throwable) {
            nextPopular = null
            emptyList()
        }
        val sources = (lists + listOf(popular)).map { it.toMutableList() }.filter { it.isNotEmpty() }
        val out = ArrayList<Video>()
        var i = 0
        while (sources.any { it.isNotEmpty() }) {
            val l = sources[i % sources.size]
            if (l.isNotEmpty()) {
                val v = l.removeAt(0)
                if (shown.add(v.url)) out.add(v)
            }
            i++
        }
        // New videos become seeds for later pages, so it never runs dry.
        seeds.addAll(out.shuffled().take(8))
        if (seeds.isEmpty()) Library.history.value.take(10).forEach { seeds.add(it.video) }
        Paged(out, if (round < 200) moreRecommended(seeds, shown, nextPopular, round + 1) else null)
    }

    /** Next page of a topic each time it's asked, so Home keeps finding new videos. */
    private val topicNext = ConcurrentHashMap<String, suspend () -> Paged>()

    private fun topicPage(t: String): List<Item> {
        val next = topicNext.remove(t)
        val p = if (next != null) kotlinx.coroutines.runBlocking { next() } else topic(t)
        p.next?.let { topicNext[t] = it }
        return p.items
    }

    /** Videos for a topic chip like "Music" or "Telugu cinema". */
    fun topic(name: String): Paged {
        val p = search(name, "videos")
        return Paged(p.items.filter { it !is Video || !it.short }, p.next)
    }

    // ---- comments

    class Comment(
        val id: String?,
        val author: String,
        val authorUrl: String?,
        val avatar: String?,
        val text: String,
        val likes: Int,
        val date: String?,
        val replyCount: Int,
        val replies: Page?,
        val pinned: Boolean,
        val hearted: Boolean,
    )

    class CommentPage(
        val comments: List<Comment>,
        val next: (suspend () -> CommentPage)?,
        val disabled: Boolean = false,
        /** Total comments on the video, or -1 if unknown. */
        val count: Long = -1,
    )

    private fun toComment(c: org.schabi.newpipe.extractor.comments.CommentsInfoItem) = Comment(
        id = runCatching { c.commentId }.getOrNull(),
        author = c.uploaderName ?: "",
        authorUrl = c.uploaderUrl,
        avatar = c.uploaderAvatars.best(120),
        text = (c.commentText?.content ?: "")
            .replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">"),
        likes = c.likeCount,
        date = c.textualUploadDate,
        replyCount = c.replyCount,
        replies = c.replies,
        pinned = c.isPinned,
        hearted = c.isHeartedByUploader,
    )

    private fun morePages(url: String, page: Page?): (suspend () -> CommentPage)? = page?.let { pg ->
        {
            val more = org.schabi.newpipe.extractor.comments.CommentsInfo.getMoreItems(service, url, pg)
            CommentPage(more.items.map { toComment(it) }, morePages(url, more.nextPage))
        }
    }

    fun comments(url: String): CommentPage {
        val info = org.schabi.newpipe.extractor.comments.CommentsInfo.getInfo(service, url)
        if (info.isCommentsDisabled) return CommentPage(emptyList(), null, disabled = true)
        val count = runCatching { info.commentsCount.toLong() }.getOrDefault(-1L)
        return CommentPage(info.relatedItems.map { toComment(it) }, morePages(url, info.nextPage), count = count)
    }

    /** The 11-character video ID from a watch or shorts link. */
    fun videoId(url: String): String? =
        Regex("(?:v=|/shorts/|youtu\\.be/|/embed/|/live/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)

    fun replies(url: String, page: Page): CommentPage {
        val more = org.schabi.newpipe.extractor.comments.CommentsInfo.getMoreItems(service, url, page)
        return CommentPage(more.items.map { toComment(it) }, morePages(url, more.nextPage))
    }

    // ---- channel

    class ChannelPage(val info: ChannelInfo, val videos: Paged) {
        val shortsTab: ListLinkHandler? = info.tabs.firstOrNull { ChannelTabs.SHORTS in it.contentFilters }

        /** Tabs we can show: (label, handler). */
        val tabs: List<Pair<String, ListLinkHandler>> = listOfNotNull(
            info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }?.let { "Videos" to it },
            shortsTab?.let { "Shorts" to it },
            info.tabs.firstOrNull { ChannelTabs.LIVESTREAMS in it.contentFilters }?.let { "Live" to it },
            info.tabs.firstOrNull { ChannelTabs.PLAYLISTS in it.contentFilters }?.let { "Playlists" to it },
        )
    }

    fun channelTab(tab: ListLinkHandler, shorts: Boolean = false): Paged {
        val p = tabPage(tab)
        return if (!shorts) p else Paged(p.items.map { if (it is Video) it.copy(short = true) else it }, p.next)
    }

    fun channel(url: String): ChannelPage {
        val info = ChannelInfo.getInfo(service, url)
        val tab: ListLinkHandler? = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
            ?: info.tabs.firstOrNull()
        val videos = if (tab != null) tabPage(tab) else Paged(emptyList(), null)
        return ChannelPage(info, videos)
    }

    fun channelShorts(page: ChannelPage): Paged =
        page.shortsTab?.let { tabPage(it).let { p -> Paged(p.items.map { i -> if (i is Video) i.copy(short = true) else i }, p.next) } }
            ?: Paged(emptyList(), null)

    private val shortTopics = listOf(
        "#shorts", "#shorts funny", "#shorts comedy", "#shorts music", "#shorts cricket", "#shorts football",
        "#shorts tech", "#shorts cooking", "#shorts food", "#shorts travel", "#shorts gaming", "#shorts motivation",
        "#shorts dance", "#shorts animals", "#shorts science", "#shorts movie", "#shorts cars", "#shorts bike",
        "#shorts art", "#shorts life hacks", "#shorts nature", "#shorts fitness", "#shorts satisfying", "#shorts news",
        "#shorts viral", "#shorts trending", "#shorts song", "#shorts magic", "#shorts diy", "#shorts history",
    )

    /** Shorts suggested by what you watched (filled in while you watch). */
    private val suggested = java.util.concurrent.ConcurrentLinkedQueue<Video>()

    fun isShortLike(v: Video) = v.short || v.durationSec in 1..180

    fun addSuggestions(info: StreamInfo) {
        val more = info.relatedItems.items().filterIsInstance<Video>().filter { isShortLike(it) && !it.live }
        more.shuffled().take(6).forEach { suggested.add(it.copy(short = true)) }
        while (suggested.size > 60) suggested.poll()
    }

    /**
     * An endless, varied feed: your channels' shorts, suggestions from what you watched,
     * and a fresh random mix of topics each time. Shorts you've seen are skipped.
     */
    /** A Shorts page fetched when the app starts; used once by the Shorts tab. */
    @Volatile
    var warmShorts: Paged? = null

    fun takeWarmShorts(): Paged? = warmShorts.also { warmShorts = null }

    fun shortsFeed(subscriptionUrls: List<String>): Paged {
        val shown = java.util.Collections.synchronizedSet(HashSet<String>())

        fun batch(includeSubs: Boolean): List<Video> {
            val pool = java.util.concurrent.Executors.newFixedThreadPool(5)
            try {
                val jobs = ArrayList<java.util.concurrent.Future<List<Video>>>()
                if (includeSubs) {
                    subscriptionUrls.shuffled().take(6).forEach { url ->
                        jobs.add(pool.submit<List<Video>> {
                            try {
                                val info = ChannelInfo.getInfo(service, url)
                                val tab = info.tabs.firstOrNull { ChannelTabs.SHORTS in it.contentFilters } ?: return@submit emptyList()
                                ChannelTabInfo.getInfo(service, tab).relatedItems.items().filterIsInstance<Video>().shuffled().take(5)
                            } catch (_: Throwable) {
                                emptyList()
                            }
                        })
                    }
                }
                shortTopics.shuffled().take(3).forEach { topic ->
                    jobs.add(pool.submit<List<Video>> {
                        try {
                            search(topic, "videos").items.filterIsInstance<Video>().filter { isShortLike(it) && !it.live }
                        } catch (_: Throwable) {
                            emptyList()
                        }
                    })
                }
                val lists = jobs.map { runCatching { it.get() }.getOrDefault(emptyList()) }
                val fromSuggestions = generateSequence { suggested.poll() }.take(12).toList()
                // Interleave the sources so one topic doesn't fill the screen.
                val mixed = ArrayList<Video>()
                val all = (lists + listOf(fromSuggestions)).map { it.shuffled().toMutableList() }.filter { it.isNotEmpty() }
                var i = 0
                while (all.any { it.isNotEmpty() }) {
                    val l = all[i % all.size]
                    if (l.isNotEmpty()) mixed.add(l.removeAt(0))
                    i++
                }
                return mixed.map { it.copy(short = true) }
                    .filter { !app.vidflow.core.SeenShorts.has(it.url) && shown.add(it.url) }
            } finally {
                pool.shutdown()
            }
        }

        fun next(round: Int): suspend () -> Paged = {
            var items = batch(includeSubs = round % 3 == 0)
            if (items.isEmpty()) items = batch(includeSubs = false)
            Paged(items, if (round < 50) next(round + 1) else null)
        }

        var first = batch(includeSubs = true)
        if (first.isEmpty()) {
            // Everything was seen already: start over with the history cleared.
            app.vidflow.core.SeenShorts.clear()
            first = batch(includeSubs = true)
        }
        return Paged(first, next(1))
    }

    private fun tabPage(tab: ListLinkHandler): Paged {
        val first = ChannelTabInfo.getInfo(service, tab)
        fun page(p: Page?): (suspend () -> Paged)? = p?.let { pg ->
            {
                val more = ChannelTabInfo.getMoreItems(service, tab, pg)
                Paged(more.items.items(), page(more.nextPage))
            }
        }
        return Paged(first.relatedItems.items(), page(first.nextPage))
    }

    /** Newest uploads of one channel, for the subscriptions feed. */
    fun latestUploads(channelUrl: String): List<Video> = try {
        channel(channelUrl).videos.items.filterIsInstance<Video>().take(15)
    } catch (_: Throwable) {
        emptyList()
    }

    // ---- playlist

    class PlaylistPage(val info: PlaylistInfo, val videos: Paged)

    fun playlist(url: String): PlaylistPage {
        val info = PlaylistInfo.getInfo(service, url)
        fun page(p: Page?): (suspend () -> Paged)? = p?.let { pg ->
            {
                val more = PlaylistInfo.getMoreItems(service, url, pg)
                Paged(more.items.items(), page(more.nextPage))
            }
        }
        return PlaylistPage(info, Paged(info.relatedItems.items(), page(info.nextPage)))
    }
}

// ---------------------------------------------------------------- format

fun formatCount(n: Long): String = if (Locale.getDefault().country == "IN") formatIndian(n) else when {
    n < 0 -> ""
    n < 1_000 -> n.toString()
    n < 1_000_000 -> trim(n / 1_000.0) + "K"
    n < 1_000_000_000 -> trim(n / 1_000_000.0) + "M"
    else -> trim(n / 1_000_000_000.0) + "B"
}

/** Indian style: 45K, 62 lakh, 3.2 crore. */
private fun formatIndian(n: Long): String = when {
    n < 0 -> ""
    n < 1_000 -> n.toString()
    n < 100_000 -> trim(n / 1_000.0) + "K"
    n < 10_000_000 -> trim(n / 100_000.0) + " lakh"
    else -> trim(n / 10_000_000.0) + " crore"
}

private fun trim(d: Double): String = if (d >= 10) d.toLong().toString() else String.format(Locale.US, "%.1f", d).removeSuffix(".0")

fun formatDuration(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

/** "3 months ago" from a video's upload date; falls back to the text YouTube gave. */
fun uploadedAgo(i: org.schabi.newpipe.extractor.stream.StreamInfo?, fallback: String?): String? {
    val millis = try {
        i?.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()
    } catch (_: Throwable) {
        null
    }
    if (millis != null) return timeAgo(millis)
    val t = i?.textualUploadDate ?: fallback ?: return null
    // Raw ISO dates like 2026-07-31T23:06:57-07:00.
    return runCatching { timeAgo(java.time.OffsetDateTime.parse(t).toInstant().toEpochMilli()) }.getOrDefault(t)
}

fun timeAgo(millis: Long): String {
    val s = ((System.currentTimeMillis() - millis) / 1000).coerceAtLeast(0)
    fun p(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"} ago"
    return when {
        s < 60 -> "Just now"
        s < 3600 -> p(s / 60, "minute")
        s < 86_400 -> p(s / 3600, "hour")
        s < 7 * 86_400 -> p(s / 86_400, "day")
        s < 30 * 86_400 -> p(s / (7 * 86_400), "week")
        s < 365 * 86_400 -> p(s / (30 * 86_400), "month")
        else -> p(s / (365 * 86_400), "year")
    }
}

/** Turns YouTube's HTML description into plain text. */
fun plainText(html: String): String = html
    .replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
    .replace("&lt;", "<").replace("&gt;", ">")
    .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
