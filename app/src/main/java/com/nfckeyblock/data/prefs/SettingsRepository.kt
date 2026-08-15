package com.nfckeyblock.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

data class AppSettings(
    val onboardingDone: Boolean = false,
    /** Minutos de espera antes de habilitar el desbloqueo de emergencia. */
    val emergencyDelayMinutes: Int = 15,
    /** Reanudar la sesión tras reiniciar el teléfono. */
    val resumeAfterReboot: Boolean = true,
    val hapticFeedback: Boolean = true,
    val showBlockNotification: Boolean = true,
    val useDynamicColor: Boolean = true,
    val lastActiveProfileId: Long = 0
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            onboardingDone = p[Keys.ONBOARDING] ?: false,
            emergencyDelayMinutes = p[Keys.EMERGENCY_DELAY] ?: 15,
            resumeAfterReboot = p[Keys.RESUME_REBOOT] ?: true,
            hapticFeedback = p[Keys.HAPTICS] ?: true,
            showBlockNotification = p[Keys.NOTIFICATION] ?: true,
            useDynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
            lastActiveProfileId = p[Keys.LAST_PROFILE] ?: 0
        )
    }

    suspend fun setOnboardingDone(value: Boolean) = edit { it[Keys.ONBOARDING] = value }
    suspend fun setEmergencyDelay(minutes: Int) = edit { it[Keys.EMERGENCY_DELAY] = minutes.coerceIn(0, 240) }
    suspend fun setResumeAfterReboot(value: Boolean) = edit { it[Keys.RESUME_REBOOT] = value }
    suspend fun setHaptics(value: Boolean) = edit { it[Keys.HAPTICS] = value }
    suspend fun setShowNotification(value: Boolean) = edit { it[Keys.NOTIFICATION] = value }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = value }
    suspend fun setLastActiveProfile(id: Long) = edit { it[Keys.LAST_PROFILE] = id }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private object Keys {
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val EMERGENCY_DELAY = intPreferencesKey("emergency_delay_minutes")
        val RESUME_REBOOT = booleanPreferencesKey("resume_after_reboot")
        val HAPTICS = booleanPreferencesKey("haptics")
        val NOTIFICATION = booleanPreferencesKey("show_notification")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LAST_PROFILE = longPreferencesKey("last_profile")
    }
}
