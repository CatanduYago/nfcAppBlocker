package com.nfckeyblock

import com.google.common.truth.Truth.assertThat
import com.nfckeyblock.data.prefs.AppSettings
import com.nfckeyblock.data.prefs.SettingsRepository
import com.nfckeyblock.domain.model.SessionEndReason
import com.nfckeyblock.domain.usecase.EmergencyUnlockUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * El desbloqueo de emergencia es la válvula de seguridad de toda la app:
 * si se pudiera acelerar, la tarjeta dejaría de significar nada.
 */
@RunWith(RobolectricTestRunner::class)
class EmergencyUnlockUseCaseTest {

    private val settings = SettingsRepository(RuntimeEnvironment.getApplication())

    @Test
    fun `no se puede confirmar antes de que venza el retardo`() = runTest {
        settings.setEmergencyDelay(15)
        val sessions = FakeSessionRepository().apply { start(1) }
        var now = 1_000L
        val useCase = EmergencyUnlockUseCase(sessions, settings) { now }

        useCase.request()
        assertThat(useCase.confirm()).isFalse()
        assertThat(sessions.session).isNotNull()

        now += 15 * 60_000L
        assertThat(useCase.confirm()).isTrue()
        assertThat(sessions.endReasons).containsExactly(SessionEndReason.EMERGENCY)
    }

    @Test
    fun `volver a solicitar no acorta la cuenta atras`() = runTest {
        settings.setEmergencyDelay(30)
        val sessions = FakeSessionRepository().apply { start(1) }
        var now = 0L
        val useCase = EmergencyUnlockUseCase(sessions, settings) { now }

        val first = useCase.request()
        now = 29 * 60_000L
        settings.setEmergencyDelay(0)
        val second = useCase.request()

        assertThat(second).isEqualTo(first)
        assertThat(useCase.confirm()).isFalse()
    }

    @Test
    fun `sin solicitud previa no hay desbloqueo`() = runTest {
        val sessions = FakeSessionRepository().apply { start(1) }
        val useCase = EmergencyUnlockUseCase(sessions, settings) { 0 }
        assertThat(useCase.confirm()).isFalse()
    }

    @Test
    fun `los ajustes por defecto son los esperados`() {
        assertThat(AppSettings().emergencyDelayMinutes).isEqualTo(15)
        assertThat(AppSettings().resumeAfterReboot).isTrue()
    }
}
