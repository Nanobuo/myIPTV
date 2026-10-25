package io.github.nanobuo.myiptv.data

import kotlinx.serialization.Serializable

/** Where a playlist comes from. */
@Serializable
enum class SourceType { URL, CONTENT_URI, FILE_PATH }

/** A playlist configured by the user. */
@Serializable
data class PlaylistSource(
    val id: String,
    val name: String,
    val type: SourceType,
    val location: String,
    /** Optional EPG (XMLTV) address, in addition to the one declared in the playlist. */
    val epgUrl: String? = null,
    /** Optional User-Agent used to download the playlist and as fallback for channels. */
    val userAgent: String? = null,
)

data class DrmInfo(
    /** "clearkey", "widevine" or "playready". */
    val scheme: String,
    /** License server URL, "kid:key" pairs or a ClearKey JSON. */
    val license: String?,
)

data class Channel(
    /** Stable identifier used for favorites and "last channel". */
    val key: String,
    val name: String,
    /** Number shown to the user: the LCN when available. */
    val number: Int,
    val hasLcn: Boolean,
    val tvgId: String?,
    val tvgName: String?,
    val logo: String?,
    val group: String?,
    val url: String,
    /** HTTP headers to send (User-Agent, Referer, ...). */
    val headers: Map<String, String>,
    val drm: DrmInfo?,
    /** Optional hint: "hls", "dash", "ss" or null to detect automatically. */
    val streamType: String?,
)

data class Playlist(
    val channels: List<Channel>,
    val epgUrls: List<String>,
)

data class Programme(
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
    val category: String?,
) {
    fun isLiveAt(time: Long) = time in start until stop
    fun progressAt(time: Long): Float =
        if (stop <= start) 0f else ((time - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
}

/** EPG data indexed by [Channel.key], each list sorted by start time. */
class EpgData(val byChannel: Map<String, List<Programme>>) {

    fun nowAndNext(channelKey: String, time: Long = System.currentTimeMillis()): Pair<Programme?, Programme?> {
        val list = byChannel[channelKey] ?: return null to null
        var idx = list.binarySearchBy(time) { it.start }
        if (idx < 0) idx = -idx - 2 // last programme starting before `time`
        val current = list.getOrNull(idx)?.takeIf { it.isLiveAt(time) }
        val next = list.getOrNull(idx + 1)
        return current to next
    }

    val isEmpty get() = byChannel.isEmpty()

    companion object {
        val EMPTY = EpgData(emptyMap())
    }
}
