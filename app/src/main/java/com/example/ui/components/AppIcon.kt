package com.example.ui.components

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.util.getApplicationInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cache global em memória para ícones de aplicativos decodificados e redimensionados.
 * Evita chamadas repetidas de IPC ao PackageManager e elimina lag/jank durante a rolagem.
 */
object AppIconCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 16).coerceIn(1024, 8192) // 1MB - 8MB max
    private val lruCache = object : LruCache<String, ImageBitmap>(cacheSize) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.width * value.height * 4) / 1024
        }
    }

    fun get(packageName: String): ImageBitmap? = lruCache.get(packageName)

    fun put(packageName: String, bitmap: ImageBitmap) {
        lruCache.put(packageName, bitmap)
    }

    fun clear() {
        lruCache.evictAll()
    }
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) { mutableStateOf(AppIconCache.get(packageName)) }

    LaunchedEffect(packageName) {
        if (iconBitmap == null) {
            val cached = AppIconCache.get(packageName)
            if (cached != null) {
                iconBitmap = cached
            } else {
                withContext(Dispatchers.IO) {
                    try {
                        val pm = context.packageManager
                        val appInfo = pm.getApplicationInfoCompat(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                        val drawable = appInfo.loadIcon(pm)
                        val bitmap = drawableToBitmap(drawable, 96, 96)?.asImageBitmap()
                        if (bitmap != null) {
                            AppIconCache.put(packageName, bitmap)
                            withContext(Dispatchers.Main) {
                                iconBitmap = bitmap
                            }
                        }
                    } catch (e: Exception) {
                        // Ignora falhas de appInfo e usa ícone fallback
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = iconBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val fallbackColor = remember(packageName) {
                val colors = listOf(
                    Color(0xFF6366F1), Color(0xFF3B82F6), Color(0xFF10B981),
                    Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6)
                )
                colors[Math.abs(packageName.hashCode()) % colors.size]
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(fallbackColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_android),
                    contentDescription = null,
                    tint = fallbackColor,
                    modifier = Modifier.fillMaxSize(0.6f)
                )
            }
        }
    }
}

private fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap? {
    return try {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            Bitmap.createScaledBitmap(drawable.bitmap, width, height, true)
        } else {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap
        }
    } catch (e: Exception) {
        null
    }
}
