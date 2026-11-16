package com.sxdpz23.navisarthi.dash.protocol

internal data class NativeMusic(val title: String, val album: String, val artist: String,
    val playing: Boolean?, val art: ByteArray?, val artSource: String = "unknown")

internal fun sameMusicContent(previous: NativeMusic?, current: NativeMusic?): Boolean =
    previous != null && current != null && previous.title == current.title && previous.album == current.album &&
        previous.artist == current.artist && previous.playing == current.playing &&
        java.util.Arrays.equals(previous.art, current.art)

internal fun musicTransferNeeded(previous: NativeMusic?, current: NativeMusic, lastSentMs: Long, nowMs: Long): Boolean =
    previous == null || previous.title != current.title || previous.album != current.album ||
        previous.artist != current.artist || previous.playing != current.playing ||
        !java.util.Arrays.equals(previous.art, current.art) || nowMs - lastSentMs >= 10_000L
