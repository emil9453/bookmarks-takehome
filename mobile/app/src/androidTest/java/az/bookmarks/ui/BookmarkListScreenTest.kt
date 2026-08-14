package az.bookmarks.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import az.bookmarks.ui.theme.BirBookmarksTheme
import org.junit.Rule
import org.junit.Test

/**
 * The states the brief names, asserted against a real composition.
 *
 * The ViewModel tests already prove which `BookmarksUiState` is produced. What they cannot see is
 * whether the screen draws the matching thing — and the empty state in particular had never been
 * rendered anywhere, because reaching it on the deployed instance means deleting every bookmark.
 */
class BookmarkListScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(api: FakeBookmarksApi) = compose.setContent {
        BirBookmarksTheme {
            BookmarkListScreen(
                onOpenBookmark = {},
                onAddBookmark = {},
                viewModel = BookmarkListViewModel(api),
            )
        }
    }

    @Test
    fun nothingSavedYetIsADifferentScreenFromNothingMatched() {
        show(FakeBookmarksApi(rows = emptyList()))

        // Nothing saved and nothing searched: the first-run state, which has to offer the way out
        // rather than blame a search the user never made.
        compose.awaitText("Nothing saved yet")
        compose.onNodeWithText("Add your first bookmark").assertIsDisplayed()
    }

    @Test
    fun aSearchThatMatchesNothingNamesWhatWasSearchedFor() {
        show(FakeBookmarksApi(rows = listOf(bookmark(1, "Kotlin coroutines"))))
        compose.awaitText("Kotlin coroutines")

        compose.onNode(hasSetTextAction()).performTextInput("zzqqxx")

        // Distinct from the empty state above: there *are* bookmarks, this one just missed. The
        // query is quoted back so it is obvious which search produced nothing.
        compose.awaitText("No matches")
        compose.onNodeWithText("Nothing saved here matches \"zzqqxx\".").assertIsDisplayed()
        compose.onNodeWithText("Clear search and filters").assertIsDisplayed()
    }

    @Test
    fun theErrorStateRetriesAndRecovers() {
        val api = FakeBookmarksApi(rows = listOf(bookmark(1, "Monolith First")))
        api.failList = true
        show(api)

        compose.awaitText("Couldn't load your bookmarks")
        // The message has to say which failure it was, or there is no way to tell whether retrying
        // is worth anything.
        compose.onNodeWithText("Can't reach the server. Check your connection and try again.")
            .assertIsDisplayed()

        api.failList = false
        compose.onNodeWithText("Try again").performClick()

        compose.awaitText("Monolith First")
    }
}

/** Waits for text to appear rather than assuming a frame has landed, which is what makes these stable. */
fun ComposeContentTestRule.awaitText(text: String, timeoutMillis: Long = 5_000) {
    waitUntil(timeoutMillis) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText(text).assertIsDisplayed()
}
