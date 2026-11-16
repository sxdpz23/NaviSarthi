package com.sxdpz23.navisarthi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sxdpz23.navisarthi.navigation.ui.map.MapViewModel
import com.sxdpz23.navisarthi.navigation.ui.settings.MAP_ROUTE
import com.sxdpz23.navisarthi.navigation.ui.settings.SettingsSection

@Composable
fun NavigationSettingsScreen(vm: MapViewModel, onBack: () -> Unit) {
    val controller = rememberNavController()
    val entry by controller.currentBackStackEntryAsState()
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(entry?.destination?.route) {
        if (entry?.destination?.route == MAP_ROUTE) {
            if (opened) onBack() else {
                opened = true
                controller.navigate(SettingsSection.HUB.route)
            }
        }
    }
    BackHandler(entry?.destination?.route == SettingsSection.HUB.route, onBack)
    Box(Modifier.fillMaxSize().padding(bottom = 112.dp)) {
        com.sxdpz23.navisarthi.navigation.ui.theme.NavigationTheme {
            com.sxdpz23.navisarthi.navigation.ui.settings.SettingsScreen(vm, controller)
        }
    }
}
