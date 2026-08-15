package com.nfckeyblock

import android.app.Application
import com.nfckeyblock.di.AppContainer
import com.nfckeyblock.service.BlockingSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import android.util.Log
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class NfcKeyBlockApp : Application() {

    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        scope.launch {
            container.profileRepository.ensureDefaultProfile()
        }

        // Un único punto que sincroniza "hay sesión" con "el foreground service existe".
        // Así da igual desde dónde se active (NFC, UI, arranque): el servicio siempre cuadra.
        scope.launch {
            container.sessionRepository.observeState()
                .map { it.isBlocking }
                .distinctUntilChanged()
                .onEach { blocking ->
                    if (!blocking) return@onEach
                    // Android 12+ prohíbe arrancar un FGS desde segundo plano. Los
                    // caminos reales (toque NFC, UI, BOOT_COMPLETED) están exentos,
                    // pero si el proceso revive por otra razón hay que fallar en
                    // silencio: el bloqueo lo sigue haciendo el AccessibilityService,
                    // solo faltaría la notificación hasta la próxima interacción.
                    runCatching { BlockingSessionService.start(this@NfcKeyBlockApp) }
                        .onFailure { Log.w(TAG, "No se pudo arrancar el servicio de sesión: ${it.message}") }
                }
                .collect()
        }
    }

    private companion object { const val TAG = "NfcKeyBlockApp" }
}
