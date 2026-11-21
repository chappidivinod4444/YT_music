package app.vidflow.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.vidflow.MainActivity

/** Keeps sound playing with a media notification when the app is in the background. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Next / previous in the notification and on headphones go through the music queue.
        val queued = object : androidx.media3.common.ForwardingPlayer(PlayerHolder.player) {
            override fun getAvailableCommands(): androidx.media3.common.Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT).add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS).add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()

            override fun isCommandAvailable(command: Int): Boolean = when (command) {
                COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                else -> super.isCommandAvailable(command)
            }

            override fun hasNextMediaItem() = true
            override fun hasPreviousMediaItem() = true
            override fun seekToNext() = app.vidflow.core.MusicQueue.next()
            override fun seekToNextMediaItem() = app.vidflow.core.MusicQueue.next()
            override fun seekToPrevious() = app.vidflow.core.MusicQueue.previous()
            override fun seekToPreviousMediaItem() = app.vidflow.core.MusicQueue.previous()
        }
        session = MediaSession.Builder(this, queued)
            .setSessionActivity(open)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
