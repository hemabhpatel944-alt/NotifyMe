# Notifyme Architecture

- **UI Layer**: Jetpack Compose + Material 3 dark-first (`NotifymeTheme`) with adaptive `NavigationBar` / `ModalNavigationDrawer` / `NavigationRail`, Inter + JetBrains Mono bundled fonts, and edge-to-edge safe insets.
- **Authentication**: Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption` interactive + `GetGoogleIdOption` silent auto-sign-in) backed by Firebase Auth.
- **Persistence & Real-Time Sync**: Cloud Firestore named enterprise database (`R.string.firestore_database_id`) with zero-trust per-user subcollection isolation (`/users/{userId}/...`), immutable append-only `events`, and terminal-state locking on `tasks`.
- **Embedded MCP Server**: Pure Kotlin `ServerSocket` Streamable HTTP JSON-RPC 2.0 server (`EmbeddedMcpServer` + `McpToolProcessor`) validating bearer tokens via SHA-256, enforcing payload/rate limits, deduplicating via `idempotencyKey`, writing to Firestore, and dispatching Android `NotificationCompat` alerts with task deep-links.
- **FCM Readiness**: Notification preferences and device channel status are modeled in `UserProfile`; remote background FCM push can be attached once FCM credentials and Cloud Functions are configured in the Firebase Console.
