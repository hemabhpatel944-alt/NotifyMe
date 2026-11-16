package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DemoSampleData
import com.example.data.LocalPreferencesStore
import com.example.data.NotifymeRepository
import com.example.data.unwrapFirestoreError
import com.example.mcp.EmbeddedMcpServer
import com.example.mcp.McpHttpTestClient
import com.example.mcp.McpServerStatus
import com.example.mcp.McpToolProcessor
import com.example.model.Agent
import com.example.model.AgentEvent
import com.example.model.AgentTask
import com.example.model.AgentType
import com.example.model.AppNotification
import com.example.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class RevealedCredential(
    val agentId: String,
    val agentName: String,
    val rawToken: String
)

class NotifymeViewModel(
    private val repository: NotifymeRepository,
    private val localPrefs: LocalPreferencesStore,
    appContext: Context,
    private val currentUserId: String,
    private val userEmail: String,
    private val userDisplayName: String,
    private val userPhotoUrl: String?
) : ViewModel() {

    private val processor = McpToolProcessor(repository, appContext.applicationContext)
    private val mcpServer = EmbeddedMcpServer(processor)

    val mcpStatus: StateFlow<McpServerStatus> = mcpServer.status
    val demoMode: StateFlow<Boolean> = localPrefs.demoModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)
    val mcpPort: StateFlow<Int> = localPrefs.mcpPortFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 8765)
    val lanBinding: StateFlow<Boolean> = localPrefs.lanBindingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    private val _revealedCredential = MutableStateFlow<RevealedCredential?>(null)
    val revealedCredential: StateFlow<RevealedCredential?> = _revealedCredential.asStateFlow()

    // In-memory session cache of tokens created/rotated during this session for the MCP Setup test runner
    private val _sessionTokens = MutableStateFlow<Map<String, String>>(emptyMap())
    val sessionTokens: StateFlow<Map<String, String>> = _sessionTokens.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _lastRpcOutput = MutableStateFlow<String?>(null)
    val lastRpcOutput: StateFlow<String?> = _lastRpcOutput.asStateFlow()

    private val _isSendingTestRpc = MutableStateFlow(false)
    val isSendingTestRpc: StateFlow<Boolean> = _isSendingTestRpc.asStateFlow()

    val profileState: StateFlow<UiState<UserProfile>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile>> { profile ->
            UiState.Success(
                profile ?: UserProfile(
                    id = currentUserId,
                    userId = currentUserId,
                    email = userEmail,
                    displayName = userDisplayName.ifBlank { "Developer" },
                    photoUrl = userPhotoUrl
                )
            )
        }
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Profile stream error", fsErr ?: error)
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val liveAgentsState: StateFlow<UiState<List<Agent>>> = repository.observeAgents(currentUserId)
        .map<List<Agent>, UiState<List<Agent>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Agents stream error", fsErr ?: error)
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load agents"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val liveTasksState: StateFlow<UiState<List<AgentTask>>> = repository.observeTasks(currentUserId)
        .map<List<AgentTask>, UiState<List<AgentTask>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Tasks stream error", fsErr ?: error)
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load tasks"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val liveEventsState: StateFlow<UiState<List<AgentEvent>>> = repository.observeEvents(currentUserId)
        .map<List<AgentEvent>, UiState<List<AgentEvent>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Events stream error", fsErr ?: error)
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load activity"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val liveNotificationsState: StateFlow<UiState<List<AppNotification>>> = repository.observeNotifications(currentUserId)
        .map<List<AppNotification>, UiState<List<AppNotification>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Notifications stream error", fsErr ?: error)
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load notifications"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val agentsState: StateFlow<UiState<List<Agent>>> = combine(liveAgentsState, demoMode) { live, isDemo ->
        if (isDemo) UiState.Success(DemoSampleData.agents) else live
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val tasksState: StateFlow<UiState<List<AgentTask>>> = combine(liveTasksState, demoMode) { live, isDemo ->
        if (isDemo) UiState.Success(DemoSampleData.tasks) else live
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val eventsState: StateFlow<UiState<List<AgentEvent>>> = combine(liveEventsState, demoMode) { live, isDemo ->
        if (isDemo) UiState.Success(DemoSampleData.events) else live
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val notificationsState: StateFlow<UiState<List<AppNotification>>> = combine(liveNotificationsState, demoMode) { live, isDemo ->
        if (isDemo) UiState.Success(DemoSampleData.notifications) else live
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(
                userId = currentUserId,
                email = userEmail,
                displayName = userDisplayName,
                photoUrl = userPhotoUrl
            )
        }
        // Keep MCP processor synced with live agents & preferences
        viewModelScope.launch {
            combine(liveAgentsState, profileState) { agState, profState ->
                val agList = (agState as? UiState.Success)?.data ?: emptyList()
                val prof = (profState as? UiState.Success)?.data
                agList to prof
            }.collect { (agList, prof) ->
                processor.updateSessionContext(currentUserId, agList, prof)
            }
        }
        // Start embedded Streamable HTTP MCP server
        viewModelScope.launch {
            mcpServer.start(port = 8765, bindLan = false)
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun dismissRevealedCredential() {
        _revealedCredential.value = null
    }

    fun toggleDemoMode(enabled: Boolean) {
        viewModelScope.launch {
            localPrefs.setDemoMode(enabled)
            _toastMessage.value = if (enabled) "Demo Mode enabled (sample data)" else "Returned to live Firestore telemetry"
        }
    }

    fun toggleMcpServer(enabled: Boolean) {
        if (enabled) {
            mcpServer.start(port = mcpPort.value, bindLan = lanBinding.value)
            _toastMessage.value = "MCP server started on port ${mcpPort.value}"
        } else {
            mcpServer.stop()
            _toastMessage.value = "MCP server stopped"
        }
    }

    fun updateMcpNetworkSettings(port: Int, bindLan: Boolean) {
        viewModelScope.launch {
            localPrefs.setMcpPort(port)
            localPrefs.setLanBinding(bindLan)
            if (mcpStatus.value.isRunning) {
                mcpServer.start(port = port, bindLan = bindLan)
            }
            _toastMessage.value = "MCP server binding updated"
        }
    }

    fun registerAgent(name: String, type: AgentType) {
        viewModelScope.launch {
            val res = repository.registerAgent(currentUserId, name, type)
            res.onSuccess { (agent, token) ->
                _sessionTokens.value = _sessionTokens.value + (agent.id to token)
                _revealedCredential.value = RevealedCredential(agent.id, agent.name, token)
                _toastMessage.value = "Registered ${agent.name}"
            }.onFailure {
                _toastMessage.value = it.message ?: "Failed to register agent"
            }
        }
    }

    fun renameAgent(agentId: String, newName: String) {
        if (demoMode.value) {
            _toastMessage.value = "Disable Demo Mode to modify live agents."
            return
        }
        viewModelScope.launch {
            repository.renameAgent(currentUserId, agentId, newName)
                .onSuccess { _toastMessage.value = "Agent renamed" }
                .onFailure { _toastMessage.value = it.message ?: "Rename failed" }
        }
    }

    fun rotateAgentCredential(agent: Agent) {
        if (demoMode.value) {
            _toastMessage.value = "Disable Demo Mode to rotate live credentials."
            return
        }
        viewModelScope.launch {
            repository.rotateAgentCredential(currentUserId, agent.id, agent.name)
                .onSuccess { newToken ->
                    _sessionTokens.value = _sessionTokens.value + (agent.id to newToken)
                    _revealedCredential.value = RevealedCredential(agent.id, agent.name, newToken)
                    _toastMessage.value = "Credential rotated for ${agent.name}"
                }
                .onFailure { _toastMessage.value = it.message ?: "Rotation failed" }
        }
    }

    fun revokeAgent(agent: Agent) {
        if (demoMode.value) {
            _toastMessage.value = "Disable Demo Mode to revoke live agents."
            return
        }
        viewModelScope.launch {
            repository.revokeAgent(currentUserId, agent.id, agent.name)
                .onSuccess {
                    _sessionTokens.value = _sessionTokens.value - agent.id
                    _toastMessage.value = "Revoked ${agent.name}"
                }
                .onFailure { _toastMessage.value = it.message ?: "Revoke failed" }
        }
    }

    fun respondToTaskInput(taskId: String, responseText: String) {
        if (demoMode.value) {
            _toastMessage.value = "Demo task: response previewed (disable Demo Mode for live tasks)."
            return
        }
        viewModelScope.launch {
            repository.respondToUserInput(currentUserId, taskId, responseText)
                .onSuccess { _toastMessage.value = "Decision sent to agent task" }
                .onFailure { _toastMessage.value = it.message ?: "Failed to submit response" }
        }
    }

    fun cancelTask(taskId: String) {
        if (demoMode.value) return
        viewModelScope.launch {
            repository.cancelTask(currentUserId, taskId)
                .onSuccess { _toastMessage.value = "Task cancelled" }
                .onFailure { _toastMessage.value = it.message ?: "Failed to cancel task" }
        }
    }

    fun markNotificationRead(notificationId: String) {
        if (demoMode.value) return
        viewModelScope.launch {
            repository.markNotificationRead(currentUserId, notificationId)
        }
    }

    fun markAllNotificationsRead(notifications: List<AppNotification>) {
        if (demoMode.value) return
        val unreadIds = notifications.filter { !it.isRead }.map { it.id }
        if (unreadIds.isEmpty()) return
        viewModelScope.launch {
            repository.markAllNotificationsRead(currentUserId, unreadIds)
                .onSuccess { _toastMessage.value = "Marked all notifications read" }
        }
    }

    fun deleteNotification(notificationId: String) {
        if (demoMode.value) return
        viewModelScope.launch {
            repository.deleteNotification(currentUserId, notificationId)
        }
    }

    fun updateThemeMode(darkTheme: Boolean) {
        val mode = if (darkTheme) "dark" else "light"
        viewModelScope.launch {
            localPrefs.setDarkThemeFallback(darkTheme)
            repository.updateProfilePreferences(currentUserId, mapOf("themeMode" to mode))
        }
    }

    fun updateNotificationPreference(field: String, value: Boolean) {
        viewModelScope.launch {
            repository.updateProfilePreferences(currentUserId, mapOf(field to value))
                .onFailure { _toastMessage.value = it.message ?: "Failed to update preference" }
        }
    }

    fun sendLiveMcpTestCall(
        agentId: String,
        toolName: String,
        taskId: String,
        title: String,
        project: String,
        detailText: String,
        progress: Int
    ) {
        val token = _sessionTokens.value[agentId]
        if (token.isNullOrBlank()) {
            _toastMessage.value = "Rotate or register an agent in this session first so its bearer token is available."
            return
        }
        val status = mcpServer.status.value
        if (!status.isRunning) {
            _toastMessage.value = "Start the MCP Server first."
            return
        }
        val localEndpoint = "http://127.0.0.1:${status.port}/mcp"

        _isSendingTestRpc.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val args = JSONObject().apply {
                put("agent_id", agentId)
                put("task_id", taskId)
                put("title", title)
                put("project", project)
                when (toolName) {
                    "notify_task_completed" -> put("summary", detailText)
                    "notify_task_failed" -> put("error", detailText)
                    "update_task_progress" -> {
                        put("progress", progress.coerceIn(0, 100))
                        put("message", detailText)
                    }
                    "request_user_input" -> {
                        put("question", detailText)
                        put("context", "Triggered via Notifyme live HTTP MCP test runner.")
                        put("options", JSONArray(listOf("Proceed", "Hold", "Rollback")))
                    }
                    "notify_agent_disconnected" -> put("reason", detailText)
                }
            }
            val result = McpHttpTestClient.sendToolCall(localEndpoint, token, toolName, args)
            _isSendingTestRpc.value = false
            result.onSuccess { (code, body) ->
                _lastRpcOutput.value = "HTTP $code\n$body"
                _toastMessage.value = if (code in 200..299) {
                    "MCP tool '$toolName' dispatched via HTTP!"
                } else {
                    "MCP server returned HTTP $code"
                }
            }.onFailure { err ->
                _lastRpcOutput.value = "Transport error: ${err.message}"
                _toastMessage.value = "MCP HTTP call failed: ${err.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mcpServer.stop()
    }

    private companion object {
        const val TAG = "NotifymeVM"
    }
}
