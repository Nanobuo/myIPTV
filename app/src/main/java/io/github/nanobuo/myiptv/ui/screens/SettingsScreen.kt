package io.github.nanobuo.myiptv.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import io.github.nanobuo.myiptv.R
import io.github.nanobuo.myiptv.data.AppSettings
import io.github.nanobuo.myiptv.data.PlaylistSource
import io.github.nanobuo.myiptv.data.SourceType
import io.github.nanobuo.myiptv.ui.MainViewModel
import io.github.nanobuo.myiptv.ui.Screen
import io.github.nanobuo.myiptv.ui.theme.ThemeColor
import io.github.nanobuo.myiptv.ui.theme.appListItemGlow
import io.github.nanobuo.myiptv.ui.theme.supportsDynamicColor
import io.github.nanobuo.myiptv.update.UpdateStatus
import io.github.nanobuo.myiptv.util.LocaleHelper

private val audioOptions = listOf(AppSettings.AUDIO_SYSTEM, "it", "en", AppSettings.AUDIO_ORIGINAL)
private val languageOptions = listOf(LocaleHelper.SYSTEM, "it", "en")

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val updateStatus by vm.updateStatus.collectAsStateWithLifecycle()
    val s = settings ?: return
    val context = LocalContext.current
    var optionsFor by remember { mutableStateOf<PlaylistSource?>(null) }
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstRequester.requestFocus() } }

    val themeOptions = ThemeColor.entries.filter { it != ThemeColor.DYNAMIC || supportsDynamicColor }
    val currentLanguage = LocaleHelper.getLanguage(context)

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item { Header(stringResource(R.string.section_playlists)) }
            items(s.playlists, key = { it.id }) { p ->
                val active = p.id == s.activePlaylist?.id
                SettingRow(
                    icon = if (p.type == SourceType.URL) Icons.Default.Link else Icons.Default.Description,
                    title = p.name,
                    subtitle = (if (active) stringResource(R.string.active) + " · " else "") + p.location,
                    selected = active,
                    onClick = { optionsFor = p },
                    modifier = if (p == s.playlists.first()) Modifier.focusRequester(firstRequester) else Modifier,
                    trailing = if (active) {
                        { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
            item {
                SettingRow(
                    icon = Icons.Default.Add,
                    title = stringResource(R.string.add_playlist),
                    onClick = { vm.navigate(Screen.PlaylistEditor(null)) },
                    modifier = if (s.playlists.isEmpty()) Modifier.focusRequester(firstRequester) else Modifier,
                )
            }
            item {
                SettingRow(
                    icon = Icons.Default.Refresh,
                    title = stringResource(R.string.refresh_all),
                    subtitle = stringResource(R.string.refresh_all_desc),
                    onClick = {
                        vm.reloadActive()
                        vm.back()
                    },
                )
            }

            item { Header(stringResource(R.string.section_player)) }
            item {
                SwitchRow(
                    icon = Icons.Default.SwapVert,
                    title = stringResource(R.string.invert_up_down),
                    subtitle = stringResource(R.string.invert_up_down_desc),
                    checked = s.invertUpDown,
                    onChange = { vm.setInvertUpDown(it) },
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.PlayCircle,
                    title = stringResource(R.string.start_last_channel),
                    subtitle = stringResource(R.string.start_last_channel_desc),
                    checked = s.startOnLastChannel,
                    onChange = { vm.setStartOnLastChannel(it) },
                )
            }
            item {
                SettingRow(
                    icon = Icons.Default.Audiotrack,
                    title = stringResource(R.string.preferred_audio),
                    subtitle = audioLabel(s.preferredAudio),
                    onClick = {
                        val next = audioOptions[(audioOptions.indexOf(s.preferredAudio) + 1) % audioOptions.size]
                        vm.setPreferredAudio(next)
                    },
                )
            }

            item { Header(stringResource(R.string.section_appearance)) }
            item {
                val light = s.themeMode == AppSettings.MODE_LIGHT
                SettingRow(
                    icon = if (light) Icons.Default.LightMode else Icons.Default.DarkMode,
                    title = stringResource(R.string.theme_mode),
                    subtitle = stringResource(if (light) R.string.theme_light else R.string.theme_dark),
                    onClick = { vm.setThemeMode(if (light) AppSettings.MODE_DARK else AppSettings.MODE_LIGHT) },
                )
            }
            item {
                val current = ThemeColor.from(s.themeColor).let { if (it in themeOptions) it else ThemeColor.BLUE }
                SettingRow(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.theme_color),
                    subtitle = stringResource(current.label),
                    onClick = {
                        val next = themeOptions[(themeOptions.indexOf(current) + 1) % themeOptions.size]
                        vm.setThemeColor(next.key)
                    },
                )
            }
            item {
                SettingRow(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.language),
                    subtitle = languageLabel(currentLanguage),
                    onClick = {
                        val next = languageOptions[(languageOptions.indexOf(currentLanguage) + 1) % languageOptions.size]
                        LocaleHelper.setLanguage(context, next)
                        restartApp(context as Activity)
                    },
                )
            }

            item { Header(stringResource(R.string.section_updates)) }
            item {
                SwitchRow(
                    icon = Icons.Default.Update,
                    title = stringResource(R.string.auto_update),
                    subtitle = stringResource(R.string.auto_update_desc),
                    checked = s.autoUpdateCheck,
                    onChange = { vm.setAutoUpdateCheck(it) },
                )
            }
            item {
                val status = updateStatus
                SettingRow(
                    icon = Icons.Default.SystemUpdate,
                    title = stringResource(R.string.check_updates),
                    subtitle = when (status) {
                        UpdateStatus.Checking -> stringResource(R.string.update_checking)
                        UpdateStatus.UpToDate -> stringResource(R.string.update_up_to_date)
                        is UpdateStatus.Failed -> stringResource(R.string.update_failed, status.message)
                        else -> stringResource(R.string.version, vm.updateManager.currentVersion)
                    },
                    onClick = { vm.checkForUpdates() },
                )
            }
            item {
                SettingRow(
                    icon = Icons.Default.Tv,
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.version, io.github.nanobuo.myiptv.BuildConfig.VERSION_NAME),
                    onClick = {},
                )
            }
        }
    }

    optionsFor?.let { playlist ->
        PlaylistOptionsDialog(
            playlist = playlist,
            isActive = playlist.id == s.activePlaylist?.id,
            onDismiss = { optionsFor = null },
            onUse = {
                optionsFor = null
                vm.setActivePlaylist(playlist)
            },
            onRefresh = {
                optionsFor = null
                if (playlist.id == s.activePlaylist?.id) vm.reloadActive() else vm.setActivePlaylist(playlist)
            },
            onEdit = {
                optionsFor = null
                vm.navigate(Screen.PlaylistEditor(playlist.id))
            },
            onDelete = {
                optionsFor = null
                vm.deletePlaylist(playlist)
            },
        )
    }
}

