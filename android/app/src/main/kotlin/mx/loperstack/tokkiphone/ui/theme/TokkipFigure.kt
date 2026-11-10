// SPDX-License-Identifier: GPL-3.0-or-later
package mx.loperstack.tokkiphone.ui.theme

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** How Tokkip feels. */
enum class TokkipMood { Idle, Happy, Working, Sad, Sleep, Surprised }

/**
 * Tokkip, alive: the bunny from the Tokkip family (Andford/tokkip), drawn as
 * in Tokkip PC and in this app's PC side (TokkipFigure.qml): cream fur, teal
 * ears and tail, round ink eyes with no shine that blink to a line. He
 * breathes, blinks, sways his ears and looks about; behind him, if [cookie],
 * the soft Material Expressive shape that changes with his [mood].
 *
 * Drawn in "studio units" (the body is 200 wide, its top at y = −90, its
 * bottom at 120), scaled to fit. Time only runs while the app is in front.
 */
@Composable
fun TokkipFigure(
    mood: TokkipMood,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    cookie: Boolean = true,
    accent: Color = Color(0xFF3F9E9A),
    cookieColor: Color = accent.copy(alpha = 0.16f),
    body: Color = Color(0xFFF7EAD3),
    description: String = "Tokkip",
) {
    var time by remember { mutableFloatStateOf(1.3f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(animate, lifecycle) {
        if (!animate) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val base = time
            val since = SystemClock.uptimeMillis()
            while (true) {
                delay(33)
                time = base + (SystemClock.uptimeMillis() - since) / 1000f
            }
        }
    }
    val ink = Color(accent.red * 0.88f, accent.green * 0.88f, accent.blue * 0.88f)
    Canvas(modifier.semantics { contentDescription = description }) {
        val t = time
        val s = min(size.width, size.height) / 360f
        translate(size.width / 2, size.height / 2 + 46 * s) {
            scale(s, s, pivot = Offset.Zero) {
                drawTokkip(mood, t, animate, cookie, accent, cookieColor, body, ink)
            }
        }
    }
}

private fun cookieShape(mood: TokkipMood): Triple<Int, Float, Float> = when (mood) {
    TokkipMood.Working -> Triple(4, 0.11f, 24f)
    TokkipMood.Happy -> Triple(10, 0.09f, 30f)
    TokkipMood.Surprised -> Triple(12, 0.08f, 14f)
    TokkipMood.Sad -> Triple(3, 0.1f, 0f)
    TokkipMood.Sleep -> Triple(0, 0f, 0f)
    TokkipMood.Idle -> Triple(9, 0.05f, 0f)
}

private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color) =
    drawOval(color, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))

private fun earPath(len: Float, w: Float, dy: Float = 0f) = Path().apply {
    moveTo(-w * 0.45f, 6f + dy)
    cubicTo(-w * 1.05f, -len * 0.45f + dy, -w * 0.65f, -len + dy, 0f, -len + dy)
    cubicTo(w * 0.65f, -len + dy, w * 1.05f, -len * 0.45f + dy, w * 0.45f, 6f + dy)
    close()
}

private val bodyPath = Path().apply {
    addRoundRect(RoundRect(-100f, -90f, 100f, 120f, CornerRadius(54f, 54f)))
}

