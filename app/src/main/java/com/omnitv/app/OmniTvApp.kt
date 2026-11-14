package com.omnitv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material.Text
import com.omnitv.app.data.CatalogRepository
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.Providers
import com.omnitv.app.data.RemoteCatalog
import com.omnitv.app.data.Studios
import com.omnitv.app.data.StreamHub
import com.omnitv.app.data.StremioClient
import com.omnitv.app.data.Video
import com.omnitv.app.player.PlayerScreen
import com.omnitv.app.ui.components.ProfileBadge
import com.omnitv.app.ui.components.TabItem
import com.omnitv.app.ui.components.TvScaffold
import com.omnitv.app.ui.screens.DetailScreen
import com.omnitv.app.ui.screens.EditProfileScreen
import com.omnitv.app.ui.screens.HomeScreen
import com.omnitv.app.ui.screens.ManageProfilesScreen
import com.omnitv.app.ui.screens.MoviesScreen
import com.omnitv.app.ui.screens.PlatformCatalogScreen
import com.omnitv.app.ui.screens.PlataformasScreen
import com.omnitv.app.ui.screens.ProfileSelectScreen
import com.omnitv.app.ui.screens.SearchScreen
import com.omnitv.app.ui.screens.SettingsScreen
import com.omnitv.app.ui.screens.StudioCatalogScreen
import com.omnitv.app.ui.theme.AppType
import com.omnitv.app.ui.theme.Background
import com.omnitv.app.ui.theme.TextPrimary
import com.omnitv.app.ui.theme.TextSecondary

/** Rutas de navegación. */
object Routes {
    const val PROFILES = "profiles"
    const val MANAGE_PROFILES = "manageProfiles"
    const val EDIT_PROFILE = "editProfile/{profileId}"
    const val HOME = "home"
    const val MOVIES = "movies"
    const val PLATFORMS = "platforms"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{videoId}"
    const val PLAYER = "player/{videoId}"
    const val PLATFORM = "platform/{providerId}"
    const val STUDIO = "studio/{studioId}"

    fun editProfile(profileId: String) = "editProfile/$profileId"
    fun detail(videoId: String) = "detail/$videoId"
    fun player(videoId: String) = "player/$videoId"
    fun platform(providerId: Int) = "platform/$providerId"
    fun studio(studioId: Int) = "studio/$studioId"
}

private val appTabs = listOf(
    TabItem("Inicio", Routes.HOME, R.drawable.ic_home),
    TabItem("Películas", Routes.MOVIES, R.drawable.ic_film),
    TabItem("Plataformas", Routes.PLATFORMS, R.drawable.ic_platforms),
    TabItem("Buscar", Routes.SEARCH, R.drawable.ic_search),
    TabItem("Ajustes", Routes.SETTINGS, R.drawable.ic_settings)
)

