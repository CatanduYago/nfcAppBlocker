package com.nfckeyblock.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.nfckeyblock.data.local.entity.BlockAttemptEntity
import com.nfckeyblock.data.local.entity.BlockedAppEntity
import com.nfckeyblock.data.local.entity.NfcCardEntity
import com.nfckeyblock.data.local.entity.ProfileEntity
import com.nfckeyblock.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observeById(id: Long): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Query("SELECT id FROM profiles ORDER BY id LIMIT 1")
    suspend fun firstId(): Long?

    @Upsert
    suspend fun upsert(profile: ProfileEntity): Long

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT packageName FROM blocked_apps WHERE profileId = :profileId")
    suspend fun blockedPackages(profileId: Long): List<String>

    @Query("SELECT packageName FROM blocked_apps WHERE profileId = :profileId")
    fun observeBlockedPackages(profileId: Long): Flow<List<String>>

    @Query("DELETE FROM blocked_apps WHERE profileId = :profileId")
    suspend fun clearBlocked(profileId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlocked(rows: List<BlockedAppEntity>)

    @Transaction
    suspend fun replaceBlocked(profileId: Long, packages: Set<String>) {
        clearBlocked(profileId)
        insertBlocked(packages.map { BlockedAppEntity(profileId, it) })
    }
}

@Dao
interface CardDao {
    @Query("SELECT * FROM nfc_cards ORDER BY createdAt")
    fun observeAll(): Flow<List<NfcCardEntity>>

    @Query("SELECT * FROM nfc_cards WHERE uidFingerprint = :uid LIMIT 1")
    suspend fun findByUid(uid: String): NfcCardEntity?

    @Query("SELECT * FROM nfc_cards WHERE tokenFingerprint = :token LIMIT 1")
    suspend fun findByToken(token: String): NfcCardEntity?

    @Query("SELECT COUNT(*) FROM nfc_cards")
    suspend fun count(): Int

    @Insert
    suspend fun insert(card: NfcCardEntity): Long

    @Update
    suspend fun update(card: NfcCardEntity)

    @Query("DELETE FROM nfc_cards WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE nfc_cards SET lastUsedAt = :at WHERE id = :id")
    suspend fun markUsed(id: Long, at: Long)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): SessionEntity?

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("UPDATE sessions SET endedAt = :at, endReason = :reason WHERE id = :id")
    suspend fun close(id: Long, at: Long, reason: String)

    @Query("UPDATE sessions SET emergencyUnlockAt = :at WHERE id = :id")
    suspend fun setEmergencyUnlockAt(id: Long, at: Long?)

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionEntity>>

    @Query("SELECT COALESCE(SUM(COALESCE(endedAt, :now) - startedAt), 0) FROM sessions")
    fun observeTotalBlockedMillis(now: Long): Flow<Long>

    @Query("SELECT COALESCE(MAX(COALESCE(endedAt, :now) - startedAt), 0) FROM sessions")
    fun observeLongestMillis(now: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM sessions")
    fun observeSessionCount(): Flow<Int>

    @Insert
    suspend fun insertAttempt(attempt: BlockAttemptEntity)

    @Query("SELECT COUNT(*) FROM block_attempts")
    fun observeAttemptCount(): Flow<Int>

    @Query(
        "SELECT packageName, COUNT(*) AS hits FROM block_attempts " +
            "GROUP BY packageName ORDER BY hits DESC LIMIT :limit"
    )
    fun observeTopAttempts(limit: Int): Flow<List<PackageCount>>

    @Query("SELECT startedAt FROM sessions ORDER BY startedAt DESC LIMIT 200")
    fun observeSessionStarts(): Flow<List<Long>>
}

data class PackageCount(val packageName: String, val hits: Int)
