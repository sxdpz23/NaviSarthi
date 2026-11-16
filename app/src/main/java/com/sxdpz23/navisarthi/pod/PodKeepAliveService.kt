package com.sxdpz23.navisarthi.pod

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import com.sxdpz23.navisarthi.MainActivity

/** BLE + GPS foreground lifetime; deliberately acquires no Wi-Fi lock or network binding. */
class PodKeepAliveService : Service() {
    private var wake: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val vehiclePrefs = getSharedPreferences("vehicle", MODE_PRIVATE)
        if (intent?.getLongExtra("sessionGeneration", -1) != vehiclePrefs.getLong("sessionGeneration", 0) ||
            vehiclePrefs.getString("model", null) != "Meteor350" ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(startId); return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("tripper_pod", "Tripper Pod connection", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = Notification.Builder(this, "tripper_pod")
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentTitle("NaviSarthi · Meteor 350")
                .setContentText("Tripper Pod connection active. Open NaviSarthi to manage navigation.")
                .setContentIntent(open).setOngoing(true).build()
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            startForeground(350, notification, types)
            if (wake?.isHeld != true) wake = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "navisarthi:pod").apply {
                    setReferenceCounted(false); acquire()
                }
        } catch (e: Exception) {
            android.util.Log.w("PodKeepAlive", "Foreground connection could not start", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        if (wake?.isHeld == true) wake?.release()
        wake = null
        super.onDestroy()
    }
    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, PodKeepAliveService::class.java)
                .putExtra("sessionGeneration", context.getSharedPreferences("vehicle", Context.MODE_PRIVATE).getLong("sessionGeneration", 0)))
        }
        fun stop(context: Context) { context.stopService(Intent(context, PodKeepAliveService::class.java)) }
    }
}
