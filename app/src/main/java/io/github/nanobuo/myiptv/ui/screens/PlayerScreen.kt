@file:OptIn(UnstableApi::class)

package io.github.nanobuo.myiptv.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.produceState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import io.github.nanobuo.myiptv.R
import io.github.nanobuo.myiptv.data.Channel
import io.github.nanobuo.myiptv.data.EpgData
import io.github.nanobuo.myiptv.player.PlayerUiState
import io.github.nanobuo.myiptv.player.TrackOption
import io.github.nanobuo.myiptv.player.StreamStats
import io.github.nanobuo.myiptv.ui.MainViewModel
import io.github.nanobuo.myiptv.ui.Screen
import io.github.nanobuo.myiptv.ui.components.VideoSurface
import io.github.nanobuo.myiptv.ui.theme.appListItemGlow
import io.github.nanobuo.myiptv.ui.components.ChannelLogo
import io.github.nanobuo.myiptv.ui.components.ProgrammeProgress
import io.github.nanobuo.myiptv.ui.components.rememberNow
import io.github.nanobuo.myiptv.ui.components.rememberTimeFormatter
import kotlinx.coroutines.delay
import java.util.Locale

private enum class Overlay { None, Channels, Tracks }

private const val BANNER_TIMEOUT_MS = 5_000L
private const val NUMBER_INPUT_TIMEOUT_MS = 1_800L

