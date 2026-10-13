package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.AppListItem
import com.example.ui.components.EmptyState
import com.example.ui.components.FilterDialog
import com.example.ui.components.TopSearchBar
import com.example.ui.components.UninstallResultsScreen
import com.example.viewmodel.FilterStatusMode
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.UiEvent
import kotlinx.coroutines.flow.collectLatest

enum class BottomTab { APPS, SELECTED, UNINSTALLED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    var currentTab by remember { mutableStateOf(BottomTab.APPS) }
    val snackbarHostState = remember { SnackbarHostState() }
    val uninstallReports by viewModel.uninstallReports.collectAsStateWithLifecycle()
    val isUninstall by viewModel.operationIsUninstall.collectAsStateWithLifecycle()
    val showShizukuRequiredDialog by viewModel.showShizukuRequiredDialog.collectAsStateWithLifecycle()

    BackHandler(enabled = uninstallReports != null || currentTab != BottomTab.APPS) {
        if (uninstallReports != null) {
            viewModel.dismissUninstallReports()
        } else {
            currentTab = BottomTab.APPS
        }
    }

    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collectLatest { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    if (uninstallReports != null) {
        UninstallResultsScreen(
            reports = uninstallReports!!,
            isUninstall = isUninstall,
            onDismiss = { viewModel.dismissUninstallReports() }
        )
        return
    }

    if (showShizukuRequiredDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissShizukuRequiredDialog() },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Shizuku Desconectado",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "Para desinstalar ou restaurar aplicativos, é necessário ter o serviço Shizuku ativo e conectado no seu aparelho.\n\nPor favor, inicie o Shizuku e conceda permissão de acesso.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissShizukuRequiredDialog()
                        viewModel.requestShizukuPermission()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Autorizar no Shizuku")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissShizukuRequiredDialog() }
                ) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        Triple(BottomTab.APPS, R.drawable.ic_apps, "Aplicativos"),
                        Triple(BottomTab.SELECTED, R.drawable.ic_checklist, "Selecionados"),
                        Triple(BottomTab.UNINSTALLED, R.drawable.ic_trash, "Lixeira")
                    )
                    tabs.forEach { (tab, iconRes, label) ->
                        val isSelected = currentTab == tab
                        val contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        }
                        
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { currentTab = tab }
                                .testTag("nav_item_${tab.name.lowercase()}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = label,
                                tint = contentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentTab) {
                BottomTab.APPS -> AppListTab(viewModel)
                BottomTab.SELECTED -> SelectedAppsTab(viewModel)
                BottomTab.UNINSTALLED -> UninstalledAppsTab(viewModel)
            }
        }
    }
}

@Composable
fun AppListTab(viewModel: MainViewModel) {
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isAppsLoading by viewModel.isAppsLoading.collectAsStateWithLifecycle()
    val selectedPackages by viewModel.selectedPackageNames.collectAsStateWithLifecycle()
    val filterShowUser by viewModel.filterShowUserApps.collectAsStateWithLifecycle()
    val filterShowSystem by viewModel.filterShowSystemApps.collectAsStateWithLifecycle()
    val statusMode by viewModel.filterStatusMode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showFilterDialog by remember { mutableStateOf(false) }

    if (showFilterDialog) {
        BackHandler { showFilterDialog = false }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.processImportedFile(context, uri, isForRestore = false)
        }
    }
    
    val filteredApps = remember(
        installedApps, searchQuery, filterShowUser, filterShowSystem, statusMode
    ) {
        installedApps.filter { app ->
            // Busca textual
            val matchesSearch = searchQuery.isBlank() || 
                app.appName.contains(searchQuery, true) || 
                app.packageName.contains(searchQuery, true) ||
                app.originLabel.contains(searchQuery, true)
            if (!matchesSearch) return@filter false

            // Filtro por tipo de app
            val matchesType = when {
                filterShowUser && filterShowSystem -> true
                filterShowUser -> !app.isSystemApp
                filterShowSystem -> app.isSystemApp
                else -> false
            }
            if (!matchesType) return@filter false

            // Filtro por status (Ativo / Desativado) - Requisito 3
            when (statusMode) {
                FilterStatusMode.ALL -> true
                FilterStatusMode.DISABLED_ONLY -> !app.isEnabled
                FilterStatusMode.ENABLED_ONLY -> app.isEnabled
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val selectedPackagesCount = selectedPackages.size
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_apps),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${filteredApps.size} APLICATIVOS" + 
                        if (selectedPackagesCount > 0) " • $selectedPackagesCount SELECIONADOS" else "",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { importLauncher.launch(arrayOf("text/plain", "*/*")) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_upload),
                        contentDescription = "Importar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { showFilterDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_filter),
                        contentDescription = "Filtros Avançados",
                        tint = if (statusMode != FilterStatusMode.ALL || !filterShowUser || !filterShowSystem) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Se houver filtros ativos, exibir chip de remoção rápida
        if (statusMode != FilterStatusMode.ALL || !filterShowUser || !filterShowSystem) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (statusMode == FilterStatusMode.DISABLED_ONLY) {
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.setFilterStatusMode(FilterStatusMode.ALL) },
                        label = { Text("Apenas Desativados", fontSize = 11.sp) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remover filtro", modifier = Modifier.size(12.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                } else if (statusMode == FilterStatusMode.ENABLED_ONLY) {
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.setFilterStatusMode(FilterStatusMode.ALL) },
                        label = { Text("Apenas Ativos", fontSize = 11.sp) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remover filtro", modifier = Modifier.size(12.dp))
                        }
                    )
                }
                if (!filterShowUser || !filterShowSystem) {
                    FilterChip(
                        selected = true,
                        onClick = {
                            viewModel.setFilterShowUserApps(true)
                            viewModel.setFilterShowSystemApps(true)
                        },
                        label = { 
                            Text(
                                if (!filterShowUser) "Apenas Sistema" else "Apenas Usuário", 
                                fontSize = 11.sp
                            ) 
                        },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remover filtro", modifier = Modifier.size(12.dp))
                        }
                    )
                }
            }
        }

        TopSearchBar(searchQuery, onSearchChange = { viewModel.setSearchQuery(it) })

        Spacer(modifier = Modifier.height(2.dp))

        if (isAppsLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (filteredApps.isEmpty()) {
            EmptyState(
                message = "Nenhum aplicativo corresponde aos filtros",
                iconRes = R.drawable.ic_search_off
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(), 
                contentPadding = PaddingValues(top = 2.dp, bottom = 12.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppListItem(app, viewModel, isTrashItem = false)
                }
            }
        }
    }

    if (showFilterDialog) {
        FilterDialog(
            viewModel = viewModel,
            onDismiss = { showFilterDialog = false }
        )
    }
}

