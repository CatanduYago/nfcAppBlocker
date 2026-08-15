package com.nfckeyblock.data.repository

import com.nfckeyblock.data.local.dao.ProfileDao
import com.nfckeyblock.data.local.dao.SessionDao
import com.nfckeyblock.data.local.entity.BlockAttemptEntity
import com.nfckeyblock.data.local.entity.SessionEntity
import com.nfckeyblock.domain.model.ActiveSession
import com.nfckeyblock.domain.model.BlockingState
import com.nfckeyblock.domain.model.SessionEndReason
import com.nfckeyblock.domain.model.StatsSummary
import com.nfckeyblock.domain.repository.SessionRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class SessionRepositoryImpl(
    private val sessionDao: SessionDao,
    private val profileDao: ProfileDao,
    private val now: () -> Long = System::currentTimeMillis
) : SessionRepository {

    /**
     * El estado vive en la base de datos, no en memoria. Esto es lo que hace que
     * cerrar la app desde recientes o matar el proceso no levante el bloqueo:
     * al reiniciarse el servicio vuelve a leer exactamente el mismo estado.
     */
    override fun observeState(): Flow<BlockingState> =
        sessionDao.observeActive().flatMapLatest { session ->
            if (session == null) flowOf(BlockingState())
            else combine(
                profileDao.observeById(session.profileId),
                profileDao.observeBlockedPackages(session.profileId)
            ) { profile, packages ->
                BlockingState(
                    session = ActiveSession(
                        id = session.id,
                        profileId = session.profileId,
                        startedAt = session.startedAt,
                        emergencyUnlockAt = session.emergencyUnlockAt
                    ),
                    profileName = profile?.name.orEmpty(),
                    blockedPackages = packages.toSet(),
                    blockedDomains = profile?.blockedDomainsCsv
                        ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty(),
                    guardSystemSettings = profile?.guardSystemSettings ?: true,
                    blockWebDomains = profile?.blockWebDomains ?: false
                )
            }
        }

    override suspend fun currentSession(): ActiveSession? = sessionDao.getActive()?.let {
        ActiveSession(it.id, it.profileId, it.startedAt, it.emergencyUnlockAt)
    }

    override suspend fun start(profileId: Long): Long {
        sessionDao.getActive()?.let { return it.id }
        return sessionDao.insert(
            SessionEntity(
                profileId = profileId,
                startedAt = now(),
                endedAt = null,
                endReason = null,
                emergencyUnlockAt = null
            )
        )
    }

    override suspend fun end(reason: SessionEndReason) {
        val active = sessionDao.getActive() ?: return
        sessionDao.close(active.id, now(), reason.name)
    }

    override suspend fun requestEmergencyUnlock(availableAt: Long) {
        val active = sessionDao.getActive() ?: return
        if (active.emergencyUnlockAt != null) return // no se puede reiniciar la cuenta atrás a la baja
        sessionDao.setEmergencyUnlockAt(active.id, availableAt)
    }

    override suspend fun cancelEmergencyUnlock() {
        val active = sessionDao.getActive() ?: return
        sessionDao.setEmergencyUnlockAt(active.id, null)
    }

    override suspend fun recordAttempt(packageName: String) {
        val active = sessionDao.getActive() ?: return
        sessionDao.insertAttempt(BlockAttemptEntity(sessionId = active.id, packageName = packageName, timestamp = now()))
    }

    override fun observeStats(): Flow<StatsSummary> {
        val reference = now()
        // Se combina en dos pasos porque la stdlib solo tipa combine hasta 5 flujos.
        val totals = combine(
            sessionDao.observeTotalBlockedMillis(reference),
            sessionDao.observeLongestMillis(reference),
            sessionDao.observeSessionCount(),
            sessionDao.observeAttemptCount(),
            sessionDao.observeTopAttempts(TOP_APPS)
        ) { total, longest, sessions, attempts, top ->
            StatsSummary(
                totalBlockedMillis = total,
                longestSessionMillis = longest,
                sessionCount = sessions,
                attemptCount = attempts,
                topAttempts = top.map { it.packageName to it.hits },
                currentStreakDays = 0
            )
        }
        return combine(totals, sessionDao.observeSessionStarts()) { summary, starts ->
            summary.copy(currentStreakDays = streakOf(starts))
        }
    }

    /** Días consecutivos (hasta hoy o ayer) con al menos una sesión. */
    private fun streakOf(starts: List<Long>): Int {
        if (starts.isEmpty()) return 0
        val days = starts.map { dayIndex(it) }.toSortedSet().reversed()
        val today = dayIndex(now())
        var expected = when (days.first()) {
            today -> today
            today - 1 -> today - 1
            else -> return 0
        }
        var streak = 0
        for (day in days) {
            if (day == expected) {
                streak++
                expected--
            } else if (day < expected) break
        }
        return streak
    }

    private companion object { const val TOP_APPS = 5 }

    private fun dayIndex(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return TimeUnit.MILLISECONDS.toDays(cal.timeInMillis)
    }
}
