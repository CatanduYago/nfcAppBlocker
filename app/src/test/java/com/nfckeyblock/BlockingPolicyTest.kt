package com.nfckeyblock

import com.google.common.truth.Truth.assertThat
import com.nfckeyblock.domain.BlockDecision
import com.nfckeyblock.domain.BlockingPolicy
import com.nfckeyblock.domain.model.ActiveSession
import com.nfckeyblock.domain.model.BlockingState
import org.junit.Test

/** El corazón del bloqueo. Si algo aquí falla, la app deja pasar apps bloqueadas. */
class BlockingPolicyTest {

    private val session = ActiveSession(id = 1, profileId = 1, startedAt = 0)
    private val blocking = BlockingState(
        session = session,
        profileName = "Trabajo",
        blockedPackages = setOf("com.instagram.android", "com.reddit.frontpage"),
        blockedDomains = setOf("instagram.com", "reddit.com")
    )

    @Test
    fun `sin sesion no se bloquea nada`() {
        val decision = BlockingPolicy.decide(BlockingState(), "com.instagram.android", null, OWN)
        assertThat(decision).isEqualTo(BlockDecision.Allow)
    }

    @Test
    fun `app en la lista se bloquea`() {
        val decision = BlockingPolicy.decide(blocking, "com.instagram.android", null, OWN)
        assertThat(decision).isEqualTo(BlockDecision.BlockApp("com.instagram.android"))
    }

    @Test
    fun `app fuera de la lista pasa`() {
        val decision = BlockingPolicy.decide(blocking, "com.spotify.music", null, OWN)
        assertThat(decision).isEqualTo(BlockDecision.Allow)
    }

    @Test
    fun `la propia app nunca se bloquea a si misma`() {
        val state = blocking.copy(blockedPackages = blocking.blockedPackages + OWN)
        assertThat(BlockingPolicy.decide(state, OWN, null, OWN)).isEqualTo(BlockDecision.Allow)
    }

    @Test
    fun `el telefono y la interfaz del sistema siguen accesibles`() {
        val state = blocking.copy(blockedPackages = setOf("com.android.dialer", "com.android.systemui"))
        assertThat(BlockingPolicy.decide(state, "com.android.dialer", null, OWN)).isEqualTo(BlockDecision.Allow)
        assertThat(BlockingPolicy.decide(state, "com.android.systemui", null, OWN)).isEqualTo(BlockDecision.Allow)
    }

    @Test
    fun `el launcher nunca se bloquea aunque este en la lista`() {
        val state = blocking.copy(blockedPackages = setOf("com.launcher"))
        val decision = BlockingPolicy.decide(state, "com.launcher", null, OWN, setOf("com.launcher"))
        assertThat(decision).isEqualTo(BlockDecision.Allow)
    }

    @Test
    fun `ir a los ajustes de accesibilidad se desvia`() {
        val decision = BlockingPolicy.decide(
            blocking, "com.android.settings",
            "com.android.settings.accessibility.AccessibilitySettings", OWN
        )
        assertThat(decision).isEqualTo(BlockDecision.DeflectSettings)
    }

    @Test
    fun `otros ajustes siguen siendo accesibles`() {
        val decision = BlockingPolicy.decide(
            blocking, "com.android.settings", "com.android.settings.wifi.WifiSettings", OWN
        )
        assertThat(decision).isEqualTo(BlockDecision.Allow)
    }

    @Test
    fun `con la guardia desactivada los ajustes no se desvian`() {
        val state = blocking.copy(guardSystemSettings = false)
        val decision = BlockingPolicy.decide(
            state, "com.android.settings",
            "com.android.settings.accessibility.AccessibilitySettings", OWN
        )
        assertThat(decision).isEqualTo(BlockDecision.Allow)
    }

    @Test
    fun `paquete nulo o vacio no rompe nada`() {
        assertThat(BlockingPolicy.decide(blocking, null, null, OWN)).isEqualTo(BlockDecision.Allow)
        assertThat(BlockingPolicy.decide(blocking, "  ", null, OWN)).isEqualTo(BlockDecision.Allow)
    }

    @Test
    fun `dominios bloqueados con y sin esquema o subdominio`() {
        val domains = setOf("instagram.com")
        assertThat(BlockingPolicy.isDomainBlocked("https://www.instagram.com/explore", domains)).isTrue()
        assertThat(BlockingPolicy.isDomainBlocked("instagram.com", domains)).isTrue()
        assertThat(BlockingPolicy.isDomainBlocked("m.instagram.com", domains)).isTrue()
        assertThat(BlockingPolicy.isDomainBlocked("https://notinstagram.com", domains)).isFalse()
        assertThat(BlockingPolicy.isDomainBlocked("https://wikipedia.org", domains)).isFalse()
        assertThat(BlockingPolicy.isDomainBlocked(null, domains)).isFalse()
        assertThat(BlockingPolicy.isDomainBlocked("instagram.com", emptySet())).isFalse()
    }

    private companion object { const val OWN = "com.nfckeyblock" }
}
