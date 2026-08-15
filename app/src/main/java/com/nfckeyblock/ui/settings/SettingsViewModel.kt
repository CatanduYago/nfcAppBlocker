package com.nfckeyblock.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfckeyblock.data.prefs.AppSettings
import com.nfckeyblock.data.prefs.SettingsRepository
import com.nfckeyblock.domain.repository.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val sessionActive: Boolean = false
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    sessions: SessionRepository
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        repository.settings,
        sessions.observeState()
    ) { settings, blocking ->
        SettingsUiState(settings, blocking.isBlocking)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setEmergencyDelay(minutes: Int) = viewModelScope.launch { repository.setEmergencyDelay(minutes) }
    fun setResumeAfterReboot(value: Boolean) = viewModelScope.launch { repository.setResumeAfterReboot(value) }
    fun setHaptics(value: Boolean) = viewModelScope.launch { repository.setHaptics(value) }
    fun setNotification(value: Boolean) = viewModelScope.launch { repository.setShowNotification(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { repository.setDynamicColor(value) }
    fun completeOnboarding() = viewModelScope.launch { repository.setOnboardingDone(true) }
}
