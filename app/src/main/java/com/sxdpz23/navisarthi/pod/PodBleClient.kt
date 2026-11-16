package com.sxdpz23.navisarthi.pod

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import com.sxdpz23.navisarthi.console.SessionEpoch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class PodStage { OFFLINE, SCANNING, CONNECTING, RECONNECTING, AUTHORIZING, PIN_REQUIRED, READY, ERROR }
data class PodDevice(val address: String, val name: String)
data class PodConnection(
    val stage: PodStage = PodStage.OFFLINE,
    val devices: List<PodDevice> = emptyList(),
    val address: String? = null,
    val error: String? = null,
    val firmware: String? = null,
    val serial: String? = null,
    val sent: Int = 0,
    val malformed: Int = 0,
)

/** All GATT and queue state is confined to Main. Every callback carries an attempt epoch. */
@SuppressLint("MissingPermission")
class PodBleClient(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("tripper_pod", Context.MODE_PRIVATE)
    private val manager = app.getSystemService(BluetoothManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val epoch = SessionEpoch()
    private val mutable = MutableStateFlow(PodConnection())
    val state = mutable.asStateFlow()
    val savedAddress: String? get() = prefs.getString("address", null)
    var autoConnect: Boolean
        get() = prefs.getBoolean("auto_connect", true)
        set(value) { prefs.edit().putBoolean("auto_connect", value).apply() }
    private var enabled = false
    private var wanted = false
    private var retries = 0
    private var gatt: BluetoothGatt? = null
    private var server: BluetoothGattServer? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var scan: ScanCallback? = null
    private val queue = PodWriteQueue()
    private var lastWrite = 0L
    private var latestScreen: ByteArray? = null
    private var resume = false

    fun hasPermissions() = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        .all { app.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) disconnect()
    }

    fun discover() {
        if (!enabled) return
        disconnect()
        if (!readyRadio()) return
        val token = epoch.next()
        activeToken = token
        mutable.value = PodConnection(stage = PodStage.SCANNING)
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = onMain(token) {
                val services = result.scanRecord?.serviceUuids.orEmpty()
                val name = result.scanRecord?.deviceName ?: result.device.name.orEmpty()
                if (!name.startsWith("RE_DISP", true) && ParcelUuid(PodProtocol.SERVICE) !in services) return@onMain
                val item = PodDevice(result.device.address, name.ifBlank { "Tripper Pod" })
                mutable.update { it.copy(devices = (it.devices + item).distinctBy { d -> d.address }) }
            }
            override fun onScanFailed(errorCode: Int) = onMain(token) { fail("Bluetooth scan failed ($errorCode)") }
        }
        scan = callback
        // Some firmware omits the service UUID from advertisements; validate it after connection.
        runCatching { manager.adapter.bluetoothLeScanner.startScan(null,
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback) }
            .onFailure { fail("Could not scan: ${it.message}") }
        later(token, 20_000) {
            stopScan()
            mutable.update { it.copy(stage = PodStage.OFFLINE, error =
                if (it.devices.isEmpty()) "No Pod found. Turn the ignition off and on, then scan again." else null) }
        }
    }

    fun connect(address: String? = savedAddress) {
        if (!enabled) return
        val selected = address ?: return
        wanted = true
        retries = 0
        begin(selected)
    }

    private fun begin(address: String) {
        closeAttempt()
        if (!enabled || !wanted || !readyRadio()) return
        val token = epoch.next()
        activeToken = token
        resume = address == savedAddress
        mutable.value = PodConnection(stage = PodStage.CONNECTING, address = address)
        val device = runCatching { manager.adapter.getRemoteDevice(address) }.getOrElse {
            fail("Invalid Pod address"); return
        }
        val serverCallback = object : BluetoothGattServerCallback() {
            override fun onServiceAdded(status: Int, service: BluetoothGattService) = onMain(token) {
                if (status != BluetoothGatt.GATT_SUCCESS) { fail("Could not create Pod reply service"); return@onMain }
                gatt = device.connectGatt(app, false, callback(token), BluetoothDevice.TRANSPORT_LE)
            }
            override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int,
                ch: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
                val bytes = value.copyOf()
                onMain(token) {
                    val allowed = device.address == address && ch.uuid == PodProtocol.CHARACTERISTIC && !preparedWrite && offset == 0
                    if (responseNeeded) server?.sendResponse(device, requestId,
                        if (allowed) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null)
                    if (allowed) receive(bytes)
                }
            }
        }
        runCatching {
            server = manager.openGattServer(app, serverCallback)
            checkNotNull(server) { "Bluetooth reply service unavailable" }
            val service = BluetoothGattService(PodProtocol.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            service.addCharacteristic(BluetoothGattCharacteristic(PodProtocol.CHARACTERISTIC,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE))
            check(server!!.addService(service)) { "Could not add Pod reply service" }
        }.onFailure { fail(it.message ?: "Bluetooth setup failed") }
        later(token, 20_000) {
            if (mutable.value.stage == PodStage.CONNECTING) fail("Pod connection timed out. Restart the ignition and try again.")
        }
    }

    private fun callback(token: Long) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) = onMain(token) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                if (!g.discoverServices()) fail("Could not discover Pod services")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                lost("Pod disconnected ($status). Restart the ignition if it cannot reconnect.")
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = onMain(token) {
            val ch = g.getService(PodProtocol.SERVICE)?.getCharacteristic(PodProtocol.CHARACTERISTIC)
            if (status != BluetoothGatt.GATT_SUCCESS || ch == null ||
                ch.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
                fail("This device does not expose a supported Tripper Pod service"); return@onMain
            }
            characteristic = ch
            mutable.update { it.copy(stage = PodStage.AUTHORIZING) }
            later(token, 250) {
                enqueue(PodProtocol.handshake(resume))
                if (!resume) mutable.update { it.copy(stage = PodStage.PIN_REQUIRED) }
                else { enqueue(PodProtocol.firmware()) }
                heartbeat(token)
            }
            later(token, 10_000) {
                if (mutable.value.stage == PodStage.AUTHORIZING) {
                    resume = false
                    enqueue(PodProtocol.handshake(false))
                    mutable.update { it.copy(stage = PodStage.PIN_REQUIRED, error = "Enter the PIN shown on your Pod") }
                }
            }
            later(token, 120_000) {
                if (mutable.value.stage in listOf(PodStage.PIN_REQUIRED, PodStage.AUTHORIZING))
                    fail("Pairing timed out. Restart the ignition and try again.")
            }
        }
        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) = onMain(token) {
            if (status != BluetoothGatt.GATT_SUCCESS) { lost("Pod write failed ($status)"); return@onMain }
            queue.complete()
            mutable.update { it.copy(sent = it.sent + 1) }
            later(token, 35) { drain() }
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) =
            onMain(token) { receive(value.copyOf()) }
    }

    private fun receive(bytes: ByteArray) {
        if (!PodProtocol.valid(bytes)) { mutable.update { it.copy(malformed = it.malformed + 1) }; return }
        when (PodProtocol.authorization(bytes)) {
            PodProtocol.Authorization.ACCEPTED -> if (mutable.value.stage in listOf(PodStage.AUTHORIZING, PodStage.PIN_REQUIRED)) authorized()
            PodProtocol.Authorization.REJECTED -> {
                resume = false
                mutable.update { it.copy(stage = PodStage.PIN_REQUIRED, error = "PIN rejected. Enter the six digits displayed on the Pod.") }
            }
            null -> when (bytes[0].toInt() and 255) {
                3 -> {
                    mutable.update { it.copy(firmware = "${bytes[1].toInt() and 255}.${bytes[2].toInt() and 255}") }
                    // Only a previously PIN-authorized device can resume. Require a protocol reply,
                    // not onConnectionStateChange or a successful local write, before reporting ready.
                    if (resume && mutable.value.stage == PodStage.AUTHORIZING) authorized()
                }
                0x30 -> mutable.update { it.copy(serial = bytes.sliceArray(1..8).joinToString("") { b -> "%02X".format(b) }) }
            }
        }
    }

    private fun authorized() {
        val address = mutable.value.address ?: return
        prefs.edit().putString("address", address).apply()
        retries = 0
        mutable.update { it.copy(stage = PodStage.READY, error = null) }
        syncClock()
        enqueue(PodProtocol.firmware()); enqueue(PodProtocol.serial())
        latestScreen?.let(::enqueue)
    }

    fun submitPin(pin: String) {
        if (!enabled || mutable.value.stage != PodStage.PIN_REQUIRED) return
        val packet = runCatching { PodProtocol.pin(pin) }.getOrElse {
            mutable.update { it.copy(error = "Enter exactly six digits") }; return
        }
        mutable.update { it.copy(stage = PodStage.AUTHORIZING, error = null) }
        enqueue(packet)
        val token = activeToken
        later(token, 10_000) {
            if (mutable.value.stage == PodStage.AUTHORIZING) mutable.update {
                it.copy(stage = PodStage.PIN_REQUIRED, error = "Authorization timed out. Check the displayed PIN and retry.")
            }
        }
    }

    fun syncClock() {
        if (mutable.value.stage != PodStage.READY) return
        val now = java.time.LocalTime.now()
        enqueue(PodProtocol.clock(now.hour, now.minute, prefs.getBoolean("clock24", true)))
    }

    fun screen(packet: ByteArray?) {
        latestScreen = packet?.copyOf()
        if (packet == null) queue.clearScreens()
        if (packet != null && mutable.value.stage == PodStage.READY) enqueue(packet)
    }

    private var activeToken = 0L
    private fun heartbeat(token: Long) {
        later(token, 1_000) {
            if (mutable.value.stage == PodStage.READY && latestScreen != null) enqueue(latestScreen!!)
            else if (SystemClock.elapsedRealtime() - lastWrite >= 2_000) enqueue(PodProtocol.firmware())
            heartbeat(token)
        }
    }

    private fun enqueue(bytes: ByteArray) {
        if (characteristic == null || !enabled || !wanted) return
        queue.offer(bytes); drain()
    }

    private fun drain() {
        val ch = characteristic ?: return
        val g = gatt ?: return
        val bytes = queue.next() ?: return
        lastWrite = SystemClock.elapsedRealtime()
        val marker = lastWrite
        val type = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val result = runCatching { g.writeCharacteristic(ch, bytes, type) }.getOrDefault(-1)
        if (result != BluetoothStatusCodes.SUCCESS) { lost("Bluetooth could not send to the Pod ($result)"); return }
        later(activeToken, 4_000) {
            if (queue.writing && lastWrite == marker) lost("Pod Bluetooth write timed out")
        }
    }

    private fun readyRadio(): Boolean {
        val error = when {
            !hasPermissions() -> "Allow Nearby devices access to connect your Tripper Pod"
            manager?.adapter == null -> "This phone has no Bluetooth adapter"
            !manager.adapter.isEnabled -> "Turn on Bluetooth to connect your Tripper Pod"
            else -> null
        }
        if (error != null) { fail(error); return false }
        return true
    }

    private fun lost(message: String) {
        val address = mutable.value.address
        closeAttempt()
        if (enabled && wanted && autoConnect && address != null && retries++ < 5) {
            mutable.update { it.copy(stage = PodStage.RECONNECTING, error = message) }
            val token = activeToken
            later(token, 2_000) { if (enabled && wanted) begin(address) }
        } else {
            wanted = false
            mutable.update { it.copy(stage = PodStage.ERROR, error = message) }
        }
    }

    private fun fail(message: String) {
        wanted = false
        closeAttempt()
        mutable.update { it.copy(stage = PodStage.ERROR, error = message) }
    }

    fun disconnect() {
        wanted = false
        latestScreen = null
        closeAttempt()
        mutable.update { it.copy(stage = PodStage.OFFLINE, error = null) }
    }

    fun forget() {
        disconnect()
        prefs.edit().remove("address").apply()
        mutable.value = PodConnection()
    }

    private fun stopScan() {
        scan?.let { callback -> runCatching { manager?.adapter?.bluetoothLeScanner?.stopScan(callback) } }
        scan = null
    }
    private fun closeAttempt() {
        activeToken = epoch.next()
        handler.removeCallbacksAndMessages(null)
        stopScan()
        runCatching { gatt?.disconnect() }; runCatching { gatt?.close() }
        runCatching { server?.close() }
        gatt = null; server = null; characteristic = null; queue.clear()
    }
    private fun onMain(token: Long, action: () -> Unit) {
        handler.post { if (enabled && epoch.current(token)) safely(action) }
    }
    private fun later(token: Long, ms: Long, action: () -> Unit) {
        handler.postDelayed({ if (enabled && epoch.current(token)) safely(action) }, ms)
    }
    private fun safely(action: () -> Unit) {
        runCatching(action).onFailure { fail("Bluetooth connection unavailable. Check permissions and retry.") }
    }
}
