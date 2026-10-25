@file:OptIn(UnstableApi::class)

package io.github.nanobuo.myiptv.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Shows the video of [player]. The same player instance can move between screens
 * (preview, guide, full screen) without restarting the stream.
 */
@Composable
fun VideoSurface(
    player: ExoPlayer?,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setKeepContentOnPlayerReset(true)
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                setBackgroundColor(android.graphics.Color.BLACK)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            if (view.resizeMode != resizeMode) view.resizeMode = resizeMode
        },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}
