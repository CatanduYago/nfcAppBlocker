package com.nfckeyblock.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfckeyblock.domain.model.InstalledApp
import com.nfckeyblock.domain.model.Profile
import com.nfckeyblock.domain.repository.InstalledAppsRepository
import com.nfckeyblock.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppsUiState(
    val loading: Boolean = true,
    val apps: List<InstalledApp> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: Long? = null,
    val blocked: Set<String> = emptySet(),
    val query: String = "",
    val showSystemApps: Boolean = false
) {
    val visibleApps: List<InstalledApp>
        get() = apps.filter { app ->
            (showSystemApps || !app.isSystem || app.packageName in blocked) &&
                (query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true))
        }
}

class AppsViewModel(
    private val installedApps: InstalledAppsRepository,
    private val profiles: ProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AppsUiState())
    val state: StateFlow<AppsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = installedApps.loadLaunchableApps()
            _state.update { it.copy(apps = apps, loading = false) }
        }
        viewModelScope.launch {
            profiles.observeProfiles().collect { list ->
                _state.update { current ->
                    val selected = current.selectedProfileId ?: list.firstOrNull()?.id
                    current.copy(
                        profiles = list,
                        selectedProfileId = selected,
                        blocked = list.firstOrNull { it.id == selected }?.blockedPackages.orEmpty()
                    )
                }
            }
        }
    }

    fun selectProfile(id: Long) = _state.update { current ->
        current.copy(
            selectedProfileId = id,
            blocked = current.profiles.firstOrNull { it.id == id }?.blockedPackages.orEmpty()
        )
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }
    fun toggleSystemApps() = _state.update { it.copy(showSystemApps = !it.showSystemApps) }

    fun toggle(packageName: String, blocked: Boolean) {
        val profileId = _state.value.selectedProfileId ?: return
        val updated = _state.value.blocked.toMutableSet().apply {
            if (blocked) add(packageName) else remove(packageName)
        }
        _state.update { it.copy(blocked = updated) }
        viewModelScope.launch { profiles.setBlockedPackages(profileId, updated) }
    }

    fun selectSuggested() {
        val profileId = _state.value.selectedProfileId ?: return
        val updated = _state.value.blocked + _state.value.apps.filter { it.isSuggested }.map { it.packageName }
        _state.update { it.copy(blocked = updated) }
        viewModelScope.launch { profiles.setBlockedPackages(profileId, updated) }
    }
}
