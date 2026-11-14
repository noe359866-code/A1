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
import com.omnitv.app.data.PlaybackResolver
import com.omnitv.app.data.PlaybackResolution
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
    // Un id de addon puede contener /, :, ~ o caracteres no ASCII. Un segmento
    // de Navigation debe llevarlos escapados; NavType.StringType los decodifica.
    private fun arg(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

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

    fun editProfile(profileId: String) = "editProfile/${arg(profileId)}"
    fun detail(videoId: String) = "detail/${arg(videoId)}"
    fun player(videoId: String) = "player/${arg(videoId)}"

    /** El título ya resuelto viaja al reproductor aunque la caché LRU haya evictado el id. */
    fun openPlayer(nav: NavHostController, video: Video) {
        CatalogRepository.registerNavigation(video)
        nav.navigate(player(video.id)) { launchSingleTop = true }
    }
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
                            popUpTo(Routes.PROFILES) { inclusive = true }
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
                        onPlay = { Routes.openPlayer(navController, it) },
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
                var video by remember(videoId) { mutableStateOf(CatalogRepository.find(videoId)) }
                var detailError by remember(videoId) { mutableStateOf(false) }
                if (video == null && !detailError) {
                    Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Cargando…", style = AppType.metadata, color = TextSecondary)
                        }
                    }
                    LaunchedEffect(videoId) {
                        val loaded = runCatching { RemoteCatalog.fetchDetail(
                            Video(
                                id = videoId, title = "", description = "",
                                backdropUrl = "", cardUrl = "", year = 0,
                                durationMinutes = 0, rating = 0.0, certification = "",
                                quality = "HD", genres = emptyList(), videoUrl = "",
                                isRemote = true,
                                source = if (videoId.startsWith("tt")) StremioClient.CINEMETA_NAME else StremioClient.NUVIO_NAME,
                                remoteType = null
                            )
                        ) }.getOrNull()
                        video = loaded
                        detailError = loaded == null
                    }
                } else if (detailError) {
                    Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No se pudo cargar la ficha", style = AppType.sectionTitle, color = TextPrimary)
                            Spacer(Modifier.height(16.dp))
                            com.omnitv.app.ui.components.PillButton("Volver", primary = false, onClick = { navController.popBackStack() })
                        }
                    }
                } else {
                    DetailScreen(
                        video = video!!,
                        onBack = { navController.popBackStack() },
                        onPlay = { Routes.openPlayer(navController, it) },
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
                if (raw == null) {
                    LaunchedEffect(videoId) { navController.popBackStack() }
                } else {
                    var resolution by remember(videoId) { mutableStateOf<PlaybackResolution?>(null) }
                    var attempt by remember(videoId) { mutableStateOf(0) }
                    LaunchedEffect(videoId, attempt) {
                        resolution = null
                        resolution = runCatching { PlaybackResolver.resolve(raw) }.getOrElse {
                            PlaybackResolution(emptyList(), "No se pudieron consultar las fuentes. Comprueba la red y reintenta.")
                        }
                    }
                    val result = resolution
                    when {
                        result == null -> Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Buscando enlaces…", style = AppType.sectionTitle, color = TextPrimary)
                                Spacer(Modifier.height(16.dp))
                                com.omnitv.app.ui.components.PillButton("Cancelar", primary = false, onClick = { navController.popBackStack() })
                            }
                        }
                        result.sources.isEmpty() -> Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
                            Column(Modifier.padding(horizontal = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Sin fuentes disponibles", style = AppType.sectionTitle, color = TextPrimary)
                                Spacer(Modifier.height(12.dp))
                                Text(result.message, style = AppType.metadata, color = TextSecondary)
                                Spacer(Modifier.height(24.dp))
                                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                                    com.omnitv.app.ui.components.PillButton("Reintentar", onClick = { attempt++ })
                                    com.omnitv.app.ui.components.PillButton("Volver", primary = false, onClick = { navController.popBackStack() })
                                }
                            }
                        }
                        else -> {
                            val first = result.sources.first()
                            PlayerScreen(
                                video = raw.copy(sources = result.sources, videoUrl = first.url, streamFormat = first.format, badge = null),
                                onBack = { navController.popBackStack() },
                                onPlayNext = { next ->
                                    CatalogRepository.registerNavigation(next)
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
