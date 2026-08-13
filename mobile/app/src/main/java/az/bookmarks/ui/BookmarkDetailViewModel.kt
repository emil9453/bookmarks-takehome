package az.bookmarks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.bookmarks.data.Bookmark
import az.bookmarks.data.BookmarkPatch
import az.bookmarks.data.BookmarksApi
import az.bookmarks.data.Network
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BookmarkDetailUiState {

    data object Loading : BookmarkDetailUiState

    /** The bookmark could not be fetched. [BookmarkDetailViewModel.load] is the retry. */
    data class Error(val message: String) : BookmarkDetailUiState

    data class Loaded(
        val bookmark: Bookmark,
        /** A toggle or a delete is in flight; both controls are disabled while it is. */
        val busy: Boolean = false,
        /** A failed toggle or delete. Shown as a snackbar — never nothing at all. */
        val actionError: String? = null,
    ) : BookmarkDetailUiState

    /** Deleted on the server. The screen leaves; the list refetches when it resumes. */
    data object Deleted : BookmarkDetailUiState
}

class BookmarkDetailViewModel(
    private val api: BookmarksApi = Network.bookmarks,
) : ViewModel() {

    private val _state = MutableStateFlow<BookmarkDetailUiState>(BookmarkDetailUiState.Loading)
    val state: StateFlow<BookmarkDetailUiState> = _state.asStateFlow()

    private var id: Long? = null
    private var loadJob: Job? = null
    private var actionJob: Job? = null

    /**
     * Called with the route argument. The id is not a constructor parameter because that would
     * mean a ViewModel factory to pass it, and this is the only place it is needed — the screen
     * calls it once per id.
     *
     * The bookmark is fetched rather than handed over from the list row: the row may be from a
     * page loaded minutes ago, and the detail screen is where a stale favourite flag would be
     * most obvious.
     */
    fun load(id: Long, force: Boolean = false) {
        // Idempotent for an id already showing. The screen calls this from a LaunchedEffect, which
        // re-runs on every Activity recreation — rotation, dark mode, font size, multi-window — and
        // an unconditional reload there wipes `busy` while a toggle or delete is still in flight.
        // The controls then come back enabled, and the next tap hits the in-flight guard below and
        // does nothing at all, which is precisely the silent failure this screen must not have.
        if (!force && this.id == id && _state.value !is BookmarkDetailUiState.Error) return

        this.id = id
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = BookmarkDetailUiState.Loading
            try {
                _state.value = BookmarkDetailUiState.Loaded(api.get(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.value = BookmarkDetailUiState.Error(failure.toMessage())
            }
        }
    }

    /**
     * Sends the change and shows what the server confirmed, rather than flipping the star first
     * and reconciling afterwards.
     *
     * Both are defensible; this one is chosen because it cannot diverge. An optimistic flip needs
     * a correct rollback on every failure path, and the case that matters — a slow or dropped
     * request — is exactly when a star that has already flipped is most misleading. The star is
     * disabled while the request is in flight, so the state on screen is always a state the
     * backend agrees with.
     */
    fun toggleFavourite() {
        val current = _state.value
        if (current !is BookmarkDetailUiState.Loaded) return
        if (actionJob?.isActive == true) return
        val bookmarkId = id ?: return

        actionJob = viewModelScope.launch {
            _state.value = current.copy(busy = true, actionError = null)
            try {
                val updated = api.update(
                    id = bookmarkId,
                    // Only the one field: a full-object update would risk overwriting something
                    // changed elsewhere with the copy this screen happens to be holding.
                    request = BookmarkPatch(favourite = !current.bookmark.favourite),
                )
                _state.value = BookmarkDetailUiState.Loaded(updated)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // The star goes back to what the server last confirmed, and says why. Read the
                // state again rather than reusing the `current` captured before the request: a
                // reload may have replaced it, and writing the old snapshot back would discard
                // fresher data.
                val latest = _state.value
                if (latest is BookmarkDetailUiState.Loaded) {
                    _state.value = latest.copy(busy = false, actionError = failure.toMessage())
                }
            }
        }
    }

    /** Called after the confirmation dialog, never straight from the button. */
    fun delete() {
        val current = _state.value
        if (current !is BookmarkDetailUiState.Loaded) return
        if (actionJob?.isActive == true) return
        val bookmarkId = id ?: return

        actionJob = viewModelScope.launch {
            _state.value = current.copy(busy = true, actionError = null)
            try {
                api.delete(bookmarkId)
                _state.value = BookmarkDetailUiState.Deleted
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val latest = _state.value
                if (latest is BookmarkDetailUiState.Loaded) {
                    _state.value = latest.copy(busy = false, actionError = failure.toMessage())
                }
            }
        }
    }

    /** Called once the snackbar has been shown, so it cannot reappear on recomposition. */
    fun actionErrorShown() = _state.update { current ->
        if (current is BookmarkDetailUiState.Loaded) current.copy(actionError = null) else current
    }
}
