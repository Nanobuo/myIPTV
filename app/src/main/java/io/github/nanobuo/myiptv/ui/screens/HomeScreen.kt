package io.github.nanobuo.myiptv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.media3.exoplayer.ExoPlayer
import io.github.nanobuo.myiptv.ui.components.VideoSurface
import io.github.nanobuo.myiptv.ui.theme.appListItemGlow
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.github.nanobuo.myiptv.ui.MainViewModel
import io.github.nanobuo.myiptv.ui.Screen
import io.github.nanobuo.myiptv.ui.components.ChannelLogo
import io.github.nanobuo.myiptv.ui.components.ProgrammeProgress
import io.github.nanobuo.myiptv.ui.components.rememberNow
import io.github.nanobuo.myiptv.ui.components.rememberTimeFormatter

const val GROUP_ALL = "\u0000all"
const val GROUP_FAVORITES = "\u0000fav"
private const val PREVIEW_DELAY_MS = 700L

fun filterChannels(channels: List<Channel>, group: String, favorites: Set<String>): List<Channel> = when (group) {
    GROUP_ALL -> channels
    GROUP_FAVORITES -> channels.filter { it.key in favorites }
    else -> channels.filter { it.group == group }
}

@Composable
fun groupLabel(group: String): String = when (group) {
    GROUP_ALL -> stringResource(R.string.group_all)
    GROUP_FAVORITES -> stringResource(R.string.group_favorites)
    else -> group
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val favorites = settings?.favorites.orEmpty()
    val context = LocalContext.current

    var selectedGroup by rememberSaveable { mutableStateOf(GROUP_ALL) }
    // Changing group while scrolling the list with the remote is debounced to keep it smooth.
    var pendingGroup by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingGroup) {
        val target = pendingGroup ?: return@LaunchedEffect
        delay(250)
        selectedGroup = target
    }
    val exoPlayer by vm.player.exoPlayer.collectAsStateWithLifecycle()
    val playingKey by vm.player.playingKey.collectAsStateWithLifecycle()
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val groups = remember(ui.groups) { listOf(GROUP_ALL, GROUP_FAVORITES) + ui.groups }
    val channels = remember(ui.channels, selectedGroup, favorites) {
        filterChannels(ui.channels, selectedGroup, favorites)
    }
    var focused by remember { mutableStateOf<Channel?>(null) }
    val now by rememberNow()
    val formatTime = rememberTimeFormatter()

    val listState = rememberLazyListState()
    val currentItemRequester = remember { FocusRequester() }
    val firstItemRequester = remember { FocusRequester() }
    val currentIndex = channels.indexOfFirst { it.key == ui.currentKey }

    // Put the focus on the channel being watched (or the first one) when the screen opens.
    LaunchedEffect(channels.isNotEmpty()) {
        if (channels.isEmpty()) return@LaunchedEffect
        if (currentIndex >= 0) {
            listState.scrollToItem((currentIndex - 3).coerceAtLeast(0))
            withFrameNanos { }
            runCatching { currentItemRequester.requestFocus() }
        } else {
            withFrameNanos { }
            runCatching { firstItemRequester.requestFocus() }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ---- Groups -----------------------------------------------------------------------
        Column(modifier = Modifier.width(220.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)) {
                Icon(Icons.Default.LiveTv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f).focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(groups, key = { it }) { group ->
                    val icon: ImageVector? = when (group) {
                        GROUP_ALL -> Icons.Default.ViewList
                        GROUP_FAVORITES -> Icons.Default.Favorite
                        else -> null
                    }
                    ListItem(
                        selected = group == selectedGroup,
                        onClick = {
                            pendingGroup = null
                            selectedGroup = group
                        },
                        modifier = Modifier.onFocusChanged { if (it.isFocused) pendingGroup = group },
                        glow = appListItemGlow(),
                        leadingContent = if (icon != null) {
                            { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) }
                        } else {
                            null
                        },
                        headlineContent = {
                            Text(groupLabel(group), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        scale = ListItemDefaults.scale(focusedScale = 1.02f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            ListItem(
                selected = false,
                onClick = { vm.navigate(Screen.Guide) },
                leadingContent = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(20.dp)) },
                headlineContent = { Text(stringResource(R.string.tv_guide)) },
                scale = ListItemDefaults.scale(focusedScale = 1.02f),
                glow = appListItemGlow(),
            )
            ListItem(
                selected = false,
                glow = appListItemGlow(),
                onClick = { vm.navigate(Screen.Settings) },
                leadingContent = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp)) },
                headlineContent = { Text(stringResource(R.string.settings)) },
                scale = ListItemDefaults.scale(focusedScale = 1.02f),
            )
        }

        // ---- Channels ---------------------------------------------------------------------
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp).height(40.dp),
            ) {
                Text(
                    groupLabel(selectedGroup),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.channels_count, channels.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ui.playlistLoading || ui.epgLoading) {
                    Spacer(Modifier.width(16.dp))
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (ui.playlistLoading) R.string.loading_playlist else R.string.loading_epg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val error = ui.playlistError
            when {
                error != null && ui.channels.isEmpty() -> Box(Modifier.weight(1f)) {
                    MessageBox(
                        text = stringResource(R.string.playlist_error, error),
                        action = stringResource(R.string.retry),
                        onAction = { vm.reloadActive() },
                    )
                }
                channels.isEmpty() && !ui.playlistLoading -> Box(Modifier.weight(1f)) {
                    MessageBox(
                        text = stringResource(if (selectedGroup == GROUP_FAVORITES) R.string.no_favorites else R.string.no_channels),
                    )
                }
                channels.isEmpty() -> Spacer(Modifier.weight(1f))
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).focusRestorer(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(channels, key = { _, ch -> ch.key }) { index, channel ->
                        val requester = when {
                            index == currentIndex -> currentItemRequester
                            index == 0 -> firstItemRequester
                            else -> null
                        }
                        ChannelRow(
                            channel = channel,
                            playing = channel.key == playingKey,
                            epg = ui.epg,
                            now = now,
                            isPlaying = channel.key == ui.currentKey,
                            isFavorite = channel.key in favorites,
                            onClick = { vm.playChannel(channel) },
                            onLongClick = {
                                val wasFavorite = channel.key in favorites
                                vm.toggleFavorite(channel)
                                Toast.makeText(
                                    context,
                                    if (wasFavorite) R.string.removed_from_favorites else R.string.added_to_favorites,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            modifier = Modifier
                                .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                                .onFocusChanged { if (it.isFocused) focused = channel },
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.home_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            )
        }

        // ---- Details ----------------------------------------------------------------------
        val detailChannel = focused?.takeIf { f -> channels.any { it.key == f.key } } ?: channels.firstOrNull()
        // Start the preview once the focus rests on a channel for a moment.
        LaunchedEffect(focused?.key) {
            val target = focused ?: return@LaunchedEffect
            delay(PREVIEW_DELAY_MS)
            vm.preview(target)
        }
        ChannelDetails(
            channel = detailChannel,
            player = exoPlayer,
            showVideo = detailChannel != null && playingKey == detailChannel.key &&
                !playerState.buffering && playerState.error == null,
            epg = ui.epg,
            now = now,
            formatTime = formatTime,
            epgError = ui.epgError,
            modifier = Modifier.width(320.dp).fillMaxHeight(),
        )
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    playing: Boolean,
    epg: EpgData,
    now: Long,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (current, _) = remember(epg, channel.key, now / 60_000) { epg.nowAndNext(channel.key, now) }
    ListItem(
        selected = isPlaying && playing,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        glow = appListItemGlow(),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.number.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(48.dp),
                    maxLines = 1,
                )
                ChannelLogo(channel, modifier = Modifier.width(64.dp), size = 40.dp)
            }
        },
        headlineContent = {
            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = if (current != null) {
            {
                Column {
                    Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    ProgrammeProgress(current.progressAt(now), Modifier.fillMaxWidth(0.6f))
                }
            }
        } else {
            null
        },
        trailingContent = if (isFavorite) {
            { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp)) }
        } else {
            null
        },
    )
}

