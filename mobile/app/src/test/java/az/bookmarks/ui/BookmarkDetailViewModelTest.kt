package az.bookmarks.ui

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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadShowsTheBookmarkTheServerHasRatherThanWhateverTheListRowHeld() = runTest(dispatcher) {
        val api = FakeDetailApi(bookmark(7L, favourite = true))
        val viewModel = BookmarkDetailViewModel(api)

        viewModel.load(7L)
        advanceUntilIdle()

        val state = viewModel.state.value as BookmarkDetailUiState.Loaded
        assertEquals(7L, state.bookmark.id)
        assertTrue(state.bookmark.favourite)
        assertEquals(listOf(7L), api.fetched)
    }

    @Test
    fun aFailedLoadIsAnErrorStateWithARetry() = runTest(dispatcher) {
        val api = FakeDetailApi(bookmark(7L)).apply { failWith = IOException("offline") }
        val viewModel = BookmarkDetailViewModel(api)

        viewModel.load(7L)
        advanceUntilIdle()

        val state = viewModel.state.value as BookmarkDetailUiState.Error
        assertTrue(state.message.contains("Can't reach the server"))

        api.failWith = null
        viewModel.load(7L)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is BookmarkDetailUiState.Loaded)
    }

    /** The toggle sends one field, and shows what came back rather than what it hoped for. */
    @Test
    fun togglingFavouriteSendsOnlyThatFieldAndShowsTheConfirmedValue() = runTest(dispatcher) {
        val api = FakeDetailApi(bookmark(7L, favourite = false))
        val viewModel = BookmarkDetailViewModel(api)
        viewModel.load(7L)
        advanceUntilIdle()

        viewModel.toggleFavourite()
        advanceUntilIdle()

        val patch = api.patches.single()
        assertEquals(true, patch.favourite)
        assertNull("a full-object update could clobber a change made elsewhere", patch.title)
        assertNull(patch.url)
        assertNull(patch.tags)
        assertNull(patch.notes)

        val state = viewModel.state.value as BookmarkDetailUiState.Loaded
        assertTrue(state.bookmark.favourite)
        assertFalse(state.busy)
    }

    @Test
    fun aFailedToggleLeavesTheStarWhereTheServerHasItAndSaysWhy() = runTest(dispatcher) {
        val api = FakeDetailApi(bookmark(7L, favourite = false))
        val viewModel = BookmarkDetailViewModel(api)
        viewModel.load(7L)
        advanceUntilIdle()

        api.failWith = IOException("offline")
        viewModel.toggleFavourite()
        advanceUntilIdle()

        val state = viewModel.state.value as BookmarkDetailUiState.Loaded
        assertFalse("the star must not show a change the server rejected", state.bookmark.favourite)
        assertFalse(state.busy)
        assertTrue(state.actionError.orEmpty().contains("Can't reach the server"))

        viewModel.actionErrorShown()
        assertNull((viewModel.state.value as BookmarkDetailUiState.Loaded).actionError)
    }

    @Test
    fun tappingTheToggleTwiceSendsOneRequest() = runTest(dispatcher) {
        val api = FakeDetailApi(bookmark(7L)).apply { blockActions = true }
        val viewModel = BookmarkDetailViewModel(api)
        viewModel.load(7L)
        advanceUntilIdle()

        viewModel.toggleFavourite()
        advanceUntilIdle()
        viewModel.toggleFavourite()
        viewModel.toggleFavourite()
        advanceUntilIdle()

        assertEquals(1, api.patches.size)
    }

    /**
     * Regression test for the silent failure a cold review found. The screen loads from a
     * `LaunchedEffect`, which re-runs on every Activity recreation — rotation, dark mode, font
     * size, multi-window. An unconditional reload there wiped `busy` while a toggle was still in
     * flight, so the controls came back enabled and the next tap hit the in-flight guard and did
     * nothing whatsoever.
     */
    @Test
    fun aRepeatedLoadForTheSameIdLeavesAnInFlightActionAlone() = runTest(dispatcher) {
        val api = FakeDetailApi(bookmark(7L)).apply { blockActions = true }
        val viewModel = BookmarkDetailViewModel(api)
        viewModel.load(7L)
        advanceUntilIdle()

        viewModel.toggleFavourite()
        advanceUntilIdle()
        assertTrue((viewModel.state.value as BookmarkDetailUiState.Loaded).busy)

        viewModel.load(7L)          // what an Activity recreation does
        advanceUntilIdle()

        assertTrue(
            "a recreation must not re-enable the controls while the request is open",
            (viewModel.state.value as BookmarkDetailUiState.Loaded).busy,
        )
        assertEquals("and must not refetch what is already shown", 1, api.fetched.size)

        // The request still completes and still wins.
        api.release()
        advanceUntilIdle()
        val state = viewModel.state.value as BookmarkDetailUiState.Loaded
        assertTrue(state.bookmark.favourite)
        assertFalse(state.busy)
    }

    @Test
    fun aDifferentIdStillLoads() = runTest(dispatcher) {
        val api = FakeDetailApi(bookmark(7L))
        val viewModel = BookmarkDetailViewModel(api)

        viewModel.load(7L)
        advanceUntilIdle()
        viewModel.load(8L)
        advanceUntilIdle()

        assertEquals(listOf(7L, 8L), api.fetched)
    }

    @Test
    fun deleteReachesTheDeletedStateSoTheScreenCanLeave() = runTest(dispatcher) {
        val api = FakeDetailApi(bookmark(7L))
        val viewModel = BookmarkDetailViewModel(api)
        viewModel.load(7L)
        advanceUntilIdle()

        viewModel.delete()
        advanceUntilIdle()

        assertEquals(BookmarkDetailUiState.Deleted, viewModel.state.value)
        assertEquals(listOf(7L), api.deleted)
    }

    @Test
    fun aFailedDeleteStaysOnTheBookmarkAndReportsIt() = runTest(dispatcher) {
        val api = FakeDetailApi(bookmark(7L)).apply { failWith = IOException("offline") }
        val viewModel = BookmarkDetailViewModel(api)
        api.failWith = null
        viewModel.load(7L)
        advanceUntilIdle()

        api.failWith = IOException("offline")
        viewModel.delete()
        advanceUntilIdle()

        val state = viewModel.state.value as BookmarkDetailUiState.Loaded
        assertEquals(7L, state.bookmark.id)
        assertFalse(state.busy)
        assertTrue(state.actionError.orEmpty().contains("Can't reach the server"))
    }
}

