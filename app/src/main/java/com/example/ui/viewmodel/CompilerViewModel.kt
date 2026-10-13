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
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    AUTH,
    SELECT_ZIP,
    COMPILATION_LIVE
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
    val targetBranch: String = "cloud-compile",
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
    val buildErrorMessage: String? = null
)

class CompilerViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = TokenStorage(application)
    private var apiClient: GitHubApiClient = GitHubApiClient { storage.token }

    private val _uiState = MutableStateFlow(
        UiState(
            token = storage.token,
            selectedRepoFullName = storage.selectedRepo,
            targetBranch = storage.targetBranch
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var compilationPollingJob: Job? = null

    init {
        if (storage.token.isNotBlank()) {
            validateAndLoadUser(storage.token)
        }
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
                    authError = "Falha ao autenticar com o GitHub: ${e.localizedMessage ?: "Verifique seu token e permissões (repo, workflow)."}"
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
                if (currentSelected.isBlank() && repos.isNotEmpty()) {
                    currentSelected = repos.first().fullName
                    storage.selectedRepo = currentSelected
                }
                _uiState.value = _uiState.value.copy(
                    userRepos = repos,
                    selectedRepoFullName = currentSelected,
                    isLoadingRepos = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingRepos = false)
            }
        }
    }

    fun onSelectRepo(repoFullName: String) {
        storage.selectedRepo = repoFullName
        _uiState.value = _uiState.value.copy(selectedRepoFullName = repoFullName)
    }

    fun onBranchChanged(branch: String) {
        val cleanBranch = branch.trim().ifBlank { "cloud-compile" }
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
                loadUserRepos()
                _uiState.value = _uiState.value.copy(
                    selectedRepoFullName = newRepo.fullName,
                    isCreatingRepo = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreatingRepo = false,
                    authError = "Não foi possível criar o repositório: ${e.localizedMessage}"
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
        val owner = parts[0]
        val repo = parts[1]
        val branch = state.targetBranch.ifBlank { "cloud-compile" }

        compilationPollingJob?.cancel()
        _uiState.value = _uiState.value.copy(
            currentScreen = AppScreen.COMPILATION_LIVE,
            buildStatus = BuildStatus.UPLOADING,
            uploadProgress = 0.05f,
            uploadStageText = "Preparando conexão com repositório $repoFullName...",
            activeRun = null,
            activeJobs = emptyList(),
            liveLogs = "Iniciando processo de compilação em nuvem...\n",
            artifacts = emptyList(),
            buildErrorMessage = null
        )

        compilationPollingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                appendLog("Conectando ao GitHub ($repoFullName)...")
                appendLog("Ramo de compilação: $branch")

                // Step 1: Check or create target branch
                var parentCommitSha: String? = null
                try {
                    val branchRefResp = apiClient.api.getBranchRef(owner, repo, branch)
                    if (branchRefResp.isSuccessful && branchRefResp.body()?.obj != null) {
                        parentCommitSha = branchRefResp.body()?.obj?.sha
                        appendLog("Ramo '$branch' localizado (Commit: ${parentCommitSha?.take(7)}).")
                    } else {
                        // Check default branch to branch off
                        val repoDetails = apiClient.api.getRepo(owner, repo)
                        val defaultBranch = repoDetails.defaultBranch
                        val defaultRef = apiClient.api.getBranchRef(owner, repo, defaultBranch)
                        val defaultSha = defaultRef.body()?.obj?.sha
                        if (defaultSha != null) {
                            apiClient.api.createRef(
                                owner,
                                repo,
                                GitCreateRefRequest(ref = "refs/heads/$branch", sha = defaultSha)
                            )
                            parentCommitSha = defaultSha
                            appendLog("Novo ramo '$branch' criado a partir de '$defaultBranch'.")
                        }
                    }
                } catch (e: Exception) {
                    appendLog("Aviso: Configurando árvore Git diretamente...")
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
                    // For small text files under 60KB, include content directly
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
                        // Create Blob via GitHub API
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

                // Step 5: Update Branch Ref
                appendLog("Atualizando ramo '$branch'...")
                try {
                    apiClient.api.updateBranchRef(
                        owner,
                        repo,
                        branch,
                        GitUpdateRefRequest(sha = commitResponse.sha, force = true)
                    )
                } catch (e: Exception) {
                    apiClient.api.createRef(
                        owner,
                        repo,
                        GitCreateRefRequest(ref = "refs/heads/$branch", sha = commitResponse.sha)
                    )
                }

                _uiState.value = _uiState.value.copy(
                    uploadProgress = 1.0f,
                    uploadStageText = "Código enviado! Aguardando o GitHub Actions iniciar os servidores de compilação...",
                    buildStatus = BuildStatus.QUEUED
                )
                appendLog("\n========================================================")
                appendLog("✓ Código-fonte enviado com sucesso para o GitHub!")
                appendLog("Aguardando GitHub Actions provisionar os runners...")
                appendLog("========================================================\n")

                // Step 6: Poll GitHub Actions Workflow Runs
                pollWorkflowRun(owner, repo, branch, commitResponse.sha)

            } catch (e: Exception) {
                val errorMsg = "Erro durante o envio: ${e.localizedMessage ?: e.javaClass.simpleName}"
                appendLog("\n❌ $errorMsg")
                _uiState.value = _uiState.value.copy(
                    buildStatus = BuildStatus.FAILURE,
                    buildErrorMessage = errorMsg
                )
            }
        }
    }

    private suspend fun pollWorkflowRun(owner: String, repo: String, branch: String, commitSha: String) {
        var foundRun: WorkflowRun? = null
        var attempts = 0
        val maxFindAttempts = 24 // up to ~60s to detect run start

        while (foundRun == null && attempts < maxFindAttempts && currentCoroutineContext().isActive) {
            attempts++
            delay(2500)
            try {
                val runsResp = apiClient.api.getWorkflowRuns(owner, repo, branch, perPage = 5)
                val matchingRun = runsResp.workflowRuns.firstOrNull { it.headSha == commitSha }
                    ?: runsResp.workflowRuns.firstOrNull() // fallback to latest run on branch
                if (matchingRun != null) {
                    foundRun = matchingRun
                    appendLog("GitHub Actions detectado: Execução #${matchingRun.runNumber} (${matchingRun.name})")
                } else {
                    appendLog("Aguardando GitHub Actions enfileirar o build (tentativa $attempts)...")
                }
            } catch (e: Exception) {
                // Keep trying
            }
        }

        if (foundRun == null) {
            appendLog("Aviso: O GitHub Actions pode demorar alguns instantes adicionais para enfileirar.")
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

                if (isCompleted) {
                    // Fetch artifacts if any were produced
                    try {
                        val artifactsResp = apiClient.api.getWorkflowArtifacts(owner, repo, runId)
                        _uiState.value = _uiState.value.copy(artifacts = artifactsResp.artifacts)
                        if (artifactsResp.artifacts.isNotEmpty()) {
                            appendLog("\n📦 Artefatos compilados disponíveis para download: ${artifactsResp.artifacts.size} arquivo(s)")
                        }
                    } catch (e: Exception) {
                        // ignore
                    }

                    if (statusEnum == BuildStatus.SUCCESS) {
                        appendLog("\n🎉 COMPILAÇÃO FINALIZADA COM SUCESSO NO GITHUB ACTIONS!")
                    } else if (statusEnum == BuildStatus.FAILURE) {
                        appendLog("\n❌ COMPILAÇÃO FALHOU NO GITHUB ACTIONS. Verifique os erros acima.")
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

    fun cancelCompilation() {
        val state = _uiState.value
        val run = state.activeRun ?: return
        val parts = state.selectedRepoFullName.split("/")
        if (parts.size != 2) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                apiClient.api.cancelWorkflowRun(parts[0], parts[1], run.id)
                appendLog("Cancelamento solicitado para a execução #${run.runNumber}...")
            } catch (e: Exception) {
                appendLog("Não foi possível cancelar: ${e.localizedMessage}")
            }
        }
    }

    fun resetCompilation() {
        compilationPollingJob?.cancel()
        _uiState.value = _uiState.value.copy(
            currentScreen = AppScreen.SELECT_ZIP,
            buildStatus = BuildStatus.IDLE,
            activeRun = null,
            activeJobs = emptyList(),
            liveLogs = "",
            artifacts = emptyList(),
            buildErrorMessage = null
        )
    }
}
