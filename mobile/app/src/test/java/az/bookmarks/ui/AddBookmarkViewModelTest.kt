package az.bookmarks.ui

import androidx.lifecycle.SavedStateHandle
import az.bookmarks.data.Bookmark
import az.bookmarks.data.BookmarkPatch
import az.bookmarks.data.BookmarksApi
import az.bookmarks.data.NewBookmark
import az.bookmarks.data.PagedResponse
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AddBookmarkViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aBlankTitleAndABadLinkAreCaughtWithoutASingleRequest() = runTest(dispatcher) {
        val api = FakeCreateApi()
        val viewModel = AddBookmarkViewModel(api = api)

        viewModel.onUrlChange("ftp://files.example.com")
        viewModel.save()
        advanceUntilIdle()

        val form = viewModel.form.value
        assertEquals("Must be a http or https link.", form.urlError)
        assertEquals("A title is required.", form.titleError)
        assertTrue("nothing should have been sent", api.created.isEmpty())
        assertFalse(form.saved)
    }

    @Test
    fun aValidFormIsTrimmedAndBlankNotesBecomeNull() = runTest(dispatcher) {
        val api = FakeCreateApi()
        val viewModel = AddBookmarkViewModel(api = api)

        viewModel.onUrlChange("  https://kotlinlang.org  ")
        viewModel.onTitleChange("  Kotlin  ")
        viewModel.onNotesChange("   ")
        viewModel.onTagDraftChange("kotlin")
        viewModel.onTagConfirmed()
        viewModel.save()
        advanceUntilIdle()

        assertEquals(1, api.created.size)
        val sent = api.created.single()
        assertEquals("https://kotlinlang.org", sent.url)
        assertEquals("Kotlin", sent.title)
        assertEquals(listOf("kotlin"), sent.tags)
        assertNull("blank notes should be absent, not an empty string", sent.notes)
        assertTrue(viewModel.form.value.saved)
    }

    @Test
    fun tagsAreConfirmedOneAtATimeDeduplicatedAndRemovable() = runTest(dispatcher) {
        val viewModel = AddBookmarkViewModel(api = FakeCreateApi())

        viewModel.onTagDraftChange("kotlin")
        viewModel.onTagConfirmed()
        viewModel.onTagDraftChange(" kotlin ")
        viewModel.onTagConfirmed()
        viewModel.onTagDraftChange("android")
        viewModel.onTagConfirmed()

        assertEquals(listOf("kotlin", "android"), viewModel.form.value.tags)
        assertEquals("", viewModel.form.value.tagDraft)

        viewModel.onTagRemoved("kotlin")
        assertEquals(listOf("android"), viewModel.form.value.tags)
    }

    /** The whole point of parsing the problem detail: the message lands under the right box. */
    @Test
    fun aBackendRejectionIsShownAgainstTheFieldItNamed() = runTest(dispatcher) {
        val api = FakeCreateApi().apply {
            failWith = httpError(
                status = 400,
                body = """
                    {"title":"Bad Request","status":400,"detail":"The request has 1 invalid field.",
                     "errors":{"title":"must not be blank"}}
                """.trimIndent(),
            )
        }
        val viewModel = AddBookmarkViewModel(api = api)

        viewModel.onUrlChange("https://example.com")
        viewModel.onTitleChange("passes here, rejected there")
        viewModel.save()
        advanceUntilIdle()

        val form = viewModel.form.value
        assertEquals("must not be blank", form.titleError)
        assertNull("a field-level failure must not also shout at the form", form.formError)
        assertFalse(form.saving)
        assertFalse(form.saved)
    }

    @Test
    fun aRejectionNamingAnUnknownFieldIsSurfacedRatherThanSwallowed() = runTest(dispatcher) {
        val api = FakeCreateApi().apply {
            failWith = httpError(400, """{"errors":{"somethingElse":"is wrong"}}""")
        }
        val viewModel = AddBookmarkViewModel(api = api)

        viewModel.onUrlChange("https://example.com")
        viewModel.onTitleChange("Title")
        viewModel.save()
        advanceUntilIdle()

        assertEquals("somethingElse: is wrong", viewModel.form.value.formError)
    }

    @Test
    fun aNetworkFailureBecomesAFormLevelMessageAndTheFormStaysUsable() = runTest(dispatcher) {
        val api = FakeCreateApi().apply { failWith = IOException("offline") }
        val viewModel = AddBookmarkViewModel(api = api)

        viewModel.onUrlChange("https://example.com")
        viewModel.onTitleChange("Title")
        viewModel.save()
        advanceUntilIdle()

        val form = viewModel.form.value
        assertTrue(form.formError.orEmpty().contains("Can't reach the server"))
        assertFalse("the button has to come back", form.saving)
        assertFalse(form.saved)
    }

    /** Double submission is the sort of thing that gets noticed in a demo. */
    @Test
    fun tappingSaveTwiceSendsOneRequest() = runTest(dispatcher) {
        val api = FakeCreateApi().apply { blockCreate = true }
        val viewModel = AddBookmarkViewModel(api = api)

        viewModel.onUrlChange("https://example.com")
        viewModel.onTitleChange("Title")
        viewModel.save()
        advanceUntilIdle()
        viewModel.save()
        viewModel.save()
        advanceUntilIdle()

        assertEquals(1, api.created.size)

        api.release()
        advanceUntilIdle()
        assertEquals(1, api.created.size)
        assertTrue(viewModel.form.value.saved)
    }

    @Test
    fun editingAFieldClearsItsErrorSoAFixLooksLikeAFix() = runTest(dispatcher) {
        val viewModel = AddBookmarkViewModel(api = FakeCreateApi())

        viewModel.save()
        advanceUntilIdle()
        assertEquals("A link is required.", viewModel.form.value.urlError)

        viewModel.onUrlChange("https://example.com")
        assertNull(viewModel.form.value.urlError)
    }

    @Test
    fun whatWasTypedSurvivesProcessDeath() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val typing = AddBookmarkViewModel(saved, FakeCreateApi())

        typing.onUrlChange("https://example.com/half-written")
        typing.onTitleChange("Half written")
        typing.onNotesChange("Came back to it later.")
        typing.onTagDraftChange("draft")
        typing.onTagConfirmed()
        typing.onFavouriteChange(true)

        // The process dies here. Navigation restores the Add screen either way, so without the
        // handle the user comes back to the screen they left with every field silently blank.
        val restored = AddBookmarkViewModel(saved, FakeCreateApi()).form.value

        assertEquals("https://example.com/half-written", restored.url)
        assertEquals("Half written", restored.title)
        assertEquals("Came back to it later.", restored.notes)
        assertEquals(listOf("draft"), restored.tags)
        assertTrue(restored.favourite)
    }

    @Test
    fun aSaveInFlightIsNotRestoredAsAStuckButton() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val api = FakeCreateApi().apply { blockCreate = true }
        val viewModel = AddBookmarkViewModel(saved, api)

        viewModel.onUrlChange("https://example.com")
        viewModel.onTitleChange("Fine")
        viewModel.save()
        advanceUntilIdle()
        assertTrue("precondition: the save is still in flight", viewModel.form.value.saving)

        // Restoring `saving` would come back to a disabled button with no request behind it.
        assertFalse(AddBookmarkViewModel(saved, FakeCreateApi()).form.value.saving)
        api.release()
    }

    @Test
    fun theFavouriteTickIsWhatGetsSent() = runTest(dispatcher) {
        val api = FakeCreateApi()
        val viewModel = AddBookmarkViewModel(api = api)

        viewModel.onUrlChange("https://example.com")
        viewModel.onTitleChange("Starred")
        viewModel.onFavouriteChange(true)
        viewModel.save()
        advanceUntilIdle()

        assertTrue("the tick must reach the request", api.created.single().favourite)
    }
}

