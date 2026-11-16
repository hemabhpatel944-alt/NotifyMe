package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.LocalPreferencesStore
import com.example.data.NotifymeRepository
import com.example.notifications.SystemNotificationDispatcher
import com.example.ui.NotifymeViewModel
import com.example.ui.UiState
import com.example.ui.components.NotifymeBrandMark
import com.example.ui.components.RevealedCredentialBanner
import com.example.ui.components.StatusPill
import com.example.ui.screens.ActivityScreen
import com.example.ui.screens.AgentsScreen
import com.example.ui.screens.LandingAuthScreen
import com.example.ui.screens.McpSetupScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TaskDetailScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.LocalNotifymeColors
import com.example.ui.theme.NotifymeTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class NotifymeDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Overview : NotifymeDestination("overview", "Overview", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard)
    data object Tasks : NotifymeDestination("tasks", "Tasks", Icons.Rounded.TaskAlt, Icons.Outlined.TaskAlt)
    data object Notifications : NotifymeDestination("notifications", "Alerts", Icons.Rounded.Notifications, Icons.Outlined.Notifications)
    data object Activity : NotifymeDestination("activity", "Activity", Icons.Rounded.History, Icons.Outlined.History)
    data object Agents : NotifymeDestination("agents", "Agents", Icons.Rounded.SmartToy, Icons.Outlined.SmartToy)
    data object McpSetup : NotifymeDestination("mcp_setup", "MCP Setup", Icons.Rounded.Hub, Icons.Outlined.Hub)
    data object Settings : NotifymeDestination("settings", "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings)

    companion object {
        val allDestinations = listOf(Overview, Tasks, Notifications, Activity, Agents, McpSetup, Settings)
        val bottomBarDestinations = listOf(Overview, Tasks, Notifications, Agents, McpSetup)
    }
}

private const val ROUTE_TASK_DETAIL = "task_detail/{taskId}"
private fun taskDetailRoute(taskId: String) = "task_detail/$taskId"

class MainActivity : ComponentActivity() {

    private var pendingTaskIdState by mutableStateOf<String?>(null)
    private var pendingNotificationIdState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractDeepLink(intent)

