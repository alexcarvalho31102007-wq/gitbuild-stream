package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GitHubApiClient
import com.example.data.model.*
import com.example.data.storage.TokenStorage
import com.example.data.zip.ZipProcessor
import com.example.data.zip.ZipScanResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    AUTH,
    SELECT_ZIP,
    COMPILATION_STEPS,
    COMPILATION_LOGS
}

enum class BuildStatus {
    IDLE,
    UPLOADING,
    QUEUED,
    IN_PROGRESS,
    SUCCESS,
    FAILURE,
    CANCELLED
}

data class UiState(
    val currentScreen: AppScreen = AppScreen.AUTH,
    val token: String = "",
    val currentUser: GitHubUser? = null,
    val isCheckingAuth: Boolean = false,
    val authError: String? = null,
    
    // Repos
    val userRepos: List<GitHubRepo> = emptyList(),
    val isLoadingRepos: Boolean = false,
    val selectedRepoFullName: String = "",
    val targetBranch: String = "main",
    val isCreatingRepo: Boolean = false,
    
    // Zip
    val zipScan: ZipScanResult? = null,
    val isProcessingZip: Boolean = false,
    val zipError: String? = null,
    
    // Upload & Compilation
    val buildStatus: BuildStatus = BuildStatus.IDLE,
    val uploadProgress: Float = 0f,
    val uploadStageText: String = "",
    val activeRun: WorkflowRun? = null,
    val activeJobs: List<WorkflowJob> = emptyList(),
    val liveLogs: String = "",
    val artifacts: List<ArtifactItem> = emptyList(),
    val buildErrorMessage: String? = null,
    val isDownloadingApk: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedApkFile: java.io.File? = null,
    val downloadErrorMessage: String? = null
)

class CompilerViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = TokenStorage(application)
    private var apiClient: GitHubApiClient = GitHubApiClient { storage.token }
    private val notificationHelper = com.example.ui.notification.CompilationNotificationHelper(application)

    private val _uiState = MutableStateFlow(
        UiState(
            token = storage.token,
            selectedRepoFullName = storage.selectedRepo,
            targetBranch = storage.targetBranch.ifBlank { "main" }
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var compilationPollingJob: Job? = null

    init {
        if (storage.token.isNotBlank()) {
            validateAndLoadUser(storage.token)
        }
    }

    private fun extractErrorMessage(e: Throwable): String {
        if (e is HttpException) {
            val code = e.code()
            val rawBody = try {
                e.response()?.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            if (!rawBody.isNullOrBlank()) {
                try {
                    val json = JSONObject(rawBody)
                    val msg = json.optString("message", "")
                    if (msg.isNotBlank()) {
                        return "HTTP $code: $msg"
                    }
                } catch (_: Exception) {
                    return "HTTP $code: $rawBody"
                }
            }
            return "HTTP $code: ${e.message()}"
        }
        return e.localizedMessage ?: e.javaClass.simpleName
    }

    fun onTokenChanged(newToken: String) {
        _uiState.value = _uiState.value.copy(token = newToken, authError = null)
    }

    fun submitToken(token: String) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            _uiState.value = _uiState.value.copy(authError = "Por favor, insira um token do GitHub válido.")
            return
        }
        storage.token = cleanToken
        apiClient = GitHubApiClient { cleanToken }
        validateAndLoadUser(cleanToken)
    }

    private fun validateAndLoadUser(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingAuth = true, authError = null)
            try {
                val user = apiClient.api.getCurrentUser()
                storage.lastUsername = user.login
                _uiState.value = _uiState.value.copy(
                    currentUser = user,
                    isCheckingAuth = false,
                    currentScreen = if (_uiState.value.currentScreen == AppScreen.AUTH) AppScreen.SELECT_ZIP else _uiState.value.currentScreen
                )
                loadUserRepos()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCheckingAuth = false,
                    authError = "Falha ao autenticar com o GitHub: ${extractErrorMessage(e)}"
                )
            }
        }
    }

    fun loadUserRepos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingRepos = true)
            try {
                val repos = apiClient.api.getUserRepos(perPage = 100)
                var currentSelected = _uiState.value.selectedRepoFullName
                var currentBranch = _uiState.value.targetBranch

                if (currentSelected.isBlank() && repos.isNotEmpty()) {
                    currentSelected = repos.first().fullName
                    currentBranch = repos.first().defaultBranch.ifBlank { "main" }
                    storage.selectedRepo = currentSelected
                    storage.targetBranch = currentBranch
                } else if (repos.isNotEmpty()) {
                    val found = repos.firstOrNull { it.fullName.equals(currentSelected, ignoreCase = true) }
                    if (found != null && currentBranch.isBlank()) {
                        currentBranch = found.defaultBranch.ifBlank { "main" }
                        storage.targetBranch = currentBranch
                    }
                }

                _uiState.value = _uiState.value.copy(
                    userRepos = repos,
                    selectedRepoFullName = currentSelected,
                    targetBranch = currentBranch.ifBlank { "main" },
                    isLoadingRepos = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingRepos = false)
            }
        }
    }

    fun onSelectRepo(repoFullName: String) {
        val cleanName = repoFullName.trim()
        val matchingRepo = _uiState.value.userRepos.firstOrNull { it.fullName.equals(cleanName, ignoreCase = true) }
        val branch = matchingRepo?.defaultBranch?.ifBlank { "main" } ?: _uiState.value.targetBranch.ifBlank { "main" }

        storage.selectedRepo = cleanName
        storage.targetBranch = branch
        _uiState.value = _uiState.value.copy(selectedRepoFullName = cleanName, targetBranch = branch)
    }

    fun onBranchChanged(branch: String) {
        val cleanBranch = branch.trim().ifBlank { "main" }
        storage.targetBranch = cleanBranch
        _uiState.value = _uiState.value.copy(targetBranch = cleanBranch)
    }

    fun createDedicatedBuildRepo(repoName: String = "git-compile-runner") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingRepo = true)
            try {
                val newRepo = apiClient.api.createRepo(
                    CreateRepoRequest(
                        name = repoName,
                        description = "Repositório dedicado criado pelo GitCompile para compilação via GitHub Actions",
                        private = true,
                        autoInit = true
                    )
                )
                storage.selectedRepo = newRepo.fullName
                storage.targetBranch = newRepo.defaultBranch.ifBlank { "main" }
                loadUserRepos()
                _uiState.value = _uiState.value.copy(
                    selectedRepoFullName = newRepo.fullName,
                    targetBranch = newRepo.defaultBranch.ifBlank { "main" },
                    isCreatingRepo = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreatingRepo = false,
                    authError = "Não foi possível criar o repositório: ${extractErrorMessage(e)}"
                )
            }
        }
    }

    fun logout() {
        compilationPollingJob?.cancel()
        storage.clear()
        apiClient = GitHubApiClient { "" }
        _uiState.value = UiState(currentScreen = AppScreen.AUTH)
    }

    fun navigateTo(screen: AppScreen) {
        _uiState.value = _uiState.value.copy(currentScreen = screen)
    }

    fun onZipSelected(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingZip = true, zipError = null)
            try {
                val scanResult = withContext(Dispatchers.IO) {
                    ZipProcessor.processZipUri(getApplication(), uri, fileName)
                }
                _uiState.value = _uiState.value.copy(
                    zipScan = scanResult,
                    isProcessingZip = false,
                    zipError = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessingZip = false,
                    zipError = "Erro ao processar arquivo ZIP: ${e.localizedMessage ?: "Arquivo inválido ou corrompido"}"
                )
            }
        }
    }

    fun clearSelectedZip() {
        _uiState.value = _uiState.value.copy(zipScan = null, zipError = null)
    }

    fun startCompilation() {
        val state = _uiState.value
        val scan = state.zipScan ?: return
        val repoFullName = state.selectedRepoFullName.ifBlank {
            _uiState.value = _uiState.value.copy(zipError = "Selecione ou informe um repositório do GitHub.")
            return
        }

        val parts = repoFullName.split("/")
        if (parts.size != 2) {
            _uiState.value = _uiState.value.copy(zipError = "Formato de repositório inválido. Use 'usuario/repositorio'.")
            return
        }
        val owner = parts[0].trim()
        val repo = parts[1].trim()
        val branch = state.targetBranch.trim().ifBlank { "main" }

        compilationPollingJob?.cancel()
        _uiState.value = _uiState.value.copy(
            currentScreen = AppScreen.COMPILATION_STEPS,
            buildStatus = BuildStatus.UPLOADING,
            uploadProgress = 0.05f,
            uploadStageText = "Conectando ao repositório $repoFullName...",
            activeRun = null,
            activeJobs = emptyList(),
            liveLogs = "Iniciando processo de compilação em nuvem...\n",
            artifacts = emptyList(),
            buildErrorMessage = null
        )

        compilationPollingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                appendLog("Conectando ao GitHub ($repoFullName)...")
                val repoDetails = apiClient.api.getRepo(owner, repo)
                val defaultBranch = repoDetails.defaultBranch.ifBlank { "main" }
                val effectiveBranch = if (branch.isBlank()) defaultBranch else branch

                appendLog("Ramo de compilação: $effectiveBranch (Padrão: $defaultBranch)")

                // Step 1: Check existing branch commit to use as parent
                var parentCommitSha: String? = null
                var branchExists = false

                try {
                    val branchRefResp = apiClient.api.getBranchRef(owner, repo, effectiveBranch)
                    if (branchRefResp.isSuccessful && branchRefResp.body()?.obj != null) {
                        branchExists = true
                        parentCommitSha = branchRefResp.body()?.obj?.sha
                        appendLog("Ramo '$effectiveBranch' localizado (Commit: ${parentCommitSha?.take(7)}).")
                    }
                } catch (_: Exception) {
                    branchExists = false
                }

                if (!branchExists && effectiveBranch != defaultBranch) {
                    try {
                        val defRefResp = apiClient.api.getBranchRef(owner, repo, defaultBranch)
                        if (defRefResp.isSuccessful && defRefResp.body()?.obj != null) {
                            parentCommitSha = defRefResp.body()?.obj?.sha
                            appendLog("Usando base de '$defaultBranch' (Commit: ${parentCommitSha?.take(7)}).")
                        }
                    } catch (_: Exception) {
                        // Repository might be empty
                    }
                }

                // Step 2: Prepare Git Tree Items
                val totalFiles = scan.files.size
                appendLog("Enviando $totalFiles arquivos do código-fonte para o GitHub...")
                _uiState.value = _uiState.value.copy(
                    uploadStageText = "Enviando $totalFiles arquivos para o GitHub...",
                    uploadProgress = 0.15f
                )

                val treeItems = mutableListOf<GitTreeItem>()
                for ((index, file) in scan.files.withIndex()) {
                    if (file.isText && file.bytes.size < 60 * 1024) {
                        val contentString = String(file.bytes, Charsets.UTF_8)
                        treeItems.add(
                            GitTreeItem(
                                path = file.path,
                                mode = "100644",
                                type = "blob",
                                content = contentString
                            )
                        )
                    } else {
                        val b64 = Base64.encodeToString(file.bytes, Base64.NO_WRAP)
                        val blobResp = apiClient.api.createBlob(owner, repo, GitBlobRequest(content = b64))
                        treeItems.add(
                            GitTreeItem(
                                path = file.path,
                                mode = "100644",
                                type = "blob",
                                sha = blobResp.sha
                            )
                        )
                    }
                    val currentProgress = 0.15f + (0.50f * (index + 1) / totalFiles)
                    _uiState.value = _uiState.value.copy(
                        uploadProgress = currentProgress,
                        uploadStageText = "Enviado (${index + 1}/$totalFiles): ${file.path.takeLast(30)}"
                    )
                    if (index % 10 == 0 || index == totalFiles - 1) {
                        val pct = ((index + 1) * 100 / totalFiles)
                        notificationHelper.updateProgress(scan.fileName, 2, "Enviando código ao GitHub ($pct%)", 5)
                    }
                }

                // Step 3: Create Git Tree
                appendLog("Gerando Git Tree no GitHub...")
                _uiState.value = _uiState.value.copy(
                    uploadStageText = "Criando árvore Git com os arquivos...",
                    uploadProgress = 0.75f
                )
                val treeResponse = apiClient.api.createTree(
                    owner,
                    repo,
                    GitTreeRequest(tree = treeItems)
                )
                appendLog("Árvore Git criada (SHA: ${treeResponse.sha.take(7)}).")

                // Step 4: Create Commit
                appendLog("Criando commit de compilação...")
                _uiState.value = _uiState.value.copy(
                    uploadStageText = "Registrando commit de compilação...",
                    uploadProgress = 0.85f
                )
                val commitMsg = "Compilação de código via GitCompile: ${scan.fileName} [${SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()).format(Date())}]"
                val commitResponse = apiClient.api.createCommit(
                    owner,
                    repo,
                    GitCommitRequest(
                        message = commitMsg,
                        tree = treeResponse.sha,
                        parents = if (parentCommitSha != null) listOf(parentCommitSha) else emptyList()
                    )
                )
                appendLog("Commit criado com sucesso (SHA: ${commitResponse.sha.take(7)}).")

                // Step 5: Update or Create Branch Ref safely
                appendLog("Atualizando referência do ramo '$effectiveBranch'...")
                var refUpdated = false

                if (branchExists) {
                    try {
                        apiClient.api.updateBranchRef(
                            owner,
                            repo,
                            effectiveBranch,
                            GitUpdateRefRequest(sha = commitResponse.sha, force = true)
                        )
                        refUpdated = true
                        appendLog("Ramo '$effectiveBranch' atualizado com sucesso.")
                    } catch (e: Exception) {
                        appendLog("Aviso ao atualizar ramo: ${extractErrorMessage(e)}. Tentando alternativa...")
                    }
                }

                if (!refUpdated) {
                    try {
                        apiClient.api.createRef(
                            owner,
                            repo,
                            GitCreateRefRequest(ref = "refs/heads/$effectiveBranch", sha = commitResponse.sha)
                        )
                        appendLog("Ramo '$effectiveBranch' criado com sucesso.")
                    } catch (e: Exception) {
                        // If 409 (Reference already exists), force update
                        try {
                            apiClient.api.updateBranchRef(
                                owner,
                                repo,
                                effectiveBranch,
                                GitUpdateRefRequest(sha = commitResponse.sha, force = true)
                            )
                            appendLog("Ramo '$effectiveBranch' forçado para o commit ${commitResponse.sha.take(7)}.")
                        } catch (e2: Exception) {
                            throw Exception("Falha ao definir o ramo '$effectiveBranch': ${extractErrorMessage(e2)}")
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    uploadProgress = 1.0f,
                    uploadStageText = "Código enviado! Disparando runners do GitHub Actions...",
                    buildStatus = BuildStatus.QUEUED
                )
                appendLog("\n========================================================")
                appendLog("✓ Código-fonte enviado com sucesso para o GitHub!")
                appendLog("Aguardando GitHub Actions provisionar os runners...")
                appendLog("========================================================\n")

                // Try to dispatch workflow directly if already registered
                try {
                    apiClient.api.dispatchWorkflow(
                        owner,
                        repo,
                        "compile.yml",
                        WorkflowDispatchRequest(ref = effectiveBranch)
                    )
                    appendLog("Disparo manual workflow_dispatch enviado ao GitHub Actions.")
                } catch (_: Exception) {
                    // Push trigger is primary
                }

                // Step 6: Poll GitHub Actions Workflow Runs
                pollWorkflowRun(owner, repo, effectiveBranch, commitResponse.sha)

            } catch (e: Exception) {
                val errorMsg = "Erro durante o envio: ${extractErrorMessage(e)}"
                appendLog("\n❌ $errorMsg")
                _uiState.value = _uiState.value.copy(
                    buildStatus = BuildStatus.FAILURE,
                    buildErrorMessage = errorMsg
                )
            }
        }
    }

    private suspend fun pollWorkflowRun(owner: String, repo: String, branch: String, commitSha: String) {
        val fileName = _uiState.value.zipScan?.fileName ?: "Projeto.zip"
        var foundRun: WorkflowRun? = null
        var attempts = 0
        val maxFindAttempts = 30 // up to ~75s
        var lastDispatchAttempt = 0

        while (foundRun == null && attempts < maxFindAttempts && currentCoroutineContext().isActive) {
            attempts++
            delay(2500)
            try {
                // Query all runs in the repo to avoid query-param mismatch
                val runsResp = apiClient.api.getWorkflowRuns(owner, repo, branch = null, perPage = 10)
                val matchingRun = runsResp.workflowRuns.firstOrNull { it.headSha == commitSha }
                    ?: runsResp.workflowRuns.firstOrNull { it.headBranch == branch }
                    ?: runsResp.workflowRuns.firstOrNull()

                if (matchingRun != null) {
                    foundRun = matchingRun
                    appendLog("GitHub Actions detectado: Execução #${matchingRun.runNumber} (${matchingRun.name})")
                    notificationHelper.updateProgress(fileName, 3, "Runner alocado para a execução #${matchingRun.runNumber}", 5)
                } else {
                    appendLog("Aguardando GitHub Actions enfileirar o build (tentativa $attempts)...")
                    notificationHelper.updateProgress(fileName, 3, "Aguardando Runner do GitHub Actions...", 5)
                    if (attempts >= 6 && attempts - lastDispatchAttempt >= 6) {
                        lastDispatchAttempt = attempts
                        try {
                            apiClient.api.dispatchWorkflow(
                                owner,
                                repo,
                                "compile.yml",
                                WorkflowDispatchRequest(ref = branch)
                            )
                            appendLog("Reenviando sinal de inicialização para o GitHub Actions...")
                        } catch (_: Exception) { }
                    }
                }
            } catch (e: Exception) {
                appendLog("Verificando status: ${extractErrorMessage(e)}")
            }
        }

        if (foundRun == null) {
            appendLog("\n⚠️ O GitHub Actions não iniciou automaticamente em 75 segundos.")
            appendLog("Verificações recomendadas:")
            appendLog("1. No GitHub, vá em https://github.com/$owner/$repo/actions e verifique se as Actions estão habilitadas (botão 'I understand my workflows, go ahead and enable them').")
            appendLog("2. Verifique se o seu Token tem o escopo 'workflow' marcado.")
            appendLog("3. Se o repositório foi recém-criado, o GitHub pode levar até 1 minuto para aprovar a primeira execução de workflow.")
            _uiState.value = _uiState.value.copy(
                buildStatus = BuildStatus.FAILURE,
                buildErrorMessage = "Timeout: GitHub Actions não iniciou. Verifique se as Actions estão habilitadas no repositório."
            )
            notificationHelper.notifyFailure(fileName, "Timeout no GitHub Actions")
            return
        }

        val runId = foundRun.id
        var isCompleted = false
        var lastLogJobId: Long? = null

        while (!isCompleted && currentCoroutineContext().isActive) {
            try {
                val currentRun = apiClient.api.getWorkflowRun(owner, repo, runId)
                val jobsResp = apiClient.api.getWorkflowRunJobs(owner, repo, runId)
                val jobs = jobsResp.jobs

                val primaryJob = jobs.firstOrNull()
                if (primaryJob != null && primaryJob.id != lastLogJobId) {
                    lastLogJobId = primaryJob.id
                }

                // Fetch latest live logs
                if (primaryJob != null) {
                    val logs = apiClient.fetchJobLogsText(owner, repo, primaryJob.id)
                    if (logs.isNotBlank() && logs.length > 20) {
                        _uiState.value = _uiState.value.copy(liveLogs = logs)
                    }
                }

                val statusEnum = when (currentRun.status) {
                    "completed" -> {
                        isCompleted = true
                        when (currentRun.conclusion) {
                            "success" -> BuildStatus.SUCCESS
                            "failure" -> BuildStatus.FAILURE
                            "cancelled" -> BuildStatus.CANCELLED
                            else -> BuildStatus.FAILURE
                        }
                    }
                    "in_progress" -> BuildStatus.IN_PROGRESS
                    else -> BuildStatus.QUEUED
                }

                _uiState.value = _uiState.value.copy(
                    activeRun = currentRun,
                    activeJobs = jobs,
                    buildStatus = statusEnum
                )

                if (statusEnum == BuildStatus.IN_PROGRESS) {
                    val currentStep = primaryJob?.steps?.firstOrNull { it.status == "in_progress" }?.name
                        ?: "Compilando com Gradle no Ubuntu Runner..."
                    notificationHelper.updateProgress(fileName, 4, currentStep, 5)
                } else if (statusEnum == BuildStatus.QUEUED) {
                    notificationHelper.updateProgress(fileName, 3, "Aguardando executor virtual...", 5)
                }

                if (isCompleted) {
                    try {
                        val artifactsResp = apiClient.api.getWorkflowArtifacts(owner, repo, runId)
                        _uiState.value = _uiState.value.copy(artifacts = artifactsResp.artifacts)
                        if (artifactsResp.artifacts.isNotEmpty()) {
                            appendLog("\n📦 Artefatos compilados disponíveis para download: ${artifactsResp.artifacts.size} arquivo(s)")
                        }
                    } catch (_: Exception) { }

                    if (statusEnum == BuildStatus.SUCCESS) {
                        appendLog("\n🎉 COMPILAÇÃO FINALIZADA COM SUCESSO NO GITHUB ACTIONS!")
                        notificationHelper.notifySuccess(fileName, "APK")
                    } else if (statusEnum == BuildStatus.FAILURE) {
                        appendLog("\n❌ COMPILAÇÃO FALHOU NO GITHUB ACTIONS. Verifique os erros acima.")
                        notificationHelper.notifyFailure(fileName, "Falha na compilação do Gradle")
                    } else {
                        notificationHelper.cancelNotification()
                    }
                    break
                }

            } catch (e: Exception) {
                // Continue polling
            }
            delay(2800)
        }
    }

    private fun appendLog(line: String) {
        val current = _uiState.value.liveLogs
        _uiState.value = _uiState.value.copy(liveLogs = if (current.isEmpty()) line else "$current\n$line")
    }

    fun downloadArtifact(artifact: ArtifactItem) {
        val fileName = _uiState.value.zipScan?.fileName ?: "Projeto.zip"
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloadingApk = true,
                downloadProgress = 0f,
                downloadErrorMessage = null
            )
            val file = com.example.data.zip.ArtifactDownloader.downloadAndExtractApk(
                getApplication(),
                apiClient,
                artifact
            ) { progress ->
                _uiState.value = _uiState.value.copy(downloadProgress = progress)
            }

            if (file != null && file.exists() && file.length() > 0L) {
                _uiState.value = _uiState.value.copy(
                    isDownloadingApk = false,
                    downloadedApkFile = file,
                    downloadErrorMessage = null
                )
                notificationHelper.notifySuccess(fileName, file.name)
            } else {
                _uiState.value = _uiState.value.copy(
                    isDownloadingApk = false,
                    downloadErrorMessage = "Falha ao baixar o APK. Verifique sua conexão com o GitHub."
                )
            }
        }
    }

    fun installDownloadedApk(context: android.content.Context) {
        val file = _uiState.value.downloadedApkFile ?: return
        com.example.data.zip.ArtifactDownloader.installOrOpenApk(context, file)
    }

    fun cancelCompilation() {
        val state = _uiState.value
        val run = state.activeRun ?: return
        val parts = state.selectedRepoFullName.split("/")
        if (parts.size != 2) return

        notificationHelper.cancelNotification()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                apiClient.api.cancelWorkflowRun(parts[0], parts[1], run.id)
                appendLog("Cancelamento solicitado para a execução #${run.runNumber}...")
            } catch (e: Exception) {
                appendLog("Não foi possível cancelar: ${extractErrorMessage(e)}")
            }
        }
    }

    fun resetCompilation() {
        compilationPollingJob?.cancel()
        notificationHelper.cancelNotification()
        _uiState.value = _uiState.value.copy(
            currentScreen = AppScreen.SELECT_ZIP,
            buildStatus = BuildStatus.IDLE,
            activeRun = null,
            activeJobs = emptyList(),
            liveLogs = "",
            artifacts = emptyList(),
            buildErrorMessage = null,
            isDownloadingApk = false,
            downloadProgress = 0f,
            downloadedApkFile = null,
            downloadErrorMessage = null
        )
    }
}
