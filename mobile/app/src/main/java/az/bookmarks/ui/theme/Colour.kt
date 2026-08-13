package az.bookmarks.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Birbank palette, measured off birbank.az and their app screenshot rather than recalled.
 *
 * Every colour the app draws comes from here. Nothing reads a Material default and nothing hard-
 * codes a hex at a call site, which is what makes "no stock purple survives anywhere" checkable
 * rather than a claim — the stock schemes are never constructed at all.
 *
 * One note on fidelity: their 152px app icon samples #FF0039, slightly hotter than the #EC3342 the
 * site uses for UI red. One red is better than two nearly identical ones, so the site value wins
 * and the mark is drawn in it.
 */
internal object Birbank {

    /** Reserved for what is actionable or live. Never a background behind body text. */
    val Red = Color(0xFFEC3342)

    /** Pressed and hovered states of the red, so the accent has somewhere darker to go. */
    val RedPressed = Color(0xFFC9202E)

    /** Body text and headings. */
    val Ink = Color(0xFF25282B)

    /** Secondary text: captions, URLs, inactive navigation labels. */
    val Grey = Color(0xFF9496AC)

    /** Hairlines, dividers and disabled foregrounds. */
    val Hairline = Color(0xFFCACCD1)

    /** The page. Cards sit on it in white. */
    val PageLight = Color(0xFFF4F5F7)

    /** Card and bar surfaces in light mode. */
    val SurfaceLight = Color(0xFFFFFFFF)

    /** The filled pill behind the search field, and other inert filled shapes. */
    val FillLight = Color(0xFFEFF0F3)

    /*
     * Dark mode is picked, not inverted. Inverting the light palette gives a mid-grey page with
     * washed-out red; these are chosen so the same red stays legible on them — a near-black page
     * with a lifted card surface, which is also how their own dark surfaces behave.
     */

    val PageDark = Color(0xFF15171A)
    val SurfaceDark = Color(0xFF1E2125)
    val FillDark = Color(0xFF2A2E33)
    val InkDark = Color(0xFFF2F3F5)

    /** Slightly lifted from the light-mode grey: #9496AC is too dim on a near-black page. */
    val GreyDark = Color(0xFFA8AABE)

    val HairlineDark = Color(0xFF34383E)

    /** The red lifted just enough to hold contrast against the dark page. */
    val RedDark = Color(0xFFFF4D5B)
}
