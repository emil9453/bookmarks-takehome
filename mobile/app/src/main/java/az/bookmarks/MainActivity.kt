package az.bookmarks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BookmarksTheme {
                BookmarksApp()
            }
        }
    }
}

@Composable
fun BookmarksTheme(content: @Composable () -> Unit) {
    // ponytail: the stock Material 3 schemes. A hand-tuned palette buys nothing for a
    // read-it-later list; swap in dynamicDarkColorScheme/dynamicLightColorScheme (API 31+) if
    // matching the user's wallpaper ever matters more than a fixed identity.
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
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
            StubScreen(title = "Bookmarks", body = "List screen — BOO-14.") {
                Button(onClick = { navController.navigateOnce(AddRoute) }) {
                    Text("Add a bookmark")
                }
                Button(onClick = { navController.navigateOnce(DetailRoute(id = 1L)) }) {
                    Text("Open bookmark 1")
                }
            }
        }

        composable<AddRoute> {
            StubScreen(title = "Add bookmark", body = "Add screen — BOO-15.") {
                Button(onClick = { navController.popBackStack() }) { Text("Back") }
            }
        }

        composable<DetailRoute> { backStackEntry ->
            val route: DetailRoute = backStackEntry.toRoute()
            StubScreen(
                title = "Bookmark ${route.id}",
                body = "Detail screen — BOO-16. Route argument arrived as id=${route.id}.",
            ) {
                Button(onClick = { navController.popBackStack() }) { Text("Back") }
            }
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
 * Placeholder for a real screen. Each destination gets its own content in BOO-14 onward; this
 * exists so the navigation graph is walkable on the device today. `actions` is a composable
 * slot rather than a list of label/lambda pairs — the compiler can memoise the slot, where a
 * list allocated at the call site is a new instance on every recomposition.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun StubScreen(
    title: String,
    body: String,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = body, style = MaterialTheme.typography.bodyLarge)
            actions()
        }
    }
}
