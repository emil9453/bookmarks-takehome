package az.bookmarks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.bookmarks.data.Bookmark
import az.bookmarks.data.BookmarksApi
import az.bookmarks.data.Network
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * What the list is currently asking the backend for. One value, so the search text and the two
 * filters cannot drift apart, and so a page-2 request can carry exactly what page 1 asked for.
 */
data class BookmarkQuery(
    val text: String = "",
    val favouritesOnly: Boolean = false,
    val tag: String? = null,
) {
    /** True when nothing is searched or filtered — the plain list. */
    val isUnfiltered: Boolean get() = text.isBlank() && !favouritesOnly && tag == null
}

/**
 * The states the brief names, as types. Which one is showing is a question the type system
 * answers, so "show me the error state" is a thing that can be demonstrated rather than a
 * combination of booleans that might be unreachable.
 *
 * ponytail: this is why there is no paging library. Paging 3 owns the loading model and buries
 * these cases inside `LoadState` and `CombinedLoadStates`; a sealed interface makes them literal
 * for the price of the load-more call below. Worth revisiting if the list ever needs placeholders
 * or cache-backed paging.
 */
sealed interface BookmarksUiState {

    data object Loading : BookmarksUiState

    /** Nothing saved yet: the first-run state, with nothing searched or filtered. */
    data object Empty : BookmarksUiState

    /**
     * A search or filter that matched nothing. Deliberately a different state from [Empty]: one
     * means "you have not saved anything", the other means "nothing here matches that". Telling a
     * first-time user to add their first bookmark because a search missed would be wrong.
     */
    data class NoResults(val query: BookmarkQuery) : BookmarksUiState

    data class Error(val message: String) : BookmarksUiState

