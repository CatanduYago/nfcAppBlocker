package com.nfckeyblock.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.nfckeyblock.domain.model.InstalledApp
import com.nfckeyblock.domain.repository.InstalledAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enumera únicamente apps con lanzador. No usamos QUERY_ALL_PACKAGES (permiso
 * sensible en Play): con el <queries> del manifest esta consulta ya devuelve
 * todo lo que el usuario puede abrir, que es todo lo que se puede bloquear.
 */
class InstalledAppsRepositoryImpl(private val context: Context) : InstalledAppsRepository {

    override suspend fun loadLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        }
        resolved.asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = runCatching { pm.getApplicationLabel(info).toString() }
                        .getOrDefault(info.packageName),
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    isSuggested = SUGGESTED.any { info.packageName.startsWith(it) }
                )
            }
            .sortedWith(compareByDescending<InstalledApp> { it.isSuggested }.thenBy { it.label.lowercase() })
            .toList()
    }

    override fun iconKeyFor(packageName: String): String = packageName

    private companion object {
        /** Sugerencias iniciales para el onboarding: las sospechosas habituales. */
        val SUGGESTED = listOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc",
            "com.google.android.youtube",
            "com.reddit.frontpage",
            "com.twitter.android",
            "com.facebook.katana",
            "com.snapchat.android",
            "tv.twitch.android",
            "com.discord",
            "com.netflix.mediaclient",
            "com.pinterest",
            "com.linkedin.android"
        )
    }
}
