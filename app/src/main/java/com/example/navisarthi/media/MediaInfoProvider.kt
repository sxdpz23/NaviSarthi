package com.example.navisarthi.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.media.MediaMetadata
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
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
    private var running = false
    private var controller: MediaController? = null
    private val commandGate = MusicCommandGate()
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }
    // Also detects grant/revocation and competing sessions while the phone is locked.
    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean("enabled", value).apply()
        _enabled.value = value
        refresh()
    }

    fun start() {
        if (running) { refresh(); return }
        running = true
        handler.post(poll)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(poll)
        bind(null)
        _nowPlaying.value = null
    }

    private fun bind(next: MediaController?) {
        if (next?.sessionToken == controller?.sessionToken) return
        runCatching { controller?.unregisterCallback(callback) }
        controller = next
        runCatching { controller?.registerCallback(callback, handler) }
    }

    fun refresh() {
        if (!_enabled.value || !isAccessGranted(context)) {
            bind(null)
            _nowPlaying.value = null
            _status.value = if (_enabled.value) "Notification access needed" else "Music off"
            return
        }
        runCatching {
            val sessions = msm?.getActiveSessions(component).orEmpty()
            // Prefer playing sessions; keep the selected paused player stable in Android's ordering.
            val eligible = sessions.filter { it.playbackState != null && it.metadata != null }
            val next = eligible.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: eligible.firstOrNull { it.sessionToken == controller?.sessionToken }
                ?: eligible.firstOrNull()
            bind(next)
            val state = next?.playbackState
            val md = next?.metadata
            _nowPlaying.value = if (next == null || md == null) null else NowPlaying(
                title = md.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                album = md.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART),
                isPlaying = state?.state == PlaybackState.STATE_PLAYING,
                actions = state?.actions ?: 0L,
            )
            _status.value = when {
                next == null -> "Open a music player on your phone"
                state?.state == PlaybackState.STATE_PLAYING -> "Playing"
                state?.state == PlaybackState.STATE_PAUSED -> "Paused"
                else -> "Player ready"
            }
        }.onFailure {
            bind(null)
            _nowPlaying.value = null
            _status.value = "Music access unavailable"
        }
    }

    fun skipNext(): Boolean = command(PlaybackState.ACTION_SKIP_TO_NEXT) { it.skipToNext() }
    fun skipPrevious(): Boolean = command(PlaybackState.ACTION_SKIP_TO_PREVIOUS) { it.skipToPrevious() }
    fun togglePlayback(): Boolean {
        refresh()
        val playing = _nowPlaying.value?.isPlaying == true
        return command(if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY,
            PlaybackState.ACTION_PLAY_PAUSE) { if (playing) it.pause() else it.play() }
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
        }.getOrElse { _status.value = "Music command failed"; false }
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
