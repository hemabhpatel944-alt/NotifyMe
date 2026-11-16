package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.AgentEvent
import com.example.model.AgentTask
import com.example.model.TaskStatus
import com.example.ui.components.TaskStatusBadge
import com.example.ui.components.formatExactTime
import com.example.ui.theme.LocalNotifymeColors
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(
    task: AgentTask?,
    events: List<AgentEvent>,
    onBack: () -> Unit,
    onRespondToInput: (String, String) -> Unit,
    onCancelTask: (String) -> Unit
) {
    val c = LocalNotifymeColors.current
    var replyText by remember(task?.id) { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(task?.title ?: "Task Details", maxLines = 1) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("task_detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (task == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Task not found or was deleted.", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            val taskEvents = remember(events, task.id) {
                events.filter { it.taskId == task.id }
                    .sortedBy { it.createdAt?.toDate()?.time ?: 0L }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Card(
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, c.subtleBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${task.id}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                    color = c.cyanAccent
                                )
                                TaskStatusBadge(task.taskStatusEnum)
                            }
                            Text(task.title, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "${task.agentName} • Project: ${task.project}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(
                                progress = { task.progressInt / 100f },
                                modifier = Modifier.fillMaxWidth().height(7.dp)
                            )
                            Text(
                                "Progress: ${task.progressInt}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!task.summary.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = task.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            if (!task.error.isNullOrBlank()) {
                                Surface(
                                    color = c.dangerBg,
                                    border = BorderStroke(1.dp, c.danger.copy(alpha = 0.4f)),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "Error: ${task.error}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = c.danger,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Created: ${formatExactTime(task.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Updated: ${formatExactTime(task.updatedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (task.completedAt != null) {
                                    Text(
                                        "Completed: ${formatExactTime(task.completedAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = c.success
                                    )
                                }
                            }

                            if (!task.taskStatusEnum.isTerminal) {
                                OutlinedButton(
                                    onClick = { onCancelTask(task.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Outlined.Cancel, contentDescription = null)
                                    Spacer(Modifier.padding(horizontal = 4.dp))
                                    Text("Cancel Task")
                                }
                            }
                        }
                    }
                }

                // Interactive User Input Prompt
                if (task.taskStatusEnum == TaskStatus.WAITING_FOR_INPUT) {
                    item {
                        Card(
                            modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = c.warningBg),
                            border = BorderStroke(1.dp, c.warning.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = c.warning)
                                    Text("Agent Waiting for Decision", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    task.pendingQuestion ?: "Please provide guidance to continue.",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (!task.pendingContext.isNullOrBlank()) {
                                    Text(
                                        task.pendingContext,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (task.pendingOptions.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        task.pendingOptions.forEach { opt ->
                                            SuggestionChip(
                                                onClick = { replyText = opt },
                                                label = { Text(opt) }
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = replyText,
                                    onValueChange = { replyText = it },
                                    label = { Text("Your decision") },
                                    placeholder = { Text("Select an option or enter instructions...") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("task_user_input_field")
                                )
                                Button(
                                    onClick = {
                                        if (replyText.isNotBlank()) {
                                            onRespondToInput(task.id, replyText)
                                            replyText = ""
                                        }
                                    },
                                    enabled = replyText.isNotBlank(),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("submit_user_input_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                                    Spacer(Modifier.padding(horizontal = 4.dp))
                                    Text("Submit Decision")
                                }
                            }
                        }
                    }
                } else if (!task.userResponse.isNullOrBlank()) {
                    item {
                        Surface(
                            modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                            color = c.successBg,
                            border = BorderStroke(1.dp, c.success.copy(alpha = 0.4f)),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = "User decision recorded: \"${task.userResponse}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.success,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Task Activity Timeline (${taskEvents.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()
                    )
                }

                if (taskEvents.isEmpty()) {
                    item {
                        Text(
                            text = "No events recorded for this task yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()
                        )
                    }
                } else {
                    items(taskEvents, key = { it.id }) { evt ->
                        Card(
                            modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, c.subtleBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(evt.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text(
                                        evt.deliveryStatus.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = c.cyanAccent
                                    )
                                }
                                Text(evt.message, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${evt.eventTypeEnum.displayName} • ${formatExactTime(evt.createdAt)}",
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
}
