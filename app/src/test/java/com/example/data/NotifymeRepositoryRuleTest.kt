package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.mcp.EmbeddedMcpServer
import com.example.mcp.McpHttpTestClient
import com.example.mcp.McpToolProcessor
import com.example.model.AgentType
import com.example.model.TaskStatus
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class NotifymeRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun profileAndAgentCrud_authenticatedOwner_succeedsAndHashesCredential() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = NotifymeRepository(firestore)

        val profileRes = withTimeout(TIMEOUT_MS) {
            repo.ensureUserProfile(uid, ALICE_EMAIL, "Alice Dev", null)
        }
        assertTrue(profileRes.isSuccess)

        val regRes = withTimeout(TIMEOUT_MS) {
            repo.registerAgent(uid, "Antigravity Test", AgentType.ANTIGRAVITY)
        }
        assertTrue(regRes.isSuccess)
        val (agent, rawToken) = regRes.getOrThrow()
        assertTrue(rawToken.startsWith("ntfy_"))
        assertEquals(64, agent.credentialHash.length)
        assertFalse(agent.credentialHash == rawToken)

        val agents = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeAgents(uid).first { list -> list.any { it.id == agent.id } }
        }
        assertTrue(agents.any { it.id == agent.id && it.name == "Antigravity Test" })
    }

    @Test
    fun crossUserAccess_bobCannotReadAliceData() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = NotifymeRepository(firestore)
        withTimeout(TIMEOUT_MS) {
            repo.ensureUserProfile(aliceUid, ALICE_EMAIL, "Alice", null)
        }
        val (agent, _) = withTimeout(TIMEOUT_MS) {
            repo.registerAgent(aliceUid, "Alice Antigravity", AgentType.ANTIGRAVITY).getOrThrow()
        }

        // Switch to Bob
        signInTestUser(BOB_EMAIL)
        var capturedError: FirebaseFirestoreException? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeAgents(aliceUid).first()
            }
        } catch (e: Exception) {
            capturedError = e.unwrapFirestoreException()
        }
        assertNotNull("Expected PERMISSION_DENIED for cross-user access", capturedError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, capturedError?.code)
        assertTrue(agent.id.isNotBlank())
    }

    @Test
    fun unauthenticatedUser_cannotObserveTasks() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repo = NotifymeRepository(firestore)

        var capturedError: FirebaseFirestoreException? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeTasks(aliceUid).first()
            }
        } catch (e: Exception) {
            capturedError = e.unwrapFirestoreException()
        }
        assertNotNull("Expected PERMISSION_DENIED when signed out", capturedError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, capturedError?.code)
    }

    @Test
    fun mcpServerEndToEnd_authenticatesDeduplicatesAndCreatesNotifications() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = NotifymeRepository(firestore)
        val profile = withTimeout(TIMEOUT_MS) {
            repo.ensureUserProfile(uid, ALICE_EMAIL, "Alice", null).getOrThrow()
        }
        val (agent, rawToken) = withTimeout(TIMEOUT_MS) {
            repo.registerAgent(uid, "Antigravity E2E", AgentType.ANTIGRAVITY).getOrThrow()
        }

        val processor = McpToolProcessor(repo, null)
        processor.updateSessionContext(uid, listOf(agent), profile)

        val port = 18765
        val server = EmbeddedMcpServer(processor)
        server.start(port = port, bindLan = false)
        // Wait briefly for server socket bind
        kotlinx.coroutines.delay(250)
        try {
            val endpoint = "http://127.0.0.1:$port/mcp"
            val taskId = "task_${UUID.randomUUID().toString().take(8)}"

            // 1. Unauthorized token returns 401
            val unauthResp = McpHttpTestClient.sendToolCall(
                endpoint = endpoint,
                bearerToken = "invalid_token",
                toolName = "notify_agent_connected",
                arguments = JSONObject()
            ).getOrThrow()
            assertEquals(401, unauthResp.first)

            // 2. Update progress
            val progResp = McpHttpTestClient.sendToolCall(
                endpoint = endpoint,
                bearerToken = rawToken,
                toolName = "update_task_progress",
                arguments = JSONObject().apply {
                    put("task_id", taskId)
                    put("title", "Build Auth Pipeline")
                    put("project", "notifyme-api")
                    put("progress", 50)
                    put("message", "Implementing JWT verification")
                }
            ).getOrThrow()
            assertEquals(200, progResp.first)

            // 3. Request user input -> respond -> get status
            val inputResp = McpHttpTestClient.sendToolCall(
                endpoint = endpoint,
                bearerToken = rawToken,
                toolName = "request_user_input",
                arguments = JSONObject().apply {
                    put("task_id", taskId)
                    put("question", "Use OAuth2 PKCE or device flow?")
                    put("options", JSONArray(listOf("OAuth2 PKCE", "Device Flow")))
                }
            ).getOrThrow()
            assertEquals(200, inputResp.first)

            withTimeout(TIMEOUT_MS) {
                repo.respondToUserInput(uid, taskId, "OAuth2 PKCE").getOrThrow()
            }

            // 4. Complete task with idempotency key
            val idemKey = "idem_${UUID.randomUUID().toString().take(8)}"
            val completeArgs = JSONObject().apply {
                put("task_id", taskId)
                put("agent_id", agent.id)
                put("title", "Build Auth Pipeline")
                put("project", "notifyme-api")
                put("summary", "Auth pipeline verified and passing tests.")
                put("idempotency_key", idemKey)
            }
            val comp1 = McpHttpTestClient.sendToolCall(
                endpoint = endpoint,
                bearerToken = rawToken,
                toolName = "notify_task_completed",
                arguments = completeArgs
            ).getOrThrow()
            assertEquals(200, comp1.first)

            // 5. Retry same idempotency key -> deduplicated
            val comp2 = McpHttpTestClient.sendToolCall(
                endpoint = endpoint,
                bearerToken = rawToken,
                toolName = "notify_task_completed",
                arguments = completeArgs
            ).getOrThrow()
            assertEquals(200, comp2.first)
            assertTrue(comp2.second.contains("\"deduplicated\":true"))

            val tasks = withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeTasks(uid).first { list ->
                    list.any { it.id == taskId && it.taskStatusEnum == TaskStatus.COMPLETED }
                }
            }
            assertTrue(tasks.any { it.id == taskId && it.progressInt == 100 })

            val notifications = withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeNotifications(uid).first { list ->
                    list.any { it.taskId == taskId }
                }
            }
            val completionNotifs = notifications.filter { it.taskId == taskId && it.category == "task_completed" }
            assertEquals(1, completionNotifs.size)
        } finally {
            server.stop()
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.notifyme@test.com"
        const val BOB_EMAIL = "bob.notifyme@test.com"
        const val TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 6000L
    }
}
