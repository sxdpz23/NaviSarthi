package com.example.navisarthi

import com.example.navisarthi.media.artworkTitleMatches
import org.junit.Assert.*
import org.junit.Test

class MusicArtworkTest {
    @Test fun notificationFallbackMustMatchCurrentTrack() {
        assertTrue(artworkTitleMatches("Current song", "Current song"))
        assertTrue(artworkTitleMatches(" Current Song ", "current song"))
        assertFalse(artworkTitleMatches("New song", "Previous song"))
        assertFalse(artworkTitleMatches("", ""))
        assertFalse(artworkTitleMatches(" ", "Current song"))
    }
}
