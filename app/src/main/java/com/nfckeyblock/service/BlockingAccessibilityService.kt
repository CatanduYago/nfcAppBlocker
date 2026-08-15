package com.nfckeyblock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nfckeyblock.NfcKeyBlockApp
import com.nfckeyblock.domain.BlockDecision
import com.nfckeyblock.domain.BlockingPolicy
import com.nfckeyblock.domain.model.BlockingState
import com.nfckeyblock.ui.block.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Detector de app en primer plano.
 *
 * Por qué AccessibilityService y no UsageStatsManager:
 *  - UsageStatsManager solo permite *sondear*. El intervalo real de agregación
 *    hace que detectes la apertura entre 0,5 y varios segundos tarde, y sondear
 *    en bucle desde un foreground service castiga la batería.
 *  - AccessibilityService recibe TYPE_WINDOW_STATE_CHANGED en el instante en que
 *    la ventana pasa a primer plano. Es lo que usan todas las apps serias del
 *    sector, y es la vía soportada por el sistema.
 *
 * Contrapartidas asumidas: el usuario debe activarlo a mano en Ajustes, Google
 * Play exige justificarlo, y el sistema puede desactivarlo (ver [AccessibilityWatchdog]).
 */
class BlockingAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val app: NfcKeyBlockApp get() = applicationContext as NfcKeyBlockApp

    /** Copia en memoria del estado: el callback de accesibilidad no puede tocar disco. */
    @Volatile private var state: BlockingState = BlockingState()
    private var lastBlockedPackage: String? = null
    private var lastBlockAt = 0L
    private var homePackages: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        homePackages = HomePackages.resolve(this)
        app.container.sessionRepository.observeState()
            .onEach { state = it }
            .launchIn(scope)
        ServiceState.accessibilityRunning = true
        Log.i(TAG, "Servicio de bloqueo conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        if (ev.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val snapshot = state
        if (!snapshot.isBlocking) return

        val pkg = ev.packageName?.toString()
        when (val decision = BlockingPolicy.decide(
            state = snapshot,
            foregroundPackage = pkg,
            foregroundClass = ev.className?.toString(),
            ownPackage = packageName,
            homePackages = homePackages
        )) {
            is BlockDecision.BlockApp -> block(decision.packageName)
            BlockDecision.DeflectSettings -> deflect()
            BlockDecision.Allow -> if (snapshot.blockWebDomains) checkBrowser(pkg, snapshot)
        }
    }

    private fun block(packageName: String) {
        val nowMs = SystemClock.elapsedRealtime()
        // Anti-rebote: al cubrir la app con BlockActivity se generan más eventos.
        if (packageName == lastBlockedPackage && nowMs - lastBlockAt < DEBOUNCE_MS) return
        lastBlockedPackage = packageName
        lastBlockAt = nowMs

        // Volver al inicio primero evita que la app bloqueada quede debajo en la
        // pila y reaparezca al cerrar la pantalla de bloqueo.
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(BlockActivity.intentFor(this, packageName))
        scope.launch { app.container.sessionRepository.recordAttempt(packageName) }
    }

    private fun deflect() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        scope.launch { app.container.notifications.notifySettingsGuard() }
    }

    /**
     * Bloqueo por dominio en navegadores. Es explícitamente "mejor esfuerzo":
     * depende de ids de vista internos de cada navegador, que cambian entre
     * versiones, y no ve nada en pestañas de incógnito de algunos navegadores.
     */
    private fun checkBrowser(pkg: String?, snapshot: BlockingState) {
        val browserPkg = pkg ?: return
        val urlBarId = BROWSER_URL_BAR_IDS[browserPkg] ?: return
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(urlBarId) }.getOrNull().orEmpty()
        val url = nodes.firstOrNull()?.text?.toString()
        if (BlockingPolicy.isDomainBlocked(url, snapshot.blockedDomains)) {
            block(browserPkg)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        ServiceState.accessibilityRunning = false
        Log.w(TAG, "Servicio de bloqueo desvinculado")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ServiceState.accessibilityRunning = false
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "BlockingA11y"
        const val DEBOUNCE_MS = 800L
        val BROWSER_URL_BAR_IDS = mapOf(
            "com.android.chrome" to "com.android.chrome:id/url_bar",
            "com.chrome.beta" to "com.chrome.beta:id/url_bar",
            "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.brave.browser" to "com.brave.browser:id/url_bar",
            "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
            "com.opera.browser" to "com.opera.browser:id/url_field"
        )
    }
}

/** Bandera en memoria; para saber el estado real siempre se consulta a Settings.Secure. */
object ServiceState {
    @Volatile var accessibilityRunning: Boolean = false
}
