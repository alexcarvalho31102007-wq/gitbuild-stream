package com.example.compiler.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.compiler.data.BuildDao
import com.example.compiler.data.BuildRecordEntity
import com.example.compiler.data.BuildStatus
import com.example.compiler.data.CompilerPreferences
import com.example.compiler.github.GitHubApiService
import com.example.compiler.github.GitHubRepo
import com.example.compiler.github.GitHubUser
import com.example.compiler.service.BuildTrackingService
import com.example.compiler.zip.ParsedProject
import com.example.compiler.zip.ProjectZipProcessor
import com.example.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

enum class HistoryFilterTab {
    ALL,
    IN_PROGRESS,
    SUCCESS,
    FAILED
}

sealed class CompilerUiEvent {
    data class ShowSnackbar(val message: String) : CompilerUiEvent()
    data class OpenLogViewer(val build: BuildRecordEntity) : CompilerUiEvent()
}

class CompilerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val buildDao: BuildDao = database.buildDao()
    val preferences = CompilerPreferences(application)
    private val gitHubService = GitHubApiService()
    private val zipProcessor = ProjectZipProcessor(application)

    val allBuilds: StateFlow<List<BuildRecordEntity>> = buildDao.getAllBuildsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeBuilds: StateFlow<List<BuildRecordEntity>> = buildDao.getActiveBuildsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProject = MutableStateFlow<ParsedProject?>(null)
    val selectedProject: StateFlow<ParsedProject?> = _selectedProject.asStateFlow()

    private val _isAnalyzingZip = MutableStateFlow(false)
    val isAnalyzingZip: StateFlow<Boolean> = _isAnalyzingZip.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilterTab.ALL)
    val historyFilter: StateFlow<HistoryFilterTab> = _historyFilter.asStateFlow()

    private val _githubUser = MutableStateFlow<GitHubUser?>(null)
    val githubUser: StateFlow<GitHubUser?> = _githubUser.asStateFlow()

    private val _userRepos = MutableStateFlow<List<GitHubRepo>>(emptyList())
    val userRepos: StateFlow<List<GitHubRepo>> = _userRepos.asStateFlow()

    private val _isTestingToken = MutableStateFlow(false)
    val isTestingToken: StateFlow<Boolean> = _isTestingToken.asStateFlow()

    private val _tokenTestResult = MutableStateFlow<String?>(null)
    val tokenTestResult: StateFlow<String?> = _tokenTestResult.asStateFlow()

    private val _isCreatingRepo = MutableStateFlow(false)
    val isCreatingRepo: StateFlow<Boolean> = _isCreatingRepo.asStateFlow()

    private val _uiEvent = MutableSharedFlow<CompilerUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    init {
        val savedToken = preferences.githubToken
        if (savedToken.isNotBlank()) {
            loadGitHubUserAndRepos(savedToken)
        }
    }

    fun setHistoryFilter(tab: HistoryFilterTab) {
        _historyFilter.value = tab
    }

    fun onProjectZipSelected(uri: Uri) {
        viewModelScope.launch {
            _isAnalyzingZip.value = true
            _analysisError.value = null
            _selectedProject.value = null

            val result = zipProcessor.parseUserProjectZip(uri)
            _isAnalyzingZip.value = false

            if (result.isSuccess) {
                _selectedProject.value = result.getOrThrow()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Erro ao ler arquivo ZIP"
                _analysisError.value = errorMsg
                emitUiEvent(CompilerUiEvent.ShowSnackbar(errorMsg))
            }
        }
    }

    fun clearSelectedProject() {
        _selectedProject.value = null
        _analysisError.value = null
    }

    fun startBuild(context: Context) {
        val project = _selectedProject.value
        if (project == null) {
            emitUiEvent(CompilerUiEvent.ShowSnackbar("Selecione um projeto ZIP antes de compilar."))
            return
        }

        if (!preferences.isConfigured) {
            emitUiEvent(CompilerUiEvent.ShowSnackbar("Configure o Token e o Repositório do GitHub na aba Configurações."))
            return
        }

        val hasActiveBuilds = activeBuilds.value.isNotEmpty()
        if (hasActiveBuilds) {
            emitUiEvent(CompilerUiEvent.ShowSnackbar("Aguarde a compilação atual terminar ou cancele-a no histórico."))
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val datePrefix = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val randomSuffix = Random.nextInt(1000, 9999).toString()
            val buildId = "BLD-$datePrefix-$randomSuffix"
            val branchName = "build/$buildId"

            // 1. Grava arquivos em diretório temporário para o serviço ler com segurança sem estourar memória do Intent
            val stagingDir = File(context.cacheDir, "staging_$buildId")
            stagingDir.mkdirs()

            project.files.forEach { (path, bytes) ->
                val targetFile = File(stagingDir, path)
                targetFile.parentFile?.mkdirs()
                FileOutputStream(targetFile).use { it.write(bytes) }
            }

            // 2. Cria registro inicial no banco de dados
            val buildRecord = BuildRecordEntity(
                id = buildId,
                projectName = project.projectName,
                packageName = project.packageName,
                fileCount = project.fileCount,
                zipSizeBytes = project.zipSizeBytes,
                createdAtTimestamp = System.currentTimeMillis(),
                status = BuildStatus.PREPARING,
                statusDetail = "Iniciando compilação Release...",
                repoOwner = preferences.repoOwner,
                repoName = preferences.repoName,
                branchName = branchName
            )

            buildDao.insertBuild(buildRecord)

            // Limpa o projeto selecionado na tela inicial
            withContext(Dispatchers.Main) {
                _selectedProject.value = null
                emitUiEvent(CompilerUiEvent.ShowSnackbar("Compilação #$buildId enviada com sucesso!"))
            }

            // 3. Inicia o serviço em segundo plano para envio, acionamento e monitoramento
            BuildTrackingService.start(context, buildId)
        }
    }

    fun cancelBuild(context: Context, buildId: String) {
        BuildTrackingService.cancel(context, buildId)
        emitUiEvent(CompilerUiEvent.ShowSnackbar("Cancelamento solicitado para #$buildId"))
    }

    fun deleteBuild(buildId: String) {
        viewModelScope.launch {
            val record = buildDao.getBuildById(buildId)
            if (record != null && record.status.isActive) {
                emitUiEvent(CompilerUiEvent.ShowSnackbar("Não é possível excluir uma compilação em andamento."))
                return@launch
            }
            buildDao.deleteBuild(buildId)
            emitUiEvent(CompilerUiEvent.ShowSnackbar("Registro #$buildId removido do histórico."))
        }
    }

    fun testGitHubToken(token: String) {
        viewModelScope.launch {
            val clean = token.trim()
            if (clean.isBlank()) {
                _tokenTestResult.value = "Por favor, informe o token do GitHub."
                return@launch
            }
            _isTestingToken.value = true
            _tokenTestResult.value = null

            val userRes = gitHubService.getUser(clean)
            _isTestingToken.value = false

            if (userRes.isSuccess) {
                val user = userRes.getOrThrow()
                _githubUser.value = user
                preferences.githubToken = clean
                _tokenTestResult.value = "Conectado como @${user.login} (${user.name ?: "Sem nome"})."
                loadGitHubUserAndRepos(clean)
                emitUiEvent(CompilerUiEvent.ShowSnackbar("Token validado com sucesso! Conectado a @${user.login}"))
            } else {
                val error = userRes.exceptionOrNull()?.message ?: "Falha ao validar token"
                _tokenTestResult.value = "Erro: $error"
                emitUiEvent(CompilerUiEvent.ShowSnackbar(error))
            }
        }
    }

    fun setRepository(owner: String, repo: String) {
        preferences.repoOwner = owner.trim()
        preferences.repoName = repo.trim()
        emitUiEvent(CompilerUiEvent.ShowSnackbar("Repositório selecionado: $owner/$repo"))
    }

    fun createDedicatedRepository(repoName: String, isPrivate: Boolean) {
        val token = preferences.githubToken
        if (token.isBlank()) {
            emitUiEvent(CompilerUiEvent.ShowSnackbar("Configure seu token do GitHub primeiro."))
            return
        }
        val cleanName = repoName.trim().replace(' ', '-')
        if (cleanName.isBlank()) {
            emitUiEvent(CompilerUiEvent.ShowSnackbar("Informe um nome para o repositório."))
            return
        }

        viewModelScope.launch {
            _isCreatingRepo.value = true
            val createRes = gitHubService.createRepo(token, cleanName, isPrivate)
            _isCreatingRepo.value = false

            if (createRes.isSuccess) {
                val repo = createRes.getOrThrow()
                preferences.repoOwner = repo.owner
                preferences.repoName = repo.name
                loadGitHubUserAndRepos(token)
                emitUiEvent(CompilerUiEvent.ShowSnackbar("Repositório criado: ${repo.fullName}"))
            } else {
                val error = createRes.exceptionOrNull()?.message ?: "Erro ao criar repositório"
                emitUiEvent(CompilerUiEvent.ShowSnackbar(error))
            }
        }
    }

    fun downloadApkManually(context: Context, record: BuildRecordEntity) {
        val artifactId = record.artifactId
        val token = preferences.githubToken
        if (artifactId == null || token.isBlank()) {
            emitUiEvent(CompilerUiEvent.ShowSnackbar("Artefato do APK não disponível para download."))
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                emitUiEvent(CompilerUiEvent.ShowSnackbar("Baixando APK Release..."))
            }

            val tempZip = File(context.cacheDir, "manual_${record.id}.zip")
            val dlRes = gitHubService.downloadArtifactZip(
                token,
                record.repoOwner,
                record.repoName,
                artifactId,
                tempZip
            )

            if (dlRes.isSuccess && tempZip.exists()) {
                val apkName = "${record.projectName.replace(' ', '_')}-release-${record.id}.apk"
                val publicDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetApk = File(publicDownloadsDir, apkName)

                val extracted = extractApkFromZip(tempZip, targetApk)
                tempZip.delete()

                if (extracted && targetApk.exists()) {
                    buildDao.updateBuild(
                        record.copy(
                            apkLocalPath = targetApk.absolutePath,
                            apkFileName = targetApk.name,
                            apkSizeBytes = targetApk.length()
                        )
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "APK salvo em Downloads: ${targetApk.name}", Toast.LENGTH_LONG).show()
                        emitUiEvent(CompilerUiEvent.ShowSnackbar("APK salvo em Downloads!"))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        emitUiEvent(CompilerUiEvent.ShowSnackbar("Não foi possível extrair o APK do artefato."))
                    }
                }
            } else {
                val error = dlRes.exceptionOrNull()?.message ?: "Erro ao baixar artefato"
                withContext(Dispatchers.Main) {
                    emitUiEvent(CompilerUiEvent.ShowSnackbar("Erro no download: $error"))
                }
            }
        }
    }

    fun installApk(context: Context, filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                emitUiEvent(CompilerUiEvent.ShowSnackbar("Arquivo APK não encontrado no dispositivo."))
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            emitUiEvent(CompilerUiEvent.ShowSnackbar("Erro ao iniciar instalação: ${e.message}"))
        }
    }

    fun shareApk(context: Context, filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                emitUiEvent(CompilerUiEvent.ShowSnackbar("Arquivo APK não encontrado para compartilhar."))
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(intent, "Compartilhar APK Release"))
        } catch (e: Exception) {
            emitUiEvent(CompilerUiEvent.ShowSnackbar("Erro ao compartilhar APK: ${e.message}"))
        }
    }

    private fun loadGitHubUserAndRepos(token: String) {
        viewModelScope.launch {
            val userRes = gitHubService.getUser(token)
            if (userRes.isSuccess) {
                _githubUser.value = userRes.getOrThrow()
            }
            val reposRes = gitHubService.getUserRepos(token)
            if (reposRes.isSuccess) {
                _userRepos.value = reposRes.getOrThrow()
            }
        }
    }

    private fun extractApkFromZip(zipFile: File, destApkFile: File): Boolean {
        try {
            java.io.FileInputStream(zipFile).use { fis ->
                java.util.zip.ZipInputStream(fis).use { zis ->
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
        } catch (_: Exception) {}
        return false
    }

    private fun emitUiEvent(event: CompilerUiEvent) {
        viewModelScope.launch {
            _uiEvent.emit(event)
        }
    }
}
