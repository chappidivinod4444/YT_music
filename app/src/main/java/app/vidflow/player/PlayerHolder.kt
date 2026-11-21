@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.vidflow.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.vidflow.core.friendlyError
import app.vidflow.core.HttpDownloader
import app.vidflow.core.Library
import app.vidflow.core.Video
import app.vidflow.core.best
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/** One shared player for the whole app, so playback continues in the background and in picture-in-picture. */
object PlayerHolder {
    lateinit var player: ExoPlayer
        private set
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val streamClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** What's loaded right now. */
    val current = MutableStateFlow<Video?>(null)
    val info = MutableStateFlow<StreamInfo?>(null)
    val qualities = MutableStateFlow<List<Int>>(emptyList())
    val quality = MutableStateFlow(0)
    val error = MutableStateFlow<String?>(null)

    /** Subtitle languages of the current video: (language tag, name). */
    val captions = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    /** Selected subtitle language, or null when captions are off. */
    val caption = MutableStateFlow<String?>(null)
    /** Chapters of the current video: (start ms, title). */
    val chapters = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    val autoplay = MutableStateFlow(true)
    val looping = MutableStateFlow(false)
    /** Increases each time a video finishes, so screens can start the next one. */
    val endedCount = MutableStateFlow(0)
    /** Videos played in this session, for the "previous" button. */
    val session = ArrayList<Video>()

    private var controller: MediaController? = null

    /** Touch controls are locked (Lock screen in the settings sheet). */
    val locked = MutableStateFlow(false)
    /** Soft glow of the video's colours behind the page. */
    val ambient = MutableStateFlow(true)
    /** Minutes left on the sleep timer: 0 = off, -1 = at the end of this video. */
    val sleep = MutableStateFlow(0)
    private val sleepRunnable = Runnable { player.pause(); sleep.value = 0 }

    /** Recent video bytes on disk, so shorts and replays start instantly. */
    private lateinit var cache: androidx.media3.datasource.cache.SimpleCache
    private lateinit var cached: androidx.media3.datasource.cache.CacheDataSource.Factory
    private val prefetchPool = java.util.concurrent.Executors.newFixedThreadPool(3)
    private val prefetched = java.util.Collections.synchronizedSet(HashSet<String>())

