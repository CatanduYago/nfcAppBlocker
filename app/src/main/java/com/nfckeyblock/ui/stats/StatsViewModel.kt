package com.nfckeyblock.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfckeyblock.domain.model.StatsSummary
import com.nfckeyblock.domain.repository.InstalledAppsRepository
import com.nfckeyblock.domain.repository.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(
    sessions: SessionRepository,
    private val installedApps: InstalledAppsRepository
) : ViewModel() {

    val state: StateFlow<StatsSummary> = sessions.observeStats()
        .map { it }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            StatsSummary(0, 0, 0, 0, emptyList(), 0)
        )
}
