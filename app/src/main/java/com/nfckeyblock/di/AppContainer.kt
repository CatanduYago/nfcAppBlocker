package com.nfckeyblock.di

import android.content.Context
import com.nfckeyblock.data.local.AppDatabase
import com.nfckeyblock.data.prefs.SettingsRepository
import com.nfckeyblock.data.repository.CardRepositoryImpl
import com.nfckeyblock.data.repository.InstalledAppsRepositoryImpl
import com.nfckeyblock.data.repository.ProfileRepositoryImpl
import com.nfckeyblock.data.repository.SessionRepositoryImpl
import com.nfckeyblock.data.security.CardCrypto
import com.nfckeyblock.domain.repository.CardRepository
import com.nfckeyblock.domain.repository.InstalledAppsRepository
import com.nfckeyblock.domain.repository.ProfileRepository
import com.nfckeyblock.domain.repository.SessionRepository
import com.nfckeyblock.domain.usecase.EmergencyUnlockUseCase
import com.nfckeyblock.domain.usecase.HandleCardTapUseCase
import com.nfckeyblock.domain.usecase.RegisterCardUseCase
import com.nfckeyblock.domain.usecase.StartSessionUseCase
import com.nfckeyblock.util.Notifications

/**
 * Inyección de dependencias manual.
 *
 * Se descarta Hilt a propósito: el grafo tiene una docena de nodos, todos
 * singleton de aplicación, y los puntos de entrada más críticos
 * (AccessibilityService, BroadcastReceiver) los instancia el sistema. Un
 * contenedor explícito evita el procesador de anotaciones y hace obvio, leyendo
 * un solo fichero, qué construye qué y cuándo.
 */
class AppContainer(private val context: Context) {

    private val database: AppDatabase by lazy { AppDatabase.build(context) }

    val cardCrypto: CardCrypto by lazy { CardCrypto() }
    val notifications: Notifications by lazy { Notifications(context) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }

    val profileRepository: ProfileRepository by lazy { ProfileRepositoryImpl(database.profileDao()) }
    val cardRepository: CardRepository by lazy { CardRepositoryImpl(database.cardDao()) }
    val sessionRepository: SessionRepository by lazy {
        SessionRepositoryImpl(database.sessionDao(), database.profileDao())
    }
    val installedAppsRepository: InstalledAppsRepository by lazy { InstalledAppsRepositoryImpl(context) }

    val handleCardTap: HandleCardTapUseCase by lazy {
        HandleCardTapUseCase(cardRepository, profileRepository, sessionRepository, cardCrypto)
    }
    val registerCard: RegisterCardUseCase by lazy { RegisterCardUseCase(cardRepository, cardCrypto) }
    val startSession: StartSessionUseCase by lazy {
        StartSessionUseCase(profileRepository, sessionRepository, settingsRepository)
    }
    val emergencyUnlock: EmergencyUnlockUseCase by lazy {
        EmergencyUnlockUseCase(sessionRepository, settingsRepository)
    }
}
