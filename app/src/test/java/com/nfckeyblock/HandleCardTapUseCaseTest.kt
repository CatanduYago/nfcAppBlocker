package com.nfckeyblock

import com.google.common.truth.Truth.assertThat
import com.nfckeyblock.data.security.Fingerprinter
import com.nfckeyblock.domain.model.CardAction
import com.nfckeyblock.domain.model.NfcCard
import com.nfckeyblock.domain.model.Profile
import com.nfckeyblock.domain.model.SessionEndReason
import com.nfckeyblock.domain.usecase.HandleCardTapUseCase
import com.nfckeyblock.domain.usecase.TapOutcome
import com.nfckeyblock.nfc.NfcTagIdentity
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HandleCardTapUseCaseTest {

    /** Huella determinista: en test solo importa que sea estable y distinta por entrada. */
    private val crypto = object : Fingerprinter {
        override fun fingerprint(bytes: ByteArray) = "fp-" + bytes.joinToString("") { "%02x".format(it) }
    }

    private val profile = Profile(
        id = 7, name = "Trabajo", colorArgb = 0, blockedPackages = setOf("com.instagram.android")
    )
    private val uid = byteArrayOf(0x04, 0x11, 0x22, 0x33)
    private val identity = NfcTagIdentity(uid, null, listOf("NfcA"), false, 0)

    private fun card(action: CardAction = CardAction.TOGGLE, profileId: Long? = 7) = NfcCard(
        id = 1, label = "Trabajo", uidFingerprint = crypto.fingerprint(uid),
        tokenFingerprint = null, action = action, profileId = profileId, createdAt = 0
    )

    private fun useCase(
        cards: FakeCardRepository,
        sessions: FakeSessionRepository,
        profiles: FakeProfileRepository = FakeProfileRepository(listOf(profile)),
        clock: () -> Long = { 60_000L }
    ) = HandleCardTapUseCase(cards, profiles, sessions, crypto, clock)

    @Test
    fun `tarjeta conocida sin sesion activa arranca el perfil`() = runTest {
        val sessions = FakeSessionRepository()
        val outcome = useCase(FakeCardRepository(listOf(card())), sessions)(identity)

        assertThat(outcome).isInstanceOf(TapOutcome.Activated::class.java)
        assertThat((outcome as TapOutcome.Activated).profileName).isEqualTo("Trabajo")
        assertThat(outcome.blockedCount).isEqualTo(1)
        assertThat(sessions.session).isNotNull()
    }

    @Test
    fun `la misma tarjeta con sesion activa la termina`() = runTest {
        val sessions = FakeSessionRepository().apply { start(7) }
        val outcome = useCase(FakeCardRepository(listOf(card())), sessions)(identity)

        assertThat(outcome).isInstanceOf(TapOutcome.Deactivated::class.java)
        assertThat(sessions.session).isNull()
        assertThat(sessions.endReasons).containsExactly(SessionEndReason.CARD)
    }

    @Test
    fun `tarjeta desconocida no cambia el estado`() = runTest {
        val sessions = FakeSessionRepository()
        val outcome = useCase(FakeCardRepository(), sessions)(identity)

        assertThat(outcome).isInstanceOf(TapOutcome.UnknownCard::class.java)
        assertThat(sessions.session).isNull()
    }

    @Test
    fun `tarjeta de solo activar no desbloquea`() = runTest {
        val sessions = FakeSessionRepository().apply { start(7) }
        val outcome = useCase(FakeCardRepository(listOf(card(CardAction.ACTIVATE_ONLY))), sessions)(identity)

        assertThat(outcome).isInstanceOf(TapOutcome.Ignored::class.java)
        assertThat(sessions.session).isNotNull()
    }

    @Test
    fun `tarjeta de solo desactivar no arranca sesion`() = runTest {
        val sessions = FakeSessionRepository()
        val outcome = useCase(FakeCardRepository(listOf(card(CardAction.DEACTIVATE_ONLY))), sessions)(identity)

        assertThat(outcome).isInstanceOf(TapOutcome.Ignored::class.java)
        assertThat(sessions.session).isNull()
    }

    @Test
    fun `un perfil sin apps seleccionadas no arranca y avisa`() = runTest {
        val profiles = FakeProfileRepository(listOf(profile.copy(blockedPackages = emptySet())))
        val sessions = FakeSessionRepository()
        val outcome = useCase(FakeCardRepository(listOf(card())), sessions, profiles)(identity)

        assertThat(outcome).isInstanceOf(TapOutcome.Error::class.java)
        assertThat(sessions.session).isNull()
    }

    @Test
    fun `el token NDEF identifica la tarjeta aunque cambie el UID`() = runTest {
        val token = byteArrayOf(0x0A, 0x0B)
        val stored = card().copy(uidFingerprint = "fp-otro", tokenFingerprint = crypto.fingerprint(token))
        val randomUid = NfcTagIdentity(byteArrayOf(0x08, 0x01, 0x02, 0x03), token, listOf("NfcA"), true, 144)
        val sessions = FakeSessionRepository()

        val outcome = useCase(FakeCardRepository(listOf(stored)), sessions)(randomUid)

        assertThat(outcome).isInstanceOf(TapOutcome.Activated::class.java)
    }

    @Test
    fun `la duracion informada al desbloquear usa el reloj inyectado`() = runTest {
        val sessions = FakeSessionRepository().apply { start(7) } // startedAt = 0
        val outcome = useCase(FakeCardRepository(listOf(card())), sessions, clock = { 90_000L })(identity)

        assertThat((outcome as TapOutcome.Deactivated).durationMillis).isEqualTo(90_000L)
    }
}
