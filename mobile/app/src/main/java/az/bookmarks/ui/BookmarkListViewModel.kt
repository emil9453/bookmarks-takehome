package az.bookmarks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.bookmarks.data.Bookmark
import az.bookmarks.data.BookmarksApi
import az.bookmarks.data.Network
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * The four states the brief names, as four types. Which one is showing is a question the type
 * system answers, so "show me the error state" is a thing that can be demonstrated rather than
 * a combination of booleans that might be unreachable.
 *
 * ponytail: this is why there is no paging library. Paging 3 owns the loading model and buries
 * these four cases inside `LoadState` and `CombinedLoadStates`; a sealed interface makes them
 * literal for the price of the load-more call below. Worth revisiting if the list ever needs
 * placeholders or cache-backed paging.
 */
sealed interface BookmarksUiState {

    data object Loading : BookmarksUiState

    /** Nothing saved yet. Distinct from a search that found nothing, which arrives in BOO-15. */
    data object Empty : BookmarksUiState

    data class Error(val message: String) : BookmarksUiState

    /**
     * `nextPage` lives here rather than in a `var` on the ViewModel on purpose. A cursor held
     * separately from the list it describes can be reset by a refresh while a page request is
     * still in flight, and the two then disagree: the same page appends twice, duplicate ids
     * reach `LazyColumn`'s keyed items, and it throws. Keeping the cursor in the same immutable
     * value as the rows makes that desync unrepresentable.
     */
    data class Data(
        val bookmarks: List<Bookmark>,
        val nextPage: Int,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
        val loadMoreFailed: Boolean = false,
        val refreshing: Boolean = false,
        /** Set when a refresh failed while rows were already on screen. Shown as a snackbar. */
        val refreshError: String? = null,
    ) : BookmarksUiState
}

class BookmarkListViewModel(
    // Default argument rather than a factory: Kotlin generates a no-arg constructor when every
    // parameter has a default, which is all `viewModel()` needs. This is the DI seam, and the
    // seam the tests use.
    private val api: BookmarksApi = Network.bookmarks,
) : ViewModel() {

    private val _state = MutableStateFlow<BookmarksUiState>(BookmarksUiState.Loading)
    val state: StateFlow<BookmarksUiState> = _state.asStateFlow()

    private var firstPageJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        load()
    }

    /** Full load, showing the loading state. Also the retry action on the error state. */
    fun load() = loadFirstPage(showLoading = true)

    /** Pull to refresh: keeps whatever is on screen and marks it refreshing. */
    fun refresh() = loadFirstPage(showLoading = false)

    private fun loadFirstPage(showLoading: Boolean) {
        val previous = _state.value
        firstPageJob?.cancel()
        // A page-N request still running would append to the list this call is replacing, so it
        // has to go too. Without this the append lands on the refreshed list and duplicates it.
        loadMoreJob?.cancel()

        firstPageJob = viewModelScope.launch {
            _state.value = if (!showLoading && previous is BookmarksUiState.Data) {
                previous.copy(refreshing = true, refreshError = null, loadMoreFailed = false)
            } else {
                BookmarksUiState.Loading
            }
            try {
                val response = api.list(page = FIRST_PAGE, size = PAGE_SIZE)
                _state.value = if (response.content.isEmpty()) {
                    BookmarksUiState.Empty
                } else {
                    BookmarksUiState.Data(
                        bookmarks = response.content,
                        nextPage = FIRST_PAGE + 1,
                        hasMore = response.page.hasMore,
                    )
                }
            } catch (cancelled: CancellationException) {
                // Cancellation is how a newer load replaces this one. Rethrowing keeps
                // structured concurrency working; swallowing it here would turn every
                // superseded request into a spurious error state.
                throw cancelled
            } catch (failure: Exception) {
                // A failed refresh must not throw away rows the user already had — same rule as
                // a failed next page. Only a load with nothing on screen becomes the error state.
                _state.value = if (previous is BookmarksUiState.Data && !showLoading) {
                    previous.copy(refreshing = false, refreshError = failure.toMessage())
                } else {
                    BookmarksUiState.Error(failure.toMessage())
                }
            }
        }
    }

    /** Called when the list footer scrolls into view. */
    fun loadMore() {
        val current = _state.value
        if (current !is BookmarksUiState.Data || !current.hasMore) return
        // Guarding on the job rather than on a flag inside the state: the flag can be cleared by
        // any other writer, and this does not depend on the launch body running synchronously.
        if (loadMoreJob?.isActive == true) return

        val page = current.nextPage
        loadMoreJob = viewModelScope.launch {
            _state.value = current.copy(loadingMore = true, loadMoreFailed = false)
            try {
                val response = api.list(page = page, size = PAGE_SIZE)
                // The guard that actually prevents the duplicate-key crash, verified by deleting
                // it and watching BookmarkListViewModelTest fail: read the state again rather
                // than building on `current`, and append only if the cursor still points at the
                // page this response is for. A refresh that landed meanwhile moved the cursor,
                // which makes this response stale and its rows already present.
                val latest = _state.value
                if (latest is BookmarksUiState.Data && latest.nextPage == page) {
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

    private companion object {
        const val FIRST_PAGE = 0
        const val PAGE_SIZE = 20
    }
}

/**
 * What the error state actually says. "Something went wrong" on its own tells the user nothing
 * about whether to retry, so the three cases that behave differently are separated: the network
 * is unreachable, the server answered with a failure, or the response did not parse.
 *
 * ponytail: the backend's RFC 9457 body carries a human `detail` string that would read better
 * than the status code. Parsing it waits for BOO-16, where per-field validation messages have to
 * be pulled out of the same body anyway — one parser, written once, at the point it is needed.
 */
internal fun Throwable.toMessage(): String = when (this) {
    is IOException -> "Can't reach the server. Check your connection and try again."
    is HttpException -> "The server returned an error (HTTP ${code()}). Try again in a moment."
    else -> "Something went wrong reading the response."
}
