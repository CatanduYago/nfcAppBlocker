package com.nfckeyblock.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfckeyblock.data.security.CardCrypto
import com.nfckeyblock.domain.model.CardAction
import com.nfckeyblock.domain.model.NfcCard
import com.nfckeyblock.domain.model.Profile
import com.nfckeyblock.domain.repository.CardRepository
import com.nfckeyblock.domain.repository.ProfileRepository
import com.nfckeyblock.domain.usecase.RegisterCardUseCase
import com.nfckeyblock.nfc.NfcTagIdentity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del asistente de registro; el escaneo es asíncrono y hay que reflejarlo. */
enum class ScanPhase { IDLE, WAITING, DETECTED, WAITING_WRITE, SAVED }

data class CardsUiState(
    val cards: List<NfcCard> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val phase: ScanPhase = ScanPhase.IDLE,
    val detected: NfcTagIdentity? = null,
    val detectedAlreadyRegistered: Boolean = false,
    val label: String = "",
    val action: CardAction = CardAction.TOGGLE,
    val profileId: Long? = null,
    val writeToCard: Boolean = true
)

class CardsViewModel(
    private val cards: CardRepository,
    profiles: ProfileRepository,
    private val registerCard: RegisterCardUseCase,
    private val crypto: CardCrypto
) : ViewModel() {

    private val local = MutableStateFlow(CardsUiState())
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Token pendiente de escribir en la tarjeta; lo consume la Activity, que es quien tiene el Tag. */
    var pendingToken: ByteArray? = null
        private set

    val state: StateFlow<CardsUiState> = combine(
        cards.observeCards(),
        profiles.observeProfiles(),
        local
    ) { cardList, profileList, current ->
        current.copy(
            cards = cardList,
            profiles = profileList,
            profileId = current.profileId ?: profileList.firstOrNull()?.id
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardsUiState())

    fun startScan() {
        pendingToken = null
        local.update { it.copy(phase = ScanPhase.WAITING, detected = null, label = "", detectedAlreadyRegistered = false) }
    }

    fun cancelScan() = local.update { it.copy(phase = ScanPhase.IDLE, detected = null) }

    fun onTagDetected(identity: NfcTagIdentity) {
        if (local.value.phase != ScanPhase.WAITING) return
        viewModelScope.launch {
            val existing = cards.findByFingerprints(
                crypto.fingerprint(identity.uid),
                identity.token?.let { crypto.fingerprint(it) }
            )
            local.update {
                it.copy(
                    phase = ScanPhase.DETECTED,
                    detected = identity,
                    detectedAlreadyRegistered = existing != null,
                    label = existing?.label ?: defaultLabel(it.cards.size),
                    writeToCard = identity.isWritable && identity.token == null
                )
            }
            if (identity.looksRandomUid && identity.token == null) {
                _messages.send("Esta tarjeta parece usar UID aleatorio: escribe el token para que sea reconocible")
            }
        }
    }

    fun setLabel(value: String) = local.update { it.copy(label = value) }
    fun setAction(action: CardAction) = local.update { it.copy(action = action) }
    fun setProfile(id: Long?) = local.update { it.copy(profileId = id) }
    fun setWriteToCard(value: Boolean) = local.update { it.copy(writeToCard = value) }

    /** Genera el token antes de escribir; la Activity lo escribirá en el próximo contacto. */
    fun prepareToken(): ByteArray = crypto.newCardToken().also { pendingToken = it }

    /** El usuario ha pulsado Guardar: o escribimos token (segundo toque) o se registra ya. */
    fun confirm(): ByteArray? {
        val current = local.value
        return if (current.writeToCard && current.detected?.isWritable == true) {
            local.update { it.copy(phase = ScanPhase.WAITING_WRITE) }
            prepareToken()
        } else {
            save(writtenToken = null)
            null
        }
    }

    fun onWriteFailed(reason: String) {
        pendingToken = null
        local.update { it.copy(phase = ScanPhase.DETECTED, writeToCard = false) }
        viewModelScope.launch { _messages.send(reason) }
    }

    fun save(writtenToken: ByteArray?) {
        val current = local.value
        val identity = current.detected ?: return
        viewModelScope.launch {
            val id = registerCard(
                identity = identity,
                label = current.label.ifBlank { defaultLabel(current.cards.size) },
                action = current.action,
                profileId = current.profileId,
                writtenToken = writtenToken
            )
            if (id == null) {
                _messages.send("Esa tarjeta ya estaba registrada")
                local.update { it.copy(phase = ScanPhase.IDLE) }
            } else {
                pendingToken = null
                local.update { it.copy(phase = ScanPhase.SAVED, detected = null) }
                _messages.send("Tarjeta guardada")
            }
        }
    }

    fun delete(card: NfcCard) = viewModelScope.launch {
        cards.delete(card.id)
        _messages.send("Tarjeta «${card.label}» eliminada")
    }

    fun rename(card: NfcCard, label: String) = viewModelScope.launch {
        cards.update(card.copy(label = label))
    }

    fun assign(card: NfcCard, profileId: Long?, action: CardAction) = viewModelScope.launch {
        cards.update(card.copy(profileId = profileId, action = action))
    }

    private fun defaultLabel(count: Int) = "Tarjeta ${count + 1}"
}