private fun bookmark(id: Long, favourite: Boolean = false) = Bookmark(
    id = id,
    url = "https://example.com/$id",
    title = "Bookmark $id",
    tags = listOf("kotlin"),
    notes = "notes for $id",
    favourite = favourite,
    createdAt = "2026-08-13T00:00:00Z",
    updatedAt = "2026-08-13T00:00:00Z",
)

private class FakeDetailApi(private var current: Bookmark) : BookmarksApi {

    val fetched = mutableListOf<Long>()
    val patches = mutableListOf<BookmarkPatch>()
    val deleted = mutableListOf<Long>()

    var failWith: Throwable? = null
    var blockActions = false
    private val gate = CompletableDeferred<Unit>()

    /** Lets a blocked toggle or delete finish, so a race can be arranged deterministically. */
    fun release() {
        gate.complete(Unit)
    }

    override suspend fun get(id: Long): Bookmark {
        fetched += id
        failWith?.let { throw it }
        return current
    }

    override suspend fun update(id: Long, request: BookmarkPatch): Bookmark {
        patches += request
        if (blockActions) gate.await()
        failWith?.let { throw it }
        current = current.copy(favourite = request.favourite ?: current.favourite)
        return current
    }

    override suspend fun delete(id: Long) {
        if (blockActions) gate.await()
        failWith?.let { throw it }
        deleted += id
    }

    override suspend fun list(
        page: Int,
        size: Int,
        q: String?,
        tag: String?,
        favourite: Boolean?,
    ): PagedResponse<Bookmark> = throw UnsupportedOperationException()

    override suspend fun create(request: NewBookmark): Bookmark =
        throw UnsupportedOperationException()
}