    /**
     * `query` and `nextPage` live here rather than in `var`s on the ViewModel on purpose. A cursor
     * held separately from the list it describes can be reset while a page request is in flight,
     * and the two then disagree: the same page appends twice, duplicate ids reach `LazyColumn`'s
     * keyed items, and it throws. Keeping both in the same immutable value as the rows makes that
     * desync unrepresentable, and it is what lets page 2 repeat page 1's filters exactly.
     */
    data class Data(
        val bookmarks: List<Bookmark>,
        val query: BookmarkQuery,
        val nextPage: Int,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
        val loadMoreFailed: Boolean = false,
        val refreshing: Boolean = false,
        /** Set when a refresh failed while rows were already on screen. Shown as a snackbar. */
        val refreshError: String? = null,
    ) : BookmarksUiState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class BookmarkListViewModel(
    // Default argument rather than a factory: Kotlin generates a no-arg constructor when every
    // parameter has a default, which is all `viewModel()` needs. This is the DI seam, and the
    // seam the tests use.
    private val api: BookmarksApi = Network.bookmarks,
) : ViewModel() {

    private val _state = MutableStateFlow<BookmarksUiState>(BookmarksUiState.Loading)
    val state: StateFlow<BookmarksUiState> = _state.asStateFlow()

    /** Updated on every keystroke so the field stays responsive; the requests are debounced. */
    private val _query = MutableStateFlow(BookmarkQuery())
    val query: StateFlow<BookmarkQuery> = _query.asStateFlow()

    /**
     * Tags offered as filter chips, accumulated from every row seen so far.
     *
     * ponytail: the API has no endpoint listing tags, and adding one to populate a chip row is
     * not worth a round trip. The consequence is worth saying out loud rather than hiding: a tag
     * that only appears on a bookmark in a page not yet loaded is not offered until it loads.
     * Sorted here because this is presentation — the ranked results themselves are never
     * re-sorted.
     */
    private val _knownTags = MutableStateFlow(emptyList<String>())
    val knownTags: StateFlow<List<String>> = _knownTags.asStateFlow()

    /** Bumped to re-run the current query: pull to refresh, and the error state's retry. */
    private val reloads = MutableStateFlow(0)

    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch {
            // One debounced flow over the whole query, not a `combine` of separate text and
            // filter flows. Two flows look better — a chip tap would not wait out the typing
            // pause — but `combine` re-emits using each branch's *last emitted* value, so a chip
            // tapped 100ms into typing fires a request with the previous text and briefly shows
            // results for a query the user never asked for. One flow has no such window, and a
            // chip tap paying 300ms is imperceptible next to showing the wrong list.
            val queries: Flow<BookmarkQuery> = _query
                // Zero for blank text so the first load is not held back on launch.
                .debounce { query -> if (query.text.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
                // Trimmed here so "kotlin" and "kotlin " are one query: the backend normalises
                // them to the same search, and without this a trailing space is a second
                // identical round trip.
                .map { query -> query.copy(text = query.text.trim()) }
                .distinctUntilChanged()

            // `reloads` is combined after the dedup, not before it: pull to refresh and retry are
            // requests to run the *same* query again, and distinctUntilChanged would swallow them.
            combine(queries, reloads) { query, _ -> query }
                // The operator that makes a stale response impossible: a new query cancels the
                // request for the previous one, so a slow early query cannot land last and win.
                // Without it, typing quickly is enough to leave the wrong results on screen.
                .flatMapLatest { query -> firstPage(query) }
                .collect { newState -> _state.value = newState }
        }
    }

    fun onSearchTextChange(text: String) = _query.update { it.copy(text = text) }

    fun onFavouritesOnlyChange(enabled: Boolean) =
        _query.update { it.copy(favouritesOnly = enabled) }

    /** Null clears the tag filter; tapping the active chip again is how the UI clears it. */
    fun onTagChange(tag: String?) = _query.update { it.copy(tag = tag) }

    /** Back to the plain list: the way out of a search that found nothing. */
    fun clearQuery() = _query.update { BookmarkQuery() }

    /**
     * Called every time the list screen resumes, so a bookmark added or deleted elsewhere shows
     * up without each screen having to report back what it did. The first resume is the one that
     * arrives with the initial load already running, so it is skipped.
     *
     * The flag lives here rather than in a `remember` in the composable, and that is the whole
     * point: Navigation Compose disposes the list's composition when another destination is shown,
     * so a remembered flag is back to its initial value on return and the reload never happens —
     * which is exactly the bug this replaced. The ViewModel outlives the composition; the
     * composable does not.
     */
    fun onScreenResumed() {
        if (resumedBefore) reload() else resumedBefore = true
    }

    private var resumedBefore = false

    /** Pull to refresh, and the error state's retry: re-run whatever is currently asked for. */
    fun reload() = reloads.update { it + 1 }

    /**
     * Emits the loading shape, then the outcome. Returned as a flow rather than launched so that
     * [flatMapLatest] owns its lifetime — that ownership is what cancels the in-flight request
     * for a query the user has already moved on from.
     */
    private fun firstPage(query: BookmarkQuery): Flow<BookmarksUiState> = flow {
        val previous = _state.value

        // Re-running the same query keeps the rows and marks them refreshing. A changed query
        // clears them: leaving the old results under a new search reads as if nothing happened.
        val sameQuery = previous is BookmarksUiState.Data && previous.query == query
        emit(
            if (previous is BookmarksUiState.Data && sameQuery) {
                previous.copy(refreshing = true, refreshError = null, loadMoreFailed = false)
            } else {
                BookmarksUiState.Loading
            },
        )

        // A superseded load must not leave a next-page request appending to the list it replaces.
        loadMoreJob?.cancel()

        emit(
            try {
                val response = api.list(
                    page = FIRST_PAGE,
                    size = PAGE_SIZE,
                    q = query.text.ifBlank { null },
                    tag = query.tag,
                    favourite = if (query.favouritesOnly) true else null,
                )
                rememberTags(response.content, replace = query.isUnfiltered)
                when {
                    response.content.isNotEmpty() -> BookmarksUiState.Data(
                        bookmarks = response.content,
                        query = query,
                        nextPage = FIRST_PAGE + 1,
                        hasMore = response.page.hasMore,
                    )
                    query.isUnfiltered -> BookmarksUiState.Empty
                    else -> BookmarksUiState.NoResults(query)
                }
            } catch (cancelled: CancellationException) {
                // Cancellation is how flatMapLatest replaces this load. Rethrowing keeps
                // structured concurrency working; swallowing it would turn every superseded
                // query into a spurious error state.
                throw cancelled
            } catch (failure: Exception) {
                // A failed refresh must not throw away rows the user already had — same rule as a
                // failed next page. Only a load with nothing to keep becomes the error state.
                //
                // Read the state again rather than reusing the `previous` captured before the
                // request: a next page may have loaded during it, and writing the old snapshot
                // back would silently drop those rows and reset the cursor onto a page already
                // shown.
                val latest = _state.value
                if (latest is BookmarksUiState.Data && sameQuery) {
                    latest.copy(refreshing = false, refreshError = failure.toMessage())
                } else {
                    BookmarksUiState.Error(failure.toMessage())
                }
            },
        )
    }

    /** Called when the list footer scrolls into view. */
    fun loadMore() {
        val current = _state.value
        if (current !is BookmarksUiState.Data || !current.hasMore) return
        // Guarding on the job rather than a flag inside the state: the flag can be cleared by any
        // other writer, and this does not depend on the launch body running synchronously.
        if (loadMoreJob?.isActive == true) return

        val page = current.nextPage
        // Page 2 must repeat page 1's filters, or it pages the unfiltered list and appends rows
        // that do not match what the user is looking at.
        val query = current.query

        loadMoreJob = viewModelScope.launch {
            _state.value = current.copy(loadingMore = true, loadMoreFailed = false)
            try {
                val response = api.list(
                    page = page,
                    size = PAGE_SIZE,
                    q = query.text.ifBlank { null },
                    tag = query.tag,
                    favourite = if (query.favouritesOnly) true else null,
                )
                rememberTags(response.content)
                // The guard that actually prevents the duplicate-key crash, verified by deleting
                // it and watching BookmarkListViewModelTest fail: read the state again rather
                // than building on `current`, and append only if the cursor and the query still
                // match the request this response answers.
                val latest = _state.value
                if (latest is BookmarksUiState.Data &&
                    latest.nextPage == page &&
                    latest.query == query
                ) {
                    _state.value = latest.copy(
                        bookmarks = latest.bookmarks + response.content,
                        nextPage = page + 1,
                        hasMore = response.page.hasMore,
                        loadingMore = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed next page must not destroy the rows already on screen. Keep them and
                // offer the retry in the footer.
                val latest = _state.value
                if (latest is BookmarksUiState.Data) {
                    _state.value = latest.copy(loadingMore = false, loadMoreFailed = true)
                }
            }
        }
    }

    /** Called once the snackbar for a failed refresh has been shown. */
    fun refreshErrorShown() {
        val current = _state.value
        if (current is BookmarksUiState.Data) {
            _state.value = current.copy(refreshError = null)
        }
    }

    /**
     * [replace] on an unfiltered first page, so a tag whose last bookmark was deleted stops being
     * offered instead of lingering as a chip that can only ever return "no matches". While a
     * filter is active the set is only added to — a filtered response contains just the matching
     * rows, and rebuilding from those would collapse the chip row to the one tag already selected
     * and leave no way to switch.
     */
    private fun rememberTags(rows: List<Bookmark>, replace: Boolean = false) {
        val seen = rows.flatMap(Bookmark::tags)
        if (seen.isEmpty() && !replace) return
        _knownTags.update { known ->
            (if (replace) seen else known + seen).distinct().sorted()
        }
    }

    private companion object {
        const val FIRST_PAGE = 0
        const val PAGE_SIZE = 20

        /** The brief asks for roughly a third of a second, and calls it out specifically. */
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

/**
 * What the error state actually says. "Something went wrong" on its own tells the user nothing
 * about whether to retry, so the three cases that behave differently are separated: the network
 * is unreachable, the server answered with a failure, or the response did not parse.
 */
internal fun Throwable.toMessage(): String = when (this) {
    is IOException -> "Can't reach the server. Check your connection and try again."
    is HttpException -> "The server returned an error (HTTP ${code()}). Try again in a moment."
    else -> "Something went wrong reading the response."
}
