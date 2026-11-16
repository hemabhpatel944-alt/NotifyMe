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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.Agent
import com.example.model.AgentEvent
import com.example.model.EventType
import com.example.ui.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatExactTime
import com.example.ui.theme.LocalNotifymeColors
import com.example.ui.theme.MonoFontFamily

enum class DateWindowFilter(val label: String, val cutoffMs: Long?) {
    ALL("All Time", null),
    TODAY("Last 24h", 24L * 60 * 60 * 1000),
    WEEK("Last 7d", 7L * 24 * 60 * 60 * 1000)
}

@Composable
fun ActivityScreen(
    eventsState: UiState<List<AgentEvent>>,
    agentsState: UiState<List<Agent>>,
    initialAgentFilterId: String? = null,
    onOpenTask: (String) -> Unit
) {
    val c = LocalNotifymeColors.current
    var selectedAgentId by remember(initialAgentFilterId) { mutableStateOf(initialAgentFilterId) }
    var selectedProject by remember { mutableStateOf<String?>(null) }
    var selectedEventType by remember { mutableStateOf<EventType?>(null) }
    var selectedDateWindow by remember { mutableStateOf(DateWindowFilter.ALL) }

    val allEvents = (eventsState as? UiState.Success)?.data ?: emptyList()
    val allAgents = (agentsState as? UiState.Success)?.data ?: emptyList()
    val projects = remember(allEvents) {
        allEvents.mapNotNull { it.project?.takeIf { p -> p.isNotBlank() } }.distinct().sorted()
    }

    val filteredEvents = remember(allEvents, selectedAgentId, selectedProject, selectedEventType, selectedDateWindow) {
        val now = System.currentTimeMillis()
        allEvents.filter { evt ->
            val matchAgent = selectedAgentId == null || evt.agentId == selectedAgentId
            val matchProject = selectedProject == null || evt.project == selectedProject
            val matchType = selectedEventType == null || evt.eventTypeEnum == selectedEventType
            val matchDate = selectedDateWindow.cutoffMs == null ||
                ((evt.createdAt?.toDate()?.time ?: now) >= now - selectedDateWindow.cutoffMs!!)
            matchAgent && matchProject && matchType && matchDate
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Date range chips
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateWindowFilter.entries.forEach { window ->
                        FilterChip(
                            selected = selectedDateWindow == window,
                            onClick = { selectedDateWindow = window },
                            label = { Text(window.label) }
                        )
                    }
                }

                // Event type chips
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedEventType == null,
                        onClick = { selectedEventType = null },
                        label = { Text("All Events") }
                    )
                    EventType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedEventType == type,
                            onClick = { selectedEventType = if (selectedEventType == type) null else type },
                            label = { Text(type.displayName) }
                        )
                    }
                }

                // Agent & Project filter chips
                if (allAgents.isNotEmpty() || projects.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                                onClick = { selectedAgentId = if (selectedAgentId == agent.id) null else agent.id },
                                label = { Text(agent.name) }
                            )
                        }
                        projects.forEach { proj ->
                            FilterChip(
                                selected = selectedProject == proj,
                                onClick = { selectedProject = if (selectedProject == proj) null else proj },
                                label = { Text("Project: $proj") }
                            )
                        }
                    }
                }
            }
        }

        if (eventsState is UiState.Loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (filteredEvents.isEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                    EmptyStateCard(
                        icon = Icons.Outlined.History,
                        title = "No activity events match",
                        subtitle = "Chronological MCP tool calls, task transitions, and notification deliveries appear here."
                    )
                }
            }
        } else {
            items(filteredEvents, key = { it.id }) { evt ->
                val (fg, bg) = when (evt.eventTypeEnum) {
                    EventType.TASK_COMPLETED -> c.success to c.successBg
                    EventType.TASK_FAILED -> c.danger to c.dangerBg
                    EventType.USER_INPUT_REQUESTED -> c.warning to c.warningBg
                    EventType.AGENT_CONNECTED -> c.success to c.successBg
                    EventType.AGENT_DISCONNECTED -> c.neutral to c.neutralBg
                    else -> c.info to c.infoBg
                }

                Card(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .then(
                            if (!evt.taskId.isNullOrBlank()) Modifier.clickable { onOpenTask(evt.taskId) }
                            else Modifier
                        ),
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
                            StatusPill(
                                label = evt.eventTypeEnum.displayName,
                                textColor = fg,
                                bgColor = bg
                            )
                            Text(
                                text = "Delivery: ${evt.deliveryStatus}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(evt.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            evt.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = listOfNotNull(evt.agentName, evt.project).joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = c.cyanAccent
                            )
                            Text(
                                text = formatExactTime(evt.createdAt),
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
