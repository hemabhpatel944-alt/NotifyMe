package com.example.data

import android.content.Context
import com.example.R
import com.example.model.Agent
import com.example.model.AgentEvent
import com.example.model.AgentStatus
import com.example.model.AgentTask
import com.example.model.AgentType
import com.example.model.AppNotification
import com.example.model.EventType
import com.example.model.NotificationCategory
import com.example.model.TaskStatus
import com.example.model.UserProfile
import com.example.security.CredentialCrypto
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class NotifymeRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userDoc(userId: String) = db.collection("users").document(userId)
    private fun agentsCol(userId: String) = userDoc(userId).collection("agents")
    private fun tasksCol(userId: String) = userDoc(userId).collection("tasks")
    private fun eventsCol(userId: String) = userDoc(userId).collection("events")
    private fun notificationsCol(userId: String) = userDoc(userId).collection("notifications")

    fun observeUserProfile(userId: String): Flow<UserProfile?> =
        userDoc(userId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    suspend fun ensureUserProfile(
        userId: String,
        email: String,
        displayName: String,
        photoUrl: String?
    ): Result<UserProfile> {
        val docRef = userDoc(userId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (existing != null) return Result.success(existing)
            }
            val safeName = displayName.trim().ifBlank { "Developer" }.take(100)
            val safeEmail = email.trim().take(200)
            val payload = mutableMapOf<String, Any>(
                "userId" to userId,
                "email" to safeEmail,
                "displayName" to safeName,
                "themeMode" to "dark",
                "pushEnabled" to false,
                "notifyOnCompleted" to true,
                "notifyOnFailed" to true,
                "notifyOnProgress" to false,
                "notifyOnInputRequired" to true,
                "notifyOnAgentConnected" to true,
                "notifyOnAgentDisconnected" to true,
                "notifyOnSystemAlerts" to true,
                "fcmTokenStatus" to "Ready for mobile FCM registration (configure FCM server key & client token in Firebase Console)",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!photoUrl.isNullOrBlank()) {
                payload["photoUrl"] = photoUrl.take(500)
            }
            docRef.set(payload).await()
            val created = docRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(id = userId, userId = userId, email = safeEmail, displayName = safeName)
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateProfilePreferences(userId: String, updates: Map<String, Any>): Result<Unit> {
        val docRef = userDoc(userId)
        return try {
            val filtered = updates.toMutableMap()
            filtered["updatedAt"] = FieldValue.serverTimestamp()
            docRef.update(filtered).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeAgents(userId: String): Flow<List<Agent>> =
        agentsCol(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap ->
                snap.toObjects(Agent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    suspend fun registerAgent(
        userId: String,
        name: String,
        type: AgentType,
        metadataSummary: String? = null,
        requestedAgentId: String? = null,
        initialStatus: AgentStatus = AgentStatus.DISCONNECTED
    ): Result<Pair<Agent, String>> {
        val agentId = if (!requestedAgentId.isNullOrBlank()) {
            CredentialCrypto.sanitizeId(requestedAgentId, "agent_${UUID.randomUUID().toString().take(8)}")
        } else {
            agentsCol(userId).document().id
        }
        val docRef = agentsCol(userId).document(agentId)
        val cleanName = name.trim().ifBlank { type.displayName }.take(80)
        val rawToken = CredentialCrypto.generateAgentToken(agentId)
        val tokenHash = CredentialCrypto.sha256Hex(rawToken)
        val prefix = CredentialCrypto.tokenPrefix(rawToken)

        val payload = mutableMapOf<String, Any>(
            "userId" to userId,
            "name" to cleanName,
            "type" to type.wireValue,
            "status" to initialStatus.wireValue,
            "totalTasks" to 0L,
            "completedTasks" to 0L,
            "failedTasks" to 0L,
            "credentialPrefix" to prefix,
            "credentialHash" to tokenHash,
            "credentialRotatedAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!metadataSummary.isNullOrBlank()) {
            payload["metadataSummary"] = metadataSummary.take(500)
        }

        return try {
            docRef.set(payload).await()
            appendEvent(
                userId = userId,
                agentId = agentId,
                agentName = cleanName,
                taskId = null,
                project = null,
                eventType = EventType.AGENT_REGISTERED,
                title = "Registered $cleanName",
                message = "Agent $cleanName (${type.displayName}) registered with SHA-256 hashed MCP credentials.",
                idempotencyKey = "reg_${agentId}_${UUID.randomUUID().toString().take(8)}",
                deliveryStatus = "recorded"
            )
            val created = docRef.get().await()
                .toObject(Agent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: Agent(
                    id = agentId,
                    userId = userId,
                    name = cleanName,
                    type = type.wireValue,
                    status = initialStatus.wireValue,
                    credentialPrefix = prefix,
                    credentialHash = tokenHash,
                    metadataSummary = metadataSummary
                )
            Result.success(created to rawToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun renameAgent(userId: String, agentId: String, newName: String): Result<Unit> {
        val docRef = agentsCol(userId).document(agentId)
        val cleanName = newName.trim().take(80)
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Agent name cannot be blank"))
        return try {
            docRef.update(
                mapOf(
                    "name" to cleanName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun rotateAgentCredential(userId: String, agentId: String, agentName: String): Result<String> {
        val docRef = agentsCol(userId).document(agentId)
        val newToken = CredentialCrypto.generateAgentToken(agentId)
        val newHash = CredentialCrypto.sha256Hex(newToken)
        val newPrefix = CredentialCrypto.tokenPrefix(newToken)
        return try {
            docRef.update(
                mapOf(
                    "credentialPrefix" to newPrefix,
                    "credentialHash" to newHash,
                    "credentialRotatedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            appendEvent(
                userId = userId,
                agentId = agentId,
                agentName = agentName,
                taskId = null,
                project = null,
                eventType = EventType.CREDENTIAL_ROTATED,
                title = "Rotated credential for $agentName",
                message = "Generated new MCP bearer credential ($newPrefix). Previous credential invalidated.",
                idempotencyKey = "rot_${agentId}_${UUID.randomUUID().toString().take(8)}",
                deliveryStatus = "recorded"
            )
            Result.success(newToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun revokeAgent(userId: String, agentId: String, agentName: String): Result<Unit> {
        val docRef = agentsCol(userId).document(agentId)
        return try {
            docRef.update(
                mapOf(
                    "status" to AgentStatus.REVOKED.wireValue,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            appendEvent(
                userId = userId,
                agentId = agentId,
                agentName = agentName,
                taskId = null,
                project = null,
                eventType = EventType.AGENT_DISCONNECTED,
                title = "Revoked $agentName",
                message = "Agent connection and bearer credential were revoked by user.",
                idempotencyKey = "rev_${agentId}_${UUID.randomUUID().toString().take(8)}",
                deliveryStatus = "recorded"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteAgent(userId: String, agentId: String): Result<Unit> {
        val docRef = agentsCol(userId).document(agentId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeTasks(userId: String): Flow<List<AgentTask>> =
        tasksCol(userId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap ->
                snap.toObjects(AgentTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    suspend fun getTask(userId: String, taskId: String): Result<AgentTask?> {
        val docRef = tasksCol(userId).document(taskId)
        return try {
            val snap = docRef.get().await()
            val task = snap.toObject(AgentTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            Result.success(task)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun respondToUserInput(
        userId: String,
        taskId: String,
        responseText: String
    ): Result<Unit> {
        val docRef = tasksCol(userId).document(taskId)
        val cleanResp = responseText.trim().take(1000)
        if (cleanResp.isBlank()) return Result.failure(IllegalArgumentException("Response cannot be empty"))
        return try {
            val snap = docRef.get().await()
            val task = snap.toObject(AgentTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Task not found"))
            if (task.taskStatusEnum.isTerminal) {
                return Result.failure(IllegalStateException("Task is already in terminal state (${task.status})"))
            }
            docRef.update(
                mapOf(
                    "status" to TaskStatus.RUNNING.wireValue,
                    "userResponse" to cleanResp,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            appendEvent(
                userId = userId,
                agentId = task.agentId,
                agentName = task.agentName,
                taskId = taskId,
                project = task.project,
                eventType = EventType.USER_INPUT_RESPONDED,
                title = "User replied to ${task.title}",
                message = "Decision submitted: \"$cleanResp\"",
                idempotencyKey = "resp_${taskId}_${UUID.randomUUID().toString().take(8)}",
                deliveryStatus = "recorded"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun cancelTask(userId: String, taskId: String): Result<Unit> {
        val docRef = tasksCol(userId).document(taskId)
        return try {
            val snap = docRef.get().await()
            val task = snap.toObject(AgentTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Task not found"))
            if (task.taskStatusEnum.isTerminal) {
                return Result.failure(IllegalStateException("Task already finished (${task.status})"))
            }
            docRef.update(
                mapOf(
                    "status" to TaskStatus.CANCELLED.wireValue,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeEvents(userId: String): Flow<List<AgentEvent>> =
        eventsCol(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(200)
            .snapshots()
            .map { snap ->
                snap.toObjects(AgentEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    fun observeNotifications(userId: String): Flow<List<AppNotification>> =
        notificationsCol(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(150)
            .snapshots()
            .map { snap ->
                snap.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    suspend fun markNotificationRead(userId: String, notificationId: String): Result<Unit> {
        val docRef = notificationsCol(userId).document(notificationId)
        return try {
            docRef.update(
                mapOf(
                    "isRead" to true,
                    "readAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(userId: String, unreadIds: List<String>): Result<Unit> {
        if (unreadIds.isEmpty()) return Result.success(Unit)
        return try {
            unreadIds.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { id ->
                    batch.update(
                        notificationsCol(userId).document(id),
                        mapOf("isRead" to true, "readAt" to FieldValue.serverTimestamp())
                    )
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$userId/notifications")
            Result.failure(e)
        }
    }

    suspend fun deleteNotification(userId: String, notificationId: String): Result<Unit> {
        val docRef = notificationsCol(userId).document(notificationId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun hasIdempotencyKey(userId: String, idempotencyKey: String): Boolean {
        return try {
            val snap = eventsCol(userId)
                .whereEqualTo("idempotencyKey", idempotencyKey)
                .limit(1)
                .get()
                .await()
            !snap.isEmpty
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$userId/events")
            false
        }
    }

    suspend fun appendEvent(
        userId: String,
        agentId: String,
        agentName: String,
        taskId: String?,
        project: String?,
        eventType: EventType,
        title: String,
        message: String,
        idempotencyKey: String,
        deliveryStatus: String
    ): Result<String> {
        val docRef = eventsCol(userId).document()
        val payload = mutableMapOf<String, Any>(
            "userId" to userId,
            "agentId" to agentId,
            "agentName" to agentName.take(80),
            "eventType" to eventType.wireValue,
            "title" to title.take(160),
            "message" to message.take(2000),
            "idempotencyKey" to idempotencyKey.take(128),
            "deliveryStatus" to deliveryStatus,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (!taskId.isNullOrBlank()) payload["taskId"] = CredentialCrypto.sanitizeId(taskId, "task")
        if (!project.isNullOrBlank()) payload["project"] = project.take(120)

        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createNotification(
        userId: String,
        taskId: String?,
        eventId: String?,
        agentName: String?,
        project: String?,
        title: String,
        message: String,
        category: NotificationCategory
    ): Result<AppNotification> {
        val docRef = notificationsCol(userId).document()
        val payload = mutableMapOf<String, Any>(
            "userId" to userId,
            "title" to title.take(160),
            "message" to message.take(1000),
            "category" to category.wireValue,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (!taskId.isNullOrBlank()) payload["taskId"] = CredentialCrypto.sanitizeId(taskId, "task")
        if (!eventId.isNullOrBlank()) payload["eventId"] = CredentialCrypto.sanitizeId(eventId, "evt")
        if (!agentName.isNullOrBlank()) payload["agentName"] = agentName.take(80)
        if (!project.isNullOrBlank()) payload["project"] = project.take(120)

        return try {
            docRef.set(payload).await()
            val notif = AppNotification(
                id = docRef.id,
                userId = userId,
                taskId = taskId,
                eventId = eventId,
                agentName = agentName,
                project = project,
                title = title.take(160),
                message = message.take(1000),
                category = category.wireValue,
                isRead = false,
                createdAt = Timestamp.now()
            )
            Result.success(notif)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun upsertTaskFromMcp(
        userId: String,
        agent: Agent,
        taskId: String,
        title: String?,
        project: String?,
        status: TaskStatus,
        progress: Int,
        summary: String? = null,
        error: String? = null,
        pendingQuestion: String? = null,
        pendingContext: String? = null,
        pendingOptions: List<String> = emptyList()
    ): Result<AgentTask> {
        val cleanTaskId = CredentialCrypto.sanitizeId(taskId, "task_${UUID.randomUUID().toString().take(8)}")
        val docRef = tasksCol(userId).document(cleanTaskId)
        val agentRef = agentsCol(userId).document(agent.id)

        return try {
            val existingSnap = docRef.get().await()
            val existingTask = if (existingSnap.exists()) {
                existingSnap.toObject(AgentTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null

            if (existingTask != null && existingTask.taskStatusEnum.isTerminal) {
                return Result.failure(
                    IllegalStateException("Task $cleanTaskId is already in terminal state '${existingTask.status}'.")
                )
            }

            val resolvedTitle = title?.trim()?.take(160)?.ifBlank { null }
                ?: existingTask?.title
                ?: "Agent Task $cleanTaskId"
            val resolvedProject = project?.trim()?.take(120)?.ifBlank { null }
                ?: existingTask?.project
                ?: "workspace"
            val clampedProgress = progress.coerceIn(0, 100)

            if (existingTask == null) {
                val createMap = mutableMapOf<String, Any>(
                    "userId" to userId,
                    "agentId" to agent.id,
                    "agentName" to agent.name,
                    "title" to resolvedTitle,
                    "project" to resolvedProject,
                    "status" to status.wireValue,
                    "progress" to clampedProgress,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!summary.isNullOrBlank()) createMap["summary"] = summary.take(2000)
                if (!error.isNullOrBlank()) createMap["error"] = error.take(2000)
                if (!pendingQuestion.isNullOrBlank()) createMap["pendingQuestion"] = pendingQuestion.take(500)
                if (!pendingContext.isNullOrBlank()) createMap["pendingContext"] = pendingContext.take(1500)
                if (pendingOptions.isNotEmpty()) createMap["pendingOptions"] = pendingOptions.take(10)
                if (status == TaskStatus.COMPLETED) createMap["completedAt"] = FieldValue.serverTimestamp()

                docRef.set(createMap).await()
                val agentIncrements = mutableMapOf<String, Any>(
                    "status" to AgentStatus.CONNECTED.wireValue,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "totalTasks" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (status == TaskStatus.COMPLETED) agentIncrements["completedTasks"] = FieldValue.increment(1L)
                if (status == TaskStatus.FAILED) agentIncrements["failedTasks"] = FieldValue.increment(1L)
                agentRef.update(agentIncrements).await()
            } else {
                val updateMap = mutableMapOf<String, Any>(
                    "agentName" to agent.name,
                    "title" to resolvedTitle,
                    "project" to resolvedProject,
                    "status" to status.wireValue,
                    "progress" to clampedProgress,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!summary.isNullOrBlank()) updateMap["summary"] = summary.take(2000)
                if (!error.isNullOrBlank()) updateMap["error"] = error.take(2000)
                if (!pendingQuestion.isNullOrBlank()) updateMap["pendingQuestion"] = pendingQuestion.take(500)
                if (!pendingContext.isNullOrBlank()) updateMap["pendingContext"] = pendingContext.take(1500)
                if (pendingOptions.isNotEmpty()) updateMap["pendingOptions"] = pendingOptions.take(10)
                if (status == TaskStatus.COMPLETED) updateMap["completedAt"] = FieldValue.serverTimestamp()

                docRef.update(updateMap).await()
                val agentUpdates = mutableMapOf<String, Any>(
                    "status" to AgentStatus.CONNECTED.wireValue,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (status == TaskStatus.COMPLETED) agentUpdates["completedTasks"] = FieldValue.increment(1L)
                if (status == TaskStatus.FAILED) agentUpdates["failedTasks"] = FieldValue.increment(1L)
                agentRef.update(agentUpdates).await()
            }

            val updated = docRef.get().await()
                .toObject(AgentTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: AgentTask(
                    id = cleanTaskId,
                    userId = userId,
                    agentId = agent.id,
                    agentName = agent.name,
                    title = resolvedTitle,
                    project = resolvedProject,
                    status = status.wireValue,
                    progress = clampedProgress.toLong(),
                    summary = summary,
                    error = error
                )
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun setAgentConnectivity(
        userId: String,
        agentId: String,
        status: AgentStatus
    ): Result<Unit> {
        val docRef = agentsCol(userId).document(agentId)
        return try {
            docRef.update(
                mapOf(
                    "status" to status.wireValue,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }
}
