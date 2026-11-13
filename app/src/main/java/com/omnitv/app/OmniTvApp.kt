package com.omnitv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.Video
import com.omnitv.app.player.PlayerScreen
import com.omnitv.app.ui.components.TabItem
import com.omnitv.app.ui.components.TvScaffold
import com.omnitv.app.ui.screens.DetailScreen
import com.omnitv.app.ui.screens.HomeScreen
import com.omnitv.app.ui.screens.MoviesScreen
import com.omnitv.app.ui.screens.SearchScreen
import com.omnitv.app.ui.screens.SettingsScreen

/** Rutas de navegación. */
object Routes {
    const val HOME = "home"
    const val MOVIES = "movies"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{videoId}"
    const val PLAYER = "player/{videoId}"

    fun detail(videoId: String) = "detail/$videoId"
    fun player(videoId: String) = "player/$videoId"
}

private val appTabs = listOf(
    TabItem("Inicio", Routes.HOME, R.drawable.ic_play),
    TabItem("Películas", Routes.MOVIES, R.drawable.ic_add),
    TabItem("Buscar", Routes.SEARCH, R.drawable.ic_search),
    TabItem("Ajustes", Routes.SETTINGS, R.drawable.ic_settings)
)

/**
 * Raíz de la app: tema Omni TV + navegación.
 * Las pantallas con pestañas usan [TvScaffold]; detalle y reproductor son
 * pantallas completas sin barra superior.
 */
@Composable
fun OmniTvApp() {
    com.omnitv.app.ui.theme.OmniTvTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Routes.HOME) {
                TvScaffold(appTabs, currentRoute, navController::selectTab) {
                    HomeScreen(
                        onOpenDetail = { navController.navigate(Routes.detail(it.id)) },
                        onPlay = { navController.navigate(Routes.player(it.id)) }
                    )
                }
            }
            composable(Routes.MOVIES) {
                TvScaffold(appTabs, currentRoute, navController::selectTab) {
                    MoviesScreen(
                        onOpenDetail = { navController.navigate(Routes.detail(it.id)) }
                    )
                }
            }
            composable(Routes.SEARCH) {
                TvScaffold(appTabs, currentRoute, navController::selectTab) {
                    SearchScreen(
                        onOpenDetail = { navController.navigate(Routes.detail(it.id)) }
                    )
                }
            }
            composable(Routes.SETTINGS) {
                TvScaffold(appTabs, currentRoute, navController::selectTab) {
                    SettingsScreen()
                }
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("videoId") { type = NavType.StringType })
            ) { entry ->
                val videoId = entry.arguments?.getString("videoId").orEmpty()
                val video = CatalogRepository.find(videoId)
                if (video != null) {
                    DetailScreen(
                        video = video,
                        onBack = { navController.popBackStack() },
                        onPlay = { navController.navigate(Routes.player(video.id)) },
                        onOpenDetail = { navController.navigate(Routes.detail(it.id)) }
                    )
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("videoId") { type = NavType.StringType })
            ) { entry ->
                val videoId = entry.arguments?.getString("videoId").orEmpty()
                val video = CatalogRepository.find(videoId)
                if (video != null) {
                    PlayerScreen(
                        video = video,
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
        }
    }
}

private fun NavHostController.selectTab(tab: TabItem) {
    navigate(tab.route) {
        popUpTo(Routes.HOME) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
