package com.example.navisarthi.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.media.MediaMetadata
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.example.navisarthi.data.RideDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Observes existing phone players; never creates a player or changes its audio route. */
class MediaInfoProvider(private val context: Context) {
    private val prefs = context.getSharedPreferences("console_music", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean("enabled", false))
    val enabled = _enabled.asStateFlow()
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying = _nowPlaying.asStateFlow()
    private val _status = MutableStateFlow("Music off")
    val status = _status.asStateFlow()
    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, NaviSarthiNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val owners = MusicObservationOwners()
    private val running get() = owners.active
    private var listening = false
    private val observed = mutableMapOf<android.media.session.MediaSession.Token, MediaController>()
    private var lastError: String? = null
    private val sessionsCallback = MediaSessionManager.OnActiveSessionsChangedListener { refresh() }
    private var controller: MediaController? = null
    private val commandGate = MusicCommandGate()
    private val volumeGate = MusicCommandGate()
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }
    // Recovery only: normal player and metadata changes arrive through callbacks.
    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            refresh()
            handler.postDelayed(this, 10_000)
        }
    }

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean("enabled", value).apply()
        _enabled.value = value
        refresh()
    }

    internal fun start(owner: MusicObservationOwner = MusicObservationOwner.CONSOLE) {
        val wasRunning = running
        owners.acquire(owner)
        if (!wasRunning) {
            diagnostic("observation started")
            handler.postDelayed(poll, 10_000)
        }
        refresh()
    }

    internal fun stop(owner: MusicObservationOwner = MusicObservationOwner.CONSOLE) {
        owners.release(owner)
        if (running) return
        closeObservation()
    }

    fun close() {
        owners.clear()
        closeObservation()
    }

    private fun closeObservation() {
        handler.removeCallbacks(poll)
        detach()
        _nowPlaying.value = null
        _status.value = if (_enabled.value) "Music controls inactive" else "Music off"
        diagnostic("observation stopped")
    }

    private fun detach() {
        if (listening) runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsCallback) }
        listening = false
        observed.values.forEach { runCatching { it.unregisterCallback(callback) } }
        observed.clear()
        controller = null
    }

    private fun observe(sessions: List<MediaController>) {
        val tokens = sessions.map { it.sessionToken }.toSet()
        observed.keys.filter { it !in tokens }.forEach { token ->
            runCatching { observed.remove(token)?.unregisterCallback(callback) }
        }
        sessions.forEach { player ->
            if (player.sessionToken !in observed) {
                player.registerCallback(callback, handler)
                observed[player.sessionToken] = player
            }
        }
    }

    private fun bind(next: MediaController?) {
        if (next?.sessionToken != controller?.sessionToken) {
            diagnostic("selected player=${next?.packageName ?: "none"}")
        }
        controller = next
    }

    private fun diagnostic(message: String) {
        Log.i("NsMusic", message)
        RideDiagnostics.log("media", message)
    }

    fun refresh() {
        if (!running || !_enabled.value || !isAccessGranted(context)) {
            detach()
            _nowPlaying.value = null
            val status = when {
                !_enabled.value -> "Music off"
                !running -> "Music controls inactive"
                else -> "Notification access needed"
            }
            if (_status.value != status) diagnostic(status)
            _status.value = status
            return
        }
        runCatching {
            if (!listening) {
                checkNotNull(msm) { "Media session service unavailable" }
                    .addOnActiveSessionsChangedListener(sessionsCallback, component, handler)
                listening = true
                diagnostic("active-session listener registered")
            }
            val sessions = msm?.getActiveSessions(component).orEmpty()
            observe(sessions)
            // Prefer playing sessions; keep the selected paused player stable in Android's ordering.
            val eligible = sessions.filter { it.playbackState != null && it.metadata != null }
            val next = eligible.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: eligible.firstOrNull { it.sessionToken == controller?.sessionToken }
                ?: eligible.firstOrNull()
            bind(next)
            val state = next?.playbackState
            val md = next?.metadata
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            _nowPlaying.value = if (next == null || md == null || !hasMusicMetadata(title, artist)) null else NowPlaying(
                title = title,
                album = md.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                artist = artist,
                art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
                isPlaying = state?.state == PlaybackState.STATE_PLAYING,
                actions = state?.actions ?: 0L,
                playbackState = state?.state,
            )
            _status.value = when {
                next == null -> "Open a music player on your phone"
                state?.state == PlaybackState.STATE_PLAYING -> "Playing"
                state?.state == PlaybackState.STATE_PAUSED -> "Paused"
                else -> "Player ready"
            }
            lastError = null
        }.onFailure {
            val error = "music refresh failed: ${it.javaClass.simpleName}"
            if (lastError != error) diagnostic(error)
            lastError = error
            detach()
            _nowPlaying.value = null
            _status.value = "Music access unavailable"
        }
    }

    fun diagnostics(): String {
        val track = _nowPlaying.value
        return """
            Music enabled: ${_enabled.value}
            Notification access: ${isAccessGranted(context)}
            Observation active: $running; listener registered: $listening
            Observed players: ${observed.size}
            Selected player: ${controller?.packageName ?: "none"}
            Player status: ${_status.value}
            Source metadata lengths (title/album/artist): ${track?.title?.length ?: 0}/${track?.album?.length ?: 0}/${track?.artist?.length ?: 0}
            Playing: ${track?.isPlaying}; supported actions: ${track?.actions ?: 0L}
        """.trimIndent()
    }

    fun skipNext(): Boolean = command(PlaybackState.ACTION_SKIP_TO_NEXT) { it.skipToNext() }
    fun skipPrevious(): Boolean = command(PlaybackState.ACTION_SKIP_TO_PREVIOUS) { it.skipToPrevious() }
    fun togglePlayback(): Boolean {
        refresh()
        val playing = controller?.playbackState?.state == PlaybackState.STATE_PLAYING
        return command(if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY,
            PlaybackState.ACTION_PLAY_PAUSE) { if (playing) it.pause() else it.play() }
    }

    /** Adjust only the phone media stream; Android retains routing and safe-volume limits. */
    fun adjustMusicVolume(increase: Boolean): Boolean {
        if (!running || !_enabled.value) return false
        val now = SystemClock.elapsedRealtime()
        if (!volumeGate.allows(now, 1L, 1L)) return false
        return runCatching {
            val audio = checkNotNull(context.getSystemService(AudioManager::class.java))
            if (audio.isVolumeFixed) {
                _status.value = "Volume is fixed on this device"
                return false
            }
            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val limit = if (increase) audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                else audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            if (current == limit) {
                _status.value = if (increase) "Media volume at maximum" else "Media volume at minimum"
                return false
            }
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
            volumeGate.sent(now)
            diagnostic(if (increase) "media volume up requested" else "media volume down requested")
            true
        }.getOrElse {
            diagnostic("volume failed: ${it.javaClass.simpleName}")
            _status.value = "Media volume unavailable"
            false
        }
    }

    private fun command(action: Long, alternative: Long = 0L,
        send: (MediaController.TransportControls) -> Unit): Boolean {
        refresh()
        val selected = controller ?: return false
        val actions = selected.playbackState?.actions ?: 0L
        if (actions and (action or alternative) == 0L) {
            _status.value = "Action unavailable in this player"
            return false
        }
        val now = SystemClock.elapsedRealtime()
        if (!commandGate.allows(now, actions, action or alternative)) return false
        return runCatching {
            send(selected.transportControls)
            commandGate.sent(now)
            true // Dispatched, not confirmation that the remote player acted.
        }.getOrElse {
            diagnostic("command failed: ${it.javaClass.simpleName}")
            _status.value = "Music command failed"
            false
        }
    }

    companion object {
        fun isAccessGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?: return false
            val expected = ComponentName(context, NaviSarthiNotificationListener::class.java)
            return flat.split(":").any { ComponentName.unflattenFromString(it) == expected }
        }
        fun accessSettingsIntent() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
