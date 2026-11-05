package com.example.navisarthi

import com.example.navisarthi.media.MusicObservationOwner.*
import com.example.navisarthi.media.MusicObservationOwners
import com.example.navisarthi.media.hasMusicMetadata
import org.junit.Assert.*
import org.junit.Test

class MusicObservationTest {
    @Test fun screenOnlyObservationEndsOnScreenExit() {
        val owners = MusicObservationOwners()
        assertFalse(owners.active)
        owners.acquire(SCREEN)
        assertTrue(owners.active)
        owners.release(SCREEN)
        assertFalse(owners.active)
    }
    @Test fun closingSettingsKeepsConsoleObservation() {
        val owners = MusicObservationOwners()
        owners.acquire(CONSOLE)
        owners.acquire(SCREEN)
        owners.release(SCREEN)
        assertTrue(owners.active)
        owners.release(CONSOLE)
        assertFalse(owners.active)
    }
    @Test fun disconnectKeepsVisibleScreenObservation() {
        val owners = MusicObservationOwners()
        owners.acquire(SCREEN)
        owners.acquire(CONSOLE)
        owners.release(CONSOLE)
        assertTrue(owners.active)
        owners.clear()
        assertFalse(owners.active)
    }
    @Test fun duplicateLifecycleEventsDoNotLeakObservation() {
        val owners = MusicObservationOwners()
        owners.acquire(SCREEN)
        owners.acquire(SCREEN)
        owners.release(CONSOLE)
        assertTrue(owners.active)
        owners.release(SCREEN)
        owners.release(SCREEN)
        assertFalse(owners.active)
    }
    @Test fun blankMetadataIsHiddenButArtistOnlyIsKept() {
        assertFalse(hasMusicMetadata("", ""))
        assertFalse(hasMusicMetadata(" \n", "\t"))
        assertTrue(hasMusicMetadata("Song", ""))
        assertTrue(hasMusicMetadata("", "Artist"))
    }
}
