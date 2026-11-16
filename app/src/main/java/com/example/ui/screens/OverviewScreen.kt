package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mcp.McpServerStatus
import com.example.model.Agent
import com.example.model.AgentEvent
import com.example.model.AgentStatus
import com.example.model.AgentTask
import com.example.model.AppNotification
import com.example.model.TaskStatus
import com.example.ui.UiState
import com.example.ui.components.AgentStatusBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MetricStatCard
import com.example.ui.components.StatusPill
import com.example.ui.components.TaskStatusBadge
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.LocalNotifymeColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun OverviewScreen(
    tasksState: UiState<List<AgentTask>>,
    agentsState: UiState<List<Agent>>,
    eventsState: UiState<List<AgentEvent>>,
    notificationsState: UiState<List<AppNotification>>,
    mcpStatus: McpServerStatus,
    demoMode: Boolean,
    onOpenTask: (String) -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToAgents: () -> Unit,
    onNavigateToMcpSetup: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToActivity: () -> Unit,
    onEnableDemoMode: () -> Unit
) {
    val c = LocalNotifymeColors.current

    val tasks = (tasksState as? UiState.Success)?.data ?: emptyList()
    val agents = (agentsState as? UiState.Success)?.data ?: emptyList()
    val events = (eventsState as? UiState.Success)?.data ?: emptyList()
    val notifications = (notificationsState as? UiState.Success)?.data ?: emptyList()

    val isLoading = tasksState is UiState.Loading && agentsState is UiState.Loading

    val totalCount = tasks.size
    val activeCount = tasks.count {
        it.taskStatusEnum == TaskStatus.RUNNING ||
            it.taskStatusEnum == TaskStatus.PENDING ||
            it.taskStatusEnum == TaskStatus.WAITING_FOR_INPUT
    }
    val completedCount = tasks.count { it.taskStatusEnum == TaskStatus.COMPLETED }
    val failedCount = tasks.count { it.taskStatusEnum == TaskStatus.FAILED }

    val connectedAgents = agents.filter { it.agentStatusEnum == AgentStatus.CONNECTED }
    val waitingTasks = tasks.filter { it.taskStatusEnum == TaskStatus.WAITING_FOR_INPUT }
    val recentCompleted = tasks.filter { it.taskStatusEnum == TaskStatus.COMPLETED }.take(3)
    val latestNotifications = notifications.take(3)
    val recentEvents = events.take(5)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Real-time MCP & Firestore Connection Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, c.subtleBorder),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Real-Time Telemetry",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                StatusPill(
                                    label = if (mcpStatus.isRunning) "MCP Listening" else "MCP Offline",
                                    textColor = if (mcpStatus.isRunning) c.success else c.neutral,
                                    bgColor = if (mcpStatus.isRunning) c.successBg else c.neutralBg
                                )
                            }
                            Text(
                                text = if (mcpStatus.isRunning) {
                                    "Endpoint ${mcpStatus.displayEndpoint} • ${connectedAgents.size} agent(s) connected"
                                } else {
                                    "Start the embedded MCP server in MCP Setup to accept agent calls."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = onNavigateToMcpSetup,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("overview_mcp_setup_button")
                        ) {
                            Icon(Icons.Outlined.Hub, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("MCP")
                        }
                    }
                }

                // 2x2 Stats Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "Total Tasks",
                        value = totalCount.toString(),
                        accentColor = MaterialTheme.colorScheme.primary,
                        icon = Icons.Outlined.TaskAlt,
                        onClick = onNavigateToTasks,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Active Tasks",
                        value = activeCount.toString(),
                        accentColor = c.info,
                        icon = Icons.Outlined.PendingActions,
                        onClick = onNavigateToTasks,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "Completed",
                        value = completedCount.toString(),
                        accentColor = c.success,
                        icon = Icons.Outlined.CheckCircle,
                        onClick = onNavigateToTasks,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Failed",
                        value = failedCount.toString(),
                        accentColor = c.danger,
                        icon = Icons.Outlined.ErrorOutline,
                        onClick = onNavigateToTasks,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (agents.isEmpty() && tasks.isEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                    EmptyStateCard(
                        icon = Icons.Outlined.Hub,
                        title = "No AI Agents Connected Yet",
                        subtitle = "Register Google Antigravity or another MCP agent, copy your configuration, and start receiving live task notifications.",
                        primaryLabel = "Connect Antigravity",
                        onPrimaryClick = onNavigateToMcpSetup,
                        secondaryLabel = if (!demoMode) "Preview Demo Data" else null,
                        onSecondaryClick = if (!demoMode) onEnableDemoMode else null
                    )
                }
            }
        } else {
            // Waiting for User Input Banner
            if (waitingTasks.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SectionTitleRow("Decision Required", null, null)
                        waitingTasks.forEach { task ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenTask(task.id) },
                                colors = CardDefaults.cardColors(containerColor = c.warningBg),
                                border = BorderStroke(1.dp, c.warning.copy(alpha = 0.45f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = c.warning, modifier = Modifier.size(18.dp))
                                        Text(task.title, style = MaterialTheme.typography.titleSmall)
                                    }
                                    Text(
                                        text = task.pendingQuestion ?: "Agent is waiting for your decision.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = { onOpenTask(task.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text("Respond")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Active Agent Sessions
            item {
                Column(
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionTitleRow("Agent Sessions", "Manage", onNavigateToAgents)
                    if (agents.isEmpty()) {
                        Text(
                            "No agents registered yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        agents.take(3).forEach { agent ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToAgents() },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, c.subtleBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Outlined.SmartToy,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column {
                                            Text(agent.name, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "${agent.agentTypeEnum.displayName} • ${agent.totalTasks} tasks",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    AgentStatusBadge(agent.agentStatusEnum)
                                }
                            }
                        }
                    }
                }
            }

            // Recently Completed Tasks
            if (recentCompleted.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SectionTitleRow("Recently Completed", "All Tasks", onNavigateToTasks)
                        recentCompleted.forEach { task ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenTask(task.id) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, c.subtleBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            task.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        TaskStatusBadge(task.taskStatusEnum)
                                    }
                                    Text(
                                        "${task.agentName} • ${task.project} • ${formatRelativeTime(task.completedAt ?: task.updatedAt)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!task.summary.isNullOrBlank()) {
                                        Text(
                                            task.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Latest Notifications
            if (latestNotifications.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SectionTitleRow("Latest Notifications", "View All", onNavigateToNotifications)
                        latestNotifications.forEach { notif ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!notif.taskId.isNullOrBlank()) onOpenTask(notif.taskId)
                                        else onNavigateToNotifications()
                                    },
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, c.subtleBorder),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Outlined.Notifications,
                                        contentDescription = null,
                                        tint = if (!notif.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(notif.title, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            notif.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        formatRelativeTime(notif.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Recent Activity Timeline
            if (recentEvents.isNotEmpty()) {
                item {
                    SectionTitleRow("Recent Activity", "Timeline", onNavigateToActivity)
                }
                items(recentEvents, key = { it.id }) { evt ->
                    Surface(
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, c.subtleBorder),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(evt.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${evt.agentName} • ${evt.eventTypeEnum.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                formatRelativeTime(evt.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitleRow(
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
