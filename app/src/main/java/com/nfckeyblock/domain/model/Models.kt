package com.nfckeyblock.domain.model

/** Acción que ejecuta una tarjeta al ser detectada. */
enum class CardAction {
    /** Si hay sesión activa la termina; si no, la inicia con el perfil asociado. */
    TOGGLE,
    /** Solo activa. Acercarla con una sesión ya activa no la desactiva (tarjeta "candado"). */
    ACTIVATE_ONLY,
    /** Solo desactiva. Útil para guardar una tarjeta "llave" en otra habitación. */
    DEACTIVATE_ONLY
}

enum class SessionEndReason { CARD, EMERGENCY, TIMER, MANUAL, UNKNOWN }

/**
 * Perfil de bloqueo. [blockedPackages] se resuelve por separado (tabla relacional).
 */
data class Profile(
    val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val emoji: String = "🎯",
    /** Bloquea también dominios en navegadores conocidos (mejor esfuerzo, ver limitaciones). */
    val blockWebDomains: Boolean = false,
    val blockedDomains: Set<String> = emptySet(),
    /** Impide navegar a los ajustes de accesibilidad mientras la sesión está activa. */
    val guardSystemSettings: Boolean = true,
    /** Duración máxima opcional en minutos; 0 = sin límite. */
    val autoEndMinutes: Int = 0,
    val blockedPackages: Set<String> = emptySet()
)

data class NfcCard(
    val id: Long = 0,
    val label: String,
    /** HMAC-SHA256 del UID con clave del Keystore. Nunca se guarda el UID en claro. */
    val uidFingerprint: String,
    /** HMAC del token NDEF escrito en la tarjeta, si se escribió. */
    val tokenFingerprint: String? = null,
    val action: CardAction,
    val profileId: Long?,
    val createdAt: Long,
    val lastUsedAt: Long? = null
)

data class ActiveSession(
    val id: Long,
    val profileId: Long,
    val startedAt: Long,
    /** Instante a partir del cual el desbloqueo de emergencia queda disponible; null = no solicitado. */
    val emergencyUnlockAt: Long? = null
)

/**
 * Instantánea que consume el AccessibilityService. Se mantiene deliberadamente plana
 * y sin dependencias de Android para poder decidir sin tocar disco en cada evento.
 */
data class BlockingState(
    val session: ActiveSession? = null,
    val profileName: String = "",
    val blockedPackages: Set<String> = emptySet(),
    val blockedDomains: Set<String> = emptySet(),
    val guardSystemSettings: Boolean = true,
    val blockWebDomains: Boolean = false
) {
    val isBlocking: Boolean get() = session != null
}

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isSuggested: Boolean
)

data class BlockAttempt(val packageName: String, val timestamp: Long, val sessionId: Long)

data class StatsSummary(
    val totalBlockedMillis: Long,
    val sessionCount: Int,
    val attemptCount: Int,
    val longestSessionMillis: Long,
    val topAttempts: List<Pair<String, Int>>,
    val currentStreakDays: Int
)
