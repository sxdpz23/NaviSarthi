package com.sxdpz23.navisarthi.comms

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

class CommsContractTest {
    @Test fun qrRequiresOurExplicitVersionAndType() {
        val token = "aB1".repeat(10) + "Z9"
        assertEquals(token, parseCommsQr("""{"v":1,"type":"navisarthi-ride","inviteToken":"$token"}"""))
        listOf(
            """{"inviteToken":"$token"}""",
            """{"v":2,"type":"navisarthi-ride","inviteToken":"$token"}""",
            """{"v":1,"type":"other-app","inviteToken":"$token"}""",
            """{"v":1,"type":"navisarthi-ride","inviteToken":"short"}""",
            "https://example.com", "x".repeat(513),
        ).forEach { raw -> assertTrue(runCatching { parseCommsQr(raw) }.isFailure) }
    }
    @Test fun credentialsOnlyGoToAnExplicitSecureOrigin() {
        assertEquals("", commsOrigin("", false))
        assertEquals("https://rides.example/", commsOrigin("https://rides.example", false))
        assertEquals("http://127.0.0.1:8080/", commsOrigin("http://127.0.0.1:8080", true))
        for (bad in listOf("http://rides.example", "https://user:secret@rides.example", "https://rides.example/path", "https://rides.example?token=x", "https://rides.example#x")) {
            assertTrue(runCatching { commsOrigin(bad, true) }.isFailure)
        }
        assertTrue(runCatching { commsOrigin("http://127.0.0.1:8080", false) }.isFailure)
    }
    @Test fun messageResumeDeduplicatesSequencesAndBoundsMemory() {
        fun message(seq: Long) = CommsMessage("id-$seq", "rider", "Text", seq, 1000)
        val merged = mergeCommsMessages(listOf(message(3), message(1)), listOf(message(2), message(3)))
        assertEquals(listOf(1L, 2L, 3L), merged.map { it.seq })
        val bounded = mergeCommsMessages(emptyList(), (1L..600L).map(::message))
        assertEquals(500, bounded.size); assertEquals(101L, bounded.first().seq)
    }
    @Test fun distanceIsStraightLineAndHandlesTheDateLine() {
        val a = CommsPosition(0.0, 0.0, 5.0, 0)
        assertEquals(0.0, commsDistanceMeters(a, a), 0.001)
        assertEquals(111195.0, commsDistanceMeters(a, a.copy(lng = 1.0)), 10.0)
        assertTrue(commsDistanceMeters(a.copy(lng = 179.9), a.copy(lng = -179.9)) < 23000)
    }
    @Test fun mapNeverShowsOwnExpiredOrNonmemberPositions() {
        val position = CommsPosition(1.0, 2.0, 5.0, 1000)
        val group = CommsGroup("group", "Ride", 0, 200000, "own", 1, 0, members = listOf(CommsMember("own", "Me", 0), CommsMember("other", "Friend", 0)))
        val state = CommsState(snapshot = CommsSnapshot(group, "own", mapOf("own" to position, "other" to position, "outsider" to position), 1000))
        assertEquals(listOf("other"), commsMapRiders(state, 2000).map { it.id })
        assertTrue(commsMapRiders(state, 32000).single().stale)
        assertTrue(commsMapRiders(state, 121001).isEmpty())
        assertTrue(commsMapRiders(state, 200000).isEmpty())
    }
    @Test fun apiUsesPrivateHeadersAndPreservesAdmissionRetry() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var captured = false
        server.createContext("/v1/groups") { exchange ->
            assertEquals("install-secret", exchange.requestHeaders.getFirst("X-Installation-Secret"))
            assertEquals("retry-id", exchange.requestHeaders.getFirst("Idempotency-Key"))
            assertNull(exchange.requestHeaders.getFirst("Authorization"))
            assertEquals("POST", exchange.requestMethod)
            assertEquals("{\"nickname\":\"Rider\"}", exchange.requestBody.bufferedReader().readText())
            captured = true
            val reply = "{\"ok\":true}".toByteArray()
            exchange.sendResponseHeaders(201, reply.size.toLong()); exchange.responseBody.use { it.write(reply) }
        }
        server.start()
        try {
            val api = CommsApi("http://127.0.0.1:${server.address.port}/")
            assertTrue(api.request("v1/groups", payload = buildJsonObject { put("nickname", "Rider") }, installation = "install-secret", retryId = "retry-id")["ok"]!!.jsonPrimitive.boolean)
            assertTrue(captured)
        } finally { server.stop(0) }
    }
    @Test fun apiDoesNotFollowRedirectsOrExposeProviderErrors() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var redirected = false
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/leak")
            val reply = "{\"error\":\"REDIRECT\"}".toByteArray()
            exchange.sendResponseHeaders(307, reply.size.toLong()); exchange.responseBody.use { it.write(reply) }
        }
        server.createContext("/leak") { exchange -> redirected = true; exchange.sendResponseHeaders(204, -1); exchange.close() }
        server.start()
        try {
            val failure = runCatching { CommsApi("http://127.0.0.1:${server.address.port}/").request("redirect", credential = "private-secret") }.exceptionOrNull()
            assertTrue(failure is CommsApiException)
            assertFalse(redirected)
            assertFalse(failure!!.message.orEmpty().contains("private-secret"))
        } finally { server.stop(0) }
    }
}
