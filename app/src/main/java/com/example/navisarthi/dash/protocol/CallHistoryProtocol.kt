package com.example.navisarthi.dash.protocol

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** OGRE r42/w2c native call menu requests and responses. */
enum class CallHistoryCategory(val code: Int, val callType: Int?) {
    FAVORITES(0x55, null), INCOMING(0xAA, 1), MISSED(0xBB, 3), OUTGOING(0xCC, 2);
    companion object {
        fun fromRequest(value: ByteArray): CallHistoryCategory? =
            if (value.size == 2 && value[0] == 0xAA.toByte()) entries.firstOrNull { it.code == (value[1].toInt() and 255) }
            else null
    }
}

data class ConsoleDialSelection(val category: CallHistoryCategory, val index: Int)

object CallHistoryProtocol {
    fun dialSelection(value: ByteArray): ConsoleDialSelection? {
        if (value.size != 2 || (value[0].toInt() and 255) !in 0..4) return null
        val category = CallHistoryCategory.entries.firstOrNull { it.code == (value[1].toInt() and 255) } ?: return null
        return ConsoleDialSelection(category, value[0].toInt() and 255)
    }
    fun missedCallCount(count: Int): ByteArray {
        val bounded = count.coerceIn(0, 65535)
        return K1GPacket.build(K1GPacket.tlv(0x06, 0x0A, (bounded shr 8) and 255, bounded and 255))
    }

    fun slot(index: Int, label: String, key: ByteArray): ByteArray {
        require(index in 0..4 && key.size == 32)
        val text = label.replace("\u0000", " ").take(19)
            .map { if (it.code < 255) it.code.toByte() else 0xFF.toByte() }.toByteArray() + byteArrayOf(0)
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return K1GPacket.build(K1GPacket.tlv(0x05, 0x1C + index, iv + cipher.doFinal(text)))
    }
    fun categoryReady(category: CallHistoryCategory): ByteArray =
        K1GPacket.build(K1GPacket.tlv(0x05, 0x23, category.code))
}
