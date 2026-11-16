package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.Agent
import com.example.model.UserProfile
import com.example.notifications.SystemNotificationDispatcher
import com.example.ui.UiState
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalNotifymeColors

@Composable
fun SettingsScreen(
    profileState: UiState<UserProfile>,
    agentsState: UiState<List<Agent>>,
    demoMode: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onUpdatePref: (String, Boolean) -> Unit,
    onToggleDemoMode: (Boolean) -> Unit,
    onManageAgents: () -> Unit,
    onSignOut: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val c = LocalNotifymeColors.current
    val profile = (profileState as? UiState.Success)?.data ?: UserProfile()
    val agents = (agentsState as? UiState.Success)?.data ?: emptyList()

    var hasOsPermission by remember {
        mutableStateOf(SystemNotificationDispatcher.hasNotificationPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasOsPermission = granted
        if (granted) {
            onUpdatePref("pushEnabled", true)
            onMessage("System push notifications enabled")
        } else {
            onUpdatePref("pushEnabled", false)
            onMessage("Notification permission denied; in-app notifications remain active.")
        }
    }

    val isDark = profile.themeMode != "light"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Section
        item {
            SettingsSectionCard(icon = Icons.Outlined.Person, title = "Profile") {
                Text(
                    text = profile.displayName.ifBlank { "Developer" },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = profile.email.ifBlank { "Signed in with Google" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Workspace UID: ${profile.userId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.cyanAccent
                )
            }
        }

        // Appearance Section
        item {
            SettingsSectionCard(icon = Icons.Outlined.DarkMode, title = "Appearance") {
                PreferenceSwitchRow(
                    title = "Dark Mode (Default)",
                    subtitle = "Obsidian developer theme with high-contrast syntax accents",
                    checked = isDark,
                    onCheckedChange = onToggleTheme,
                    testTag = "settings_dark_mode_switch"
                )
            }
        }

        // Notification Preferences Section
        item {
            SettingsSectionCard(icon = Icons.Outlined.NotificationsActive, title = "Notification Preferences") {
                PreferenceSwitchRow(
                    title = "Android Push Notifications",
                    subtitle = if (hasOsPermission) "Permission granted • fires OS alerts on MCP events"
                    else "Requests Android notification permission only when enabled",
                    checked = profile.pushEnabled && hasOsPermission,
                    onCheckedChange = { enable ->
                        if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !SystemNotificationDispatcher.hasNotificationPermission(context)
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            hasOsPermission = SystemNotificationDispatcher.hasNotificationPermission(context)
                            onUpdatePref("pushEnabled", enable)
                        }
                    },
                    testTag = "settings_push_switch"
                )
                HorizontalDivider(color = c.subtleBorder)
                PreferenceSwitchRow(
                    title = "Task Completed",
                    subtitle = "Alert when notify_task_completed fires",
                    checked = profile.notifyOnCompleted,
                    onCheckedChange = { onUpdatePref("notifyOnCompleted", it) }
                )
                PreferenceSwitchRow(
                    title = "Task Failed",
                    subtitle = "Alert immediately on agent errors",
                    checked = profile.notifyOnFailed,
                    onCheckedChange = { onUpdatePref("notifyOnFailed", it) }
                )
                PreferenceSwitchRow(
                    title = "User Input Required",
                    subtitle = "Alert when an agent awaits your decision",
                    checked = profile.notifyOnInputRequired,
                    onCheckedChange = { onUpdatePref("notifyOnInputRequired", it) }
                )
                PreferenceSwitchRow(
                    title = "Task Progress Updates",
                    subtitle = "Generate notifications for incremental progress updates",
                    checked = profile.notifyOnProgress,
                    onCheckedChange = { onUpdatePref("notifyOnProgress", it) }
                )
                PreferenceSwitchRow(
                    title = "Agent Connected / Disconnected",
                    subtitle = "Notify when MCP sessions start or close",
                    checked = profile.notifyOnAgentConnected,
                    onCheckedChange = {
                        onUpdatePref("notifyOnAgentConnected", it)
                        onUpdatePref("notifyOnAgentDisconnected", it)
                    }
                )
            }
        }

        // Connected Devices & FCM Readiness
        item {
            SettingsSectionCard(icon = Icons.Outlined.Devices, title = "Connected Devices & Push Channels") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Local Android Device", style = MaterialTheme.typography.titleSmall)
                    StatusPill(
                        label = if (hasOsPermission && profile.pushEnabled) "Active" else "Standby",
                        textColor = if (hasOsPermission && profile.pushEnabled) c.success else c.neutral,
                        bgColor = if (hasOsPermission && profile.pushEnabled) c.successBg else c.neutralBg
                    )
                }
                Text(
                    text = "In-app real-time notifications and local Android system notifications are active. Remote Firebase Cloud Messaging (FCM) background push requires configuring FCM credentials and a cloud dispatch function in the Firebase Console.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Security & Agent Connections
        item {
            SettingsSectionCard(icon = Icons.Outlined.Security, title = "Security & Agent Connections") {
                Text(
                    text = "• Per-agent SHA-256 hashed credentials\n• Strict Firestore user-ownership isolation rules\n• Sliding-window rate limiting (60 req/min) & 64 KB payload cap\n• Idempotent event deduplication",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onManageAgents,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Outlined.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Manage ${agents.size} Agent(s)")
                }
            }
        }

        // Demo Mode Toggle
        item {
            SettingsSectionCard(icon = Icons.Outlined.Science, title = "Developer Preview") {
                PreferenceSwitchRow(
                    title = "Demo Mode (Sample Data)",
                    subtitle = "Clearly badged sample telemetry. Never fakes live agent connections.",
                    checked = demoMode,
                    onCheckedChange = onToggleDemoMode,
                    testTag = "settings_demo_mode_switch"
                )
            }
        }

        // Account Management / Sign Out
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.danger,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    val c = LocalNotifymeColors.current
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
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}
