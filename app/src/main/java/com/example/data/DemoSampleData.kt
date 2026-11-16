package com.example.data

import com.example.model.Agent
import com.example.model.AgentEvent
import com.example.model.AgentStatus
import com.example.model.AgentTask
import com.example.model.AgentType
import com.example.model.AppNotification
import com.example.model.EventType
import com.example.model.NotificationCategory
import com.example.model.TaskStatus
import com.google.firebase.Timestamp
import java.util.Date

object DemoSampleData {

    private fun minutesAgo(mins: Long): Timestamp =
        Timestamp(Date(System.currentTimeMillis() - mins * 60_000L))

    val agents: List<Agent> = listOf(
        Agent(
            id = "demo_antigravity",
            userId = "demo",
            name = "Antigravity Primary",
            type = AgentType.ANTIGRAVITY.wireValue,
            status = AgentStatus.CONNECTED.wireValue,
            totalTasks = 14L,
            completedTasks = 11L,
            failedTasks = 1L,
            credentialPrefix = "ntfy_demo_a8f2...9c1e",
            credentialHash = "0".repeat(64),
            metadataSummary = "Demo workspace • Gemini coding agent",
            lastSeenAt = minutesAgo(1),
            createdAt = minutesAgo(420),
            updatedAt = minutesAgo(1)
        ),
        Agent(
            id = "demo_claude",
            userId = "demo",
            name = "Claude Code CLI",
            type = AgentType.CLAUDE_CODE.wireValue,
            status = AgentStatus.DISCONNECTED.wireValue,
            totalTasks = 6L,
            completedTasks = 5L,
            failedTasks = 0L,
            credentialPrefix = "ntfy_demo_7b11...4d02",
            credentialHash = "1".repeat(64),
            metadataSummary = "Demo repo • backend refactor",
            lastSeenAt = minutesAgo(45),
            createdAt = minutesAgo(960),
            updatedAt = minutesAgo(45)
        )
    )

    val tasks: List<AgentTask> = listOf(
        AgentTask(
            id = "demo_task_auth",
            userId = "demo",
            agentId = "demo_antigravity",
            agentName = "Antigravity Primary",
            title = "Authentication System",
            project = "notifyme-core",
            status = TaskStatus.COMPLETED.wireValue,
            progress = 100L,
            summary = "Implemented Google Sign-In via Credential Manager, SHA-256 agent token hashing, and Firestore ownership rules.",
            createdAt = minutesAgo(38),
            updatedAt = minutesAgo(12),
            completedAt = minutesAgo(12)
        ),
        AgentTask(
            id = "demo_task_migration",
            userId = "demo",
            agentId = "demo_antigravity",
            agentName = "Antigravity Primary",
            title = "Migrate Event Pipeline to Idempotent Dispatch",
            project = "notifyme-mcp",
            status = TaskStatus.WAITING_FOR_INPUT.wireValue,
            progress = 65L,
            summary = "Idempotency key deduplication ready; awaiting retention window decision.",
            pendingQuestion = "How long should idempotency keys be retained in the event index?",
            pendingContext = "Shorter retention saves index storage; 7 days covers long offline agent retries.",
            pendingOptions = listOf("24 hours", "7 days (Recommended)", "30 days"),
            createdAt = minutesAgo(22),
            updatedAt = minutesAgo(3)
        ),
        AgentTask(
            id = "demo_task_rate_limit",
            userId = "demo",
            agentId = "demo_antigravity",
            agentName = "Antigravity Primary",
            title = "Streamable HTTP Rate Limiter",
            project = "notifyme-mcp",
            status = TaskStatus.RUNNING.wireValue,
            progress = 55L,
            summary = "Adding per-agent sliding-window throttling and 64KB payload guard.",
            createdAt = minutesAgo(9),
            updatedAt = minutesAgo(1)
        ),
        AgentTask(
            id = "demo_task_lint",
            userId = "demo",
            agentId = "demo_claude",
            agentName = "Claude Code CLI",
            title = "E2E Integration Test Suite",
            project = "notifyme-tests",
            status = TaskStatus.FAILED.wireValue,
            progress = 80L,
            error = "Firestore emulator port 8085 was unreachable during CI dry run.",
            createdAt = minutesAgo(90),
            updatedAt = minutesAgo(52)
        )
    )

