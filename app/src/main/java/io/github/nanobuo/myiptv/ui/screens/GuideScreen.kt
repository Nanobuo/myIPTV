package io.github.nanobuo.myiptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.nanobuo.myiptv.R
import io.github.nanobuo.myiptv.data.Channel
import io.github.nanobuo.myiptv.data.Programme
import io.github.nanobuo.myiptv.ui.MainViewModel
import io.github.nanobuo.myiptv.ui.components.ChannelLogo
import io.github.nanobuo.myiptv.ui.components.ProgrammeProgress
import io.github.nanobuo.myiptv.ui.components.VideoSurface
import io.github.nanobuo.myiptv.ui.components.rememberNow
import io.github.nanobuo.myiptv.ui.components.rememberTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MINUTE = 60_000L
private const val SLOT = 30 * MINUTE
private const val WINDOW = 4 * SLOT
private const val MAX_PAST = 6 * 60 * MINUTE
private val CHANNEL_COLUMN = 230.dp
private val ROW_HEIGHT = 56.dp

private fun floorToSlot(time: Long) = time - Math.floorMod(time, SLOT)

/** Programmes of [list] overlapping [from, to). */
private fun List<Programme>.inWindow(from: Long, to: Long) = filter { it.stop > from && it.start < to }

private fun List<Programme>.at(time: Long) = firstOrNull { time >= it.start && time < it.stop }

/**
 * TV guide grid. The channel being played keeps playing in the top-left corner.
 * Navigation: ▲▼ channels, ◀▶ programmes, OK watches the channel full screen.
 */
