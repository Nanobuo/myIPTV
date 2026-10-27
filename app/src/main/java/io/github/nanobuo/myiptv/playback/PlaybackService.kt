@file:OptIn(UnstableApi::class)

package io.github.nanobuo.myiptv.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.nanobuo.myiptv.MainActivity
import io.github.nanobuo.myiptv.MyIptvApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Phones and tablets: keeps the channel audio playing with the screen off or the app in
 * background, with the media notification and lock screen controls.
 * The player itself stays owned by [io.github.nanobuo.myiptv.player.PlayerController].
 */
class PlaybackService : MediaSessionService() {

    private val scope = MainScope()
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val controller = (application as MyIptvApp).container.player
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Follow the player instance: it is recreated when the app comes back from background.
        scope.launch {
            controller.exoPlayer.collect { player ->
                session?.let {
                    removeSession(it)
                    it.release()
                }
                session = player?.let {
                    MediaSession.Builder(this@PlaybackService, it)
                        .setSessionActivity(openApp)
                        .build()
                        .also(::addSession)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The app was closed from the recent apps: stop playing.
        (application as MyIptvApp).container.player.stop()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }
}
