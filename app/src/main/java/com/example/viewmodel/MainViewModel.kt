package com.example.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.manager.AppOperationEngine
import com.example.manager.EngineResult
import com.example.manager.ShizukuManager
import com.example.receiver.PackageEventReceiver
import com.example.repository.AppInfo
import com.example.repository.AppRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

enum class OperationStatus {
    PENDING, IN_PROGRESS, SUCCESS, FAILED
}

enum class FilterStatusMode {
    ALL, DISABLED_ONLY, ENABLED_ONLY
}

data class UninstallReport(
    val appName: String,
    val packageName: String,
    val status: OperationStatus,
    val message: String?
)

sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val shizukuManager = ShizukuManager(application)
    private val operationEngine = AppOperationEngine(application, shizukuManager)
    private val repository = AppRepository(application, shizukuManager)

    // Shizuku Connection States
    private val _isShizukuInstalled = MutableStateFlow(false)
    val isShizukuInstalled: StateFlow<Boolean> = _isShizukuInstalled.asStateFlow()

    private val _isShizukuRunning = MutableStateFlow(false)
    val isShizukuRunning: StateFlow<Boolean> = _isShizukuRunning.asStateFlow()

    private val _hasShizukuPermission = MutableStateFlow(false)
    val hasShizukuPermission: StateFlow<Boolean> = _hasShizukuPermission.asStateFlow()

    private val _showShizukuRequiredDialog = MutableStateFlow(false)
    val showShizukuRequiredDialog: StateFlow<Boolean> = _showShizukuRequiredDialog.asStateFlow()

    fun triggerShizukuRequiredDialog() {
        _showShizukuRequiredDialog.value = true
    }

    fun dismissShizukuRequiredDialog() {
        _showShizukuRequiredDialog.value = false
    }

    fun isShizukuConnected(): Boolean {
        refreshShizukuStatus()
        return _isShizukuRunning.value && _hasShizukuPermission.value
    }

    // Apps Loading States
    private val _isAppsLoading = MutableStateFlow(true)
    val isAppsLoading: StateFlow<Boolean> = _isAppsLoading.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _uninstalledApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val uninstalledApps: StateFlow<List<AppInfo>> = _uninstalledApps.asStateFlow()

    // UI Filtering and Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterShowUserApps = MutableStateFlow(true)
    val filterShowUserApps: StateFlow<Boolean> = _filterShowUserApps.asStateFlow()

    private val _filterShowSystemApps = MutableStateFlow(true)
    val filterShowSystemApps: StateFlow<Boolean> = _filterShowSystemApps.asStateFlow()

    private val _filterStatusMode = MutableStateFlow(FilterStatusMode.ALL)
    val filterStatusMode: StateFlow<FilterStatusMode> = _filterStatusMode.asStateFlow()

    // Multiple selection
    private val _selectedPackageNames = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackageNames: StateFlow<Set<String>> = _selectedPackageNames.asStateFlow()

    // UI Action Notifications
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    // Uninstall / Restore Reports
    private val _uninstallReports = MutableStateFlow<List<UninstallReport>?>(null)
    val uninstallReports: StateFlow<List<UninstallReport>?> = _uninstallReports.asStateFlow()

    private val _operationIsUninstall = MutableStateFlow(true)
    val operationIsUninstall: StateFlow<Boolean> = _operationIsUninstall.asStateFlow()

    fun dismissUninstallReports() {
        _uninstallReports.value = null
    }

    // Shizuku permission grant listener
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == ShizukuManager.PERMISSION_REQUEST_CODE) {
            viewModelScope.launch(Dispatchers.Main) {
                refreshShizukuStatus()
                if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    emitUiEvent(UiEvent.ShowSnackbar("Permissão concedida pelo Shizuku!"))
                    loadInstalledApps(silent = true)
                } else {
                    emitUiEvent(UiEvent.ShowSnackbar("Permissão negada pelo Shizuku."))
                }
            }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        viewModelScope.launch(Dispatchers.Main) {
            refreshShizukuStatus()
            loadInstalledApps(silent = true)
        }
    }
    
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        viewModelScope.launch(Dispatchers.Main) {
            refreshShizukuStatus()
        }
    }

    private val dynamicPackageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainViewModel", "Broadcast dinâmico de pacote recebido: ${intent?.action}")
            loadInstalledApps(silent = true)
        }
    }

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            shizukuManager.addPermissionListener(shizukuPermissionListener)
        } catch (e: Throwable) {
            Log.e("MainViewModel", "Erro ao inicializar Shizuku listeners", e)
        }

        // Registrar dinamicamente para mudanças imediatas de pacote
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            application.registerReceiver(dynamicPackageReceiver, filter)
        } catch (e: Throwable) {
            Log.e("MainViewModel", "Erro ao registrar dynamicPackageReceiver", e)
        }

        // Monitorar eventos emitidos pelo PackageEventReceiver com debounce para absorver pacotes em lote
        viewModelScope.launch {
            PackageEventReceiver.packageEvents
                .debounce(300L)
                .collect {
                    loadInstalledApps(silent = true)
                }
        }

        refreshShizukuStatus()
        loadInstalledApps(silent = false)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            shizukuManager.removePermissionListener(shizukuPermissionListener)
        } catch (e: Throwable) {
            Log.e("MainViewModel", "Erro ao remover Shizuku listeners", e)
        }

        try {
            getApplication<Application>().unregisterReceiver(dynamicPackageReceiver)
        } catch (_: Throwable) {
            // Pode não ter sido registrado
        }
    }

    private var lastForegroundSyncTimestamp = 0L

    /**
     * Chamado quando o app retorna do segundo plano ou é reaberto (MainActivity onResume).
     * Reavalia conexões, serviços Shizuku e estado da lista sem causar piscamentos indesejados.
     */
    fun onActivityResumed() {
        val wasRunning = _isShizukuRunning.value
        val hadPerm = _hasShizukuPermission.value
        refreshShizukuStatus()
        val isNowRunning = _isShizukuRunning.value
        val hasNowPerm = _hasShizukuPermission.value

        val now = System.currentTimeMillis()
        val shizukuChanged = (wasRunning != isNowRunning) || (hadPerm != hasNowPerm)
        val isStale = (now - lastForegroundSyncTimestamp > 15_000L)

        // Se uma carga já estiver em andamento, não dispara uma nova para evitar concorrência redundante
        if (loadAppsJob?.isActive != true) {
            if (shizukuChanged || isStale || _installedApps.value.isEmpty()) {
                lastForegroundSyncTimestamp = now
                loadInstalledApps(silent = _installedApps.value.isNotEmpty())
            }
        }
    }

    /**
     * Atualiza os estados de integridade e conexão com o Shizuku Binder.
     */
    fun refreshShizukuStatus() {
        val installed = shizukuManager.isInstalled()
        val running = shizukuManager.isRunning()
        val permission = if (running) shizukuManager.hasPermission() else false

        _isShizukuInstalled.value = installed
        _isShizukuRunning.value = running
        _hasShizukuPermission.value = permission
    }

    /**
     * Solicita permissão ao Shizuku.
     */
    fun requestShizukuPermission() {
        if (!shizukuManager.isInstalled()) {
            emitUiEvent(UiEvent.ShowSnackbar("Shizuku não instalado. Baixe o Shizuku primeiro."))
            return
        }
        if (!shizukuManager.isRunning()) {
            emitUiEvent(UiEvent.ShowSnackbar("O Shizuku está desligado. Inicie o serviço no app Shizuku."))
            return
        }
        shizukuManager.requestPermission()
    }

    private var loadAppsJob: Job? = null
    @Volatile
    private var pendingReloadRequested: Boolean = false

    /**
     * Carrega a lista completa de aplicativos de forma assíncrona, robusta e estável.
     * Nunca cancela tarefas em execução para evitar 'StandaloneCoroutine was cancelled'
     * e garante recarga pendente caso novas solicitações cheguem durante a carga ativa.
     */
    fun loadInstalledApps(silent: Boolean = false) {
        if (!silent || _installedApps.value.isEmpty()) {
            _isAppsLoading.value = true
        }

        // Se já existe uma tarefa ativa carregando os aplicativos, não a cancelamos.
        // Apenas sinalizamos que há uma recarga pendente se a lista já possuía itens anteriormente.
        if (loadAppsJob?.isActive == true) {
            if (_installedApps.value.isNotEmpty()) {
                pendingReloadRequested = true
            }
            return
        }

        loadAppsJob = viewModelScope.launch(Dispatchers.Main) {
            try {
                do {
                    pendingReloadRequested = false
                    val (installed, uninstalled) = withContext(Dispatchers.IO) {
                        repository.getAllApps()
                    }
                    _installedApps.value = installed
                    _uninstalledApps.value = uninstalled

                    // Preserva a seleção existente removendo apenas pacotes que não existem mais
                    val allValidPackages = (installed.map { it.packageName } + uninstalled.map { it.packageName }).toSet()
                    _selectedPackageNames.update { current ->
                        current.filter { allValidPackages.contains(it) }.toSet()
                    }
                } while (pendingReloadRequested)
            } catch (e: CancellationException) {
                // Cancelamento normal pelo ciclo de vida do ViewModel; repassar a exceção
                throw e
            } catch (e: Exception) {
                Log.e("MainViewModel", "Erro ao carregar aplicativos", e)
                if (!silent) {
                    emitUiEvent(UiEvent.ShowSnackbar("Falha ao carregar apps: ${e.localizedMessage ?: "Erro desconhecido"}"))
                }
            } finally {
                _isAppsLoading.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterShowUserApps(show: Boolean) {
        _filterShowUserApps.value = show
    }

    fun setFilterShowSystemApps(show: Boolean) {
        _filterShowSystemApps.value = show
    }

    fun setFilterStatusMode(mode: FilterStatusMode) {
        _filterStatusMode.value = mode
    }

    fun removeAppFromTrash(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeAppFromTrash(packageName)
            _uninstalledApps.update { list -> list.filter { it.packageName != packageName } }
            _selectedPackageNames.update { it - packageName }
            withContext(Dispatchers.Main) {
                emitUiEvent(UiEvent.ShowSnackbar("Item removido da lixeira."))
            }
        }
    }

    fun clearAllTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllTrash()
            _uninstalledApps.value = emptyList()
            clearSelection()
            withContext(Dispatchers.Main) {
                emitUiEvent(UiEvent.ShowSnackbar("Lixeira limpa com sucesso."))
            }
        }
    }

    fun processImportedFile(context: Context, uri: Uri, isForRestore: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.use { it.readText() } ?: return@launch
                val importedPackages = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                
                withContext(Dispatchers.Main) {
                    val availablePackages = if (isForRestore) {
                        _uninstalledApps.value.map { it.packageName }.toSet()
                    } else {
                        _installedApps.value.filter { !it.isCritical }.map { it.packageName }.toSet()
                    }
                    val validPackagesToSelect = importedPackages.intersect(availablePackages)
                    
                    if (validPackagesToSelect.isNotEmpty()) {
                        _selectedPackageNames.update { it + validPackagesToSelect }
                        emitUiEvent(UiEvent.ShowSnackbar("${validPackagesToSelect.size} pacotes selecionados."))
                    } else {
                        val listName = if (isForRestore) "lixeira" else "instalados"
                        emitUiEvent(UiEvent.ShowSnackbar("Nenhum pacote válido encontrado para selecionar em '$listName'."))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    emitUiEvent(UiEvent.ShowSnackbar("Erro ao importar arquivo."))
                }
            }
        }
    }

    fun exportUninstalledApps(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val textToSave = _uninstalledApps.value.joinToString("\n") { app ->
                    "${app.appName} [${app.packageName}]"
                }
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.bufferedWriter().use { writer ->
                        writer.write(textToSave)
                    }
                }
                withContext(Dispatchers.Main) {
                    emitUiEvent(UiEvent.ShowSnackbar("Lista de desinstalados salva com sucesso!"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    emitUiEvent(UiEvent.ShowSnackbar("Erro ao salvar lista: ${e.localizedMessage}"))
                }
            }
        }
    }

    fun toggleAppSelection(packageName: String) {
        _selectedPackageNames.update { current ->
            if (current.contains(packageName)) current - packageName else current + packageName
        }
    }

    fun clearSelection() {
        _selectedPackageNames.value = emptySet()
    }

    private val isOperating = AtomicBoolean(false)

    /**
     * Centraliza a orquestração segura de operações em lote (desinstalação ou restauração),
     * eliminando código duplicado de relatórios, transição de status e sincronização.
     */
    private fun executeBatchOperation(
        isUninstall: Boolean,
        targetApps: List<AppInfo>,
        processApp: suspend (AppInfo) -> EngineResult,
        onSuccess: suspend (AppInfo, EngineResult) -> Unit
    ) {
        _operationIsUninstall.value = isUninstall

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val reports = targetApps.map { app ->
                    UninstallReport(app.appName, app.packageName, OperationStatus.PENDING, null)
                }.toMutableList()

                _uninstallReports.value = reports.toList()

                for (i in reports.indices) {
                    val currentReport = reports[i]
                    reports[i] = currentReport.copy(status = OperationStatus.IN_PROGRESS)
                    _uninstallReports.value = reports.toList()

                    val app = targetApps.find { it.packageName == currentReport.packageName } ?: continue
                    val engineResult = processApp(app)

                    if (engineResult.isSuccess) {
                        reports[i] = currentReport.copy(
                            status = OperationStatus.SUCCESS,
                            message = engineResult.message
                        )
                        onSuccess(app, engineResult)
                    } else {
                        reports[i] = currentReport.copy(
                            status = OperationStatus.FAILED,
                            message = engineResult.message ?: "Falha na operação"
                        )
                    }
                    _uninstallReports.value = reports.toList()
                }

                clearSelection()

                // Atualização assíncrona dos estados de fundo
                try {
                    val (refreshedInstalled, refreshedUninstalled) = repository.getAllApps()
                    _installedApps.value = refreshedInstalled
                    _uninstalledApps.value = refreshedUninstalled
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e("MainViewModel", "Erro na sincronização em segundo plano", e)
                }
            } finally {
                isOperating.set(false)
            }
        }
    }

    /**
     * Comando 1: Desinstalar aplicativo (individual ou em lote).
     * Utiliza automaticamente a melhor API disponível (Shizuku, PackageInstaller, ACTION_DELETE)
     * com fallback inteligente em múltiplos níveis.
     */
    fun uninstallSelectedApps() {
        val packagesToUninstall = _selectedPackageNames.value.toList()
        if (packagesToUninstall.isEmpty()) return

        if (!isOperating.compareAndSet(false, true)) {
            emitUiEvent(UiEvent.ShowSnackbar("Uma operação já está em andamento. Aguarde."))
            return
        }

        val currentInstalled = _installedApps.value
        val appsToUninstall = packagesToUninstall.mapNotNull { pkg ->
            currentInstalled.find { it.packageName == pkg }
        }

        val hasOnlySystemApps = appsToUninstall.isNotEmpty() && appsToUninstall.all { it.isSystemApp }

        // Se foram selecionados apenas apps do sistema e o Shizuku não está ativo,
        // exibe o diálogo para o usuário conectar o Shizuku (essencial para desinstalação de apps do sistema)
        if (hasOnlySystemApps && !isShizukuConnected()) {
            isOperating.set(false)
            triggerShizukuRequiredDialog()
            return
        }

        executeBatchOperation(
            isUninstall = true,
            targetApps = appsToUninstall,
            processApp = { app -> operationEngine.uninstallApp(app) },
            onSuccess = { app, engineResult ->
                repository.recordManualUninstall(app, "Desinstalado via ${engineResult.methodUsed}")

                val updatedApp = app.copy(
                    isEnabled = false,
                    isInstalled = false,
                    uninstalledTimestamp = System.currentTimeMillis(),
                    uninstalledReason = "Desinstalado via ${engineResult.methodUsed}",
                    isRestorableViaShizuku = app.isSystemApp
                )

                _installedApps.update { list -> list.filter { it.packageName != app.packageName } }
                _uninstalledApps.update { list -> 
                    (listOf(updatedApp) + list.filter { it.packageName != app.packageName })
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName.ifBlank { it.packageName } })
                }
            }
        )
    }

    /**
     * Comando 2: Restaurar aplicativo (individual ou em lote).
     * Utiliza automaticamente a melhor API disponível (Shizuku, Loja/Google Play).
     */
    fun restoreSelectedApps() {
        val packagesToRestore = _selectedPackageNames.value.toList()
        if (packagesToRestore.isEmpty()) return

        if (!isOperating.compareAndSet(false, true)) {
            emitUiEvent(UiEvent.ShowSnackbar("Uma operação já está em andamento. Aguarde."))
            return
        }

        val currentUninstalled = _uninstalledApps.value
        val appsToRestore = packagesToRestore.mapNotNull { pkg ->
            currentUninstalled.find { it.packageName == pkg }
        }

        executeBatchOperation(
            isUninstall = false,
            targetApps = appsToRestore,
            processApp = { app -> operationEngine.restoreApp(app) },
            onSuccess = { app, engineResult ->
                if (engineResult.message == null) {
                    repository.removeAppFromTrash(app.packageName)
                    val restoredApp = app.copy(isEnabled = true, isInstalled = true)
                    _uninstalledApps.update { list -> list.filter { it.packageName != app.packageName } }
                    _installedApps.update { list -> (list + restoredApp).sortedBy { it.appName.lowercase() } }
                }
            }
        )
    }

    private fun emitUiEvent(event: UiEvent) {
        viewModelScope.launch {
            _uiEvent.emit(event)
        }
    }
}
