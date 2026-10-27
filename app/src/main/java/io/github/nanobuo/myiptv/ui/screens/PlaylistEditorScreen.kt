package io.github.nanobuo.myiptv.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.github.nanobuo.myiptv.R
import io.github.nanobuo.myiptv.data.PlaylistSource
import io.github.nanobuo.myiptv.data.SourceType
import io.github.nanobuo.myiptv.ui.MainViewModel
import java.util.UUID
import androidx.compose.material3.Text as M3Text

private enum class BrowserTarget { Playlist, Epg }

private val playlistExtensions = setOf("m3u", "m3u8", "txt")
private val epgExtensions = setOf("xml", "gz", "xmltv")

@Composable
fun PlaylistEditorScreen(vm: MainViewModel, playlistId: String?) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val existing = settings?.playlists?.firstOrNull { it.id == playlistId }
    val context = LocalContext.current
    val defaultName = stringResource(R.string.my_playlist)

    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(if (existing == null || existing.type == SourceType.URL) SourceType.URL else existing.type) }
    var url by rememberSaveable { mutableStateOf(existing?.takeIf { it.type == SourceType.URL }?.location.orEmpty()) }
    var fileLocation by rememberSaveable { mutableStateOf(existing?.takeIf { it.type != SourceType.URL }?.location) }
    var fileType by rememberSaveable { mutableStateOf(existing?.type?.takeIf { it != SourceType.URL } ?: SourceType.CONTENT_URI) }
    var epgUrl by rememberSaveable { mutableStateOf(existing?.epgUrl.orEmpty()) }
    var userAgent by rememberSaveable { mutableStateOf(existing?.userAgent.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var browserTarget by remember { mutableStateOf<BrowserTarget?>(null) }

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

    fun launchPicker(launcher: () -> Unit) {
        try {
            launcher()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.picker_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstRequester.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp),
        ) {
            item {
                Text(
                    stringResource(if (existing == null) R.string.new_playlist else R.string.edit_playlist),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { M3Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().focusRequester(firstRequester),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SourceChoice(
                        selected = type == SourceType.URL,
                        icon = Icons.Default.Link,
                        label = stringResource(R.string.source_url),
                        onClick = { type = SourceType.URL },
                    )
                    SourceChoice(
                        selected = type != SourceType.URL,
                        icon = Icons.Default.Description,
                        label = stringResource(R.string.source_file),
                        onClick = { type = fileType },
                    )
                }
            }
            if (type == SourceType.URL) {
                item {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it.trim() },
                        label = { M3Text(stringResource(R.string.playlist_url)) },
                        placeholder = { M3Text("https://…/playlist.m3u") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.playlist_file) + ": " +
                            (fileLocation?.let { displayLocation(context, it) } ?: stringResource(R.string.no_file_selected)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { launchPicker { playlistPicker.launch(arrayOf("*/*")) } }) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.pick_file_system))
                        }
                        OutlinedButton(onClick = { browserTarget = BrowserTarget.Playlist }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.pick_file_browser))
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = epgUrl,
                    onValueChange = { epgUrl = it.trim() },
                    label = { M3Text(stringResource(R.string.epg_url)) },
                    supportingText = { M3Text(stringResource(R.string.epg_url_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { launchPicker { epgPicker.launch(arrayOf("*/*")) } }) {
                        Text(stringResource(R.string.pick_epg_file) + " · " + stringResource(R.string.pick_file_system))
                    }
                    OutlinedButton(onClick = { browserTarget = BrowserTarget.Epg }) {
                        Text(stringResource(R.string.pick_file_browser))
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    label = { M3Text(stringResource(R.string.user_agent)) },
                    supportingText = { M3Text(stringResource(R.string.user_agent_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = {
                        val location = if (type == SourceType.URL) url.trim() else fileLocation
                        if (location.isNullOrBlank()) {
                            error = context.getString(R.string.error_missing_source)
                            return@Button
                        }
                        val sourceType = if (type == SourceType.URL) SourceType.URL else fileType
                        vm.savePlaylist(
                            PlaylistSource(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                name = name.trim().ifBlank { guessName(location, sourceType) ?: defaultName },
                                type = sourceType,
                                location = location,
                                epgUrl = epgUrl.trim().ifBlank { null },
                                userAgent = userAgent.trim().ifBlank { null },
                            ),
                        )
                    }) { Text(stringResource(R.string.save)) }
                    OutlinedButton(onClick = { vm.back() }) { Text(stringResource(R.string.cancel)) }
                    if (existing != null) {
                        OutlinedButton(onClick = {
                            vm.back()
                            vm.deletePlaylist(existing)
                        }) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        browserTarget?.let { target ->
            FileBrowser(
                extensions = if (target == BrowserTarget.Playlist) playlistExtensions else epgExtensions,
                onPick = { file ->
                    if (target == BrowserTarget.Playlist) {
                        fileLocation = file.absolutePath
                        fileType = SourceType.FILE_PATH
                        type = SourceType.FILE_PATH
                        if (name.isBlank()) name = file.nameWithoutExtension
                    } else {
                        epgUrl = file.absolutePath
                    }
                    browserTarget = null
                },
                onDismiss = { browserTarget = null },
            )
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceChoice(selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    androidx.tv.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
    ) { Text(label) }
}

internal fun persistPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

internal fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()

internal fun displayLocation(context: Context, location: String): String =
    if (location.startsWith("content://")) displayName(context, Uri.parse(location)) ?: location else location

internal fun guessName(location: String, type: SourceType): String? = when (type) {
    SourceType.URL -> runCatching { Uri.parse(location).host }.getOrNull()
    else -> location.substringAfterLast('/').substringBeforeLast('.').takeIf { it.isNotBlank() && !it.contains(':') }
}
