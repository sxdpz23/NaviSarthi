package com.sxdpz23.navisarthi.comms

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.sxdpz23.navisarthi.BuildConfig
import com.sxdpz23.navisarthi.NaviSarthiApplication
import com.sxdpz23.navisarthi.navigation.core.location.SIM_PROVIDER
import com.sxdpz23.navisarthi.navigation.core.location.REPLAY_PROVIDER
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID

/** Process-scoped ride session backed by an encrypted cache for the active ride's chat. */
class CommsRepository private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val origin = runCatching { commsOrigin(BuildConfig.COMMS_API_URL, BuildConfig.DEBUG) }.getOrDefault("")
    private val api = CommsApi(origin)
    private val vault = CommsVault(context)
    private var stored = vault.read()?.takeIf { it.origin == origin }
    private val mutable = MutableStateFlow(CommsState(configured = origin.isNotBlank()))
    val state: StateFlow<CommsState> = mutable.asStateFlow()
    private var generation = 0L
    private var connectionEpoch = 0L
    private var connection: Job? = null
    private var socket: WebSocket? = null
    private var locationJob: Job? = null
    private var audioJob: Job? = null
    private var roomEvents: Job? = null
    private var room: Room? = null
    private val microphoneMutex = Mutex()
    private var deadline = Long.MAX_VALUE
    private var cursor = 0L
    private var serverAnchorMs = 0L
    private var serverAnchorElapsed = 0L

    init {
        if (stored == null) vault.clear()
        stored?.let { saved ->
            val expires = saved.admission?.expiresAt ?: saved.pending?.expiresAt ?: 0L
            if (expires <= System.currentTimeMillis()) clear("Your ride has ended. Comms data has been cleared.")
            else {
                deadline = SystemClock.elapsedRealtime() + (expires - System.currentTimeMillis()).coerceAtMost(14 * COMMS_DAY_MS)
                mutable.update { it.copy(hasSession = saved.admission != null, pendingAdmission = saved.pending != null, invitation = saved.admission?.inviteToken) }
                saved.admission?.let { admission ->
                    vault.readChat(admission)?.let { chat ->
                        cursor = chat.cursor
                        mutable.update { it.copy(messages = chat.messages) }
                    }
                    connect()
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(1000)
                if (stored != null && SystemClock.elapsedRealtime() >= deadline) clear("Your ride has ended. Comms data has been cleared.")
                else mutable.update { current ->
                    val snapshot = current.snapshot ?: return@update current
                    val now = if (serverAnchorMs != 0L) serverAnchorMs + SystemClock.elapsedRealtime() - serverAnchorElapsed else System.currentTimeMillis() + current.serverOffsetMs
                    val fresh = snapshot.positions.filterValues { now - it.receivedAt < 120_000 }
                    current.copy(snapshot = if (fresh == snapshot.positions) snapshot else snapshot.copy(positions = fresh), serverOffsetMs = now - System.currentTimeMillis())
                }
            }
        }
    }
    fun dismissError() { mutable.update { it.copy(error = null) } }
    fun reportError(message: String) { mutable.update { it.copy(error = message) } }
    fun dismissPreview() { mutable.update { it.copy(preview = null) }; scannedInvitation = null }
    private var scannedInvitation: String? = null

    private fun action(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e) }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    private fun failure(error: Exception) {
        if (error is CommsApiException && error.status in listOf(401, 410) && stored?.admission != null) {
            clear("This ride is no longer available. Comms data has been cleared."); return
        }
        val text = when ((error as? CommsApiException)?.code) {
            "GROUP_FULL" -> "This ride already has 12 riders."
            "INVITE_UNAVAILABLE" -> "This invitation is no longer available. Scan the latest ride QR."
            "SESSION_ENDED" -> "This ride has ended."
            "REMOVED_FROM_GROUP" -> "You can no longer join this ride."
            "ALREADY_IN_GROUP" -> "This phone already belongs to a ride. Retry the original connection."
            "OWNER_REQUIRED" -> "Only the ride organiser can do that."
            "INVALID_EXTENSION" -> "The end time can only be extended, up to 14 days from creation."
            "TRANSFER_OR_END_REQUIRED" -> "Choose another organiser before leaving, or end the ride."
            "RATE_LIMITED", "MESSAGE_RATE_LIMIT" -> "Please wait a minute before trying again."
            "AUDIO_NOT_CONFIGURED" -> "Audio is not available for this ride yet."
            "SERVICE_CAPACITY_EXCEEDED" -> "Comms server capacity is exhausted. Chat and locations will retry automatically. Existing ride audio can continue."
            "LOCATION_SHARING_STOPPED" -> "Location sharing has stopped. Enable it again to share."
            "MESSAGE_LIMIT" -> "This ride has reached its chat limit."
            "ADMISSION_LIMIT" -> "This ride cannot accept more new memberships."
            else -> "Could not connect to Comms. Check your connection and retry."
        }
        mutable.update { it.copy(error = text) }
    }
    private suspend fun call(path: String, method: String = "POST", payload: JsonObject = buildJsonObject {}): JsonObject {
        val credential = stored?.admission?.credential ?: throw CancellationException("No session")
        val epoch = generation
        val result = api.request(path, method, payload, credential)
        if (epoch != generation || stored?.admission?.credential != credential) throw CancellationException("Session changed")
        return result
    }

    fun create(nickname: String, name: String, days: Int) = action {
        require(days in 1..14 && nickname.trim().length in 1..40 && name.trim().length in 1..80)
        check(stored == null && origin.isNotEmpty())
        val expires = System.currentTimeMillis() + days * COMMS_DAY_MS
        prepareAdmission("v1/groups", buildJsonObject { put("nickname", nickname.trim()); put("name", name.trim()); put("expiresAt", expires) }, expires)
        admit()
    }
    fun scan(raw: String) = action {
        val token = try { parseCommsQr(raw) } catch (_: Exception) { reportError("This is not a NaviSarthi ride QR."); return@action }
        check(stored == null)
        val preview = commsJson.decodeFromJsonElement<CommsPreview>(api.request("v1/invitations/resolve", payload = buildJsonObject { put("inviteToken", token) }))
        scannedInvitation = token
        mutable.update { it.copy(preview = preview) }
    }
    fun join(nickname: String) = action {
        require(nickname.trim().length in 1..40)
        val preview = state.value.preview ?: return@action
        val invitation = scannedInvitation ?: return@action
        check(stored == null)
        prepareAdmission("v1/groups/join", buildJsonObject { put("nickname", nickname.trim()); put("inviteToken", invitation) }, preview.expiresAt)
        admit()
    }
    private fun prepareAdmission(path: String, payload: JsonObject, expiresAt: Long) {
        val record = StoredComms(origin, CommsVault.newSecret(), pending = PendingAdmission(UUID.randomUUID().toString(), path, payload.toString(), expiresAt))
        vault.write(record); stored = record
        deadline = SystemClock.elapsedRealtime() + (expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
        mutable.update { it.copy(pendingAdmission = true, preview = null) }
    }
    private suspend fun admit() {
        val saved = stored ?: return; val pending = saved.pending ?: return
        val epoch = generation
        val response = try {
            api.request(pending.path, payload = commsJson.parseToJsonElement(pending.body).jsonObject, installation = saved.installationSecret, retryId = pending.requestId)
        } catch (e: CommsApiException) {
            // Definitive rejection means no membership was allocated. Network/5xx responses retain the exact retry receipt.
            if (e.status in listOf(400, 403, 404, 410) || e.code in listOf("GROUP_FULL", "ADMISSION_LIMIT")) clear()
            throw e
        }
        if (epoch != generation) throw CancellationException("Session changed")
        val admission = commsJson.decodeFromJsonElement<CommsAdmission>(response)
        val record = saved.copy(admission = admission, pending = null)
        vault.write(record); stored = record; scannedInvitation = null
        mutable.update { it.copy(hasSession = true, pendingAdmission = false, invitation = admission.inviteToken, preview = null) }
        connect()
    }
    fun retryAdmission() = action { admit() }
    fun retryConnection() { connect() }
    fun onAppForeground() {
        if (stored == null) return
        if (SystemClock.elapsedRealtime() >= deadline) {
            clear("Your ride has ended. Comms data has been cleared.")
        } else if (stored?.admission != null) {
            mutable.update { it.copy(connected = false) }
            connect()
        }
    }

    private fun applySnapshot(snapshot: CommsSnapshot) {
        val saved = stored ?: return; val member = saved.admission ?: return
        check(snapshot.group.id == member.groupId && snapshot.memberId == member.memberId)
        val remaining = snapshot.group.expiresAt - snapshot.serverTime
        if (remaining <= 0) { clear("Your ride has ended. Comms data has been cleared."); return }
        deadline = SystemClock.elapsedRealtime() + remaining.coerceAtMost(14 * COMMS_DAY_MS)
        serverAnchorMs = snapshot.serverTime; serverAnchorElapsed = SystemClock.elapsedRealtime()
        if (member.expiresAt != snapshot.group.expiresAt) {
            val next = saved.copy(admission = member.copy(expiresAt = snapshot.group.expiresAt))
            vault.write(next); stored = next
        }
        mutable.update { it.copy(snapshot = snapshot, serverOffsetMs = snapshot.serverTime - System.currentTimeMillis(), connected = true, error = null) }
    }
    private fun connect() {
        connection?.cancel(); socket?.cancel()
        if (stored?.admission == null || origin.isEmpty()) return
        val epoch = ++connectionEpoch
        val sessionGeneration = generation
        connection = scope.launch {
            var backoff = 1000L
            while (isActive && epoch == connectionEpoch && sessionGeneration == generation && stored?.admission != null) {
                var currentSocket: WebSocket? = null
                try {
                    val snapshot = commsJson.decodeFromJsonElement<CommsSnapshot>(call("v1/session", "GET"))
                    applySnapshot(snapshot)
                    if (epoch != connectionEpoch || sessionGeneration != generation) break
                    val ticket = call("v1/realtime-ticket")["ticket"]!!.jsonPrimitive.content
                    val done = CompletableDeferred<Unit>()
                    var lastEvent = SystemClock.elapsedRealtime()
                    currentSocket = api.events(ticket, cursor, object : WebSocketListener() {
                        override fun onMessage(webSocket: WebSocket, text: String) {
                            scope.launch {
                                if (epoch != connectionEpoch || sessionGeneration != generation) return@launch
                                try {
                                    require(text.length <= 1_048_576)
                                    val event = commsJson.parseToJsonElement(text).jsonObject
                                    lastEvent = SystemClock.elapsedRealtime()
                                    when (event["type"]?.jsonPrimitive?.content) {
                                        "snapshot" -> applySnapshot(commsJson.decodeFromJsonElement<CommsSnapshot>(event))
                                        "messages" -> {
                                            val messages = commsJson.decodeFromJsonElement<List<CommsMessage>>(event["messages"]!!)
                                            appendMessages(messages)
                                        }
                                    }
                                } catch (_: Exception) { done.complete(Unit) }
                            }
                        }
                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { done.complete(Unit) }
                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { done.complete(Unit) }
                    })
                    socket = currentSocket
                    backoff = 1000
                    while (isActive && !done.isCompleted && SystemClock.elapsedRealtime() - lastEvent < 45_000) delay(1000)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    failure(e)
                    if (e is CommsApiException && (e.status == 429 || e.code == "SERVICE_CAPACITY_EXCEEDED")) backoff = 60_000
                }
                finally { currentSocket?.cancel() }
                if (epoch != connectionEpoch || sessionGeneration != generation) break
                mutable.update { it.copy(connected = false) }
                delay(backoff); backoff = (backoff * 2).coerceAtMost(if (backoff >= 60_000) 120_000 else 15_000)
            }
        }
    }
    private fun appendMessages(messages: List<CommsMessage>) {
        if (messages.isEmpty()) return
        // Advance only from the realtime stream's ordered catch-up, not from a send response.
        val current = state.value.messages
        val merged = mergeCommsMessages(current, messages)
        val nextCursor = maxOf(cursor, messages.maxOf { it.seq })
        if (current == merged && cursor == nextCursor) return
        cursor = nextCursor
        mutable.update { it.copy(messages = merged) }
        cacheChat(merged)
    }
    private fun cacheChat(messages: List<CommsMessage>) {
        val admission = stored?.admission ?: return
        // A failed cache write must not interrupt the live chat.
        runCatching { vault.writeChat(CachedCommsChat(admission.groupId, admission.memberId, cursor, messages)) }
    }
    fun send(text: String) = action {
        val trimmed = text.trim(); require(trimmed.length in 1..2000)
        check(state.value.pendingMessage == null)
        mutable.update { it.copy(pendingMessage = PendingCommsMessage(UUID.randomUUID().toString(), trimmed)) }
        sendPending()
    }
    fun retryMessage() = action { sendPending() }
    private suspend fun sendPending() {
        val pending = state.value.pendingMessage ?: return
        val message = commsJson.decodeFromJsonElement<CommsMessage>(call("v1/messages", payload = buildJsonObject { put("id", pending.id); put("text", pending.text) }))
        val current = state.value.messages
        val merged = mergeCommsMessages(current, listOf(message))
        mutable.update { it.copy(messages = merged, pendingMessage = null) }
        if (merged != current) cacheChat(merged)
    }
    fun extend(daysFromCreation: Int) = action {
        val group = state.value.snapshot?.group ?: return@action
        require(daysFromCreation in 1..14)
        call("v1/session/extend", payload = buildJsonObject { put("expiresAt", group.createdAt + daysFromCreation * COMMS_DAY_MS) })
        applySnapshot(commsJson.decodeFromJsonElement(call("v1/session", "GET")))
    }
    fun rotateInvitation() = action {
        val token = call("v1/session/invitation")["inviteToken"]!!.jsonPrimitive.content
        stored?.let { saved ->
            val next = saved.copy(admission = saved.admission?.copy(inviteToken = token))
            vault.write(next); stored = next
        }
        mutable.update { it.copy(invitation = token) }
    }
    fun transfer(memberId: String) = action {
        call("v1/session/transfer", payload = buildJsonObject { put("memberId", memberId) })
        // The previous organiser must not keep displaying an invitation after transferring ownership.
        stored?.let { saved -> val next = saved.copy(admission = saved.admission?.copy(inviteToken = null)); vault.write(next); stored = next }
        mutable.update { it.copy(invitation = null) }
        applySnapshot(commsJson.decodeFromJsonElement(call("v1/session", "GET")))
    }
    fun remove(memberId: String) = action { call("v1/session/remove", payload = buildJsonObject { put("memberId", memberId) }) }
    fun leave(endGroup: Boolean) = action {
        stopLocalMedia()
        call(if (endGroup) "v1/session/end" else "v1/session/leave")
        clear(if (endGroup) "Ride ended. Comms data has been cleared." else "You left the ride. Local Comms data has been cleared.")
    }

    internal fun startSharing() {
        if (state.value.sharing || stored?.admission == null) return
        mutable.update { it.copy(sharing = true) }
        locationJob = scope.launch {
            try {
                val sharingId = call("v1/location/start")["sharingId"]!!.jsonPrimitive.content
                var lastSent = 0L
                (context as NaviSarthiApplication).locationProvider.updates(5000).collect { fix ->
                    if (fix.provider in listOf(SIM_PROVIDER, REPLAY_PROVIDER)) return@collect
                    if (!fix.hasAccuracy() || fix.accuracy > 10000 || SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos > 30_000_000_000L) return@collect
                    val now = SystemClock.elapsedRealtime()
                    if (lastSent != 0L && now - lastSent < if (fix.hasSpeed() && fix.speed > 1f) 5000 else 30_000) return@collect
                    val position = CommsPosition(fix.latitude, fix.longitude, fix.accuracy.toDouble(), System.currentTimeMillis() + state.value.serverOffsetMs, sharingId)
                    mutable.update { it.copy(ownPosition = position) }
                    try {
                        call("v1/location", "PUT", buildJsonObject { put("lat", position.lat); put("lng", position.lng); put("accuracyM", position.accuracyM); put("sharingId", sharingId) })
                        lastSent = now
                    } catch (e: CancellationException) { throw e }
                    catch (e: CommsApiException) { if (e.status in listOf(401, 410) || e.code == "LOCATION_SHARING_STOPPED") throw e }
                    catch (_: Exception) { /* The next fresh GPS fix retries; never queue an old position. */ }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e); mutable.update { it.copy(sharing = false, ownPosition = null) } }
        }
    }
    fun stopSharing() {
        locationJob?.cancel(); locationJob = null
        mutable.update { it.copy(sharing = false, ownPosition = null) }
        scope.launch {
            try { call("v1/location", "DELETE") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e); reportError("Sharing stopped on this phone. The last shared position may remain visible for up to two minutes while offline.") }
        }
    }
    internal fun joinAudio() {
        if (state.value.audio != CommsAudioState.OFF || stored?.admission == null) return
        mutable.update { it.copy(audio = CommsAudioState.CONNECTING, muted = false) }
        audioJob = scope.launch {
            try {
                val token = call("v1/audio/token")
                val activeRoom = LiveKit.create(context)
                room = activeRoom
                roomEvents = scope.launch {
                    activeRoom.events.collect { event ->
                        if (room !== activeRoom) return@collect
                        when (event) {
                            is RoomEvent.Disconnected -> { leaveAudio(); reportError("You left the audio room. Tap Join audio to reconnect.") }
                            is RoomEvent.Reconnecting -> mutable.update { it.copy(audio = CommsAudioState.RECONNECTING) }
                            is RoomEvent.Reconnected -> mutable.update { it.copy(audio = CommsAudioState.CONNECTED) }
                            else -> Unit
                        }
                        if (room === activeRoom) mutable.update { it.copy(audioParticipants = activeRoom.remoteParticipants.size + 1) }
                    }
                }
                activeRoom.connect(token["url"]!!.jsonPrimitive.content, token["token"]!!.jsonPrimitive.content)
                check(activeRoom.localParticipant.setMicrophoneEnabled(true))
                mutable.update { it.copy(audio = CommsAudioState.CONNECTED, audioParticipants = activeRoom.remoteParticipants.size + 1) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { leaveAudio(); failure(e) }
        }
    }
    fun toggleMute() {
        val activeRoom = room ?: return
        scope.launch {
            try {
                microphoneMutex.withLock {
                    if (room !== activeRoom) return@withLock
                    val muted = !state.value.muted
                    check(activeRoom.localParticipant.setMicrophoneEnabled(!muted))
                    if (room === activeRoom) mutable.update { it.copy(muted = muted) }
                }
            } catch (_: Exception) { if (room === activeRoom) reportError("Could not change the microphone. Leave audio and try again.") }
        }
    }
    fun leaveAudio() {
        audioJob?.cancel(); audioJob = null; roomEvents?.cancel(); roomEvents = null
        val old = room; room = null
        old?.disconnect(); old?.release()
        mutable.update { it.copy(audio = CommsAudioState.OFF, muted = false, audioParticipants = 0) }
    }
    internal fun stopLocalMedia() {
        locationJob?.cancel(); locationJob = null; leaveAudio()
        mutable.update { it.copy(sharing = false, ownPosition = null) }
    }
    private fun clear(message: String? = null) {
        java.io.File(context.cacheDir, "comms-invitations").listFiles()?.forEach { it.delete() }
        generation++; connection?.cancel(); socket?.cancel(); connection = null; socket = null
        stopLocalMedia(); context.stopService(Intent(context, CommsService::class.java))
        stored = null; vault.clear(); cursor = 0; deadline = Long.MAX_VALUE; scannedInvitation = null; serverAnchorMs = 0L
        mutable.value = CommsState(configured = origin.isNotEmpty(), error = message)
    }
    companion object {
        @Volatile private var instance: CommsRepository? = null
        fun get(context: Context): CommsRepository = instance ?: synchronized(this) {
            instance ?: CommsRepository(context.applicationContext).also { instance = it }
        }
    }
}
