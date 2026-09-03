package app.pocketshell.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * PocketShell design system "Quiet Aurora" — color tokens (docs/UI-REDESIGN.md §3.1).
 *
 * Cool graphite surfaces, one soft periwinkle-violet accent, semantics
 * (ok/warn/error) reserved for state. Terminal canvas stays near-black ink
 * in every app theme — the "framed ink" terminal look.
 */

// ---- brand accent -----------------------------------------------------------

val AuroraAccent = Color(0xFF93A6F5)
val AuroraAccentDim = Color(0xFF3D4EA8)
val AuroraOnAccent = Color(0xFF101631)
val AuroraAccentContainer = Color(0xFF232C52)
val AuroraOnAccentContainer = Color(0xFFDCE2FF)
val AuroraAccentContainerLight = Color(0xFFDFE4FF)

// ---- dark surfaces ----------------------------------------------------------

val InkBg = Color(0xFF0B0D12)
val InkSurfaceLowest = Color(0xFF090A0F)
val InkSurfaceLow = Color(0xFF12151D)
val InkSurface = Color(0xFF171B25)
val InkSurfaceHigh = Color(0xFF1D2230)
val InkSurfaceHighest = Color(0xFF242A3B)
val InkOnSurface = Color(0xFFE4E7F2)
val InkOnSurfaceVariant = Color(0xFF9AA1B8)
val InkOutline = Color(0xFF2C3347)

// ---- light surfaces ---------------------------------------------------------

val PaperBg = Color(0xFFF7F8FC)
val PaperSurface = Color(0xFFFFFFFF)
val PaperSurfaceLow = Color(0xFFEEF0F7)
val PaperSurfaceHigh = Color(0xFFE4E7F1)
val PaperSurfaceHighest = Color(0xFFD9DDEA)
val PaperOnSurface = Color(0xFF171A24)
val PaperOnSurfaceVariant = Color(0xFF5A6072)
val PaperOutline = Color(0xFFC7CCDD)

// ---- AMOLED -----------------------------------------------------------------

val AmoledBg = Color(0xFF000000)
val AmoledSurfaceLow = Color(0xFF050505)
val AmoledSurface = Color(0xFF0A0A0F)
val AmoledSurfaceHigh = Color(0xFF101018)
val AmoledSurfaceHighest = Color(0xFF16161F)

// ---- semantics --------------------------------------------------------------

val OkGreen = Color(0xFF7CC7A5)
val OkGreenContainer = Color(0xFF17342A)
val WarnAmber = Color(0xFFE8C07A)
val WarnAmberContainer = Color(0xFF3A2F17)
val ErrorSoft = Color(0xFFF0A9A5)
val ErrorSoftContainer = Color(0xFF3D1F1F)
val ErrorLight = Color(0xFFB3261E)
val ErrorLightContainer = Color(0xFFF9DEDC)
val OkLight = Color(0xFF1E6B4E)
val WarnLight = Color(0xFF7A5A16)

// ---- terminal canvas (framed ink, every theme) ------------------------------

val TerminalCanvas = Color(0xFF0C0E12)
val TerminalCanvasFrame = Color(0xFF12151D)

// ---- hero gradient (the ONE gradient — docs/UI-REDESIGN.md §3.1) ------------

val HeroGradientDarkStart = Color(0xFF1B2238)
val HeroGradientDarkEnd = Color(0xFF26304F)
val HeroGradientLightStart = Color(0xFFE7EBFF)
val HeroGradientLightEnd = Color(0xFFD6DEFF)

// ---- brand logo gradient ----------------------------------------------------

val LogoGradientStart = Color(0xFF7C93F0)
val LogoGradientEnd = Color(0xFF5F6FD6)

// ---- schemes ----------------------------------------------------------------

