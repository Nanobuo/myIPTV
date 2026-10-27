package io.github.nanobuo.myiptv.ui.mobile

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nanobuo.myiptv.BuildConfig
import io.github.nanobuo.myiptv.R
import io.github.nanobuo.myiptv.data.AppSettings
import io.github.nanobuo.myiptv.data.PlaylistSource
import io.github.nanobuo.myiptv.data.SourceType
import io.github.nanobuo.myiptv.ui.MainViewModel
import io.github.nanobuo.myiptv.ui.Screen
import io.github.nanobuo.myiptv.ui.theme.ThemeColor
import io.github.nanobuo.myiptv.ui.theme.supportsDynamicColor
import io.github.nanobuo.myiptv.update.UpdateStatus
import io.github.nanobuo.myiptv.util.LocaleHelper

private enum class Choice { Audio, ThemeMode, ThemeColor, Language }

@Composable
fun MobileSettingsScreen(vm: MainViewModel, padding: PaddingValues) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val updateStatus by vm.updateStatus.collectAsStateWithLifecycle()
    val s = settings ?: return
    val context = LocalContext.current
    var choice by remember { mutableStateOf<Choice?>(null) }
    var optionsFor by remember { mutableStateOf<PlaylistSource?>(null) }
    val language = LocaleHelper.getLanguage(context)

    val audioOptions = listOf(
        AppSettings.AUDIO_SYSTEM to stringResource(R.string.audio_system),
        "it" to "Italiano",
        "en" to "English",
        AppSettings.AUDIO_ORIGINAL to stringResource(R.string.audio_original),
    )
    val modeOptions = listOf(
        AppSettings.MODE_SYSTEM to stringResource(R.string.theme_system),
        AppSettings.MODE_LIGHT to stringResource(R.string.theme_light),
        AppSettings.MODE_DARK to stringResource(R.string.theme_dark),
    )
    val colorOptions = ThemeColor.entries
        .filter { it != ThemeColor.DYNAMIC || supportsDynamicColor }
        .map { it.key to stringResource(it.label) }
    val languageOptions = listOf(
        LocaleHelper.SYSTEM to stringResource(R.string.language_system),
        "it" to "Italiano",
        "en" to "English",
    )
    fun label(options: List<Pair<String, String>>, value: String) = options.firstOrNull { it.first == value }?.second ?: options.first().second

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp),
            )
        }

        item { SectionHeader(stringResource(R.string.section_playlists)) }
        items(s.playlists, key = { it.id }) { p ->
            val active = p.id == s.activePlaylist?.id
            SettingItem(
                icon = if (p.type == SourceType.URL) Icons.Default.Link else Icons.Default.Description,
                title = p.name,
                subtitle = (if (active) stringResource(R.string.active) + " · " else "") + p.location,
                trailing = if (active) {
                    { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                } else {
                    null
                },
                onClick = { optionsFor = p },
            )
        }
        item { SettingItem(Icons.Default.Add, stringResource(R.string.add_playlist)) { vm.navigate(Screen.PlaylistEditor(null)) } }
        item {
            SettingItem(Icons.Default.Refresh, stringResource(R.string.refresh_all), stringResource(R.string.refresh_all_desc)) {
                vm.reloadActive()
                vm.openTab(Screen.Home)
            }
        }

        item { SectionHeader(stringResource(R.string.section_player)) }
        item {
            SwitchRow(Icons.Default.SwapVert, stringResource(R.string.invert_swipe), stringResource(R.string.invert_swipe_desc), s.invertUpDown) {
                vm.setInvertUpDown(it)
            }
        }
        item {
            SwitchRow(Icons.Default.PlayCircle, stringResource(R.string.start_last_channel), stringResource(R.string.start_last_channel_desc), s.startOnLastChannel) {
                vm.setStartOnLastChannel(it)
            }
        }
        item {
            SwitchRow(Icons.Default.PictureInPictureAlt, stringResource(R.string.pip), stringResource(R.string.pip_desc), s.pipEnabled) {
                vm.setPipEnabled(it)
            }
        }
        item {
            SwitchRow(Icons.Default.Headphones, stringResource(R.string.background_audio), stringResource(R.string.background_audio_desc), s.backgroundAudio) {
                vm.setBackgroundAudio(it)
            }
        }
        item {
            SettingItem(Icons.Default.Audiotrack, stringResource(R.string.preferred_audio), label(audioOptions, s.preferredAudio)) { choice = Choice.Audio }
        }

        item { SectionHeader(stringResource(R.string.section_appearance)) }
        item { SettingItem(Icons.Default.DarkMode, stringResource(R.string.theme_mode), label(modeOptions, s.themeMode)) { choice = Choice.ThemeMode } }
        item { SettingItem(Icons.Default.Palette, stringResource(R.string.theme_color), label(colorOptions, s.themeColor)) { choice = Choice.ThemeColor } }
        item { SettingItem(Icons.Default.Language, stringResource(R.string.language), label(languageOptions, language)) { choice = Choice.Language } }

        item { SectionHeader(stringResource(R.string.section_updates)) }
        item {
            SwitchRow(Icons.Default.Update, stringResource(R.string.auto_update), stringResource(R.string.auto_update_desc), s.autoUpdateCheck) {
                vm.setAutoUpdateCheck(it)
            }
        }
        item {
            val status = updateStatus
            SettingItem(
                Icons.Default.SystemUpdate,
                stringResource(R.string.check_updates),
                when (status) {
                    UpdateStatus.Checking -> stringResource(R.string.update_checking)
                    UpdateStatus.UpToDate -> stringResource(R.string.update_up_to_date)
                    is UpdateStatus.Failed -> stringResource(R.string.update_failed, status.message)
                    else -> stringResource(R.string.version, vm.updateManager.currentVersion)
                },
            ) { vm.checkForUpdates() }
        }
        item { SettingItem(Icons.Default.Tv, stringResource(R.string.app_name), stringResource(R.string.version, BuildConfig.VERSION_NAME)) {} }
    }

    when (choice) {
        Choice.Audio -> ChoiceDialog(stringResource(R.string.preferred_audio), audioOptions, s.preferredAudio, onSelect = {
            vm.setPreferredAudio(it)
            choice = null
        }, onDismiss = { choice = null })
        Choice.ThemeMode -> ChoiceDialog(stringResource(R.string.theme_mode), modeOptions, s.themeMode, onSelect = {
            vm.setThemeMode(it)
            choice = null
        }, onDismiss = { choice = null })
        Choice.ThemeColor -> ChoiceDialog(stringResource(R.string.theme_color), colorOptions, s.themeColor, onSelect = {
            vm.setThemeColor(it)
            choice = null
        }, onDismiss = { choice = null })
        Choice.Language -> ChoiceDialog(stringResource(R.string.language), languageOptions, language, onSelect = {
            choice = null
            if (it != language) {
                LocaleHelper.setLanguage(context, it)
                context.findActivity()?.let(::restart)
            }
        }, onDismiss = { choice = null })
        null -> Unit
    }

    optionsFor?.let { playlist ->
        val active = playlist.id == s.activePlaylist?.id
        AlertDialog(
            onDismissRequest = { optionsFor = null },
            title = { Text(playlist.name) },
            text = {
                Column {
                    if (!active) {
                        SettingItem(Icons.Default.CheckCircle, stringResource(R.string.use_playlist)) {
                            optionsFor = null
                            vm.setActivePlaylist(playlist)
                        }
                    }
                    SettingItem(Icons.Default.Refresh, stringResource(R.string.refresh_now)) {
                        optionsFor = null
                        if (active) vm.reloadActive() else vm.setActivePlaylist(playlist)
                    }
                    SettingItem(Icons.Default.Edit, stringResource(R.string.edit_playlist)) {
                        optionsFor = null
                        vm.navigate(Screen.PlaylistEditor(playlist.id))
                    }
                    SettingItem(Icons.Default.Delete, stringResource(R.string.delete)) {
                        optionsFor = null
                        vm.deletePlaylist(playlist)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { optionsFor = null }) { Text(stringResource(R.string.close)) } },
        )
    }
}

private fun restart(activity: Activity) {
    val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    activity.finishAffinity()
    if (intent != null) activity.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
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
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingItem(icon, title, subtitle, trailing = { Switch(checked = checked, onCheckedChange = onChange) }) { onChange(!checked) }
}
