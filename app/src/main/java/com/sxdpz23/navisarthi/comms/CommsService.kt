package com.sxdpz23.navisarthi.comms

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sxdpz23.navisarthi.MainActivity
import com.sxdpz23.navisarthi.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** User-started media only. Android must never recreate a microphone/location session silently. */
class CommsService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: CommsRepository
    private var started = false
    override fun onCreate() {
        super.onCreate()
        repository = CommsRepository.get(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Ride communications", NotificationManager.IMPORTANCE_LOW),
        )
        scope.launch {
            repository.state.map { it.sharing to it.audio }.distinctUntilChanged().collect { (sharing, audio) ->
                if (!started) return@collect
                if (!sharing && audio == CommsAudioState.OFF) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
                else foreground(sharing, audio != CommsAudioState.OFF)
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: run { stopSelf(); return START_NOT_STICKY }
        started = true
        try {
            when (action) {
                START_LOCATION -> {
                    require(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                    foreground(true, repository.state.value.audio != CommsAudioState.OFF)
                    repository.startSharing()
                }
                JOIN_AUDIO -> {
                    require(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                    foreground(repository.state.value.sharing, true)
                    repository.joinAudio()
                }
                STOP_LOCATION -> repository.stopSharing()
                LEAVE_AUDIO -> repository.leaveAudio()
                STOP_ALL -> { repository.stopSharing(); repository.leaveAudio(); stopSelf() }
                else -> stopSelf()
            }
        } catch (_: Exception) {
            repository.stopLocalMedia()
            repository.reportError("Comms could not start. Check the microphone and location permissions, then try again.")
            stopSelf()
        }
        if (!repository.state.value.sharing && repository.state.value.audio == CommsAudioState.OFF) stopSelf()
        return START_NOT_STICKY
    }
    private fun foreground(sharing: Boolean, audio: Boolean) {
        val types = (if (sharing) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0) or
            (if (audio) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        if (types == 0) return
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_comms)
            .setContentTitle("Comms is active")
            .setContentText(listOfNotNull(if (audio) "Ride audio" else null, if (sharing) "Location sharing" else null).joinToString(" · "))
            .setContentIntent(open).setOngoing(true).setSilent(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        if (audio) notification.addAction(0, "Leave audio", control(LEAVE_AUDIO, 1))
        if (sharing) notification.addAction(0, "Stop sharing", control(STOP_LOCATION, 2))
        ServiceCompat.startForeground(this, NOTIFICATION, notification.build(), types)
    }
    private fun control(action: String, id: Int): PendingIntent = PendingIntent.getService(
        this, id, Intent(this, CommsService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    override fun onDestroy() {
        started = false; scope.cancel()
        if (repository.state.value.sharing) repository.stopSharing()
        repository.stopLocalMedia(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val START_LOCATION = "comms.start_location"
        const val STOP_LOCATION = "comms.stop_location"
        const val JOIN_AUDIO = "comms.join_audio"
        const val LEAVE_AUDIO = "comms.leave_audio"
        const val STOP_ALL = "comms.stop_all"
        private const val CHANNEL = "comms_ride"
        private const val NOTIFICATION = 7312
        fun start(context: Context, action: String) {
            try { ContextCompat.startForegroundService(context, Intent(context, CommsService::class.java).setAction(action)) }
            catch (_: Exception) { CommsRepository.get(context).reportError("Open Comms on your phone and try again.") }
        }
    }
}
