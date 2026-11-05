package com.example.navisarthi.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.example.navisarthi.dash.protocol.CallHistoryCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Queries only requested categories; no call log is persisted or sent to diagnostics. */
class ConsoleCallHistory(private val context: Context) {
    private val prefs = context.getSharedPreferences("console_call_history", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean("enabled", false))
    val enabled = _enabled.asStateFlow()
    private val _status = MutableStateFlow("Call history off")
    val status = _status.asStateFlow()
    private val _favorites = MutableStateFlow(prefs.getBoolean("favorites", false))
    val favoritesEnabled = _favorites.asStateFlow()
    private val _dialing = MutableStateFlow(prefs.getBoolean("dialing", false))
    val dialingEnabled = _dialing.asStateFlow()
    private val _badge = MutableStateFlow(prefs.getBoolean("missed_badge", false))
    val badgeEnabled = _badge.asStateFlow()
    private val _dialStatus = MutableStateFlow(if (_dialing.value) "Select a console contact to call" else "Console dialing off")
    val dialStatus = _dialStatus.asStateFlow()
    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun hasContactsAccess() = granted(Manifest.permission.READ_CONTACTS)
    fun hasDialAccess() = granted(Manifest.permission.CALL_PHONE)
    fun setFavorites(value: Boolean) { prefs.edit().putBoolean("favorites", value).apply(); _favorites.value = value }
    fun setDialing(value: Boolean) {
        prefs.edit().putBoolean("dialing", value).apply(); _dialing.value = value
        _dialStatus.value = if (!value) "Console dialing off" else if (!hasDialAccess()) "Phone permission needed" else "Select a console contact to call"
    }
    fun setBadge(value: Boolean) { prefs.edit().putBoolean("missed_badge", value).apply(); _badge.value = value }
    fun categoryAllowed(category: CallHistoryCategory) = if (category == CallHistoryCategory.FAVORITES)
        _favorites.value && hasContactsAccess() else _enabled.value && hasAccess()
    fun dial(number: String): Boolean {
        if (!_dialing.value || !hasDialAccess()) { _dialStatus.value = "Enable dialing and allow Phone access"; return false }
        val normalized = consoleDialNumber(number) ?: return false
        return try {
            checkNotNull(context.getSystemService(TelecomManager::class.java)).placeCall(Uri.fromParts("tel", normalized, null), Bundle())
            _dialStatus.value = "Call requested"
            true
        } catch (_: SecurityException) { _dialStatus.value = "Phone access denied"; false }
        catch (_: Exception) { _dialStatus.value = "Unable to place call on this device"; false }
    }
    suspend fun missedCount(): Int = withContext(Dispatchers.IO) {
        if (!_badge.value || !hasAccess()) return@withContext 0
        try {
            context.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID),
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = ?", arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "1"), null)
                ?.use { it.count.coerceAtMost(65535) } ?: 0
        } catch (_: Exception) { 0 }
    }
    private fun favoriteRows(): List<ConsoleCallEntry> {
        if (!_favorites.value || !hasContactsAccess()) return emptyList()
        val phone = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val rows = mutableListOf<ConsoleCallEntry>()
        val contacts = mutableSetOf<Long>()
        val numbers = mutableSetOf<String>()
        context.contentResolver.query(phone, arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.STARRED} = ?", arrayOf("1"),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC, ${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} ASC, ${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, ${ContactsContract.CommonDataKinds.Phone._ID} ASC")?.use { cursor ->
            while (rows.size < 5 && cursor.moveToNext()) {
                val number = consoleDialNumber(cursor.getString(2).orEmpty()) ?: continue
                val id = cursor.getLong(0)
                if (id in contacts || number in numbers) continue
                contacts += id; numbers += number
                rows += ConsoleCallEntry(cursor.getString(1)?.takeIf(String::isNotBlank) ?: number, number)
            }
        }
        return rows
    }
    fun hasAccess(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean("enabled", value).apply()
        _enabled.value = value
        refreshAccess()
    }
    fun refreshAccess() {
        if (!_dialing.value) _dialStatus.value = "Console dialing off"
        else if (!hasDialAccess()) _dialStatus.value = "Phone permission needed"
        else if (_dialStatus.value == "Phone permission needed" || _dialStatus.value == "Console dialing off")
            _dialStatus.value = "Select a console contact to call"
        _status.value = when {
            !_enabled.value -> "Call history off"
            !hasAccess() -> "Call log permission needed"
            else -> "Ready · latest five per category"
        }
    }
    suspend fun entries(category: CallHistoryCategory): List<ConsoleCallEntry> = withContext(Dispatchers.IO) {
        refreshAccess()
        if (!categoryAllowed(category)) return@withContext emptyList()
        try {
            if (category == CallHistoryCategory.FAVORITES) return@withContext favoriteRows()
            val uri = CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "5").build()
            val rows = mutableListOf<ConsoleCallEntry>()
            val cursor = context.contentResolver.query(uri,
                arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.NUMBER_PRESENTATION),
                "${CallLog.Calls.TYPE} = ?", arrayOf(category.callType.toString()), "${CallLog.Calls.DATE} DESC")
            if (cursor == null) {
                _status.value = "Call history unavailable"
                return@withContext emptyList()
            }
            cursor.use {
                while (rows.size < 5 && it.moveToNext()) {
                    val number = it.getString(1).orEmpty()
                    val label = if (it.getInt(2) != CallLog.Calls.PRESENTATION_ALLOWED) "Private number"
                        else it.getString(0)?.takeIf(String::isNotBlank) ?: number.ifBlank { "Unknown caller" }
                    rows += ConsoleCallEntry(label, if (it.getInt(2) == CallLog.Calls.PRESENTATION_ALLOWED) consoleDialNumber(number) else null)
                }
            }
            rows
        } catch (_: SecurityException) {
            _status.value = "Call log access denied on this device"
            emptyList()
        } catch (_: Exception) {
            _status.value = "Call history unavailable"
            emptyList()
        }
    }
}
