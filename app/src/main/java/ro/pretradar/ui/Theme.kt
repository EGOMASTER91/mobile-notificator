package ro.pretradar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.pretradar.domain.ThemeChoice

data class RadarPalette(
    val background: Color, val card: Color, val text: Color, val muted: Color,
    val border: Color, val green: Color, val softGreen: Color, val amber: Color, val softAmber: Color,
    val ink: Color, val onInk: Color, val softSurface: Color, val hot: Color, val softHot: Color
)

val LightPalette = RadarPalette(
    background = Color(0xFFF4F6F5), card = Color.White, text = Color(0xFF122C29),
    muted = Color(0xFF5B7069), border = Color(0xFFDCE5DF), green = Color(0xFF087F65),
    softGreen = Color(0xFFE0F2EA), amber = Color(0xFF855800), softAmber = Color(0xFFFFF4D8),
    ink = Color(0xFF153831), onInk = Color(0xFFF3FFF9), softSurface = Color(0xFFEDF2EF),
    hot = Color(0xFFB74628), softHot = Color(0xFFFFEEE5)
)
val DarkPalette = RadarPalette(
    background = Color(0xFF101A1B), card = Color(0xFF192627), text = Color(0xFFF0F7F3),
    muted = Color(0xFFA8BCB5), border = Color(0xFF304440), green = Color(0xFF65DAB7),
    softGreen = Color(0xFF1D3D33), amber = Color(0xFFFFD88A), softAmber = Color(0xFF443622),
    ink = Color(0xFF153831), onInk = Color(0xFFF3FFF9), softSurface = Color(0xFF223332),
    hot = Color(0xFFFFB397), softHot = Color(0xFF422D25)
)
val LocalRadarPalette = staticCompositionLocalOf { LightPalette }

@Composable fun RadarTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.DARK -> true
        ThemeChoice.LIGHT -> false
    }
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(
        primary = p.green, onPrimary = Color(0xFF07382A), primaryContainer = p.softGreen, onPrimaryContainer = p.green,
        secondary = p.green, onSecondary = Color(0xFF07382A), secondaryContainer = p.softGreen, onSecondaryContainer = p.green,
        tertiary = p.hot, onTertiary = p.ink, tertiaryContainer = p.softHot, onTertiaryContainer = p.hot,
        background = p.background, onBackground = p.text, surface = p.card, onSurface = p.text,
        surfaceVariant = p.softSurface, onSurfaceVariant = p.muted, surfaceTint = Color.Transparent,
        surfaceContainerLowest = p.background, surfaceContainerLow = p.card,
        surfaceContainer = p.card, surfaceContainerHigh = p.softSurface, surfaceContainerHighest = p.softSurface,
        outline = p.border, outlineVariant = p.border, inverseSurface = LightPalette.text,
        inverseOnSurface = LightPalette.card, error = Color(0xFFFFB4AB), errorContainer = Color(0xFF482725),
        onError = Color(0xFF5A1510), onErrorContainer = Color(0xFFFFDAD5)
    ) else lightColorScheme(
        primary = p.green, onPrimary = Color.White, primaryContainer = p.softGreen, onPrimaryContainer = p.green,
        secondary = p.green, onSecondary = Color.White, secondaryContainer = p.softGreen, onSecondaryContainer = p.green,
        tertiary = p.hot, onTertiary = Color.White, tertiaryContainer = p.softHot, onTertiaryContainer = p.hot,
        background = p.background, onBackground = p.text, surface = p.card, onSurface = p.text,
        surfaceVariant = p.softSurface, onSurfaceVariant = p.muted, surfaceTint = Color.Transparent,
        surfaceContainerLowest = p.card, surfaceContainerLow = p.card,
        surfaceContainer = p.card, surfaceContainerHigh = p.softSurface, surfaceContainerHighest = p.softSurface,
        outline = p.border, outlineVariant = p.border, inverseSurface = p.ink,
        inverseOnSurface = p.onInk, error = Color(0xFFAA3225), errorContainer = Color(0xFFFFE5DF),
        onError = Color.White, onErrorContainer = Color(0xFF7E251A)
    )
    val typography = Typography(
        displaySmall = radarType(38, 44, FontWeight.Bold, -.9f),
        headlineLarge = radarType(32, 38, FontWeight.Bold, -.7f),
        headlineMedium = radarType(27, 33, FontWeight.Bold, -.5f),
        headlineSmall = radarType(23, 29, FontWeight.Bold, -.35f),
        titleLarge = radarType(21, 27, FontWeight.Bold, -.3f),
        titleMedium = radarType(16, 22, FontWeight.SemiBold, -.15f),
        titleSmall = radarType(14, 20, FontWeight.SemiBold),
        bodyLarge = radarType(16, 24), bodyMedium = radarType(14, 21), bodySmall = radarType(12, 18),
        labelLarge = radarType(14, 20, FontWeight.SemiBold),
        labelMedium = radarType(12, 17, FontWeight.SemiBold, .15f),
        labelSmall = radarType(12, 16, FontWeight.Medium, .15f)
    )
    CompositionLocalProvider(LocalRadarPalette provides p) {
        MaterialTheme(
            colorScheme = scheme, typography = typography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp),
                extraLarge = RoundedCornerShape(24.dp)
            ), content = content
        )
    }
}

private fun radarType(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size.sp,
    lineHeight = line.sp, letterSpacing = tracking.sp
)
