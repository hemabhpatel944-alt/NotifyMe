package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.AgentStatus
import com.example.model.TaskStatus
import com.example.ui.RevealedCredential
import com.example.ui.theme.LocalNotifymeColors
import com.example.ui.theme.MonoFontFamily
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun NotifymeBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val colors = LocalNotifymeColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        colors.cyanAccent
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.NotificationsActive,
            contentDescription = "Notifyme brand icon",
            tint = Color.White,
            modifier = Modifier.size(size * 0.56f)
        )
    }
}

@Composable
fun StatusPill(
    label: String,
    textColor: Color,
    bgColor: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = bgColor,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = textColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun TaskStatusBadge(status: TaskStatus, modifier: Modifier = Modifier) {
    val c = LocalNotifymeColors.current
    val (fg, bg, icon) = when (status) {
        TaskStatus.COMPLETED -> Triple(c.success, c.successBg, Icons.Rounded.CheckCircle)
        TaskStatus.FAILED -> Triple(c.danger, c.dangerBg, Icons.Rounded.Error)
        TaskStatus.RUNNING -> Triple(c.info, c.infoBg, Icons.Rounded.Sync)
        TaskStatus.WAITING_FOR_INPUT -> Triple(c.warning, c.warningBg, Icons.AutoMirrored.Rounded.HelpOutline)
        TaskStatus.PENDING -> Triple(c.neutral, c.neutralBg, Icons.Rounded.Schedule)
        TaskStatus.CANCELLED -> Triple(c.neutral, c.neutralBg, Icons.Rounded.Close)
    }
    StatusPill(label = status.displayName, textColor = fg, bgColor = bg, icon = icon, modifier = modifier)
}

@Composable
fun AgentStatusBadge(status: AgentStatus, modifier: Modifier = Modifier) {
    val c = LocalNotifymeColors.current
    val (fg, bg) = when (status) {
        AgentStatus.CONNECTED -> c.success to c.successBg
        AgentStatus.DISCONNECTED -> c.neutral to c.neutralBg
        AgentStatus.REVOKED -> c.danger to c.dangerBg
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            Text(
                text = status.displayName,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = fg
            )
        }
    }
}

@Composable
fun MetricStatCard(
    title: String,
    value: String,
    accentColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val c = LocalNotifymeColors.current
    Card(
        modifier = modifier
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
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
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun CodeSnippetCard(
    title: String,
    code: String,
    onCopied: (String) -> Unit,
    modifier: Modifier = Modifier,
    copyButtonTag: String = "copy_code_button"
) {
    val c = LocalNotifymeColors.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.codeSurface),
        border = BorderStroke(1.dp, c.subtleBorder),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                        onCopied("Copied $title to clipboard")
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(copyButtonTag)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Rounded.Check else Icons.Outlined.ContentCopy,
                        contentDescription = "Copy $title",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (copied) "Copied" else "Copy", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

@Composable
fun RevealedCredentialBanner(
    credential: RevealedCredential?,
    onDismiss: () -> Unit,
    onCopied: (String) -> Unit
) {
    val c = LocalNotifymeColors.current
    val clipboard = LocalClipboardManager.current
    AnimatedVisibility(visible = credential != null) {
        credential?.let { cred ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = c.warningBg),
                border = BorderStroke(1.dp, c.warning.copy(alpha = 0.5f)),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = "Agent credential",
                                tint = c.warning,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "One-Time MCP Credential • ${cred.agentName}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss token banner")
                        }
                    }
                    Text(
                        text = "Copy this bearer token now. Notifyme stores only its SHA-256 hash and will not display it again after this session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(c.codeSurface)
                            .border(1.dp, c.subtleBorder, MaterialTheme.shapes.small)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = cred.rawToken,
                            style = MaterialTheme.typography.labelMedium,
                            color = c.cyanAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString(cred.rawToken))
                                onCopied("Bearer token copied")
                            },
                            modifier = Modifier.testTag("copy_revealed_token_button")
                        ) {
                            Text("Copy")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    primaryLabel: String? = null,
    onPrimaryClick: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val c = LocalNotifymeColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, c.subtleBorder),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (primaryLabel != null && onPrimaryClick != null) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onPrimaryClick,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(primaryLabel)
                    }
                    if (secondaryLabel != null && onSecondaryClick != null) {
                        OutlinedButton(
                            onClick = onSecondaryClick,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text(secondaryLabel)
                        }
                    }
                }
            }
        }
    }
}

fun formatRelativeTime(ts: Timestamp?): String {
    if (ts == null) return "Just now"
    val diffMs = (System.currentTimeMillis() - ts.toDate().time).coerceAtLeast(0L)
    val mins = diffMs / 60_000L
    return when {
        mins < 1 -> "Just now"
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(ts.toDate())
    }
}

fun formatExactTime(ts: Timestamp?): String {
    if (ts == null) return "Pending sync"
    return SimpleDateFormat("MMM d, yyyy • HH:mm:ss", Locale.getDefault()).format(ts.toDate())
}
