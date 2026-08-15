package com.nfckeyblock.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException

sealed interface WriteResult {
    data object Success : WriteResult
    data object ReadOnly : WriteResult
    data class TooSmall(val required: Int, val available: Int) : WriteResult
    data class Failed(val cause: Throwable) : WriteResult
}

/**
 * Escribe en la tarjeta un mensaje NDEF con dos registros:
 *
 *  1. Un registro MIME propio con un token aleatorio de 32 bytes.
 *  2. Un AAR (Android Application Record) con el id de paquete.
 *
 * El AAR es lo que hace que, con la app cerrada, el sistema abra ESTA app y
 * no un selector ni la Play Store de otra: es el mecanismo con el que Android
 * garantiza el destino del dispatch. El token no es un secreto (cualquiera con
 * el móvil pegado a la tarjeta lo lee), solo aporta entropía frente al UID.
 */
class NdefWriter {

    fun write(tag: Tag, token: ByteArray, packageName: String): WriteResult {
        val message = NdefMessage(
            arrayOf(
                NdefRecord.createMime(NfcTagParser.MIME_TYPE, token),
                NdefRecord.createApplicationRecord(packageName)
            )
        )
        val required = message.toByteArray().size

        Ndef.get(tag)?.let { ndef ->
            return try {
                ndef.connect()
                when {
                    !ndef.isWritable -> WriteResult.ReadOnly
                    ndef.maxSize < required -> WriteResult.TooSmall(required, ndef.maxSize)
                    else -> {
                        ndef.writeNdefMessage(message)
                        WriteResult.Success
                    }
                }
            } catch (e: IOException) {
                WriteResult.Failed(e)
            } catch (e: FormatException) {
                WriteResult.Failed(e)
            } finally {
                runCatching { ndef.close() }
            }
        }

        NdefFormatable.get(tag)?.let { formatable ->
            return try {
                formatable.connect()
                formatable.format(message)
                WriteResult.Success
            } catch (e: IOException) {
                WriteResult.Failed(e)
            } catch (e: FormatException) {
                WriteResult.Failed(e)
            } finally {
                runCatching { formatable.close() }
            }
        }

        return WriteResult.Failed(UnsupportedOperationException("La tarjeta no soporta NDEF"))
    }
}
