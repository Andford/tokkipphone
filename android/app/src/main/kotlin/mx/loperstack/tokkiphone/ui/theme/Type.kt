// SPDX-License-Identifier: GPL-3.0-or-later
package mx.loperstack.tokkiphone.ui.theme

import android.content.res.AssetManager
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * The fonts bundled with the app (`assets/fonts` in the repository, packaged
 * as assets under `fonts/`): Tokkip's Roboto Flex for text and Fredoka for
 * headings, Figtree for Graphite's text, Instrument Serif for Graphite's
 * headings, Space Mono for codes.
 */
@Immutable
class AppFonts(private val assets: AssetManager) {
    /** Variable fonts: one file each, every weight the app uses. */
    private fun variable(file: String, weights: List<Int>) = FontFamily(
        weights.map { weight ->
            Font(file, assets, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
        },
    )
    val sans = variable("fonts/RobotoFlex.ttf", listOf(400, 500, 600, 650, 700))
    /** Tokkip's round headings. */
    val display = variable("fonts/Fredoka.ttf", listOf(500, 600, 650, 700))
    /** Graphite's text. */
    val figtree = variable("fonts/Figtree.ttf", listOf(400, 500, 600, 650, 700))
    val serif = FontFamily(Font("fonts/InstrumentSerif-Regular.ttf", assets))
    val mono = FontFamily(
        Font("fonts/SpaceMono-Regular.ttf", assets),
        Font("fonts/SpaceMono-Bold.ttf", assets, FontWeight.Bold),
    )
}

val LocalAppFonts = staticCompositionLocalOf<AppFonts> { error("TokkipPhoneTheme provides the fonts") }

/**
 * Material's type scale (sizes and line heights suit a phone) in the bundled
 * fonts, with the weights from docs/design/tokens.json: bold headings in
 * Bloom, serif headings in Graphite.
 */
internal fun typography(fonts: AppFonts, graphite: Boolean): Typography {
    val base = Typography()
    fun TextStyle.text(weight: Int) = copy(fontFamily = if (graphite) fonts.figtree else fonts.sans, fontWeight = FontWeight(weight))
    fun TextStyle.heading() =
        if (graphite) copy(fontFamily = fonts.serif, fontWeight = FontWeight.Normal) else copy(fontFamily = fonts.display, fontWeight = FontWeight(600))
    return base.copy(
        displayLarge = base.displayLarge.heading(),
        displayMedium = base.displayMedium.heading(),
        displaySmall = base.displaySmall.heading(),
        headlineLarge = base.headlineLarge.heading(),
        headlineMedium = base.headlineMedium.heading(),
        headlineSmall = base.headlineSmall.heading(),
        titleLarge = base.titleLarge.text(600),
        titleMedium = base.titleMedium.text(600),
        titleSmall = base.titleSmall.text(600),
        bodyLarge = base.bodyLarge.text(400),
        bodyMedium = base.bodyMedium.text(400),
        bodySmall = base.bodySmall.text(400),
        labelLarge = base.labelLarge.text(600),
        labelMedium = base.labelMedium.text(600),
        labelSmall = base.labelSmall.text(500),
    )
}
