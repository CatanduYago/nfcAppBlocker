package com.nfckeyblock.util

import java.util.concurrent.TimeUnit

object Format {
    fun duration(millis: Long): String {
        if (millis <= 0) return "0 min"
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return when {
            hours > 0 -> "${hours} h ${minutes} min"
            minutes > 0 -> "$minutes min"
            else -> "${TimeUnit.MILLISECONDS.toSeconds(millis)} s"
        }
    }

    fun clock(millis: Long): String {
        val total = millis / 1000
        return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
    }
}
