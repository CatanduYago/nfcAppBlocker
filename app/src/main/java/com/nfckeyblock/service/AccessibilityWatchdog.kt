package com.nfckeyblock.service

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils

/**
 * Observa Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES.
 *
 * Android no permite impedir que el usuario desactive un servicio de
 * accesibilidad, y no debería permitirlo: sería la puerta perfecta para el
 * malware. Lo que sí podemos hacer es enterarnos al instante y avisar de forma
 * visible, que es la única defensa honesta disponible sin ser device owner.
 */
class AccessibilityWatchdog(
    private val context: Context,
    private val onDisabled: () -> Unit
) {
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (!isServiceEnabled(context)) onDisabled()
        }
    }

    fun start() {
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            observer
        )
    }

    fun stop() {
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }

    companion object {
        /**
         * Fuente de verdad sobre si el servicio está activo. No se usa una
         * bandera estática porque el proceso puede haber muerto y renacer.
         */
        fun isServiceEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${BlockingAccessibilityService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
            return splitter.any { it.equals(expected, ignoreCase = true) }
        }
    }
}
