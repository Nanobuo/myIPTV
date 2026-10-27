package io.github.nanobuo.myiptv.ui.mobile

import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nanobuo.myiptv.R
import io.github.nanobuo.myiptv.data.Channel
import io.github.nanobuo.myiptv.data.EpgData
import io.github.nanobuo.myiptv.player.TrackOption
import io.github.nanobuo.myiptv.ui.MainViewModel
import io.github.nanobuo.myiptv.ui.Screen
import io.github.nanobuo.myiptv.ui.components.ChannelLogo
import io.github.nanobuo.myiptv.ui.components.ProgrammeProgress
import io.github.nanobuo.myiptv.ui.components.rememberNow
import io.github.nanobuo.myiptv.ui.components.rememberTimeFormatter
import io.github.nanobuo.myiptv.ui.screens.AspectMode
import io.github.nanobuo.myiptv.ui.screens.FullScreenVideo
import io.github.nanobuo.myiptv.ui.screens.StatsPanel
import kotlinx.coroutines.delay

private enum class Sheet { None, Audio, Subtitles, Aspect, Info, Channels }

private const val CONTROLS_TIMEOUT_MS = 4_000L

/**
 * Full screen player for touch screens.
 * Tap: show/hide controls. Swipe up/down: next/previous channel.
 */
@Composable
fun MobilePlayerScreen(vm: MainViewModel, inPip: Boolean, onEnterPip: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val exoPlayer by vm.player.exoPlayer.collectAsStateWithLifecycle()
    val channel = ui.channels.firstOrNull { it.key == ui.currentKey }
    val now by rememberNow(10_000L)
    val formatTime = rememberTimeFormatter()
    val context = LocalContext.current
    val pipSupported = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }
    val invert = settings?.invertUpDown == true

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var statsVisible by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(Sheet.None) }
    var aspectMode by rememberSaveable { mutableStateOf(AspectMode.Fit) }

    fun touch() {
        controlsVisible = true
        interaction++
    }
    LaunchedEffect(ui.currentKey) { touch() }
    LaunchedEffect(interaction, controlsVisible, sheet) {
        if (controlsVisible && sheet == Sheet.None) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    var dragTotal by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(inPip) {
                if (inPip) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        // Swipe up: next channel, swipe down: previous (reversed with "invert").
                        if (kotlin.math.abs(dragTotal) > swipeThreshold) {
                            val forward = (dragTotal < 0) != invert
                            vm.zap(if (forward) 1 else -1)
                        }
                    },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragTotal += amount
                    },
                )
            }
            .pointerInput(inPip) {
                if (inPip) return@pointerInput
                detectTapGestures(onTap = {
                    if (controlsVisible) controlsVisible = false else touch()
                })
            },
    ) {
        FullScreenVideo(exoPlayer, if (inPip) AspectMode.Fit else aspectMode, playerState.videoAspect)

        val error = playerState.error
        if (error != null && !inPip) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text(error, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.player.retry() }) { Text(stringResource(R.string.retry)) }
            }
        } else if (playerState.buffering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(if (inPip) 24.dp else 48.dp), color = Color.White)
        }

        // In the small Picture-in-Picture window only the video is shown.
        AnimatedVisibility(
            visible = statsVisible && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            StatsPanel(vm, Modifier.clickable { statsVisible = false })
        }

        AnimatedVisibility(
            visible = controlsVisible && channel != null && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (channel != null) {
                PlayerControlsOverlay(
                    channel = channel,
                    epg = ui.epg,
                    now = now,
                    formatTime = formatTime,
                    aspectMode = aspectMode,
                    statsVisible = statsVisible,
                    pipSupported = pipSupported,
                    onBack = { vm.back() },
                    onPrevious = {
                        touch()
                        vm.zap(-1)
                    },
                    onNext = {
                        touch()
                        vm.zap(1)
                    },
                    onStats = {
                        touch()
                        statsVisible = !statsVisible
                    },
                    onPip = onEnterPip,
                    onGuide = { vm.navigate(Screen.Guide) },
                    onSheet = { sheet = it },
                )
            }
        }
    }

    if (sheet != Sheet.None && channel != null && !inPip) {
        PlayerSheet(
            sheet = sheet,
            vm = vm,
            channel = channel,
            epg = ui.epg,
            now = now,
            formatTime = formatTime,
            aspectMode = aspectMode,
            onAspect = { aspectMode = it },
            onDismiss = {
                sheet = Sheet.None
                touch()
            },
        )
    }
}

