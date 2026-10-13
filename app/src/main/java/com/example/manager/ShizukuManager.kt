package com.example.manager

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.example.util.getApplicationInfoCompat
import com.example.util.getPackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

data class CommandResult(
    val exitCode: Int,
    val output: String,
    val error: String,
    val isSuccess: Boolean
)

class ShizukuManager(private val context: Context) {

    companion object {
        const val TAG = "ShizukuManager"
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val PERMISSION_REQUEST_CODE = 1412
    }

    fun isInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfoCompat(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun hasPermission(): Boolean {
        if (!isRunning()) return false
        return try {
            if (Shizuku.isPreV11()) true else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    fun requestPermission() {
        if (!isRunning() || Shizuku.isPreV11()) return
        try {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (e: Throwable) {
            Log.e(TAG, "Falha ao solicitar permissão do Shizuku", e)
        }
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (e: Throwable) {
            Log.e(TAG, "Falha ao adicionar listener de permissão", e)
        }
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.removeRequestPermissionResultListener(listener)
        } catch (e: Throwable) {
            Log.e(TAG, "Falha ao remover listener de permissão", e)
        }
    }

    private val newProcessMethod by lazy {
        try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        } catch (e: Throwable) {
            Log.e(TAG, "newProcess method via reflection não encontrado", e)
            null
        }
    }

    private fun newShizukuProcess(command: Array<String>): Process? {
        return try {
            newProcessMethod?.invoke(null, command, null, null) as? Process
        } catch (e: Throwable) {
            Log.e(TAG, "Erro ao invocar newProcess no Shizuku", e)
            null
        }
    }

    /**
     * Executa um comando privilegiado no shell do Shizuku com captura segura de stdout e stderr,
     * drenagem concorrente de streams para prevenir deadlocks de pipe do SO e timeout configurável.
     */
    fun executeCommand(command: Array<String>, timeoutMs: Long = 12000L): CommandResult {
        if (!isRunning() || !hasPermission()) {
            return CommandResult(-1, "", "Shizuku não disponível ou sem permissão", false)
        }

        return try {
            val process = newShizukuProcess(command)
                ?: return CommandResult(-1, "", "Não foi possível iniciar o processo Shizuku", false)

            val stdOutBuilder = StringBuilder()
            val stdErrBuilder = StringBuilder()

            val outThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stdOutBuilder.appendLine(line)
                        }
                    }
                } catch (_: Throwable) {}
            }.apply { isDaemon = true; start() }

