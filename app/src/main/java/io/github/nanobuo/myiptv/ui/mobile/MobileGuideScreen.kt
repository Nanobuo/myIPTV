package io.github.nanobuo.myiptv.ui.mobile

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nanobuo.myiptv.R
import io.github.nanobuo.myiptv.data.Channel
import io.github.nanobuo.myiptv.data.Programme
import io.github.nanobuo.myiptv.ui.MainViewModel
import io.github.nanobuo.myiptv.ui.components.ChannelLogo
import io.github.nanobuo.myiptv.ui.components.ProgrammeProgress
import io.github.nanobuo.myiptv.ui.components.VideoSurface
import io.github.nanobuo.myiptv.ui.components.rememberNow
import io.github.nanobuo.myiptv.ui.components.rememberTimeFormatter

private const val MINUTE = 60_000L
private const val SLOT = 30 * MINUTE
private const val SPAN = 26 * 60 * MINUTE
private val ROW_HEIGHT = 60.dp
private val DP_PER_MINUTE = 3.2.dp

/** TV guide for touch screens: drag sideways to move in time, up and down for channels. */
@Composable
fun MobileGuideScreen(vm: MainViewModel, padding: PaddingValues) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val playingKey by vm.player.playingKey.collectAsStateWithLifecycle()
    val exoPlayer by vm.player.exoPlayer.collectAsStateWithLifecycle()
    val now by rememberNow(30_000L)
    val formatTime = rememberTimeFormatter()
    val tablet = isTablet()
    val channelColumn = if (tablet) 200.dp else 84.dp

    // The grid starts an hour ago and covers the next day.
    val start = remember { val t = System.currentTimeMillis() - 60 * MINUTE; t - Math.floorMod(t, SLOT) }
    val totalWidth = DP_PER_MINUTE * (SPAN / MINUTE).toFloat()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        scroll.scrollTo(with(density) { (DP_PER_MINUTE * ((now - start - SLOT) / MINUTE).toFloat()).roundToPx() })
    }
    var details by remember { mutableStateOf<Pair<Channel, Programme?>?>(null) }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Text(
            stringResource(R.string.tv_guide),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
        )
        // The channel being played continues at the top.
        val playing = ui.channels.firstOrNull { it.key == playingKey }
        if (playing != null && exoPlayer != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .widthIn(max = if (tablet) 360.dp else 200.dp)
                        .fillMaxWidth(0.5f)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .clickable { vm.playChannel(playing) },
                ) {
                    VideoSurface(exoPlayer, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                val (current, _) = remember(ui.epg, playing.key, now / 60_000) { ui.epg.nowAndNext(playing.key, now) }
                Column(Modifier.weight(1f)) {
                    Text("${playing.number}  ${playing.name}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    current?.let {
                        Text(it.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        ProgrammeProgress(it.progressAt(now), Modifier.fillMaxWidth())
                    }
                }
            }
        }

        if (ui.channels.isEmpty()) {
            MessageView(stringResource(R.string.no_channels))
        } else {
            // Time ruler, scrolled together with every row.
            Row(Modifier.fillMaxWidth().height(28.dp)) {
                Spacer(Modifier.width(channelColumn))
                Box(Modifier.horizontalScroll(scroll)) {
                    Box(Modifier.width(totalWidth).fillMaxHeight()) {
                        for (i in 0 until (SPAN / SLOT).toInt()) {
                            Text(
                                formatTime(start + i * SLOT),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.offset(x = DP_PER_MINUTE * (i * 30).toFloat() + 4.dp).align(Alignment.CenterStart),
                            )
                        }
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(ui.channels, key = { it.key }, contentType = { "row" }) { channel ->
                    GuideRow(
                        channel = channel,
                        programmes = ui.epg.byChannel[channel.key].orEmpty(),
                        start = start,
                        now = now,
                        scroll = scroll,
                        totalWidth = totalWidth,
                        channelColumn = channelColumn,
                        showName = tablet,
                        playing = channel.key == playingKey,
                        onChannel = { vm.playChannel(channel) },
                        onProgramme = { details = channel to it },
                    )
                }
            }
        }
    }

    details?.let { (channel, programme) ->
        ModalBottomSheet(
            onDismissRequest = { details = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("${channel.number}  ${channel.name}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                if (programme != null) {
                    Text(programme.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.time_range, formatTime(programme.start), formatTime(programme.stop)) +
                            "  ·  " + stringResource(R.string.duration_minutes, ((programme.stop - programme.start) / MINUTE).toInt()) +
                            (programme.category?.let { "  ·  $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (programme.isLiveAt(now)) {
                        Spacer(Modifier.height(8.dp))
                        ProgrammeProgress(programme.progressAt(now), Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(programme.description ?: stringResource(R.string.no_description), style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(stringResource(R.string.no_epg), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    details = null
                    vm.playChannel(channel)
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.watch_channel))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    programmes: List<Programme>,
    start: Long,
    now: Long,
    scroll: ScrollState,
    totalWidth: Dp,
    channelColumn: Dp,
    showName: Boolean,
    playing: Boolean,
    onChannel: () -> Unit,
    onProgramme: (Programme?) -> Unit,
) {
    val end = start + SPAN
    val visible = remember(programmes, start) { programmes.filter { it.stop > start && it.start < end } }
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(vertical = 2.dp)) {
        Row(
            Modifier
                .width(channelColumn)
                .fillMaxHeight()
                .clickable(onClick = onChannel)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ChannelLogo(channel, modifier = Modifier.width(56.dp), size = 32.dp)
                Text(
                    channel.number.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (playing) colors.primary else colors.onSurface,
                )
            }
            if (showName) {
                Spacer(Modifier.width(8.dp))
                Text(
                    channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (playing) colors.primary else colors.onSurface,
                )
            }
        }
        Box(Modifier.horizontalScroll(scroll)) {
            Box(Modifier.width(totalWidth).fillMaxHeight()) {
                if (visible.isEmpty()) {
                    ProgrammeBox(title = stringResource(R.string.no_epg), live = false, modifier = Modifier.fillMaxSize()) { onProgramme(null) }
                }
                visible.forEach { p ->
                    val from = maxOf(p.start, start)
                    val to = minOf(p.stop, end)
                    ProgrammeBox(
                        title = p.title,
                        live = p.isLiveAt(now),
                        modifier = Modifier
                            .offset(x = DP_PER_MINUTE * ((from - start) / MINUTE).toFloat())
                            .width(DP_PER_MINUTE * ((to - from) / MINUTE).toFloat())
                            .fillMaxHeight(),
                    ) { onProgramme(p) }
                }
                // "Now" marker.
                Box(
                    Modifier
                        .offset(x = DP_PER_MINUTE * ((now - start) / MINUTE).toFloat())
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(colors.error),
                )
            }
        }
    }
}

@Composable
private fun ProgrammeBox(title: String, live: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (live) colors.secondaryContainer else colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            color = if (live) colors.onSecondaryContainer else colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
