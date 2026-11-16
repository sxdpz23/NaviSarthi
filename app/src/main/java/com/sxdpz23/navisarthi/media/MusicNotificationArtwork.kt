package com.sxdpz23.navisarthi.media

import android.app.Notification
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Artwork only from media notifications; never read message notifications. */
internal object MusicNotificationArtwork {
    data class Entry(val notificationKey: String, val title: String, val icon: Icon)
    private val _entries = MutableStateFlow<Map<String, Entry>>(emptyMap())
    val entries = _entries.asStateFlow()

    fun posted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        if (!notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return
        val icon = notification.getLargeIcon()
        if (icon == null) { removed(sbn); return }
        _entries.value = _entries.value + (sbn.packageName to Entry(sbn.key,
            notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(), icon))
    }

    fun removed(sbn: StatusBarNotification) {
        if (_entries.value[sbn.packageName]?.notificationKey == sbn.key)
            _entries.value = _entries.value - sbn.packageName
    }

    fun clear() { _entries.value = emptyMap() }
}

internal fun artworkTitleMatches(trackTitle: String, notificationTitle: String): Boolean =
    trackTitle.isNotBlank() && trackTitle.trim().equals(notificationTitle.trim(), ignoreCase = true)
