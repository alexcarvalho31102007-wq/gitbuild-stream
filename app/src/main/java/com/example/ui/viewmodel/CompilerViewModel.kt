package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GitHubApiClient
import com.example.data.model.*
import com.example.data.storage.TokenStorage
import com.example.data.zip.ArtifactDownloader
import com.example.data.zip.ZipProcessor
import com.example.data.zip.ZipScanResult
import com.example.ui.notification.CompilationNotificationHelper
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

enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    SUCCESS,
    FAILURE
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

    // Zip & File
    val selectedFileName: String = "",
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
    val downloadErrorMessage: String? = null,

    // 4 Real Stages Tracking
    val step1Status: StepStatus = StepStatus.PENDING,
    val step1Detail: String = "Validação dos arquivos e injeção do workflow",
    val step2Status: StepStatus = StepStatus.PENDING,
    val step2Detail: String = "Criação de commit e envio para o GitHub",
    val step3Status: StepStatus = StepStatus.PENDING,
    val step3Detail: String = "Execução do Gradle no GitHub Actions",
    val step4Status: StepStatus = StepStatus.PENDING,
    val step4Detail: String = "Geração e disponibilização do APK"
)

class CompilerViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = TokenStorage(application)
    private var apiClient: GitHubApiClient = GitHubApiClient { storage.token }
    private val notificationHelper = CompilationNotificationHelper(application)

    private val _uiState = MutableStateFlow(
        UiState(
            token = storage.token,
            selectedRepoFullName = storage.selectedRepo,
            targetBranch = storage.targetBranch.ifBlank { "main" }
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var compilationPollingJob: Job? = null
    private var isCurrentlyCompiling: Boolean = false

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
                    authError = "Falha ao validar token: ${extractErrorMessage(e)}"
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

    fun createDedicatedBuildRepo(repoName: String = "git-compile-runner", onComplete: () -> Unit = {}) {
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
                onComplete()
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
        isCurrentlyCompiling = false
        storage.clear()
        apiClient = GitHubApiClient { "" }
        _uiState.value = UiState(currentScreen = AppScreen.AUTH)
    }

    fun navigateTo(screen: AppScreen) {
        _uiState.value = _uiState.value.copy(currentScreen = screen)
    }

    /**
     * Called immediately when user picks a ZIP file.
     * Automatically extracts, sanitizes, and triggers GitHub Actions compilation.
     * Enforces single compilation per request.
     */
    fun onZipSelectedAndStartCompilation(uri: Uri, fileName: String) {
        if (isCurrentlyCompiling) {
            return
        }

        val cleanFileName = if (fileName.isNotBlank()) fileName else "codigo-fonte.zip"

        viewModelScope.launch {
            // Transition directly to the compilation screen
            _uiState.value = _uiState.value.copy(
                selectedFileName = cleanFileName,
                currentScreen = AppScreen.COMPILATION_STEPS,
                isProcessingZip = true,
                zipError = null,
                buildStatus = BuildStatus.UPLOADING,
                step1Status = StepStatus.IN_PROGRESS,
                step1Detail = "Extraindo e validando arquivos do projeto...",
                step2Status = StepStatus.PENDING,
                step2Detail = "Aguardando preparação...",
                step3Status = StepStatus.PENDING,
                step3Detail = "Aguardando envio...",
                step4Status = StepStatus.PENDING,
                step4Detail = "Aguardando compilação...",
                activeRun = null,
                activeJobs = emptyList(),
                liveLogs = "",
                artifacts = emptyList(),
                downloadedApkFile = null,
                downloadErrorMessage = null,
                buildErrorMessage = null
            )

            notificationHelper.updateProgress(cleanFileName, 1, "Preparação do projeto")

            // Process Zip
            val scanResult = try {
                withContext(Dispatchers.IO) {
                    ZipProcessor.processZipUri(getApplication(), uri, cleanFileName)
                }
            } catch (e: Exception) {
                val err = "Erro ao processar arquivo ZIP: ${e.localizedMessage ?: "Arquivo inválido"}"
                _uiState.value = _uiState.value.copy(
                    isProcessingZip = false,
                    buildStatus = BuildStatus.FAILURE,
                    step1Status = StepStatus.FAILURE,
                    step1Detail = err,
                    buildErrorMessage = err
                )
                notificationHelper.notifyFailure(cleanFileName, "Falha ao preparar o projeto")
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                zipScan = scanResult,
                isProcessingZip = false,
                step1Status = StepStatus.SUCCESS,
                step1Detail = "Projeto preparado (${scanResult.files.size} arquivos)."
            )

            // Make sure repository is selected or create one automatically
            var repoFullName = _uiState.value.selectedRepoFullName
            if (repoFullName.isBlank()) {
                if (_uiState.value.userRepos.isNotEmpty()) {
                    repoFullName = _uiState.value.userRepos.first().fullName
                    storage.selectedRepo = repoFullName
                    _uiState.value = _uiState.value.copy(selectedRepoFullName = repoFullName)
                } else {
                    // Create dedicated repository automatically
                    try {
                        val newRepo = withContext(Dispatchers.IO) {
                            apiClient.api.createRepo(
                                CreateRepoRequest(
                                    name = "git-compile-runner",
                                    description = "Repositório criado pelo GitCompile para compilação",
                                    private = true,
                                    autoInit = true
                                )
                            )
                        }
                        repoFullName = newRepo.fullName
                        storage.selectedRepo = repoFullName
                        storage.targetBranch = newRepo.defaultBranch.ifBlank { "main" }
                        _uiState.value = _uiState.value.copy(
                            selectedRepoFullName = repoFullName,
                            targetBranch = newRepo.defaultBranch.ifBlank { "main" }
                        )
                    } catch (e: Exception) {
                        val err = "Falha ao configurar repositório no GitHub: ${extractErrorMessage(e)}"
                        _uiState.value = _uiState.value.copy(
                            buildStatus = BuildStatus.FAILURE,
                            step2Status = StepStatus.FAILURE,
                            step2Detail = err,
                            buildErrorMessage = err
                        )
                        notificationHelper.notifyFailure(cleanFileName, "Erro no repositório")
                        return@launch
                    }
                }
            }

            // Execute real compilation
            executeCompilation(scanResult, repoFullName, _uiState.value.targetBranch)
        }
    }

    private fun executeCompilation(scan: ZipScanResult, repoFullName: String, branch: String) {
        val parts = repoFullName.split("/")
        if (parts.size != 2) {
            val err = "Nome de repositório inválido: $repoFullName"
            _uiState.value = _uiState.value.copy(
                buildStatus = BuildStatus.FAILURE,
                step2Status = StepStatus.FAILURE,
                step2Detail = err,
                buildErrorMessage = err
            )
            return
        }

        val owner = parts[0].trim()
        val repo = parts[1].trim()
        val effectiveBranch = branch.trim().ifBlank { "main" }
        val fileName = scan.fileName

        compilationPollingJob?.cancel()
        isCurrentlyCompiling = true

        _uiState.value = _uiState.value.copy(
            buildStatus = BuildStatus.UPLOADING,
            step2Status = StepStatus.IN_PROGRESS,
            step2Detail = "Conectando ao GitHub ($repoFullName)...",
            uploadProgress = 0.05f
        )
        notificationHelper.updateProgress(fileName, 2, "Envio do código-fonte")

        compilationPollingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                appendLog("Conectando ao repositório GitHub ($repoFullName)...")
                val repoDetails = apiClient.api.getRepo(owner, repo)
                val defaultBranch = repoDetails.defaultBranch.ifBlank { "main" }
                val targetBranch = if (effectiveBranch.isBlank()) defaultBranch else effectiveBranch

                // Step 2.1: Check parent commit
                var parentCommitSha: String? = null
                var branchExists = false
                try {
                    val branchRefResp = apiClient.api.getBranchRef(owner, repo, targetBranch)
                    if (branchRefResp.isSuccessful && branchRefResp.body()?.obj != null) {
                        branchExists = true
                        parentCommitSha = branchRefResp.body()?.obj?.sha
                    }
                } catch (_: Exception) {
                    branchExists = false
                }

                if (!branchExists && targetBranch != defaultBranch) {
                    try {
                        val defRefResp = apiClient.api.getBranchRef(owner, repo, defaultBranch)
                        if (defRefResp.isSuccessful && defRefResp.body()?.obj != null) {
                            parentCommitSha = defRefResp.body()?.obj?.sha
                        }
                    } catch (_: Exception) { }
                }

                // Step 2.2: Upload blobs
                val totalFiles = scan.files.size
                appendLog("Enviando $totalFiles arquivos para o GitHub...")
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

                    val pct = ((index + 1) * 100 / totalFiles)
                    val progressFloat = 0.05f + (0.75f * (index + 1) / totalFiles)
                    _uiState.value = _uiState.value.copy(
                        uploadProgress = progressFloat,
                        step2Detail = "Enviando arquivos ($pct%): ${index + 1} de $totalFiles"
                    )

                    if (index % 15 == 0 || index == totalFiles - 1) {
                        notificationHelper.updateProgress(fileName, 2, "Envio do código-fonte ($pct%)")
                    }
                }

                // Step 2.3: Create Git Tree
                appendLog("Criando árvore de arquivos Git...")
                val treeResponse = apiClient.api.createTree(
                    owner,
                    repo,
                    GitTreeRequest(tree = treeItems)
                )

                // Step 2.4: Create Commit
                appendLog("Registrando commit de compilação...")
                val commitMsg = "Compilação de ${scan.fileName} via GitCompile [${SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).format(Date())}]"
                val commitResponse = apiClient.api.createCommit(
                    owner,
                    repo,
                    GitCommitRequest(
                        message = commitMsg,
                        tree = treeResponse.sha,
                        parents = if (parentCommitSha != null) listOf(parentCommitSha) else emptyList()
                    )
                )

                // Step 2.5: Update branch
                appendLog("Atualizando ramo '$targetBranch'...")
                var refUpdated = false
                if (branchExists) {
                    try {
                        apiClient.api.updateBranchRef(
                            owner,
                            repo,
                            targetBranch,
                            GitUpdateRefRequest(sha = commitResponse.sha, force = true)
                        )
                        refUpdated = true
                    } catch (_: Exception) { }
                }

                if (!refUpdated) {
                    try {
                        apiClient.api.createRef(
                            owner,
                            repo,
                            GitCreateRefRequest(ref = "refs/heads/$targetBranch", sha = commitResponse.sha)
                        )
                    } catch (_: Exception) {
                        apiClient.api.updateBranchRef(
                            owner,
                            repo,
                            targetBranch,
                            GitUpdateRefRequest(sha = commitResponse.sha, force = true)
                        )
                    }
                }

                // Step 2 Completed
                _uiState.value = _uiState.value.copy(
                    step2Status = StepStatus.SUCCESS,
                    step2Detail = "Código enviado com sucesso para o GitHub.",
                    uploadProgress = 1.0f,
                    buildStatus = BuildStatus.QUEUED,
                    step3Status = StepStatus.IN_PROGRESS,
                    step3Detail = "Aguardando executor virtual do GitHub Actions..."
                )
                notificationHelper.updateProgress(fileName, 3, "Compilação do APK no GitHub Actions")

                // Trigger workflow_dispatch if configured
                try {
                    apiClient.api.dispatchWorkflow(
                        owner,
                        repo,
                        "compile.yml",
                        WorkflowDispatchRequest(ref = targetBranch)
                    )
                } catch (_: Exception) { }

                // Step 3: Monitor GitHub Actions
                pollWorkflow(owner, repo, targetBranch, commitResponse.sha, fileName)

            } catch (e: Exception) {
                isCurrentlyCompiling = false
                val err = "Erro no envio do código: ${extractErrorMessage(e)}"
                appendLog("❌ $err")
                _uiState.value = _uiState.value.copy(
                    buildStatus = BuildStatus.FAILURE,
                    step2Status = StepStatus.FAILURE,
                    step2Detail = err,
                    buildErrorMessage = err
                )
                notificationHelper.notifyFailure(fileName, "Falha no envio do código")
            }
        }
    }

    private suspend fun pollWorkflow(owner: String, repo: String, branch: String, commitSha: String, fileName: String) {
        var foundRun: WorkflowRun? = null
        var attempts = 0
        val maxWaitAttempts = 30 // ~75 seconds
        var lastDispatch = 0

        while (foundRun == null && attempts < maxWaitAttempts && currentCoroutineContext().isActive) {
            attempts++
            delay(2500)
            try {
                val runsResp = apiClient.api.getWorkflowRuns(owner, repo, branch = null, perPage = 10)
                val matching = runsResp.workflowRuns.firstOrNull { it.headSha == commitSha }
                    ?: runsResp.workflowRuns.firstOrNull { it.headBranch == branch }
                    ?: runsResp.workflowRuns.firstOrNull()

                if (matching != null) {
                    foundRun = matching
                    _uiState.value = _uiState.value.copy(
                        activeRun = matching,
                        step3Detail = "GitHub Actions #${matching.runNumber}: em andamento"
                    )
                    notificationHelper.updateProgress(fileName, 3, "Compilação do APK no GitHub Actions")
                } else {
                    if (attempts >= 5 && attempts - lastDispatch >= 5) {
                        lastDispatch = attempts
                        try {
                            apiClient.api.dispatchWorkflow(owner, repo, "compile.yml", WorkflowDispatchRequest(ref = branch))
                        } catch (_: Exception) { }
                    }
                }
            } catch (e: Exception) {
                appendLog("Verificando status: ${extractErrorMessage(e)}")
            }
        }

        if (foundRun == null) {
            isCurrentlyCompiling = false
            val err = "O GitHub Actions não iniciou dentro do prazo esperado. Verifique se as Actions estão ativadas no repositório."
            appendLog("❌ $err")
            _uiState.value = _uiState.value.copy(
                buildStatus = BuildStatus.FAILURE,
                step3Status = StepStatus.FAILURE,
                step3Detail = "Tempo limite excedido no GitHub Actions.",
                buildErrorMessage = err
            )
            notificationHelper.notifyFailure(fileName, "Timeout no GitHub Actions")
            return
        }

        val runId = foundRun.id
        var isCompleted = false

        while (!isCompleted && currentCoroutineContext().isActive) {
            try {
                val currentRun = apiClient.api.getWorkflowRun(owner, repo, runId)
                val jobsResp = apiClient.api.getWorkflowRunJobs(owner, repo, runId)
                val jobs = jobsResp.jobs
                val primaryJob = jobs.firstOrNull()

                // Fetch latest job logs
                if (primaryJob != null) {
                    val logs = apiClient.fetchJobLogsText(owner, repo, primaryJob.id)
                    if (logs.isNotBlank() && logs.length > 20) {
                        _uiState.value = _uiState.value.copy(liveLogs = logs)
                    }
                }

                val currentStepName = primaryJob?.steps?.firstOrNull { it.status == "in_progress" }?.name
                    ?: "Compilando código com Gradle no Ubuntu Runner..."

                when (currentRun.status) {
                    "completed" -> {
                        isCompleted = true
                        isCurrentlyCompiling = false

                        if (currentRun.conclusion == "success") {
                            // Step 3 Success, Proceed to Step 4
                            _uiState.value = _uiState.value.copy(
                                activeRun = currentRun,
                                activeJobs = jobs,
                                step3Status = StepStatus.SUCCESS,
                                step3Detail = "APK compilado com sucesso!",
                                step4Status = StepStatus.IN_PROGRESS,
                                step4Detail = "Localizando arquivos e gerando link do APK..."
                            )
                            notificationHelper.updateProgress(fileName, 4, "Finalização do APK")

                            // Poll artifacts for up to 12 seconds
                            var artifactsList: List<ArtifactItem> = emptyList()
                            for (artAttempt in 1..6) {
                                try {
                                    val artResp = apiClient.api.getWorkflowArtifacts(owner, repo, runId)
                                    if (artResp.artifacts.isNotEmpty()) {
                                        artifactsList = artResp.artifacts
                                        break
                                    }
                                } catch (_: Exception) { }
                                delay(2000)
                            }

                            _uiState.value = _uiState.value.copy(
                                buildStatus = BuildStatus.SUCCESS,
                                step4Status = StepStatus.SUCCESS,
                                step4Detail = if (artifactsList.isNotEmpty()) "APK disponível para download!" else "Compilação concluída no GitHub Actions.",
                                artifacts = artifactsList
                            )
                            notificationHelper.notifySuccess(fileName, "APK")
                            appendLog("\n🎉 COMPILAÇÃO FINALIZADA COM SUCESSO!")
                        } else {
                            // Failure
                            val failReason = currentRun.conclusion ?: "falha"
                            _uiState.value = _uiState.value.copy(
                                buildStatus = BuildStatus.FAILURE,
                                activeRun = currentRun,
                                activeJobs = jobs,
                                step3Status = StepStatus.FAILURE,
                                step3Detail = "Compilação falhou no Gradle ($failReason).",
                                step4Status = StepStatus.PENDING,
                                step4Detail = "Cancelado devido a falha na compilação.",
                                buildErrorMessage = "A compilação do APK falhou no GitHub Actions. Verifique os logs de erro para mais detalhes."
                            )
                            notificationHelper.notifyFailure(fileName, "Falha na compilação")
                            appendLog("\n❌ COMPILAÇÃO FALHOU NO GITHUB ACTIONS.")
                        }
                    }
                    "in_progress" -> {
                        _uiState.value = _uiState.value.copy(
                            buildStatus = BuildStatus.IN_PROGRESS,
                            activeRun = currentRun,
                            activeJobs = jobs,
                            step3Status = StepStatus.IN_PROGRESS,
                            step3Detail = currentStepName
                        )
                        notificationHelper.updateProgress(fileName, 3, "Compilação do APK: $currentStepName")
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(
                            buildStatus = BuildStatus.QUEUED,
                            activeRun = currentRun,
                            activeJobs = jobs,
                            step3Status = StepStatus.IN_PROGRESS,
                            step3Detail = "Na fila de execução do GitHub Actions..."
                        )
                        notificationHelper.updateProgress(fileName, 3, "Aguardando fila do GitHub Actions")
                    }
                }

                if (isCompleted) break

            } catch (_: Exception) { }
            delay(2800)
        }
    }

    private fun appendLog(line: String) {
        val current = _uiState.value.liveLogs
        _uiState.value = _uiState.value.copy(liveLogs = if (current.isEmpty()) line else "$current\n$line")
    }

    /**
     * Downloads the compiled APK directly from the artifact.
     * If artifacts list is empty, attempts re-fetching or opens browser fallback.
     */
    fun downloadLatestApk(context: Context) {
        val state = _uiState.value
        val fileName = state.selectedFileName.ifBlank { "app.apk" }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloadingApk = true,
                downloadProgress = 0f,
                downloadErrorMessage = null
            )

            var artifactToDownload = state.artifacts.firstOrNull { it.name.contains("apk", ignoreCase = true) }
                ?: state.artifacts.firstOrNull()

            // If artifacts is empty, try re-querying once
            if (artifactToDownload == null && state.activeRun != null) {
                val parts = state.selectedRepoFullName.split("/")
                if (parts.size == 2) {
                    try {
                        val artResp = apiClient.api.getWorkflowArtifacts(parts[0], parts[1], state.activeRun.id)
                        if (artResp.artifacts.isNotEmpty()) {
                            _uiState.value = _uiState.value.copy(artifacts = artResp.artifacts)
                            artifactToDownload = artResp.artifacts.firstOrNull()
                        }
                    } catch (_: Exception) { }
                }
            }

            if (artifactToDownload != null) {
                val file = ArtifactDownloader.downloadAndExtractApk(
                    getApplication(),
                    apiClient,
                    artifactToDownload
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
                    // Auto open install dialog
                    ArtifactDownloader.installOrOpenApk(context, file)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isDownloadingApk = false,
                        downloadErrorMessage = "Falha ao baixar o arquivo. Abrindo página de download..."
                    )
                    openRunInBrowser(context)
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isDownloadingApk = false,
                    downloadErrorMessage = "Abrindo página do GitHub Actions para download..."
                )
                openRunInBrowser(context)
            }
        }
    }

    fun openRunInBrowser(context: Context) {
        val state = _uiState.value
        val url = state.activeRun?.htmlUrl?.ifBlank { null }
            ?: if (state.selectedRepoFullName.isNotBlank()) "https://github.com/${state.selectedRepoFullName}/actions" else "https://github.com"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    fun installDownloadedApk(context: Context) {
        val file = _uiState.value.downloadedApkFile ?: return
        ArtifactDownloader.installOrOpenApk(context, file)
    }

    fun cancelCompilation() {
        val state = _uiState.value
        val run = state.activeRun ?: return
        val parts = state.selectedRepoFullName.split("/")
        if (parts.size != 2) return

        notificationHelper.cancelNotification()
        isCurrentlyCompiling = false
        compilationPollingJob?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                apiClient.api.cancelWorkflowRun(parts[0], parts[1], run.id)
            } catch (_: Exception) { }
        }

        _uiState.value = _uiState.value.copy(
            buildStatus = BuildStatus.CANCELLED,
            step3Status = StepStatus.FAILURE,
            step3Detail = "Compilação cancelada pelo usuário.",
            step4Status = StepStatus.PENDING,
            step4Detail = "Processo interrompido."
        )
    }

    fun resetCompilation() {
        compilationPollingJob?.cancel()
        isCurrentlyCompiling = false
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
            downloadErrorMessage = null,
            step1Status = StepStatus.PENDING,
            step2Status = StepStatus.PENDING,
            step3Status = StepStatus.PENDING,
            step4Status = StepStatus.PENDING
        )
    }
}
