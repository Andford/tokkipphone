// SPDX-License-Identifier: GPL-3.0-or-later
package mx.loperstack.tokkiphone.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** The Galletita theme (still "hive" inside) is active. */
val LocalHiveTheme = compositionLocalOf { false }

/** True while Android's battery saver is on: decorative animation rests. */
@Composable
fun rememberPowerSaver(): Boolean {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var saving by remember { mutableStateOf(power?.isPowerSaveMode == true) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                saving = power?.isPowerSaveMode == true
            }
        }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return saving
}

/**
 * Galletita's backdrop (the theme is still "hive" inside, so saved settings
 * keep working), the same as the Windows app's and the installer's: soft
 * Tokkip cookies, the Material Expressive shape he sits on, scattered behind
 * the app. They appear from [origin], each turns slowly and breathes, and now
 * and then a soft glow travels outward across them.
 *
 * Light on the battery: a few dozen cached paths drawn on one Canvas; time
 * advances about 30 times a second, only while the app is in front, and only
 * the backdrop redraws. With [animate] false they hold still.
 */
@Composable
fun HiveComb(
    dark: Boolean,
    animate: Boolean,
    modifier: Modifier = Modifier,
    cellDp: Float = Tokens.COMB_CELL_DP,
    origin: (androidx.compose.ui.geometry.Size) -> Offset = { Offset(it.width / 2f, it.height * 0.18f) },
) {
    val line = if (dark) {
        Color(0xFF80D5D0).copy(alpha = Tokens.COMB_LINE_ALPHA_DARK)
    } else {
        Color(0xFF006A67).copy(alpha = Tokens.COMB_LINE_ALPHA_LIGHT)
    }
    val cellPx = with(LocalDensity.current) { cellDp * density }

    // Seconds, carried across pauses so it picks up where it stopped.
    var time by remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(animate, lifecycle) {
        if (!animate) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val base = time
            val since = SystemClock.uptimeMillis()
            while (true) {
                delay(Tokens.COMB_FRAME_MS)
                time = base + (SystemClock.uptimeMillis() - since) / 1000f
            }
        }
    }
    val moving = animate
    Canvas(modifier.fillMaxSize()) {
        val t = if (moving) time else 0f
        val step = maxOf(72f * density, cellPx * 6.5f)
        val o = origin(size)
        val diagonal = sqrt(size.width * size.width + size.height * size.height)
        val stroke = Stroke(width = 1.6f * density)
        val pulse = (t % Tokens.COMB_PULSE_SECONDS) / Tokens.COMB_PULSE_SECONDS
        val cols = (size.width / step).toInt() + 2
        val rows = (size.height / (step * 0.87f)).toInt() + 2
        for (r in 0 until rows) for (q in 0 until cols) {
            val seed = sin(r * 12.9898f + q * 78.233f) * 43758.5453f
            val f = seed - kotlin.math.floor(seed)
            val x = q * step + (if (r % 2 == 1) step / 2 else 0f) + (f - 0.5f) * step * 0.3f
            val y = r * step * 0.87f + (0.5f - f) * step * 0.25f
            val dx = x - o.x
            val dy = y - o.y
            val dist = sqrt(dx * dx + dy * dy) / diagonal
            val fade = (1f - dist / 2.4f).coerceIn(0f, 1f)
            if (fade < 0.01f) continue
            val wave = if (moving) (1f - kotlin.math.abs(dist - pulse * 1.05f) * 9f).coerceAtLeast(0f) else 0f
            val breath = if (moving) sin(t * 2f * PI.toFloat() / Tokens.COMB_BREATH_SECONDS + f * 6.283f) else 0f
            val radius = step * (0.2f + 0.16f * f) * (1f + breath * 0.05f)
            val turn = f * 6.283f + t * (f - 0.5f) * 16f * PI.toFloat() / 180f
            val path = cookiePath(Offset(x, y), radius, turn)
            val a = 0.35f + 0.65f * fade
            drawPath(path, line.copy(alpha = line.alpha * (0.4f + 0.5f * wave) * a))
            drawPath(path, line.copy(alpha = (line.alpha * (0.9f + 0.6f * wave) * a).coerceAtMost(1f)), style = stroke)
        }
    }
}

/** Tokkip's cookie: a circle with nine soft bumps, turned by [turn] radians. */
internal fun cookiePath(center: Offset, radius: Float, turn: Float, lobes: Int = 9, depth: Float = 0.07f): Path = Path().apply {
    for (i in 0..72) {
        val th = i / 72f * 2f * PI.toFloat()
        val rr = radius * (1f + depth * kotlin.math.cos(lobes * (th - turn))) / (1f + depth)
        val px = center.x + kotlin.math.cos(th) * rr
        val py = center.y + kotlin.math.sin(th) * rr
        if (i == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}