private fun DrawScope.drawTokkip(
    mood: TokkipMood, t: Float, animate: Boolean, cookie: Boolean,
    accent: Color, cookieColor: Color, body: Color, ink: Color,
) {
    if (cookie) {
        val (lobes, depth, spin) = cookieShape(mood)
        val r = 165f + if (animate) sin(t * 1.3f) * 3f else 0f
        drawPath(cookiePath(Offset(0f, -36f), r, t * spin * PI.toFloat() / 180f, lobes, depth), cookieColor)
    }
    val breath = if (animate) sin(t * 2.1f) else 0f
    val bounce = if (mood == TokkipMood.Happy && animate) -abs(sin(t * 5f)) * 10f else 0f
    val droop = when (mood) { TokkipMood.Sad -> 26f; TokkipMood.Sleep -> 16f; TokkipMood.Surprised -> -8f; else -> 0f }
    val sway = if (animate) sin(t * 1.6f) * 3f else 0f
    val wiggle = if (mood == TokkipMood.Happy && animate) sin(t * 9f) * 8f else 0f
    val look = when {
        mood == TokkipMood.Working && animate -> sin(t * 1.4f) * 0.6f
        mood == TokkipMood.Idle && animate -> sin(t * 0.37f) * sin(t * 0.23f) * 0.5f
        else -> 0f
    }
    val nod = when (mood) { TokkipMood.Sad -> -0.18f; TokkipMood.Sleep -> -0.1f; else -> 0f }
    val cycle = (t + 0.7f) % 3.7f
    val open = if (animate && cycle < 0.14f) abs(cycle - 0.07f) / 0.07f else 1f

    translate(0f, bounce) {
        scale(1 + breath * 0.012f, 1 - breath * 0.012f, pivot = Offset(0f, 120f)) {
            // Tail and ears.
            oval(96f + sin(t * 3f) * 2f, 74f, 26f, 26f, accent)
            for (sd in listOf(-1f, 1f)) {
                translate(sd * 50f, -84f) {
                    rotate(sd * 10f + sd * droop + sd * sway + (if (sd < 0) wiggle else -wiggle), pivot = Offset.Zero) {
                        drawPath(earPath(128f, 46f), body)
                        drawPath(earPath(128f * 0.78f, 46f * 0.52f, -8f), accent)
                    }
                }
            }
            // Body, with a soft light from above.
            drawPath(bodyPath, body)
            clipPath(bodyPath) {
                drawRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.06f)), -90f, 120f),
                    Offset(-120f, -100f), Size(240f, 230f),
                )
                translate(sin(look) * 62f, 20f - sin(nod) * 40f) {
                    val cheek = Color(240 / 255f, 120 / 255f, 140 / 255f, if (mood == TokkipMood.Happy) 0.6f else 0.36f)
                    for (sd in listOf(-1f, 1f)) oval(sd * 70f, 18f, 17f, 9f, cheek)
                    val line = Stroke(width = 6f, cap = StrokeCap.Round)
                    for (sd in listOf(-1f, 1f)) {
                        translate(sd * 46f, -6f) {
                            when (mood) {
                                TokkipMood.Happy -> drawArc(ink, 207f, 126f, false, Offset(-13f, -5f), Size(26f, 26f), style = line)
                                TokkipMood.Sleep -> drawArc(ink, 27f, 126f, false, Offset(-12f, -14f), Size(24f, 24f), style = line)
                                else -> {
                                    val r = if (mood == TokkipMood.Surprised) 15f else 12f
                                    val o = if (mood == TokkipMood.Sad) min(open, 0.8f) else open
                                    if (o < 0.35f) drawLine(ink, Offset(-12f, 0f), Offset(12f, 0f), 6f, StrokeCap.Round)
                                    else oval(0f, 0f, r, r * min(1f, o), ink)
                                }
                            }
                        }
                    }
                    if (mood == TokkipMood.Sad) {
                        val k = (t % 3.2f) / 3.2f
                        if (k < 0.7f) oval(-50f, 12f + k * 40f, 5f, 7f, Color(120 / 255f, 190 / 255f, 1f, 0.85f * (1 - k / 0.7f)))
                    }
                    val mouth = Stroke(width = 5.5f, cap = StrokeCap.Round)
                    val y = 30f
                    when (mood) {
                        TokkipMood.Happy -> drawArc(ink, 21.6f, 136.8f, false, Offset(-22f, y - 36f), Size(44f, 44f), style = mouth)
                        TokkipMood.Sad -> drawArc(ink, 219.6f, 100.8f, false, Offset(-16f, y), Size(32f, 32f), style = mouth)
                        TokkipMood.Surprised -> oval(0f, y, 8f, 10f, ink)
                        TokkipMood.Sleep -> oval(0f, y, 5f, 5f + sin(t * 1.4f) * 1.5f, ink)
                        TokkipMood.Working -> drawLine(ink, Offset(-12f, y), Offset(12f, y), 5.5f, StrokeCap.Round)
                        TokkipMood.Idle -> drawArc(ink, 36f, 108f, false, Offset(-18f, y - 30f), Size(36f, 36f), style = mouth)
                    }
                }
            }
            // Paws, low on his sides.
            val paw = Color(body.red * 0.95f, body.green * 0.95f, body.blue * 0.95f)
            for (sd in listOf(-1f, 1f)) {
                val dy = if (mood == TokkipMood.Working && animate) sin(t * 8f + sd) * 4f else 0f
                translate(sd * 80f, 96f + dy) {
                    rotate(sd * 11.5f, pivot = Offset.Zero) { oval(0f, 0f, 22f, 17f, paw) }
                }
            }
        }
        // Asleep: little z's drifting up (as dots, to need no font here).
        if (mood == TokkipMood.Sleep) {
            for (i in 0 until 3) {
                val k = (t * 0.35f + i / 3f) % 1f
                oval(70f + k * 50f + i * 10f, -110f - k * 90f, 5f + i * 2f, 5f + i * 2f, ink.copy(alpha = sin(k * PI.toFloat()) * 0.7f))
            }
        }
    }
}
