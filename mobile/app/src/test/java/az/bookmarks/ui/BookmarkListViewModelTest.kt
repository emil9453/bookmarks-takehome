package az.bookmarks.ui

import az.bookmarks.data.Bookmark
import az.bookmarks.data.BookmarksApi
import az.bookmarks.data.PageMeta
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

/**
 * The paging state machine. A cold review found a crash in here that the build could not see:
 * a refresh landing while a next-page request was in flight appended the same page twice, and
 * duplicate ids make `LazyColumn`'s keyed items throw. These tests are what would have caught it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun anEmptyFirstPageIsTheEmptyStateNotAnEmptyList() = runTest(dispatcher) {
        val api = FakeApi().apply { pages[0] = page(ids = IntRange.EMPTY, number = 0, totalPages = 0) }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()

        assertEquals(BookmarksUiState.Empty, viewModel.state.value)
    }

    @Test
    fun theFirstPageBecomesDataWithTheCursorOnTheNextPage() = runTest(dispatcher) {
        val api = FakeApi().apply { pages[0] = page(ids = 1..20, number = 0, totalPages = 3) }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()

        val state = viewModel.state.value as BookmarksUiState.Data
        assertEquals(20, state.bookmarks.size)
        assertEquals(1, state.nextPage)
        assertTrue(state.hasMore)
        assertFalse(state.loadingMore)
    }

    @Test
    fun loadMoreAppendsTheNextPageAndStopsAtTheEnd() = runTest(dispatcher) {
        val api = FakeApi().apply {
            pages[0] = page(ids = 1..20, number = 0, totalPages = 2)
            pages[1] = page(ids = 21..25, number = 1, totalPages = 2)
        }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        val state = viewModel.state.value as BookmarksUiState.Data
        assertEquals(25, state.bookmarks.size)
        assertEquals((1L..25L).toList(), state.bookmarks.map(Bookmark::id))
        assertFalse("the last page must not offer another", state.hasMore)
    }

    /**
     * The regression test for the crash. A next-page request is in flight, a refresh lands and
     * replaces the list, and then the footer scrolls back into view and asks for more. Before the
     * fix this issued two requests for page 1 and appended both, producing duplicate ids.
     */
    @Test
    fun aRefreshDuringLoadMoreCannotAppendTheSamePageTwice() = runTest(dispatcher) {
        val api = FakeApi().apply {
            pages[0] = page(ids = 1..20, number = 0, totalPages = 3)
            pages[1] = page(ids = 21..40, number = 1, totalPages = 3)
            suspendOnPage = 1
        }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()

        viewModel.loadMore()          // page 1 goes out and blocks
        advanceUntilIdle()
        viewModel.refresh()           // replaces the list under it
        advanceUntilIdle()
        viewModel.loadMore()          // footer back in view, asks again
        advanceUntilIdle()

        api.release()                 // both page-1 requests are now free to return
        advanceUntilIdle()

        val state = viewModel.state.value as BookmarksUiState.Data
        val ids = state.bookmarks.map(Bookmark::id)
        assertEquals("a page was appended twice", ids.distinct(), ids)
        assertEquals(40, ids.size)
        assertEquals(2, state.nextPage)
    }

    @Test
    fun aFailedRefreshKeepsTheRowsAndReportsSeparately() = runTest(dispatcher) {
        val api = FakeApi().apply { pages[0] = page(ids = 1..20, number = 0, totalPages = 3) }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()

        api.failWith = IOException("offline")
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value as BookmarksUiState.Data
        assertEquals("a failed refresh must not empty the list", 20, state.bookmarks.size)
        assertFalse(state.refreshing)
        assertEquals(
            "Can't reach the server. Check your connection and try again.",
            state.refreshError,
        )

        viewModel.refreshErrorShown()
        assertNull((viewModel.state.value as BookmarksUiState.Data).refreshError)
    }

    @Test
    fun aFailedFirstLoadIsTheErrorStateBecauseThereIsNothingToKeep() = runTest(dispatcher) {
        val api = FakeApi().apply { failWith = IOException("offline") }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()

        val state = viewModel.state.value as BookmarksUiState.Error
        assertTrue(state.message.contains("Can't reach the server"))
    }

    @Test
    fun aFailedNextPageKeepsTheRowsAndOffersARetry() = runTest(dispatcher) {
        val api = FakeApi().apply { pages[0] = page(ids = 1..20, number = 0, totalPages = 3) }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()

        api.failWith = IOException("offline")
        viewModel.loadMore()
        advanceUntilIdle()

        val state = viewModel.state.value as BookmarksUiState.Data
        assertEquals(20, state.bookmarks.size)
        assertFalse(state.loadingMore)
        assertTrue(state.loadMoreFailed)
        assertEquals("the cursor must not advance past a page that failed", 1, state.nextPage)
    }
}

private class FakeApi : BookmarksApi {

    val pages = mutableMapOf<Int, PagedResponse<Bookmark>>()
    var failWith: Throwable? = null

    /** Requests for this page block until [release], so a race can be arranged deterministically. */
    var suspendOnPage: Int? = null
    private val gate = CompletableDeferred<Unit>()

    val requestedPages = mutableListOf<Int>()

    fun release() = gate.complete(Unit).let { }

    override suspend fun list(page: Int, size: Int): PagedResponse<Bookmark> {
        requestedPages += page
        if (suspendOnPage == page) gate.await()
        failWith?.let { throw it }
        return pages.getValue(page)
    }
}

private fun page(ids: IntRange, number: Int, totalPages: Int) = PagedResponse(
    content = ids.map { bookmark(it.toLong()) },
    page = PageMeta(size = 20, number = number, totalElements = 60, totalPages = totalPages),
)

private fun bookmark(id: Long) = Bookmark(
    id = id,
    url = "https://example.com/$id",
    title = "Bookmark $id",
    tags = listOf("test"),
    notes = null,
    favourite = false,
    createdAt = "2026-08-12T00:00:00Z",
    updatedAt = "2026-08-12T00:00:00Z",
)
