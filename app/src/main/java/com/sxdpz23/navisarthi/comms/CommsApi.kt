package com.sxdpz23.navisarthi.comms

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class CommsApiException(val status: Int, val code: String) : IOException("Comms request failed")

/** Dedicated client: no shared cache, analytics interceptor, credential logging or cross-origin redirects. */
internal class CommsApi(val origin: String) {
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        // Allow a sleeping service to wake without automatically replaying a write.
        .retryOnConnectionFailure(false).connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS).callTimeout(100, TimeUnit.SECONDS).build()
    suspend fun request(path: String, method: String = "POST", payload: JsonObject = buildJsonObject {}, credential: String? = null,
                        installation: String? = null, retryId: String? = null): JsonObject = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(origin.toHttpUrl().resolve(path)!!).header("Cache-Control", "no-store")
        credential?.let { request.header("Authorization", "Bearer $it") }
        installation?.let { request.header("X-Installation-Secret", it) }
        retryId?.let { request.header("Idempotency-Key", it) }
        request.method(method, if (method in listOf("GET", "DELETE")) null else payload.toString().toRequestBody("application/json".toMediaType()))
        val call = http.newCall(request.build()); continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        val source = it.body.source()
                        source.request(1_048_577)
                        if (source.buffer.size > 1_048_576) throw IOException("Response too large")
                        val data = commsJson.parseToJsonElement(source.readUtf8()).jsonObject
                        if (!it.isSuccessful) throw CommsApiException(it.code, data["error"]?.jsonPrimitive?.content ?: "SERVICE_UNAVAILABLE")
                        continuation.resume(data)
                    }
                } catch (e: Exception) { continuation.resumeWithException(e) }
            }
        })
    }
    fun events(ticket: String, afterSeq: Long, listener: WebSocketListener): WebSocket = http.newWebSocket(
        Request.Builder().url(origin.toHttpUrl().resolve("v1/events")!!).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(buildJsonObject { put("type", "auth"); put("ticket", ticket); put("afterSeq", afterSeq) }.toString())
                listener.onOpen(webSocket, response)
            }
            override fun onMessage(webSocket: WebSocket, text: String) = listener.onMessage(webSocket, text)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = listener.onFailure(webSocket, t, response)
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onClosed(webSocket, code, reason)
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null); listener.onClosing(webSocket, code, reason) }
        },
    )
}
