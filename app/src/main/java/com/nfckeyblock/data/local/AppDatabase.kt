package com.nfckeyblock.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nfckeyblock.data.local.dao.CardDao
import com.nfckeyblock.data.local.dao.ProfileDao
import com.nfckeyblock.data.local.dao.SessionDao
import com.nfckeyblock.data.local.entity.BlockAttemptEntity
import com.nfckeyblock.data.local.entity.BlockedAppEntity
import com.nfckeyblock.data.local.entity.NfcCardEntity
import com.nfckeyblock.data.local.entity.ProfileEntity
import com.nfckeyblock.data.local.entity.SessionEntity

@Database(
    entities = [
        ProfileEntity::class,
        BlockedAppEntity::class,
        NfcCardEntity::class,
        SessionEntity::class,
        BlockAttemptEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun cardDao(): CardDao
    abstract fun sessionDao(): SessionDao

    companion object {
        /**
         * Sin encriptación de BD por defecto: el almacenamiento interno ya está cifrado
         * en reposo desde Android 10 (FBE) y no guardamos secretos en claro aquí —
         * solo HMACs cuya clave vive en el Keystore. Ver docs/SEGURIDAD.md.
         */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "nfckeyblock.db")
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