            val errThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stdErrBuilder.appendLine(line)
                        }
                    }
                } catch (_: Throwable) {}
            }.apply { isDaemon = true; start() }

            val startTime = System.currentTimeMillis()
            var finished = false
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    process.exitValue()
                    finished = true
                    break
                } catch (_: IllegalThreadStateException) {
                    try {
                        Thread.sleep(80)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }

            if (!finished) {
                try { process.destroy() } catch (_: Throwable) {}
                return CommandResult(-1, "", "Tempo limite esgotado para o comando (${timeoutMs}ms)", false)
            }

            try { outThread.join(800) } catch (_: Throwable) {}
            try { errThread.join(800) } catch (_: Throwable) {}

            val stdOut = stdOutBuilder.toString().trim()
            val stdErr = stdErrBuilder.toString().trim()
            val exitCode = try { process.exitValue() } catch (_: Throwable) { -1 }

            val hasFailureWord = stdOut.contains("Failure", ignoreCase = true) ||
                    stdErr.contains("Failure", ignoreCase = true) ||
                    stdOut.contains("Error", ignoreCase = true) ||
                    stdErr.contains("Error", ignoreCase = true) ||
                    stdOut.contains("Exception", ignoreCase = true)

            val isSuccess = exitCode == 0 && !hasFailureWord

            CommandResult(exitCode, stdOut, stdErr, isSuccess)
        } catch (e: Throwable) {
            Log.e(TAG, "Exceção ao executar comando: ${command.joinToString(" ")}", e)
            CommandResult(-1, "", e.localizedMessage ?: "Exceção desconhecida", false)
        }
    }

    /**
     * Verifica se o pacote está instalado para o usuário atual via PackageManager.
     */
    fun isAppInstalledLocal(packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfoCompat(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_INSTALLED) != 0
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Verifica se o pacote está ativado para o usuário atual via PackageManager.
     */
    fun isAppEnabledLocal(packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfoCompat(packageName, 0)
            val setting = context.packageManager.getApplicationEnabledSetting(packageName)
            when (setting) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                else -> appInfo.enabled
            }
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Desinstalação avançada com múltiplos níveis de fallback interno no Shizuku:
     * Nível 1: pm uninstall --user 0 <pkg>
     * Nível 2: cmd package uninstall --user 0 <pkg>
     * Nível 3: pm uninstall <pkg> (tentativa de desinstalação completa para usuário comum)
     * Nível 4: pm disable-user --user 0 <pkg> (caso o sistema proteja contra desinstalação)
     * Nível 5: pm hide --user 0 <pkg>
     * Nível 6: pm suspend <pkg>
     */
    suspend fun uninstallApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isRunning() || !hasPermission()) return@withContext false

        // Nível 1: Desinstalação padrão para o usuário atual
        val res1 = executeCommand(arrayOf("pm", "uninstall", "--user", "0", packageName))
        if (res1.isSuccess || !isAppInstalledLocal(packageName)) {
            Log.d(TAG, "Pacote $packageName desinstalado via pm uninstall --user 0")
            return@withContext true
        }

        // Nível 2: Desinstalação via serviço 'cmd package'
        val res2 = executeCommand(arrayOf("cmd", "package", "uninstall", "--user", "0", packageName))
        if (res2.isSuccess || !isAppInstalledLocal(packageName)) {
            Log.d(TAG, "Pacote $packageName desinstalado via cmd package uninstall --user 0")
            return@withContext true
        }

        // Nível 3: Desinstalação global sem flag de usuário
        val res3 = executeCommand(arrayOf("pm", "uninstall", packageName))
        if (res3.isSuccess || !isAppInstalledLocal(packageName)) {
            Log.d(TAG, "Pacote $packageName desinstalado via pm uninstall global")
            return@withContext true
        }

        // Nível 4: Fallback inteligente - desativar pacote para usuário 0 caso o pacote de sistema seja protegido
        val res4 = executeCommand(arrayOf("pm", "disable-user", "--user", "0", packageName))
        if (res4.isSuccess || !isAppEnabledLocal(packageName)) {
            Log.d(TAG, "Pacote $packageName desativado como fallback via pm disable-user")
            return@withContext true
        }

        // Nível 5: Ocultar pacote para o usuário
        val res5 = executeCommand(arrayOf("pm", "hide", "--user", "0", packageName))
        if (res5.isSuccess) {
            Log.d(TAG, "Pacote $packageName ocultado como fallback via pm hide")
            return@withContext true
        }

        // Nível 6: Suspender pacote
        val res6 = executeCommand(arrayOf("pm", "suspend", "--user", "0", packageName))
        if (res6.isSuccess) {
            Log.d(TAG, "Pacote $packageName suspenso como fallback via pm suspend")
            return@withContext true
        }

        false
    }

    /**
     * Restauração avançada com múltiplos níveis de fallback interno no Shizuku:
     * Nível 1: cmd package install-existing --user 0 <pkg>
     * Nível 2: pm install-existing --user 0 <pkg>
     * Nível 3: pm enable --user 0 <pkg>
     * Nível 4: pm unhide --user 0 <pkg>
     * Nível 5: pm unsuspend --user 0 <pkg>
     */
    suspend fun restoreApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isRunning() || !hasPermission()) return@withContext false

        // Nível 1: Instalação de pacote existente no sistema via 'cmd package'
        val res1 = executeCommand(arrayOf("cmd", "package", "install-existing", "--user", "0", packageName))
        if (res1.isSuccess || isAppInstalledLocal(packageName)) {
            Log.d(TAG, "Pacote $packageName restaurado via cmd package install-existing")
            return@withContext true
        }

        // Nível 2: Instalação de pacote existente via 'pm install-existing'
        val res2 = executeCommand(arrayOf("pm", "install-existing", "--user", "0", packageName))
        if (res2.isSuccess || isAppInstalledLocal(packageName)) {
            Log.d(TAG, "Pacote $packageName restaurado via pm install-existing")
            return@withContext true
        }

        // Nível 3: Reativação se estava apenas desativado
        val res3 = executeCommand(arrayOf("pm", "enable", "--user", "0", packageName))
        if (res3.isSuccess || isAppEnabledLocal(packageName)) {
            Log.d(TAG, "Pacote $packageName reativado via pm enable")
            return@withContext true
        }

        // Nível 4: Desocultar se estava oculto
        val res4 = executeCommand(arrayOf("pm", "unhide", "--user", "0", packageName))
        if (res4.isSuccess) {
            Log.d(TAG, "Pacote $packageName desocultado via pm unhide")
            return@withContext true
        }

        // Nível 5: Dessuspender se estava suspenso
        val res5 = executeCommand(arrayOf("pm", "unsuspend", "--user", "0", packageName))
        if (res5.isSuccess) {
            Log.d(TAG, "Pacote $packageName dessuspenso via pm unsuspend")
            return@withContext true
        }

        false
    }

    /**
     * Consulta lista de pacotes desinstalados para o usuário 0 através do shell do sistema.
     */
    fun queryUninstalledPackagesFromShell(): List<String> {
        if (!isRunning() || !hasPermission()) return emptyList()
        val result = executeCommand(arrayOf("pm", "list", "packages", "-u"))
        if (!result.isSuccess && result.output.isBlank()) return emptyList()
        return parsePackagesList(result.output)
    }

    /**
     * Consulta lista de pacotes desativados através do shell do sistema.
     */
    fun queryDisabledPackagesFromShell(): List<String> {
        if (!isRunning() || !hasPermission()) return emptyList()
        val result = executeCommand(arrayOf("pm", "list", "packages", "-d"))
        if (!result.isSuccess && result.output.isBlank()) return emptyList()
        return parsePackagesList(result.output)
    }

    private fun parsePackagesList(output: String): List<String> {
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotBlank() }
            .toList()
    }
}
