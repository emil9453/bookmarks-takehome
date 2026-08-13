package az.bookmarks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Corner radii taken from the reference: cards and tiles are generously rounded, and anything
 * pill-shaped is fully rounded rather than "quite round".
 */
private val BirbankShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Both schemes are written out rather than derived from a seed colour. Material's dynamic and
 * generated schemes are what produce the stock purple; naming every role means there is no slot
 * left for one to appear in — including the ones this app does not draw today, which is why
 * `surfaceVariant`, `outline` and the error roles are set too.
 */
private val LightScheme = lightColorScheme(
    primary = Birbank.Red,
    onPrimary = Birbank.SurfaceLight,
    primaryContainer = Birbank.Red,
    onPrimaryContainer = Birbank.SurfaceLight,
    secondary = Birbank.Ink,
    onSecondary = Birbank.SurfaceLight,
    // The filled pill behind the search field, and the inert filled chips.
    secondaryContainer = Birbank.FillLight,
    onSecondaryContainer = Birbank.Ink,
    tertiary = Birbank.Red,
    onTertiary = Birbank.SurfaceLight,
    background = Birbank.PageLight,
    onBackground = Birbank.Ink,
    surface = Birbank.SurfaceLight,
    onSurface = Birbank.Ink,
    surfaceVariant = Birbank.FillLight,
    onSurfaceVariant = Birbank.Grey,
    surfaceContainerLowest = Birbank.SurfaceLight,
    surfaceContainerLow = Birbank.SurfaceLight,
    surfaceContainer = Birbank.FillLight,
    surfaceContainerHigh = Birbank.FillLight,
    surfaceContainerHighest = Birbank.FillLight,
    outline = Birbank.Hairline,
    outlineVariant = Birbank.Hairline,
    error = Birbank.Red,
    onError = Birbank.SurfaceLight,
    errorContainer = Birbank.FillLight,
    onErrorContainer = Birbank.Red,
    inverseSurface = Birbank.Ink,
    inverseOnSurface = Birbank.SurfaceLight,
)

private val DarkScheme = darkColorScheme(
    primary = Birbank.RedDark,
    onPrimary = Birbank.PageDark,
    primaryContainer = Birbank.RedDark,
    onPrimaryContainer = Birbank.PageDark,
    secondary = Birbank.InkDark,
    onSecondary = Birbank.PageDark,
    secondaryContainer = Birbank.FillDark,
    onSecondaryContainer = Birbank.InkDark,
    tertiary = Birbank.RedDark,
    onTertiary = Birbank.PageDark,
    background = Birbank.PageDark,
    onBackground = Birbank.InkDark,
    surface = Birbank.SurfaceDark,
    onSurface = Birbank.InkDark,
    surfaceVariant = Birbank.FillDark,
    onSurfaceVariant = Birbank.GreyDark,
    surfaceContainerLowest = Birbank.SurfaceDark,
    surfaceContainerLow = Birbank.SurfaceDark,
    surfaceContainer = Birbank.FillDark,
    surfaceContainerHigh = Birbank.FillDark,
    surfaceContainerHighest = Birbank.FillDark,
    outline = Birbank.HairlineDark,
    outlineVariant = Birbank.HairlineDark,
    error = Birbank.RedDark,
    onError = Birbank.PageDark,
    errorContainer = Birbank.FillDark,
    onErrorContainer = Birbank.RedDark,
    inverseSurface = Birbank.InkDark,
    inverseOnSurface = Birbank.PageDark,
)

@Composable
fun BirBookmarksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = BirbankTypography,
        shapes = BirbankShapes,
        content = content,
    )
}
