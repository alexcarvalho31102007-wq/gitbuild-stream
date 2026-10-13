package com.example.compiler.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import com.example.compiler.data.BuildRecordEntity
import com.example.compiler.data.BuildStatus
import com.example.compiler.data.CompilerPreferences
import com.example.compiler.github.GitHubApiService
import com.example.compiler.github.TreeItem
import com.example.compiler.notification.BuildNotificationManager
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class BuildTrackingService : Service {

    constructor() : super()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: BuildNotificationManager
    private lateinit var gitHubService: GitHubApiService
    private lateinit var database: AppDatabase
    private lateinit var preferences: CompilerPreferences

    override fun onCreate() {
        super.onCreate()
        notificationManager = BuildNotificationManager(this)
        gitHubService = GitHubApiService()
        database = AppDatabase.getInstance(this)
        preferences = CompilerPreferences(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val buildId = intent?.getStringExtra(EXTRA_BUILD_ID)

        if (action == ACTION_CANCEL_BUILD && buildId != null) {
            cancelActiveBuild(buildId)
            return START_NOT_STICKY
        }

        if (buildId != null) {
            startBuildProcess(buildId)
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startBuildProcess(buildId: String) {
        serviceScope.launch {
            val buildDao = database.buildDao()
            val initialRecord = buildDao.getBuildById(buildId)
            if (initialRecord == null) {
                stopSelf()
                return@launch
            }

            val foregroundNotification = notificationManager.buildForegroundNotification(
                buildId = buildId,
                projectName = initialRecord.projectName,
                statusDetail = "Iniciando compilação do projeto..."
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    foregroundNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, foregroundNotification)
            }

            try {
                notificationManager.notifyStarted(buildId, initialRecord.projectName)
                executeBuildPipeline(buildId)
            } catch (e: Exception) {
                Log.e("BuildTrackingService", "Falha crítica na compilação $buildId", e)
                val failedRecord = buildDao.getBuildById(buildId) ?: initialRecord
                buildDao.updateBuild(
                    failedRecord.copy(
                        status = BuildStatus.FAILED,
                        statusDetail = "Falha no processo: ${e.message}",
                        errorMessage = e.message ?: "Erro desconhecido",
                        finishedAtTimestamp = System.currentTimeMillis()
                    )
                )
                notificationManager.notifyFailure(buildId, initialRecord.projectName, e.message)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun executeBuildPipeline(buildId: String) {
        val buildDao = database.buildDao()
        var record = buildDao.getBuildById(buildId) ?: return
        val token = preferences.githubToken
        val owner = record.repoOwner
        val repo = record.repoName
        val branchName = record.branchName

        // 1. Enviar arquivos do projeto para o GitHub
        updateStatus(buildId, BuildStatus.UPLOADING, "Garantindo workflow oficial de compilação Release...")
        val ensureWorkflowRes = gitHubService.ensureWorkflowFile(token, owner, repo)
        if (ensureWorkflowRes.isFailure) {
            val error = ensureWorkflowRes.exceptionOrNull()?.message ?: "Falha ao configurar workflow"
            failBuild(buildId, "Erro na configuração do GitHub: $error")
            return
        }

        updateStatus(buildId, BuildStatus.UPLOADING, "Carregando arquivos do projeto para envio...")
        val stagingDir = File(cacheDir, "staging_$buildId")
        if (!stagingDir.exists() || stagingDir.listFiles().isNullOrEmpty()) {
            failBuild(buildId, "Arquivos do projeto não encontrados na área de preparação local.")
            return
        }

        val projectFiles = listFilesRecursively(stagingDir)
        val treeItems = mutableListOf<TreeItem>()

        for ((index, file) in projectFiles.withIndex()) {
            val relativePath = file.relativeTo(stagingDir).path.replace('\\', '/')
            val progressPercent = ((index + 1) * 100) / projectFiles.size
            updateStatus(
                buildId,
                BuildStatus.UPLOADING,
                "Enviando arquivos para o GitHub ($progressPercent%): $relativePath"
            )

            val bytes = file.readBytes()
            val blobRes = gitHubService.createBlob(token, owner, repo, bytes)
            if (blobRes.isFailure) {
                failBuild(buildId, "Falha ao enviar arquivo $relativePath: ${blobRes.exceptionOrNull()?.message}")
                return
            }

            val blobSha = blobRes.getOrThrow()
            val isExecutable = relativePath == "gradlew" || relativePath.endsWith(".sh")
            treeItems.add(
                TreeItem(
                    path = relativePath,
                    mode = if (isExecutable) "100755" else "100644",
                    type = "blob",
                    sha = blobSha
                )
            )
        }

        // Adiciona também o workflow explicitamente na árvore da branch
        val workflowYamlBytes = gitHubService.getReleaseWorkflowYaml().toByteArray(Charsets.UTF_8)
        val workflowBlob = gitHubService.createBlob(token, owner, repo, workflowYamlBytes).getOrNull()
        if (workflowBlob != null) {
            treeItems.add(
                TreeItem(
                    path = ".github/workflows/android-release.yml",
                    mode = "100644",
                    type = "blob",
                    sha = workflowBlob
                )
            )
        }

        updateStatus(buildId, BuildStatus.UPLOADING, "Criando árvore Git e commit atômico...")
        val treeRes = gitHubService.createTree(token, owner, repo, treeItems)
        if (treeRes.isFailure) {
            failBuild(buildId, "Erro ao criar árvore Git: ${treeRes.exceptionOrNull()?.message}")
            return
        }
        val treeSha = treeRes.getOrThrow()

        val commitRes = gitHubService.createCommit(
            token = token,
            owner = owner,
            repo = repo,
            message = "Compilação Release #$buildId - ${record.projectName}",
            treeSha = treeSha,
            parentSha = null
        )
        if (commitRes.isFailure) {
            failBuild(buildId, "Erro ao criar commit: ${commitRes.exceptionOrNull()?.message}")
            return
        }
        val commitSha = commitRes.getOrThrow()

        val branchRes = gitHubService.createOrUpdateBranch(token, owner, repo, branchName, commitSha)
        if (branchRes.isFailure) {
            failBuild(buildId, "Erro ao atualizar branch no GitHub: ${branchRes.exceptionOrNull()?.message}")
            return
        }

        record = buildDao.getBuildById(buildId) ?: return
        buildDao.updateBuild(record.copy(commitSha = commitSha))

        // Limpa diretório de preparação para liberar espaço
        stagingDir.deleteRecursively()

        // 2. Acionar o Workflow (Exatamente 1 execução real, sem duplicidades)
        updateStatus(buildId, BuildStatus.DISPATCHED, "Iniciando compilação no GitHub Actions...")
        val dispatchRes = gitHubService.dispatchWorkflow(
            token = token,
            owner = owner,
            repo = repo,
            workflowFileName = "android-release.yml",
            ref = branchName,
            inputs = mapOf(
                "build_id" to buildId,
                "project_name" to record.projectName
            )
        )
        if (dispatchRes.isFailure) {
            failBuild(buildId, "Erro ao disparar workflow: ${dispatchRes.exceptionOrNull()?.message}")
            return
        }

        // Aguarda alguns segundos para o GitHub registrar a execução
        delay(4000)

        // 3. Localizar e Monitorar o Workflow Run
        updateStatus(buildId, BuildStatus.QUEUED, "Aguardando runner do GitHub Actions...")
        var workflowRunId: Long? = null
        var runAttempts = 0

        while (workflowRunId == null && runAttempts < 12) {
            runAttempts++
            val runsRes = gitHubService.getWorkflowRuns(token, owner, repo, branch = branchName)
            if (runsRes.isSuccess) {
                val runs = runsRes.getOrThrow()
                val targetRun = runs.firstOrNull()
                if (targetRun != null) {
                    workflowRunId = targetRun.id
                    record = buildDao.getBuildById(buildId) ?: return
                    buildDao.updateBuild(
                        record.copy(
                            workflowRunId = targetRun.id,
                            runNumber = targetRun.runNumber,
                            htmlUrl = targetRun.htmlUrl
                        )
                    )
                    break
                }
            }
            delay(4000)
        }

        if (workflowRunId == null) {
            failBuild(buildId, "Não foi possível sincronizar com a execução do GitHub Actions após o disparo.")
            return
        }

        // Loop de Acompanhamento em Segundo Plano
        var isCompleted = false
        var pollCycles = 0
        val maxCycles = 180 // Até 30 minutos

        while (!isCompleted && pollCycles < maxCycles) {
            pollCycles++
            delay(7000)

            val runRes = gitHubService.getWorkflowRun(token, owner, repo, workflowRunId)
            if (runRes.isFailure) continue

            val run = runRes.getOrThrow()

            // Atualiza status detalhado a partir dos jobs/etapas
            val jobsRes = gitHubService.getRunJobs(token, owner, repo, workflowRunId)
            var currentStepName: String? = null
            var firstJobId: Long? = null

            if (jobsRes.isSuccess) {
                val jobs = jobsRes.getOrThrow()
                val job = jobs.firstOrNull()
                firstJobId = job?.id
                currentStepName = job?.currentStepName
            }

            if (run.status == "in_progress") {
                val detail = if (!currentStepName.isNullOrBlank()) {
                    "Etapa: $currentStepName"
                } else {
                    "Executando assembleRelease no Gradle..."
                }
                updateStatus(buildId, BuildStatus.BUILDING, detail)
            } else if (run.status == "queued") {
                updateStatus(buildId, BuildStatus.QUEUED, "Na fila de espera do GitHub Actions...")
            }

            if (run.status == "completed") {
                isCompleted = true
                val conclusion = run.conclusion

                if (conclusion == "success") {
                    handleBuildSuccess(buildId, workflowRunId, firstJobId)
                } else {
                    handleBuildFailure(buildId, workflowRunId, firstJobId, conclusion ?: "falha")
                }
            }
        }

        if (!isCompleted) {
            failBuild(buildId, "Tempo limite excedido aguardando a compilação no GitHub Actions.")
        }
    }

    private suspend fun handleBuildSuccess(buildId: String, runId: Long, jobId: Long?) {
        val buildDao = database.buildDao()
        val record = buildDao.getBuildById(buildId) ?: return
        val token = preferences.githubToken
        val owner = record.repoOwner
        val repo = record.repoName

        updateStatus(buildId, BuildStatus.DOWNLOADING_ARTIFACT, "Compilação concluída! Localizando APK Release gerado...")

        // Busca artefatos
        delay(2000)
        val artifactsRes = gitHubService.getRunArtifacts(token, owner, repo, runId)
        val artifacts = artifactsRes.getOrNull().orEmpty()
        val apkArtifact = artifacts.firstOrNull { it.name.startsWith("release-apk") || it.name.contains("apk") }

        // Carrega os logs para registro no histórico
        var fullLogs: String? = null
        if (jobId != null) {
            val logsRes = gitHubService.getJobLogs(token, owner, repo, jobId)
            fullLogs = logsRes.getOrNull()
        }

        var downloadedFile: File? = null

        if (apkArtifact != null && preferences.isAutoDownloadApk) {
            updateStatus(buildId, BuildStatus.DOWNLOADING_ARTIFACT, "Baixando APK Release gerado (${formatSize(apkArtifact.sizeInBytes)})...")

            val tempZip = File(cacheDir, "artifact_$buildId.zip")
            val dlRes = gitHubService.downloadArtifactZip(token, owner, repo, apkArtifact.id, tempZip)

            if (dlRes.isSuccess && tempZip.exists()) {
                val apkName = "${record.projectName.replace(' ', '_')}-release-$buildId.apk"
                val publicDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetApk = File(publicDownloadsDir, apkName)

                // Extrai o APK de dentro do zip do artefato
                val extracted = extractApkFromZip(tempZip, targetApk)
                tempZip.delete()

                if (extracted && targetApk.exists()) {
                    downloadedFile = targetApk
                }
            }
        }

        val finalRecord = record.copy(
            status = BuildStatus.SUCCESS,
            statusDetail = "APK Release pronto para instalação",
            finishedAtTimestamp = System.currentTimeMillis(),
            artifactId = apkArtifact?.id,
            apkFileName = downloadedFile?.name ?: "${record.projectName}-release.apk",
            apkLocalPath = downloadedFile?.absolutePath,
            apkSizeBytes = downloadedFile?.length() ?: apkArtifact?.sizeInBytes,
            downloadUrl = apkArtifact?.archiveDownloadUrl,
            logs = fullLogs
        )

        buildDao.updateBuild(finalRecord)
        notificationManager.notifySuccess(buildId, record.projectName, finalRecord.apkFileName)
    }

    private suspend fun handleBuildFailure(buildId: String, runId: Long, jobId: Long?, conclusion: String) {
        val buildDao = database.buildDao()
        val record = buildDao.getBuildById(buildId) ?: return
        val token = preferences.githubToken
        val owner = record.repoOwner
        val repo = record.repoName

        var logsText: String? = null
        var extractedError = "Falha durante assembleRelease (conclusão: $conclusion)"

        if (jobId != null) {
            val logsRes = gitHubService.getJobLogs(token, owner, repo, jobId)
            if (logsRes.isSuccess) {
                logsText = logsRes.getOrThrow()
                extractedError = parseErrorFromLogs(logsText)
            }
        }

        val finalRecord = record.copy(
            status = BuildStatus.FAILED,
            statusDetail = "Falha: $extractedError",
            errorMessage = extractedError,
            finishedAtTimestamp = System.currentTimeMillis(),
            logs = logsText
        )

        buildDao.updateBuild(finalRecord)
        notificationManager.notifyFailure(buildId, record.projectName, extractedError)
    }

    private suspend fun failBuild(buildId: String, message: String) {
        val buildDao = database.buildDao()
        val record = buildDao.getBuildById(buildId) ?: return
        buildDao.updateBuild(
            record.copy(
                status = BuildStatus.FAILED,
                statusDetail = message,
                errorMessage = message,
                finishedAtTimestamp = System.currentTimeMillis()
            )
        )
        notificationManager.notifyFailure(buildId, record.projectName, message)
    }

    private suspend fun updateStatus(buildId: String, status: BuildStatus, detail: String) {
        val buildDao = database.buildDao()
        val record = buildDao.getBuildById(buildId) ?: return
        buildDao.updateBuild(
            record.copy(
                status = status,
                statusDetail = detail
            )
        )
        val notif = notificationManager.buildForegroundNotification(buildId, record.projectName, detail)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    private fun cancelActiveBuild(buildId: String) {
        serviceScope.launch {
            val buildDao = database.buildDao()
            val record = buildDao.getBuildById(buildId) ?: return@launch
            val runId = record.workflowRunId
            if (runId != null) {
                gitHubService.cancelWorkflowRun(
                    preferences.githubToken,
                    record.repoOwner,
                    record.repoName,
                    runId
                )
            }
            buildDao.updateBuild(
                record.copy(
                    status = BuildStatus.CANCELLED,
                    statusDetail = "Compilação cancelada pelo usuário.",
                    finishedAtTimestamp = System.currentTimeMillis()
                )
            )
            notificationManager.cancelProgressNotification(buildId)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun extractApkFromZip(zipFile: File, destApkFile: File): Boolean {
        try {
            FileInputStream(zipFile).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                            destApkFile.parentFile?.mkdirs()
                            FileOutputStream(destApkFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            return true
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BuildTrackingService", "Erro ao descompactar artefato APK", e)
        }
        return false
    }

    private fun parseErrorFromLogs(logs: String): String {
        val lines = logs.lines()
        val errorLines = lines.filter {
            it.contains("ERROR:") || it.contains("FAILURE:") ||
                it.contains("Execution failed for task") || it.contains("Compilation error")
        }
        return if (errorLines.isNotEmpty()) {
            errorLines.take(3).joinToString("\n").take(250)
        } else {
            "Falha reportada na execução do Gradle assembleRelease. Inspecione os logs completos."
        }
    }

    private fun listFilesRecursively(dir: File): List<File> {
        val list = mutableListOf<File>()
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                list.addAll(listFilesRecursively(file))
            } else {
                list.add(file)
            }
        }
        return list
    }

    private fun formatSize(bytes: Long): String {
        return if (bytes < 1024 * 1024) {
            "${bytes / 1024} KB"
        } else {
            String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val NOTIFICATION_ID = 2001
        const val ACTION_START_BUILD = "com.example.compiler.START_BUILD"
        const val ACTION_CANCEL_BUILD = "com.example.compiler.CANCEL_BUILD"
        const val EXTRA_BUILD_ID = "extra_build_id"

        fun start(context: Context, buildId: String) {
            val intent = Intent(context, BuildTrackingService::class.java).apply {
                action = ACTION_START_BUILD
                putExtra(EXTRA_BUILD_ID, buildId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context, buildId: String) {
            val intent = Intent(context, BuildTrackingService::class.java).apply {
                action = ACTION_CANCEL_BUILD
                putExtra(EXTRA_BUILD_ID, buildId)
            }
            context.startService(intent)
        }
    }
}
