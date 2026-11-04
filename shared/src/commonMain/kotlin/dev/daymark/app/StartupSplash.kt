package dev.daymark.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.daymark.resources.Res
import dev.daymark.resources.app_name
import dev.daymark.resources.daymark_splash_transparent
import dev.daymark.resources.startup_loading
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal expect fun reducedMotionEnabled(): Boolean

@Composable
internal fun StartupSplash(active: Boolean) {
    val name = stringResource(Res.string.app_name)
    val loading = stringResource(Res.string.startup_loading)
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF071C16)).semantics {
            contentDescription = loading
            liveRegion = LiveRegionMode.Polite
        },
        contentAlignment = Alignment.Center
    ) {
        val textBrush = if (active && !reducedMotionEnabled()) shimmerBrush() else Brush.linearGradient(
            listOf(Color(0xFFC2DF9D), Color(0xFFC2DF9D))
        )
        Box(Modifier.size(160.dp)) {
            Image(painterResource(Res.drawable.daymark_splash_transparent), null, Modifier.fillMaxSize())
            Text(
                text = name,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = 184.dp),
                style = MaterialTheme.typography.titleLarge.copy(brush = textBrush)
            )
        }
    }
}

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "startup shimmer")
    val position by transition.animateFloat(
        initialValue = -500f,
        targetValue = 500f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "startup shimmer position"
    )
    return Brush.linearGradient(
        colors = listOf(Color(0xFFC2DF9D), Color(0xFFF6FAEF), Color(0xFFC2DF9D)),
        start = Offset(position - 100f, 0f),
        end = Offset(position + 100f, 0f)
    )
}
