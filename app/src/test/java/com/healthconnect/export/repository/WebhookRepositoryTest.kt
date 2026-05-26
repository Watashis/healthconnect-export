package com.healthconnect.export.repository

import com.healthconnect.export.data.DailyHealthRecord
import com.healthconnect.export.data.ExportMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket


class WebhookRepositoryTest {

    private val repo = WebhookRepository()

    // =============================================
    // Helper: create test records
    // =============================================

    private fun createTestRecord(date: String = "2026-05-24"): DailyHealthRecord =
        DailyHealthRecord(
            date = date,
            metadata = ExportMetadata(
                appVersion = "1.0.0",
                exportTimestamp = "2026-05-24T12:00:00",
                timezone = "UTC"
            )
        )

    private val singleRecord = listOf(createTestRecord())
    private val multipleRecords = listOf(
        createTestRecord("2026-05-24"),
        createTestRecord("2026-05-25")
    )

    // =============================================
    // Helper: start a local HTTP server
    // =============================================

    /**
     * Starts a local HTTP server on a random port and runs a test block.
     * The server reads the full HTTP request (headers + body) and returns a canned response.
     */
    private fun withServer(
        responseCode: Int = 200,
        responseBody: String = "{}",
        testBlock: (url: String) -> Unit
    ) {
        val server = ServerSocket(0)
        server.soTimeout = 5_000
        val port = server.localPort
        val url = "http://127.0.0.1:$port/hook"

        val serverThread = Thread {
            try {
                val client = server.accept()
                client.use { socket ->
                    // Read full request (headers + body) to prevent connection hanging
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val requestLines = mutableListOf<String>()
                    var line = reader.readLine()
                    while (line != null && line.isNotEmpty()) {
                        requestLines.add(line)
                        line = reader.readLine()
                    }

                    // Read body if Content-Length is present
                    val contentLength = requestLines
                        .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                        ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
                    if (contentLength > 0) {
                        val body = CharArray(contentLength)
                        reader.read(body, 0, contentLength)
                        requestLines.add(String(body))
                    }

                    val responseBytes =
                        "HTTP/1.1 $responseCode \r\nContent-Length: ${responseBody.toByteArray().size}\r\nConnection: close\r\n\r\n$responseBody"
                            .toByteArray()
                    socket.getOutputStream().write(responseBytes)
                    socket.getOutputStream().flush()
                }
            } catch (_: Exception) {
                // Server closed — ignore
            }
        }
        serverThread.start()

        try {
            testBlock(url)
        } finally {
            serverThread.join(2_000)
            server.close()
        }
    }

    /**
     * Starts a local HTTP server, captures the full HTTP request received,
     * runs a test block that sends the request, and returns the captured raw request string.
     */
    private fun withServerAndCapture(
        responseCode: Int = 200,
        responseBody: String = "{}",
        block: (url: String) -> Unit
    ): String {
        val server = ServerSocket(0)
        server.soTimeout = 5_000
        val port = server.localPort
        val url = "http://127.0.0.1:$port/hook"

        var capturedRequest: String? = null

        val serverThread = Thread {
            try {
                val client = server.accept()
                client.use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val requestLines = mutableListOf<String>()
                    var line = reader.readLine()
                    while (line != null && line.isNotEmpty()) {
                        requestLines.add(line)
                        line = reader.readLine()
                    }

                    val contentLength = requestLines
                        .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                        ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
                    var bodyString = ""
                    if (contentLength > 0) {
                        val body = CharArray(contentLength)
                        reader.read(body, 0, contentLength)
                        bodyString = String(body)
                    }

                    capturedRequest = requestLines.joinToString("\n") + "\n\n" + bodyString

                    val responseBytes =
                        "HTTP/1.1 $responseCode \r\nContent-Length: ${responseBody.toByteArray().size}\r\nConnection: close\r\n\r\n$responseBody"
                            .toByteArray()
                    socket.getOutputStream().write(responseBytes)
                    socket.getOutputStream().flush()
                }
            } catch (_: Exception) {
                // Server closed — ignore
            }
        }
        serverThread.start()

