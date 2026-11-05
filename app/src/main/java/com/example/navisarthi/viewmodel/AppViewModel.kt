package com.example.navisarthi.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionState { Connected, Searching, Offline }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val featurePrefs = app.getSharedPreferences("app_features", Context.MODE_PRIVATE)
    private val _garageEnabled = MutableStateFlow(featurePrefs.getBoolean("garage_enabled", false))
    val garageEnabled = _garageEnabled.asStateFlow()

    fun setGarageEnabled(enabled: Boolean) {
        featurePrefs.edit().putBoolean("garage_enabled", enabled).apply()
        _garageEnabled.value = enabled
    }

    private val _conn = MutableStateFlow(ConnectionState.Connected)
    val conn = _conn.asStateFlow()

    private val _garageTab = MutableStateFlow("Maintenance")
    val garageTab = _garageTab.asStateFlow()

    fun setConn(state: ConnectionState) { _conn.value = state }
    fun setGarageTab(tab: String) { _garageTab.value = tab }
}
