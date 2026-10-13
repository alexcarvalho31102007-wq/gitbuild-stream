package com.example.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.InstalledSnapshotEntity
import com.example.data.UninstalledAppEntity
import com.example.manager.ShizukuManager
import com.example.util.getApplicationInfoCompat
import com.example.util.getInstalledApplicationsCompat
import com.example.util.getInstalledPackagesCompat
import com.example.util.getPackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String = "",
    val versionCode: Long = 0L,
    val isSystemApp: Boolean = false,
    val isEnabled: Boolean = true,
    val isInstalled: Boolean = true,
    val originLabel: String = "Desconhecido",
    val installerPackageName: String? = null,
    val isCritical: Boolean = false,
    val uninstalledTimestamp: Long? = null,
    val uninstalledReason: String? = null,
    val isRestorableViaShizuku: Boolean = false
) {
    val versionDisplay: String
        get() = when {
            versionName.isNotBlank() && versionCode > 0 -> "v$versionName ($versionCode)"
            versionName.isNotBlank() -> "v$versionName"
            versionCode > 0 -> "v$versionCode"
            else -> "v1.0"
        }

    val statusLabel: String
        get() = when {
            !isInstalled -> "Desinstalado"
            !isEnabled -> "Desativado"
            isCritical -> "Crítico"
            isSystemApp -> "Sistema"
            else -> "Ativo"
        }
}

