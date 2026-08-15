package com.nfckeyblock.domain.usecase

import com.nfckeyblock.data.prefs.SettingsRepository
import com.nfckeyblock.data.security.Fingerprinter
import com.nfckeyblock.domain.model.CardAction
import com.nfckeyblock.domain.model.NfcCard
import com.nfckeyblock.domain.model.SessionEndReason
import com.nfckeyblock.domain.repository.CardRepository
import com.nfckeyblock.domain.repository.ProfileRepository
import com.nfckeyblock.domain.repository.SessionRepository
import com.nfckeyblock.nfc.NfcTagIdentity
import kotlinx.coroutines.flow.first

/** Resultado de acercar una tarjeta, pensado para pintarse directamente en la UI. */
sealed interface TapOutcome {
    data class Activated(val profileName: String, val blockedCount: Int) : TapOutcome
    data class Deactivated(val durationMillis: Long) : TapOutcome
    data class UnknownCard(val uidHex: String) : TapOutcome
    data class Ignored(val reason: String) : TapOutcome
    data class Error(val message: String) : TapOutcome
}

/**
 * Corazón funcional de la app: traduce "tarjeta detectada" en un cambio de estado.
 * No conoce Android más allá del modelo de tarjeta, así que es testeable en JVM.
 */
class HandleCardTapUseCase(
    private val cards: CardRepository,
    private val profiles: ProfileRepository,
    private val sessions: SessionRepository,
    private val crypto: Fingerprinter,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(identity: NfcTagIdentity): TapOutcome {
        if (identity.uid.isEmpty() && identity.token == null) {
            return TapOutcome.Error("No se ha podido leer la tarjeta")
        }
        val uidFp = crypto.fingerprint(identity.uid)
        val tokenFp = identity.token?.let { crypto.fingerprint(it) }
        val card = cards.findByFingerprints(uidFp, tokenFp) ?: return TapOutcome.UnknownCard(identity.uidHex)

        cards.markUsed(card.id, now())
        val active = sessions.currentSession()

        return when (card.action) {
            CardAction.TOGGLE -> if (active == null) activate(card) else deactivate(active.startedAt)
            CardAction.ACTIVATE_ONLY ->
                if (active != null) TapOutcome.Ignored("Ya hay una sesión activa") else activate(card)
            CardAction.DEACTIVATE_ONLY ->
                if (active == null) TapOutcome.Ignored("No hay ninguna sesión activa")
                else deactivate(active.startedAt)
        }
    }

    private suspend fun activate(card: NfcCard): TapOutcome {
        val profileId = card.profileId ?: profiles.ensureDefaultProfile()
        val profile = profiles.getProfile(profileId) ?: return TapOutcome.Error("El perfil ya no existe")
        if (profile.blockedPackages.isEmpty()) {
            return TapOutcome.Error("El perfil «${profile.name}» no tiene apps seleccionadas")
        }
        sessions.start(profileId)
        return TapOutcome.Activated(profile.name, profile.blockedPackages.size)
    }

    private suspend fun deactivate(startedAt: Long): TapOutcome {
        sessions.end(SessionEndReason.CARD)
        return TapOutcome.Deactivated(now() - startedAt)
    }
}

/** Alta de tarjeta. Devuelve el id o null si esa tarjeta ya estaba registrada. */
class RegisterCardUseCase(
    private val cards: CardRepository,
    private val crypto: Fingerprinter
) {
    suspend operator fun invoke(
        identity: NfcTagIdentity,
        label: String,
        action: CardAction,
        profileId: Long?,
        writtenToken: ByteArray?
    ): Long? {
        val uidFp = crypto.fingerprint(identity.uid)
        val tokenFp = (writtenToken ?: identity.token)?.let { crypto.fingerprint(it) }
        if (cards.findByFingerprints(uidFp, tokenFp) != null) return null
        return cards.register(label, uidFp, tokenFp, action, profileId)
    }
}

/** Arranque manual desde la UI (útil sin NFC o para probar). */
class StartSessionUseCase(
    private val profiles: ProfileRepository,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository
) {
    suspend operator fun invoke(profileId: Long): Result<Unit> {
        val profile = profiles.getProfile(profileId)
            ?: return Result.failure(IllegalStateException("Perfil inexistente"))
        if (profile.blockedPackages.isEmpty()) {
            return Result.failure(IllegalStateException("Selecciona al menos una app"))
        }
        sessions.start(profileId)
        settings.setLastActiveProfile(profileId)
        return Result.success(Unit)
    }
}

/**
 * Desbloqueo de emergencia con retardo.
 *
 * Sin esto, perder la tarjeta dejaría el teléfono medio inutilizado; con un
 * botón inmediato, la tarjeta no serviría para nada. El retardo configurable
 * (15 min por defecto) es el equilibrio: sigue siendo una fricción real, pero
 * nunca un secuestro del dispositivo.
 */
class EmergencyUnlockUseCase(
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun request(): Long {
        val delayMinutes = settings.settings.first().emergencyDelayMinutes
        val availableAt = now() + delayMinutes * 60_000L
        sessions.requestEmergencyUnlock(availableAt)
        return sessions.currentSession()?.emergencyUnlockAt ?: availableAt
    }

    suspend fun cancel() = sessions.cancelEmergencyUnlock()

    suspend fun confirm(): Boolean {
        val session = sessions.currentSession() ?: return false
        val availableAt = session.emergencyUnlockAt ?: return false
        if (now() < availableAt) return false
        sessions.end(SessionEndReason.EMERGENCY)
        return true
    }
}
