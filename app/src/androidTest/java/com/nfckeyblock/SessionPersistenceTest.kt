package com.nfckeyblock

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nfckeyblock.data.local.AppDatabase
import com.nfckeyblock.data.local.entity.ProfileEntity
import com.nfckeyblock.data.repository.SessionRepositoryImpl
import com.nfckeyblock.domain.model.SessionEndReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprueba la propiedad de la que depende toda la resistencia a evasiones:
 * el estado de bloqueo vive en disco, así que matar el proceso no lo borra.
 */
@RunWith(AndroidJUnit4::class)
class SessionPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: SessionRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = SessionRepositoryImpl(db.sessionDao(), db.profileDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedProfile(): Long = db.profileDao().upsert(
        ProfileEntity(
            name = "Test", colorArgb = 0, emoji = "🎯", blockWebDomains = false,
            blockedDomainsCsv = "", guardSystemSettings = true, autoEndMinutes = 0
        )
    ).also { id -> db.profileDao().replaceBlocked(id, setOf("com.instagram.android")) }

    @Test
    fun sesionActivaSeExponeEnElEstado() = runTest {
        val profileId = seedProfile()
        repository.start(profileId)

        val state = repository.observeState().first()
        assertThat(state.isBlocking).isTrue()
        assertThat(state.blockedPackages).containsExactly("com.instagram.android")
        assertThat(state.profileName).isEqualTo("Test")
    }

    @Test
    fun unaNuevaInstanciaDelRepositorioVeLaMismaSesion() = runTest {
        val profileId = seedProfile()
        repository.start(profileId)

        // Simula que el proceso murió y el servicio se recreó desde cero.
        val revived = SessionRepositoryImpl(db.sessionDao(), db.profileDao())
        assertThat(revived.currentSession()).isNotNull()
    }

    @Test
    fun noSeAbrenDosSesionesALaVez() = runTest {
        val profileId = seedProfile()
        val first = repository.start(profileId)
        val second = repository.start(profileId)
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun cerrarLaSesionLiberaElEstado() = runTest {
        val profileId = seedProfile()
        repository.start(profileId)
        repository.end(SessionEndReason.CARD)

        assertThat(repository.currentSession()).isNull()
        assertThat(repository.observeState().first().isBlocking).isFalse()
    }

    @Test
    fun losIntentosSeAsocianALaSesionAbierta() = runTest {
        val profileId = seedProfile()
        repository.start(profileId)
        repository.recordAttempt("com.instagram.android")
        repository.recordAttempt("com.instagram.android")

        val stats = repository.observeStats().first()
        assertThat(stats.attemptCount).isEqualTo(2)
        assertThat(stats.topAttempts.first().first).isEqualTo("com.instagram.android")
    }
}
