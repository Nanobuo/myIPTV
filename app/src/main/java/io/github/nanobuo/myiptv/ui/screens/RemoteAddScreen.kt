package io.github.nanobuo.myiptv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.github.nanobuo.myiptv.R
import io.github.nanobuo.myiptv.data.PlaylistSource
import io.github.nanobuo.myiptv.data.SourceType
import io.github.nanobuo.myiptv.ui.MainViewModel
import io.github.nanobuo.myiptv.util.QrCode
import io.github.nanobuo.myiptv.util.RemoteInputServer
import java.util.UUID

/** Shows a QR code: the phone opens a form served by the TV and sends the playlist details. */
@Composable
fun RemoteAddScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val defaultName = stringResource(R.string.my_playlist)
    var address by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val server = RemoteInputServer(context) { received ->
            vm.savePlaylist(
                PlaylistSource(
                    id = UUID.randomUUID().toString(),
                    name = received.name.ifBlank { guessName(received.url, SourceType.URL) ?: defaultName },
                    type = SourceType.URL,
                    location = received.url,
                    epgUrl = received.epgUrl.ifBlank { null },
                    userAgent = received.userAgent.ifBlank { null },
                ),
            )
        }
        address = server.start()
        failed = address == null
        onDispose { server.stop() }
    }

    val qr = remember(address) { address?.let { QrCode.create(it) } }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }

    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(300.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (qr != null) {
                    Image(qr, contentDescription = address, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(40.dp))
            Column(Modifier.widthIn(max = 520.dp)) {
                Text(stringResource(R.string.add_from_phone), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                if (failed) {
                    Text(stringResource(R.string.remote_no_network), color = MaterialTheme.colorScheme.error)
                } else {
                    Text(stringResource(R.string.remote_instructions), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.remote_or_open), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(address.orEmpty(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = { vm.back() }, modifier = Modifier.focusRequester(requester)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}