@Composable
private fun ChannelDetails(
    channel: Channel?,
    player: ExoPlayer?,
    showVideo: Boolean,
    epg: EpgData,
    now: Long,
    formatTime: (Long) -> String,
    epgError: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (channel == null) return@Surface
        val (current, next) = remember(epg, channel.key, now / 60_000) { epg.nowAndNext(channel.key, now) }
        Column(Modifier.padding(20.dp)) {
            // 16:9 preview box: live video of the focused channel, its logo until the video starts.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
            ) {
                if (player != null) VideoSurface(player, Modifier.fillMaxSize())
                if (!showVideo) {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChannelLogo(channel, modifier = Modifier.fillMaxWidth(0.6f), size = 80.dp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "${channel.number}  ${channel.name}",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            channel.group?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            if (current == null && next == null) {
                Text(
                    epgError?.let { stringResource(R.string.epg_error, it) } ?: stringResource(R.string.no_epg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (current != null) {
                Label(stringResource(R.string.now_playing))
                Text(current.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.time_range, formatTime(current.start), formatTime(current.stop)) +
                        "  ·  " + stringResource(R.string.minutes_left, ((current.stop - now) / 60_000).toInt().coerceAtLeast(0)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                ProgrammeProgress(current.progressAt(now), Modifier.fillMaxWidth())
                current.description?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(16.dp))
            }
            if (next != null) {
                Label(stringResource(R.string.up_next))
                Text(next.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.time_range, formatTime(next.start), formatTime(next.stop)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

@Composable
fun MessageBox(text: String, action: String? = null, onAction: () -> Unit = {}) {
    val requester = remember { FocusRequester() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                androidx.tv.material3.Button(onClick = onAction, modifier = Modifier.focusRequester(requester)) { Text(action) }
                LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
            }
        }
    }
}
