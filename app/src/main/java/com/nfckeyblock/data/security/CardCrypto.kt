package com.nfckeyblock.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Convierte identificadores de tarjeta (UID o token NDEF) en huellas irreversibles.
 *
 * Por qué HMAC y no un hash simple:
 *  - Un UID tiene 4 o 7 bytes. Un SHA-256 pelado de un espacio tan pequeño es
 *    trivial de invertir por fuerza bruta (2^56 como mucho, y en la práctica los
 *    UID reales están muy sesgados por fabricante).
 *  - Con HMAC y una clave que vive dentro del Keystore y no es exportable, quien
 *    lea la base de datos no puede reconstruir el UID ni fabricar una entrada válida.
 *
 * Lo que esto NO resuelve: el UID viaja en claro por el aire y es clonable.
 * Ver [docs/SEGURIDAD.md] y la nota sobre NTAG 424 DNA / DESFire.
 */
/** Abstracción mínima para poder testear los casos de uso sin Keystore. */
interface Fingerprinter {
    fun fingerprint(bytes: ByteArray): String
}

class CardCrypto(private val keyAlias: String = DEFAULT_ALIAS) : Fingerprinter {

    override fun fingerprint(bytes: ByteArray): String {
        val mac = Mac.getInstance(MAC_ALGORITHM)
        mac.init(secretKey())
        return Base64.encodeToString(mac.doFinal(bytes), Base64.NO_WRAP)
    }

    /** Token aleatorio de 32 bytes para escribir en tarjetas NDEF regrabables. */
    fun newCardToken(): ByteArray = ByteArray(TOKEN_BYTES).also {
        java.security.SecureRandom().nextBytes(it)
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN)
                .setKeySize(256)
                // Sin setUserAuthenticationRequired: el servicio debe poder verificar
                // una tarjeta con la pantalla bloqueada, sin pedir huella al usuario.
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MAC_ALGORITHM = "HmacSHA256"
        private const val DEFAULT_ALIAS = "nfckeyblock_card_hmac_v1"
        private const val TOKEN_BYTES = 32
    }
}
