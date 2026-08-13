package az.bookmarks.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import az.bookmarks.R

/**
 * Onest, the Birbank typeface. Open Font Licence, so the file ships inside the APK — no dependency,
 * no downloadable-fonts provider, nothing to license. `res/font/onest_ofl_license.txt` travels with
 * it, which is what the licence asks for.
 *
 * It is the single variable font rather than four static cuts: one 193KB file covers every weight,
 * where four static instances would be closer to 500KB. `FontVariation` needs API 26, and minSdk is
 * 26, so nothing has to fall back.
 */
@OptIn(ExperimentalTextApi::class)
private fun onest(weight: FontWeight) = Font(
    resId = R.font.onest,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

internal val Onest = FontFamily(
    onest(FontWeight.Normal),
    onest(FontWeight.Medium),
    onest(FontWeight.SemiBold),
    onest(FontWeight.Bold),
)

/**
 * Sizes and weights are deliberately spread rather than one size everywhere: a screen title at 22,
 * a card title at 16 medium, body at 14, and captions at 12 in the muted grey. That spread is what
 * makes the hierarchy readable without any rules or boxes.
 *
 * Only the styles this app actually uses are overridden. Anything left at the Material default here
 * is a style nothing renders.
 */
internal val BirbankTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)
