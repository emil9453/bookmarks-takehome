package az.bookmarks.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import az.bookmarks.data.Bookmark
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BookmarkDetailScreen(
    id: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: BookmarkDetailViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // rememberSaveable, not remember: an Activity recreation with the dialog up would otherwise
    // dismiss it silently. This is the only UI state on this screen that lives outside the
    // ViewModel, so it is the only place that needs saying.
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    val busy = (state as? BookmarkDetailUiState.Loaded)?.busy == true

    // Leaving mid-request cancels it: popping this destination clears its ViewModelStore, which
    // cancels viewModelScope, which cancels the OkHttp call. On a cold backend the delete can be
    // waiting a minute, and a user who gives up and presses back would have confirmed a
    // destructive action that then silently did not happen. So back is held until it settles.
    BackHandler(enabled = busy) { /* deliberately swallowed while an action is in flight */ }

    // Keyed on the id so reusing this screen for a different bookmark refetches, and the same one
    // does not refetch on every recomposition.
    LaunchedEffect(id) { viewModel.load(id) }

    // Keyed on the Boolean, not on `state`: keying on the state object restarts this effect on
    // every unrelated change, which is the LaunchedEffect trap mobile/CLAUDE.md names.
    val deleted = state is BookmarkDetailUiState.Deleted
    LaunchedEffect(deleted) {
        if (deleted) onDeleted()
    }

    val actionError = (state as? BookmarkDetailUiState.Loaded)?.actionError
    LaunchedEffect(actionError) {
        if (actionError != null) {
            snackbarHostState.showSnackbar(actionError)
            viewModel.actionErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookmark") },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !busy) { Text("Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val current = state) {
                BookmarkDetailUiState.Loading, BookmarkDetailUiState.Deleted ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                is BookmarkDetailUiState.Error -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Couldn't load this bookmark",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(text = current.message, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { viewModel.load(id, force = true) }) { Text("Try again") }
                }

                is BookmarkDetailUiState.Loaded -> BookmarkDetail(
                    bookmark = current.bookmark,
                    busy = current.busy,
                    onOpenLink = {
                        // A device with no browser is rare, but ACTION_VIEW with nothing to handle
                        // it throws rather than returning null, so the failure is reported instead
                        // of crashing or doing nothing.
                        val opened = runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, current.bookmark.url.toUri()))
                        }.isSuccess
                        if (!opened) {
                            scope.launch {
                                snackbarHostState.showSnackbar("No app on this device can open that link.")
                            }
                        }
                    },
                    onToggleFavourite = viewModel::toggleFavourite,
                    onDeleteRequested = { confirmingDelete = true },
                )
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this bookmark?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.delete()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BookmarkDetail(
    bookmark: Bookmark,
    busy: Boolean,
    onOpenLink: () -> Unit,
    onToggleFavourite: () -> Unit,
    onDeleteRequested: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = bookmark.title, style = MaterialTheme.typography.headlineSmall)

        Text(
            text = bookmark.url,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable(onClick = onOpenLink),
        )

        if (bookmark.tags.isNotEmpty()) {
            // Plain text, the same shape the list row uses. Chips here were tappable and did
            // nothing: a ripple invites "filter by this tag", and offering that feedback without
            // the behaviour is worse than not offering it. A lazy row for three labels inside an
            // already-scrolling column was the other half of the mistake.
            Text(
                text = bookmark.tags.joinToString(" ") { tag -> "#$tag" },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (!bookmark.notes.isNullOrBlank()) {
            HorizontalDivider()
            Text(text = "Notes", style = MaterialTheme.typography.labelLarge)
            Text(text = bookmark.notes, style = MaterialTheme.typography.bodyMedium)
        }

        HorizontalDivider()

        Text(
            text = "Saved ${formatSavedAt(bookmark.createdAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onToggleFavourite,
            modifier = Modifier.fillMaxWidth(),
            // Disabled in flight: the star always shows what the server last confirmed, so there
            // is no window in which it can disagree with the backend.
            enabled = !busy,
        ) {
            Text(if (bookmark.favourite) "★ Remove from favourites" else "☆ Add to favourites")
        }

        OutlinedButton(
            onClick = onDeleteRequested,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        ) {
            Text("Delete")
        }

        if (busy) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * `createdAt` is carried as the raw ISO-8601 string the API sends, so it is parsed at the one
 * place that displays it. `java.time` is available without desugaring at minSdk 26, which is why
 * there is no date library here.
 */
private fun formatSavedAt(createdAt: String): String = runCatching {
    Instant.parse(createdAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
}.getOrDefault(createdAt)
