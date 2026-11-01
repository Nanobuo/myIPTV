package io.github.nanobuo.myiptv.ui.mobile

import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/** Standard Google Cast button. [onDark] picks a light icon for dark backgrounds. */
@Composable
fun CastButton(onDark: Boolean, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            val theme = if (onDark) {
                androidx.appcompat.R.style.Theme_AppCompat_NoActionBar
            } else {
                androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar
            }
            MediaRouteButton(ContextThemeWrapper(ctx, theme)).also {
                runCatching { CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, it) }
            }
        },
        modifier = modifier,
    )
}
