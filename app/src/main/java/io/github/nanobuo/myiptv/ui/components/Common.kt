package io.github.nanobuo.myiptv.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.SubcomposeAsyncImage
import io.github.nanobuo.myiptv.data.Channel
import kotlinx.coroutines.delay
import java.util.Date

/** Current time, refreshed periodically so EPG progress stays up to date. */
@Composable
fun rememberNow(intervalMs: Long = 20_000L): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(intervalMs)
        value = System.currentTimeMillis()
    }
}

/** Formats times following the device 12/24h preference. */
@Composable
fun rememberTimeFormatter(): (Long) -> String {
    val context = LocalContext.current
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    return remember(format) { { time: Long -> format.format(Date(time)) } }
}

@Composable
fun ChannelLogo(channel: Channel, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        if (channel.logo != null) {
            SubcomposeAsyncImage(
                model = channel.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(size),
                error = { LogoPlaceholder(channel) },
            )
        } else {
            LogoPlaceholder(channel)
        }
    }
}

@Composable
private fun LogoPlaceholder(channel: Channel) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = channel.name.split(' ').filter { it.isNotBlank() }.take(2)
                .joinToString("") { it.take(1) }.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
fun ProgrammeProgress(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.height(4.dp).clip(RoundedCornerShape(2.dp)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
