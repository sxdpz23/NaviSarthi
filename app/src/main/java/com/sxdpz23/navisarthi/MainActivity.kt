package com.sxdpz23.navisarthi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import com.sxdpz23.navisarthi.ui.components.VehiclePicker
import com.sxdpz23.navisarthi.ui.theme.VehicleModel
import com.sxdpz23.navisarthi.data.SyncRepository
import com.sxdpz23.navisarthi.ui.navigation.AppNavigation
import com.sxdpz23.navisarthi.ui.theme.Bg1
import com.sxdpz23.navisarthi.ui.theme.NaviSarthiTheme
import com.sxdpz23.navisarthi.viewmodel.RouteViewModel
import com.sxdpz23.navisarthi.viewmodel.DashViewModel
import com.google.firebase.auth.FirebaseAuth
import com.sxdpz23.navisarthi.console.ConsoleType
import com.sxdpz23.navisarthi.pod.PodViewModel

@dagger.hilt.android.AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var openNav by mutableStateOf(false)
    private val navigationViewModel: com.sxdpz23.navisarthi.navigation.ui.map.MapViewModel by viewModels()
    private val navigation by lazy {
        com.sxdpz23.navisarthi.navigation.NavigationActivityDelegate(this, navigationViewModel) {
            if (dashViewModel.remainingJourney() != null) dashViewModel.exitNavigation()
            if (podViewModel.remainingJourney() != null) podViewModel.exitNavigation()
        }
    }
    private val dashViewModel: DashViewModel by viewModels()
    private val routeViewModel: RouteViewModel by viewModels()
    private val podViewModel: PodViewModel by viewModels()

    private fun enableVehicle(model: VehicleModel?) {
        dashViewModel.setConsoleEnabled(model?.consoleType == ConsoleType.TRIPPER_DASH)
        podViewModel.setConsoleEnabled(model?.consoleType == ConsoleType.TRIPPER_POD)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigation.onCreate(handleStartupIntent = activeJourney() == null)
        if (navigationViewModel.state.value.navigating) openNav = true
        enableEdgeToEdge()

        val sync = SyncRepository.get(applicationContext)
        // Drop any legacy account session while retaining the local database.
        if (com.sxdpz23.navisarthi.data.FirebaseGate.isConfigured(applicationContext)) {
            sync.stopSync()
            FirebaseAuth.getInstance().signOut()
        }

        // Maintenance + document-expiry reminders on app open (fire even if Garage is never
        // opened). Only on a fresh launch — savedInstanceState != null means this is a config
        // change (e.g. rotation), where re-running it is wasted work.
        if (savedInstanceState == null) {
            Thread {
                com.sxdpz23.navisarthi.data.MaintenanceNotifier.check(
                    applicationContext, sync.maintenanceItems(), sync.odometer().toInt()
                )
                com.sxdpz23.navisarthi.data.MaintenanceNotifier.checkDocuments(applicationContext, sync.documents())
            }.start()
        }

        handleIntent(intent)
        val vehiclePrefs = getSharedPreferences("vehicle", MODE_PRIVATE)
        val savedVehicle = VehicleModel.entries.firstOrNull { it.name == vehiclePrefs.getString("model", null) }
        enableVehicle(savedVehicle)
        val vehicleSession = com.sxdpz23.navisarthi.console.VehicleSessionCoordinator(savedVehicle?.name,
            stop = {
                dashViewModel.setConsoleEnabled(false)
                podViewModel.setConsoleEnabled(false)
            },
            persist = {
                vehiclePrefs.edit().putString("model", it)
                    .putLong("sessionGeneration", vehiclePrefs.getLong("sessionGeneration", 0) + 1).apply()
            },
            activate = { type ->
                dashViewModel.setConsoleEnabled(type == ConsoleType.TRIPPER_DASH)
                podViewModel.setConsoleEnabled(type == ConsoleType.TRIPPER_POD)
            })
        setContent {
            var vehicle by remember { mutableStateOf(savedVehicle) }
            fun chooseVehicle(model: VehicleModel) {
                if (vehicleSession.select(model.name, model.consoleType)) vehicle = model
            }
            NaviSarthiTheme(vehicle = vehicle ?: VehicleModel.Himalayan450) {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg1) {
                    if (vehicle == null) {
                        VehiclePicker(current = null, onSelect = ::chooseVehicle)
                    } else {
                        key(vehicle) {
                            AppNavigation(routeViewModel = routeViewModel, dashViewModel = dashViewModel,
                                podViewModel = podViewModel, onVehicleSelected = ::chooseVehicle,
                                openNav = openNav, onNavOpened = { openNav = false })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // An active console journey receives the share as its next stop in the route editor.
        // Do not also apply the phone map's automatic-start link preference to the same share.
        if (activeJourney() == null) navigation.handleIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if ((intent?.action in listOf(Intent.ACTION_SEND, Intent.ACTION_VIEW, com.sxdpz23.navisarthi.navigation.ui.TripShortcut.ACTION)) && activeJourney() == null) {
            openNav = true // The shared map ViewModel has received the link.
            return
        }
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                    routeViewModel.handleSharedText(text, activeJourney())
                }
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data?.toString() ?: return
                routeViewModel.handleSharedText(uri, activeJourney())
            }
        }
    }

    private fun activeJourney() = if (getSharedPreferences("vehicle", MODE_PRIVATE).getString("model", null) == VehicleModel.Meteor350.name)
        podViewModel.remainingJourney() else dashViewModel.remainingJourney()

    override fun onStart() { super.onStart(); navigation.onStart() }
    override fun onStop() { navigation.onStop(); super.onStop() }
    override fun onResume() { super.onResume(); navigation.onResume() }
    override fun onPictureInPictureModeChanged(active: Boolean, config: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(active, config)
        navigation.onPictureInPictureChanged(active)
    }
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (!event.isPrintingKey && com.sxdpz23.navisarthi.navigation.ui.ZoomKeys.handle(event)) return true
        return super.dispatchKeyEvent(event) || (event.isPrintingKey && com.sxdpz23.navisarthi.navigation.ui.ZoomKeys.handle(event))
    }
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(com.sxdpz23.navisarthi.navigation.ui.AppLocale.wrap(
            com.sxdpz23.navisarthi.navigation.ui.AdaptiveDensity.wrap(base)))
    }
}
