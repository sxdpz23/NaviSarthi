package com.example.navisarthi.media

internal enum class MusicObservationOwner { SCREEN, CONSOLE }

/** Idempotent lifecycle ownership: stopping the screen must not stop console playback controls. */
internal class MusicObservationOwners {
    private val owners = mutableSetOf<MusicObservationOwner>()
    val active get() = owners.isNotEmpty()
    fun acquire(owner: MusicObservationOwner) { owners.add(owner) }
    fun release(owner: MusicObservationOwner) { owners.remove(owner) }
    fun clear() { owners.clear() }
}

internal fun hasMusicMetadata(title: String, artist: String) = title.isNotBlank() || artist.isNotBlank()
