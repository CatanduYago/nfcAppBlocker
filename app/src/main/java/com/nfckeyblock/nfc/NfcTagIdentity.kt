package com.nfckeyblock.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Parcelable

/**
 * Identidad extraída de una tarjeta antes de aplicarle HMAC.
 *
 * @param uid       identificador entregado por el controlador NFC. Presente casi siempre,
 *                  pero OJO: hay tarjetas con UID aleatorio por sesión (algunas DESFire y
 *                  los emuladores HCE de otros móviles), donde no sirve como identidad.
 * @param token     contenido del registro NDEF propio, si la tarjeta fue escrita por la app.
 * @param techList  tecnologías reportadas, útiles para el diagnóstico en la UI.
 * @param tag       referencia viva al tag. Solo es válida mientras la tarjeta sigue
 *                  en el campo: en cuanto se retira, cualquier operación lanza IOException.
 *                  Por eso escribir exige un segundo contacto y no se puede diferir.
 */
data class NfcTagIdentity(
    val uid: ByteArray,
    val token: ByteArray?,
    val techList: List<String>,
    val isWritable: Boolean,
    val maxNdefSize: Int,
    val tag: Tag? = null
) {
    val uidHex: String get() = uid.joinToString(":") { "%02X".format(it) }

    /** UID de 4 bytes que empieza por 08 es la marca clásica de UID aleatorio. */
    val looksRandomUid: Boolean get() = uid.size == 4 && uid[0] == 0x08.toByte()

    override fun equals(other: Any?): Boolean =
        other is NfcTagIdentity && uid.contentEquals(other.uid) &&
            (token?.contentEquals(other.token ?: ByteArray(0)) ?: (other.token == null))

    override fun hashCode(): Int = uid.contentHashCode() * 31 + (token?.contentHashCode() ?: 0)
}

object NfcTagParser {

    const val MIME_TYPE = "application/vnd.com.nfckeyblock.key"

    fun fromIntent(intent: Intent): NfcTagIdentity? {
        val tag = intent.parcelable<Tag>(NfcAdapter.EXTRA_TAG) ?: return null
        val messages = intent.parcelableArray(NfcAdapter.EXTRA_NDEF_MESSAGES)
            ?.filterIsInstance<NdefMessage>()
            .orEmpty()
        return fromTag(tag, messages)
    }

    fun fromTag(tag: Tag, ndefMessages: List<NdefMessage> = emptyList()): NfcTagIdentity {
        val ndef = runCatching { android.nfc.tech.Ndef.get(tag) }.getOrNull()
        val messages = ndefMessages.ifEmpty {
            // En modo lector el intent no trae NDEF: hay que leerlo del tag.
            runCatching {
                ndef?.use { it.connect(); listOfNotNull(it.ndefMessage) }
            }.getOrNull().orEmpty()
        }
        return NfcTagIdentity(
            uid = tag.id ?: ByteArray(0),
            token = extractToken(messages),
            techList = tag.techList.map { it.substringAfterLast('.') },
            isWritable = ndef?.isWritable ?: (android.nfc.tech.NdefFormatable.get(tag) != null),
            maxNdefSize = ndef?.maxSize ?: 0,
            tag = tag
        )
    }

    private fun extractToken(messages: List<NdefMessage>): ByteArray? = messages
        .flatMap { it.records.asList() }
        .firstOrNull { record ->
            runCatching { String(record.type, Charsets.US_ASCII) }.getOrNull() == MIME_TYPE
        }
        ?.payload
        ?.takeIf { it.isNotEmpty() }

    private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(key, T::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(key)

    private fun Intent.parcelableArray(key: String): Array<out Parcelable>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableArrayExtra(key, Parcelable::class.java)
        else @Suppress("DEPRECATION") getParcelableArrayExtra(key)
}
