package com.sxdpz23.navisarthi.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sxdpz23.navisarthi.ui.NaviSarthiIcons
import com.sxdpz23.navisarthi.ui.screens.*
import com.sxdpz23.navisarthi.ui.theme.*
import com.sxdpz23.navisarthi.viewmodel.AppViewModel
import com.sxdpz23.navisarthi.viewmodel.ConnStage
import com.sxdpz23.navisarthi.viewmodel.ConnectionState
import com.sxdpz23.navisarthi.viewmodel.DashViewModel
import com.sxdpz23.navisarthi.viewmodel.RouteViewModel
import com.sxdpz23.navisarthi.console.ConsoleType
import com.sxdpz23.navisarthi.pod.PodStage
import com.sxdpz23.navisarthi.pod.PodViewModel

sealed class Screen(val route: String) {
    object Home     : Screen("home")
    object Route    : Screen("route")
    object Nav      : Screen("nav")
    object Dash     : Screen("dash")
    object Pod      : Screen("pod")
    object Garage   : Screen("garage")
    object Rides    : Screen("rides")
    object Groups   : Screen("groups")
    object NavigationSettings : Screen("navigation_settings")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    appViewModel: AppViewModel = viewModel(),
    dashViewModel: DashViewModel = viewModel(),
    routeViewModel: RouteViewModel = viewModel(),
    podViewModel: PodViewModel,
    onVehicleSelected: (VehicleModel) -> Unit,
    openNav: Boolean = false,
    onNavOpened: () -> Unit = {},
) {
    val pod = LocalVehicle.current.consoleType == ConsoleType.TRIPPER_POD
    val allowScreenOff by com.sxdpz23.navisarthi.dash.DashPreferences.screenOff.collectAsState()
    val displaySession by com.sxdpz23.navisarthi.dash.nav.NavDisplaySession.state.collectAsState()
    val screenActivity = androidx.compose.ui.platform.LocalContext.current.let { context ->
        var current: android.content.Context = context
        while (current is android.content.ContextWrapper && current !is android.app.Activity) current = current.baseContext
        current as? android.app.Activity
    }
    DisposableEffect(screenActivity, allowScreenOff, displaySession.active, pod) {
        val hold = displaySession.active && !pod && !allowScreenOff
        if (hold) screenActivity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (hold) screenActivity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val consoleRoute = if (pod) Screen.Pod.route else Screen.Dash.route
    LaunchedEffect(pod) { if (pod) podViewModel.autoConnectSaved() }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val garageEnabled by appViewModel.garageEnabled.collectAsState()

    // Covers restored back stacks as well as disabling Garage from Settings.
    LaunchedEffect(garageEnabled, currentRoute) {
        if (!garageEnabled && currentRoute == Screen.Garage.route) {
            navController.navigate(consoleRoute) {
                popUpTo(consoleRoute) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Analytics screen_view per destination. A single-Activity Compose app doesn't auto-log these,
    // so feed each nav route to Analytics — that's what makes per-feature usage (and time spent on
    // each screen) visible. No-op when Firebase isn't configured.
    LaunchedEffect(currentRoute) {
        currentRoute?.let { com.sxdpz23.navisarthi.util.Telemetry.logScreen(it) }
    }

    fun navigateTab(route: String) {
        if (route == currentRoute) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val tabs = listOf(
        GlassTab(consoleRoute, NaviSarthiIcons.Gauge, if (pod) "Pod" else "Dash"),
        GlassTab(Screen.Nav.route, NaviSarthiIcons.Navi, "Nav"),
    ) + (if (garageEnabled) listOf(GlassTab(Screen.Garage.route, NaviSarthiIcons.Wrench, "Garage")) else emptyList()) + listOf(
        GlassTab(Screen.Groups.route, NaviSarthiIcons.HelmetIntercom, "Groups"),
        GlassTab(Screen.Settings.route, NaviSarthiIcons.Gear, "Settings"),
    )
    fun rank(route: String?) = tabs.indexOfFirst { it.route == route }.coerceAtLeast(0)
    val activity = androidx.compose.ui.platform.LocalContext.current as androidx.activity.ComponentActivity
    val navigationViewModel: com.sxdpz23.navisarthi.navigation.ui.map.MapViewModel = viewModel(viewModelStoreOwner = activity)
    val pageMotion = com.sxdpz23.navisarthi.navigation.ui.PageTransitions.enabled.value
    val backdrop = rememberGraphicsLayer()
    var backdropOrigin by remember { mutableStateOf(Offset.Zero) }
    var compactBar by remember { mutableStateOf(false) }
    val barScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y < -6f) compactBar = true
                if (consumed.y > 6f) compactBar = false
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(currentRoute) {
        compactBar = false
        if (currentRoute != Screen.Nav.route) com.sxdpz23.navisarthi.dash.nav.NavDisplaySession.hidePhone()
    }
    LaunchedEffect(openNav) {
        if (openNav) { navigateTab(Screen.Nav.route); onNavOpened() }
    }

    val garageTab by appViewModel.garageTab.collectAsState()

    // Single source of truth for connection status: the real dash stage. Fixes Home
    // claiming "Connected" while the Dash screen says it isn't.
    //
    // IMPORTANT: collect WITHOUT reading the state in the composable body. While streaming,
    // dashViewModel.ui updates ~15×/s (frame counter, rider position…). Reading it directly here
    // recomposed the entire nav scaffold 15×/s, which starved/janked the bottom-nav tabs — you
    // couldn't open Home while connected. derivedStateOf re-evaluates cheaply but only recomposes
    // readers when `conn` actually changes (i.e. when the STAGE changes).
    val dashUiState = dashViewModel.ui.collectAsState()
    val dashConn by remember {
        derivedStateOf {
            when (dashUiState.value.stage) {
                ConnStage.STREAMING -> ConnectionState.Connected
                ConnStage.WIFI, ConnStage.AUTH -> ConnectionState.Searching
                else -> ConnectionState.Offline
            }
        }
    }
    val podState by podViewModel.ble.state.collectAsState()
    val conn = if (!pod) dashConn else when (podState.stage) {
        PodStage.READY -> ConnectionState.Connected
        PodStage.SCANNING, PodStage.CONNECTING, PodStage.RECONNECTING, PodStage.AUTHORIZING, PodStage.PIN_REQUIRED -> ConnectionState.Searching
        else -> ConnectionState.Offline
    }

    if (com.sxdpz23.navisarthi.navigation.ui.PipMode.active.value) {
        NavScreen(navigationViewModel, pod, onOpenSettings = {})
        return
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()
            .then(if (currentRoute == Screen.Nav.route || currentRoute == Screen.Route.route) Modifier else Modifier.statusBarsPadding())
            .nestedScroll(barScroll).onGloballyPositioned { backdropOrigin = it.positionInRoot() }.drawWithContent {
            backdrop.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop)
        }) {
            NavHost(
                navController = navController,
                startDestination = consoleRoute,
                enterTransition = { if (!pageMotion) androidx.compose.animation.EnterTransition.None else slideInHorizontally(tween(240)) { if (rank(targetState.destination.route) >= rank(initialState.destination.route)) 24 else -24 } + fadeIn(tween(220)) },
                exitTransition = { if (!pageMotion) androidx.compose.animation.ExitTransition.None else slideOutHorizontally(tween(240)) { if (rank(targetState.destination.route) >= rank(initialState.destination.route)) -24 else 24 } + fadeOut(tween(180)) },
            ) {
                // Restore an older task directly into the new console-first shell.
                composable(Screen.Home.route) {
                    LaunchedEffect(Unit) {
                        navController.navigate(consoleRoute) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }

                composable(Screen.Nav.route) {
                    NavScreen(navigationViewModel, pod, onOpenSettings = { navController.navigate(Screen.NavigationSettings.route) })
                }
                // Compatibility with restored tasks; there is only one route editor now.
                composable(Screen.Route.route) {
                    LaunchedEffect(Unit) {
                        navController.navigate(Screen.Nav.route) {
                            popUpTo(Screen.Route.route) { inclusive = true }; launchSingleTop = true
                        }
                    }
                }
                composable(consoleRoute) {
                    if (pod) PodScreen(vm = podViewModel) else DashScreen(vm = dashViewModel)
                }

                composable(Screen.Garage.route) {
                    if (garageEnabled) GarageScreen(
                        tab = garageTab,
                        onTabChange = { appViewModel.setGarageTab(it) },
                    )
                }

                composable(Screen.Groups.route) { GroupsScreen() }

                composable(Screen.Rides.route) {
                    RidesScreen()
                }

                composable(Screen.NavigationSettings.route) {
                    LaunchedEffect(Unit) { navController.navigate(Screen.Settings.route) {
                        popUpTo(Screen.NavigationSettings.route) { inclusive = true }; launchSingleTop = true
                    } }
                }
                composable(Screen.Settings.route) {
                    UnifiedSettingsScreen(
                        navigation = navigationViewModel,
                        conn = conn, onConnChange = appViewModel::setConn,
                        dash = dashViewModel, pod = podViewModel,
                        onVehicleSelected = onVehicleSelected, garageEnabled = garageEnabled,
                        onGarageEnabledChange = appViewModel::setGarageEnabled,
                        onBack = { navigateTab(consoleRoute) },
                        onOpenRides = { navController.navigate(Screen.Rides.route) },
                    )
                }
            }
        }

        // Checks GitHub Releases on app open; shows an update dialog only if a newer
        // version is published. Renders nothing otherwise.
        com.sxdpz23.navisarthi.ui.components.UpdatePrompt()

        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
            LiquidGlassBar(tabs, if (currentRoute in listOf(Screen.Rides.route, Screen.NavigationSettings.route)) Screen.Settings.route else if (currentRoute == Screen.Route.route) Screen.Nav.route else currentRoute ?: consoleRoute,
                backdrop, backdropOrigin, compactBar, onSelect = { compactBar = false; navigateTab(it) })
        }
    }
}
