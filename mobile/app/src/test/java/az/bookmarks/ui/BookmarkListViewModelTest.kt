package az.bookmarks.ui

import az.bookmarks.data.Bookmark
import az.bookmarks.data.BookmarkPatch
import az.bookmarks.data.BookmarksApi
import az.bookmarks.data.NewBookmark
import az.bookmarks.data.PageMeta
import az.bookmarks.data.PagedResponse
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
 * The paging and search state machine. A cold review found a crash in here that the build could
 * not see: a refresh landing while a next-page request was in flight appended the same page
 * twice, and duplicate ids make `LazyColumn`'s keyed items throw. These tests are what would have
 * caught it, plus the search behaviour the brief calls out specifically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // --- the list itself -------------------------------------------------------------------

    @Test
    fun anEmptyFirstPageWithNoQueryIsTheEmptyState() = runTest(dispatcher) {
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
        assertEquals((1L..25L).toList(), state.bookmarks.map(Bookmark::id))
        assertFalse("the last page must not offer another", state.hasMore)
    }

    /**
     * The regression test for the crash. A next-page request is in flight, a reload lands and
     * replaces the list, and then the footer scrolls back into view and asks for more. Before the
     * fix this issued two requests for page 1 and appended both, producing duplicate ids.
     */
    @Test
    fun aReloadDuringLoadMoreCannotAppendTheSamePageTwice() = runTest(dispatcher) {
        val api = FakeApi().apply {
            pages[0] = page(ids = 1..20, number = 0, totalPages = 3)
            pages[1] = page(ids = 21..40, number = 1, totalPages = 3)
            suspendOnPage = 1
        }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()

        viewModel.loadMore()          // page 1 goes out and blocks
        advanceUntilIdle()
        viewModel.reload()            // replaces the list under it
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
    fun aFailedReloadKeepsTheRowsAndReportsSeparately() = runTest(dispatcher) {
        val api = FakeApi().apply { pages[0] = page(ids = 1..20, number = 0, totalPages = 3) }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()

        api.failWith = IOException("offline")
        viewModel.reload()
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

    // --- search and filtering -------------------------------------------------------------

    /** The brief calls the debounce out specifically, so it gets a test that counts requests. */
    @Test
    fun typingFiresOneRequestAfterThePauseNotOnePerKeystroke() = runTest(dispatcher) {
        val api = FakeApi().apply { pages[0] = page(ids = 1..3, number = 0, totalPages = 1) }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()
        val afterFirstLoad = api.requests.size

        "kotlin".forEachIndexed { index, _ ->
            viewModel.onSearchTextChange("kotlin".take(index + 1))
            advanceTimeBy(50)          // faster than the 300ms debounce
        }
        assertEquals("a keystroke must not fire a request", afterFirstLoad, api.requests.size)

        advanceUntilIdle()             // now let the pause elapse
        assertEquals(afterFirstLoad + 1, api.requests.size)
        assertEquals("kotlin", api.requests.last().q)
    }

    /**
     * The out-of-order problem: a slow early query must never overwrite a newer one's results.
     * `flatMapLatest` is what guarantees it, by cancelling the superseded request.
     */
    @Test
    fun aSlowResponseForAnOldQueryCannotOverwriteANewerOne() = runTest(dispatcher) {
        val api = FakeApi().apply {
            pages[0] = page(ids = 1..3, number = 0, totalPages = 1)
            byQuery["slow"] = page(ids = 100..100, number = 0, totalPages = 1)
            byQuery["fast"] = page(ids = 200..200, number = 0, totalPages = 1)
            suspendOnQuery = "slow"
        }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()

        viewModel.onSearchTextChange("slow")
        advanceUntilIdle()                  // "slow" is in flight and blocked
        viewModel.onSearchTextChange("fast")
        advanceUntilIdle()                  // "fast" lands

        api.release()                       // the abandoned "slow" response comes back last
        advanceUntilIdle()

        val state = viewModel.state.value as BookmarksUiState.Data
        assertEquals(
            "the stale query's results won",
            listOf(200L),
            state.bookmarks.map(Bookmark::id),
        )
        assertEquals("fast", state.query.text)
    }

    @Test
    fun aSearchThatMatchesNothingIsNoResultsNotEmpty() = runTest(dispatcher) {
        val api = FakeApi().apply {
            pages[0] = page(ids = 1..3, number = 0, totalPages = 1)
            byQuery["nothing"] = page(ids = IntRange.EMPTY, number = 0, totalPages = 0)
        }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()

        viewModel.onSearchTextChange("nothing")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("a missed search must not claim nothing is saved", state is BookmarksUiState.NoResults)
        assertEquals("nothing", (state as BookmarksUiState.NoResults).query.text)
    }

    @Test
    fun searchAndBothFiltersCombineIntoOneRequest() = runTest(dispatcher) {
        val api = FakeApi().apply {
            pages[0] = page(ids = 1..3, number = 0, totalPages = 1)
            byQuery["kotlin"] = page(ids = 4..6, number = 0, totalPages = 1)
        }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()
        val afterFirstLoad = api.requests.size

        viewModel.onSearchTextChange("kotlin")
        viewModel.onFavouritesOnlyChange(true)
        viewModel.onTagChange("android")
        advanceUntilIdle()

        // Counted, not just inspected: asserting only on the last request would pass even if
        // every intermediate combination had been sent as its own search.
        val searches = api.requests.drop(afterFirstLoad)
        assertEquals("three settings, one request", 1, searches.size)
        assertEquals("kotlin", searches.single().q)
        assertEquals("android", searches.single().tag)
        assertEquals(true, searches.single().favourite)
    }

    /**
     * Regression test for a window a cold review found. The text and the filters used to be two
     * flows joined with `combine`, which re-emits using each branch's last *emitted* value — so a
     * chip tapped inside the debounce window fired a search with the pre-debounce text and briefly
     * showed results for a query nobody asked for.
     */
    @Test
    fun tappingAFilterWhileTypingNeverSearchesForTheOlderText() = runTest(dispatcher) {
        val api = FakeApi().apply {
            pages[0] = page(ids = 1..3, number = 0, totalPages = 1)
            byQuery["kot"] = page(ids = 7..9, number = 0, totalPages = 1)
        }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()
        val afterFirstLoad = api.requests.size

        viewModel.onSearchTextChange("kot")
        advanceTimeBy(100)                       // still inside the 300ms window
        viewModel.onFavouritesOnlyChange(true)   // the tap that used to fire with stale text
        advanceUntilIdle()

        val searches = api.requests.drop(afterFirstLoad)
        assertEquals("a filter tap must not fire its own search", 1, searches.size)
        assertEquals("kot", searches.single().q)
        assertEquals(true, searches.single().favourite)
    }

    @Test
    fun aTrailingSpaceIsNotASecondIdenticalSearch() = runTest(dispatcher) {
        val api = FakeApi().apply {
            pages[0] = page(ids = 1..3, number = 0, totalPages = 1)
            byQuery["kotlin"] = page(ids = 4..6, number = 0, totalPages = 1)
        }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()
        val afterFirstLoad = api.requests.size

        viewModel.onSearchTextChange("kotlin")
        advanceUntilIdle()
        viewModel.onSearchTextChange("kotlin ")
        advanceUntilIdle()

        assertEquals(1, api.requests.drop(afterFirstLoad).size)
    }

    @Test
    fun clearingTheOnlyRowUnderAFilterDoesNotClaimNothingIsSaved() = runTest(dispatcher) {
        // The state a delete under an active filter produces: Data, empty, query still set.
        val state = BookmarksUiState.Data(
            bookmarks = emptyList(),
            query = BookmarkQuery(tag = "mobile"),
            nextPage = 1,
            hasMore = false,
        )
        assertFalse(
            "an empty filtered list is not an empty app",
            state.query.isUnfiltered,
        )
    }

    @Test
    fun blankSearchAndUnsetFiltersAreOmittedRatherThanSentEmpty() = runTest(dispatcher) {
        val api = FakeApi().apply { pages[0] = page(ids = 1..3, number = 0, totalPages = 1) }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()

        val first = api.requests.first()
        assertNull("an empty q would filter on the empty string", first.q)
        assertNull(first.tag)
        assertNull("favourite=false would hide favourites, not stop filtering", first.favourite)
    }

    /** Page 2 of a search has to be page 2 of that search, not of the unfiltered list. */
    @Test
    fun loadMoreRepeatsTheFiltersThatProducedPageOne() = runTest(dispatcher) {
        val api = FakeApi().apply {
            pages[0] = page(ids = 1..3, number = 0, totalPages = 1)
            byQuery["kotlin"] = page(ids = 4..23, number = 0, totalPages = 2)
        }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()
        viewModel.onSearchTextChange("kotlin")
        viewModel.onFavouritesOnlyChange(true)
        advanceUntilIdle()

        api.byQuery["kotlin"] = page(ids = 24..25, number = 1, totalPages = 2)
        viewModel.loadMore()
        advanceUntilIdle()

        val nextPageRequest = api.requests.last()
        assertEquals(1, nextPageRequest.page)
        assertEquals("kotlin", nextPageRequest.q)
        assertEquals(true, nextPageRequest.favourite)
    }

    @Test
    fun tagsSeenSoFarAreOfferedAsFiltersSortedAndDeduplicated() = runTest(dispatcher) {
        val api = FakeApi().apply {
            pages[0] = PagedResponse(
                content = listOf(
                    bookmark(1L).copy(tags = listOf("kotlin", "android")),
                    bookmark(2L).copy(tags = listOf("android", "compose")),
                ),
                page = PageMeta(size = 20, number = 0, totalElements = 2, totalPages = 1),
            )
        }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()

        assertEquals(listOf("android", "compose", "kotlin"), viewModel.knownTags.value)
    }

    @Test
    fun clearingTheQueryGoesBackToThePlainList() = runTest(dispatcher) {
        val api = FakeApi().apply {
            pages[0] = page(ids = 1..3, number = 0, totalPages = 1)
            byQuery["nothing"] = page(ids = IntRange.EMPTY, number = 0, totalPages = 0)
        }

        val viewModel = BookmarkListViewModel(api)
        advanceUntilIdle()
        viewModel.onSearchTextChange("nothing")
        advanceUntilIdle()
        assertTrue(viewModel.state.value is BookmarksUiState.NoResults)

        viewModel.clearQuery()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is BookmarksUiState.Data)
        assertEquals(BookmarkQuery(), viewModel.query.value)
    }
}

