package com.nfckeyblock.domain.repository

import com.nfckeyblock.domain.model.ActiveSession
import com.nfckeyblock.domain.model.BlockingState
import com.nfckeyblock.domain.model.CardAction
import com.nfckeyblock.domain.model.InstalledApp
import com.nfckeyblock.domain.model.NfcCard
import com.nfckeyblock.domain.model.Profile
import com.nfckeyblock.domain.model.SessionEndReason
import com.nfckeyblock.domain.model.StatsSummary
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfiles(): Flow<List<Profile>>
    fun observeProfile(id: Long): Flow<Profile?>
    suspend fun getProfile(id: Long): Profile?
    suspend fun upsert(profile: Profile): Long
    suspend fun delete(id: Long)
    suspend fun setBlockedPackages(profileId: Long, packages: Set<String>)
    suspend fun ensureDefaultProfile(): Long
}

interface CardRepository {
    fun observeCards(): Flow<List<NfcCard>>
    suspend fun findByFingerprints(uidFingerprint: String, tokenFingerprint: String?): NfcCard?
    suspend fun register(label: String, uidFingerprint: String, tokenFingerprint: String?, action: CardAction, profileId: Long?): Long
    suspend fun update(card: NfcCard)
    suspend fun delete(id: Long)
    suspend fun markUsed(id: Long, at: Long)
    suspend fun count(): Int
}

interface SessionRepository {
    fun observeState(): Flow<BlockingState>
    suspend fun currentSession(): ActiveSession?
    suspend fun start(profileId: Long): Long
    suspend fun end(reason: SessionEndReason)
    suspend fun requestEmergencyUnlock(availableAt: Long)
    suspend fun cancelEmergencyUnlock()
    suspend fun recordAttempt(packageName: String)
    fun observeStats(): Flow<StatsSummary>
}

interface InstalledAppsRepository {
    suspend fun loadLaunchableApps(): List<InstalledApp>
    fun iconKeyFor(packageName: String): String
}
