package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import com.example.ui.components.AppIconCache

/**
 * Classe de aplicação personalizada do UninstallX.
 * Gerencia ciclo de vida global, liberação de memória sob pressão do sistema
 * e captura global de exceções para máxima estabilidade e prevenção de crashes.
 */
class UninstallXApp : Application() {

    companion object {
        private const val TAG = "UninstallXApp"
    }

    override fun onCreate() {
        super.onCreate()
        initCrashResilience()
    }

    private fun initCrashResilience() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Exceção não tratada capturada na thread '${thread.name}'", throwable)
            try {
                // Em caso de OOM fatal, limpa os caches de ícones imediatamente
                AppIconCache.clear()
            } catch (_: Throwable) {}

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "onTrimMemory recebido com nível $level")
        // Libera caches de bitmap quando o sistema sinaliza pressão de memória
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        ) {
            AppIconCache.clear()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "onLowMemory recebido! Esvaziando todos os caches.")
        AppIconCache.clear()
    }
}
