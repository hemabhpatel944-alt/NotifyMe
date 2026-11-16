package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AppNotification
import com.example.model.NotificationCategory
import com.example.ui.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.LocalNotifymeColors

@Composable
fun NotificationsScreen(
    notificationsState: UiState<List<AppNotification>>,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: (List<AppNotification>) -> Unit,
    onDelete: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenPreferences: () -> Unit
) {
    val c = LocalNotifymeColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<NotificationCategory?>(null) }

    val allNotifications = (notificationsState as? UiState.Success)?.data ?: emptyList()
    val unreadCount = allNotifications.count { !it.isRead }

    val filtered = remember(allNotifications, searchQuery, selectedCategory) {
        val q = searchQuery.trim().lowercase()
        allNotifications.filter { n ->
            val matchesQuery = q.isBlank() ||
                n.title.lowercase().contains(q) ||
                n.message.lowercase().contains(q) ||
                (n.agentName?.lowercase()?.contains(q) == true) ||
                (n.project?.lowercase()?.contains(q) == true)
            val matchesCat = selectedCategory == null || n.categoryEnum == selectedCategory
            matchesQuery && matchesCat
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (unreadCount > 0) "$unreadCount unread notification(s)" else "All caught up",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onOpenPreferences,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Preferences")
                        }
                        if (unreadCount > 0) {
                            OutlinedButton(
                                onClick = { onMarkAllRead(allNotifications) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("mark_all_read_button")
                            ) {
                                Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Mark All")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search notifications...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notifications_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All (${allNotifications.size})") }
                    )
                    NotificationCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                            label = { Text(cat.displayName) }
                        )
                    }
                }
            }
        }

        if (notificationsState is UiState.Loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (filtered.isEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                    EmptyStateCard(
                        icon = Icons.Outlined.NotificationsNone,
                        title = if (allNotifications.isEmpty()) "No notifications yet" else "No matching notifications",
                        subtitle = "When agents finish tasks, encounter errors, or request decisions, alerts appear here in real time."
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { notif ->
                val (catFg, catBg) = when (notif.categoryEnum) {
                    NotificationCategory.TASK_COMPLETED -> c.success to c.successBg
                    NotificationCategory.TASK_FAILED -> c.danger to c.dangerBg
                    NotificationCategory.USER_INPUT_REQUIRED -> c.warning to c.warningBg
                    NotificationCategory.TASK_PROGRESS -> c.info to c.infoBg
                    NotificationCategory.AGENT_CONNECTED -> c.success to c.successBg
                    NotificationCategory.AGENT_DISCONNECTED -> c.neutral to c.neutralBg
                    NotificationCategory.SYSTEM_ALERT -> c.info to c.infoBg
                }
                Card(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .clickable {
                            if (!notif.isRead) onMarkRead(notif.id)
                            if (!notif.taskId.isNullOrBlank()) onOpenTask(notif.taskId)
                        }
                        .testTag("notification_card_${notif.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!notif.isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (!notif.isRead) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else c.subtleBorder
                    )
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
                            StatusPill(
                                label = notif.categoryEnum.displayName,
                                textColor = catFg,
                                bgColor = catBg
                            )
                            Text(
                                text = formatRelativeTime(notif.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = notif.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (!notif.isRead) FontWeight.Bold else FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = notif.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val meta = listOfNotNull(notif.agentName, notif.project).joinToString(" • ")
                            Text(
                                text = meta.ifBlank { "Notifyme System" },
                                style = MaterialTheme.typography.labelSmall,
                                color = c.cyanAccent
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!notif.taskId.isNullOrBlank()) {
                                    TextButton(onClick = {
                                        if (!notif.isRead) onMarkRead(notif.id)
                                        onOpenTask(notif.taskId)
                                    }) {
                                        Text("Open Task")
                                    }
                                }
                                if (!notif.isRead) {
                                    IconButton(
                                        onClick = { onMarkRead(notif.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Outlined.MarkEmailRead, contentDescription = "Mark as read")
                                    }
                                }
                                IconButton(
                                    onClick = { onDelete(notif.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete notification")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
