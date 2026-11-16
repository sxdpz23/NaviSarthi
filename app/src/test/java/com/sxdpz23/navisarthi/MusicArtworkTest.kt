package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.media.artworkTitleMatches
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
