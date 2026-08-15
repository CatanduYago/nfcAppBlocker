package com.nfckeyblock

import com.nfckeyblock.domain.model.ActiveSession
import com.nfckeyblock.domain.model.BlockingState
import com.nfckeyblock.domain.model.CardAction
import com.nfckeyblock.domain.model.NfcCard
import com.nfckeyblock.domain.model.Profile
import com.nfckeyblock.domain.model.SessionEndReason
import com.nfckeyblock.domain.model.StatsSummary
import com.nfckeyblock.domain.repository.CardRepository
import com.nfckeyblock.domain.repository.ProfileRepository
import com.nfckeyblock.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeCardRepository(initial: List<NfcCard> = emptyList()) : CardRepository {
    private val cards = MutableStateFlow(initial)
    var markedUsed: Long? = null

    override fun observeCards(): Flow<List<NfcCard>> = cards
    override suspend fun findByFingerprints(uidFingerprint: String, tokenFingerprint: String?): NfcCard? =
        cards.value.firstOrNull { it.tokenFingerprint != null && it.tokenFingerprint == tokenFingerprint }
            ?: cards.value.firstOrNull { it.uidFingerprint == uidFingerprint }

    override suspend fun register(
        label: String, uidFingerprint: String, tokenFingerprint: String?,
        action: CardAction, profileId: Long?
    ): Long {
        val id = (cards.value.maxOfOrNull { it.id } ?: 0) + 1
        cards.value = cards.value + NfcCard(id, label, uidFingerprint, tokenFingerprint, action, profileId, 0)
        return id
    }

    override suspend fun update(card: NfcCard) {
        cards.value = cards.value.map { if (it.id == card.id) card else it }
    }

    override suspend fun delete(id: Long) { cards.value = cards.value.filterNot { it.id == id } }
    override suspend fun markUsed(id: Long, at: Long) { markedUsed = id }
    override suspend fun count(): Int = cards.value.size
}

class FakeProfileRepository(initial: List<Profile> = emptyList()) : ProfileRepository {
    private val profiles = MutableStateFlow(initial)
    override fun observeProfiles(): Flow<List<Profile>> = profiles
    override fun observeProfile(id: Long): Flow<Profile?> = flowOf(profiles.value.firstOrNull { it.id == id })
    override suspend fun getProfile(id: Long): Profile? = profiles.value.firstOrNull { it.id == id }
    override suspend fun upsert(profile: Profile): Long {
        profiles.value = profiles.value.filterNot { it.id == profile.id } + profile
        return profile.id
    }
    override suspend fun delete(id: Long) { profiles.value = profiles.value.filterNot { it.id == id } }
    override suspend fun setBlockedPackages(profileId: Long, packages: Set<String>) {
        profiles.value = profiles.value.map { if (it.id == profileId) it.copy(blockedPackages = packages) else it }
    }
    override suspend fun ensureDefaultProfile(): Long = profiles.value.firstOrNull()?.id ?: 1
}

class FakeSessionRepository : SessionRepository {
    var session: ActiveSession? = null
    val endReasons = mutableListOf<SessionEndReason>()
    val attempts = mutableListOf<String>()

    override fun observeState(): Flow<BlockingState> = flowOf(BlockingState(session = session))
    override suspend fun currentSession(): ActiveSession? = session
    override suspend fun start(profileId: Long): Long {
        session = ActiveSession(1, profileId, 0)
        return 1
    }
    override suspend fun end(reason: SessionEndReason) {
        endReasons += reason
        session = null
    }
    override suspend fun requestEmergencyUnlock(availableAt: Long) {
        session = session?.copy(emergencyUnlockAt = availableAt)
    }
    override suspend fun cancelEmergencyUnlock() { session = session?.copy(emergencyUnlockAt = null) }
    override suspend fun recordAttempt(packageName: String) { attempts += packageName }
    override fun observeStats(): Flow<StatsSummary> = flowOf(StatsSummary(0, 0, 0, 0, emptyList(), 0))
}
