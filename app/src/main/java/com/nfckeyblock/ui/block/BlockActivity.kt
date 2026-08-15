package com.nfckeyblock.ui.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.nfckeyblock.NfcKeyBlockApp
import com.nfckeyblock.domain.usecase.TapOutcome
import com.nfckeyblock.nfc.NfcReaderController
import com.nfckeyblock.service.BlockingSessionService
import com.nfckeyblock.ui.theme.NfcKeyBlockTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Pantalla que cubre la app bloqueada.
 *
 * Detalles que importan:
 *  - Habilita el Reader Mode: se puede desbloquear apoyando la tarjeta aquí
 *    mismo, sin volver a la app principal.
 *  - Ignora el botón atrás. No es una "trampa": la Activity se cierra sola en
 *    cuanto la sesión termina, y siempre queda el botón de inicio del sistema.
 *  - excludeFromRecents en el manifest evita que quede como tarea suelta.
 */
class BlockActivity : ComponentActivity() {

    private lateinit var reader: NfcReaderController
    private val message = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as NfcKeyBlockApp
        reader = NfcReaderController(this)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        // Si la sesión termina por cualquier vía, esta pantalla debe desaparecer sola.
        lifecycleScope.launch {
            app.container.sessionRepository.observeState().collect { state ->
                if (!state.isBlocking) finish()
            }
        }

        setContent {
            val settings by app.container.settingsRepository.settings.collectAsState(
                initial = com.nfckeyblock.data.prefs.AppSettings()
            )
            val state by app.container.sessionRepository.observeState().collectAsState(
                initial = com.nfckeyblock.domain.model.BlockingState()
            )
            val note by message.collectAsState()
            NfcKeyBlockTheme(dynamicColor = settings.useDynamicColor) {
                BlockScreen(
                    profileName = state.profileName,
                    blockedApp = intent.getStringExtra(EXTRA_PACKAGE),
                    startedAt = state.session?.startedAt ?: 0L,
                    nfcAvailable = reader.isSupported && reader.isEnabled,
                    note = note,
                    onHome = { moveTaskToBack(true) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as NfcKeyBlockApp
        reader.enable { identity ->
            lifecycleScope.launch {
                when (val outcome = app.container.handleCardTap(identity)) {
                    is TapOutcome.Deactivated -> {
                        BlockingSessionService.stop(this@BlockActivity)
                        finish()
                    }
                    is TapOutcome.UnknownCard -> message.value = "Esa tarjeta no está registrada"
                    is TapOutcome.Ignored -> message.value = outcome.reason
                    is TapOutcome.Error -> message.value = outcome.message
                    is TapOutcome.Activated -> Unit
                }
            }
        }
    }

    override fun onPause() {
        reader.disable()
        super.onPause()
    }

    companion object {
        private const val EXTRA_PACKAGE = "blocked_package"

        fun intentFor(context: Context, packageName: String): Intent =
            Intent(context, BlockActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                .putExtra(EXTRA_PACKAGE, packageName)
    }
}
