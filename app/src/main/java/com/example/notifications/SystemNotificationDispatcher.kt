package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.AppNotification

object SystemNotificationDispatcher {

    const val CHANNEL_ID = "notifyme_agent_events"
    const val EXTRA_OPEN_TASK_ID = "extra_open_task_id"
    const val EXTRA_OPEN_NOTIFICATION_ID = "extra_open_notification_id"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Agent Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time notifications from Google Antigravity and MCP agents."
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun dispatch(context: Context, notification: AppNotification) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!notification.taskId.isNullOrBlank()) {
                putExtra(EXTRA_OPEN_TASK_ID, notification.taskId)
            }
            putExtra(EXTRA_OPEN_NOTIFICATION_ID, notification.id)
        }

        val requestCode = (notification.id.hashCode() and 0x7fffffff)
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subText = listOfNotNull(
            notification.agentName?.takeIf { it.isNotBlank() },
            notification.project?.takeIf { it.isNotBlank() }
        ).joinToString(" • ").ifBlank { "Notifyme MCP" }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifyme_bell)
            .setContentTitle(notification.title)
            .setContentText(notification.message)
            .setSubText(subText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(requestCode, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }
}