@Composable
fun GuideScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val exoPlayer by vm.player.exoPlayer.collectAsStateWithLifecycle()
    val playingKey by vm.player.playingKey.collectAsStateWithLifecycle()
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val channels = ui.channels
    val now by rememberNow(30_000L)
    val formatTime = rememberTimeFormatter()

    var row by rememberSaveable {
        mutableIntStateOf(channels.indexOfFirst { it.key == ui.currentKey }.coerceAtLeast(0))
    }
    var windowStart by rememberSaveable { mutableLongStateOf(floorToSlot(System.currentTimeMillis())) }
    var cursor by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    if (channels.isEmpty()) {
        MessageBox(stringResource(R.string.no_channels))
        return
    }
    val safeRow = row.coerceIn(0, channels.lastIndex)
    val focusedChannel = channels[safeRow]
    val focusedList = ui.epg.byChannel[focusedChannel.key].orEmpty()
    val focusedProgramme = focusedList.at(maxOf(cursor, windowStart))

    LaunchedEffect(Unit) {
        listState.scrollToItem((safeRow - 2).coerceAtLeast(0))
        runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(safeRow) {
        val visible = listState.layoutInfo.visibleItemsInfo
        val first = visible.firstOrNull()?.index ?: 0
        val last = visible.lastOrNull()?.index ?: 0
        if (safeRow <= first || safeRow >= last) {
            listState.scrollToItem((safeRow - 2).coerceAtLeast(0))
        }
    }

    fun moveTo(time: Long) {
        val minTime = floorToSlot(now - MAX_PAST)
        val t = maxOf(time, minTime)
        cursor = t
        if (t < windowStart) windowStart = floorToSlot(t)
        if (t >= windowStart + WINDOW) windowStart = floorToSlot(t)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 24.dp)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val anchor = maxOf(cursor, windowStart)
                when (event.key) {
                    Key.DirectionUp -> row = (safeRow - 1).coerceAtLeast(0)
                    Key.DirectionDown -> row = (safeRow + 1).coerceAtMost(channels.lastIndex)
                    Key.ChannelUp, Key.PageUp -> row = (safeRow - 6).coerceAtLeast(0)
                    Key.ChannelDown, Key.PageDown -> row = (safeRow + 6).coerceAtMost(channels.lastIndex)
                    Key.DirectionLeft -> {
                        val current = focusedList.at(anchor)
                        val previous = focusedList.lastOrNull { it.stop <= (current?.start ?: anchor) && it.start < anchor }
                        moveTo(previous?.let { maxOf(it.start, it.stop - SLOT) } ?: (anchor - SLOT))
                    }
                    Key.DirectionRight -> {
                        val current = focusedList.at(anchor)
                        val next = focusedList.firstOrNull { it.start >= (current?.stop ?: (anchor + 1)) }
                        moveTo(next?.start ?: (anchor + SLOT))
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> vm.playChannel(focusedChannel)
                    else -> return@onKeyEvent false
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        // ---- Top: mini player and programme details ----------------------------------------
        Row(Modifier.height(190.dp).fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
            ) {
                val showVideo = playingKey != null && exoPlayer != null
                if (showVideo) {
                    VideoSurface(exoPlayer, Modifier.fillMaxSize())
                } else {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChannelLogo(focusedChannel, Modifier.fillMaxWidth(0.6f), size = 80.dp)
                    }
                }
                if (showVideo && playerState.buffering) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${focusedChannel.number}  ${focusedChannel.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                if (focusedProgramme != null) {
                    Text(
                        focusedProgramme.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        dayLabel(focusedProgramme.start, now) + "  " +
                            stringResource(R.string.time_range, formatTime(focusedProgramme.start), formatTime(focusedProgramme.stop)) +
                            "  ·  " + stringResource(R.string.duration_minutes, ((focusedProgramme.stop - focusedProgramme.start) / MINUTE).toInt()) +
                            (focusedProgramme.category?.let { "  ·  $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (focusedProgramme.isLiveAt(now)) {
                        Spacer(Modifier.height(6.dp))
                        ProgrammeProgress(focusedProgramme.progressAt(now), Modifier.fillMaxWidth(0.5f))
                    }
                    focusedProgramme.description?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(
                        stringResource(R.string.no_epg),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.guide_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Grid ------------------------------------------------------------------------
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val timelineWidth = maxWidth - CHANNEL_COLUMN
            Column {
                TimeHeader(windowStart, now, timelineWidth, formatTime)
                Box {
                    LazyColumn(state = listState, userScrollEnabled = false) {
                        itemsIndexed(channels, key = { _, ch -> ch.key }, contentType = { _, _ -> "row" }) { index, channel ->
                            GuideRow(
                                channel = channel,
                                programmes = ui.epg.byChannel[channel.key].orEmpty(),
                                windowStart = windowStart,
                                timelineWidth = timelineWidth,
                                focusedTime = if (index == safeRow) maxOf(cursor, windowStart) else null,
                                isPlaying = channel.key == playingKey,
                                now = now,
                            )
                        }
                    }
                    // "Now" marker across the grid.
                    if (now in windowStart until windowStart + WINDOW) {
                        Box(
                            Modifier
                                .offset(x = CHANNEL_COLUMN + timelineWidth * ((now - windowStart).toFloat() / WINDOW))
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.error),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun dayLabel(time: Long, now: Long): String {
    val dayFormat = remember { SimpleDateFormat("yyyyMMdd", Locale.ROOT) }
    if (dayFormat.format(Date(time)) == dayFormat.format(Date(now))) return stringResource(R.string.today)
    val format = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    return format.format(Date(time)).replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

@Composable
private fun TimeHeader(windowStart: Long, now: Long, timelineWidth: Dp, formatTime: (Long) -> String) {
    Row(Modifier.height(32.dp).fillMaxWidth()) {
        Text(
            dayLabel(windowStart, now),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(CHANNEL_COLUMN).padding(start = 8.dp),
        )
        Box(Modifier.width(timelineWidth)) {
            for (i in 0 until (WINDOW / SLOT).toInt()) {
                Text(
                    formatTime(windowStart + i * SLOT),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.offset(x = timelineWidth * (i.toFloat() * SLOT / WINDOW) + 6.dp),
                )
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    programmes: List<Programme>,
    windowStart: Long,
    timelineWidth: Dp,
    focusedTime: Long?,
    isPlaying: Boolean,
    now: Long,
) {
    val windowEnd = windowStart + WINDOW
    val visible = remember(programmes, windowStart) { programmes.inWindow(windowStart, windowEnd) }
    val colors = MaterialTheme.colorScheme
    Row(Modifier.height(ROW_HEIGHT).fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            Modifier
                .width(CHANNEL_COLUMN)
                .fillMaxHeight()
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (focusedTime != null) colors.surfaceVariant else Color.Transparent)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                channel.number.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isPlaying) colors.primary else colors.onSurface,
                modifier = Modifier.width(40.dp),
            )
            ChannelLogo(channel, Modifier.width(52.dp), size = 34.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                channel.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) colors.primary else colors.onSurface,
            )
        }
        Box(Modifier.width(timelineWidth).fillMaxHeight()) {
            if (visible.isEmpty()) {
                ProgrammeCell(
                    title = stringResource(R.string.no_epg),
                    focused = focusedTime != null,
                    live = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            visible.forEach { p ->
                val start = maxOf(p.start, windowStart)
                val end = minOf(p.stop, windowEnd)
                ProgrammeCell(
                    title = p.title,
                    focused = focusedTime != null && focusedTime >= p.start && focusedTime < p.stop,
                    live = p.isLiveAt(now),
                    modifier = Modifier
                        .offset(x = timelineWidth * ((start - windowStart).toFloat() / WINDOW))
                        .width(timelineWidth * ((end - start).toFloat() / WINDOW))
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun ProgrammeCell(title: String, focused: Boolean, live: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        focused -> colors.primary
        live -> colors.surfaceVariant
        else -> colors.surface
    }
    Box(
        modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) colors.onPrimary else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
