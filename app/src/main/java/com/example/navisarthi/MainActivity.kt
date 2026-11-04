package com.example.navisarthi

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
import com.example.navisarthi.ui.components.VehiclePicker
import com.example.navisarthi.ui.theme.VehicleModel
import com.example.navisarthi.data.SyncRepository
import com.example.navisarthi.ui.navigation.AppNavigation
import com.example.navisarthi.ui.theme.Bg1
import com.example.navisarthi.ui.theme.NaviSarthiTheme
import com.example.navisarthi.viewmodel.RouteViewModel
import com.example.navisarthi.viewmodel.DashViewModel
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    private val dashViewModel: DashViewModel by viewModels()
    private val routeViewModel: RouteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sync = SyncRepository.get(applicationContext)
        // Drop any legacy account session while retaining the local database.
        if (com.example.navisarthi.data.FirebaseGate.isConfigured(applicationContext)) {
            sync.stopSync()
            FirebaseAuth.getInstance().signOut()
        }

        // Maintenance + document-expiry reminders on app open (fire even if Garage is never
        // opened). Only on a fresh launch — savedInstanceState != null means this is a config
        // change (e.g. rotation), where re-running it is wasted work.
        if (savedInstanceState == null) {
            Thread {
                com.example.navisarthi.data.MaintenanceNotifier.check(
                    applicationContext, sync.maintenanceItems(), sync.odometer().toInt()
                )
                com.example.navisarthi.data.MaintenanceNotifier.checkDocuments(applicationContext, sync.documents())
            }.start()
        }

        handleIntent(intent)
        val vehiclePrefs = getSharedPreferences("vehicle", MODE_PRIVATE)
        val savedVehicle = VehicleModel.entries.firstOrNull { it.name == vehiclePrefs.getString("model", null) }
        setContent {
            var vehicle by remember { mutableStateOf(savedVehicle) }
            fun chooseVehicle(model: VehicleModel) {
                vehiclePrefs.edit().putString("model", model.name).apply()
                vehicle = model
            }
            NaviSarthiTheme(vehicle = vehicle ?: VehicleModel.Himalayan450) {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg1) {
                    if (vehicle == null) {
                        VehiclePicker(current = null, onSelect = ::chooseVehicle)
                    } else {
                        AppNavigation(routeViewModel = routeViewModel, dashViewModel = dashViewModel,
                            onVehicleSelected = ::chooseVehicle)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                    routeViewModel.handleSharedText(text, dashViewModel.remainingJourney())
                }
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data?.toString() ?: return
                routeViewModel.handleSharedText(uri, dashViewModel.remainingJourney())
            }
        }
    }
}