private fun httpError(status: Int, body: String) = HttpException(
    Response.error<Unit>(status, body.toResponseBody("application/problem+json".toMediaType())),
)

private class FakeCreateApi : BookmarksApi {

    val created = mutableListOf<NewBookmark>()
    var failWith: Throwable? = null
    var blockCreate = false
    private val gate = CompletableDeferred<Unit>()

    fun release() {
        gate.complete(Unit)
    }

    override suspend fun create(request: NewBookmark): Bookmark {
        created += request
        if (blockCreate) gate.await()
        failWith?.let { throw it }
        return Bookmark(
            id = 1L,
            url = request.url,
            title = request.title,
            tags = request.tags,
            notes = request.notes,
            favourite = request.favourite,
            createdAt = "2026-08-13T00:00:00Z",
            updatedAt = "2026-08-13T00:00:00Z",
        )
    }

    override suspend fun list(
        page: Int,
        size: Int,
        q: String?,
        tag: String?,
        favourite: Boolean?,
    ): PagedResponse<Bookmark> = throw UnsupportedOperationException()

    override suspend fun get(id: Long): Bookmark = throw UnsupportedOperationException()

    override suspend fun update(id: Long, request: BookmarkPatch): Bookmark =
        throw UnsupportedOperationException()

    override suspend fun delete(id: Long) = throw UnsupportedOperationException()
}
