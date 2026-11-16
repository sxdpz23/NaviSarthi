package com.sxdpz23.navisarthi.dash.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Native phone replies must work even when requested by a Main-dispatcher UI callback. */
object CallHistoryTransfer {
    suspend fun send(
        category: CallHistoryCategory,
        labels: List<String>,
        key: ByteArray,
        allowed: () -> Boolean,
        sessionCurrent: () -> Boolean,
        sendPacket: (ByteArray) -> Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        for (index in 0..4) {
            currentCoroutineContext().ensureActive()
            if (!sessionCurrent()) return@withContext false
            val label = if (allowed()) labels.getOrNull(index).orEmpty() else ""
            if (!sendPacket(CallHistoryProtocol.slot(index, label, key))) return@withContext false
            delay(10)
        }
        currentCoroutineContext().ensureActive()
        if (!sessionCurrent()) return@withContext false
        sendPacket(CallHistoryProtocol.categoryReady(category))
    }
}
