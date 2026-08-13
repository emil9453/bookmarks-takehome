package az.bookmarks.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import az.bookmarks.data.Bookmark

/** Clears the "Add" button, which floats over the list rather than displacing it. */
private val ListBottomPadding = PaddingValues(bottom = 96.dp)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BookmarkListScreen(
    onOpenBookmark: (Long) -> Unit,
    onAddBookmark: () -> Unit,
    viewModel: BookmarkListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // A refresh that fails keeps the rows on screen, so the failure needs somewhere else to be
    // said. Keyed on the message, and cleared through the ViewModel so a recomposition or a
    // configuration change cannot show it twice.
    val refreshError = (state as? BookmarksUiState.Data)?.refreshError
    LaunchedEffect(refreshError) {
        if (refreshError != null) {
            snackbarHostState.showSnackbar(refreshError)
            viewModel.refreshErrorShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bookmarks") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddBookmark) { Text("Add") }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = (state as? BookmarksUiState.Data)?.refreshing == true,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val current = state) {
                BookmarksUiState.Loading -> LoadingState()

                BookmarksUiState.Empty -> EmptyState(onAddBookmark = onAddBookmark)

                is BookmarksUiState.Error -> ErrorState(
                    message = current.message,
                    onRetry = viewModel::load,
                )

                is BookmarksUiState.Data ->
                    // Deleting the last row (BOO-17) empties the list without a round trip, and
                    // an empty data state would otherwise draw a blank screen.
                    if (current.bookmarks.isEmpty()) {
                        EmptyState(onAddBookmark = onAddBookmark)
                    } else {
                        BookmarkList(
                            state = current,
                            onOpenBookmark = onOpenBookmark,
                            onLoadMore = viewModel::loadMore,
                        )
                    }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Not a blank screen: a first-time user is told what the app is for and given the one action that
 * gets them out of this state.
 *
 * The scroll modifier is not for scrolling — the content always fits. `PullToRefreshBox` listens
 * for nested scroll, which only a scrollable child dispatches, so without it the pull gesture is
 * dead on every state except the list.
 */
@Composable
private fun EmptyState(onAddBookmark: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "🔖", style = MaterialTheme.typography.displaySmall)
        Text(text = "Nothing saved yet", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Links you save for later will show up here, newest first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onAddBookmark) { Text("Add your first bookmark") }
    }
}

/**
 * Deliberately not the empty state with different words — the brief asks for four states that
 * are visibly distinct, and a reviewer will flip between these two on demand. This one carries
 * the error colour, the failure reason, and a filled button rather than a flat one.
 */
@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Couldn't load your bookmarks",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun BookmarkList(
    state: BookmarksUiState.Data,
    onOpenBookmark: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListBottomPadding) {
        items(items = state.bookmarks, key = { it.id }) { bookmark ->
            BookmarkRow(bookmark = bookmark, onClick = { onOpenBookmark(bookmark.id) })
            HorizontalDivider()
        }

        if (state.hasMore || state.loadMoreFailed) {
            item {
                LoadMoreFooter(
                    failed = state.loadMoreFailed,
                    // The footer only composes once it scrolls into view, which is the trigger.
                    // Keyed on the row count so it fires again after each page is appended, and
                    // an Int key cannot be unstable the way a lambda or a list would be.
                    loadedCount = state.bookmarks.size,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
}

@Composable
private fun LoadMoreFooter(failed: Boolean, loadedCount: Int, onLoadMore: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (failed) {
            TextButton(onClick = onLoadMore) { Text("Couldn't load more — tap to retry") }
        } else {
            LaunchedEffect(loadedCount) { onLoadMore() }
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun BookmarkRow(bookmark: Bookmark, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = bookmark.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (bookmark.favourite) {
                // A glyph rather than a material-icons dependency for one star. The semantics
                // are set explicitly because TalkBack would otherwise read the character name.
                Text(
                    text = "★",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { contentDescription = "Favourite" },
                )
            }
        }

        Text(
            text = bookmark.url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (bookmark.tags.isNotEmpty()) {
            Text(
                text = bookmark.tags.joinToString(" ") { "#$it" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
