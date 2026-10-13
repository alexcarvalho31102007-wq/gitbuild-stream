package com.example.util

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Funções utilitárias e extensões de compatibilidade para o [PackageManager].
 * Centraliza o tratamento de versões do Android (API 33+ PackageInfoFlags vs legado)
 * e fornece proteção contra IPC Overflow (TransactionTooLargeException / DeadSystemException).
 */
object PackageManagerCompat {
    private const val TAG = "PackageManagerCompat"

    fun getInstalledPackagesCompat(pm: PackageManager, flags: Int): List<PackageInfo> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(flags)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Falha ao consultar pacotes com flags completas, tentando fallback leve", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledPackages(0)
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    fun getInstalledApplicationsCompat(pm: PackageManager, flags: Int): List<ApplicationInfo> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(flags)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Falha ao consultar applications com flags completas, tentando fallback leve", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledApplications(0)
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    fun getPackageInfoCompat(pm: PackageManager, packageName: String, flags: Int): PackageInfo {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }
        } catch (e: Throwable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
        }
    }

    fun getApplicationInfoCompat(pm: PackageManager, packageName: String, flags: Int): ApplicationInfo {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, flags)
            }
        } catch (e: Throwable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
        }
    }
}

// Extensões de conveniência para chamar diretamente no PackageManager
fun PackageManager.getInstalledPackagesCompat(flags: Int): List<PackageInfo> =
    PackageManagerCompat.getInstalledPackagesCompat(this, flags)

fun PackageManager.getInstalledApplicationsCompat(flags: Int): List<ApplicationInfo> =
    PackageManagerCompat.getInstalledApplicationsCompat(this, flags)

fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int): PackageInfo =
    PackageManagerCompat.getPackageInfoCompat(this, packageName, flags)

fun PackageManager.getApplicationInfoCompat(packageName: String, flags: Int): ApplicationInfo =
    PackageManagerCompat.getApplicationInfoCompat(this, packageName, flags)
