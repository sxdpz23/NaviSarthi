package com.sxdpz23.navisarthi.comms

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credentials and the active ride's chat are encrypted separately and excluded from backups. */
internal class CommsVault(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "comms-session.enc"))
    private val chatFile = AtomicFile(File(context.cacheDir, "comms-chat.enc"))
    private val alias = "navisarthi.comms.session.v1"
    private val chatContext = "$alias.chat"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(): StoredComms? {
        if (!file.baseFile.exists()) return null
        return try {
            commsJson.decodeFromString<StoredComms>(readEncrypted(file, alias, 16384))
        } catch (_: Exception) { clear(); null }
    }
    @Synchronized fun write(value: StoredComms) {
        writeEncrypted(file, alias, commsJson.encodeToString(value))
    }
    @Synchronized fun readChat(admission: CommsAdmission): CachedCommsChat? {
        if (!chatFile.baseFile.exists()) return null
        val chat = runCatching {
            commsJson.decodeFromString<CachedCommsChat>(readEncrypted(chatFile, chatContext, 8 * 1024 * 1024))
        }.getOrNull()
        if (chat?.groupId == admission.groupId && chat.memberId == admission.memberId) return chat
        chatFile.delete()
        return null
    }
    @Synchronized fun writeChat(chat: CachedCommsChat) {
        writeEncrypted(chatFile, chatContext, commsJson.encodeToString(chat))
    }
    private fun readEncrypted(source: AtomicFile, purpose: String, maxBytes: Int): String {
        val bytes = source.openRead().use { it.readBytes() }
        require(bytes.size in 29..maxBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(purpose.toByteArray())
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString()
    }
    private fun writeEncrypted(target: AtomicFile, purpose: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key())
            updateAAD(purpose.toByteArray())
        }
        val bytes = cipher.iv + cipher.doFinal(value.toByteArray())
        val stream = target.startWrite()
        try { stream.write(bytes); target.finishWrite(stream) } catch (error: Exception) { target.failWrite(stream); throw error }
    }
    @Synchronized fun clear() {
        file.delete()
        chatFile.delete()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) }
    }
    companion object {
        fun newSecret(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    }
}
