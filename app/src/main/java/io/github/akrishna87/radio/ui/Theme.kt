package io.github.akrishna87.radio.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Vaanalai's colours: near-black with a pink → violet → cyan glow and a lime highlight. */
object Brand {
    val Background = Color(0xFF0B0712)
    val Surface = Color(0xFF16101F)
    val Surface2 = Color(0xFF201830)
    val Outline = Color(0xFF2E2445)
    val Text = Color(0xFFF7F3FF)
    val SubText = Color(0xFFA99FC2)
    val Faint = Color(0xFF6E6585)

    val Pink = Color(0xFFFF4FA3)
    val Violet = Color(0xFF8B5CF6)
    val Cyan = Color(0xFF22D3EE)
    val Lime = Color(0xFFD9FF3D)

    val Gradient = Brush.linearGradient(listOf(Pink, Violet, Cyan))
    val HorizontalGradient = Brush.horizontalGradient(listOf(Pink, Violet, Cyan))

    /** Pairs of colours for the genre tiles. */
    val TilePalettes = listOf(
        Color(0xFFFF4FA3) to Color(0xFF8B5CF6),
        Color(0xFFFF8A3D) to Color(0xFFFF3D7F),
        Color(0xFF22D3EE) to Color(0xFF6366F1),
        Color(0xFF34D399) to Color(0xFF0EA5E9),
        Color(0xFFA855F7) to Color(0xFF3B82F6),
        Color(0xFFFACC15) to Color(0xFFF97316),
        Color(0xFFF43F5E) to Color(0xFF9333EA),
        Color(0xFF2DD4BF) to Color(0xFF65A30D),
    )
}

private val Colors = darkColorScheme(
    primary = Brand.Pink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B1A3A),
    onPrimaryContainer = Color(0xFFFFD6EA),
    secondary = Brand.Cyan,
    onSecondary = Color(0xFF002A33),
    secondaryContainer = Color(0xFF1C2B4A),
    onSecondaryContainer = Color(0xFFD5F6FF),
    tertiary = Brand.Lime,
    onTertiary = Color.Black,
    background = Brand.Background,
    onBackground = Brand.Text,
    surface = Brand.Background,
    onSurface = Brand.Text,
    surfaceVariant = Brand.Surface2,
    onSurfaceVariant = Brand.SubText,
    surfaceContainerLowest = Color(0xFF07040C),
    surfaceContainerLow = Brand.Surface,
    surfaceContainer = Brand.Surface,
    surfaceContainerHigh = Brand.Surface2,
    surfaceContainerHighest = Color(0xFF2A2140),
    outline = Brand.Outline,
    outlineVariant = Color(0xFF241C36),
    error = Color(0xFFFF7A8A),
)

private val Base = Typography()

private val Type = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.Bold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Bold),
)

/** The big gradient wordmark. */
val WordmarkStyle = TextStyle(
    brush = Brand.HorizontalGradient,
    fontSize = 38.sp,
    fontWeight = FontWeight.Black,
    letterSpacing = (-1.5).sp,
)

/** Always dark: the neon look is the point. */
@Composable
fun RadioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