        setContent {
            val localPrefs = remember { LocalPreferencesStore(applicationContext) }
            val fallbackDark by localPrefs.darkThemeFallbackFlow.collectAsStateWithLifecycle(initialValue = true)
            var overrideDarkTheme by remember { mutableStateOf<Boolean?>(null) }

            NotifymeTheme(darkTheme = overrideDarkTheme ?: fallbackDark) {
                NotifymeRootApp(
                    pendingTaskId = pendingTaskIdState,
                    pendingNotificationId = pendingNotificationIdState,
                    onConsumeDeepLink = {
                        pendingTaskIdState = null
                        pendingNotificationIdState = null
                    },
                    onThemeResolved = { dark -> overrideDarkTheme = dark }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractDeepLink(intent)
    }

    private fun extractDeepLink(intent: Intent?) {
        val taskId = intent?.getStringExtra(SystemNotificationDispatcher.EXTRA_OPEN_TASK_ID)
        val notifId = intent?.getStringExtra(SystemNotificationDispatcher.EXTRA_OPEN_NOTIFICATION_ID)
        if (!taskId.isNullOrBlank()) pendingTaskIdState = taskId
        if (!notifId.isNullOrBlank()) pendingNotificationIdState = notifId
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun NotifymeRootApp(
    pendingTaskId: String?,
    pendingNotificationId: String?,
    onConsumeDeepLink: () -> Unit,
    onThemeResolved: (Boolean) -> Unit
) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val user = currentUser
    if (user == null) {
        onThemeResolved(true)
        Box(modifier = Modifier.fillMaxSize()) {
            LandingAuthScreen(
                onAuthSuccess = { },
                onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    } else {
        key(user.uid) {
            AuthenticatedNotifymeShell(
                user = user,
                pendingTaskId = pendingTaskId,
                pendingNotificationId = pendingNotificationId,
                onConsumeDeepLink = onConsumeDeepLink,
                onThemeResolved = onThemeResolved
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedNotifymeShell(
    user: FirebaseUser,
    pendingTaskId: String?,
    pendingNotificationId: String?,
    onConsumeDeepLink: () -> Unit,
    onThemeResolved: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: NotifymeViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                NotifymeViewModel(
                    repository = NotifymeRepository(db),
                    localPrefs = LocalPreferencesStore(app),
                    appContext = app,
                    currentUserId = user.uid,
                    userEmail = user.email.orEmpty(),
                    userDisplayName = user.displayName ?: "Developer",
                    userPhotoUrl = user.photoUrl?.toString()
                )
            }
        }
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val agentsState by viewModel.agentsState.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val eventsState by viewModel.eventsState.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val mcpStatus by viewModel.mcpStatus.collectAsStateWithLifecycle()
    val demoMode by viewModel.demoMode.collectAsStateWithLifecycle()
    val lanBinding by viewModel.lanBinding.collectAsStateWithLifecycle()
    val revealedCredential by viewModel.revealedCredential.collectAsStateWithLifecycle()
    val sessionTokens by viewModel.sessionTokens.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val lastRpcOutput by viewModel.lastRpcOutput.collectAsStateWithLifecycle()
    val isSendingTestRpc by viewModel.isSendingTestRpc.collectAsStateWithLifecycle()

    LaunchedEffect(profileState) {
        val prof = (profileState as? UiState.Success)?.data
        if (prof != null) {
            onThemeResolved(prof.themeMode != "light")
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(toastMessage) {
        val msg = toastMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Deep-link from Android OS Notification
    LaunchedEffect(pendingTaskId, pendingNotificationId) {
        if (!pendingNotificationId.isNullOrBlank()) {
            viewModel.markNotificationRead(pendingNotificationId)
        }
        if (!pendingTaskId.isNullOrBlank()) {
            navController.navigate(taskDetailRoute(pendingTaskId))
            onConsumeDeepLink()
        }
    }

    var activityAgentFilterId by remember { mutableStateOf<String?>(null) }

    val notificationsList = (notificationsState as? UiState.Success)?.data ?: emptyList()
    val unreadCount = notificationsList.count { !it.isRead }
    val c = LocalNotifymeColors.current

    fun navigateTopLevel(dest: NotifymeDestination) {
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isDetailScreen = currentRoute?.startsWith("task_detail/") == true

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 720.dp
        val drawerState = rememberDrawerState(DrawerValue.Closed)

        val currentDestinationTitle = NotifymeDestination.allDestinations
            .firstOrNull { it.route == currentRoute }?.label ?: "Notifyme"

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !useRail && !isDetailScreen,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NotifymeBrandMark(size = 34.dp)
                        Column {
                            Text("Notifyme", style = MaterialTheme.typography.titleMedium)
                            Text("MCP Agent Hub", style = MaterialTheme.typography.labelSmall, color = c.cyanAccent)
                        }
                    }
                    HorizontalDivider(color = c.subtleBorder)
                    Spacer(Modifier.size(8.dp))
                    NotifymeDestination.allDestinations.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationDrawerItem(
                            selected = selected,
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (dest != NotifymeDestination.Activity) activityAgentFilterId = null
                                navigateTopLevel(dest)
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = dest.label
                                )
                            },
                            label = { Text(dest.label) },
                            badge = if (dest == NotifymeDestination.Notifications && unreadCount > 0) {
                                { Badge { Text(unreadCount.toString()) } }
                            } else null,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .testTag("drawer_item_${dest.route}")
                        )
                    }
                }
            }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (useRail && !isDetailScreen) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(Modifier.size(12.dp))
                        NotifymeBrandMark(size = 34.dp)
                        Spacer(Modifier.size(16.dp))
                        NotifymeDestination.allDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (dest != NotifymeDestination.Activity) activityAgentFilterId = null
                                    navigateTopLevel(dest)
                                },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (dest == NotifymeDestination.Notifications && unreadCount > 0) {
                                                Badge { Text(unreadCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    }
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag("rail_item_${dest.route}")
                            )
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentWindowInsets = WindowInsets.navigationBars,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        if (!isDetailScreen) {
                            TopAppBar(
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(currentDestinationTitle)
                                        if (mcpStatus.isRunning) {
                                            StatusPill(
                                                label = "MCP Live",
                                                textColor = c.success,
                                                bgColor = c.successBg
                                            )
                                        }
                                    }
                                },
                                navigationIcon = {
                                    if (!useRail) {
                                        IconButton(
                                            onClick = { scope.launch { drawerState.open() } },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("open_drawer_button")
                                        ) {
                                            Icon(Icons.Outlined.Menu, contentDescription = "Open navigation menu")
                                        }
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = { navigateTopLevel(NotifymeDestination.Activity) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("topbar_activity_button")
                                    ) {
                                        Icon(Icons.Outlined.History, contentDescription = "Activity timeline")
                                    }
                                    IconButton(
                                        onClick = { navigateTopLevel(NotifymeDestination.Settings) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("topbar_settings_button")
                                    ) {
                                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background
                                )
                            )
                        }
                    },
                    bottomBar = {
                        if (!useRail && !isDetailScreen) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                windowInsets = WindowInsets.navigationBars
                            ) {
                                NotifymeDestination.bottomBarDestinations.forEach { dest ->
                                    val selected = currentRoute == dest.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateTopLevel(dest) },
                                        icon = {
                                            BadgedBox(
                                                badge = {
                                                    if (dest == NotifymeDestination.Notifications && unreadCount > 0) {
                                                        Badge { Text(unreadCount.toString()) }
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                    contentDescription = dest.label
                                                )
                                            }
                                        },
                                        label = { Text(dest.label, maxLines = 1) },
                                        modifier = Modifier.testTag("bottom_nav_${dest.route}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (demoMode) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = c.infoBg
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "DEMO MODE • Showing sample agent telemetry",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = c.info
                                    )
                                    TextButton(onClick = { viewModel.toggleDemoMode(false) }) {
                                        Text("Exit Demo")
                                    }
                                }
                            }
                        }

                        RevealedCredentialBanner(
                            credential = revealedCredential,
                            onDismiss = viewModel::dismissRevealedCredential,
                            onCopied = viewModel::showToast
                        )

                        NavHost(
                            navController = navController,
                            startDestination = NotifymeDestination.Overview.route,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(NotifymeDestination.Overview.route) {
                                OverviewScreen(
                                    tasksState = tasksState,
                                    agentsState = agentsState,
                                    eventsState = eventsState,
                                    notificationsState = notificationsState,
                                    mcpStatus = mcpStatus,
                                    demoMode = demoMode,
                                    onOpenTask = { taskId -> navController.navigate(taskDetailRoute(taskId)) },
                                    onNavigateToTasks = { navigateTopLevel(NotifymeDestination.Tasks) },
                                    onNavigateToAgents = { navigateTopLevel(NotifymeDestination.Agents) },
                                    onNavigateToMcpSetup = { navigateTopLevel(NotifymeDestination.McpSetup) },
                                    onNavigateToNotifications = { navigateTopLevel(NotifymeDestination.Notifications) },
                                    onNavigateToActivity = { navigateTopLevel(NotifymeDestination.Activity) },
                                    onEnableDemoMode = { viewModel.toggleDemoMode(true) }
                                )
                            }

                            composable(NotifymeDestination.Tasks.route) {
                                TasksScreen(
                                    tasksState = tasksState,
                                    agentsState = agentsState,
                                    onOpenTask = { taskId -> navController.navigate(taskDetailRoute(taskId)) },
                                    onNavigateToMcpSetup = { navigateTopLevel(NotifymeDestination.McpSetup) }
                                )
                            }

                            composable(
                                route = ROUTE_TASK_DETAIL,
                                arguments = listOf(navArgument("taskId") { type = NavType.StringType })
                            ) { entry ->
                                val taskId = entry.arguments?.getString("taskId").orEmpty()
                                val allTasks = (tasksState as? UiState.Success)?.data ?: emptyList()
                                val allEvents = (eventsState as? UiState.Success)?.data ?: emptyList()
                                val targetTask = allTasks.firstOrNull { it.id == taskId }
                                TaskDetailScreen(
                                    task = targetTask,
                                    events = allEvents,
                                    onBack = { navController.popBackStack() },
                                    onRespondToInput = viewModel::respondToTaskInput,
                                    onCancelTask = viewModel::cancelTask
                                )
                            }

                            composable(NotifymeDestination.Notifications.route) {
                                NotificationsScreen(
                                    notificationsState = notificationsState,
                                    onMarkRead = viewModel::markNotificationRead,
                                    onMarkAllRead = viewModel::markAllNotificationsRead,
                                    onDelete = viewModel::deleteNotification,
                                    onOpenTask = { taskId -> navController.navigate(taskDetailRoute(taskId)) },
                                    onOpenPreferences = { navigateTopLevel(NotifymeDestination.Settings) }
                                )
                            }

                            composable(NotifymeDestination.Activity.route) {
                                ActivityScreen(
                                    eventsState = eventsState,
                                    agentsState = agentsState,
                                    initialAgentFilterId = activityAgentFilterId,
                                    onOpenTask = { taskId -> navController.navigate(taskDetailRoute(taskId)) }
                                )
                            }

                            composable(NotifymeDestination.Agents.route) {
                                AgentsScreen(
                                    agentsState = agentsState,
                                    onRegisterAgent = viewModel::registerAgent,
                                    onRenameAgent = viewModel::renameAgent,
                                    onRotateCredential = viewModel::rotateAgentCredential,
                                    onRevokeAgent = viewModel::revokeAgent,
                                    onViewAgentActivity = { agentId ->
                                        activityAgentFilterId = agentId
                                        navigateTopLevel(NotifymeDestination.Activity)
                                    }
                                )
                            }

                            composable(NotifymeDestination.McpSetup.route) {
                                McpSetupScreen(
                                    mcpStatus = mcpStatus,
                                    lanBinding = lanBinding,
                                    agentsState = agentsState,
                                    sessionTokens = sessionTokens,
                                    lastRpcOutput = lastRpcOutput,
                                    isSendingTestRpc = isSendingTestRpc,
                                    onToggleServer = viewModel::toggleMcpServer,
                                    onUpdateNetworkSettings = viewModel::updateMcpNetworkSettings,
                                    onRegisterAntigravity = {
                                        viewModel.registerAgent("Antigravity Primary", com.example.model.AgentType.ANTIGRAVITY)
                                    },
                                    onRotateCredential = viewModel::rotateAgentCredential,
                                    onSendLiveTestCall = viewModel::sendLiveMcpTestCall,
                                    onCopied = viewModel::showToast
                                )
                            }

                            composable(NotifymeDestination.Settings.route) {
                                SettingsScreen(
                                    profileState = profileState,
                                    agentsState = agentsState,
                                    demoMode = demoMode,
                                    onToggleTheme = viewModel::updateThemeMode,
                                    onUpdatePref = viewModel::updateNotificationPreference,
                                    onToggleDemoMode = viewModel::toggleDemoMode,
                                    onManageAgents = { navigateTopLevel(NotifymeDestination.Agents) },
                                    onSignOut = {
                                        scope.launch {
                                            withContext(NonCancellable) {
                                                try {
                                                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                                                } catch (_: Exception) {
                                                } finally {
                                                    Firebase.auth.signOut()
                                                }
                                            }
                                        }
                                    },
                                    onMessage = viewModel::showToast
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
