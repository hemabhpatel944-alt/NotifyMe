package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.model.TaskStatus
import com.example.ui.components.CodeSnippetCard
import com.example.ui.components.NotifymeBrandMark
import com.example.ui.components.StatusPill
import com.example.ui.components.TaskStatusBadge
import com.example.ui.theme.LocalNotifymeColors
import com.example.ui.theme.MonoFontFamily
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LandingAuthScreen(
    onAuthSuccess: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val c = LocalNotifymeColors.current

    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var showDocsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    )
                )
            )
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Top Brand Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NotifymeBrandMark(size = 38.dp)
                        Column {
                            Text(
                                text = "Notifyme",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "MCP AGENT HUB",
                                style = MaterialTheme.typography.labelSmall,
                                color = c.cyanAccent
                            )
                        }
                    }
                    StatusPill(
                        label = "MCP 2025-03",
                        textColor = MaterialTheme.colorScheme.primary,
                        bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        icon = Icons.Outlined.Hub
                    )
                }

                // Hero Card + Generated Visual Art
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, c.subtleBorder),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(175.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_notifyme_hero),
                                contentDescription = "Notifyme MCP Telemetry Banner",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                                            )
                                        )
                                    )
                            )
                            StatusPill(
                                label = "Primary Integration: Google Antigravity",
                                textColor = c.success,
                                bgColor = c.successBg,
                                icon = Icons.Outlined.Bolt,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(14.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Your AI agents work. You stay informed.",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Connect Google Antigravity, Claude Code, Codex, and custom coding agents via the Model Context Protocol. Receive real-time task progress, failure alerts, and decision prompts right on your device.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (authError != null) {
                                Surface(
                                    color = c.dangerBg,
                                    shape = MaterialTheme.shapes.small,
                                    border = BorderStroke(1.dp, c.danger.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = authError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.danger,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Button(
                                    onClick = {
                                        isSigningIn = true
                                        authError = null
                                        triggerInteractiveGoogleSignIn(
                                            context = context,
                                            credentialManager = credentialManager,
                                            scope = scope,
                                            onSuccess = {
                                                isSigningIn = false
                                                onAuthSuccess()
                                            },
                                            onCancelled = {
                                                isSigningIn = false
                                            },
                                            onError = { msg ->
                                                isSigningIn = false
                                                authError = msg
                                            }
                                        )
                                    },
                                    enabled = !isSigningIn,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("google_sign_in_button")
                                ) {
                                    if (isSigningIn) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Signing in...")
                                    } else {
                                        Text("Get Started with Google")
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showDocsSheet = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("view_documentation_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Article,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("View Documentation")
                                }
                            }
                        }
                    }
                }

                // Product Preview Card
                SectionHeader(
                    eyebrow = "PRODUCT PREVIEW",
                    title = "Live Developer Command Center"
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, c.subtleBorder),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SAMPLE WORKSPACE PREVIEW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            StatusPill(
                                label = "Sample Preview",
                                textColor = c.info,
                                bgColor = c.infoBg
                            )
                        }
                        PreviewTaskRow(
                            title = "Authentication System",
                            agent = "Antigravity • notifyme-core",
                            status = TaskStatus.COMPLETED,
                            summary = "Credential Manager Sign-In & SHA-256 token hashing verified."
                        )
                        PreviewTaskRow(
                            title = "Migrate Event Pipeline",
                            agent = "Antigravity • notifyme-mcp",
                            status = TaskStatus.WAITING_FOR_INPUT,
                            summary = "Decision needed: Retain idempotency keys for 7 days?"
                        )
                    }
                }

                // How It Works
                SectionHeader(
                    eyebrow = "HOW IT WORKS",
                    title = "Three steps to zero-friction oversight"
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HowItWorksStep(
                        step = "01",
                        title = "Connect your AI agent",
                        description = "Register Google Antigravity or any MCP client, copy your generated mcp_config.json, and authenticate with a SHA-256 hashed bearer token."
                    )
                    HowItWorksStep(
                        step = "02",
                        title = "Agent calls Notifyme MCP tools",
                        description = "Your agent invokes update_task_progress, request_user_input, notify_task_completed, or notify_task_failed over Streamable HTTP."
                    )
                    HowItWorksStep(
                        step = "03",
                        title = "Notifyme delivers instant alerts",
                        description = "Events sync to Firestore in real time, update your dashboard timeline, and trigger deep-linked Android push notifications."
                    )
                }

                // Supported Integrations
                SectionHeader(
                    eyebrow = "INTEGRATIONS",
                    title = "Built for Google Antigravity & open MCP"
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IntegrationChip("Google Antigravity", highlighted = true)
                    IntegrationChip("Claude Code", highlighted = false)
                    IntegrationChip("Codex", highlighted = false)
                    IntegrationChip("Cursor", highlighted = false)
                    IntegrationChip("Custom MCP Agents", highlighted = false)
                }

                // Features Grid
                SectionHeader(
                    eyebrow = "CAPABILITIES",
                    title = "Engineered for reliability & isolation"
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureRow(
                        icon = Icons.Outlined.NotificationsActive,
                        title = "Real-time notifications",
                        desc = "In-app notification center plus deep-linked Android OS alerts."
                    )
                    FeatureRow(
                        icon = Icons.Outlined.TaskAlt,
                        title = "Task monitoring & input replies",
                        desc = "Track 0-100% progress, inspect error logs, and answer agent decisions."
                    )
                    FeatureRow(
                        icon = Icons.Outlined.SmartToy,
                        title = "Agent credential management",
                        desc = "Per-agent SHA-256 hashed tokens with one-tap rotation and revocation."
                    )
                    FeatureRow(
                        icon = Icons.Outlined.History,
                        title = "Idempotent activity history",
                        desc = "Chronological immutable audit log with duplicate-retry protection."
                    )
                    FeatureRow(
                        icon = Icons.Outlined.Devices,
                        title = "Multi-device cloud sync",
                        desc = "Zero-trust Firestore isolation scoped strictly to your Google account."
                    )
                    FeatureRow(
                        icon = Icons.Outlined.Shield,
                        title = "Hardened MCP transport",
                        desc = "Bearer auth, 64KB payload bounds, and sliding-window rate limiting."
                    )
                }

                HorizontalDivider(color = c.subtleBorder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notifyme • Your AI agents work. Notifyme keeps you informed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showDocsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDocsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Antigravity MCP Integration Guide",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Sign in with Google to spin up your local Streamable HTTP MCP endpoint and generate per-agent bearer credentials.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CodeSnippetCard(
                    title = "antigravity_mcp_config.json",
                    code = """
{
  "mcpServers": {
    "notifyme": {
      "transport": "streamable-http",
      "url": "http://127.0.0.1:8765/mcp",
      "headers": {
        "Authorization": "Bearer ${'$'}{NOTIFYME_AGENT_TOKEN}"
      }
    }
  }
}
                    """.trimIndent(),
                    onCopied = onMessage
                )
                CodeSnippetCard(
                    title = "Antigravity System Instruction",
                    code = ANTIGRAVITY_INSTRUCTION_TEMPLATE,
                    onCopied = onMessage
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(eyebrow: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun PreviewTaskRow(
    title: String,
    agent: String,
    status: TaskStatus,
    summary: String
) {
    val c = LocalNotifymeColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            TaskStatusBadge(status = status)
        }
        Text(
            text = agent,
            style = MaterialTheme.typography.labelMedium,
            color = c.cyanAccent
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HowItWorksStep(step: String, title: String, description: String) {
    val c = LocalNotifymeColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, c.subtleBorder),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = step,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun IntegrationChip(name: String, highlighted: Boolean) {
    val c = LocalNotifymeColors.current
    Surface(
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (highlighted) MaterialTheme.colorScheme.primary else c.subtleBorder),
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (highlighted) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, desc: String) {
    val c = LocalNotifymeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = c.cyanAccent,
            modifier = Modifier.size(22.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            }
        } catch (_: Exception) {
            // Silent auto-sign-in is best-effort; interactive Google Sign-In button handles user login
        }
    }
}

private fun triggerInteractiveGoogleSignIn(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSuccess: () -> Unit,
    onCancelled: () -> Unit,
    onError: (String) -> Unit
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onError("Google Sign-In configuration missing (default_web_client_id not found).")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context as Activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onSuccess()
            } else {
                onError("Unexpected credential type returned by Google Sign-In.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
            onCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onError(e.localizedMessage ?: "Sign-in failed. Please try again.")
        }
    }
}

const val ANTIGRAVITY_INSTRUCTION_TEMPLATE = """When you begin a task, register or identify the task in Notifyme.
When meaningful progress occurs, call update_task_progress.
When you successfully finish a task, call notify_task_completed with the task ID, project, title, and a concise summary.
If a task fails, call notify_task_failed with a useful error summary.
If you require a decision from the user, call request_user_input.
Do not report a task as completed unless the work has actually been completed and verified."""