@Composable
fun SelectedAppsTab(viewModel: MainViewModel) {
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val selectedPackages by viewModel.selectedPackageNames.collectAsStateWithLifecycle()
    var selectedSearchQuery by remember { mutableStateOf("") }
    
    val selectedApps = remember(installedApps, selectedPackages, selectedSearchQuery) {
        installedApps.filter { selectedPackages.contains(it.packageName) &&
            (it.appName.contains(selectedSearchQuery, true) || it.packageName.contains(selectedSearchQuery, true))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_checklist),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${selectedApps.size} SELECIONADOS",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selectedApps.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearSelection() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Limpar Seleção", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                }
            }
        }

        TopSearchBar(selectedSearchQuery, onSearchChange = { selectedSearchQuery = it })

        if (selectedApps.isEmpty()) {
            EmptyState(
                message = "Nenhum aplicativo selecionado",
                iconRes = R.drawable.ic_checklist
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f), 
                contentPadding = PaddingValues(top = 2.dp, bottom = 12.dp)
            ) {
                items(selectedApps, key = { it.packageName }) { app ->
                    AppListItem(app, viewModel, isTrashItem = false)
                }
            }

            var showUninstallConfirmationQuery by remember { mutableStateOf(false) }

            Button(
                onClick = { 
                    showUninstallConfirmationQuery = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Text("Desinstalar Selecionados", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            if (showUninstallConfirmationQuery) {
                AlertDialog(
                    onDismissRequest = { showUninstallConfirmationQuery = false },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete_sweep),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Confirmar Desinstalação", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Text(
                            text = "Deseja realmente desinstalar os ${selectedApps.size} aplicativos selecionados via Shizuku?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.uninstallSelectedApps()
                                showUninstallConfirmationQuery = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Desinstalar Lote", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUninstallConfirmationQuery = false }) {
                            Text("Cancelar", fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun UninstalledAppsTab(viewModel: MainViewModel) {
    val uninstalledApps by viewModel.uninstalledApps.collectAsStateWithLifecycle()
    val selectedPackages by viewModel.selectedPackageNames.collectAsStateWithLifecycle()
    var selectedSearchQuery by remember { mutableStateOf("") }
    
    val filteredUninstalled = remember(uninstalledApps, selectedPackages, selectedSearchQuery) {
        uninstalledApps
            .filter { 
                it.appName.contains(selectedSearchQuery, true) || it.packageName.contains(selectedSearchQuery, true)
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName.ifBlank { it.packageName } })
    }

    val anySelected = selectedPackages.isNotEmpty()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.exportUninstalledApps(context, uri)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_trash),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${uninstalledApps.size} NA LIXEIRA",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uninstalledApps.isNotEmpty()) {
                    TextButton(
                        onClick = { 
                            if (anySelected) {
                                viewModel.clearSelection() 
                            } else {
                                uninstalledApps.forEach { app ->
                                    viewModel.toggleAppSelection(app.packageName)
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = if (anySelected) "Desmarcar" else "Selecionar Tudo", 
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        )
                    }

                    IconButton(
                        onClick = { exportLauncher.launch("desinstalados.txt") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = "Exportar Desinstalados",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        TopSearchBar(selectedSearchQuery, onSearchChange = { selectedSearchQuery = it })

        if (uninstalledApps.isEmpty()) {
            EmptyState(
                message = "Sua lixeira está vazia",
                iconRes = R.drawable.ic_trash
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f), 
                contentPadding = PaddingValues(top = 2.dp, bottom = 12.dp)
            ) {
                items(filteredUninstalled, key = { it.packageName }) { app ->
                    AppListItem(app, viewModel, isTrashItem = true)
                }
            }

            if (anySelected) {
                Button(
                    onClick = { viewModel.restoreSelectedApps() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_restore),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text("Restaurar Selecionados via Shizuku", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, fontSize = 15.sp)
                }
            }
        }
    }
}
