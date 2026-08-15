package com.nfckeyblock.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val emoji: String,
    val blockWebDomains: Boolean,
    val blockedDomainsCsv: String,
    val guardSystemSettings: Boolean,
    val autoEndMinutes: Int
)

@Entity(
    tableName = "blocked_apps",
    primaryKeys = ["profileId", "packageName"],
    foreignKeys = [ForeignKey(
        entity = ProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("profileId")]
)
data class BlockedAppEntity(
    val profileId: Long,
    val packageName: String
)

@Entity(
    tableName = "nfc_cards",
    indices = [Index(value = ["uidFingerprint"], unique = true), Index("profileId")],
    foreignKeys = [ForeignKey(
        entity = ProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.SET_NULL
    )]
)
data class NfcCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val uidFingerprint: String,
    val tokenFingerprint: String?,
    val action: String,
    val profileId: Long?,
    val createdAt: Long,
    val lastUsedAt: Long?
)

@Entity(tableName = "sessions", indices = [Index("startedAt"), Index("endedAt")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val endReason: String?,
    val emergencyUnlockAt: Long?
)

@Entity(
    tableName = "block_attempts",
    indices = [Index("sessionId"), Index("timestamp"), Index("packageName")]
)
data class BlockAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val packageName: String,
    val timestamp: Long
)
