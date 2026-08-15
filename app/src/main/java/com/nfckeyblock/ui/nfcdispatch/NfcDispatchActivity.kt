package com.nfckeyblock.ui.nfcdispatch

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.nfckeyblock.NfcKeyBlockApp
import com.nfckeyblock.domain.usecase.TapOutcome
import com.nfckeyblock.nfc.NfcTagParser
import com.nfckeyblock.service.BlockingSessionService
import com.nfckeyblock.ui.MainActivity
import com.nfckeyblock.util.Format
import kotlinx.coroutines.launch

/**
 * Punto de entrada del sistema cuando se detecta una tarjeta con la app cerrada.
 *
 * Es una Activity y no un Service porque Android SOLO entrega los tags NFC
 * mediante dispatch a una Activity. No existe forma soportada de recibir un tag
 * en segundo plano puro: el sistema exige una interacción visible, precisamente
 * para que ninguna app pueda leer tarjetas a tus espaldas.
 *
 * La Activity es transparente y se cierra sola, así que la experiencia real es
 * "acerco la tarjeta y aparece un aviso", sin abrir la app entera.
 */
class NfcDispatchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle()
    }

    private fun handle() {
        val app = application as NfcKeyBlockApp
        val identity = NfcTagParser.fromIntent(intent)
        if (identity == null) {
            finishWith("No se ha podido leer la tarjeta")
            return
        }

        lifecycleScope.launch {
            val outcome = app.container.handleCardTap(identity)
            when (outcome) {
                is TapOutcome.Activated -> {
                    // Arrancar el FGS aquí es legítimo: estamos en primer plano.
                    BlockingSessionService.start(this@NfcDispatchActivity)
                    vibrate(ACTIVATE_PATTERN)
                    finishWith("🔒 ${outcome.profileName}: ${outcome.blockedCount} apps bloqueadas")
                }
                is TapOutcome.Deactivated -> {
                    BlockingSessionService.stop(this@NfcDispatchActivity)
                    vibrate(DEACTIVATE_PATTERN)
                    finishWith("🔓 Desbloqueado tras ${Format.duration(outcome.durationMillis)}")
                }
                is TapOutcome.UnknownCard -> {
                    // Tarjeta desconocida: se abre la app para que el usuario decida
                    // registrarla, en lugar de dejarle un mensaje sin salida.
                    startActivity(MainActivity.registerCardIntent(this@NfcDispatchActivity))
                    finishWith("Tarjeta no registrada")
                }
                is TapOutcome.Ignored -> finishWith(outcome.reason)
                is TapOutcome.Error -> finishWith(outcome.message)
            }
        }
    }

    private fun finishWith(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
        overridePendingTransition(0, 0)
    }

    private fun vibrate(pattern: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        } ?: return
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    private companion object {
        val ACTIVATE_PATTERN = longArrayOf(0, 40, 60, 40)
        val DEACTIVATE_PATTERN = longArrayOf(0, 120)
    }
}