class AppRepository(
    private val context: Context,
    private val shizukuManager: ShizukuManager? = null
) {

    private val packageManager: PackageManager = context.packageManager
    private val appDatabase = AppDatabase.getInstance(context)
    private val launcherPackage: String? by lazy { getDefaultLauncherInternal() }
    private val syncMutex = Mutex()

    /**
     * Carrega a lista completa de aplicativos instalados e desinstalados
     * utilizando todas as APIs oficiais do Android (PackageManager, ApplicationInfo,
     * status de ativação do componente) e fontes privilegiadas (Shizuku/Shell).
     */
    suspend fun getAllApps(): Pair<List<AppInfo>, List<AppInfo>> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val currentLauncher = launcherPackage
            val dao = appDatabase.appDao()

        // 1. FLAGS ABRANGENTES DE BUSCA NO PACKAGEMANAGER
        val flags = PackageManager.GET_META_DATA or
                PackageManager.MATCH_UNINSTALLED_PACKAGES or
                PackageManager.MATCH_DISABLED_COMPONENTS or
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS

        // Coleta primária via getInstalledPackages
        val packagesMap = LinkedHashMap<String, PackageInfo>()
        try {
            val primaryPackages = packageManager.getInstalledPackagesCompat(flags)
            for (pkg in primaryPackages) {
                packagesMap[pkg.packageName] = pkg
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "Erro em getInstalledPackagesCompat", e)
        }

        // Coleta secundária via getInstalledApplications (garante capturar serviços ou pacotes ocultos)
        try {
            val rawApplications = packageManager.getInstalledApplicationsCompat(flags)
            for (appInfo in rawApplications) {
                if (!packagesMap.containsKey(appInfo.packageName)) {
                    try {
                        val pkg = packageManager.getPackageInfoCompat(appInfo.packageName, flags)
                        packagesMap[pkg.packageName] = pkg
                    } catch (e: Exception) {
                        // Criação de PackageInfo de contingência
                        val fallbackPkg = PackageInfo().apply {
                            packageName = appInfo.packageName
                            applicationInfo = appInfo
                        }
                        packagesMap[appInfo.packageName] = fallbackPkg
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "Erro em getInstalledApplicationsCompat", e)
        }

        // Coleta terciária via Shizuku Shell (quando disponível e autorizado)
        val shellDisabledPackages = HashSet<String>()
        val shellUninstalledPackages = HashSet<String>()
        if (shizukuManager != null && shizukuManager.isRunning() && shizukuManager.hasPermission()) {
            try {
                shellDisabledPackages.addAll(shizukuManager.queryDisabledPackagesFromShell())
                val uninstalledFromShell = shizukuManager.queryUninstalledPackagesFromShell()
                for (pkgName in uninstalledFromShell) {
                    if (!packagesMap.containsKey(pkgName)) {
                        shellUninstalledPackages.add(pkgName)
                        try {
                            val pkg = packageManager.getPackageInfoCompat(pkgName, flags)
                            packagesMap[pkgName] = pkg
                        } catch (e: Exception) {
                            // Ignora pacotes inacessíveis via API padrão
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AppRepository", "Erro ao consultar pacotes via Shizuku Shell", e)
            }
        }

        val installedList = ArrayList<AppInfo>(packagesMap.size)
        val uninstalledFromSystem = ArrayList<AppInfo>()

        for ((packageName, pkg) in packagesMap) {
            val appInfo = pkg.applicationInfo ?: continue

            // Verificação precisa de instalação:
            // FLAG_INSTALLED == 0 significa que o app foi removido para este usuário específico (ex: app de sistema)
            val isInstalled = (appInfo.flags and ApplicationInfo.FLAG_INSTALLED) != 0 &&
                    !shellUninstalledPackages.contains(packageName)

            // Verificação precisa de status de desativação (API oficial getApplicationEnabledSetting)
            val isEnabled = isPackageActuallyEnabled(packageName, appInfo, shellDisabledPackages)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val appName = try {
                appInfo.loadLabel(packageManager).toString()
            } catch (e: Exception) {
                packageName
            }

            val versionName = pkg.versionName ?: ""
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            }

            val (originLabel, installerPkg) = resolveOrigin(packageName, isSystem)
            val isCritical = isCriticalApp(packageName, currentLauncher)

            val info = AppInfo(
                packageName = packageName,
                appName = appName,
                versionName = versionName,
                versionCode = versionCode,
                isSystemApp = isSystem,
                isEnabled = isEnabled,
                isInstalled = isInstalled,
                originLabel = originLabel,
                installerPackageName = installerPkg,
                isCritical = isCritical,
                isRestorableViaShizuku = isSystem && !isInstalled
            )

            if (isInstalled) {
                // Apps instalados (incluindo desativados) permanecem na lista principal
                installedList.add(info)
            } else {
                // Apps com FLAG_INSTALLED == 0 pertencem à Lixeira
                uninstalledFromSystem.add(
                    info.copy(
                        uninstalledTimestamp = System.currentTimeMillis(),
                        uninstalledReason = "Aplicativo de sistema desinstalado (Detectado nativamente)",
                        isRestorableViaShizuku = true
                    )
                )
            }
        }

        // 2. SINCRONIZAÇÃO E DETECÇÃO DE DELTA (Desinstalações feitas por outras ferramentas / Google Play / ADB)
        val currentInstalledPackages = installedList.associateBy { it.packageName }
        val previousSnapshots = dao.getAllSnapshots()
        val detectedExternalUninstalls = ArrayList<UninstalledAppEntity>()

        for (snapshot in previousSnapshots) {
            if (!currentInstalledPackages.containsKey(snapshot.packageName)) {
                val entity = UninstalledAppEntity(
                    packageName = snapshot.packageName,
                    appName = snapshot.appName,
                    versionName = snapshot.versionName,
                    versionCode = snapshot.versionCode,
                    isSystemApp = snapshot.isSystemApp,
                    originLabel = snapshot.originLabel,
                    installerPackageName = snapshot.installerPackageName,
                    uninstalledTimestamp = System.currentTimeMillis(),
                    uninstalledReason = "Desinstalado externamente (detectado por sincronização)",
                    isRestorableViaShizuku = snapshot.isSystemApp
                )
                detectedExternalUninstalls.add(entity)
            }
        }

            val reinstalledPackages = installedList.map { it.packageName }

            // Atualizar snapshots no banco de dados local em transação atômica única
            val newSnapshots = installedList.map { app ->
                InstalledSnapshotEntity(
                    packageName = app.packageName,
                    appName = app.appName,
                    versionName = app.versionName,
                    versionCode = app.versionCode,
                    isSystemApp = app.isSystemApp,
                    isEnabled = app.isEnabled,
                    originLabel = app.originLabel,
                    installerPackageName = app.installerPackageName,
                    lastSeenTimestamp = System.currentTimeMillis()
                )
            }

            try {
                dao.syncSnapshotsAndUninstalls(newSnapshots, detectedExternalUninstalls, reinstalledPackages)
            } catch (e: Throwable) {
                Log.e("AppRepository", "Erro na sincronização de snapshots e lixeira", e)
            }

            // 3. Montar a lista completa da LIXEIRA
            val dbUninstalledEntities = try { dao.getAllUninstalledApps() } catch (_: Throwable) { emptyList() }
            val combinedUninstalled = LinkedHashMap<String, AppInfo>()

            for (sysApp in uninstalledFromSystem) {
                combinedUninstalled[sysApp.packageName] = sysApp
            }

            for (entity in dbUninstalledEntities) {
                if (!currentInstalledPackages.containsKey(entity.packageName)) {
                    combinedUninstalled[entity.packageName] = AppInfo(
                        packageName = entity.packageName,
                        appName = entity.appName,
                        versionName = entity.versionName,
                        versionCode = entity.versionCode,
                        isSystemApp = entity.isSystemApp,
                        isEnabled = false,
                        isInstalled = false,
                        originLabel = entity.originLabel,
                        installerPackageName = entity.installerPackageName,
                        isCritical = false,
                        uninstalledTimestamp = entity.uninstalledTimestamp,
                        uninstalledReason = entity.uninstalledReason,
                        isRestorableViaShizuku = entity.isRestorableViaShizuku
                    )
                }
            }

            installedList.sortBy { it.appName.lowercase() }
            val uninstalledList = combinedUninstalled.values.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName.ifBlank { it.packageName } }
            )

            Pair(installedList, uninstalledList)
        }
    }

    /**
     * Determina se o pacote está de fato ativado usando a API oficial getApplicationEnabledSetting
     * combinada com o estado do shell e flags do sistema.
     */
    private fun isPackageActuallyEnabled(
        packageName: String,
        appInfo: ApplicationInfo,
        shellDisabledPackages: Set<String>
    ): Boolean {
        if (shellDisabledPackages.contains(packageName)) return false

        return try {
            val setting = packageManager.getApplicationEnabledSetting(packageName)
            when (setting) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                else -> appInfo.enabled
            }
        } catch (e: Exception) {
            appInfo.enabled
        }
    }

    suspend fun removeAppFromTrash(packageName: String) = withContext(Dispatchers.IO) {
        appDatabase.appDao().deleteUninstalledApp(packageName)
    }

    suspend fun clearAllTrash() = withContext(Dispatchers.IO) {
        appDatabase.appDao().clearUninstalledApps()
    }

    suspend fun recordManualUninstall(app: AppInfo, reason: String = "Desinstalado pelo UninstallX") = withContext(Dispatchers.IO) {
        val entity = UninstalledAppEntity(
            packageName = app.packageName,
            appName = app.appName,
            versionName = app.versionName,
            versionCode = app.versionCode,
            isSystemApp = app.isSystemApp,
            originLabel = app.originLabel,
            installerPackageName = app.installerPackageName,
            uninstalledTimestamp = System.currentTimeMillis(),
            uninstalledReason = reason,
            isRestorableViaShizuku = app.isSystemApp
        )
        appDatabase.appDao().insertUninstalledApp(entity)
        appDatabase.appDao().deleteSnapshot(app.packageName)
    }

    private fun resolveOrigin(packageName: String, isSystemApp: Boolean): Pair<String, String?> {
        if (isSystemApp) {
            return Pair("Sistema (Pré-instalado)", null)
        }

        val installerPkg: String? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sourceInfo: InstallSourceInfo? = packageManager.getInstallSourceInfo(packageName)
                sourceInfo?.installingPackageName ?: sourceInfo?.initiatingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
        } catch (e: Exception) {
            null
        }

        val originLabel = when (installerPkg) {
            "com.android.vending" -> "Google Play Store"
            "org.fdroid.fdroid", "org.fdroid.fdroid.privileged" -> "F-Droid"
            "com.aurora.store" -> "Aurora Store"
            "com.amazon.venezia" -> "Amazon Appstore"
            "com.sec.android.app.samsungapps" -> "Galaxy Store"
            "com.xiaomi.mipicks" -> "Xiaomi GetApps"
            "com.huawei.appmarket" -> "Huawei AppGallery"
            "moe.shizuku.privileged.api", "dev.rikka.shizuku" -> "Shizuku"
            "com.google.android.packageinstaller", "com.android.packageinstaller" -> "Instalador de Pacotes"
            "com.android.shell" -> "ADB / Linha de comando"
            null, "" -> "APK Manual / Externo"
            else -> {
                try {
                    val label = packageManager.getApplicationInfoCompat(installerPkg, 0)
                        .loadLabel(packageManager).toString()
                    if (label.isNotBlank()) label else installerPkg
                } catch (e: Exception) {
                    installerPkg
                }
            }
        }

        return Pair(originLabel, installerPkg)
    }

    private fun getDefaultLauncherInternal(): String? {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }

    private fun isCriticalApp(packageName: String, defaultLauncher: String?): Boolean {
        return packageName == "android" ||
                packageName == "com.android.systemui" ||
                packageName == "com.android.settings" ||
                packageName == "com.google.android.gms" ||
                packageName == "moe.shizuku.privileged.api" ||
                packageName == "dev.rikka.shizuku" ||
                packageName == defaultLauncher
    }
}

