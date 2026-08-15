package com.nfckeyblock.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap

/**
 * Icono de app leído del PackageManager. Se cachea por paquete en la composición
 * para no volver a rasterizar en cada scroll; los drawables adaptativos son caros.
 */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val painter = remember(packageName) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            BitmapPainter(drawable.toBitmap(ICON_PX, ICON_PX).asImageBitmap())
        }.getOrNull()
    }
    if (painter != null) {
        Image(painter = painter, contentDescription = null, modifier = modifier)
    }
}

private const val ICON_PX = 128
