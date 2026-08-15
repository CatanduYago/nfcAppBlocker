package com.nfckeyblock.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nfckeyblock.di.AppContainer
import com.nfckeyblock.ui.apps.AppsViewModel
import com.nfckeyblock.ui.cards.CardsViewModel
import com.nfckeyblock.ui.home.HomeViewModel
import com.nfckeyblock.ui.profiles.ProfilesViewModel
import com.nfckeyblock.ui.settings.SettingsViewModel
import com.nfckeyblock.ui.stats.StatsViewModel

/** Fábrica única: sin Hilt, un solo sitio donde se cablean los ViewModels. */
fun appViewModelFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        HomeViewModel(
            container.sessionRepository,
            container.profileRepository,
            container.startSession,
            container.emergencyUnlock,
            container.cardRepository
        )
    }
    initializer { AppsViewModel(container.installedAppsRepository, container.profileRepository) }
    initializer { CardsViewModel(container.cardRepository, container.profileRepository, container.registerCard, container.cardCrypto) }
    initializer { ProfilesViewModel(container.profileRepository, container.installedAppsRepository) }
    initializer { StatsViewModel(container.sessionRepository, container.installedAppsRepository) }
    initializer { SettingsViewModel(container.settingsRepository, container.sessionRepository) }
}
