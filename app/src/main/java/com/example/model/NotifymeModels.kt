package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AgentType(val wireValue: String, val displayName: String) {
    ANTIGRAVITY("antigravity", "Google Antigravity"),
    CLAUDE_CODE("claude_code", "Claude Code"),
    CODEX("codex", "Codex"),
    CURSOR("cursor", "Cursor"),
    CUSTOM("custom", "Custom MCP Agent");

    companion object {
        fun fromWire(value: String): AgentType =
            entries.firstOrNull { it.wireValue == value.lowercase() } ?: CUSTOM
    }
}

enum class AgentStatus(val wireValue: String, val displayName: String) {
    CONNECTED("connected", "Connected"),
    DISCONNECTED("disconnected", "Disconnected"),
    REVOKED("revoked", "Revoked");

    companion object {
        fun fromWire(value: String): AgentStatus =
            entries.firstOrNull { it.wireValue == value.lowercase() } ?: DISCONNECTED
    }
}

enum class TaskStatus(val wireValue: String, val displayName: String) {
    PENDING("pending", "Pending"),
    RUNNING("running", "Running"),
    COMPLETED("completed", "Completed"),
    FAILED("failed", "Failed"),
    WAITING_FOR_INPUT("waiting_for_input", "Waiting for Input"),
    CANCELLED("cancelled", "Cancelled");

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED

    companion object {
        fun fromWire(value: String): TaskStatus =
            entries.firstOrNull { it.wireValue == value.lowercase() } ?: PENDING
    }
}

enum class NotificationCategory(val wireValue: String, val displayName: String) {
    TASK_COMPLETED("task_completed", "Task Completed"),
    TASK_FAILED("task_failed", "Task Failed"),
    TASK_PROGRESS("task_progress", "Task Progress"),
    USER_INPUT_REQUIRED("user_input_required", "User Input Required"),
    AGENT_CONNECTED("agent_connected", "Agent Connected"),
    AGENT_DISCONNECTED("agent_disconnected", "Agent Disconnected"),
    SYSTEM_ALERT("system_alert", "System Alerts");

    companion object {
        fun fromWire(value: String): NotificationCategory =
            entries.firstOrNull { it.wireValue == value.lowercase() } ?: SYSTEM_ALERT
    }
}

enum class EventType(val wireValue: String, val displayName: String) {
    TASK_STARTED("task_started", "Task Started"),
    TASK_PROGRESS("task_progress", "Task Progress"),
    TASK_COMPLETED("task_completed", "Task Completed"),
    TASK_FAILED("task_failed", "Task Failed"),
    USER_INPUT_REQUESTED("user_input_requested", "User Input Requested"),
    USER_INPUT_RESPONDED("user_input_responded", "User Input Responded"),
    AGENT_CONNECTED("agent_connected", "Agent Connected"),
    AGENT_DISCONNECTED("agent_disconnected", "Agent Disconnected"),
    AGENT_REGISTERED("agent_registered", "Agent Registered"),
    CREDENTIAL_ROTATED("credential_rotated", "Credential Rotated"),
    NOTIFICATION_DISPATCHED("notification_dispatched", "Notification Dispatched");

    companion object {
        fun fromWire(value: String): EventType =
            entries.firstOrNull { it.wireValue == value.lowercase() } ?: TASK_PROGRESS
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val themeMode: String = "dark",
    val pushEnabled: Boolean = false,
    val notifyOnCompleted: Boolean = true,
    val notifyOnFailed: Boolean = true,
    val notifyOnProgress: Boolean = false,
    val notifyOnInputRequired: Boolean = true,
    val notifyOnAgentConnected: Boolean = true,
    val notifyOnAgentDisconnected: Boolean = true,
    val notifyOnSystemAlerts: Boolean = true,
    val fcmTokenStatus: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun shouldNotify(category: NotificationCategory): Boolean = when (category) {
        NotificationCategory.TASK_COMPLETED -> notifyOnCompleted
        NotificationCategory.TASK_FAILED -> notifyOnFailed
        NotificationCategory.TASK_PROGRESS -> notifyOnProgress
        NotificationCategory.USER_INPUT_REQUIRED -> notifyOnInputRequired
        NotificationCategory.AGENT_CONNECTED -> notifyOnAgentConnected
        NotificationCategory.AGENT_DISCONNECTED -> notifyOnAgentDisconnected
        NotificationCategory.SYSTEM_ALERT -> notifyOnSystemAlerts
    }
}

@IgnoreExtraProperties
data class Agent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val type: String = AgentType.ANTIGRAVITY.wireValue,
    val status: String = AgentStatus.DISCONNECTED.wireValue,
    val totalTasks: Long = 0L,
    val completedTasks: Long = 0L,
    val failedTasks: Long = 0L,
    val credentialPrefix: String = "",
    val credentialHash: String = "",
    val metadataSummary: String? = null,
    val lastSeenAt: Timestamp? = null,
    val credentialRotatedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val agentTypeEnum: AgentType get() = AgentType.fromWire(type)
    val agentStatusEnum: AgentStatus get() = AgentStatus.fromWire(status)
}

@IgnoreExtraProperties
data class AgentTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val agentId: String = "",
    val agentName: String = "",
    val title: String = "",
    val project: String = "",
    val status: String = TaskStatus.PENDING.wireValue,
    val progress: Long = 0L,
    val summary: String? = null,
    val error: String? = null,
    val pendingQuestion: String? = null,
    val pendingContext: String? = null,
    val pendingOptions: List<String> = emptyList(),
    val userResponse: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
) {
    val taskStatusEnum: TaskStatus get() = TaskStatus.fromWire(status)
    val progressInt: Int get() = progress.toInt().coerceIn(0, 100)
}

@IgnoreExtraProperties
data class AgentEvent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val agentId: String = "",
    val agentName: String = "",
    val taskId: String? = null,
    val project: String? = null,
    val eventType: String = EventType.TASK_PROGRESS.wireValue,
    val title: String = "",
    val message: String = "",
    val idempotencyKey: String = "",
    val deliveryStatus: String = "recorded",
    @ServerTimestamp val createdAt: Timestamp? = null,
) {
    val eventTypeEnum: EventType get() = EventType.fromWire(eventType)
}

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val taskId: String? = null,
    val eventId: String? = null,
    val agentName: String? = null,
    val project: String? = null,
    val title: String = "",
    val message: String = "",
    val category: String = NotificationCategory.SYSTEM_ALERT.wireValue,
    val isRead: Boolean = false,
    val readAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
) {
    val categoryEnum: NotificationCategory get() = NotificationCategory.fromWire(category)
}
