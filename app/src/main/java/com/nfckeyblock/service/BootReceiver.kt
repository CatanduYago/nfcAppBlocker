package com.nfckeyblock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nfckeyblock.NfcKeyBlockApp
import com.nfckeyblock.domain.model.SessionEndReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Reiniciar el teléfono es el intento de evasión más obvio. Como el estado vive
 * en Room, la sesión sigue abierta tras el arranque: aquí solo hay que volver a
 * levantar la notificación y el watchdog.
 *
 * El AccessibilityService lo rearranca el propio sistema si sigue habilitado en
 * Ajustes, no hace falta (ni se puede) hacerlo desde aquí.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val app = context.applicationContext as? NfcKeyBlockApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = app.container.sessionRepository.currentSession() ?: return@launch
                val resume = app.container.settingsRepository.settings.first().resumeAfterReboot
                if (resume) {
                    BlockingSessionService.start(context)
                    Log.i(TAG, "Sesión ${session.id} reanudada tras el arranque")
                } else {
                    app.container.sessionRepository.end(SessionEndReason.MANUAL)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fallo al restaurar la sesión", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object { const val TAG = "BootReceiver" }
}
