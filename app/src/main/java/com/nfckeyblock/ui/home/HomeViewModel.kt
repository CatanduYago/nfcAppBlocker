package com.nfckeyblock.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfckeyblock.domain.model.BlockingState
import com.nfckeyblock.domain.model.NfcCard
import com.nfckeyblock.domain.model.Profile
import com.nfckeyblock.domain.model.SessionEndReason
import com.nfckeyblock.domain.repository.CardRepository
import com.nfckeyblock.domain.repository.ProfileRepository
import com.nfckeyblock.domain.repository.SessionRepository
import com.nfckeyblock.domain.usecase.EmergencyUnlockUseCase
import com.nfckeyblock.domain.usecase.StartSessionUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiState(
    val state: BlockingState = BlockingState(),
    val profiles: List<Profile> = emptyList(),
    val cards: List<NfcCard> = emptyList(),
    val elapsedMillis: Long = 0,
    val emergencyRemainingMillis: Long? = null,
    val emergencyReady: Boolean = false
) {
    val hasCards: Boolean get() = cards.isNotEmpty()
}

class HomeViewModel(
    private val sessions: SessionRepository,
    profiles: ProfileRepository,
    private val startSession: StartSessionUseCase,
    private val emergency: EmergencyUnlockUseCase,
    cards: CardRepository
) : ViewModel() {

    private val tick = MutableStateFlow(System.currentTimeMillis())
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        sessions.observeState(),
        profiles.observeProfiles(),
        cards.observeCards(),
        tick
    ) { state, profileList, cardList, now ->
        val session = state.session
        val emergencyAt = session?.emergencyUnlockAt
        HomeUiState(
            state = state,
            profiles = profileList,
            cards = cardList,
            elapsedMillis = session?.let { now - it.startedAt } ?: 0,
            emergencyRemainingMillis = emergencyAt?.let { (it - now).coerceAtLeast(0) },
            emergencyReady = emergencyAt != null && now >= emergencyAt
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            while (isActive) {
                tick.value = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }

    fun startManual(profileId: Long) = viewModelScope.launch {
        startSession(profileId).onFailure { _messages.send(it.message ?: "No se pudo iniciar la sesión") }
    }

    /**
     * Terminar sin tarjeta desde la UI solo se permite si nunca se registró
     * ninguna: en cuanto hay tarjeta, la salida legítima es la tarjeta o el
     * desbloqueo de emergencia con retardo.
     */
    fun stopManual() = viewModelScope.launch {
        if (uiState.value.hasCards) {
            _messages.send("Acerca tu tarjeta o usa el desbloqueo de emergencia")
            return@launch
        }
        sessions.end(SessionEndReason.MANUAL)
    }

    fun requestEmergency() = viewModelScope.launch {
        val at = emergency.request()
        val minutes = ((at - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
        _messages.send("Desbloqueo disponible en $minutes min")
    }

    fun cancelEmergency() = viewModelScope.launch { emergency.cancel() }

    fun confirmEmergency() = viewModelScope.launch {
        if (!emergency.confirm()) _messages.send("Todavía no está disponible")
    }
}
