package com.nfckeyblock.data.repository

import com.nfckeyblock.data.local.dao.CardDao
import com.nfckeyblock.data.local.entity.NfcCardEntity
import com.nfckeyblock.domain.model.CardAction
import com.nfckeyblock.domain.model.NfcCard
import com.nfckeyblock.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CardRepositoryImpl(private val dao: CardDao) : CardRepository {

    override fun observeCards(): Flow<List<NfcCard>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /**
     * Se busca primero por token NDEF (más entrópico) y se cae al UID.
     * Basta con que uno de los dos coincida: así una tarjeta registrada por UID
     * sigue funcionando aunque después se le escriba contenido NDEF, y viceversa.
     */
    override suspend fun findByFingerprints(uidFingerprint: String, tokenFingerprint: String?): NfcCard? {
        tokenFingerprint?.let { token -> dao.findByToken(token)?.let { return it.toDomain() } }
        return dao.findByUid(uidFingerprint)?.toDomain()
    }

    override suspend fun register(
        label: String,
        uidFingerprint: String,
        tokenFingerprint: String?,
        action: CardAction,
        profileId: Long?
    ): Long = dao.insert(
        NfcCardEntity(
            label = label,
            uidFingerprint = uidFingerprint,
            tokenFingerprint = tokenFingerprint,
            action = action.name,
            profileId = profileId,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = null
        )
    )

    override suspend fun update(card: NfcCard) = dao.update(card.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun markUsed(id: Long, at: Long) = dao.markUsed(id, at)
    override suspend fun count(): Int = dao.count()
}

private fun NfcCardEntity.toDomain() = NfcCard(
    id = id,
    label = label,
    uidFingerprint = uidFingerprint,
    tokenFingerprint = tokenFingerprint,
    action = runCatching { CardAction.valueOf(action) }.getOrDefault(CardAction.TOGGLE),
    profileId = profileId,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt
)

private fun NfcCard.toEntity() = NfcCardEntity(
    id = id,
    label = label,
    uidFingerprint = uidFingerprint,
    tokenFingerprint = tokenFingerprint,
    action = action.name,
    profileId = profileId,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt
)
