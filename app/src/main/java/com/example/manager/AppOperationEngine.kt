package com.example.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.repository.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceCapabilities(
    val hasShizuku: Boolean,
    val hasShizukuPermission: Boolean,
    val hasPackageInstaller: Boolean,
    val hasDeletePackagesPermission: Boolean
) {
    val isPrivilegedAvailable: Boolean get() = hasShizuku && hasShizukuPermission
}

data class EngineResult(
    val isSuccess: Boolean,
    val methodUsed: String,
    val message: String? = null
)

class AppOperationEngine(
    private val context: Context,
    private val shizukuManager: ShizukuManager
) {

    companion object {
        const val TAG = "AppOperationEngine"
    }

    private val packageManager: PackageManager = context.packageManager

    /**
     * Detecta dinamicamente todos os métodos, APIs e permissões disponíveis no dispositivo.
     */
    fun detectCapabilities(): DeviceCapabilities {
        val hasShizukuRunning = shizukuManager.isRunning()
        val hasShizukuPerm = if (hasShizukuRunning) shizukuManager.hasPermission() else false

        val hasPackageInstaller = try {
            packageManager.packageInstaller != null
        } catch (e: Throwable) {
            false
        }

        val hasDeletePackagesPerm = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.checkSelfPermission(android.Manifest.permission.REQUEST_DELETE_PACKAGES) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        } catch (e: Throwable) {
            false
        }

        return DeviceCapabilities(
            hasShizuku = hasShizukuRunning,
            hasShizukuPermission = hasShizukuPerm,
            hasPackageInstaller = hasPackageInstaller,
            hasDeletePackagesPermission = hasDeletePackagesPerm
        )
    }

    /**
     * Executa a desinstalação de um aplicativo utilizando a melhor API/método disponível,
     * aplicando fallback inteligente em cascata:
     * 1. Shizuku / Privilegiado (quando disponível e com permissão)
     * 2. PackageInstaller nativo do Android
     * 3. Intent do sistema (ACTION_DELETE / ACTION_UNINSTALL_PACKAGE)
     * 4. Para apps de sistema sem Shizuku: Redirecionamento para desativação nas configurações
     */
    suspend fun uninstallApp(app: AppInfo): EngineResult = withContext(Dispatchers.IO) {
        val caps = detectCapabilities()

        // 1. Tentar Shizuku / ADB Privilegiado em primeiro lugar
        if (caps.isPrivilegedAvailable) {
            Log.d(TAG, "Tentando desinstalação via Shizuku para ${app.packageName}")
            val shizukuSuccess = shizukuManager.uninstallApp(app.packageName)
            if (shizukuSuccess) {
                return@withContext EngineResult(
                    isSuccess = true,
                    methodUsed = "Shizuku Privilegiado",
                    message = null
                )
            }
            Log.w(TAG, "Shizuku falhou para ${app.packageName}. Tentando fallback nativo...")
        }

        // Se o aplicativo for de usuário (não de sistema), aplicar fallback nativo do Android
        if (!app.isSystemApp) {
            // 2. Tentar API nativa do PackageInstaller
            if (caps.hasPackageInstaller) {
                val packageInstallerSuccess = tryUninstallViaPackageInstaller(app.packageName)
                if (packageInstallerSuccess) {
                    return@withContext EngineResult(
                        isSuccess = true,
                        methodUsed = "PackageInstaller Nativo",
                        message = "Confirmação do sistema solicitada"
                    )
                }
            }

            // 3. Tentar Intent do Sistema (ACTION_DELETE)
            val intentSuccess = launchNativeUninstallIntent(app.packageName)
            if (intentSuccess) {
                return@withContext EngineResult(
                    isSuccess = true,
                    methodUsed = "Intent do Sistema (ACTION_DELETE)",
                    message = "Confirmação do sistema aberta"
                )
            }
        } else {
            // Aplicativo de sistema sem Shizuku ou cujo Shizuku falhou
            if (!caps.isPrivilegedAvailable) {
                // Oferecer abertura das configurações do app para que o usuário possa desativá-lo
                val openedSettings = launchAppSettings(app.packageName)
                return@withContext EngineResult(
                    isSuccess = openedSettings,
                    methodUsed = "Configurações do Android",
                    message = if (openedSettings) "Abra para desativar nas configurações" else "Requer Shizuku ativo para apps do sistema"
                )
            }
        }

        EngineResult(
            isSuccess = false,
            methodUsed = "Nenhum método compatível",
            message = "Falha ao remover pacote com os métodos disponíveis"
        )
    }

    /**
     * Executa a restauração de um aplicativo utilizando a melhor API/método disponível:
     * 1. Shizuku / ADB Privilegiado (cmd package install-existing / pm enable / unhide)
     * 2. Loja de aplicativos / Google Play Store Intent para reinstalação de apps de terceiros
     */
    suspend fun restoreApp(app: AppInfo): EngineResult = withContext(Dispatchers.IO) {
        val caps = detectCapabilities()

        // 1. Shizuku Privilegiado
        if (caps.isPrivilegedAvailable) {
            val restored = shizukuManager.restoreApp(app.packageName)
            if (restored) {
                return@withContext EngineResult(
                    isSuccess = true,
                    methodUsed = "Shizuku Privilegiado",
                    message = null
                )
            }
        }

        // 2. Se for app de terceiros completamente desinstalado do armazenamento:
        // Redirecionar para a loja / Google Play Store para download e restauração
        val openedStore = launchStoreIntent(app.packageName)
        if (openedStore) {
            return@withContext EngineResult(
                isSuccess = true,
                methodUsed = "Google Play / Loja",
                message = "Redirecionado para reinstalar na loja"
            )
        }

        EngineResult(
            isSuccess = false,
            methodUsed = "Falha de Restauração",
            message = "Não foi possível restaurar sem Shizuku ou loja de apps"
        )
    }

    /**
     * Tenta desinstalação através da API nativa PackageInstaller do Android.
     */
     private fun tryUninstallViaPackageInstaller(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.parse("package:$packageName")
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return safeStartActivity(intent, "PackageInstaller para $packageName")
    }

    /**
     * Dispara o Intent de desinstalação nativo padrão do Android (ACTION_DELETE).
     */
    private fun launchNativeUninstallIntent(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return safeStartActivity(intent, "ACTION_DELETE para $packageName")
    }

    /**
     * Abre a tela de informações do aplicativo nas configurações do sistema Android.
     */
    private fun launchAppSettings(packageName: String): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return safeStartActivity(intent, "configurações para $packageName")
    }

    /**
     * Abre o aplicativo na Google Play Store ou gerenciador de pacotes para reinstalação.
     */
    private fun launchStoreIntent(packageName: String): Boolean {
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (safeStartActivity(marketIntent, "loja market:// para $packageName")) {
            return true
        }
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return safeStartActivity(webIntent, "loja web para $packageName")
    }

    private fun safeStartActivity(intent: Intent, actionDescription: String): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Falha ao iniciar atividade ($actionDescription)", e)
            false
        }
    }
}
