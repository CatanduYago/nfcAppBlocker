package com.nfckeyblock.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.nfckeyblock.NfcKeyBlockApp
import com.nfckeyblock.domain.model.SessionEndReason
import com.nfckeyblock.util.Notifications
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que vive exactamente lo que dura una sesión.
 *
 * No es quien bloquea (eso lo hace el AccessibilityService); su trabajo es:
 *  1. Mostrar la notificación persistente exigida por Android, que además es
 *     el recordatorio visible de que la sesión sigue activa.
 *  2. Vigilar que el servicio de accesibilidad siga habilitado (watchdog).
 *  3. Cerrar la sesión sola si el perfil tiene duración máxima.
 *
 * Se arranca desde una Activity o desde BOOT_COMPLETED, ambos contextos donde
 * Android permite iniciar un foreground service.
 */
class BlockingSessionService : LifecycleService() {

    private val app: NfcKeyBlockApp get() = applicationContext as NfcKeyBlockApp
    private var watchdog: AccessibilityWatchdog? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(Notifications.SESSION_NOTIFICATION_ID, app.container.notifications.sessionNotification(null, 0))
        watchdog = AccessibilityWatchdog(this) { app.container.notifications.notifyIntegrityBroken() }
            .also { it.start() }
        observeSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // START_STICKY: si el sistema mata el proceso por presión de memoria,
        // el servicio se recrea y vuelve a leer el estado desde Room.
        return START_STICKY
    }

    private fun observeSession() {
        lifecycleScope.launch {
            app.container.sessionRepository.observeState().collectLatest { state ->
                val session = state.session
                if (session == null) {
                    stopSelf()
                    return@collectLatest
                }
                app.container.notifications.updateSession(state.profileName, session.startedAt)

                val profile = app.container.profileRepository.getProfile(session.profileId)
                val limit = profile?.autoEndMinutes ?: 0
                if (limit > 0 && System.currentTimeMillis() - session.startedAt >= limit * 60_000L) {
                    app.container.sessionRepository.end(SessionEndReason.TIMER)
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        watchdog?.stop()
        watchdog = null
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, BlockingSessionService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BlockingSessionService::class.java))
        }
    }
}