@Composable
fun PlayerScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val channel = ui.channels.firstOrNull { it.key == ui.currentKey }
    val now by rememberNow(10_000L)
    val formatTime = rememberTimeFormatter()

    // The player instance is owned by the activity, so it survives screen changes.
    val exoPlayer by vm.player.exoPlayer.collectAsStateWithLifecycle()

    var overlay by remember { mutableStateOf(Overlay.None) }
    var bannerVisible by remember { mutableStateOf(true) }
    var statsVisible by remember { mutableStateOf(false) }
    var bannerToken by remember { mutableIntStateOf(0) }
    var numberInput by remember { mutableStateOf("") }
    var aspectMode by rememberSaveable { mutableStateOf(AspectMode.Fit) }

    fun showBanner() {
        bannerVisible = true
        bannerToken++
    }

    LaunchedEffect(ui.currentKey) { showBanner() }
    LaunchedEffect(bannerToken, bannerVisible, statsVisible) {
        if (bannerVisible && !statsVisible) {
            delay(BANNER_TIMEOUT_MS)
            bannerVisible = false
        }
    }
    LaunchedEffect(numberInput) {
        if (numberInput.isNotEmpty()) {
            delay(NUMBER_INPUT_TIMEOUT_MS)
            numberInput.toIntOrNull()?.let { vm.playNumber(it) }
            numberInput = ""
        }
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(overlay) {
        if (overlay == Overlay.None) {
            withFrameNanos { }
            runCatching { rootFocus.requestFocus() }
        }
    }

    BackHandler(enabled = overlay != Overlay.None || numberInput.isNotEmpty() || bannerVisible || statsVisible) {
        when {
            overlay != Overlay.None -> overlay = Overlay.None
            numberInput.isNotEmpty() -> numberInput = ""
            else -> {
                bannerVisible = false
                statsVisible = false
            }
        }
    }

    val invert = settings?.invertUpDown == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (overlay != Overlay.None) return@onKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val code = event.nativeKeyEvent.keyCode
                if (code in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 ||
                    code in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9
                ) {
                    val digit = if (code <= AndroidKeyEvent.KEYCODE_9) code - AndroidKeyEvent.KEYCODE_0 else code - AndroidKeyEvent.KEYCODE_NUMPAD_0
                    if (numberInput.length < 4) numberInput += digit.toString()
                    return@onKeyEvent true
                }
                when (event.key) {
                    Key.DirectionUp -> vm.zap(if (invert) -1 else 1)
                    Key.DirectionDown -> vm.zap(if (invert) 1 else -1)
                    Key.ChannelUp -> vm.zap(1)
                    Key.ChannelDown -> vm.zap(-1)
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (numberInput.isNotEmpty()) {
                            numberInput.toIntOrNull()?.let { vm.playNumber(it) }
                            numberInput = ""
                        } else if (playerState.error != null) {
                            vm.player.retry()
                        } else if (statsVisible) {
                            statsVisible = false
                            bannerVisible = false
                        } else {
                            statsVisible = true
                            showBanner()
                        }
                    }
                    Key.DirectionLeft -> {
                        overlay = Overlay.Channels
                    }
                    Key.DirectionRight, Key.Menu -> {
                        overlay = Overlay.Tracks
                    }
                    Key.Info -> {
                        statsVisible = !statsVisible
                        if (statsVisible) showBanner() else bannerVisible = false
                    }
                    Key.Guide -> vm.navigate(Screen.Guide)
                    Key.LastChannel -> vm.playPrevious()
                    else -> return@onKeyEvent false
                }
                true
            }
            .focusRequester(rootFocus)
            .focusable(enabled = overlay == Overlay.None),
    ) {
        FullScreenVideo(exoPlayer, aspectMode, playerState.videoAspect)

        AnimatedVisibility(
            visible = statsVisible && overlay == Overlay.None,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            StatsPanel(vm)
        }

        val error = playerState.error
        if (error != null) {
            ErrorBox(error, Modifier.align(Alignment.Center))
        } else if (playerState.buffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(56.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (numberInput.isNotEmpty()) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
            ) {
                Text(
                    numberInput.padEnd(maxOf(numberInput.length, 3), '_'),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = bannerVisible && overlay == Overlay.None && channel != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (channel != null) {
                InfoBanner(
                    channel = channel,
                    epg = ui.epg,
                    now = now,
                    formatTime = formatTime,
                    playerState = playerState,
                    isFavorite = channel.key in settings?.favorites.orEmpty(),
                )
            }
        }

        AnimatedVisibility(
            visible = overlay == Overlay.Channels,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            ChannelListPanel(
                channels = ui.channels,
                groups = ui.groups,
                favorites = settings?.favorites.orEmpty(),
                currentKey = ui.currentKey,
                epg = ui.epg,
                now = now,
                onSelect = {
                    overlay = Overlay.None
                    if (it.key != ui.currentKey) vm.playChannel(it) else showBanner()
                },
                onToggleFavorite = { vm.toggleFavorite(it) },
            )
        }

        AnimatedVisibility(
            visible = overlay == Overlay.Tracks,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            TrackPanel(
                state = playerState,
                aspectMode = aspectMode,
                onAudio = { vm.player.selectAudio(it) },
                onText = { vm.player.selectText(it) },
                onAspect = { aspectMode = it },
                onGuide = {
                    overlay = Overlay.None
                    vm.navigate(Screen.Guide)
                },
                onClose = { overlay = Overlay.None },
            )
        }
    }
}

@Composable
private fun ErrorBox(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(32.dp),
        shape = RoundedCornerShape(24.dp),
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
    ) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.error_retry_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoBanner(
    channel: Channel,
    epg: EpgData,
    now: Long,
    formatTime: (Long) -> String,
    playerState: PlayerUiState,
    isFavorite: Boolean,
) {
    val (current, next) = remember(epg, channel.key, now / 30_000) { epg.nowAndNext(channel.key, now) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.95f))))
            .padding(start = 48.dp, end = 48.dp, top = 64.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                channel.number.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(20.dp))
            ChannelLogo(channel, modifier = Modifier.width(120.dp), size = 72.dp)
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isFavorite) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    playerState.videoQuality?.let { Badge(it) }
                    if (playerState.audioTracks.size > 1) {
                        Badge(stringResource(R.string.audio_tracks_count, playerState.audioTracks.size))
                    }
                    if (playerState.textTracks.isNotEmpty()) Badge("CC")
                }
                Spacer(Modifier.height(8.dp))
                if (current != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.time_range, formatTime(current.start), formatTime(current.stop)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            current.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    ProgrammeProgress(current.progressAt(now), Modifier.fillMaxWidth(0.7f))
                } else {
                    Text(stringResource(R.string.no_epg), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
                }
                if (next != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.up_next) + "  " + formatTime(next.start),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            next.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.player_hint), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Spacer(Modifier.width(8.dp))
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun ChannelListPanel(
    channels: List<Channel>,
    groups: List<String>,
    favorites: Set<String>,
    currentKey: String?,
    epg: EpgData,
    now: Long,
    onSelect: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
) {
    val allGroups = remember(groups) { listOf(GROUP_ALL, GROUP_FAVORITES) + groups }
    var groupIndex by remember { mutableIntStateOf(0) }
    val group = allGroups.getOrElse(groupIndex) { GROUP_ALL }
    val list = remember(channels, group, favorites) { filterChannels(channels, group, favorites) }
    val listState = rememberLazyListState()
    val currentRequester = remember { FocusRequester() }
    val firstRequester = remember { FocusRequester() }
    val currentIndex = list.indexOfFirst { it.key == currentKey }

    LaunchedEffect(group) {
        if (list.isEmpty()) return@LaunchedEffect
        listState.scrollToItem((currentIndex - 4).coerceAtLeast(0))
        withFrameNanos { }
        runCatching { (if (currentIndex >= 0) currentRequester else firstRequester).requestFocus() }
    }

    Surface(
        modifier = Modifier
            .width(480.dp)
            .fillMaxHeight()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        groupIndex = Math.floorMod(groupIndex - 1, allGroups.size)
                        true
                    }
                    Key.DirectionRight -> {
                        groupIndex = Math.floorMod(groupIndex + 1, allGroups.size)
                        true
                    }
                    else -> false
                }
            },
        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Column(Modifier.padding(vertical = 24.dp, horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Default.ChevronLeft, contentDescription = null)
                Text(
                    groupLabel(group),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
            Spacer(Modifier.height(12.dp))
            if (list.isEmpty()) {
                // Keep something focusable so the panel can still receive keys.
                Box(Modifier.fillMaxWidth().focusRequester(firstRequester).focusable().padding(16.dp)) {
                    Text(
                        stringResource(if (group == GROUP_FAVORITES) R.string.no_favorites else R.string.no_channels),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LaunchedEffect(group) {
                    withFrameNanos { }
                    runCatching { firstRequester.requestFocus() }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(list, key = { _, ch -> ch.key }) { index, ch ->
                        val requester = when {
                            index == currentIndex -> currentRequester
                            index == 0 -> firstRequester
                            else -> null
                        }
                        val (current, _) = remember(epg, ch.key, now / 60_000) { epg.nowAndNext(ch.key, now) }
                        ListItem(
                            selected = ch.key == currentKey,
                            onClick = { onSelect(ch) },
                            onLongClick = { onToggleFavorite(ch) },
                            modifier = if (requester != null) Modifier.focusRequester(requester) else Modifier,
                            scale = ListItemDefaults.scale(focusedScale = 1.02f),
                            glow = appListItemGlow(),
                            leadingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        ch.number.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(44.dp),
                                    )
                                    ChannelLogo(ch, modifier = Modifier.width(52.dp), size = 34.dp)
                                }
                            },
                            headlineContent = {
                                Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = if (current != null) {
                                { Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            } else {
                                null
                            },
                            trailingContent = if (ch.key in favorites) {
                                { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackPanel(
    state: PlayerUiState,
    aspectMode: AspectMode,
    onAudio: (TrackOption) -> Unit,
    onText: (TrackOption?) -> Unit,
    onAspect: (AspectMode) -> Unit,
    onGuide: () -> Unit,
    onClose: () -> Unit,
) {
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstRequester.requestFocus() }
    }
    Surface(
        modifier = Modifier
            .width(420.dp)
            .fillMaxHeight()
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (event.type == KeyEventType.KeyDown) onClose()
                        true
                    }
                    Key.DirectionRight -> true
                    else -> false
                }
            },
        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 24.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                OptionRow(
                    label = stringResource(R.string.tv_guide),
                    selected = false,
                    onClick = onGuide,
                    icon = Icons.Default.CalendarMonth,
                    modifier = Modifier.focusRequester(firstRequester),
                )
            }
            item { SectionTitle(Icons.Default.Audiotrack, stringResource(R.string.audio)) }
            if (state.audioTracks.isEmpty()) {
                item { OptionRow(stringResource(R.string.no_tracks), selected = false, onClick = {}) }
            }
            itemsIndexed(state.audioTracks) { _, track ->
                OptionRow(
                    label = track.label,
                    selected = track.selected,
                    onClick = { onAudio(track) },
                )
            }
            item { SectionTitle(Icons.Default.Subtitles, stringResource(R.string.subtitles)) }
            item {
                OptionRow(stringResource(R.string.subtitles_off), selected = !state.textEnabled, onClick = { onText(null) })
            }
            itemsIndexed(state.textTracks) { _, track ->
                OptionRow(track.label, selected = state.textEnabled && track.selected, onClick = { onText(track) })
            }
            item { SectionTitle(Icons.Default.AspectRatio, stringResource(R.string.aspect_ratio)) }
            AspectMode.entries.forEach { mode ->
                item {
                    OptionRow(stringResource(mode.label), selected = aspectMode == mode, onClick = { onAspect(mode) })
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        glow = appListItemGlow(),
        leadingContent = if (icon != null) {
            { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) }
        } else {
            null
        },
        headlineContent = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = if (selected) {
            { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp)) }
        } else {
            null
        },
    )
}

/** Picture formats. Most channels are already 16:9, so stretch and zoom act on top of that. */
enum class AspectMode(val label: Int) {
    Fit(R.string.aspect_fit),
    Stretch(R.string.aspect_fill),
    Zoom(R.string.aspect_zoom),
}

@Composable
private fun FullScreenVideo(player: ExoPlayer?, mode: AspectMode, videoAspect: Float) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val screenAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 16f / 9f
        // Videos with the same shape as the screen would look identical in every mode:
        // for them stretch removes side bars (4:3 inside 16:9) and zoom crops black borders.
        val sameShape = videoAspect <= 0f || kotlin.math.abs(videoAspect - screenAspect) < 0.1f
        val (resize, scaleX, scaleY) = when (mode) {
            AspectMode.Fit -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 1f, 1f)
            AspectMode.Stretch -> if (sameShape) {
                Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 4f / 3f, 1f)
            } else {
                Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 1f, 1f)
            }
            AspectMode.Zoom -> if (sameShape) {
                Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 4f / 3f, 4f / 3f)
            } else {
                Triple(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 1f, 1f)
            }
        }
        VideoSurface(
            player = player,
            resizeMode = resize,
            modifier = Modifier.requiredSize(maxWidth * scaleX, maxHeight * scaleY),
        )
    }
}

