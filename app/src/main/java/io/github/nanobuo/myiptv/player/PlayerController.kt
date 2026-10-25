@file:OptIn(UnstableApi::class)

package io.github.nanobuo.myiptv.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import io.github.nanobuo.myiptv.R
import io.github.nanobuo.myiptv.data.AppSettings
import io.github.nanobuo.myiptv.data.Channel
import io.github.nanobuo.myiptv.data.M3uParser
import io.github.nanobuo.myiptv.data.Network
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import java.util.Locale

data class TrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean,
)

data class PlayerUiState(
    val buffering: Boolean = false,
    val error: String? = null,
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    val textEnabled: Boolean = false,
    val videoQuality: String? = null,
)

/**
 * Owns the [ExoPlayer] instance. The player is created when playback is visible and released when
 * the app goes to background, as recommended for TV devices.
 */
class PlayerController(
    private val context: Context,
    private val client: OkHttpClient,
) {
    var player: ExoPlayer? = null
        private set

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var current: Channel? = null
    private var attempts: List<String?> = listOf(null)
    private var attemptIndex = 0
    private var retryCount = 0

    var preferredAudio: String = AppSettings.AUDIO_SYSTEM
        set(value) {
            field = value
            player?.let(::applyAudioPreference)
        }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
            if (playbackState == Player.STATE_READY) {
                retryCount = 0
                _state.update { it.copy(error = null) }
            }
        }

        override fun onTracksChanged(tracks: Tracks) = updateTracks(tracks)

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            val h = videoSize.height
            val quality = when {
                h <= 0 -> null
                h >= 2000 -> "4K"
                h >= 1000 -> "FHD"
                h >= 700 -> "HD"
                else -> "SD"
            }
            _state.update { it.copy(videoQuality = quality) }
        }

        override fun onPlayerError(error: PlaybackException) = handleError(error)
    }

    fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val p = ExoPlayer.Builder(context, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        p.addListener(listener)
        p.playWhenReady = true
        applyAudioPreference(p)
        player = p
        current?.let { prepare(it) }
        return p
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        player?.removeListener(listener)
        player?.release()
        player = null
    }

    fun play(channel: Channel) {
        handler.removeCallbacksAndMessages(null)
        current = channel
        attempts = buildAttempts(channel)
        attemptIndex = 0
        retryCount = 0
        _state.value = PlayerUiState(buffering = true)
        player?.let { p ->
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .clearOverrides()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
        prepare(channel)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        current = null
        player?.stop()
        player?.clearMediaItems()
    }

    fun retry() {
        current?.let {
            retryCount = 0
            prepare(it)
        }
    }

    private fun prepare(channel: Channel) {
        val p = player ?: return
        _state.update { it.copy(error = null, buffering = true) }
        p.setMediaSource(buildMediaSource(channel, attempts[attemptIndex]))
        p.prepare()
        p.play()
    }

    // ---- Track selection -------------------------------------------------------------------

    fun selectAudio(option: TrackOption) {
        val p = player ?: return
        val group = p.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
    }

    fun selectText(option: TrackOption?) {
        val p = player ?: return
        val builder = p.trackSelectionParameters.buildUpon()
        if (option == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val group = p.currentTracks.groups.getOrNull(option.groupIndex) ?: return
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        }
        p.trackSelectionParameters = builder.build()
    }

    private fun updateTracks(tracks: Tracks) {
        val audio = mutableListOf<TrackOption>()
        val text = mutableListOf<TrackOption>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> audio += TrackOption(
                        groupIndex, i, audioLabel(format, audio.size + 1), group.isTrackSelected(i),
                    )
                    C.TRACK_TYPE_TEXT -> text += TrackOption(
                        groupIndex, i, textLabel(format, text.size + 1), group.isTrackSelected(i),
                    )
                }
            }
        }
        val textEnabled = player?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == false &&
            text.any { it.selected }
        _state.update { it.copy(audioTracks = audio, textTracks = text, textEnabled = textEnabled) }
    }

    private fun languageName(format: Format): String? {
        val lang = format.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED && it != "und" }
            ?: return null
        val locale = Locale.forLanguageTag(lang)
        return locale.getDisplayLanguage(Locale.getDefault())
            .takeIf { it.isNotBlank() && !it.equals(lang, ignoreCase = true) }
            ?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            ?: lang.uppercase()
    }

    private fun audioLabel(format: Format, position: Int): String {
        val name = format.label?.takeIf { it.isNotBlank() } ?: languageName(format)
            ?: context.getString(R.string.track_number, position)
        val details = listOfNotNull(
            when (format.sampleMimeType) {
                MimeTypes.AUDIO_AC3 -> "Dolby Digital"
                MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital+"
                MimeTypes.AUDIO_AC4 -> "Dolby AC-4"
                MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD -> "DTS"
                MimeTypes.AUDIO_AAC -> "AAC"
                MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MPEG"
                MimeTypes.AUDIO_OPUS -> "Opus"
                else -> null
            },
            when (format.channelCount) {
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> null
            },
        )
        val label = if (details.isEmpty()) name else "$name · ${details.joinToString(" ")}"
        return if ((format.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO) != 0) "$label (AD)" else label
    }

    private fun textLabel(format: Format, position: Int): String {
        val name = format.label?.takeIf { it.isNotBlank() } ?: languageName(format)
            ?: context.getString(R.string.track_number, position)
        return if ((format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) "$name (forced)" else name
    }

    private fun applyAudioPreference(p: ExoPlayer) {
        val builder = p.trackSelectionParameters.buildUpon()
        when (preferredAudio) {
            AppSettings.AUDIO_SYSTEM -> builder.setPreferredAudioLanguage(Locale.getDefault().language)
            AppSettings.AUDIO_ORIGINAL -> builder.setPreferredAudioLanguage(null).setPreferredAudioRoleFlags(C.ROLE_FLAG_MAIN)
            else -> builder.setPreferredAudioLanguage(preferredAudio)
        }
        p.trackSelectionParameters = builder.build()
    }

    // ---- Media sources ---------------------------------------------------------------------

    private fun buildAttempts(channel: Channel): List<String?> {
        val hint = channel.streamType ?: guessType(channel.url)
        return listOf(hint, TYPE_HLS, TYPE_DASH, TYPE_PROGRESSIVE).distinct()
    }

    private fun guessType(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> TYPE_HLS
            path.endsWith(".mpd") -> TYPE_DASH
            path.endsWith(".ism") || path.endsWith("/manifest") -> TYPE_SS
            path.endsWith(".ts") || path.endsWith(".mp4") || path.endsWith(".mkv") -> TYPE_PROGRESSIVE
            else -> null
        }
    }

    private fun buildMediaSource(channel: Channel, type: String?): MediaSource {
        val headers = LinkedHashMap(channel.headers)
        val userAgentKey = headers.keys.firstOrNull { it.equals(M3uParser.HEADER_USER_AGENT, ignoreCase = true) }
        val userAgent = userAgentKey?.let { headers.remove(it) } ?: Network.defaultUserAgent

        val dataSourceFactory = OkHttpDataSource.Factory(client)
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(headers)

        val itemBuilder = MediaItem.Builder()
            .setUri(channel.url)
            .setMediaId(channel.key)
        when (type) {
            TYPE_HLS -> itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            TYPE_DASH -> itemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            TYPE_SS -> itemBuilder.setMimeType(MimeTypes.APPLICATION_SS)
            TYPE_PROGRESSIVE -> itemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
        }
        itemBuilder.setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder().setMaxPlaybackSpeed(1.02f).build(),
        )

        val factory = DefaultMediaSourceFactory(dataSourceFactory)
        val drm = channel.drm
        if (drm != null) {
            val uuid = when (drm.scheme) {
                "widevine" -> C.WIDEVINE_UUID
                "playready" -> C.PLAYREADY_UUID
                else -> C.CLEARKEY_UUID
            }
            val license = drm.license?.trim().orEmpty()
            val localKeys = if (uuid == C.CLEARKEY_UUID) clearKeyJson(license) else null
            if (localKeys != null) {
                val manager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(LocalMediaDrmCallback(localKeys.toByteArray()))
                factory.setDrmSessionManagerProvider { manager }
            } else if (license.startsWith("http", ignoreCase = true)) {
                val licenseUrl = license.substringBefore('|')
                val licenseHeaders = license.substringAfter('|', "").substringBefore('|')
                    .let { M3uParser.parseHeaderString(it) }
                itemBuilder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(uuid)
                        .setLicenseUri(licenseUrl)
                        .setLicenseRequestHeaders(licenseHeaders)
                        .build(),
                )
                val provider = DefaultDrmSessionManagerProvider()
                provider.setDrmHttpDataSourceFactory(dataSourceFactory)
                factory.setDrmSessionManagerProvider(provider)
            }
        }
        return factory.createMediaSource(itemBuilder.build())
    }

    /** Converts `kid:key` pairs (hex or base64) into a ClearKey JSON license. */
    private fun clearKeyJson(license: String): String? {
        if (license.isEmpty() || license.startsWith("http", ignoreCase = true)) return null
        if (license.startsWith("{")) return license
        val keys = license.split(',', ';').mapNotNull { pair ->
            val parts = pair.trim().split(':')
            if (parts.size != 2) return@mapNotNull null
            """{"kty":"oct","kid":"${toBase64Url(parts[0])}","k":"${toBase64Url(parts[1])}"}"""
        }
        if (keys.isEmpty()) return null
        return """{"keys":[${keys.joinToString(",")}],"type":"temporary"}"""
    }

    private fun toBase64Url(value: String): String {
        val v = value.trim()
        val bytes = if (v.length % 2 == 0 && v.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            ByteArray(v.length / 2) { i -> v.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } else {
            return v.replace('+', '-').replace('/', '_').trimEnd('=')
        }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    // ---- Errors ----------------------------------------------------------------------------

    private fun handleError(error: PlaybackException) {
        val p = player ?: return
        val channel = current ?: return
        when {
            error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                p.seekToDefaultPosition()
                p.prepare()
            }

            error.errorCode in FORMAT_ERRORS && attemptIndex < attempts.lastIndex -> {
                attemptIndex++
                prepare(channel)
            }

            retryCount < MAX_RETRIES -> {
                retryCount++
                _state.update { it.copy(buffering = true) }
                handler.postDelayed({ if (current == channel) prepare(channel) }, 1500L * retryCount)
            }

            else -> {
                val message = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        context.getString(R.string.error_http, error.cause?.message ?: "")
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        context.getString(R.string.error_network)
                    PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
                    PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
                    PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DRM_UNSPECIFIED ->
                        context.getString(R.string.error_drm)
                    else -> context.getString(R.string.error_generic, error.errorCodeName)
                }
                _state.update { it.copy(error = message, buffering = false) }
            }
        }
    }

    private companion object {
        const val TYPE_HLS = "hls"
        const val TYPE_DASH = "dash"
        const val TYPE_SS = "ss"
        const val TYPE_PROGRESSIVE = "progressive"
        const val MAX_RETRIES = 3

        val FORMAT_ERRORS = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        )
    }
}
