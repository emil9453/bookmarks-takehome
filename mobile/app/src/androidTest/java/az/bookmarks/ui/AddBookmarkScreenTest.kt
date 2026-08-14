package az.bookmarks.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import az.bookmarks.ui.theme.BirBookmarksTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The add-then-return path, which is the one the unit tests structurally cannot reach: whether the
 * screen actually leaves when the save succeeds is a fact about the composition, not the ViewModel.
 * A `saved` flag that flips and a screen that stays put would pass every test in the JVM suite.
 */
class AddBookmarkScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var savedCalled = false

    private fun show(api: FakeBookmarksApi) = compose.setContent {
        BirBookmarksTheme {
            AddBookmarkScreen(
                onSaved = { savedCalled = true },
                onCancel = {},
                viewModel = AddBookmarkViewModel(api = api),
            )
        }
    }

    private fun field(index: Int) = compose.onAllNodes(hasSetTextAction())[index]

    @Test
    fun aBlankFormIsRejectedWithoutASingleRequestOrLeavingTheScreen() {
        val api = FakeBookmarksApi()
        show(api)

        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("A link is required.").assertIsDisplayed()
        compose.onNodeWithText("A title is required.").assertIsDisplayed()
        assertTrue("nothing should have been sent", api.created.isEmpty())
        assertFalse("the screen must not leave on a rejected save", savedCalled)
    }

    @Test
    fun savingSendsWhatWasTypedAndThenLeavesTheScreen() {
        val api = FakeBookmarksApi()
        show(api)

        field(URL).performTextInput("https://example.com/added")
        field(TITLE).performTextInput("Added from a test")
        field(TAG_DRAFT).performTextInput("instrumented")
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Save to favourites").performClick()

        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(TIMEOUT) { savedCalled }

        val sent = api.created.single()
        assertEquals("https://example.com/added", sent.url)
        assertEquals("Added from a test", sent.title)
        assertEquals(listOf("instrumented"), sent.tags)
        // The tick was hardcoded false until recently, so this is the assertion that keeps it wired.
        assertTrue("the favourite tick must reach the request", sent.favourite)
    }

    private companion object {
        const val URL = 0
        const val TITLE = 1
        const val TAG_DRAFT = 2
        const val TIMEOUT = 5_000L
    }
}