        try {
            block(url)
        } finally {
            serverThread.join(2_000)
            server.close()
        }
        return capturedRequest ?: ""
    }

    // =============================================
    // sendRecords — Success path
    // =============================================

    @Test
    fun `sendRecords returns Success on 200`() {
        val body = """{"status":"ok","id":"abc123"}"""
        withServer(responseCode = 200, responseBody = body) { url ->
            val result = runBlocking {
                repo.sendRecords(url, singleRecord, null)
            }

            assertTrue(result is WebhookResult.Success)
            assertEquals(200, (result as WebhookResult.Success).statusCode)
            assertEquals(body, result.responseBody)
        }
    }

    @Test
    fun `sendRecords returns Success on 201`() {
        withServer(responseCode = 201, responseBody = "Created") { url ->
            val result = runBlocking {
                repo.sendRecords(url, singleRecord, null)
            }

            assertTrue(result is WebhookResult.Success)
            assertEquals(201, (result as WebhookResult.Success).statusCode)
        }
    }

    @Test
    fun `sendRecords returns Success on 204`() {
        withServer(responseCode = 204, responseBody = "") { url ->
            val result = runBlocking {
                repo.sendRecords(url, singleRecord, null)
            }

            assertTrue(result is WebhookResult.Success)
            assertEquals(204, (result as WebhookResult.Success).statusCode)
            assertEquals("", result.responseBody)
        }
    }

    // =============================================
    // sendRecords — Error path
    // =============================================

    @Test
    fun `sendRecords returns Error on 400`() {
        val errorBody = """{"error":"bad request"}"""
        withServer(responseCode = 400, responseBody = errorBody) { url ->
            val result = runBlocking {
                repo.sendRecords(url, singleRecord, null)
            }

            assertTrue(result is WebhookResult.Error)
            val error = result as WebhookResult.Error
            assertEquals(400, error.statusCode)
            assertTrue(error.message.contains("400"))
            assertTrue(error.message.contains(errorBody))
        }
    }

    @Test
    fun `sendRecords returns Error on 500`() {
        val errorBody = "Internal Server Error"
        withServer(responseCode = 500, responseBody = errorBody) { url ->
            val result = runBlocking {
                repo.sendRecords(url, singleRecord, null)
            }

            assertTrue(result is WebhookResult.Error)
            val error = result as WebhookResult.Error
            assertEquals(500, error.statusCode)
            assertTrue(error.message.contains(errorBody))
        }
    }

    @Test
    fun `sendRecords returns Error on 403`() {
        withServer(responseCode = 403, responseBody = "Forbidden") { url ->
            val result = runBlocking {
                repo.sendRecords(url, singleRecord, null)
            }

            assertTrue(result is WebhookResult.Error)
            assertEquals(403, (result as WebhookResult.Error).statusCode)
        }
    }

    // =============================================
    // sendRecords — Exception handling
    // =============================================

    @Test
    fun `sendRecords returns Error on network exception`() {
        // Connect to a closed port — triggers connection refused
        val result = runBlocking {
            repo.sendRecords("http://127.0.0.1:1/hook", singleRecord, null)
        }

        assertTrue(result is WebhookResult.Error)
        val error = result as WebhookResult.Error
        assertEquals(0, error.statusCode)
        assertNotNull(error.message)
        assertTrue(error.message.isNotBlank())
    }

    @Test
    fun `sendRecords returns Error on invalid URL`() {
        val result = runBlocking {
            repo.sendRecords("http://nonexistent-domain-xyz-12345.com/hook", singleRecord, null)
        }

        assertTrue(result is WebhookResult.Error)
        assertEquals(0, (result as WebhookResult.Error).statusCode)
    }

    // =============================================
    // sendRecords — Bearer token
    // =============================================

    @Test
    fun `sendRecords sets Bearer token when authToken provided`() {
        val request = withServerAndCapture { url ->
            runBlocking {
                repo.sendRecords(url, singleRecord, "my-secret-token")
            }
        }

        assertTrue(request.contains("Authorization: Bearer my-secret-token"))
    }

    @Test
    fun `sendRecords does not set Authorization when authToken is null`() {
        val request = withServerAndCapture { url ->
            runBlocking {
                repo.sendRecords(url, singleRecord, null)
            }
        }

        assertFalse(
            "Authorization header should not be present for null token",
            request.contains("Authorization:", ignoreCase = true)
        )
    }

    @Test
    fun `sendRecords does not set Authorization when authToken is blank`() {
        val request = withServerAndCapture { url ->
            runBlocking {
                repo.sendRecords(url, singleRecord, "")
            }
        }

        assertFalse(
            "Authorization header should not be present for blank token",
            request.contains("Authorization:", ignoreCase = true)
        )
    }

    // =============================================
    // sendRecords — Headers and timeouts
    // =============================================

    @Test
    fun `sendRecords sets Content-Type and Accept headers`() {
        val request = withServerAndCapture { url ->
            runBlocking {
                repo.sendRecords(url, singleRecord, null)
            }
        }

        assertTrue(request.contains("Content-Type: application/json"))
        assertTrue(request.contains("Accept: application/json"))
    }

    @Test
    fun `sendRecords uses POST method`() {
        val request = withServerAndCapture { url ->
            runBlocking {
                repo.sendRecords(url, singleRecord, null)
            }
        }

        assertTrue(
            "Request should start with POST",
            request.startsWith("POST")
        )
    }

    // =============================================
    // sendRecords — Multiple records
    // =============================================

    @Test
    fun `sendRecords with multiple records works correctly`() {
        withServer(responseCode = 200, responseBody = "ok") { url ->
            val result = runBlocking {
                repo.sendRecords(url, multipleRecords, null)
            }

            assertTrue(result is WebhookResult.Success)
            assertEquals(200, (result as WebhookResult.Success).statusCode)
        }
    }

    // =============================================
    // sendRecords — Auth token with special characters
    // =============================================

    @Test
    fun `sendRecords sends Bearer token with special characters`() {
        val complexToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.secret-token-value"
        val request = withServerAndCapture { url ->
            runBlocking {
                repo.sendRecords(url, singleRecord, complexToken)
            }
        }

        assertTrue(request.contains("Authorization: Bearer $complexToken"))
    }

    // =============================================
    // sendRecords — Request body serialization
    // =============================================

    @Test
    fun `sendRecords serializes records as JSON array in body`() {
        val request = withServerAndCapture { url ->
            runBlocking {
                repo.sendRecords(url, singleRecord, null)
            }
        }

        val body = request.substringAfter("\n\n")
        assertTrue("Body should contain record date", body.contains("2026-05-24"))
        assertTrue("Body should be JSON array", body.trimStart().startsWith("["))
        assertTrue("Body should contain metadata", body.contains("metadata"))
    }

    // =============================================
    // isValidWebhookUrl — Edge cases
    // =============================================

    @Test
    fun `empty url is invalid`() {
        assertFalse(repo.isValidWebhookUrl(""))
    }

    @Test
    fun `blank url is invalid`() {
        assertFalse(repo.isValidWebhookUrl("   "))
        assertFalse(repo.isValidWebhookUrl("\t"))
        assertFalse(repo.isValidWebhookUrl("\n"))
    }

    @Test
    fun `valid https url returns true`() {
        assertTrue(repo.isValidWebhookUrl("https://example.com"))
    }

    @Test
    fun `valid http url returns true`() {
        assertTrue(repo.isValidWebhookUrl("http://example.com"))
    }

    @Test
    fun `valid https url with path returns true`() {
        assertTrue(repo.isValidWebhookUrl("https://example.com/api/health-data"))
    }

    @Test
    fun `valid https url with query params returns true`() {
        assertTrue(repo.isValidWebhookUrl("https://hooks.example.com/send?format=json&key=abc"))
    }

    @Test
    fun `valid https url with port returns true`() {
        assertTrue(repo.isValidWebhookUrl("https://example.com:8443/webhook"))
    }

    @Test
    fun `valid ip address url returns true`() {
        assertTrue(repo.isValidWebhookUrl("https://192.168.1.100:8080/webhook"))
    }

    @Test
    fun `url without host is invalid`() {
        assertFalse(repo.isValidWebhookUrl("http://"))
        assertFalse(repo.isValidWebhookUrl("https://"))
    }

    @Test
    fun `ftp protocol is invalid`() {
        assertFalse(repo.isValidWebhookUrl("ftp://files.example.com/data"))
    }

    @Test
    fun `file protocol is invalid`() {
        assertFalse(repo.isValidWebhookUrl("file:///tmp/data.json"))
    }

    @Test
    fun `gibberish string is invalid`() {
        assertFalse(repo.isValidWebhookUrl("not-a-url"))
        assertFalse(repo.isValidWebhookUrl("hello world"))
    }

    @Test
    fun `localhost http url is valid`() {
        assertTrue(repo.isValidWebhookUrl("http://localhost:3000/webhook"))
    }

    @Test
    fun `localhost https with path is valid`() {
        assertTrue(repo.isValidWebhookUrl("https://localhost/api"))
    }

    @Test
    fun `url with percent encoding is valid`() {
        assertTrue(repo.isValidWebhookUrl("https://example.com/hook?token=a%20b%26c"))
    }

    @Test
    fun `url with authentication part is valid`() {
        assertTrue(repo.isValidWebhookUrl("https://user:pass@hooks.example.com/data"))
    }

    @Test
    fun `url with fragment is still valid`() {
        assertTrue(repo.isValidWebhookUrl("https://example.com/hook#callback"))
    }

    @Test
    fun `url with only hostname is valid`() {
        assertTrue(repo.isValidWebhookUrl("https://hooks.example.com"))
    }

    @Test
    fun `wss protocol is invalid`() {
        assertFalse(repo.isValidWebhookUrl("wss://example.com/ws"))
    }

    @Test
    fun `url with underscore in hostname is valid`() {
        assertTrue(repo.isValidWebhookUrl("https://my_webhook.example.com/callback"))
    }

    @Test
    fun `url with subdomain with hyphen is valid`() {
        assertTrue(repo.isValidWebhookUrl("https://my-webhook.example.com/callback"))
    }

    @Test
    fun `empty after trim blank string is invalid`() {
        assertFalse(repo.isValidWebhookUrl("  \n  "))
        assertFalse(repo.isValidWebhookUrl(" \r\n "))
    }

    @Test
    fun `error stream is null falls back to empty string`() {
        // Server returns 500 with Content-Length: 0 — errorStream will be empty
        withServer(responseCode = 500, responseBody = "") { url ->
            val result = runBlocking {
                repo.sendRecords(url, singleRecord, null)
            }

            assertTrue(result is WebhookResult.Error)
            val error = result as WebhookResult.Error
            assertEquals(500, error.statusCode)
            assertTrue(error.message.isNotBlank())
        }
    }
}
