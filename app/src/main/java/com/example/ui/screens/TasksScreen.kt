package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Agent
import com.example.model.AgentTask
import com.example.model.TaskStatus
import com.example.ui.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.TaskStatusBadge
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.LocalNotifymeColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun TasksScreen(
    tasksState: UiState<List<AgentTask>>,
    agentsState: UiState<List<Agent>>,
    onOpenTask: (String) -> Unit,
    onNavigateToMcpSetup: () -> Unit
) {
    val c = LocalNotifymeColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<TaskStatus?>(null) }
    var selectedAgentId by remember { mutableStateOf<String?>(null) }
    var sortNewestFirst by remember { mutableStateOf(true) }

    val allTasks = (tasksState as? UiState.Success)?.data ?: emptyList()
    val allAgents = (agentsState as? UiState.Success)?.data ?: emptyList()

    val filteredTasks = remember(allTasks, searchQuery, selectedStatus, selectedAgentId, sortNewestFirst) {
        val q = searchQuery.trim().lowercase()
        val list = allTasks.filter { task ->
            val matchesQuery = q.isBlank() ||
                task.title.lowercase().contains(q) ||
                task.project.lowercase().contains(q) ||
                task.id.lowercase().contains(q) ||
                task.agentName.lowercase().contains(q)
            val matchesStatus = selectedStatus == null || task.taskStatusEnum == selectedStatus
            val matchesAgent = selectedAgentId == null || task.agentId == selectedAgentId
            matchesQuery && matchesStatus && matchesAgent
        }
        if (sortNewestFirst) {
            list.sortedByDescending { (it.updatedAt ?: it.createdAt)?.toDate()?.time ?: 0L }
        } else {
            list.sortedBy { (it.updatedAt ?: it.createdAt)?.toDate()?.time ?: 0L }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search tasks, IDs, projects...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tasks_search_input")
                    )
                    OutlinedButton(
                        onClick = { sortNewestFirst = !sortNewestFirst },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tasks_sort_button")
                    ) {
                        Icon(Icons.Outlined.SwapVert, contentDescription = "Sort order")
                        Text(if (sortNewestFirst) "Newest" else "Oldest")
                    }
                }

                // Status Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedStatus == null,
                        onClick = { selectedStatus = null },
                        label = { Text("All (${allTasks.size})") }
                    )
                    TaskStatus.entries.forEach { status ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = if (selectedStatus == status) null else status },
                            label = { Text(status.displayName) }
                        )
                    }
                }

                // Agent Filter Chips
                if (allAgents.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedAgentId == null,
                            onClick = { selectedAgentId = null },
                            label = { Text("All Agents") }
                        )
                        allAgents.forEach { agent ->
                            FilterChip(
                                selected = selectedAgentId == agent.id,
                                onClick = {
                                    selectedAgentId = if (selectedAgentId == agent.id) null else agent.id
                                },
                                label = { Text(agent.name) }
                            )
                        }
                    }
                }
            }
        }

        if (tasksState is UiState.Loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (filteredTasks.isEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                    EmptyStateCard(
                        icon = Icons.Outlined.TaskAlt,
                        title = if (allTasks.isEmpty()) "No tasks reported yet" else "No matching tasks",
                        subtitle = if (allTasks.isEmpty()) {
                            "Tasks appear automatically here when your connected AI agent calls update_task_progress, request_user_input, notify_task_completed, or notify_task_failed."
                        } else {
                            "Try clearing your search or status filter."
                        },
                        primaryLabel = if (allTasks.isEmpty()) "Open MCP Setup" else null,
                        onPrimaryClick = if (allTasks.isEmpty()) onNavigateToMcpSetup else null
                    )
                }
            }
        } else {
            items(filteredTasks, key = { it.id }) { task ->
                Card(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .clickable { onOpenTask(task.id) }
                        .testTag("task_card_${task.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, c.subtleBorder),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            TaskStatusBadge(task.taskStatusEnum)
                        }

                        Text(
                            text = "${task.agentName} • ${task.project} • #${task.id}",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                            color = c.cyanAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (task.taskStatusEnum == TaskStatus.RUNNING || task.progressInt in 1..99) {
                            LinearProgressIndicator(
                                progress = { task.progressInt / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )
                        }

                        val detailPreview = task.error ?: task.pendingQuestion ?: task.summary
                        if (!detailPreview.isNullOrBlank()) {
                            Text(
                                text = detailPreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (task.taskStatusEnum == TaskStatus.FAILED) c.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Progress ${task.progressInt}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Updated ${formatRelativeTime(task.updatedAt ?: task.createdAt)}",
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
