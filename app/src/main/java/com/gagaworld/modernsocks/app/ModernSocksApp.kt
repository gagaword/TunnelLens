package com.gagaworld.modernsocks.app

import androidx.annotation.StringRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gagaworld.modernsocks.R
import com.gagaworld.modernsocks.data.preferences.ThemeMode
import com.gagaworld.modernsocks.ui.approuting.AppRoutingRoute
import com.gagaworld.modernsocks.ui.home.HomeRoute
import com.gagaworld.modernsocks.ui.profileedit.ProfileEditRoute
import com.gagaworld.modernsocks.ui.profiles.ProfilesRoute
import com.gagaworld.modernsocks.ui.settings.SettingsScreen
import com.gagaworld.modernsocks.ui.settings.SettingsViewModel
import com.gagaworld.modernsocks.ui.theme.ModernSocksTheme
import kotlinx.coroutines.launch

internal const val OVERFLOW_SETTINGS_TEST_TAG = "overflow_settings"

private enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home("home", R.string.navigation_home, Icons.Default.Home),
    Profiles("profiles", R.string.navigation_profiles, Icons.AutoMirrored.Filled.List),
    Settings("settings", R.string.navigation_settings, Icons.Default.Settings),
}

private object ProfileEditDestination {
    const val NEW_ROUTE = "profile/new"
    const val EDIT_ROUTE = "profile/{profileId}"

    fun route(profileId: Long): String = "profile/$profileId"
}

private object AppRoutingDestination {
    const val ROUTE = "profile/{profileId}/apps"
    fun route(profileId: Long): String = "profile/$profileId/apps"
}

@Composable
fun ModernSocksApp(container: AppContainer) {
    val settingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(
            container.settingsRepository,
            container.appLanguageController,
        )
    }
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    ModernSocksTheme(
        darkTheme = darkTheme,
        dynamicColor = settings.dynamicColor,
    ) {
        AppNavigation(
            container = container,
            settingsViewModel = settingsViewModel,
        )
    }
}

