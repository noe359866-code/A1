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
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.player.PlayerScreen
import com.omnitv.app.ui.components.ProfileBadge
import com.omnitv.app.ui.components.TabItem
import com.omnitv.app.ui.components.TvScaffold
import com.omnitv.app.ui.screens.DetailScreen
import com.omnitv.app.ui.screens.EditProfileScreen
import com.omnitv.app.ui.screens.HomeScreen
import com.omnitv.app.ui.screens.ManageProfilesScreen
import com.omnitv.app.ui.screens.MoviesScreen
import com.omnitv.app.ui.screens.ProfileSelectScreen
import com.omnitv.app.ui.screens.SearchScreen
import com.omnitv.app.ui.screens.SettingsScreen

/** Rutas de navegación. */
object Routes {
    const val PROFILES = "profiles"
    const val MANAGE_PROFILES = "manageProfiles"
    const val EDIT_PROFILE = "editProfile/{profileId}"
    const val HOME = "home"
    const val MOVIES = "movies"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{videoId}"
    const val PLAYER = "player/{videoId}"

    fun editProfile(profileId: String) = "editProfile/$profileId"
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
 *
 * Flujo estilo Netflix: al arrancar se muestra «¿Quién está viendo?»;
 * tras elegir perfil se entra a las pestañas principales. El avatar del
 * perfil siempre está accesible en la esquina superior derecha.
 */
@Composable
fun OmniTvApp() {
    com.omnitv.app.ui.theme.OmniTvTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        val profileBadge: @Composable () -> Unit = {
            ProfileBadge(
                profile = ProfileStore.currentProfile,
                onClick = { navController.navigate(Routes.PROFILES) }
            )
        }

        NavHost(
            navController = navController,
            startDestination = Routes.PROFILES,
            modifier = Modifier.fillMaxSize()
        ) {
            // ---------------- Perfiles ----------------
            composable(Routes.PROFILES) {
                ProfileSelectScreen(
                    onSelected = { profile ->
                        ProfileStore.select(profile.id)
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onManage = { navController.navigate(Routes.MANAGE_PROFILES) },
                    onAddProfile = { navController.navigate(Routes.editProfile("new")) }
                )
            }
            composable(Routes.MANAGE_PROFILES) {
                ManageProfilesScreen(
                    onEdit = { navController.navigate(Routes.editProfile(it)) },
                    onAddProfile = { navController.navigate(Routes.editProfile("new")) },
                    onDone = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.EDIT_PROFILE,
                arguments = listOf(navArgument("profileId") { type = NavType.StringType })
            ) { entry ->
                EditProfileScreen(
                    profileId = entry.arguments?.getString("profileId") ?: "new",
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() }
                )
            }

            // ---------------- Pestañas principales ----------------
            composable(Routes.HOME) {
                TvScaffold(appTabs, currentRoute, navController::selectTab, trailing = profileBadge) {
                    HomeScreen(
                        onOpenDetail = { navController.navigate(Routes.detail(it.id)) },
                        onPlay = { navController.navigate(Routes.player(it.id)) }
                    )
                }
            }
            composable(Routes.MOVIES) {
                TvScaffold(appTabs, currentRoute, navController::selectTab, trailing = profileBadge) {
                    MoviesScreen(
                        onOpenDetail = { navController.navigate(Routes.detail(it.id)) }
                    )
                }
            }
            composable(Routes.SEARCH) {
                TvScaffold(appTabs, currentRoute, navController::selectTab, trailing = profileBadge) {
                    SearchScreen(
                        onOpenDetail = { navController.navigate(Routes.detail(it.id)) }
                    )
                }
            }
            composable(Routes.SETTINGS) {
                TvScaffold(appTabs, currentRoute, navController::selectTab, trailing = profileBadge) {
                    SettingsScreen(
                        onSwitchProfile = { navController.navigate(Routes.PROFILES) }
                    )
                }
            }

            // ---------------- Contenido ----------------
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
