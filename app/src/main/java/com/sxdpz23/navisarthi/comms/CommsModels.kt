package com.sxdpz23.navisarthi.comms

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import kotlin.math.*

internal val commsJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
const val COMMS_DAY_MS = 86_400_000L

@Serializable data class CommsMember(val id: String, val nickname: String, val joinedAt: Long)
@Serializable data class CommsPosition(val lat: Double, val lng: Double, val accuracyM: Double, val receivedAt: Long, val sharingId: String = "")
@Serializable data class CommsGroup(
    val id: String, val name: String, val createdAt: Long, val expiresAt: Long,
    val ownerId: String, val revision: Long, val messageSeq: Long,
    val audioAvailable: Boolean = false, val members: List<CommsMember> = emptyList(),
)
@Serializable data class CommsSnapshot(val group: CommsGroup, val memberId: String, val positions: Map<String, CommsPosition> = emptyMap(), val serverTime: Long)
@Serializable data class CommsMessage(val id: String, val memberId: String, val text: String, val seq: Long, val sentAt: Long)
@Serializable data class CommsAdmission(val groupId: String, val memberId: String, val credential: String, val expiresAt: Long, val inviteToken: String? = null) {
    override fun toString(): String = "CommsAdmission(groupId=$groupId, memberId=$memberId, secrets=redacted)"
}
@Serializable data class CommsPreview(val name: String, val expiresAt: Long, val memberCount: Int, val capacity: Int)
@Serializable data class CommsQr(val v: Int, val type: String, val inviteToken: String)
@Serializable internal data class CachedCommsChat(
    val groupId: String,
    val memberId: String,
    val cursor: Long,
    val messages: List<CommsMessage>,
)
@Serializable internal data class PendingAdmission(val requestId: String, val path: String, val body: String, val expiresAt: Long)
@Serializable internal data class StoredComms(
    val origin: String, val installationSecret: String,
    val admission: CommsAdmission? = null, val pending: PendingAdmission? = null,
) {
    override fun toString(): String = "StoredComms(secrets=redacted)"
}
data class PendingCommsMessage(val id: String, val text: String)
enum class CommsAudioState { OFF, CONNECTING, CONNECTED, RECONNECTING }
data class CommsState(
    val configured: Boolean = false, val hasSession: Boolean = false, val pendingAdmission: Boolean = false,
    val snapshot: CommsSnapshot? = null, val messages: List<CommsMessage> = emptyList(),
    val connected: Boolean = false, val busy: Boolean = false, val error: String? = null,
    val preview: CommsPreview? = null, val invitation: String? = null,
    val pendingMessage: PendingCommsMessage? = null,
    val sharing: Boolean = false, val ownPosition: CommsPosition? = null,
    val audio: CommsAudioState = CommsAudioState.OFF, val muted: Boolean = false, val audioParticipants: Int = 0,
    val serverOffsetMs: Long = 0,
)

/** Strictly recognise our versioned QR; never interpret arbitrary QR text as a URL or command. */
fun parseCommsQr(raw: String): String {
    require(raw.length <= 512) { "Invalid ride QR" }
    val payload = commsJson.decodeFromString<CommsQr>(raw)
    require(payload.v == 1 && payload.type == "navisarthi-ride" && payload.inviteToken.matches(Regex("[A-Za-z0-9]{32}"))) { "Invalid ride QR" }
    return payload.inviteToken
}
fun commsOrigin(raw: String, debug: Boolean): String {
    val value = raw.trim()
    if (value.isEmpty()) return ""
    val uri = URI(value)
    require(uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.orEmpty() in listOf("", "/"))
    require(uri.scheme == "https" || (debug && uri.scheme == "http" && uri.host in listOf("127.0.0.1", "localhost")))
    return value.trimEnd('/') + "/"
}
fun commsDistanceMeters(a: CommsPosition, b: CommsPosition): Double {
    val dLat = Math.toRadians(b.lat - a.lat); val dLng = Math.toRadians(b.lng - a.lng)
    val value = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
    return 6_371_000 * 2 * atan2(sqrt(value.coerceIn(0.0, 1.0)), sqrt((1 - value).coerceIn(0.0, 1.0)))
}
fun mergeCommsMessages(current: List<CommsMessage>, incoming: List<CommsMessage>): List<CommsMessage> =
    (current + incoming).distinctBy { it.seq }.sortedBy { it.seq }.takeLast(500)