val QuietAuroraDark = darkColorScheme(
    primary = AuroraAccent,
    onPrimary = AuroraOnAccent,
    primaryContainer = AuroraAccentContainer,
    onPrimaryContainer = AuroraOnAccentContainer,
    secondary = OkGreen,
    onSecondary = Color(0xFF07231A),
    secondaryContainer = OkGreenContainer,
    onSecondaryContainer = Color(0xFFCDEEDF),
    tertiary = WarnAmber,
    onTertiary = Color(0xFF2E2106),
    tertiaryContainer = WarnAmberContainer,
    onTertiaryContainer = Color(0xFFF7E3C0),
    error = ErrorSoft,
    onError = Color(0xFF3A1110),
    errorContainer = ErrorSoftContainer,
    onErrorContainer = Color(0xFFF9DEDC),
    background = InkBg,
    onBackground = InkOnSurface,
    surface = InkSurface,
    onSurface = InkOnSurface,
    onSurfaceVariant = InkOnSurfaceVariant,
    surfaceContainerLowest = InkSurfaceLowest,
    surfaceContainerLow = InkSurfaceLow,
    surfaceContainer = InkSurface,
    surfaceContainerHigh = InkSurfaceHigh,
    surfaceContainerHighest = InkSurfaceHighest,
    surfaceDim = InkBg,
    surfaceBright = InkSurfaceHighest,
    outline = InkOutline,
    outlineVariant = InkOutline,
)

val QuietAuroraLight = lightColorScheme(
    primary = AuroraAccentDim,
    onPrimary = Color.White,
    primaryContainer = AuroraAccentContainerLight,
    onPrimaryContainer = Color(0xFF101A45),
    secondary = OkLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F0E4),
    onSecondaryContainer = Color(0xFF0A2B1F),
    tertiary = WarnLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7EBCF),
    onTertiaryContainer = Color(0xFF3A2B08),
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorLightContainer,
    onErrorContainer = Color(0xFF410E0B),
    background = PaperBg,
    onBackground = PaperOnSurface,
    surface = PaperSurface,
    onSurface = PaperOnSurface,
    onSurfaceVariant = PaperOnSurfaceVariant,
    surfaceContainerLowest = PaperSurface,
    surfaceContainerLow = PaperSurfaceLow,
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = PaperSurfaceHigh,
    surfaceContainerHighest = PaperSurfaceHighest,
    surfaceDim = PaperSurfaceLow,
    surfaceBright = PaperSurface,
    outline = PaperOutline,
    outlineVariant = PaperOutline,
)

val QuietAuroraAmoled = darkColorScheme(
    primary = AuroraAccent,
    onPrimary = AuroraOnAccent,
    primaryContainer = Color(0xFF1A2140),
    onPrimaryContainer = AuroraOnAccentContainer,
    secondary = OkGreen,
    onSecondary = Color(0xFF07231A),
    secondaryContainer = Color(0xFF0E231C),
    onSecondaryContainer = Color(0xFFCDEEDF),
    tertiary = WarnAmber,
    onTertiary = Color(0xFF2E2106),
    tertiaryContainer = Color(0xFF261D0C),
    onTertiaryContainer = Color(0xFFF7E3C0),
    error = ErrorSoft,
    onError = Color(0xFF3A1110),
    errorContainer = Color(0xFF2A1514),
    onErrorContainer = Color(0xFFF9DEDC),
    background = AmoledBg,
    onBackground = InkOnSurface,
    surface = AmoledSurface,
    onSurface = InkOnSurface,
    onSurfaceVariant = InkOnSurfaceVariant,
    surfaceContainerLowest = AmoledBg,
    surfaceContainerLow = AmoledSurfaceLow,
    surfaceContainer = AmoledSurface,
    surfaceContainerHigh = AmoledSurfaceHigh,
    surfaceContainerHighest = AmoledSurfaceHighest,
    surfaceDim = AmoledBg,
    surfaceBright = AmoledSurfaceHigh,
    outline = Color(0xFF232838),
    outlineVariant = Color(0xFF232838),
)
