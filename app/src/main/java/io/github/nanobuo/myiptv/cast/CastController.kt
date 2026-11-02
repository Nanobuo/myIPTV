package io.github.nanobuo.myiptv.cast

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import io.github.nanobuo.myiptv.data.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sends channels to a Chromecast / Google TV with the standard Google receiver.
 * Note: the standard receiver cannot send custom HTTP headers (User-Agent, Referer) nor play
 * protected streams, so channels that need them may not work when cast.
 */
class CastController(private val context: Context) {

    private var castContext: CastContext? = null

    private val _deviceName = MutableStateFlow<String?>(null)
    /** Name of the device being cast to, null when not casting. */
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    val isCasting: Boolean get() = _deviceName.value != null

    /**
     * True when Google Play services are present, so Cast can be offered.
     * Cast itself is initialized only when the user taps the Cast button.
     */
    val available: Boolean by lazy {
        runCatching {
            com.google.android.gms.common.GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS
        }.getOrDefault(false)
    }

    /** Routes the Cast dialogs should list, available after [attach]. */
    val routeSelector: androidx.mediarouter.media.MediaRouteSelector?
        get() = runCatching { castContext?.mergedSelector }.getOrNull()

    var onSessionStarted: (() -> Unit)? = null
    var onSessionEnded: (() -> Unit)? = null

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = connected(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = connected(session)
        override fun onSessionEnded(session: CastSession, error: Int) = disconnected()
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    /** Initializes Google Cast (phones and tablets only). Must be called on the main thread. */
    fun attach(): Boolean {
        if (castContext != null) return true
        val cast = runCatching { CastContext.getSharedInstance(context) }.getOrNull() ?: return false
        castContext = cast
        runCatching {
            cast.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
            cast.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let { _deviceName.value = it.castDevice?.friendlyName ?: "Chromecast" }
        }
        return true
    }

    private fun connected(session: CastSession) {
        _deviceName.value = session.castDevice?.friendlyName ?: "Chromecast"
        onSessionStarted?.invoke()
    }

    private fun disconnected() {
        if (_deviceName.value == null) return
        _deviceName.value = null
        onSessionEnded?.invoke()
    }

    /** Plays [channel] on the cast device. */
    fun cast(channel: Channel): Boolean = runCatching { castInternal(channel) }.getOrDefault(false)

    private fun castInternal(channel: Channel): Boolean {
        val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return false
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, channel.name)
            channel.group?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            channel.logo?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val info = MediaInfo.Builder(channel.url)
            .setContentUrl(channel.url)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(contentType(channel))
            .setMetadata(metadata)
            .build()
        client.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build())
        return true
    }

    fun stop() {
        runCatching { castContext?.sessionManager?.endCurrentSession(true) }
    }

    private fun contentType(channel: Channel): String {
        val path = channel.url.substringBefore('?').lowercase()
        return when {
            channel.streamType == "dash" || path.endsWith(".mpd") -> "application/dash+xml"
            channel.streamType == "ss" || path.endsWith(".ism") || path.endsWith("/manifest") -> "application/vnd.ms-sstr+xml"
            path.endsWith(".ts") -> "video/mp2t"
            path.endsWith(".mp4") -> "video/mp4"
            else -> "application/x-mpegurl"
        }
    }
}
