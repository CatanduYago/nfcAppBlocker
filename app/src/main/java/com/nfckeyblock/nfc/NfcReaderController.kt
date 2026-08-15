package com.nfckeyblock.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Bundle

/**
 * Envuelve el Reader Mode, que es el modo correcto para leer tarjetas con la
 * app en primer plano.
 *
 * Por qué Reader Mode y no el viejo foreground dispatch:
 *  - No dispara el sonido ni la animación del sistema (se puede silenciar).
 *  - No provoca relanzados de Activity ni pasa por el intent dispatcher.
 *  - Permite desactivar la comprobación NDEF, que en tarjetas no formateadas
 *    introduce un retardo perceptible.
 * El callback llega en un hilo de binder, nunca en el hilo principal.
 */
class NfcReaderController(private val activity: Activity) {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    val isSupported: Boolean get() = adapter != null
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    fun enable(onTag: (NfcTagIdentity) -> Unit) {
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, PRESENCE_DELAY_MS) }
        adapter?.enableReaderMode(activity, { tag -> onTag(NfcTagParser.fromTag(tag)) }, flags, extras)
    }

    fun disable() {
        adapter?.disableReaderMode(activity)
    }

    private companion object { const val PRESENCE_DELAY_MS = 250 }
}
