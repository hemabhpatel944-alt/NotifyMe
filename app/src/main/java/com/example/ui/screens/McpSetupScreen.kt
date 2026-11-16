package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.mcp.McpServerStatus
import com.example.model.Agent
import com.example.model.AgentStatus
import com.example.model.AgentType
import com.example.ui.UiState
import com.example.ui.components.CodeSnippetCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalNotifymeColors
import com.example.ui.theme.MonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun McpSetupScreen(
    mcpStatus: McpServerStatus,
    lanBinding: Boolean,
    agentsState: UiState<List<Agent>>,
    sessionTokens: Map<String, String>,
    lastRpcOutput: String?,
    isSendingTestRpc: Boolean,
    onToggleServer: (Boolean) -> Unit,
    onUpdateNetworkSettings: (Int, Boolean) -> Unit,
    onRegisterAntigravity: () -> Unit,
    onRotateCredential: (Agent) -> Unit,
    onSendLiveTestCall: (agentId: String, toolName: String, taskId: String, title: String, project: String, detailText: String, progress: Int) -> Unit,
    onCopied: (String) -> Unit
) {
    val c = LocalNotifymeColors.current
    val agents = (agentsState as? UiState.Success)?.data ?: emptyList()
    val activeAgents = agents.filter { it.agentStatusEnum != AgentStatus.REVOKED }

    var selectedAgentId by remember(activeAgents) {
        mutableStateOf(
            activeAgents.firstOrNull { it.agentTypeEnum == AgentType.ANTIGRAVITY }?.id
                ?: activeAgents.firstOrNull()?.id
        )
    }
    val selectedAgent = activeAgents.firstOrNull { it.id == selectedAgentId }
    val activeSessionToken = selectedAgent?.let { sessionTokens[it.id] }

    var selectedTool by remember { mutableStateOf("notify_task_completed") }
    var testTaskId by remember { mutableStateOf("task_${UUID.randomUUID().toString().take(6)}") }
    var testTitle by remember { mutableStateOf("Authentication System") }
    var testProject by remember { mutableStateOf("notifyme-core") }
    var testDetail by remember { mutableStateOf("Implemented Google Sign-In and SHA-256 MCP authentication.") }
    var testProgress by remember { mutableFloatStateOf(50f) }

    val configJson = remember(mcpStatus.displayEndpoint, activeSessionToken, selectedAgent) {
        val tokenDisplay = activeSessionToken ?: "\${NOTIFYME_AGENT_TOKEN}"
        val agentIdComment = selectedAgent?.id ?: "your_agent_id"
        """
{
  "mcpServers": {
    "notifyme": {
      "transport": "streamable-http",
      "url": "${mcpStatus.displayEndpoint}",
      "headers": {
        "Authorization": "Bearer $tokenDisplay",
        "X-Notifyme-Agent-Id": "$agentIdComment"
      }
    }
  }
}
        """.trimIndent()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Live MCP Server Control & Status
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
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Embedded MCP Server", style = MaterialTheme.typography.titleMedium)
                            StatusPill(
                                label = if (mcpStatus.isRunning) "Listening on ${mcpStatus.displayEndpoint}" else "Stopped",
                                textColor = if (mcpStatus.isRunning) c.success else c.neutral,
                                bgColor = if (mcpStatus.isRunning) c.successBg else c.neutralBg
                            )
                        }
                        Switch(
                            checked = mcpStatus.isRunning,
                            onCheckedChange = onToggleServer,
                            modifier = Modifier.testTag("mcp_server_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Expose on Local Wi-Fi / LAN", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Binds 0.0.0.0:${mcpStatus.port} so Antigravity on your workstation can call this device over LAN.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = lanBinding,
                            onCheckedChange = { onUpdateNetworkSettings(mcpStatus.port, it) }
                        )
                    }

                    val lastConn = mcpStatus.lastSuccessfulConnectionAtMillis?.let { ms ->
                        SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(ms))
                    } ?: "No MCP calls received in this session yet"

                    Text(
                        text = "Last successful connection: $lastConn • Requests handled: ${mcpStatus.requestsHandled}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Credential Selection / Generation
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
                    Text("1. Agent Credentials", style = MaterialTheme.typography.titleMedium)
                    if (activeAgents.isEmpty()) {
                        Text(
                            "Register Google Antigravity first to generate a SHA-256 authenticated bearer token.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onRegisterAntigravity,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("mcp_quick_register_antigravity")
                        ) {
                            Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create Antigravity Agent & Token")
                        }
                    } else {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeAgents.forEach { agent ->
                                FilterChip(
                                    selected = selectedAgent?.id == agent.id,
                                    onClick = { selectedAgentId = agent.id },
                                    label = { Text(agent.name) }
                                )
                            }
                        }
                        selectedAgent?.let { ag ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (activeSessionToken != null) {
                                        "Active session token ready (injected below)"
                                    } else {
                                        "Prefix ${ag.credentialPrefix} • Rotate key to reveal a fresh token"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (activeSessionToken != null) c.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { onRotateCredential(ag) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("mcp_rotate_key_button")
                                ) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Rotate")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Generated Antigravity MCP Config
        item {
            Column(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("2. Antigravity MCP Configuration", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Add this server entry to your Antigravity MCP configuration file (or store your bearer token in NOTIFYME_AGENT_TOKEN).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CodeSnippetCard(
                    title = "antigravity_mcp_config.json",
                    code = configJson,
                    onCopied = onCopied,
                    copyButtonTag = "copy_antigravity_config_button"
                )
            }
        }

        // 4. Antigravity Agent Instruction Template
        item {
            Column(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("3. Antigravity Agent Instruction Template", style = MaterialTheme.typography.titleMedium)
                Text(
                    "MCP tool calls are initiated by your agent. Add these instructions to your Antigravity rules/prompt so it reports lifecycle events accurately:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CodeSnippetCard(
                    title = "antigravity_instructions.md",
                    code = ANTIGRAVITY_INSTRUCTION_TEMPLATE,
                    onCopied = onCopied,
                    copyButtonTag = "copy_agent_instructions_button"
                )
            }
        }

        // 5. Live End-to-End HTTP MCP Test Dispatch
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
                    Text("4. Live HTTP MCP Test Runner", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Sends a real JSON-RPC 2.0 POST request with Bearer auth to http://127.0.0.1:${mcpStatus.port}/mcp, writes to Firestore, and fires notifications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val tools = listOf(
                        "notify_task_completed",
                        "update_task_progress",
                        "request_user_input",
                        "notify_task_failed",
                        "notify_agent_connected",
                        "notify_agent_disconnected"
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tools.forEach { t ->
                            FilterChip(
                                selected = selectedTool == t,
                                onClick = { selectedTool = t },
                                label = { Text(t) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = testTaskId,
                            onValueChange = { testTaskId = it },
                            label = { Text("task_id") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = testProject,
                            onValueChange = { testProject = it },
                            label = { Text("project") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = testTitle,
                        onValueChange = { testTitle = it },
                        label = { Text("title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = testDetail,
                        onValueChange = { testDetail = it },
                        label = {
                            Text(
                                when (selectedTool) {
                                    "notify_task_failed" -> "error"
                                    "request_user_input" -> "question"
                                    "notify_agent_disconnected" -> "reason"
                                    else -> "summary / message"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (selectedTool == "update_task_progress") {
                        Text("Progress: ${testProgress.toInt()}%", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = testProgress,
                            onValueChange = { testProgress = it },
                            valueRange = 0f..100f
                        )
                    }

                    Button(
                        onClick = {
                            val ag = selectedAgent ?: return@Button
                            onSendLiveTestCall(
                                ag.id,
                                selectedTool,
                                testTaskId,
                                testTitle,
                                testProject,
                                testDetail,
                                testProgress.toInt()
                            )
                            testTaskId = "task_${UUID.randomUUID().toString().take(6)}"
                        },
                        enabled = selectedAgent != null && activeSessionToken != null && mcpStatus.isRunning && !isSendingTestRpc,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("send_live_mcp_test_button")
                    ) {
                        if (isSendingTestRpc) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Dispatching...")
                        } else {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Send Live MCP Tool Call")
                        }
                    }

                    if (lastRpcOutput != null) {
                        CodeSnippetCard(
                            title = "JSON-RPC Response",
                            code = lastRpcOutput,
                            onCopied = onCopied
                        )
                    }
                }
            }
        }

        // 6. Troubleshooting Guide
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Troubleshooting Guide", style = MaterialTheme.typography.titleMedium)
                    }
                    TroubleshootItem(
                        title = "MCP server connection failures",
                        detail = "Confirm the Embedded MCP Server toggle is ON and port ${mcpStatus.port} is reachable. If calling from a workstation, enable 'Expose on Local Wi-Fi / LAN' and verify both devices share the same network."
                    )
                    TroubleshootItem(
                        title = "Authentication errors (401 / 403)",
                        detail = "Verify the Authorization header uses 'Bearer <token>' from an active, non-revoked agent. Rotating a key immediately invalidates previous tokens."
                    )
                    TroubleshootItem(
                        title = "Invalid payloads (400 / -32602)",
                        detail = "Ensure progress is 0..100, task_id/title/project are non-empty strings, and total JSON-RPC body size is under 64 KB."
                    )
                    TroubleshootItem(
                        title = "Notification permission issues",
                        detail = "System push alerts require enabling Push Notifications in Settings and granting Android POST_NOTIFICATIONS permission."
                    )
                    TroubleshootItem(
                        title = "Disconnected or stale agents",
                        detail = "Have Antigravity invoke notify_agent_connected on session start and notify_agent_disconnected on shutdown."
                    )
                }
            }
        }
    }
}

@Composable
private fun TroubleshootItem(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
