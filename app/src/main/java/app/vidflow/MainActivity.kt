package app.vidflow

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.vidflow.core.Library
import app.vidflow.core.MusicHistory
import app.vidflow.core.MusicQueue
import app.vidflow.core.Video
import app.vidflow.core.Yt
import app.vidflow.player.PlayerHolder
import app.vidflow.ui.MusicApp
import app.vidflow.ui.MusicTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VidflowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Yt.init()
        Library.init(this)
        app.vidflow.core.GoogleAccount.init(this)
        app.vidflow.core.Prefs.init(this)
        app.vidflow.core.Saved.init(this)
        MusicHistory.init(this)
        coil3.SingletonImageLoader.setSafe { ctx ->
            coil3.ImageLoader.Builder(ctx)
                .components { add(coil3.network.okhttp.OkHttpNetworkFetcherFactory()) }
                .crossfade(true)
                .build()
        }
        PlayerHolder.init(this)
        MusicQueue.start()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MusicTheme { MusicApp() } }
        handleShare(intent)
    }

    override fun onStart() {
        super.onStart()
        PlayerHolder.connectSession(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !getSharedPreferences("settings", MODE_PRIVATE).getBoolean("askedNotif", false)
        ) {
            // Needed for the play/pause controls while music plays in the background.
            getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("askedNotif", true).apply()
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** A YouTube / YouTube Music link shared or opened here: play it. */
    private fun handleShare(intent: Intent?) {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: intent?.dataString ?: return
        val url = Regex("https?://\\S+").find(text)?.value ?: return
        val id = Yt.videoId(url) ?: return
        CoroutineScope(Dispatchers.Main).launch {
            val v = runCatching {
                withContext(Dispatchers.IO) {
                    val i = Yt.stream("https://www.youtube.com/watch?v=$id")
                    Video(
                        url = i.url, title = i.name, thumbnail = i.thumbnails.maxByOrNull { it.height }?.url,
                        channel = i.uploaderName ?: "", channelUrl = i.uploaderUrl, channelAvatar = null,
                        durationSec = i.duration, views = i.viewCount, uploaded = null, uploadedAtMillis = null,
                        live = false, short = false,
                    )
                }
            }.getOrNull() ?: return@launch
            MusicQueue.startRadio(v)
        }
    }
}
