package md.vicproj.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Aceeasi paleta stricta alb-negru ca in aplicatia Windows (Theme.cs). */
object VicColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF101010)
    val SurfaceAlt = Color(0xFF1A1A1A)
    val Foreground = Color(0xFFFFFFFF)
    val Muted = Color(0xFF9E9E9E)
    val MutedDim = Color(0xFF606060)
    val Border = Color(0xFF3A3A3A)
    val Hover = Color(0xFFE8E8E8)
    val HoverDark = Color(0xFF2E2E2E)
    val Press = Color(0xFFC8C8C8)

    // Aurora ambientala: bule albastre, blurate, in miscare permanenta (singura exceptie de la alb-negru).
    val AuroraBlue1 = Color(0x552563EB)
    val AuroraBlue2 = Color(0x4D1D4ED8)
    val AuroraBlue3 = Color(0x4D3B82F6)
    val AuroraBlue4 = Color(0x401E40AF)
}

@Composable
fun VicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = VicColors.Foreground,
            onPrimary = VicColors.Background,
            background = VicColors.Background,
            onBackground = VicColors.Foreground,
            surface = VicColors.Surface,
            onSurface = VicColors.Foreground,
        ),
        content = content,
    )
}
