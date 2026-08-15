package com.nfckeyblock.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** El launcher nunca se bloquea: hacerlo dejaría el teléfono sin salida. */
object HomePackages {
    fun resolve(context: Context): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return runCatching {
            context.packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }
}
