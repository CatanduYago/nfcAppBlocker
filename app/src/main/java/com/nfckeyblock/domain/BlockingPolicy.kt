package com.nfckeyblock.domain

import com.nfckeyblock.domain.model.BlockingState

/**
 * Decisión pura, sin dependencias de Android, sobre qué hacer cuando una ventana
 * pasa a primer plano. Separarla del servicio permite testear el corazón del
 * bloqueo con tests JVM rápidos.
 */
sealed interface BlockDecision {
    data object Allow : BlockDecision
    data class BlockApp(val packageName: String) : BlockDecision
    /** El usuario intenta llegar a los ajustes que desactivarían el bloqueo. */
    data object DeflectSettings : BlockDecision
}

object BlockingPolicy {

    /**
     * Paquetes que jamás se bloquean: hacerlo dejaría el teléfono inutilizable
     * o impediría atender una emergencia.
     */
    val NEVER_BLOCK: Set<String> = setOf(
        "com.android.systemui",
        "com.android.settings.intelligence",
        "com.android.inputmethod.latin",
        "com.google.android.inputmethod.latin",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.android.phone",
        "com.google.android.apps.wellbeing",
        "com.android.permissioncontroller"
    )

    /** Pantallas de ajustes cuya visita durante una sesión se considera evasión. */
    private val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
        "com.oppo.settings",
        "com.coloros.settings"
    )

    private val SENSITIVE_SETTINGS_HINTS = listOf(
        "accessibility",
        "installedappdetails",
        "applicationdetails",
        "appinfo",
        "manageapplications",
        "uninstall",
        "deviceadmin"
    )

    fun decide(
        state: BlockingState,
        foregroundPackage: String?,
        foregroundClass: String? = null,
        ownPackage: String,
        homePackages: Set<String> = emptySet()
    ): BlockDecision {
        if (!state.isBlocking) return BlockDecision.Allow
        val pkg = foregroundPackage?.takeIf { it.isNotBlank() } ?: return BlockDecision.Allow
        if (pkg == ownPackage) return BlockDecision.Allow
        if (pkg in NEVER_BLOCK) return BlockDecision.Allow
        if (pkg in homePackages) return BlockDecision.Allow

        if (pkg in state.blockedPackages) return BlockDecision.BlockApp(pkg)

        if (state.guardSystemSettings && pkg in SETTINGS_PACKAGES) {
            val cls = foregroundClass?.lowercase().orEmpty()
            if (SENSITIVE_SETTINGS_HINTS.any { it in cls }) return BlockDecision.DeflectSettings
        }
        return BlockDecision.Allow
    }

    /**
     * Comprobación de dominio para navegadores. Se compara sobre el texto de la
     * barra de direcciones, que puede venir sin esquema y con o sin "www.".
     */
    fun isDomainBlocked(urlBarText: String?, blockedDomains: Set<String>): Boolean {
        if (urlBarText.isNullOrBlank() || blockedDomains.isEmpty()) return false
        val host = urlBarText.trim()
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .removePrefix("www.")
            .lowercase()
        if (host.isEmpty()) return false
        return blockedDomains.any { raw ->
            val d = raw.removePrefix("www.").lowercase()
            host == d || host.endsWith(".$d")
        }
    }
}
