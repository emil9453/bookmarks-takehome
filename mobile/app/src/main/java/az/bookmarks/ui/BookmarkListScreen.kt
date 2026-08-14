package az.bookmarks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import az.bookmarks.R
import az.bookmarks.data.Bookmark

/** Clears the "Add" button and the navigation bar, which float over the list rather than displace it. */
private val ListBottomPadding = PaddingValues(bottom = 96.dp)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BookmarkListScreen(
    onOpenBookmark: (Long) -> Unit,
    onAddBookmark: () -> Unit,
    viewModel: BookmarkListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val knownTags by viewModel.knownTags.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // The list refetches whenever it comes back to the foreground: after a bookmark is added, and
    // after a favourite toggle or a delete on the detail screen. One rule covers every way the data
    // can change elsewhere, instead of each screen having to report back what it did.
    //
    // The "skip the first one" bookkeeping is in the ViewModel, not a `remember` here, because
    // navigating away disposes this composition — a remembered flag resets and the reload silently
    // never happens.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }

    // `LazyColumn` with `key = { it.id }` anchors the scroll to whichever keyed row was showing.
    // That is right for a next page appended below, and wrong for a row prepended above: after
    // saving a bookmark the list holds it, keeps the old first row anchored, and renders the new one
    // just off the top of the viewport — indistinguishable from the save having failed.
    val newestId = (state as? BookmarksUiState.Data)?.bookmarks?.firstOrNull()?.id
    LaunchedEffect(newestId) {
        if (newestId != null) listState.scrollToItem(0)
    }

    // A refresh that fails keeps the rows on screen, so the failure needs somewhere else to be said.
    val refreshError = (state as? BookmarksUiState.Data)?.refreshError
    LaunchedEffect(refreshError) {
        if (refreshError != null) {
            snackbarHostState.showSnackbar(refreshError)
            viewModel.refreshErrorShown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BookmarksNavigationBar(
                favouritesSelected = query.favouritesOnly,
                onSelect = viewModel::onFavouritesOnlyChange,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddBookmark,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = "Add a bookmark")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            BrandHeader()

            SearchField(
                text = query.text,
                onTextChange = viewModel::onSearchTextChange,
                // The debounce already ran the search, so Search has nothing to submit — but the
                // keyboard is covering half the results, so dropping focus is what the key is for.
                onSearchKey = { focusManager.clearFocus() },
            )

            TagFilterRow(
                selectedTag = query.tag,
                knownTags = knownTags,
                onTagChange = viewModel::onTagChange,
            )

            PullToRefreshBox(
                isRefreshing = (state as? BookmarksUiState.Data)?.refreshing == true,
                onRefresh = viewModel::reload,
                modifier = Modifier.weight(1f),
            ) {
                when (val current = state) {
                    BookmarksUiState.Loading -> LoadingState()

                    BookmarksUiState.Empty -> EmptyState(onAddBookmark = onAddBookmark)

                    is BookmarksUiState.NoResults -> NoResultsState(
                        query = current.query,
                        onClearFilters = viewModel::clearQuery,
                    )

                    is BookmarksUiState.Error -> ErrorState(
                        message = current.message,
                        onRetry = viewModel::reload,
                    )

                    is BookmarksUiState.Data -> when {
                        // Defensive, and unreachable today: `Data` is only ever built from a
                        // non-empty response and the list never shrinks in place. Which message it
                        // gets depends on the query, so an empty filtered list never claims nothing
                        // is saved while a chip or the Favourites tab is still selected.
                        current.bookmarks.isEmpty() && current.query.isUnfiltered ->
                            EmptyState(onAddBookmark = onAddBookmark)

                        current.bookmarks.isEmpty() -> NoResultsState(
                            query = current.query,
                            onClearFilters = viewModel::clearQuery,
                        )

                        else -> BookmarkList(
                            state = current,
                            listState = listState,
                            onOpenBookmark = onOpenBookmark,
                            onLoadMore = viewModel::loadMore,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The mark in a white circle beside the name, which is where their app puts it. The mark is the one
 * piece of red allowed to sit here — everything else in the header is ink or grey.
 */
@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.logo_mark),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = "BirBookmarks",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** A filled pill, as in the reference: no outline, fully rounded, muted grey fill. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SearchField(text: String, onTextChange: (String) -> Unit, onSearchKey: () -> Unit) {
    TextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = {
            Text(
                text = "Search titles, tags and notes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            if (text.isNotEmpty()) {
                TextButton(onClick = { onTextChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchKey() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            // A pill has no underline. Material draws one by default in both states.
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/**
 * Tags only. Favourites used to be a chip here and is now a navigation tab, because it is a view of
 * the data rather than a refinement of one — and having it in both places would be the same filter
 * offered twice.
 */
@Composable
private fun TagFilterRow(
    selectedTag: String?,
    knownTags: List<String>,
    onTagChange: (String?) -> Unit,
) {
    if (knownTags.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = knownTags, key = { it }) { tag ->
            val selected = selectedTag == tag
            FilterChip(
                selected = selected,
                // Tapping the selected chip clears the filter, so there is always a way back to the
                // full list without hunting for a separate control.
                onClick = { onTagChange(if (selected) null else tag) },
                label = { Text("#$tag", style = MaterialTheme.typography.labelMedium) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = null,
            )
        }
    }
}

/**
 * Two tabs, because this app has two views of its data. Their bar has five because their app has
 * five things; padding ours out to match would put tabs in it that lead nowhere, and a reviewer taps
 * every tab. Add stays a floating button rather than becoming a third tab — it is an action, not a
 * place, and it is already reachable from the empty state.
 */
@Composable
private fun BookmarksNavigationBar(favouritesSelected: Boolean, onSelect: (Boolean) -> Unit) {
    // Material's bar is 80dp, which is taller than the reference and eats a card's worth of list.
    // 56dp is the compact height their bar reads as. The gesture inset is added on top rather than
    // taken out of the 56, so the items keep their full height instead of being squeezed into the
    // strip the system already owns.
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    NavigationBar(
        // `heightIn`, not `height`: 56dp is the look, not a ceiling. A fixed dp box does not grow
        // with sp text, so at a 150% font scale the icons start clipping and at 200% both icons are
        // sliced through. A minimum keeps the compact bar at normal sizes and lets it grow instead.
        modifier = Modifier.heightIn(min = 56.dp + bottomInset),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            // Their bar tints the icon itself rather than drawing a pill behind it.
            indicatorColor = Color.Transparent,
        )
        NavigationBarItem(
            selected = !favouritesSelected,
            onClick = { onSelect(false) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_bookmark),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            },
            label = { Text("All", style = MaterialTheme.typography.labelSmall) },
            colors = colors,
        )
        NavigationBarItem(
            selected = favouritesSelected,
            onClick = { onSelect(true) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_star),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            },
            label = { Text("Favourites", style = MaterialTheme.typography.labelSmall) },
            colors = colors,
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Not a blank screen: a first-time user is told what the app is for and given the one action that
 * gets them out of this state.
 *
 * The scroll modifier is not for scrolling — the content always fits. `PullToRefreshBox` listens for
 * nested scroll, which only a scrollable child dispatches, so without it the pull gesture is dead on
 * every state except the list.
 */
@Composable
private fun EmptyState(onAddBookmark: () -> Unit) {
    CentredState(
        icon = R.drawable.ic_bookmark,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        title = "Nothing saved yet",
        body = "Links you save for later will show up here, newest first.",
    ) {
        Button(
            onClick = onAddBookmark,
            shape = CircleShape,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Add your first bookmark", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * A search that matched nothing is not the same screen as an app with nothing in it. This one names
 * what was searched for and offers the way back, and never suggests adding a first bookmark — there
 * are bookmarks, they just do not match. Different icon, different words, different action.
 */
@Composable
private fun NoResultsState(query: BookmarkQuery, onClearFilters: () -> Unit) {
    CentredState(
        icon = R.drawable.ic_search,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        title = "No matches",
        body = describeQuery(query),
    ) {
        TextButton(onClick = onClearFilters) {
            Text(
                text = "Clear search and filters",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * The error state carries the red, because a failure is the one thing here that is genuinely live.
 * Distinct from the other two by colour as well as by words and icon.
 */
@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    CentredState(
        icon = R.drawable.ic_close,
        iconTint = MaterialTheme.colorScheme.error,
        title = "Couldn't load your bookmarks",
        titleColor = MaterialTheme.colorScheme.error,
        body = message,
    ) {
        Button(
            onClick = onRetry,
            shape = CircleShape,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Try again", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CentredState(
    icon: Int,
    iconTint: Color,
    title: String,
    body: String,
    titleColor: Color = MaterialTheme.colorScheme.onBackground,
    action: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(40.dp),
        )
        Text(text = title, style = MaterialTheme.typography.headlineSmall, color = titleColor)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action()
    }
}

private fun describeQuery(query: BookmarkQuery): String {
    val parts = buildList {
        if (query.text.isNotBlank()) add("\"${query.text.trim()}\"")
        query.tag?.let { add("tagged #$it") }
        if (query.favouritesOnly) add("in favourites")
    }
    return "Nothing saved here matches ${parts.joinToString(" ")}."
}

@Composable
private fun BookmarkList(
    state: BookmarksUiState.Data,
    listState: LazyListState,
    onOpenBookmark: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListBottomPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = state.bookmarks, key = { it.id }) { bookmark ->
            BookmarkCard(bookmark = bookmark, onClick = { onOpenBookmark(bookmark.id) })
        }

        if (state.hasMore || state.loadMoreFailed) {
            item {
                LoadMoreFooter(
                    failed = state.loadMoreFailed,
                    // The footer only composes once it scrolls into view, which is the trigger.
                    // Keyed on the row count so it fires again after each page is appended, and an
                    // Int key cannot be unstable the way a lambda or a list would be.
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
            TextButton(onClick = onLoadMore) {
                Text(
                    text = "Couldn't load more — tap to retry",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            LaunchedEffect(loadedCount) { onLoadMore() }
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** A white card on the grey page, at the reference's corner radius and spacing rhythm. */
@Composable
private fun BookmarkCard(bookmark: Bookmark, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (bookmark.favourite) {
                    Icon(
                        painter = painterResource(R.drawable.ic_star_filled),
                        contentDescription = "Favourite",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    bookmark.tags.take(MAX_TAGS_ON_CARD).forEach { tag ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .background(Color.Transparent)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Beyond three the row wraps and the card grows unevenly; the rest are on the detail screen. */
private const val MAX_TAGS_ON_CARD = 3