@Composable
private fun AppNavigation(
    container: AppContainer,
    settingsViewModel: SettingsViewModel,
) {
    val navController = rememberNavController()

    fun navigateTo(destination: TopLevelDestination) {
        navController.navigate(destination.route) {
            popUpTo(TopLevelDestination.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        AppNavHost(
            navController = navController,
            container = container,
            settingsViewModel = settingsViewModel,
            useLargeScreenLayout = maxWidth >= 600.dp,
            onNavigate = ::navigateTo,
        )
    }
}

@Composable
private fun TopLevelShell(
    selected: TopLevelDestination,
    useLargeScreenLayout: Boolean,
    onNavigate: (TopLevelDestination) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    if (useLargeScreenLayout) {
        LargeTopLevelShell(selected, onNavigate, content)
    } else {
        CompactTopLevelShell(selected, onNavigate, content)
    }
}

@Composable
private fun CompactTopLevelShell(
    selected: TopLevelDestination,
    onNavigate: (TopLevelDestination) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(56.dp),
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        modifier = Modifier.padding(top = 12.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        modifier = Modifier.padding(top = 4.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                TopLevelDestination.entries.forEach { destination ->
                    NavigationDrawerItem(
                        selected = selected == destination,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                if (selected != destination) onNavigate(destination)
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                AppTopBar(
                    selected = selected,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNavigate = onNavigate,
                )
            },
        ) { padding -> content(Modifier.padding(padding)) }
    }
}

@Composable
private fun LargeTopLevelShell(
    selected: TopLevelDestination,
    onNavigate: (TopLevelDestination) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail {
            TopLevelDestination.entries.forEach { destination ->
                NavigationRailItem(
                    selected = selected == destination,
                    onClick = { onNavigate(destination) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            topBar = {
                AppTopBar(
                    selected = selected,
                    onOpenDrawer = null,
                    onNavigate = onNavigate,
                )
            },
        ) { padding -> content(Modifier.padding(padding)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    selected: TopLevelDestination,
    onOpenDrawer: (() -> Unit)?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val titleRes = if (selected == TopLevelDestination.Home) {
        R.string.app_name
    } else {
        selected.labelRes
    }

    CenterAlignedTopAppBar(
        title = { Text(stringResource(titleRes)) },
        navigationIcon = {
            if (onOpenDrawer != null) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.navigation_open_drawer),
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.navigation_profiles)) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                    },
                    onClick = {
                        menuExpanded = false
                        onNavigate(TopLevelDestination.Profiles)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.navigation_settings)) },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    modifier = Modifier.testTag(OVERFLOW_SETTINGS_TEST_TAG),
                    onClick = {
                        menuExpanded = false
                        onNavigate(TopLevelDestination.Settings)
                    },
                )
            }
        },
    )
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    container: AppContainer,
    settingsViewModel: SettingsViewModel,
    useLargeScreenLayout: Boolean,
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Home.route,
        modifier = modifier,
        enterTransition = {
            if (initialState.destination.route.isTopLevelRoute() &&
                targetState.destination.route.isTopLevelRoute()
            ) {
                topLevelEnterTransition()
            } else {
                forwardEnterTransition()
            }
        },
        exitTransition = {
            if (initialState.destination.route.isTopLevelRoute() &&
                targetState.destination.route.isTopLevelRoute()
            ) {
                topLevelExitTransition()
            } else {
                forwardExitTransition()
            }
        },
        popEnterTransition = { backwardEnterTransition() },
        popExitTransition = { backwardExitTransition() },
    ) {
        composable(TopLevelDestination.Home.route) {
            TopLevelShell(
                selected = TopLevelDestination.Home,
                useLargeScreenLayout = useLargeScreenLayout,
                onNavigate = onNavigate,
            ) { contentModifier ->
                HomeRoute(
                    vpnController = container.vpnController,
                    profileRepository = container.profileRepository,
                    modifier = contentModifier,
                )
            }
        }
        composable(TopLevelDestination.Profiles.route) {
            TopLevelShell(
                selected = TopLevelDestination.Profiles,
                useLargeScreenLayout = useLargeScreenLayout,
                onNavigate = onNavigate,
            ) { contentModifier ->
                ProfilesRoute(
                    repository = container.profileRepository,
                    transferManager = container.profileTransferManager,
                    onAddProfile = { navController.navigate(ProfileEditDestination.NEW_ROUTE) },
                    onEditProfile = { id ->
                        navController.navigate(ProfileEditDestination.route(id))
                    },
                    modifier = contentModifier,
                )
            }
        }
        composable(TopLevelDestination.Settings.route) {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val language by settingsViewModel.language.collectAsStateWithLifecycle()
            TopLevelShell(
                selected = TopLevelDestination.Settings,
                useLargeScreenLayout = useLargeScreenLayout,
                onNavigate = onNavigate,
            ) { contentModifier ->
                SettingsScreen(
                    settings = settings,
                    language = language,
                    onLanguageChanged = settingsViewModel::setLanguage,
                    onThemeModeChanged = settingsViewModel::setThemeMode,
                    onDynamicColorChanged = settingsViewModel::setDynamicColor,
                    onAutoConnectChanged = settingsViewModel::setAutoConnect,
                    onExpandAdvancedChanged = settingsViewModel::setExpandAdvanced,
                    modifier = contentModifier,
                )
            }
        }
        composable(ProfileEditDestination.NEW_ROUTE) {
            ProfileEditRoute(
                profileId = null,
                profileRepository = container.profileRepository,
                settingsRepository = container.settingsRepository,
                onBack = { navController.navigateUpTo(TopLevelDestination.Profiles.route) },
                onSaved = { navController.navigateUpTo(TopLevelDestination.Profiles.route) },
                onAppRouting = {},
            )
        }
        composable(
            route = ProfileEditDestination.EDIT_ROUTE,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            ProfileEditRoute(
                profileId = entry.arguments?.getLong("profileId"),
                profileRepository = container.profileRepository,
                settingsRepository = container.settingsRepository,
                onBack = { navController.navigateUpTo(TopLevelDestination.Profiles.route) },
                onSaved = { navController.navigateUpTo(TopLevelDestination.Profiles.route) },
                onAppRouting = { profileId ->
                    navController.navigate(AppRoutingDestination.route(profileId))
                },
            )
        }
        composable(
            route = AppRoutingDestination.ROUTE,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) { entry ->
            AppRoutingRoute(
                profileId = requireNotNull(entry.arguments?.getLong("profileId")),
                profileRepository = container.profileRepository,
                installedAppRepository = container.installedAppRepository,
                onBack = {
                    navController.navigateUpTo(ProfileEditDestination.route(
                        requireNotNull(entry.arguments?.getLong("profileId")),
                    ))
                },
            )
        }
    }
}

private fun String?.isTopLevelRoute(): Boolean =
    TopLevelDestination.entries.any { it.route == this }

private fun NavHostController.navigateUpTo(fallbackRoute: String) {
    if (!navigateUp()) {
        navigate(fallbackRoute) { launchSingleTop = true }
    }
}

private fun topLevelEnterTransition(): EnterTransition =
    fadeIn(
        animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing),
    ) + scaleIn(
        initialScale = 0.985f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
    )

private fun topLevelExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(durationMillis = 140))

private fun forwardEnterTransition(): EnterTransition =
    fadeIn(
        animationSpec = tween(durationMillis = 220, delayMillis = 35),
    ) + slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth / 10 },
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
    )

private fun forwardExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(durationMillis = 150)) + slideOutHorizontally(
        targetOffsetX = { fullWidth -> -fullWidth / 16 },
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
    )

private fun backwardEnterTransition(): EnterTransition =
    fadeIn(
        animationSpec = tween(durationMillis = 210, delayMillis = 25),
    ) + slideInHorizontally(
        initialOffsetX = { fullWidth -> -fullWidth / 12 },
        animationSpec = tween(durationMillis = 270, easing = FastOutSlowInEasing),
    )

private fun backwardExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(durationMillis = 140)) + slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth / 12 },
        animationSpec = tween(durationMillis = 230, easing = FastOutSlowInEasing),
    )
