package com.example.mcp

import android.content.Context
import android.util.Log
import com.example.data.NotifymeRepository
import com.example.model.Agent
import com.example.model.AgentStatus
import com.example.model.AgentType
import com.example.model.EventType
import com.example.model.NotificationCategory
import com.example.model.TaskStatus
import com.example.model.UserProfile
import com.example.notifications.SystemNotificationDispatcher
import com.example.security.CredentialCrypto
import com.example.security.SlidingWindowRateLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit

data class McpServerStatus(
    val isRunning: Boolean = false,
    val port: Int = 8765,
    val bindHost: String = "127.0.0.1",
    val displayEndpoint: String = "http://127.0.0.1:8765/mcp",
    val requestsHandled: Long = 0L,
    val lastSuccessfulConnectionAtMillis: Long? = null,
    val lastConnectedAgentName: String? = null,
    val lastError: String? = null
)

data class McpToolDispatchOutcome(
    val httpStatus: Int,
    val responseJson: String,
    val notificationCreated: Boolean = false
)

class McpToolProcessor(
    private val repository: NotifymeRepository,
    private val appContext: Context? = null
) {
    private val rateLimiter = SlidingWindowRateLimiter(maxRequestsPerWindow = 60, windowMillis = 60_000L)

    @Volatile
    private var currentUserId: String? = null

    @Volatile
    private var currentAgents: List<Agent> = emptyList()

    @Volatile
    private var currentProfile: UserProfile? = null

    fun updateSessionContext(userId: String?, agents: List<Agent>, profile: UserProfile?) {
        currentUserId = userId
        currentAgents = agents
        currentProfile = profile
    }

    suspend fun handleJsonRpc(
        bearerToken: String?,
        rawBody: String
    ): McpToolDispatchOutcome {
        val uid = currentUserId ?: return errorJsonRpc(null, 401, -32001, "No active authenticated Notifyme user session.")
        if (rawBody.toByteArray(Charsets.UTF_8).size > 65_536) {
            return errorJsonRpc(null, 413, -32600, "Request payload exceeds 64 KB limit.")
        }

        val requestJson = try {
            JSONObject(rawBody)
        } catch (e: Exception) {
            return errorJsonRpc(null, 400, -32700, "Invalid JSON-RPC payload: ${e.message}")
        }

        val rpcId = requestJson.opt("id")?.takeIf { it != JSONObject.NULL }
        val method = requestJson.optString("method", "")
        if (method.isBlank()) {
            return errorJsonRpc(rpcId, 400, -32600, "Missing JSON-RPC 'method'.")
        }

        // Authenticate Bearer token via SHA-256 hash against user's active agents
        val token = bearerToken?.removePrefix("Bearer ")?.trim().orEmpty()
        if (token.isBlank()) {
            return errorJsonRpc(rpcId, 401, -32001, "Missing Authorization: Bearer <agent_token> header.")
        }
        val tokenHash = CredentialCrypto.sha256Hex(token)
        val matchedAgent = currentAgents.firstOrNull {
            CredentialCrypto.constantTimeEquals(it.credentialHash, tokenHash)
        } ?: return errorJsonRpc(rpcId, 401, -32001, "Invalid MCP agent credential.")

        if (matchedAgent.agentStatusEnum == AgentStatus.REVOKED) {
            return errorJsonRpc(rpcId, 403, -32002, "Agent '${matchedAgent.name}' credential has been revoked.")
        }

        if (!rateLimiter.allow(matchedAgent.id)) {
            return errorJsonRpc(rpcId, 429, -32003, "Rate limit exceeded (60 req/min) for agent '${matchedAgent.name}'.")
        }

        return when (method) {
            "initialize" -> {
                val result = JSONObject().apply {
                    put("protocolVersion", "2025-03-26")
                    put("capabilities", JSONObject().apply {
                        put("tools", JSONObject().apply { put("listChanged", false) })
                    })
                    put("serverInfo", JSONObject().apply {
                        put("name", "notifyme-mcp-server")
                        put("version", "1.0.0")
                    })
                }
                successJsonRpc(rpcId, result)
            }
            "notifications/initialized", "ping" -> {
                successJsonRpc(rpcId, JSONObject())
            }
            "tools/list" -> {
                successJsonRpc(rpcId, JSONObject().put("tools", buildToolSchemas()))
            }
            "tools/call" -> {
                val params = requestJson.optJSONObject("params")
                    ?: return errorJsonRpc(rpcId, 400, -32602, "Missing params object for tools/call.")
                val toolName = params.optString("name", "")
                val args = params.optJSONObject("arguments") ?: JSONObject()
                executeToolCall(rpcId, uid, matchedAgent, toolName, args)
            }
            else -> errorJsonRpc(rpcId, 404, -32601, "Method '$method' not supported.")
        }
    }

    private suspend fun executeToolCall(
        rpcId: Any?,
        userId: String,
        authenticatedAgent: Agent,
        toolName: String,
        args: JSONObject
    ): McpToolDispatchOutcome {
        val suppliedAgentId = args.optString("agent_id", "").trim()
        if (suppliedAgentId.isNotBlank() && suppliedAgentId != authenticatedAgent.id) {
            return errorJsonRpc(
                rpcId,
                403,
                -32002,
                "agent_id '$suppliedAgentId' does not match authenticated credential for '${authenticatedAgent.id}'."
            )
        }

        val idempotencyKey = args.optString("idempotency_key", "")
            .ifBlank { args.optString("event_id", "") }
            .trim()
        if (idempotencyKey.isNotBlank() && repository.hasIdempotencyKey(userId, idempotencyKey)) {
            return successToolText(
                rpcId,
                JSONObject().apply {
                    put("ok", true)
                    put("deduplicated", true)
                    put("idempotency_key", idempotencyKey)
                    put("message", "Duplicate event ignored; notification already dispatched.")
                },
                notificationCreated = false
            )
        }
        val resolvedIdempotency = idempotencyKey.ifBlank {
            "${toolName}_${authenticatedAgent.id}_${UUID.randomUUID().toString().take(12)}"
        }

        return when (toolName) {
            "notify_task_completed" -> {
                val taskId = args.optString("task_id", "").trim()
                val title = args.optString("title", "").trim()
                val project = args.optString("project", "").trim()
                val summary = args.optString("summary", "").trim()
                if (taskId.isBlank() || title.isBlank() || project.isBlank() || summary.isBlank()) {
                    return errorJsonRpc(rpcId, 400, -32602, "notify_task_completed requires task_id, title, project, and summary.")
                }
                val taskResult = repository.upsertTaskFromMcp(
                    userId = userId,
                    agent = authenticatedAgent,
                    taskId = taskId,
                    title = title,
                    project = project,
                    status = TaskStatus.COMPLETED,
                    progress = 100,
                    summary = summary
                )
                if (taskResult.isFailure) {
                    return errorJsonRpc(rpcId, 409, -32004, taskResult.exceptionOrNull()?.message ?: "Failed to complete task")
                }
                val task = taskResult.getOrThrow()
                val shouldNotify = currentProfile?.shouldNotify(NotificationCategory.TASK_COMPLETED) ?: true
                val evtId = repository.appendEvent(
                    userId = userId,
                    agentId = authenticatedAgent.id,
                    agentName = authenticatedAgent.name,
                    taskId = task.id,
                    project = task.project,
                    eventType = EventType.TASK_COMPLETED,
                    title = "${authenticatedAgent.name} completed \"${task.title}\"",
                    message = summary,
                    idempotencyKey = resolvedIdempotency,
                    deliveryStatus = if (shouldNotify) "delivered" else "suppressed"
                ).getOrNull()

                var notifCreated = false
                if (shouldNotify) {
                    val notif = repository.createNotification(
                        userId = userId,
                        taskId = task.id,
                        eventId = evtId,
                        agentName = authenticatedAgent.name,
                        project = task.project,
                        title = "${authenticatedAgent.name} completed \"${task.title}\"",
                        message = summary,
                        category = NotificationCategory.TASK_COMPLETED
                    ).getOrNull()
                    if (notif != null) {
                        notifCreated = true
                        maybeDispatchOsNotification(notif)
                    }
                }
                successToolText(
                    rpcId,
                    JSONObject().apply {
                        put("ok", true)
                        put("task_id", task.id)
                        put("status", task.status)
                        put("notification_dispatched", notifCreated)
                    },
                    notificationCreated = notifCreated
                )
            }

            "notify_task_failed" -> {
                val taskId = args.optString("task_id", "").trim()
                val title = args.optString("title", "").trim()
                val error = args.optString("error", "").trim()
                val project = args.optString("project", "").trim()
                if (taskId.isBlank() || title.isBlank() || error.isBlank() || project.isBlank()) {
                    return errorJsonRpc(rpcId, 400, -32602, "notify_task_failed requires task_id, title, error, and project.")
                }
                val taskResult = repository.upsertTaskFromMcp(
                    userId = userId,
                    agent = authenticatedAgent,
                    taskId = taskId,
                    title = title,
                    project = project,
                    status = TaskStatus.FAILED,
                    progress = 0,
                    error = error
                )
                if (taskResult.isFailure) {
                    return errorJsonRpc(rpcId, 409, -32004, taskResult.exceptionOrNull()?.message ?: "Failed to record task failure")
                }
                val task = taskResult.getOrThrow()
                val shouldNotify = currentProfile?.shouldNotify(NotificationCategory.TASK_FAILED) ?: true
                val evtId = repository.appendEvent(
                    userId = userId,
                    agentId = authenticatedAgent.id,
                    agentName = authenticatedAgent.name,
                    taskId = task.id,
                    project = task.project,
                    eventType = EventType.TASK_FAILED,
                    title = "${authenticatedAgent.name} failed \"${task.title}\"",
                    message = error,
                    idempotencyKey = resolvedIdempotency,
                    deliveryStatus = if (shouldNotify) "delivered" else "suppressed"
                ).getOrNull()

                var notifCreated = false
                if (shouldNotify) {
                    val notif = repository.createNotification(
                        userId = userId,
                        taskId = task.id,
                        eventId = evtId,
                        agentName = authenticatedAgent.name,
                        project = task.project,
                        title = "${authenticatedAgent.name} failed \"${task.title}\"",
                        message = error,
                        category = NotificationCategory.TASK_FAILED
                    ).getOrNull()
                    if (notif != null) {
                        notifCreated = true
                        maybeDispatchOsNotification(notif)
                    }
                }
                successToolText(
                    rpcId,
                    JSONObject().apply {
                        put("ok", true)
                        put("task_id", task.id)
                        put("status", task.status)
                        put("notification_dispatched", notifCreated)
                    },
                    notificationCreated = notifCreated
                )
            }

            "update_task_progress" -> {
                val taskId = args.optString("task_id", "").trim()
                if (!args.has("progress")) {
                    return errorJsonRpc(rpcId, 400, -32602, "update_task_progress requires 'progress' integer between 0 and 100.")
                }
                val progress = args.optInt("progress", -1)
                if (taskId.isBlank() || progress !in 0..100) {
                    return errorJsonRpc(rpcId, 400, -32602, "Progress must be between 0 and 100 and task_id must be non-blank.")
                }
                val message = args.optString("message", "Progress updated to $progress%").trim().ifBlank { "Progress updated to $progress%" }
                val title = args.optString("title", "").trim().ifBlank { null }
                val project = args.optString("project", "").trim().ifBlank { null }

                val taskResult = repository.upsertTaskFromMcp(
                    userId = userId,
                    agent = authenticatedAgent,
                    taskId = taskId,
                    title = title,
                    project = project,
                    status = TaskStatus.RUNNING,
                    progress = progress,
                    summary = message
                )
                if (taskResult.isFailure) {
                    return errorJsonRpc(rpcId, 409, -32004, taskResult.exceptionOrNull()?.message ?: "Failed to update progress")
                }
                val task = taskResult.getOrThrow()
                val shouldNotify = currentProfile?.shouldNotify(NotificationCategory.TASK_PROGRESS) ?: false
                val evtId = repository.appendEvent(
                    userId = userId,
                    agentId = authenticatedAgent.id,
                    agentName = authenticatedAgent.name,
                    taskId = task.id,
                    project = task.project,
                    eventType = EventType.TASK_PROGRESS,
                    title = "${task.title} reached $progress%",
                    message = message,
                    idempotencyKey = resolvedIdempotency,
                    deliveryStatus = if (shouldNotify) "delivered" else "suppressed"
                ).getOrNull()

                var notifCreated = false
                if (shouldNotify) {
                    val notif = repository.createNotification(
                        userId = userId,
                        taskId = task.id,
                        eventId = evtId,
                        agentName = authenticatedAgent.name,
                        project = task.project,
                        title = "${task.title} ($progress%)",
                        message = message,
                        category = NotificationCategory.TASK_PROGRESS
                    ).getOrNull()
                    if (notif != null) {
                        notifCreated = true
                        maybeDispatchOsNotification(notif)
                    }
                }
                successToolText(
                    rpcId,
                    JSONObject().apply {
                        put("ok", true)
                        put("task_id", task.id)
                        put("progress", task.progressInt)
                        put("status", task.status)
                        put("notification_dispatched", notifCreated)
                    },
                    notificationCreated = notifCreated
                )
            }

            "request_user_input" -> {
                val taskId = args.optString("task_id", "").trim()
                val question = args.optString("question", "").trim()
                val context = args.optString("context", "").trim().ifBlank { null }
                val title = args.optString("title", "").trim().ifBlank { null }
                val project = args.optString("project", "").trim().ifBlank { null }
                val optionsArray = args.optJSONArray("options")
                val options = buildList {
                    if (optionsArray != null) {
                        for (i in 0 until minOf(optionsArray.length(), 10)) {
                            val opt = optionsArray.optString(i, "").trim()
                            if (opt.isNotBlank()) add(opt)
                        }
                    }
                }
                if (taskId.isBlank() || question.isBlank()) {
                    return errorJsonRpc(rpcId, 400, -32602, "request_user_input requires task_id and question.")
                }

                val taskResult = repository.upsertTaskFromMcp(
                    userId = userId,
                    agent = authenticatedAgent,
                    taskId = taskId,
                    title = title,
                    project = project,
                    status = TaskStatus.WAITING_FOR_INPUT,
                    progress = 50,
                    pendingQuestion = question,
                    pendingContext = context,
                    pendingOptions = options
                )
                if (taskResult.isFailure) {
                    return errorJsonRpc(rpcId, 409, -32004, taskResult.exceptionOrNull()?.message ?: "Failed to request input")
                }
                val task = taskResult.getOrThrow()
                val shouldNotify = currentProfile?.shouldNotify(NotificationCategory.USER_INPUT_REQUIRED) ?: true
                val evtId = repository.appendEvent(
                    userId = userId,
                    agentId = authenticatedAgent.id,
                    agentName = authenticatedAgent.name,
                    taskId = task.id,
                    project = task.project,
                    eventType = EventType.USER_INPUT_REQUESTED,
                    title = "Input needed for \"${task.title}\"",
                    message = question,
                    idempotencyKey = resolvedIdempotency,
                    deliveryStatus = if (shouldNotify) "delivered" else "suppressed"
                ).getOrNull()

                var notifCreated = false
                if (shouldNotify) {
                    val notif = repository.createNotification(
                        userId = userId,
                        taskId = task.id,
                        eventId = evtId,
                        agentName = authenticatedAgent.name,
                        project = task.project,
                        title = "Decision needed: ${task.title}",
                        message = question,
                        category = NotificationCategory.USER_INPUT_REQUIRED
                    ).getOrNull()
                    if (notif != null) {
                        notifCreated = true
                        maybeDispatchOsNotification(notif)
                    }
                }
                successToolText(
                    rpcId,
                    JSONObject().apply {
                        put("ok", true)
                        put("task_id", task.id)
                        put("status", task.status)
                        put("notification_dispatched", notifCreated)
                    },
                    notificationCreated = notifCreated
                )
            }

            "get_task_status" -> {
                val taskId = args.optString("task_id", "").trim()
                if (taskId.isBlank()) {
                    return errorJsonRpc(rpcId, 400, -32602, "get_task_status requires task_id.")
                }
                val cleanId = CredentialCrypto.sanitizeId(taskId, taskId)
                val task = repository.getTask(userId, cleanId).getOrNull()
                    ?: return errorJsonRpc(rpcId, 404, -32004, "Task '$cleanId' not found.")
                successToolText(
                    rpcId,
                    JSONObject().apply {
                        put("task_id", task.id)
                        put("status", task.status)
                        put("progress", task.progressInt)
                        put("agent", task.agentName)
                        put("project", task.project)
                        put("last_update", task.updatedAt?.toDate()?.toInstant()?.toString() ?: "")
                        put("latest_summary", task.summary ?: task.error ?: task.pendingQuestion ?: "")
                        if (!task.userResponse.isNullOrBlank()) {
                            put("user_response", task.userResponse)
                        }
                    },
                    notificationCreated = false
                )
            }

            "register_agent" -> {
                val agentName = args.optString("agent_name", "").trim()
                val agentTypeRaw = args.optString("agent_type", "custom").trim()
                if (agentName.isBlank()) {
                    return errorJsonRpc(rpcId, 400, -32602, "register_agent requires agent_name.")
                }
                val metadata = args.optJSONObject("metadata")?.toString()?.take(500)
                val reg = repository.registerAgent(
                    userId = userId,
                    name = agentName,
                    type = AgentType.fromWire(agentTypeRaw),
                    metadataSummary = metadata,
                    initialStatus = AgentStatus.CONNECTED
                )
                if (reg.isFailure) {
                    return errorJsonRpc(rpcId, 500, -32000, reg.exceptionOrNull()?.message ?: "Registration failed")
                }
                val (newAgent, _) = reg.getOrThrow()
                // Never expose raw token in MCP response or logs
                successToolText(
                    rpcId,
                    JSONObject().apply {
                        put("ok", true)
                        put("agent_id", newAgent.id)
                        put("agent_name", newAgent.name)
                        put("agent_type", newAgent.type)
                        put("status", newAgent.status)
                        put("credential_prefix", newAgent.credentialPrefix)
                    },
                    notificationCreated = false
                )
            }

            "notify_agent_connected" -> {
                repository.setAgentConnectivity(userId, authenticatedAgent.id, AgentStatus.CONNECTED)
                val shouldNotify = currentProfile?.shouldNotify(NotificationCategory.AGENT_CONNECTED) ?: true
                val evtId = repository.appendEvent(
                    userId = userId,
                    agentId = authenticatedAgent.id,
                    agentName = authenticatedAgent.name,
                    taskId = null,
                    project = null,
                    eventType = EventType.AGENT_CONNECTED,
                    title = "${authenticatedAgent.name} connected",
                    message = "${authenticatedAgent.agentTypeEnum.displayName} established an authenticated MCP session.",
                    idempotencyKey = resolvedIdempotency,
                    deliveryStatus = if (shouldNotify) "delivered" else "suppressed"
                ).getOrNull()

                var notifCreated = false
                if (shouldNotify) {
                    val notif = repository.createNotification(
                        userId = userId,
                        taskId = null,
                        eventId = evtId,
                        agentName = authenticatedAgent.name,
                        project = null,
                        title = "${authenticatedAgent.name} connected",
                        message = "MCP session is active and ready to report task updates.",
                        category = NotificationCategory.AGENT_CONNECTED
                    ).getOrNull()
                    if (notif != null) {
                        notifCreated = true
                        maybeDispatchOsNotification(notif)
                    }
                }
                successToolText(
                    rpcId,
                    JSONObject().apply {
                        put("ok", true)
                        put("agent_id", authenticatedAgent.id)
                        put("status", "connected")
                    },
                    notificationCreated = notifCreated
                )
            }

            "notify_agent_disconnected" -> {
                val reason = args.optString("reason", "Agent closed MCP session.").trim().ifBlank { "Agent closed MCP session." }
                repository.setAgentConnectivity(userId, authenticatedAgent.id, AgentStatus.DISCONNECTED)
                val shouldNotify = currentProfile?.shouldNotify(NotificationCategory.AGENT_DISCONNECTED) ?: true
                val evtId = repository.appendEvent(
                    userId = userId,
                    agentId = authenticatedAgent.id,
                    agentName = authenticatedAgent.name,
                    taskId = null,
                    project = null,
                    eventType = EventType.AGENT_DISCONNECTED,
                    title = "${authenticatedAgent.name} disconnected",
                    message = reason,
                    idempotencyKey = resolvedIdempotency,
                    deliveryStatus = if (shouldNotify) "delivered" else "suppressed"
                ).getOrNull()

                var notifCreated = false
                if (shouldNotify) {
                    val notif = repository.createNotification(
                        userId = userId,
                        taskId = null,
                        eventId = evtId,
                        agentName = authenticatedAgent.name,
                        project = null,
                        title = "${authenticatedAgent.name} disconnected",
                        message = reason,
                        category = NotificationCategory.AGENT_DISCONNECTED
                    ).getOrNull()
                    if (notif != null) {
                        notifCreated = true
                        maybeDispatchOsNotification(notif)
                    }
                }
                successToolText(
                    rpcId,
                    JSONObject().apply {
                        put("ok", true)
                        put("agent_id", authenticatedAgent.id)
                        put("status", "disconnected")
                    },
                    notificationCreated = notifCreated
                )
            }

            else -> errorJsonRpc(rpcId, 404, -32601, "Unknown MCP tool: '$toolName'.")
        }
    }

    private fun maybeDispatchOsNotification(notification: com.example.model.AppNotification) {
        val ctx = appContext ?: return
        if (currentProfile?.pushEnabled == true) {
            SystemNotificationDispatcher.dispatch(ctx, notification)
        }
    }

    private fun successJsonRpc(id: Any?, result: JSONObject): McpToolDispatchOutcome {
        val body = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id ?: JSONObject.NULL)
            put("result", result)
        }
        return McpToolDispatchOutcome(200, body.toString())
    }

    private fun successToolText(
        id: Any?,
        payload: JSONObject,
        notificationCreated: Boolean
    ): McpToolDispatchOutcome {
        val result = JSONObject().apply {
            put("content", JSONArray().put(
                JSONObject().apply {
                    put("type", "text")
                    put("text", payload.toString())
                }
            ))
            put("structuredContent", payload)
            put("isError", false)
        }
        val body = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id ?: JSONObject.NULL)
            put("result", result)
        }
        return McpToolDispatchOutcome(200, body.toString(), notificationCreated)
    }

    private fun errorJsonRpc(id: Any?, httpStatus: Int, code: Int, message: String): McpToolDispatchOutcome {
        val body = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id ?: JSONObject.NULL)
            put("error", JSONObject().apply {
                put("code", code)
                put("message", message)
            })
        }
        return McpToolDispatchOutcome(httpStatus, body.toString())
    }

    private fun buildToolSchemas(): JSONArray = JSONArray().apply {
        put(toolSchema("notify_task_completed", "Notify the user when an agent successfully completes a task.", listOf("task_id", "title", "project", "summary")))
        put(toolSchema("notify_task_failed", "Notify the user when an agent encounters a task failure.", listOf("task_id", "title", "error", "project")))
        put(toolSchema("update_task_progress", "Update task progress between 0 and 100.", listOf("task_id", "progress", "message")))
        put(toolSchema("request_user_input", "Request a decision from the user and pause task.", listOf("task_id", "question")))
        put(toolSchema("get_task_status", "Retrieve the latest task status and user reply.", listOf("task_id")))
        put(toolSchema("register_agent", "Register an agent with Notifyme.", listOf("agent_name", "agent_type")))
        put(toolSchema("notify_agent_connected", "Notify the user when an agent connects.", emptyList()))
        put(toolSchema("notify_agent_disconnected", "Notify the user when an agent disconnects.", emptyList()))
    }

    private fun toolSchema(name: String, description: String, required: List<String>): JSONObject =
        JSONObject().apply {
            put("name", name)
            put("description", description)
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray(required))
            })
        }
}

