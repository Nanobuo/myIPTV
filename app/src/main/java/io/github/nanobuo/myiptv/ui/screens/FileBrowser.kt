package io.github.nanobuo.myiptv.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import io.github.nanobuo.myiptv.R
import java.io.File

private fun hasStorageAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

private data class StorageRoot(val label: String, val dir: File, val removable: Boolean)

private fun storageRoots(context: Context): List<StorageRoot> {
    val roots = mutableListOf(
        StorageRoot(context.getString(R.string.internal_storage), Environment.getExternalStorageDirectory(), false),
    )
    context.getExternalFilesDirs(null).drop(1).filterNotNull().forEach { appDir ->
        val root = File(appDir.absolutePath.substringBefore("/Android/data"))
        if (root.exists()) roots += StorageRoot("${context.getString(R.string.external_storage)} (${root.name})", root, true)
    }
    return roots
}

/** Simple storage browser that works with the remote, for devices without a system file picker. */
@Composable
fun FileBrowser(
    extensions: Set<String>,
    onPick: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(hasStorageAccess(context)) }
    var permissionUnavailable by remember { mutableStateOf(false) }
    var dir by remember { mutableStateOf<File?>(null) }
    val roots = remember { storageRoots(context) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasAccess = hasStorageAccess(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasAccess = it }

    BackHandler {
        val current = dir
        dir = when {
            current == null -> {
                onDismiss()
                null
            }
            roots.any { it.dir == current } -> null
            else -> current.parentFile
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.extraSmall,
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
            Text(stringResource(R.string.file_browser_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                dir?.absolutePath ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (!hasAccess) {
                val requester = remember { FocusRequester() }
                Text(stringResource(R.string.storage_permission_text), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Button(
                    modifier = Modifier.focusRequester(requester),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val opened = runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }.recoverCatching {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            }.isSuccess
                            permissionUnavailable = !opened
                        } else {
                            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    },
                ) { Text(stringResource(R.string.grant_permission)) }
                if (permissionUnavailable) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.permission_unavailable), color = MaterialTheme.colorScheme.error)
                }
                LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
            } else {
                BrowserList(
                    dir = dir,
                    roots = roots,
                    extensions = extensions,
                    onNavigate = { dir = it },
                    onPick = onPick,
                )
            }
        }
    }
}

@Composable
private fun BrowserList(
    dir: File?,
    roots: List<StorageRoot>,
    extensions: Set<String>,
    onNavigate: (File?) -> Unit,
    onPick: (File) -> Unit,
) {
    val context = LocalContext.current
    val current = dir
    val entries: List<BrowserEntry> = remember(current) {
        if (current == null) {
            roots.map { BrowserEntry(it.label, it.dir, if (it.removable) EntryKind.Usb else EntryKind.Storage) }
        } else {
            val files = current.listFiles().orEmpty()
                .filter { !it.isHidden && (it.isDirectory || it.extension.lowercase() in extensions) }
                .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
                .map { BrowserEntry(it.name, it, if (it.isDirectory) EntryKind.Folder else EntryKind.File) }
            listOf(BrowserEntry(context.getString(R.string.parent_folder), current, EntryKind.Up)) + files
        }
    }
    val firstRequester = remember(current) { FocusRequester() }
    LaunchedEffect(current) {
        withFrameNanos { }
        runCatching { firstRequester.requestFocus() }
    }
    LazyColumn(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        itemsIndexed(entries, key = { _, e -> e.kind.name + e.file.absolutePath }) { index, entry ->
            SettingRow(
                icon = when (entry.kind) {
                    EntryKind.Up -> Icons.Default.ArrowUpward
                    EntryKind.Folder -> Icons.Default.Folder
                    EntryKind.File -> Icons.Default.Description
                    EntryKind.Storage -> Icons.Default.SdStorage
                    EntryKind.Usb -> Icons.Default.Usb
                },
                title = entry.label,
                onClick = {
                    when (entry.kind) {
                        EntryKind.Up -> onNavigate(if (roots.any { it.dir == entry.file }) null else entry.file.parentFile)
                        EntryKind.File -> onPick(entry.file)
                        else -> onNavigate(entry.file)
                    }
                },
                modifier = if (index == 0) Modifier.focusRequester(firstRequester) else Modifier,
            )
        }
        if (current != null && entries.size == 1) {
            item {
                Text(
                    stringResource(R.string.empty_folder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

private enum class EntryKind { Up, Folder, File, Storage, Usb }

private data class BrowserEntry(val label: String, val file: File, val kind: EntryKind)
