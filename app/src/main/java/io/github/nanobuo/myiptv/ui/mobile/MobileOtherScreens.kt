package io.github.nanobuo.myiptv.ui.mobile

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nanobuo.myiptv.R
import io.github.nanobuo.myiptv.data.PlaylistSource
import io.github.nanobuo.myiptv.data.SourceType
import io.github.nanobuo.myiptv.ui.MainViewModel
import io.github.nanobuo.myiptv.ui.Screen
import io.github.nanobuo.myiptv.ui.screens.displayLocation
import io.github.nanobuo.myiptv.ui.screens.displayName
import io.github.nanobuo.myiptv.ui.screens.guessName
import io.github.nanobuo.myiptv.ui.screens.persistPermission
import io.github.nanobuo.myiptv.update.UpdateStatus
import java.util.UUID

@Composable
fun MobileWelcomeScreen(vm: MainViewModel) {
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 520.dp)) {
            Icon(Icons.Default.LiveTv, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.welcome_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = { vm.navigate(Screen.PlaylistEditor(null)) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_playlist))
            }
            TextButton(onClick = { vm.navigate(Screen.Settings) }) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings))
            }
        }
    }
}

/** Add or edit a playlist. On phones and tablets the system file picker is always available. */
@Composable
fun MobilePlaylistEditor(vm: MainViewModel, playlistId: String?) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val existing = settings?.playlists?.firstOrNull { it.id == playlistId }
    val context = LocalContext.current
    val defaultName = stringResource(R.string.my_playlist)

    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var isUrl by rememberSaveable { mutableStateOf(existing == null || existing.type == SourceType.URL) }
    var url by rememberSaveable { mutableStateOf(existing?.takeIf { it.type == SourceType.URL }?.location.orEmpty()) }
    var fileLocation by rememberSaveable { mutableStateOf(existing?.takeIf { it.type != SourceType.URL }?.location) }
    var fileType by rememberSaveable { mutableStateOf(existing?.type?.takeIf { it != SourceType.URL } ?: SourceType.CONTENT_URI) }
    var epgUrl by rememberSaveable { mutableStateOf(existing?.epgUrl.orEmpty()) }
    var userAgent by rememberSaveable { mutableStateOf(existing?.userAgent.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    val playlistPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistPermission(context, uri)
            fileLocation = uri.toString()
            fileType = SourceType.CONTENT_URI
            if (name.isBlank()) name = displayName(context, uri)?.substringBeforeLast('.') ?: ""
        }
    }
    val epgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistPermission(context, uri)
            epgUrl = uri.toString()
        }
    }
    fun launch(block: () -> Unit) {
        try {
            block()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.picker_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (existing == null) R.string.new_playlist else R.string.edit_playlist)) },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isUrl,
                    onClick = { isUrl = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.source_url)) }
                SegmentedButton(
                    selected = !isUrl,
                    onClick = { isUrl = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.source_file)) }
            }
            if (isUrl) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim() },
                    label = { Text(stringResource(R.string.playlist_url)) },
                    placeholder = { Text("https://…/playlist.m3u") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    stringResource(R.string.playlist_file) + ": " +
                        (fileLocation?.let { displayLocation(context, it) } ?: stringResource(R.string.no_file_selected)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = { launch { playlistPicker.launch(arrayOf("*/*")) } }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.choose_file))
                }
            }
            OutlinedTextField(
                value = epgUrl,
                onValueChange = { epgUrl = it.trim() },
                label = { Text(stringResource(R.string.epg_url)) },
                supportingText = { Text(stringResource(R.string.epg_url_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { launch { epgPicker.launch(arrayOf("*/*")) } }) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pick_epg_file))
            }
            OutlinedTextField(
                value = userAgent,
                onValueChange = { userAgent = it },
                label = { Text(stringResource(R.string.user_agent)) },
                supportingText = { Text(stringResource(R.string.user_agent_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val location = if (isUrl) url.trim() else fileLocation
                    if (location.isNullOrBlank()) {
                        error = context.getString(R.string.error_missing_source)
                    } else {
                        val type = if (isUrl) SourceType.URL else fileType
                        vm.savePlaylist(
                            PlaylistSource(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                name = name.trim().ifBlank { guessName(location, type) ?: defaultName },
                                type = type,
                                location = location,
                                epgUrl = epgUrl.trim().ifBlank { null },
                                userAgent = userAgent.trim().ifBlank { null },
                            ),
                        )
                    }
                }) { Text(stringResource(R.string.save)) }
                if (existing != null) {
                    OutlinedButton(onClick = {
                        vm.back()
                        vm.deletePlaylist(existing)
                    }) { Text(stringResource(R.string.delete)) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Update flow for touch screens: available → downloading → installing (or errors). */
@Composable
fun MobileUpdateDialog(vm: MainViewModel) {
    val show by vm.showUpdateDialog.collectAsStateWithLifecycle()
    val status by vm.updateStatus.collectAsStateWithLifecycle()
    if (!show) return
    when (val s = status) {
        is UpdateStatus.Available -> AlertDialog(
            onDismissRequest = { vm.dismissUpdateDialog() },
            title = { Text(stringResource(R.string.update_available)) },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.update_available_text, s.info.version, vm.updateManager.currentVersion))
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(s.info.notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { Button(onClick = { vm.installUpdate(s.info) }) { Text(stringResource(R.string.update_now)) } },
            dismissButton = { TextButton(onClick = { vm.dismissUpdateDialog() }) { Text(stringResource(R.string.later)) } },
        )
        is UpdateStatus.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_downloading, (s.progress * 100).toInt())) },
            text = { LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
        UpdateStatus.Installing -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_installing)) },
            confirmButton = {},
        )
        is UpdateStatus.NeedsPermission -> AlertDialog(
            onDismissRequest = { vm.dismissUpdateDialog() },
            title = { Text(stringResource(R.string.update_available)) },
            text = { Text(stringResource(R.string.update_permission)) },
            confirmButton = {
                Row {
                    TextButton(onClick = { vm.updateManager.openInstallPermissionSettings() }) { Text(stringResource(R.string.open_settings)) }
                    TextButton(onClick = { vm.installUpdate(s.info) }) { Text(stringResource(R.string.retry)) }
                }
            },
            dismissButton = { TextButton(onClick = { vm.dismissUpdateDialog() }) { Text(stringResource(R.string.later)) } },
        )
        is UpdateStatus.Failed -> AlertDialog(
            onDismissRequest = { vm.dismissUpdateDialog() },
            title = { Text(stringResource(R.string.update_error, s.message)) },
            confirmButton = { TextButton(onClick = { vm.dismissUpdateDialog() }) { Text(stringResource(R.string.close)) } },
        )
        else -> Unit
    }
}
