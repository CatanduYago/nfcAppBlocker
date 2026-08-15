package com.nfckeyblock.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.nfckeyblock.service.AccessibilityWatchdog

data class PermissionStatus(
    val accessibilityEnabled: Boolean,
    val notificationsEnabled: Boolean,
    val usageStatsGranted: Boolean,
    val overlayGranted: Boolean,
    val nfcAvailable: Boolean,
    val nfcEnabled: Boolean,
    val batteryUnrestricted: Boolean
) {
    /** Lo mínimo imprescindible para que el bloqueo funcione de verdad. */
    val readyToBlock: Boolean get() = accessibilityEnabled
}

object Permissions {

    fun status(context: Context): PermissionStatus {
        val nfc = NfcAdapter.getDefaultAdapter(context)
        return PermissionStatus(
            accessibilityEnabled = AccessibilityWatchdog.isServiceEnabled(context),
            notificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == 0
            } else true,
            usageStatsGranted = hasUsageStats(context),
            overlayGranted = Settings.canDrawOverlays(context),
            nfcAvailable = nfc != null,
            nfcEnabled = nfc?.isEnabled == true,
            batteryUnrestricted = isBatteryUnrestricted(context)
        )
    }

    private fun hasUsageStats(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isBatteryUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(android.os.PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun usageAccessIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun overlayIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun nfcSettingsIntent(): Intent =
        Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun batteryOptimizationIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