private fun restartApp(activity: Activity) {
    val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    activity.finishAffinity()
    if (intent != null) activity.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

@Composable
private fun audioLabel(value: String): String = when (value) {
    AppSettings.AUDIO_SYSTEM -> stringResource(R.string.audio_system)
    AppSettings.AUDIO_ORIGINAL -> stringResource(R.string.audio_original)
    "it" -> "Italiano"
    "en" -> "English"
    else -> value
}

@Composable
private fun languageLabel(value: String): String = when (value) {
    "it" -> "Italiano"
    "en" -> "English"
    else -> stringResource(R.string.language_system)
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        glow = appListItemGlow(),
        leadingContent = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = trailing,
    )
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = { onChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun PlaylistOptionsDialog(
    playlist: PlaylistSource,
    isActive: Boolean,
    onDismiss: () -> Unit,
    onUse: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.width(460.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(playlist.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    playlist.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                if (!isActive) {
                    SettingRow(Icons.Default.CheckCircle, stringResource(R.string.use_playlist), onUse, Modifier.focusRequester(requester))
                }
                SettingRow(
                    Icons.Default.Refresh,
                    stringResource(R.string.refresh_now),
                    onRefresh,
                    if (isActive) Modifier.focusRequester(requester) else Modifier,
                )
                SettingRow(Icons.Default.Edit, stringResource(R.string.edit_playlist), onEdit)
                SettingRow(Icons.Default.Delete, stringResource(R.string.delete), onDelete)
                LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
            }
        }
    }
}
