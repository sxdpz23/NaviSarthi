package com.example.navisarthi.media

internal enum class ConsoleMusicAction { PREVIOUS, NEXT, TOGGLE, VOLUME_UP, VOLUME_DOWN }

/** Codes from the media-from-navigation captures indexed in capture.md. */
internal object ConsoleMusicInput {
    fun actionFor(code: Int, enabled: Boolean, ringing: Boolean, activeCall: Boolean): ConsoleMusicAction? {
        if (!enabled || ringing) return null
        return when (code) {
            0x0A -> ConsoleMusicAction.PREVIOUS
            0x09 -> ConsoleMusicAction.NEXT
            0x05 -> ConsoleMusicAction.TOGGLE
            0x06 -> if (activeCall) null else ConsoleMusicAction.VOLUME_UP
            0x07 -> if (activeCall) null else ConsoleMusicAction.VOLUME_DOWN
            else -> null // 0x18 enters the overlay; Home/Back and map zoom stay console/navigation-owned.
        }
    }
}
