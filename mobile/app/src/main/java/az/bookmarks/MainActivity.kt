package az.bookmarks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import az.bookmarks.ui.AddBookmarkScreen
import az.bookmarks.ui.BookmarkDetailScreen
import az.bookmarks.ui.BookmarkListScreen
import az.bookmarks.ui.theme.BirBookmarksTheme
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BirBookmarksTheme {
                BookmarksApp()
            }
        }
    }
}

/*
 * Type-safe navigation routes. These are @Serializable rather than string paths, so the detail
 * argument is a Long at the call site instead of something parsed back out of a URL.
 */
@Serializable
object ListRoute

@Serializable
object AddRoute

@Serializable
data class DetailRoute(val id: Long)

@Composable
fun BookmarksApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ListRoute) {
        composable<ListRoute> {
            BookmarkListScreen(
                onOpenBookmark = { id -> navController.navigateOnce(DetailRoute(id)) },
                onAddBookmark = { navController.navigateOnce(AddRoute) },
            )
        }

        composable<AddRoute> {
            AddBookmarkScreen(
                // Both just go back. The list refetches when it resumes, so a saved bookmark is
                // there without this screen having to tell it anything.
                onSaved = { navController.popOnce() },
                onCancel = { navController.popOnce() },
            )
        }

        composable<DetailRoute> { backStackEntry ->
            val route: DetailRoute = backStackEntry.toRoute()
            BookmarkDetailScreen(
                id = route.id,
                onBack = { navController.popOnce() },
                // Same as onBack: the list refetches when it resumes, so a deleted bookmark is
                // gone from it without this screen having to say so.
                onDeleted = { navController.popOnce() },
            )
        }
    }
}

/**
 * A destination stays composed and hit-testable through its exit transition, so two taps a
 * few frames apart otherwise push the same destination twice and cost the user two back
 * presses to undo one navigation.
 */
private fun NavController.navigateOnce(route: Any) {
    navigate(route) { launchSingleTop = true }
}

/**
 * The same reasoning in reverse. A destination is still hit-testable during its exit transition,
 * so two taps on Back a few frames apart pop twice — the second one takes the list with it and
 * leaves an empty back stack behind a blank screen. Popping only from a RESUMED entry drops the
 * duplicate.
 */
private fun NavController.popOnce() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}
