package com.sxdpz23.navisarthi.ui.navigation

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
    object Dash     : Screen("dash")
    object Pod      : Screen("pod")
    object Garage   : Screen("garage")
    object Rides    : Screen("rides")
    object Settings : Screen("settings")
}

private data class NavTab(val screen: Screen, val icon: ImageVector, val label: String)

private val bottomTabs = listOf(
    NavTab(Screen.Home,   NaviSarthiIcons.Home,    "Home"),
    NavTab(Screen.Route,  NaviSarthiIcons.Navi,    "Route"),
    NavTab(Screen.Dash,   NaviSarthiIcons.Dash,    "Dash"),
    NavTab(Screen.Garage, NaviSarthiIcons.Wrench,  "Garage"),
    NavTab(Screen.Rides,  NaviSarthiIcons.History, "Rides"),
)

@Composable
fun AppNavigation(
    appViewModel: AppViewModel = viewModel(),
    dashViewModel: DashViewModel = viewModel(),
    routeViewModel: RouteViewModel = viewModel(),
    podViewModel: PodViewModel,
    onVehicleSelected: (VehicleModel) -> Unit,
) {
    val pod = LocalVehicle.current.consoleType == ConsoleType.TRIPPER_POD
    val consoleRoute = if (pod) Screen.Pod.route else Screen.Dash.route
    LaunchedEffect(pod) { if (pod) podViewModel.autoConnectSaved() }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val garageEnabled by appViewModel.garageEnabled.collectAsState()

    // Covers restored back stacks as well as disabling Garage from Settings.
    LaunchedEffect(garageEnabled, currentRoute) {
        if (!garageEnabled && currentRoute == Screen.Garage.route) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Home.route) { inclusive = true }
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

    // Navigate to a top-level destination with ONE consistent pattern — whether the tap came from
    // the bottom bar or a Home-screen button. The Home button used to do a plain navigate() while
    // the bottom bar used saveState/restoreState; mixing them corrupted the back stack so that,
    // after opening Dash from the Home button, tapping Home did nothing (target == start
    // destination + a restoreState mismatch). Routing both through this fixes it.
    fun navigateTab(route: String) {
        if (route == Screen.Home.route && navController.popBackStack(route, inclusive = false)) return
        navController.navigate(route) {
            popUpTo(Screen.Home.route)
            launchSingleTop = true
        }
    }

    val showBottomNav = currentRoute in listOf(
        Screen.Home.route, Screen.Route.route, consoleRoute,
        Screen.Garage.route, Screen.Rides.route,
    )

    val garageTab by appViewModel.garageTab.collectAsState()
    val routeState by routeViewModel.state.collectAsState()

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

    // Prefetch map tiles the moment a destination resolves (internet still reachable here)
    LaunchedEffect(routeState.destination?.lat, routeState.destination?.lng) {
        val d = routeState.destination
        if (!pod && d?.lat != null && d.lng != null) dashViewModel.prefetchTiles(d.lat, d.lng)
    }

    // Auto-navigate to Route when a Maps share arrives
    LaunchedEffect(routeState.pendingNavigate, currentRoute) {
        if (routeState.pendingNavigate && currentRoute != null) {
            if (currentRoute != Screen.Route.route) {
                navController.navigate(Screen.Route.route) { launchSingleTop = true }
            }
            routeViewModel.onNavigated()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        conn = conn,
                        onNavigate = { dest ->
                            when (dest) {
                                // Top-level destinations go through the shared tab navigation so the
                                // back stack stays consistent with the bottom bar (see navigateTab).
                                "route" -> navigateTab(Screen.Route.route)
                                "dash", "pod" -> navigateTab(consoleRoute)
                                "garage" -> if (garageEnabled) navigateTab(Screen.Garage.route)
                                // Settings is a detail screen (its own back), not a tab.
                                "settings" -> navController.navigate(Screen.Settings.route)
                            }
                        },
                        routeViewModel = routeViewModel,
                    )
                }

                composable(Screen.Route.route) {
                    RouteScreen(
                        routeViewModel = routeViewModel,
                        onBack = { navController.popBackStack() },
                        onSentToDash = { _ ->
                            val plan = routeViewModel.state.value
                            if (pod) {
                                podViewModel.startJourney(plan.stops, plan.mode, plan.legs, offline = plan.offline)
                                if (podViewModel.ble.state.value.stage != PodStage.READY) {
                                    if (podViewModel.ble.savedAddress != null) podViewModel.ble.connect()
                                    else podViewModel.ble.discover()
                                }
                            } else {
                                dashViewModel.startJourney(plan.stops, plan.mode, plan.legs, offline = plan.offline)
                                // Wi-Fi → auth → stream; Pod navigation never enters this branch.
                                dashViewModel.connect()
                            }
                            navController.navigate(consoleRoute) {
                                popUpTo(Screen.Home.route)
                            }
                        },
                    )
                }

                // The inactive console destination is absent from this vehicle's graph.
                composable(consoleRoute) {
                    if (pod) PodScreen(vm = podViewModel) else DashScreen(vm = dashViewModel)
                }

                composable(Screen.Garage.route) {
                    if (garageEnabled) GarageScreen(
                        tab = garageTab,
                        onTabChange = { appViewModel.setGarageTab(it) },
                    )
                }

                composable(Screen.Rides.route) {
                    RidesScreen()
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        conn = conn,
                        onConnChange = { appViewModel.setConn(it) },
                        dashViewModel = dashViewModel,
                        podViewModel = podViewModel,
                        onVehicleSelected = onVehicleSelected,
                        garageEnabled = garageEnabled,
                        onGarageEnabledChange = appViewModel::setGarageEnabled,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        // Checks GitHub Releases on app open; shows an update dialog only if a newer
        // version is published. Renders nothing otherwise.
        com.sxdpz23.navisarthi.ui.components.UpdatePrompt()

        if (showBottomNav) {
            NaviSarthiBottomNav(
                currentRoute = currentRoute,
                garageEnabled = garageEnabled,
                pod = pod,
                onNavSelect = { screen -> navigateTab(screen.route) },
            )
        }
    }
}

@Composable
private fun NaviSarthiBottomNav(
    currentRoute: String?,
    garageEnabled: Boolean,
    pod: Boolean,
    onNavSelect: (Screen) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xE60D0F11))
            .border(width = 1.dp, color = Line, shape = androidx.compose.ui.graphics.RectangleShape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .navigationBarsPadding(),
    ) {
        bottomTabs.map { if (pod && it.screen == Screen.Dash) it.copy(screen = Screen.Pod, label = "Pod") else it }
            .filter { garageEnabled || it.screen != Screen.Garage }.forEach { tab ->
            val active = currentRoute == tab.screen.route
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onNavSelect(tab.screen) }
                    .padding(vertical = 6.dp),
            ) {
                // Fixed-height container with the icon top-aligned and the active dot
                // bottom-aligned — so reserving space for the dot doesn't shove the
                // selected icon downward relative to the others.
                Box(Modifier.height(29.dp), contentAlignment = Alignment.TopCenter) {
                    Icon(
                        tab.icon, contentDescription = tab.label,
                        tint = if (active) Gold else TextLo,
                        modifier = Modifier.size(23.dp),
                    )
                    if (active) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Gold)
                        )
                    }
                }
                Text(
                    tab.label,
                    color = if (active) Gold else TextLo,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GeistFamily,
                )
            }
        }
    }
}