private data class Request(
    val page: Int,
    val size: Int,
    val q: String?,
    val tag: String?,
    val favourite: Boolean?,
)

private class FakeApi : BookmarksApi {

    /** Responses for the unfiltered list, by page number. */
    val pages = mutableMapOf<Int, PagedResponse<Bookmark>>()

    /** Responses for a given `q`, which take precedence when the query matches. */
    val byQuery = mutableMapOf<String, PagedResponse<Bookmark>>()

    var failWith: Throwable? = null

    /** Requests matching these block until [release], so a race can be arranged deterministically. */
    var suspendOnPage: Int? = null
    var suspendOnQuery: String? = null
    private val gate = CompletableDeferred<Unit>()

    val requests = mutableListOf<Request>()

    fun release() {
        gate.complete(Unit)
    }

    override suspend fun list(
        page: Int,
        size: Int,
        q: String?,
        tag: String?,
        favourite: Boolean?,
    ): PagedResponse<Bookmark> {
        requests += Request(page, size, q, tag, favourite)
        if (suspendOnPage == page || (q != null && suspendOnQuery == q)) gate.await()
        failWith?.let { throw it }
        return byQuery[q] ?: pages.getValue(page)
    }

    // The list screen never calls these; a fake that pretended to support them could hide a
    // wrong call rather than failing on it.
    override suspend fun get(id: Long): Bookmark = throw UnsupportedOperationException()

    override suspend fun create(request: NewBookmark): Bookmark =
        throw UnsupportedOperationException()

    override suspend fun update(id: Long, request: BookmarkPatch): Bookmark =
        throw UnsupportedOperationException()

    override suspend fun delete(id: Long) = throw UnsupportedOperationException()
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
