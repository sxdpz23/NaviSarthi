package com.example.navisarthi.media

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Passive observer. Never scans, bonds, changes routing or opens a new Bluetooth connection. */
@SuppressLint("MissingPermission")
internal class BluetoothAccessoryBattery(private val context: Context) {
    private val prefs = context.getSharedPreferences("accessory_battery", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean("enabled", true))
    val enabled = _enabled.asStateFlow()
    private val _iconsOnly = MutableStateFlow(prefs.getBoolean("icons_only", false))
    val iconsOnly = _iconsOnly.asStateFlow()
    fun setIconsOnly(value: Boolean) { prefs.edit().putBoolean("icons_only", value).apply(); _iconsOnly.value = value }
    private val _readings = MutableStateFlow<List<AccessoryBattery>>(emptyList())
    val readings = _readings.asStateFlow()
    private val _status = MutableStateFlow("Bluetooth access needed")
    val status = _status.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter get() = manager?.adapter
    private val state = AccessoryBatteryState()
    private val mobileBattery = MobileBatteryMonitor(context)
    val mobilePercent = mobileBattery.percent
    private val profiles = mutableMapOf<Int, BluetoothProfile>()
    private val requestedProfiles = mutableSetOf<Int>()
    private val aclDevices = mutableMapOf<String, BluetoothDevice>()
    private var connectedIds = emptySet<String>()
    private var registered = false
    private var screen = false
    private var streaming = false
    private val wanted get() = screen || streaming
    private val profileIds = listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP, BluetoothProfile.HEARING_AID, BluetoothProfile.LE_AUDIO)

    fun hasAccess() = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    fun setEnabled(value: Boolean) { prefs.edit().putBoolean("enabled", value).apply(); _enabled.value = value; refresh() }
    fun setScreenVisible(value: Boolean) { screen = value; schedule() }
    fun setStreaming(value: Boolean) { streaming = value; schedule() }
    private fun schedule() {
        handler.removeCallbacks(poll)
        refresh()
        if (wanted) handler.postDelayed(poll, 5000)
    }
    private val poll = object : Runnable {
        override fun run() {
            refresh()
            if (wanted) handler.postDelayed(this, 5000)
        }
    }
    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (!wanted || !_enabled.value || !hasAccess()) {
                runCatching { adapter?.closeProfileProxy(profile, proxy) }
                return
            }
            profiles[profile] = proxy
            refresh()
        }
        override fun onServiceDisconnected(profile: Int) {
            profiles.remove(profile)
            requestedProfiles.remove(profile)
            if (wanted) refresh()
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (!hasAccess()) { refresh(); return }
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            runCatching {
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> if (device != null) aclDevices[device.address] = device
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (device != null) {
                        aclDevices.remove(device.address)
                        state.disconnect(device.address)
                    }
                    BATTERY_ACTION -> if (device != null) state.report(device.address, intent.getIntExtra(BATTERY_EXTRA, -1))
                }
                refresh()
            }.onFailure { stopObservation(); _status.value = "Accessory information unavailable" }
        }
    }
    fun refresh() {
        mobileBattery.setActive(wanted && _enabled.value)
        if (!wanted || !_enabled.value || !hasAccess()) {
            stopObservation()
            _status.value = when {
                !_enabled.value -> "Battery pill off"
                !hasAccess() -> "Nearby devices permission needed"
                else -> "Accessory monitoring inactive"
            }
            return
        }
        runCatching {
            val bluetooth = adapter
            if (!registered) {
                val filter = IntentFilter().apply {
                    addAction(BATTERY_ACTION)
                    addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                    addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                    addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
                    addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
                    addAction("android.bluetooth.action.LE_AUDIO_CONNECTION_STATE_CHANGED")
                    addAction("android.bluetooth.hearingaid.profile.action.CONNECTION_STATE_CHANGED")
                }
                // Bluetooth system broadcasts can originate from the separate Bluetooth process.
                ContextCompat.registerReceiver(context, receiver, filter, Manifest.permission.BLUETOOTH_CONNECT,
                    handler, ContextCompat.RECEIVER_EXPORTED)
                registered = true
            }
            if (bluetooth == null || !bluetooth.isEnabled) {
                closeProfiles()
                state.clear(); aclDevices.clear(); connectedIds = emptySet(); _readings.value = emptyList()
                _status.value = "Bluetooth is off"
                return
            }
            profileIds.forEach { profile ->
                if (requestedProfiles.add(profile) && !bluetooth.getProfileProxy(context, listener, profile)) requestedProfiles.remove(profile)
            }
            val devices = mutableMapOf<String, BluetoothDevice>()
            profiles.values.toList().forEach { proxy ->
                proxy.connectedDevices.forEach { devices[it.address] = it }
            }
            manager?.getConnectedDevices(BluetoothProfile.GATT)?.forEach { devices[it.address] = it }
            devices.putAll(aclDevices)
            val ids = devices.keys.toSet()
            (connectedIds - ids).forEach(state::disconnect)
            connectedIds = ids
            state.connections(devices.mapValues { (_, device) -> device.name.orEmpty() },
                devices.mapValues { (_, device) -> iconFor(device) })
            _readings.value = state.readings()
            _status.value = when {
                devices.isEmpty() -> "No connected accessories detected"
                _readings.value.isEmpty() -> "Waiting for an accessory battery report"
                else -> "${_readings.value.size} accessory batteries available"
            }
        }.onFailure {
            stopObservation()
            _status.value = "Accessory information unavailable"
        }
    }
    private fun iconFor(device: BluetoothDevice): AccessoryIcon {
        val info = device.bluetoothClass ?: return AccessoryIcon.BLUETOOTH
        return when (info.deviceClass) {
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> AccessoryIcon.HEADPHONES
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> AccessoryIcon.SPEAKER
            BluetoothClass.Device.WEARABLE_WRIST_WATCH -> AccessoryIcon.WATCH
            else -> if (info.majorDeviceClass == BluetoothClass.Device.Major.PERIPHERAL && info.deviceClass and 0x40 != 0)
                AccessoryIcon.KEYBOARD else AccessoryIcon.BLUETOOTH
        }
    }
    private fun closeProfiles() {
        val old = profiles.toMap()
        profiles.clear(); requestedProfiles.clear()
        old.forEach { (profile, proxy) -> runCatching { adapter?.closeProfileProxy(profile, proxy) } }
    }
    private fun stopObservation() {
        if (registered) { runCatching { context.unregisterReceiver(receiver) }; registered = false }
        closeProfiles()
        aclDevices.clear(); connectedIds = emptySet(); state.clear(); _readings.value = emptyList()
    }
    fun close() { screen = false; streaming = false; handler.removeCallbacks(poll); mobileBattery.close(); stopObservation() }

    companion object {
        // Exposed as system broadcasts on supporting Android builds; not a public cached-battery getter.
        private const val BATTERY_ACTION = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val BATTERY_EXTRA = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }
}