    fun init(context: Context) {
        app = context.applicationContext
        ambient.value = app.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("ambient", true)
        cache = androidx.media3.datasource.cache.SimpleCache(
            java.io.File(app.cacheDir, "media"),
            androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(300L * 1024 * 1024),
            androidx.media3.database.StandaloneDatabaseProvider(app),
        )
        cached = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(ChunkedHttpDataSource.Factory(streamClient))
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            // Same file = same key, even if request-only parameters differ.
            .setCacheKeyFactory { spec ->
                val u = spec.uri
                if (u.query == null) u.toString()
                else u.buildUpon().clearQuery().apply {
                    for (name in u.queryParameterNames) if (name != "rn" && name != "range" && name != "cpn") {
                        u.getQueryParameter(name)?.let { appendQueryParameter(name, it) }
                    }
                }.build().toString()
            }
        // Smaller start buffer: videos begin playing sooner.
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(12_000, 45_000, 500, 1_200)
            .build()
        player = ExoPlayer.Builder(app)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && player.repeatMode == Player.REPEAT_MODE_OFF) {
                    if (sleep.value == -1) {
                        sleep.value = 0
                        return
                    }
                    endedCount.value++
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                error.value = "Couldn't play this video (${e.errorCodeName}). Tap retry."
            }
        })
        // Save where you stopped, every few seconds.
        main.postDelayed(object : Runnable {
            override fun run() {
                val v = current.value
                if (v != null && player.isPlaying) Library.savePosition(v.url, player.currentPosition)
                main.postDelayed(this, 5000)
            }
        }, 5000)
    }

    /** Connects to the background service so the media notification appears. */
    fun connectSession(context: Context) {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ controller = runCatching { future.get() }.getOrNull() }, { it.run() })
    }

    fun preferredHeight(): Int = app.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("quality", 720)

    fun setPreferredHeight(h: Int) {
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("quality", h).apply()
    }

    /** Must be called on the main thread with info fetched in the background. */
    fun play(video: Video, streamInfo: StreamInfo, height: Int = preferredHeight(), resumeAt: Long? = null, loop: Boolean = false) {
        error.value = null
        looping.value = loop
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (!video.short && session.lastOrNull()?.url != video.url) {
            session.add(video)
            if (session.size > 50) session.removeAt(0)
        }
        chapters.value = try {
            streamInfo.streamSegments.map { it.startTimeSeconds * 1000L to it.title }
        } catch (_: Throwable) {
            emptyList()
        }
        captions.value = subtitleChoices(streamInfo).map { it.languageTag to it.displayLanguageName }
        caption.value = null
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        val sameVideo = current.value?.url == video.url
        current.value = video
        info.value = streamInfo
        val heights = videoChoices(streamInfo).map { it.heightOrParsed() }.filter { it > 0 }.distinct().sortedDescending()
        qualities.value = heights
        try {
            val (source, chosen) = buildSource(video, streamInfo, height)
            quality.value = chosen
            val start = resumeAt ?: if (sameVideo) player.currentPosition else Library.position(video.url).let {
                if (streamInfo.duration > 0 && it > (streamInfo.duration * 1000 - 15_000)) 0 else it
            }
            player.setMediaSource(source, start)
            player.prepare()
            player.playWhenReady = true
        } catch (t: Throwable) {
            error.value = friendlyError(t)
        }
    }

    /** True while playing sound only (music mode), false when the video is shown. */
    val audioOnly = MutableStateFlow(true)

    /** Plays only the sound of a song: smaller downloads, starts faster, saves battery. */
    fun playAudio(video: Video, streamInfo: StreamInfo, resumeAt: Long = 0) {
        error.value = null
        looping.value = false
        current.value = video
        info.value = streamInfo
        audioOnly.value = true
        chapters.value = emptyList()
        captions.value = emptyList()
        try {
            val audios = streamInfo.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            val audio = audios.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
                ?: audios.maxByOrNull { it.averageBitrate }
            val source = if (audio != null) {
                androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(cached)
                    .createMediaSource(mediaItem(audio.content, video, streamInfo))
            } else {
                buildSource(video, streamInfo, 144).first
            }
            player.setMediaSource(source, resumeAt)
            player.prepare()
            player.playWhenReady = true
        } catch (t: Throwable) {
            error.value = friendlyError(t)
        }
    }

    /** Switches the current song between sound only and video, keeping the position. */
    fun setVideoMode(video: Boolean) {
        val v = current.value ?: return
        val i = info.value ?: return
        val at = player.currentPosition
        if (video) {
            audioOnly.value = false
            play(v, i, 480, at)
        } else {
            playAudio(v, i, at)
        }
    }

    fun changeQuality(height: Int) {
        val v = current.value ?: return
        val i = info.value ?: return
        setPreferredHeight(height)
        play(v, i, height, player.currentPosition, loop = player.repeatMode == Player.REPEAT_MODE_ONE)
    }

    /** Background: keep the sound, stop downloading video to save data and battery. */
    fun setVideoEnabled(on: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !on)
            .build()
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    /** Subtitles we can show: your language and English first, max 8. */
    private fun subtitleChoices(i: StreamInfo) = try {
        val lang = java.util.Locale.getDefault().language
        i.subtitles.filter { it.isUrl && it.format == MediaFormat.VTT }
            .sortedBy { s ->
                when {
                    s.languageTag.startsWith(lang) -> 0
                    s.languageTag.startsWith("en") -> 1
                    s.isAutoGenerated -> 3
                    else -> 2
                }
            }
            .distinctBy { it.languageTag }
            .take(8)
    } catch (_: Throwable) {
        emptyList()
    }

    fun setCaption(tag: String?) {
        caption.value = tag
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, tag == null)
            .apply { if (tag != null) setPreferredTextLanguage(tag) }
            .build()
    }

    fun setLoop(on: Boolean) {
        looping.value = on
        player.repeatMode = if (on) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun setAmbient(on: Boolean) {
        ambient.value = on
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("ambient", on).apply()
    }

    /** Pause after [minutes]; -1 = when this video ends; 0 = off. */
    fun setSleep(minutes: Int) {
        main.removeCallbacks(sleepRunnable)
        sleep.value = minutes
        if (minutes > 0) main.postDelayed(sleepRunnable, minutes * 60_000L)
    }

    /**
     * Downloads the first second or two of a video in the background, so when you
     * swipe to it, it starts at once from the disk cache.
     */
    fun prefetch(i: StreamInfo, height: Int) {
        if (!prefetched.add(i.url)) return
        prefetchPool.execute {
            try {
                val (video, audio) = pick(i, height) ?: return@execute
                for ((url, bytes) in listOf(video.content to 1_500_000L, audio.content to 300_000L)) {
                    val spec = androidx.media3.datasource.DataSpec.Builder().setUri(url).setPosition(0).setLength(bytes).build()
                    androidx.media3.datasource.cache.CacheWriter(cached.createDataSource(), spec, null, null).cache()
                }
            } catch (_: Throwable) {
                prefetched.remove(i.url)
            }
        }
    }

    private fun pick(i: StreamInfo, wantHeight: Int): Pair<VideoStream, AudioStream>? {
        val videos = videoChoices(i)
        val audios = i.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.itagItem != null }
        if (videos.isEmpty() || audios.isEmpty() || i.duration <= 0) return null
        // Prefer the chosen height (or the closest below it), H.264 first for smooth hardware decoding.
        val byHeight = videos.groupBy { it.heightOrParsed() }
        val target = byHeight.keys.filter { it <= wantHeight }.maxOrNull() ?: byHeight.keys.min()
        val candidates = byHeight[target].orEmpty()
        val video = candidates.firstOrNull { it.format == MediaFormat.MPEG_4 } ?: candidates.first()
        val audio = audios.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: audios.maxBy { it.averageBitrate }
        return video to audio
    }

    /** Picture only, low quality: for the silent previews on Home. */
    fun previewSource(v: Video, i: StreamInfo): MediaSource? {
        if (v.live) return null
        val (video, _) = pick(i, 360) ?: return null
        return dash(video.content, video.itagItem!!, i.duration, cached, v, i)
    }

    /** Lets the preview player share the disk cache and network settings. */
    fun appContext(): Context = app

    fun chapterAt(ms: Long): String? = chapters.value.lastOrNull { it.first <= ms }?.second

    fun stop() {
        current.value?.let { Library.savePosition(it.url, player.currentPosition) }
        player.stop()
        player.clearMediaItems()
        current.value = null
        info.value = null
    }

    // ---------------------------------------------------------------- sources

    /** Quality as shown to people ("720p"), which also works for vertical videos. */
    private fun VideoStream.heightOrParsed(): Int =
        resolution.takeWhile { it.isDigit() }.toIntOrNull()?.takeIf { it > 0 } ?: height.coerceAtLeast(0)

    private fun videoChoices(i: StreamInfo): List<VideoStream> =
        i.videoOnlyStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.itagItem != null }

    private fun metadata(v: Video, i: StreamInfo): MediaMetadata = MediaMetadata.Builder()
        .setTitle(i.name ?: v.title)
        .setArtist(i.uploaderName ?: v.channel)
        .setArtworkUri((i.thumbnails.best() ?: v.thumbnail)?.let { Uri.parse(it) })
        .build()

    private fun mediaItem(uri: String, v: Video, i: StreamInfo) = MediaItem.Builder()
        .setUri(uri)
        .setMediaId(v.url)
        .setMediaMetadata(metadata(v, i))
        .build()

    /** Picks the streams and builds a media source. Returns it with the chosen height. */
    private fun buildSource(v: Video, i: StreamInfo, wantHeight: Int): Pair<MediaSource, Int> {
        val chunked = cached

        // Live streams come as HLS playlists.
        if (i.hlsUrl.isNotEmpty() && (v.live || i.videoOnlyStreams.isEmpty())) {
            val http = DefaultHttpDataSource.Factory().setUserAgent(HttpDownloader.USER_AGENT)
            return HlsMediaSource.Factory(http).createMediaSource(mediaItem(i.hlsUrl, v, i)) to 0
        }

        val picked = pick(i, wantHeight)
        if (picked != null) {
            val (video, audio) = picked
            val target = video.heightOrParsed()
            val videoSource = dash(video.content, video.itagItem!!, i.duration, chunked, v, i)
            val audioSource = dash(audio.content, audio.itagItem!!, i.duration, chunked, v, i)
            val subs = subtitleChoices(i).map { s ->
                val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.content))
                    .setMimeType(androidx.media3.common.MimeTypes.TEXT_VTT)
                    .setLanguage(s.languageTag)
                    .setLabel(s.displayLanguageName)
                    .build()
                androidx.media3.exoplayer.source.SingleSampleMediaSource.Factory(
                    DefaultHttpDataSource.Factory().setUserAgent(HttpDownloader.USER_AGENT)
                ).createMediaSource(config, C.TIME_UNSET)
            }
            return MergingMediaSource(videoSource, audioSource, *subs.toTypedArray()) to target
        }

        // Fallback: a single file with video and sound together (lower quality).
        val muxed = i.videoStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { it.heightOrParsed() }
        if (muxed != null) {
            return ProgressiveMediaSource.Factory(chunked).createMediaSource(mediaItem(muxed.content, v, i)) to muxed.heightOrParsed()
        }
        // Audio only.
        val audio: AudioStream = i.audioStreams.filter { it.isUrl }.maxByOrNull { it.averageBitrate }
            ?: throw IllegalStateException("no playable streams")
        return ProgressiveMediaSource.Factory(chunked).createMediaSource(mediaItem(audio.content, v, i)) to 0
    }

    /** Wraps one stream in a small DASH description so the player can seek using its index. */
    private fun dash(
        url: String,
        itag: org.schabi.newpipe.extractor.services.youtube.ItagItem,
        durationSec: Long,
        factory: androidx.media3.datasource.DataSource.Factory,
        v: Video,
        i: StreamInfo,
    ): MediaSource {
        val mpd = YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(url, itag, durationSec)
        val manifest = DashManifestParser().parse(Uri.parse(url), ByteArrayInputStream(mpd.toByteArray(Charsets.UTF_8)))
        return DashMediaSource.Factory(factory).createMediaSource(manifest, mediaItem(url, v, i))
    }
}