/**
 * Raíz de la app: tema Omni TV + navegación.
 *
 * Flujo profesional: puerta de perfiles → navegación por pestañas tvOS.
 * El avatar del perfil siempre está accesible en la esquina superior derecha.
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
                            popUpTo(Routes.PROFILES) { inclusive = false }
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
                        onOpenDetail = { navController.navigate(Routes.detail(it.id.substringBefore('~'))) },
                        onPlay = { navController.navigate(Routes.player(it.id)) },
                        onSeeAll = { navController.navigate(Routes.platform(it.id)) },
                        onOpenPlatforms = { navController.navigate(Routes.PLATFORMS) },
                        onOpenStudio = { navController.navigate(Routes.studio(it.id)) }
                    )
                }
            }
            composable(Routes.MOVIES) {
                TvScaffold(appTabs, currentRoute, navController::selectTab, trailing = profileBadge) {
                    MoviesScreen(
                        onOpenDetail = { navController.navigate(Routes.detail(it.id.substringBefore('~'))) }
                    )
                }
            }
            composable(Routes.SEARCH) {
                TvScaffold(appTabs, currentRoute, navController::selectTab, trailing = profileBadge) {
                    SearchScreen(
                        onOpenDetail = { navController.navigate(Routes.detail(it.id.substringBefore('~'))) }
                    )
                }
            }
            composable(Routes.PLATFORMS) {
                TvScaffold(appTabs, currentRoute, navController::selectTab, trailing = profileBadge) {
                    PlataformasScreen(
                        onOpenProvider = { navController.navigate(Routes.platform(it.id)) },
                        onOpenStudio = { navController.navigate(Routes.studio(it.id)) }
                    )
                }
            }
            composable(
                route = Routes.STUDIO,
                arguments = listOf(navArgument("studioId") { type = NavType.IntType })
            ) { entry ->
                val studioId = entry.arguments?.getInt("studioId") ?: -1
                val studio = Studios.byId(studioId)
                if (studio != null) {
                    StudioCatalogScreen(
                        studio = studio,
                        onBack = { navController.popBackStack() },
                        onOpenDetail = { navController.navigate(Routes.detail(it.id)) }
                    )
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
            composable(
                route = Routes.PLATFORM,
                arguments = listOf(navArgument("providerId") { type = NavType.IntType })
            ) { entry ->
                val providerId = entry.arguments?.getInt("providerId") ?: -1
                val provider = Providers.byId(providerId)
                if (provider != null) {
                    PlatformCatalogScreen(
                        provider = provider,
                        onBack = { navController.popBackStack() },
                        onOpenDetail = { navController.navigate(Routes.detail(it.id)) }
                    )
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
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
                var video by remember { mutableStateOf(CatalogRepository.find(videoId)) }
                if (video == null) {
                    Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Cargando…", style = AppType.metadata, color = TextSecondary)
                        }
                    }
                    LaunchedEffect(videoId) {
                        video = RemoteCatalog.fetchDetail(
                            Video(
                                id = videoId, title = "", description = "",
                                backdropUrl = "", cardUrl = "", year = 0,
                                durationMinutes = 0, rating = 0.0, certification = "",
                                quality = "HD", genres = emptyList(), videoUrl = "",
                                isRemote = true,
                                source = if (videoId.startsWith("tt")) StremioClient.CINEMETA_NAME else StremioClient.NUVIO_NAME,
                                remoteType = null
                            )
                        )
                        if (video == null) navController.popBackStack()
                    }
                } else {
                    DetailScreen(
                        video = video!!,
                        onBack = { navController.popBackStack() },
                        onPlay = { navController.navigate(Routes.player(it.id)) },
                        onOpenDetail = { navController.navigate(Routes.detail(it.id.substringBefore('~'))) }
                    )
                }
            }
            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("videoId") { type = NavType.StringType })
            ) { entry ->
                val videoId = entry.arguments?.getString("videoId").orEmpty()
                val raw = remember(videoId) { CatalogRepository.find(videoId) }

                when {
                    raw == null -> LaunchedEffect(Unit) { navController.popBackStack() }

                    // Todo el contenido es remoto: resolver siempre vía addons configurados.
                    // Sin enlaces no hay reproducción silenciosa con demo.
                    else -> {
                        var resolved by remember { mutableStateOf<Video?>(null) }
                        var error by remember { mutableStateOf<String?>(null) }

                        if (resolved == null && error == null) {
                            Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Buscando enlaces…", style = AppType.metadata, color = TextSecondary)
                                }
                            }
                            LaunchedEffect(videoId) {
                                val streams = StreamHub.resolve(raw)
                                if (streams.isNotEmpty()) {
                                    resolved = raw.copy(
                                        sources = streams,
                                        videoUrl = streams.first().url,
                                        streamFormat = streams.first().format,
                                        badge = null
                                    )
                                } else {
                                    // Si el vídeo trae URL directa (p.ej. HLS de proveedor), úsala
                                    if (raw.videoUrl.isNotEmpty()) {
                                        resolved = raw
                                    } else {
                                        error = StreamHub.lastStatus.value.ifEmpty {
                                            "No hay fuentes disponibles. Configura un addon de streams en Ajustes."
                                        }
                                    }
                                }
                            }
                        } else if (error != null) {
                            Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
                                Column(
                                    Modifier.padding(horizontal = 48.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Sin fuentes disponibles", style = AppType.sectionTitle, color = TextPrimary)
                                    Spacer(Modifier.height(12.dp))
                                    Text(error!!, style = AppType.metadata, color = TextSecondary)
                                    Spacer(Modifier.height(24.dp))
                                    androidx.compose.foundation.layout.Row {
                                        com.omnitv.app.ui.components.PillButton(
                                            text = "Volver",
                                            iconRes = R.drawable.ic_back,
                                            primary = false,
                                            onClick = { navController.popBackStack() }
                                        )
                                    }
                                }
                            }
                        } else {
                            PlayerScreen(
                                video = resolved!!,
                                onBack = { navController.popBackStack() },
                                onPlayNext = { next ->
                                    navController.navigate(Routes.player(next.id)) {
                                        popUpTo(Routes.PLAYER) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
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