@Composable
private fun PlayerControlsOverlay(
    channel: Channel,
    epg: EpgData,
    now: Long,
    formatTime: (Long) -> String,
    aspectMode: AspectMode,
    statsVisible: Boolean,
    pipSupported: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStats: () -> Unit,
    onPip: () -> Unit,
    onGuide: () -> Unit,
    onSheet: (Sheet) -> Unit,
) {
    val (current, next) = remember(epg, channel.key, now / 30_000) { epg.nowAndNext(channel.key, now) }
    Box(Modifier.fillMaxSize()) {
        // Top: back, channel and programme, stats and PiP.
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
            }
            ChannelLogo(channel, modifier = Modifier.width(64.dp), size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${channel.number}  ${channel.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                current?.let {
                    Text(
                        stringResource(R.string.time_range, formatTime(it.start), formatTime(it.stop)) + "  " + it.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onStats) {
                Icon(
                    Icons.Default.QueryStats,
                    contentDescription = stringResource(R.string.stats_title),
                    tint = if (statsVisible) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
            if (pipSupported) {
                IconButton(onClick = onPip) {
                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = stringResource(R.string.pip), tint = Color.White)
                }
            }
        }

        // Center: previous / next channel.
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(96.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
            )
            FilledTonalIconButton(onClick = onPrevious, colors = colors, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.previous_channel))
            }
            FilledTonalIconButton(onClick = onNext, colors = colors, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.next_channel))
            }
        }

        // Bottom: programme progress and buttons.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (current != null) {
                ProgrammeProgress(current.progressAt(now), Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
            }
            next?.let {
                Text(
                    stringResource(R.string.up_next) + "  " + formatTime(it.start) + "  " + it.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ControlChip(Icons.Default.CalendarMonth, stringResource(R.string.tv_guide), onGuide)
                ControlChip(Icons.Default.Info, stringResource(R.string.info)) { onSheet(Sheet.Info) }
                Spacer(Modifier.weight(1f))
                ControlChip(Icons.AutoMirrored.Filled.List, stringResource(R.string.tab_channels)) { onSheet(Sheet.Channels) }
                ControlChip(Icons.Default.Audiotrack, stringResource(R.string.audio)) { onSheet(Sheet.Audio) }
                ControlChip(Icons.Default.Subtitles, stringResource(R.string.subtitles)) { onSheet(Sheet.Subtitles) }
                ControlChip(Icons.Default.AspectRatio, stringResource(aspectMode.label)) { onSheet(Sheet.Aspect) }
            }
            Text(
                stringResource(R.string.swipe_hint),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun ControlChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun PlayerSheet(
    sheet: Sheet,
    vm: MainViewModel,
    channel: Channel,
    epg: EpgData,
    now: Long,
    formatTime: (Long) -> String,
    aspectMode: AspectMode,
    onAspect: (AspectMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            when (sheet) {
                Sheet.Audio -> {
                    SectionHeader(stringResource(R.string.audio))
                    if (playerState.audioTracks.isEmpty()) {
                        Text(stringResource(R.string.no_tracks), modifier = Modifier.padding(16.dp))
                    }
                    playerState.audioTracks.forEach { track ->
                        OptionItem(track.label, track.selected) {
                            vm.player.selectAudio(track)
                            onDismiss()
                        }
                    }
                }
                Sheet.Subtitles -> {
                    SectionHeader(stringResource(R.string.subtitles))
                    OptionItem(stringResource(R.string.subtitles_off), !playerState.textEnabled) {
                        vm.player.selectText(null)
                        onDismiss()
                    }
                    playerState.textTracks.forEach { track: TrackOption ->
                        OptionItem(track.label, playerState.textEnabled && track.selected) {
                            vm.player.selectText(track)
                            onDismiss()
                        }
                    }
                }
                Sheet.Aspect -> {
                    SectionHeader(stringResource(R.string.aspect_ratio))
                    AspectMode.entries.forEach { mode ->
                        OptionItem(stringResource(mode.label), mode == aspectMode) {
                            onAspect(mode)
                            onDismiss()
                        }
                    }
                }
                Sheet.Info -> ProgrammeInfo(channel, epg, now, formatTime)
                Sheet.Channels -> ChannelsSheetList(vm, onDismiss)
                Sheet.None -> Unit
            }
        }
    }
}

@Composable
private fun OptionItem(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(label) },
        trailingContent = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
    )
}

@Composable
private fun ProgrammeInfo(channel: Channel, epg: EpgData, now: Long, formatTime: (Long) -> String) {
    val (current, next) = remember(epg, channel.key, now / 30_000) { epg.nowAndNext(channel.key, now) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("${channel.number}  ${channel.name}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        if (current == null) {
            Text(stringResource(R.string.no_epg), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(current.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.time_range, formatTime(current.start), formatTime(current.stop)) +
                    "  ·  " + stringResource(R.string.minutes_left, ((current.stop - now) / 60_000).toInt().coerceAtLeast(0)) +
                    (current.category?.let { "  ·  $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ProgrammeProgress(current.progressAt(now), Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text(current.description ?: stringResource(R.string.no_description), style = MaterialTheme.typography.bodyLarge)
        }
        if (next != null) {
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.up_next).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(next.title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.time_range, formatTime(next.start), formatTime(next.stop)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            next.description?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ChannelsSheetList(vm: MainViewModel, onDismiss: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val index = ui.channels.indexOfFirst { it.key == ui.currentKey }
        if (index > 2) listState.scrollToItem(index - 2)
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(420.dp)) {
        items(ui.channels, key = { it.key }) { ch ->
            ListItem(
                modifier = Modifier.clickable {
                    vm.playChannel(ch)
                    onDismiss()
                },
                leadingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ch.number.toString(), fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                        ChannelLogo(ch, modifier = Modifier.width(52.dp), size = 32.dp)
                    }
                },
                headlineContent = {
                    Text(
                        ch.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (ch.key == ui.currentKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                },
            )
        }
    }
}
