package com.nfckeyblock.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nfckeyblock.R
import com.nfckeyblock.ui.MainActivity

class Notifications(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SESSION, context.getString(R.string.channel_session_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_session_desc); setShowBadge(false) }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INTEGRITY, context.getString(R.string.channel_integrity_name), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.channel_integrity_desc) }
        )
    }

    fun sessionNotification(profileName: String?, startedAt: Long): Notification =
        NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(context.getString(R.string.notif_session_title))
            .setContentText(
                profileName?.let { "Perfil: $it · acerca tu tarjeta para terminar" }
                    ?: "Acerca tu tarjeta para terminar"
            )
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply {
                if (startedAt > 0) {
                    setUsesChronometer(true)
                    setWhen(startedAt)
                }
            }
            .setContentIntent(openApp())
            .build()

    fun updateSession(profileName: String, startedAt: Long) {
        notifyIfAllowed(SESSION_NOTIFICATION_ID, sessionNotification(profileName, startedAt))
    }

    fun notifyIntegrityBroken() {
        notifyIfAllowed(
            INTEGRITY_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_INTEGRITY)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(context.getString(R.string.notif_integrity_title))
                .setContentText(context.getString(R.string.notif_integrity_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build()
        )
    }

    fun notifySettingsGuard() {
        notifyIfAllowed(
            GUARD_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_INTEGRITY)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Ajustes protegidos")
                .setContentText("No puedes cambiar la configuración de accesibilidad durante una sesión.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setTimeoutAfter(TIMEOUT_MS)
                .build()
        )
    }

    private fun notifyIfAllowed(id: Int, notification: Notification) {
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) return
        runCatching { compat.notify(id, notification) }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        const val CHANNEL_SESSION = "session"
        const val CHANNEL_INTEGRITY = "integrity"
        const val SESSION_NOTIFICATION_ID = 1001
        const val INTEGRITY_NOTIFICATION_ID = 1002
        const val GUARD_NOTIFICATION_ID = 1003
        private const val TIMEOUT_MS = 6000L
    }
}
