package io.github.nanobuo.myiptv.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import io.github.nanobuo.myiptv.R
import io.github.nanobuo.myiptv.data.Channel

/**
 * Presence on the TV home screen:
 * - the last watched channel in "Continue watching" / "Play next";
 * - a "myIPTV" row with the favorite channels (launchers that show app rows).
 * Selecting an item opens the app directly on that channel (myiptv://channel/<key>).
 * Everything is best effort: launchers may ignore it.
 */
@SuppressLint("RestrictedApi")
object TvHome {
    private const val PREFS = "tv_home"
    private const val KEY_WATCH_NEXT = "watch_next_id"
    private const val KEY_CHANNEL = "channel_id"
    private const val KEY_PROGRAMS = "program_ids"
    private const val KEY_SIGNATURE = "programs_signature"
    private const val MAX_PROGRAMS = 30

    fun channelLink(key: String): Uri = Uri.Builder().scheme("myiptv").authority("channel").appendPath(key).build()

    /** Key of the channel in a myiptv://channel/<key> link, or null. */
    fun keyFromLink(uri: Uri?): String? =
        uri?.takeIf { it.scheme == "myiptv" && it.host == "channel" }?.lastPathSegment

    fun updateWatchNext(context: Context, channel: Channel, programme: String?) = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldId = prefs.getLong(KEY_WATCH_NEXT, -1L)
        if (oldId >= 0) {
            runCatching { context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(oldId), null, null) }
        }
        val builder = WatchNextProgram.Builder()
            .setType(TvContractCompat.WatchNextPrograms.TYPE_CHANNEL)
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
            .setTitle(channel.name)
            .setDescription(programme ?: channel.group.orEmpty())
            .setLive(true)
            .setIntentUri(channelLink(channel.key))
            .setInternalProviderId(channel.key)
        channel.logo?.let {
            builder.setPosterArtUri(Uri.parse(it))
            builder.setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
        }
        val id = PreviewChannelHelper(context).publishWatchNextProgram(builder.build())
        prefs.edit().putLong(KEY_WATCH_NEXT, id).apply()
    }

    /** Publishes the favorite channels (or the first ones) as the app row on the home screen. */
    fun syncChannelRow(context: Context, channels: List<Channel>, favorites: Set<String>) = runCatching {
        val list = channels.filter { it.key in favorites }.ifEmpty { channels }.take(MAX_PROGRAMS)
        if (list.isEmpty()) return@runCatching
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val helper = PreviewChannelHelper(context)

        var channelId = prefs.getLong(KEY_CHANNEL, -1L)
        val exists = channelId >= 0 && runCatching { helper.getPreviewChannel(channelId) }.getOrNull() != null
        val signature = list.joinToString("\n") { it.key + "|" + it.name + "|" + it.logo }.hashCode()
        if (exists && prefs.getInt(KEY_SIGNATURE, 0) == signature) return@runCatching

        if (!exists) {
            val logo = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(256, 256)
            val row = PreviewChannel.Builder()
                .setDisplayName(context.getString(R.string.home_row_title))
                .setAppLinkIntentUri(Uri.parse("myiptv://open"))
                .apply { if (logo != null) setLogo(logo) }
                .build()
            channelId = helper.publishDefaultChannel(row)
            prefs.edit().putLong(KEY_CHANNEL, channelId).apply()
        }

        prefs.getStringSet(KEY_PROGRAMS, emptySet()).orEmpty().forEach { id ->
            id.toLongOrNull()?.let { runCatching { helper.deletePreviewProgram(it) } }
        }
        val ids = list.mapNotNull { ch ->
            val builder = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(TvContractCompat.PreviewPrograms.TYPE_CHANNEL)
                .setTitle(ch.name)
                .setDescription(ch.group.orEmpty())
                .setLive(true)
                .setIntentUri(channelLink(ch.key))
                .setInternalProviderId(ch.key)
            ch.logo?.let {
                builder.setPosterArtUri(Uri.parse(it))
                builder.setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
            }
            runCatching { helper.publishPreviewProgram(builder.build()).toString() }.getOrNull()
        }
        prefs.edit().putStringSet(KEY_PROGRAMS, ids.toSet()).putInt(KEY_SIGNATURE, signature).apply()
    }
}
