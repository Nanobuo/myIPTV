package io.github.nanobuo.myiptv.ui.mobile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import io.github.nanobuo.myiptv.R
import io.github.nanobuo.myiptv.data.Channel
import io.github.nanobuo.myiptv.data.EpgData
import io.github.nanobuo.myiptv.ui.MainViewModel
import io.github.nanobuo.myiptv.ui.components.ChannelLogo
import io.github.nanobuo.myiptv.ui.components.ProgrammeProgress
import io.github.nanobuo.myiptv.ui.components.VideoSurface
import io.github.nanobuo.myiptv.ui.components.rememberNow
import io.github.nanobuo.myiptv.ui.components.rememberTimeFormatter
import io.github.nanobuo.myiptv.ui.screens.GROUP_ALL
import io.github.nanobuo.myiptv.ui.screens.GROUP_FAVORITES
import io.github.nanobuo.myiptv.ui.screens.filterChannels
import io.github.nanobuo.myiptv.ui.screens.groupLabel

/** Channel list. On tablets the selected channel plays as a preview in a side pane. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MobileChannelsScreen(vm: MainViewModel, padding: PaddingValues) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val playingKey by vm.player.playingKey.collectAsStateWithLifecycle()
    val exoPlayer by vm.player.exoPlayer.collectAsStateWithLifecycle()
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val favorites = settings?.favorites.orEmpty()
    val now by rememberNow()
    val formatTime = rememberTimeFormatter()
    val twoPane = isTwoPane()

    var group by rememberSaveable { mutableStateOf(GROUP_ALL) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedKey by rememberSaveable { mutableStateOf(ui.currentKey) }
    val groups = remember(ui.groups) { listOf(GROUP_ALL, GROUP_FAVORITES) + ui.groups }
    val channels = remember(ui.channels, group, favorites, query) {
        val q = query.trim()
        filterChannels(ui.channels, group, favorites).filter {
            q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.number.toString() == q
        }
    }

    Row(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                }) {
                    Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, contentDescription = stringResource(R.string.search))
                }
            }
            if (searchOpen) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groups, key = { it }) { g ->
                    FilterChip(
                        selected = g == group,
                        onClick = { group = g },
                        label = { Text(groupLabel(g), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = if (g == GROUP_FAVORITES) {
                            { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }
            if (ui.playlistLoading || ui.epgLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            val error = ui.playlistError
            when {
                error != null && ui.channels.isEmpty() -> MessageView(
                    stringResource(R.string.playlist_error, error),
                    action = stringResource(R.string.retry),
                    onAction = { vm.reloadActive() },
                )
                channels.isEmpty() && !ui.playlistLoading -> MessageView(
                    stringResource(if (group == GROUP_FAVORITES) R.string.no_favorites else R.string.no_channels),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(channels, key = { it.key }, contentType = { "channel" }) { channel ->
                        ChannelListRow(
                            channel = channel,
                            epg = ui.epg,
                            now = now,
                            playing = channel.key == playingKey,
                            selected = twoPane && channel.key == selectedKey,
                            favorite = channel.key in favorites,
                            onClick = {
                                if (twoPane) {
                                    selectedKey = channel.key
                                    vm.preview(channel)
                                } else {
                                    vm.playChannel(channel)
                                }
                            },
                            onToggleFavorite = { vm.toggleFavorite(channel) },
                        )
                    }
                }
            }
        }

        if (twoPane) {
            val detail = channels.firstOrNull { it.key == selectedKey } ?: ui.channels.firstOrNull { it.key == selectedKey }
            DetailPane(
                channel = detail,
                epg = ui.epg,
                now = now,
                formatTime = formatTime,
                player = exoPlayer,
                showVideo = detail != null && playingKey == detail.key && playerState.error == null,
                buffering = playerState.buffering,
                favorite = detail != null && detail.key in favorites,
                onPlay = { detail?.let(vm::playChannel) },
                onToggleFavorite = { detail?.let(vm::toggleFavorite) },
                modifier = Modifier.weight(0.9f).fillMaxHeight(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelListRow(
    channel: Channel,
    epg: EpgData,
    now: Long,
    playing: Boolean,
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val (current, _) = remember(epg, channel.key, now / 60_000) { epg.nowAndNext(channel.key, now) }
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onToggleFavorite),
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.number.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(44.dp),
                )
                ChannelLogo(channel, modifier = Modifier.width(60.dp), size = 38.dp)
            }
        },
        headlineContent = {
            Text(
                channel.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = if (current != null) {
            {
                Column {
                    Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    ProgrammeProgress(current.progressAt(now), Modifier.fillMaxWidth(0.7f))
                }
            }
        } else {
            null
        },
        trailingContent = {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = stringResource(R.string.group_favorites),
                    tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun DetailPane(
    channel: Channel?,
    epg: EpgData,
    now: Long,
    formatTime: (Long) -> String,
    player: ExoPlayer?,
    showVideo: Boolean,
    buffering: Boolean,
    favorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.padding(12.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        if (channel == null) {
            MessageView(stringResource(R.string.select_channel_hint))
            return@Surface
        }
        val (current, next) = remember(epg, channel.key, now / 60_000) { epg.nowAndNext(channel.key, now) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                if (showVideo && player != null) {
                    VideoSurface(player, Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        ChannelLogo(channel, modifier = Modifier.fillMaxWidth(0.5f), size = 72.dp)
                    }
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(8.dp),
                    )
                }
                if (showVideo && buffering) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("${channel.number}  ${channel.name}", style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            channel.group?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlay) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.watch_fullscreen))
                }
                OutlinedButton(onClick = onToggleFavorite) {
                    Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = stringResource(R.string.group_favorites))
                }
            }
            Spacer(Modifier.height(16.dp))
            if (current == null && next == null) {
                Text(stringResource(R.string.no_epg), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (current != null) {
                Text(stringResource(R.string.now_playing).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(current.title, style = MaterialTheme.typography.titleMedium)
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
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(16.dp))
            }
            if (next != null) {
                Text(stringResource(R.string.up_next).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(next.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.time_range, formatTime(next.start), formatTime(next.stop)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