@Composable
private fun StatsPanel(vm: MainViewModel) {
    val stats by produceState<StreamStats?>(initialValue = vm.player.stats()) {
        while (true) {
            value = vm.player.stats()
            delay(1_000)
        }
    }
    val s = stats ?: return
    val rows = buildList {
        add(stringResource(R.string.stat_stream) to (s.streamType + (s.variants?.let { " · ${stringResource(R.string.stat_variants, it)}" } ?: "")))
        s.resolution?.let { res ->
            add(stringResource(R.string.stat_resolution) to listOfNotNull(res, s.frameRate?.let { "%.2f fps".format(Locale.US, it).replace(".00", "") }, s.hdr).joinToString(" · "))
        }
        s.videoCodec?.let { add(stringResource(R.string.stat_video) to it) }
        s.videoBitrate?.let { add(stringResource(R.string.stat_video_bitrate) to formatBitrate(it.toLong())) }
        val audio = listOfNotNull(
            s.audioCodec,
            s.audioChannels?.let { channelsLabel(it) },
            s.audioSampleRate?.let { "${it / 1000.0} kHz".replace(".0 ", " ") },
            s.audioBitrate?.let { formatBitrate(it.toLong()) },
            s.audioLanguage,
        )
        if (audio.isNotEmpty()) add(stringResource(R.string.stat_audio) to audio.joinToString(" · "))
        add(stringResource(R.string.stat_speed) to formatBitrate(s.bandwidth))
        add(stringResource(R.string.stat_buffer) to "%.1f s".format(Locale.US, s.bufferMs / 1000f))
        s.liveOffsetMs?.let { add(stringResource(R.string.stat_live_offset) to "%.1f s".format(Locale.US, it / 1000f)) }
        add(stringResource(R.string.stat_dropped) to s.droppedFrames.toString())
        s.videoDecoder?.let { add(stringResource(R.string.stat_decoder) to it) }
    }
    Surface(
        modifier = Modifier.padding(24.dp).width(460.dp),
        shape = RoundedCornerShape(20.dp),
        colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.72f), contentColor = Color.White),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                stringResource(R.string.stats_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            rows.forEach { (label, value) ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.width(130.dp),
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun formatBitrate(bps: Long): String = when {
    bps <= 0 -> "–"
    bps >= 1_000_000 -> "%.1f Mbps".format(Locale.US, bps / 1_000_000f)
    else -> "${bps / 1000} kbps"
}

private fun channelsLabel(count: Int): String = when (count) {
    1 -> "Mono"
    2 -> "Stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$count ch"
}
