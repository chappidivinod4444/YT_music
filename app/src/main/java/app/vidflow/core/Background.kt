package app.vidflow.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.vidflow.MainActivity
import app.vidflow.R
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val CH_DOWNLOADS = "downloads"
private const val CH_UPLOADS = "uploads"

private fun channels(c: Context) {
    val nm = c.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CH_DOWNLOADS, "Downloads", NotificationManager.IMPORTANCE_LOW))
    nm.createNotificationChannel(NotificationChannel(CH_UPLOADS, "New uploads", NotificationManager.IMPORTANCE_DEFAULT))
}

private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
    c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/** Runs downloads one at a time with a progress notification, even if you leave the app. */
class DownloadService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private var running = 0

    companion object {
        fun start(c: Context, video: Video, height: Int) {
            val i = Intent(c, DownloadService::class.java)
                .putExtra("video", Library.videoJson(video).toString())
                .putExtra("height", height)
            ContextCompat.startForegroundService(c, i)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channels(this)
        ServiceCompat.startForeground(
            this, 7, progress("Preparing download…", 0),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        val json = intent?.getStringExtra("video") ?: return START_NOT_STICKY
        val video = runCatching { Library.videoFrom(org.json.JSONObject(json)) }.getOrNull() ?: return START_NOT_STICKY
        val height = intent.getIntExtra("height", 720)
        running++
        worker.execute {
            val id = java.util.UUID.randomUUID().toString()
            val watcher = Thread {
                try {
                    while (true) {
                        val job = Downloads.jobs.value.firstOrNull { it.id == id }
                        if (job != null) {
                            getSystemService(NotificationManager::class.java).notify(7, progress("${job.title}\n${job.status}", (job.progress * 100).toInt()))
                            if (job.done || job.failed) break
                        }
                        Thread.sleep(700)
                    }
                } catch (_: InterruptedException) {
                }
            }.apply { start() }
            Downloads.run(this, video, height, id)
            watcher.interrupt()
            val job = Downloads.jobs.value.firstOrNull { it.id == id }
            getSystemService(NotificationManager::class.java).notify(
                id.hashCode(),
                NotificationCompat.Builder(this, CH_DOWNLOADS)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(if (job?.failed == true) "Download failed" else "Downloaded")
                    .setContentText(job?.title + if (job?.failed == true) " — ${job.status}" else "")
                    .setContentIntent(openApp(this))
                    .setAutoCancel(true)
                    .build(),
            )
            running--
            if (running <= 0) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun progress(text: String, percent: Int): Notification =
        NotificationCompat.Builder(this, CH_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(this))
            .build()
}

/** Checks your channels every few hours and notifies you about new videos. */
class UploadsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        if (Library.subscriptions.value.isEmpty()) return Result.success()
        return try {
            Feed.refresh()
            val prefs = c.getSharedPreferences("notif", Context.MODE_PRIVATE)
            val last = prefs.getLong("notified", System.currentTimeMillis() - 6 * 3600 * 1000)
            val fresh = Feed.videos.value.orEmpty().filter { (it.uploadedAtMillis ?: 0) > last && !it.short }.take(5)
            if (fresh.isNotEmpty()) {
                channels(c)
                val nm = c.getSystemService(NotificationManager::class.java)
                fresh.forEach { v ->
                    nm.notify(
                        v.url.hashCode(),
                        NotificationCompat.Builder(c, CH_UPLOADS)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle(v.channel)
                            .setContentText(v.title)
                            .setContentIntent(
                                PendingIntent.getActivity(
                                    c, v.url.hashCode(),
                                    Intent(c, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse(v.url)),
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                                )
                            )
                            .setAutoCancel(true)
                            .build(),
                    )
                }
                prefs.edit().putLong("notified", System.currentTimeMillis()).apply()
            }
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        fun schedule(c: Context, on: Boolean) {
            val wm = WorkManager.getInstance(c)
            if (!on) {
                wm.cancelUniqueWork("uploads")
                return
            }
            val req = PeriodicWorkRequestBuilder<UploadsWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork("uploads", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
