package com.example.navisarthi

import com.example.navisarthi.media.ConsoleMusicAction.*
import com.example.navisarthi.media.ConsoleMusicInput
import com.example.navisarthi.dash.protocol.DashCommands
import org.junit.Assert.*
import org.junit.Test

class ConsoleMusicTest {
    private fun action(code: Int, enabled: Boolean = true, ringing: Boolean = false, active: Boolean = false) =
        ConsoleMusicInput.actionFor(code, enabled, ringing, active)

    @Test fun capturedJoystickMappingPreservesLeftRightAndAddsToggleVolume() {
        assertEquals(PREVIOUS, action(0x0A))
        assertEquals(NEXT, action(0x09))
        assertEquals(TOGGLE, action(0x05))
        assertEquals(VOLUME_UP, action(0x06))
        assertEquals(VOLUME_DOWN, action(0x07))
    }
    @Test fun enteringOverlayAndNavigationButtonsNeverToggleMusic() {
        listOf(0x18, 0x12, 0x13, 0x14, 0xFF).forEach { assertNull(action(it)) }
    }
    @Test fun disabledMusicAndRingingCallsBlockMusicCommands() {
        listOf(0x0A, 0x09, 0x05, 0x06, 0x07).forEach {
            assertNull(action(it, enabled = false))
            assertNull(action(it, ringing = true))
        }
    }
    @Test fun activeCallKeepsDownForHangupAndDoesNotAlterMusicVolume() {
        assertNull(action(0x06, active = true))
        assertNull(action(0x07, active = true))
        assertEquals(NEXT, action(0x09, active = true))
    }
    private fun fields(title: String, album: String, artist: String): List<String> {
        val packet = DashCommands.nowPlaying(title, album, artist)
        assertEquals(0x05, packet[17].toInt())
        assertEquals(0x0D, packet[18].toInt())
        val length = ((packet[19].toInt() and 255) shl 8) or (packet[20].toInt() and 255)
        assertEquals(packet.size - 21, length)
        return packet.copyOfRange(21, packet.size).toString(Charsets.ISO_8859_1).split('\u0000')
    }
    @Test fun ribbonUsesCapturedAlbumTitleArtistOrderEvenWithoutAlbum() {
        assertEquals(listOf("Album", "Song", "Artist"), fields("Song", "Album", "Artist"))
        assertEquals(listOf("", "Song", "Artist"), fields("Song", "", "Artist"))
        assertEquals(listOf("", "", ""), fields("", "", ""))
    }
    @Test fun metadataUsesReferenceByteCodecAnd19UnitLimit() {
        assertEquals("Artist Guest", fields("Song", "", "Artist\u0000Guest")[2])
        assertEquals("A".repeat(19), fields("A".repeat(25), "", "Artist")[1])
        assertEquals("Café", fields("Café", "", "Artist")[1])
        assertEquals("\u00ff", fields("\u0939", "", "Artist")[1])
    }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    @Test fun nativeAvailabilityMatchesOgreConstants() {
        assertEquals("0016000200000000020100054b314720000517000155", DashCommands.musicAvailable(true).hex())
        assertEquals("0016000200000000020100054b3147200005170001aa", DashCommands.musicAvailable(false).hex())
    }
    @Test fun nativePlaybackStateMatchesOgreConstants() {
        assertEquals("0016000200000000020100054b314720000519000155", DashCommands.musicPlaybackState(true).hex())
        assertEquals("0016000200000000020100054b3147200005190001aa", DashCommands.musicPlaybackState(false).hex())
    }
}
