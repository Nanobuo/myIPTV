package io.github.nanobuo.myiptv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import io.github.nanobuo.myiptv.R
import io.github.nanobuo.myiptv.ui.MainViewModel
import io.github.nanobuo.myiptv.ui.components.ProgrammeProgress
import io.github.nanobuo.myiptv.update.UpdateStatus

/** Shows the update flow: available → downloading → installing (or errors). */
@Composable
fun UpdateDialogHost(vm: MainViewModel) {
    val show by vm.showUpdateDialog.collectAsStateWithLifecycle()
    val status by vm.updateStatus.collectAsStateWithLifecycle()
    if (!show) return
    val visible = status is UpdateStatus.Available || status is UpdateStatus.Downloading ||
        status is UpdateStatus.Installing || status is UpdateStatus.NeedsPermission || status is UpdateStatus.Failed
    if (!visible) return

    val busy = status is UpdateStatus.Downloading || status is UpdateStatus.Installing
    Dialog(
        onDismissRequest = { if (!busy) vm.dismissUpdateDialog() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.width(520.dp),
        ) {
            Column(Modifier.padding(28.dp)) {
                val requester = remember { FocusRequester() }
                when (val s = status) {
                    is UpdateStatus.Available -> {
                        Text(stringResource(R.string.update_available), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.update_available_text, s.info.version, vm.updateManager.currentVersion))
                        if (s.info.notes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                s.info.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { vm.installUpdate(s.info) }, modifier = Modifier.focusRequester(requester)) {
                                Text(stringResource(R.string.update_now))
                            }
                            OutlinedButton(onClick = { vm.dismissUpdateDialog() }) { Text(stringResource(R.string.later)) }
                        }
                    }
                    is UpdateStatus.Downloading -> {
                        Text(
                            stringResource(R.string.update_downloading, (s.progress * 100).toInt()),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        ProgrammeProgress(s.progress, Modifier.fillMaxWidth())
                    }
                    UpdateStatus.Installing -> {
                        Text(stringResource(R.string.update_installing), style = MaterialTheme.typography.titleMedium)
                    }
                    is UpdateStatus.NeedsPermission -> {
                        Text(stringResource(R.string.update_available), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.update_permission))
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { vm.updateManager.openInstallPermissionSettings() },
                                modifier = Modifier.focusRequester(requester),
                            ) { Text(stringResource(R.string.open_settings)) }
                            OutlinedButton(onClick = { vm.installUpdate(s.info) }) { Text(stringResource(R.string.retry)) }
                            OutlinedButton(onClick = { vm.dismissUpdateDialog() }) { Text(stringResource(R.string.later)) }
                        }
                    }
                    is UpdateStatus.Failed -> {
                        Text(stringResource(R.string.update_error, s.message), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { vm.dismissUpdateDialog() }, modifier = Modifier.focusRequester(requester)) {
                            Text(stringResource(R.string.close))
                        }
                    }
                    else -> Unit
                }
                LaunchedEffect(status::class) { runCatching { requester.requestFocus() } }
            }
        }
    }
}
