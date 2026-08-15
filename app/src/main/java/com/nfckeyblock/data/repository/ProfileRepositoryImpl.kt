package com.nfckeyblock.data.repository

import com.nfckeyblock.data.local.dao.ProfileDao
import com.nfckeyblock.data.local.entity.ProfileEntity
import com.nfckeyblock.domain.model.Profile
import com.nfckeyblock.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ProfileRepositoryImpl(private val dao: ProfileDao) : ProfileRepository {

    override fun observeProfiles(): Flow<List<Profile>> =
        dao.observeAll().flatMapLatest { entities ->
            if (entities.isEmpty()) flowOf(emptyList())
            else combine(entities.map { e -> dao.observeBlockedPackages(e.id).map { e.toDomain(it.toSet()) } }) {
                it.toList()
            }
        }

    override fun observeProfile(id: Long): Flow<Profile?> =
        combine(dao.observeById(id), dao.observeBlockedPackages(id)) { entity, pkgs ->
            entity?.toDomain(pkgs.toSet())
        }

    override suspend fun getProfile(id: Long): Profile? =
        dao.getById(id)?.toDomain(dao.blockedPackages(id).toSet())

    override suspend fun upsert(profile: Profile): Long {
        val id = dao.upsert(profile.toEntity())
        val resolvedId = if (id > 0) id else profile.id
        if (profile.blockedPackages.isNotEmpty() || profile.id != 0L) {
            dao.replaceBlocked(resolvedId, profile.blockedPackages)
        }
        return resolvedId
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun setBlockedPackages(profileId: Long, packages: Set<String>) =
        dao.replaceBlocked(profileId, packages)

    /** Idempotente: se llama en cada arranque para que nunca haya cero perfiles. */
    override suspend fun ensureDefaultProfile(): Long {
        dao.firstId()?.let { return it }
        return dao.upsert(
            ProfileEntity(
                name = "Concentración",
                colorArgb = 0xFF4F46E5.toInt(),
                emoji = "🎯",
                blockWebDomains = false,
                blockedDomainsCsv = DEFAULT_DOMAINS,
                guardSystemSettings = true,
                autoEndMinutes = 0
            )
        )
    }

    private companion object {
        const val DEFAULT_DOMAINS =
            "instagram.com,tiktok.com,x.com,twitter.com,reddit.com,youtube.com,facebook.com,twitch.tv"
    }
}

internal fun ProfileEntity.toDomain(blocked: Set<String>) = Profile(
    id = id,
    name = name,
    colorArgb = colorArgb,
    emoji = emoji,
    blockWebDomains = blockWebDomains,
    blockedDomains = blockedDomainsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
    guardSystemSettings = guardSystemSettings,
    autoEndMinutes = autoEndMinutes,
    blockedPackages = blocked
)

internal fun Profile.toEntity() = ProfileEntity(
    id = id,
    name = name,
    colorArgb = colorArgb,
    emoji = emoji,
    blockWebDomains = blockWebDomains,
    blockedDomainsCsv = blockedDomains.joinToString(","),
    guardSystemSettings = guardSystemSettings,
    autoEndMinutes = autoEndMinutes
)