    val events: List<AgentEvent> = listOf(
        AgentEvent(
            id = "demo_evt_1",
            userId = "demo",
            agentId = "demo_antigravity",
            agentName = "Antigravity Primary",
            taskId = "demo_task_rate_limit",
            project = "notifyme-mcp",
            eventType = EventType.TASK_PROGRESS.wireValue,
            title = "Streamable HTTP Rate Limiter reached 55%",
            message = "Configured sliding-window 60 req/min limiter and verified 429 JSON-RPC response.",
            idempotencyKey = "demo_idem_1",
            deliveryStatus = "suppressed",
            createdAt = minutesAgo(1)
        ),
        AgentEvent(
            id = "demo_evt_2",
            userId = "demo",
            agentId = "demo_antigravity",
            agentName = "Antigravity Primary",
            taskId = "demo_task_migration",
            project = "notifyme-mcp",
            eventType = EventType.USER_INPUT_REQUESTED.wireValue,
            title = "Input needed for \"Migrate Event Pipeline to Idempotent Dispatch\"",
            message = "How long should idempotency keys be retained in the event index?",
            idempotencyKey = "demo_idem_2",
            deliveryStatus = "delivered",
            createdAt = minutesAgo(3)
        ),
        AgentEvent(
            id = "demo_evt_3",
            userId = "demo",
            agentId = "demo_antigravity",
            agentName = "Antigravity Primary",
            taskId = "demo_task_auth",
            project = "notifyme-core",
            eventType = EventType.TASK_COMPLETED.wireValue,
            title = "Antigravity Primary completed \"Authentication System\"",
            message = "All 14 security rule checks passed.",
            idempotencyKey = "demo_idem_3",
            deliveryStatus = "delivered",
            createdAt = minutesAgo(12)
        ),
        AgentEvent(
            id = "demo_evt_4",
            userId = "demo",
            agentId = "demo_claude",
            agentName = "Claude Code CLI",
            taskId = "demo_task_lint",
            project = "notifyme-tests",
            eventType = EventType.TASK_FAILED.wireValue,
            title = "Claude Code CLI failed \"E2E Integration Test Suite\"",
            message = "Firestore emulator port 8085 was unreachable during CI dry run.",
            idempotencyKey = "demo_idem_4",
            deliveryStatus = "delivered",
            createdAt = minutesAgo(52)
        )
    )

    val notifications: List<AppNotification> = listOf(
        AppNotification(
            id = "demo_notif_1",
            userId = "demo",
            taskId = "demo_task_migration",
            eventId = "demo_evt_2",
            agentName = "Antigravity Primary",
            project = "notifyme-mcp",
            title = "Decision needed: Migrate Event Pipeline",
            message = "How long should idempotency keys be retained in the event index?",
            category = NotificationCategory.USER_INPUT_REQUIRED.wireValue,
            isRead = false,
            createdAt = minutesAgo(3)
        ),
        AppNotification(
            id = "demo_notif_2",
            userId = "demo",
            taskId = "demo_task_auth",
            eventId = "demo_evt_3",
            agentName = "Antigravity Primary",
            project = "notifyme-core",
            title = "Antigravity completed \"Authentication System\"",
            message = "Implemented Google Sign-In, SHA-256 agent token hashing, and Firestore rules.",
            category = NotificationCategory.TASK_COMPLETED.wireValue,
            isRead = false,
            createdAt = minutesAgo(12)
        ),
        AppNotification(
            id = "demo_notif_3",
            userId = "demo",
            taskId = "demo_task_lint",
            eventId = "demo_evt_4",
            agentName = "Claude Code CLI",
            project = "notifyme-tests",
            title = "Claude Code failed \"E2E Integration Test Suite\"",
            message = "Firestore emulator port 8085 was unreachable during CI dry run.",
            category = NotificationCategory.TASK_FAILED.wireValue,
            isRead = true,
            readAt = minutesAgo(40),
            createdAt = minutesAgo(52)
        )
    )
}
