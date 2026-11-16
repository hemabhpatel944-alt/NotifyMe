package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.model.Agent
import com.example.model.AgentStatus
import com.example.model.AgentType
import com.example.ui.UiState
import com.example.ui.components.AgentStatusBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.LocalNotifymeColors
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentsScreen(
    agentsState: UiState<List<Agent>>,
    onRegisterAgent: (String, AgentType) -> Unit,
    onRenameAgent: (String, String) -> Unit,
    onRotateCredential: (Agent) -> Unit,
    onRevokeAgent: (Agent) -> Unit,
    onViewAgentActivity: (String) -> Unit
) {
    val c = LocalNotifymeColors.current
    var showRegisterDialog by remember { mutableStateOf(false) }
    var renamingAgent by remember { mutableStateOf<Agent?>(null) }

    val agents = (agentsState as? UiState.Success)?.data ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Connected AI Agents", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Manage per-agent SHA-256 credentials & sessions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showRegisterDialog = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("register_agent_button")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Register")
                }
            }
        }

        if (agentsState is UiState.Loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (agents.isEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                    EmptyStateCard(
                        icon = Icons.Outlined.SmartToy,
                        title = "No Agents Registered",
                        subtitle = "Register Google Antigravity, Claude Code, Codex, or a custom MCP agent to generate an authenticated bearer token.",
                        primaryLabel = "Register Antigravity",
                        onPrimaryClick = { showRegisterDialog = true }
                    )
                }
            }
        } else {
            items(agents, key = { it.id }) { agent ->
                Card(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .testTag("agent_card_${agent.id}"),
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(agent.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${agent.agentTypeEnum.displayName} • ID: ${agent.id}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                    color = c.cyanAccent
                                )
                            }
                            AgentStatusBadge(agent.agentStatusEnum)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            StatBit("Total", agent.totalTasks.toString())
                            StatBit("Completed", agent.completedTasks.toString())
                            StatBit("Failed", agent.failedTasks.toString())
                            StatBit(
                                "Last Seen",
                                if (agent.lastSeenAt != null) formatRelativeTime(agent.lastSeenAt) else "Never"
                            )
                        }

                        Text(
                            text = "Credential: ${agent.credentialPrefix} (SHA-256 stored)",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onViewAgentActivity(agent.id) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Activity")
                            }
                            OutlinedButton(
                                onClick = { renamingAgent = agent },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Rename")
                            }
                            if (agent.agentStatusEnum != AgentStatus.REVOKED) {
                                OutlinedButton(
                                    onClick = { onRotateCredential(agent) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Rotate Key")
                                }
                                OutlinedButton(
                                    onClick = { onRevokeAgent(agent) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Outlined.Block, contentDescription = null, tint = c.danger, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Revoke", color = c.danger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRegisterDialog) {
        var agentName by remember { mutableStateOf("Antigravity Primary") }
        var selectedType by remember { mutableStateOf(AgentType.ANTIGRAVITY) }

        AlertDialog(
            onDismissRequest = { showRegisterDialog = false },
            title = { Text("Register AI Agent") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = agentName,
                        onValueChange = { agentName = it },
                        label = { Text("Agent Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_agent_name_input")
                    )
                    Text("Agent Type", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AgentType.entries.forEach { t ->
                            FilterChip(
                                selected = selectedType == t,
                                onClick = { selectedType = t },
                                label = { Text(t.displayName) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (agentName.isNotBlank()) {
                            onRegisterAgent(agentName, selectedType)
                            showRegisterDialog = false
                        }
                    },
                    enabled = agentName.isNotBlank(),
                    modifier = Modifier.testTag("confirm_register_agent_button")
                ) {
                    Text("Create & Generate Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegisterDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    renamingAgent?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renamingAgent = null },
            title = { Text("Rename Agent") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Agent Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRenameAgent(target.id, newName)
                            renamingAgent = null
                        }
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingAgent = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatBit(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
