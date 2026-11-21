package app.vidflow.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.vidflow.core.Video
import app.vidflow.core.Yt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Silent preview on Home: when you stop scrolling, the video in view starts playing
 * without sound, like YouTube. Uses its own small player so it never disturbs what
 * you're actually watching or listening to.
 */
object Preview {
    private var exo: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    /** The video being previewed, and whether its first picture is on screen yet. */
    val url = MutableStateFlow<String?>(null)
    val showing = MutableStateFlow(false)
    val position = MutableStateFlow(0L)

    fun player(): ExoPlayer = exo ?: ExoPlayer.Builder(PlayerHolder.appContext())
        .setLoadControl(
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(4_000, 12_000, 400, 800).build()
        )
        .build().also { p ->
            p.volume = 0f
            p.repeatMode = Player.REPEAT_MODE_OFF
            p.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    showing.value = true
                }
            })
            exo = p
        }

    fun start(v: Video) {
        if (url.value == v.url) return
        stop()
        // Don't compete with a video or song that's already playing.
        if (PlayerHolder.player.isPlaying) return
        url.value = v.url
        job = scope.launch {
            try {
                val info = withContext(Dispatchers.IO) { Yt.stream(v.url) }
                val source = withContext(Dispatchers.IO) { PlayerHolder.previewSource(v, info) } ?: return@launch
                if (url.value != v.url) return@launch
                val p = player()
                p.setMediaSource(source)
                p.prepare()
                p.play()
                while (url.value == v.url) {
                    position.value = p.currentPosition
                    kotlinx.coroutines.delay(500)
                }
            } catch (_: Throwable) {
            }
        }
    }

    /** Where the preview got to, so opening the video can carry on from there. */
    fun positionFor(videoUrl: String): Long? =
        if (url.value == videoUrl && showing.value) exo?.currentPosition?.takeIf { it > 2_000 } else null

    fun stop() {
        job?.cancel()
        job = null
        url.value = null
        showing.value = false
        position.value = 0L
        exo?.let { it.stop(); it.clearMediaItems() }
    }
}
