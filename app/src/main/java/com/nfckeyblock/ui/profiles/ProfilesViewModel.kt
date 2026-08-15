package com.nfckeyblock.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfckeyblock.domain.model.InstalledApp
import com.nfckeyblock.domain.model.Profile
import com.nfckeyblock.domain.repository.InstalledAppsRepository
import com.nfckeyblock.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfilesUiState(
    val profiles: List<Profile> = emptyList(),
    val apps: List<InstalledApp> = emptyList(),
    val editing: Profile? = null
)

class ProfilesViewModel(
    private val profiles: ProfileRepository,
    installedApps: InstalledAppsRepository
) : ViewModel() {

    private val editing = MutableStateFlow<Profile?>(null)
    private val apps = MutableStateFlow<List<InstalledApp>>(emptyList())

    val state: StateFlow<ProfilesUiState> = combine(
        profiles.observeProfiles(),
        apps,
        editing
    ) { list, appList, current ->
        ProfilesUiState(list, appList, current)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesUiState())

    init {
        viewModelScope.launch { apps.value = installedApps.loadLaunchableApps() }
    }

    fun newProfile() {
        editing.value = Profile(name = "", colorArgb = PALETTE.random(), emoji = EMOJIS.random())
    }

    fun edit(profile: Profile) { editing.value = profile }
    fun cancel() { editing.value = null }

    fun updateDraft(transform: (Profile) -> Profile) = editing.update { it?.let(transform) }

    fun save() {
        val draft = editing.value ?: return
        if (draft.name.isBlank()) return
        viewModelScope.launch {
            profiles.upsert(draft)
            editing.value = null
        }
    }

    fun delete(profile: Profile) = viewModelScope.launch {
        profiles.delete(profile.id)
    }

    private companion object {
        val PALETTE = listOf(0xFF4F46E5, 0xFF059669, 0xFFDC2626, 0xFFD97706, 0xFF7C3AED).map { it.toInt() }
        val EMOJIS = listOf("🎯", "📚", "💼", "🌙", "🏃", "🧘")
    }
}