class EmbeddedMcpServer(
    private val processor: McpToolProcessor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null
    private var serverSocket: ServerSocket? = null

    private val _status = MutableStateFlow(McpServerStatus())
    val status: StateFlow<McpServerStatus> = _status.asStateFlow()

    fun start(port: Int = 8765, bindLan: Boolean = false) {
        stop()
        acceptJob = scope.launch {
            try {
                val bindAddress = if (bindLan) InetAddress.getByName("0.0.0.0") else InetAddress.getByName("127.0.0.1")
                val socket = ServerSocket(port, 32, bindAddress)
                serverSocket = socket
                val hostLabel = if (bindLan) resolveLanIp() ?: "127.0.0.1" else "127.0.0.1"
                _status.value = McpServerStatus(
                    isRunning = true,
                    port = port,
                    bindHost = hostLabel,
                    displayEndpoint = "http://$hostLabel:$port/mcp",
                    requestsHandled = _status.value.requestsHandled,
                    lastSuccessfulConnectionAtMillis = _status.value.lastSuccessfulConnectionAtMillis,
                    lastConnectedAgentName = _status.value.lastConnectedAgentName,
                    lastError = null
                )

                while (isActive && !socket.isClosed) {
                    val client = socket.accept()
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                if (serverSocket != null) {
                    Log.w("EmbeddedMcpServer", "MCP server stopped: ${e.message}")
                    _status.value = _status.value.copy(
                        isRunning = false,
                        lastError = e.message
                    )
                }
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        _status.value = _status.value.copy(isRunning = false)
    }

    private suspend fun handleClient(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 10_000
            val input = BufferedInputStream(sock.getInputStream())
            val rawHeaders = readHeaders(input) ?: return
            val headerLines = rawHeaders.split("\r\n")
            val requestLine = headerLines.firstOrNull()?.split(" ") ?: return
            if (requestLine.size < 2) return
            val method = requestLine[0].uppercase()
            val path = requestLine[1].substringBefore("?")

            val headers = mutableMapOf<String, String>()
            headerLines.drop(1).forEach { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
            }

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            if (contentLength > 65_536) {
                writeHttpResponse(sock, 413, """{"error":"Payload too large (max 64KB)"}""")
                return
            }

            val bodyBytes = if (contentLength > 0) readExactly(input, contentLength) else ByteArray(0)
            val bodyString = bodyBytes.toString(Charsets.UTF_8)

            when {
                method == "GET" && path == "/health" -> {
                    val health = JSONObject().apply {
                        put("status", "ok")
                        put("service", "notifyme-mcp")
                        put("protocol", "streamable-http")
                        put("port", _status.value.port)
                    }
                    writeHttpResponse(sock, 200, health.toString())
                }
                method == "POST" && path == "/mcp" -> {
                    val authHeader = headers["authorization"]
                    val outcome = processor.handleJsonRpc(authHeader, bodyString)
                    if (outcome.httpStatus in 200..299) {
                        _status.value = _status.value.copy(
                            requestsHandled = _status.value.requestsHandled + 1L,
                            lastSuccessfulConnectionAtMillis = System.currentTimeMillis(),
                            lastError = null
                        )
                    } else {
                        _status.value = _status.value.copy(
                            lastError = "HTTP ${outcome.httpStatus}"
                        )
                    }
                    writeHttpResponse(sock, outcome.httpStatus, outcome.responseJson)
                }
                else -> {
                    writeHttpResponse(sock, 404, """{"error":"Not Found. Use POST /mcp or GET /health"}""")
                }
            }
        }
    }

    private fun readHeaders(input: BufferedInputStream): String? {
        val baos = ByteArrayOutputStream()
        var prev3 = 0
        var prev2 = 0
        var prev1 = 0
        while (baos.size() < 16_384) {
            val b = input.read()
            if (b == -1) return null
            baos.write(b)
            if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
                return baos.toString(Charsets.UTF_8.name()).trimEnd()
            }
            prev3 = prev2
            prev2 = prev1
            prev1 = b
        }
        return null
    }

    private fun readExactly(input: BufferedInputStream, length: Int): ByteArray {
        val buf = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(buf, offset, length - offset)
            if (read == -1) break
            offset += read
        }
        return if (offset == length) buf else buf.copyOf(offset)
    }

    private fun writeHttpResponse(socket: Socket, status: Int, jsonBody: String) {
        val statusText = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            409 -> "Conflict"
            413 -> "Payload Too Large"
            429 -> "Too Many Requests"
            else -> "Internal Server Error"
        }
        val bodyBytes = jsonBody.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $status $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        val out = socket.getOutputStream()
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(bodyBytes)
        out.flush()
    }

    private fun resolveLanIp(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}

object McpHttpTestClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun sendToolCall(
        endpoint: String,
        bearerToken: String,
        toolName: String,
        arguments: JSONObject
    ): Result<Pair<Int, String>> {
        val rpcPayload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", UUID.randomUUID().toString().take(8))
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", toolName)
                put("arguments", arguments)
            })
        }
        val body = rpcPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $bearerToken")
            .addHeader("Accept", "application/json")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                Result.success(response.code to respBody)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
