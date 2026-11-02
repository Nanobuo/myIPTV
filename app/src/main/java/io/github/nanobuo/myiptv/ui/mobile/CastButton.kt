package io.github.nanobuo.myiptv.ui.mobile

import android.view.ContextThemeWrapper
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import io.github.nanobuo.myiptv.R
import io.github.nanobuo.myiptv.cast.CastController

/**
 * Cast button. Google Cast is started only when tapped, then the standard device chooser
 * (or the controls of the current cast) is shown.
 */
@Composable
fun CastButton(cast: CastController, onDark: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val device by cast.deviceName.collectAsStateWithLifecycle()
    IconButton(
        modifier = modifier,
        onClick = {
            val shown = runCatching {
                if (!cast.attach()) return@runCatching false
                val theme = if (onDark) {
                    androidx.mediarouter.R.style.Theme_MediaRouter
                } else {
                    androidx.mediarouter.R.style.Theme_MediaRouter_Light
                }
                val themed = ContextThemeWrapper(context, theme)
                if (cast.isCasting) {
                    MediaRouteControllerDialog(themed).show()
                } else {
                    val selector = cast.routeSelector ?: return@runCatching false
                    MediaRouteChooserDialog(themed).apply { routeSelector = selector }.show()
                }
                true
            }.getOrDefault(false)
            if (!shown) Toast.makeText(context, R.string.cast_unavailable, Toast.LENGTH_SHORT).show()
        },
    ) {
        Icon(
            if (device != null) Icons.Default.CastConnected else Icons.Default.Cast,
            contentDescription = stringResource(R.string.cast),
            tint = when {
                device != null -> MaterialTheme.colorScheme.primary
                onDark -> Color.White
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
