package az.bookmarks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.bookmarks.data.BookmarksApi
import az.bookmarks.data.NewBookmark
import az.bookmarks.data.Network
import az.bookmarks.data.problemDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * The form, its per-field messages, and whether a save is in flight. One value so the UI cannot
 * see a half-updated form, and so "saving" cannot disagree with the button that reads it.
 */
data class AddBookmarkForm(
    val url: String = "",
    val title: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    /** The tag being typed, before it is confirmed into [tags]. */
    val tagDraft: String = "",
    val urlError: String? = null,
    val titleError: String? = null,
    val tagsError: String? = null,
    val notesError: String? = null,
    /** A failure that belongs to no single field: the network, or a status with no field map. */
    val formError: String? = null,
    val saving: Boolean = false,
    /** Flips once, on success. The screen navigates back when it sees this. */
    val saved: Boolean = false,
)

class AddBookmarkViewModel(
    private val api: BookmarksApi = Network.bookmarks,
) : ViewModel() {

    private val _form = MutableStateFlow(AddBookmarkForm())
    val form: StateFlow<AddBookmarkForm> = _form.asStateFlow()

    private var saveJob: Job? = null

    // Clearing the field's error as it is edited: leaving a stale message under a box the user is
    // actively fixing reads as though the fix did not register.
    fun onUrlChange(value: String) = _form.update { it.copy(url = value, urlError = null) }

    fun onTitleChange(value: String) = _form.update { it.copy(title = value, titleError = null) }

    fun onNotesChange(value: String) = _form.update { it.copy(notes = value, notesError = null) }

    fun onTagDraftChange(value: String) = _form.update { it.copy(tagDraft = value, tagsError = null) }

    /** Confirms the typed tag. Duplicates are dropped rather than rejected — the intent is clear. */
    fun onTagConfirmed() = _form.update { form ->
        val tag = form.tagDraft.trim()
        when {
            tag.isEmpty() -> form.copy(tagDraft = "")
            tag.length > MAX_TAG -> form.copy(tagsError = "Tags can be at most $MAX_TAG characters.")
            tag in form.tags -> form.copy(tagDraft = "")
            else -> form.copy(tags = form.tags + tag, tagDraft = "", tagsError = null)
        }
    }

    fun onTagRemoved(tag: String) = _form.update { it.copy(tags = it.tags - tag) }

    /**
     * Validates on the phone first, then sends.
     *
     * Both ends validating is deliberate, not duplication: this side is instant and keeps a
     * pointless round trip off a sleeping free-tier backend, and the backend's copy is the one
     * that actually protects the data, since any client can be bypassed. When the backend rejects
     * something this missed, its per-field messages are shown in the same places.
     */
    fun save() {
        // The guard against a double submit is the job, not a flag another writer could clear.
        if (saveJob?.isActive == true) return

        val validated = _form.value.validated()
        _form.value = validated
        if (validated.hasErrors) return

        saveJob = viewModelScope.launch {
            _form.update { it.copy(saving = true, formError = null) }
            try {
                api.create(
                    NewBookmark(
                        url = validated.url.trim(),
                        title = validated.title.trim(),
                        tags = validated.tags,
                        notes = validated.notes.trim().ifBlank { null },
                        favourite = false,
                    ),
                )
                _form.update { it.copy(saving = false, saved = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _form.update { it.withFailure(failure) }
            }
        }
    }

    private companion object {
        // Mirrors the column widths the backend validates against. Drifting apart would turn a
        // clear message under the right box into a surprise 400.
        const val MAX_URL = 2048
        const val MAX_TITLE = 200
        const val MAX_TAG = 50
        const val MAX_NOTES = 2000
    }

    private fun AddBookmarkForm.validated(): AddBookmarkForm {
        val url = url.trim()
        val title = title.trim()
        return copy(
            urlError = when {
                url.isEmpty() -> "A link is required."
                // Same restriction the backend applies: a read-it-later app saves web pages, so
                // ftp:// and file:// are mistakes worth naming rather than passing on.
                !url.startsWith("http://") && !url.startsWith("https://") ->
                    "Must be a http or https link."
                url.length > MAX_URL -> "Links can be at most $MAX_URL characters."
                else -> null
            },
            titleError = when {
                title.isEmpty() -> "A title is required."
                title.length > MAX_TITLE -> "Titles can be at most $MAX_TITLE characters."
                else -> null
            },
            notesError = if (notes.length > MAX_NOTES) {
                "Notes can be at most $MAX_NOTES characters."
            } else {
                null
            },
            tagsError = if (tags.any { it.length > MAX_TAG }) {
                "Tags can be at most $MAX_TAG characters."
            } else {
                null
            },
        )
    }

    /**
     * A rejected save is put back on the fields the backend named. `errors` is keyed by the JSON
     * field name that was sent, so this is a lookup rather than a guess; anything left over
     * becomes the form-level message so nothing is swallowed.
     */
    private fun AddBookmarkForm.withFailure(failure: Throwable): AddBookmarkForm {
        val problem = (failure as? HttpException)?.problemDetail()
        val fields = problem?.errors.orEmpty()
        val unmapped = fields.keys - KNOWN_FIELDS
        return copy(
            saving = false,
            urlError = fields["url"] ?: urlError,
            titleError = fields["title"] ?: titleError,
            tagsError = fields["tags"] ?: tagsError,
            notesError = fields["notes"] ?: notesError,
            formError = when {
                unmapped.isNotEmpty() ->
                    unmapped.joinToString("; ") { field -> "$field: ${fields[field]}" }
                fields.isNotEmpty() -> null
                else -> problem?.detail ?: failure.toMessage()
            },
        )
    }
}

private val KNOWN_FIELDS = setOf("url", "title", "tags", "notes", "favourite")

private val AddBookmarkForm.hasErrors: Boolean
    get() = urlError != null || titleError != null || tagsError != null || notesError != null
