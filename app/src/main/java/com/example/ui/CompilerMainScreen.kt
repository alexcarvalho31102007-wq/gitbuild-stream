package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.compiler.data.BuildRecordEntity
import com.example.compiler.viewmodel.CompilerUiEvent
import com.example.compiler.viewmodel.CompilerViewModel
import com.example.ui.components.BuildHistoryTab
import com.example.ui.components.BuildLogDialog
import com.example.ui.components.NewBuildTab
import com.example.ui.components.SettingsTab
import kotlinx.coroutines.flow.collectLatest

enum class CompilerNavTab {
    NEW_BUILD,
    HISTORY,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompilerMainScreen(viewModel: CompilerViewModel) {
    var currentTab by remember { mutableStateOf(CompilerNavTab.NEW_BUILD) }
    val snackbarHostState = remember { SnackbarHostState() }
    val activeBuilds by viewModel.activeBuilds.collectAsStateWithLifecycle()
    var selectedBuildForLogs by remember { mutableStateOf<BuildRecordEntity?>(null) }

    BackHandler(enabled = currentTab != CompilerNavTab.NEW_BUILD) {
        currentTab = CompilerNavTab.NEW_BUILD
    }

    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collectLatest { event ->
            when (event) {
                is CompilerUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is CompilerUiEvent.OpenLogViewer -> selectedBuildForLogs = event.build
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_build),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "BuildX • Release Compiler",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Compilações Reais via GitHub Actions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
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
                        Triple(CompilerNavTab.NEW_BUILD, R.drawable.ic_build, "Compilar"),
                        Triple(CompilerNavTab.HISTORY, R.drawable.ic_history, "Histórico"),
                        Triple(CompilerNavTab.SETTINGS, R.drawable.ic_settings, "Configurações")
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
                            BadgedBox(
                                badge = {
                                    if (tab == CompilerNavTab.HISTORY && activeBuilds.isNotEmpty()) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text("${activeBuilds.size}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = label,
                                    tint = contentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentTab) {
                CompilerNavTab.NEW_BUILD -> NewBuildTab(
                    viewModel = viewModel,
                    onNavigateToSettings = { currentTab = CompilerNavTab.SETTINGS },
                    onNavigateToHistory = { currentTab = CompilerNavTab.HISTORY }
                )
                CompilerNavTab.HISTORY -> BuildHistoryTab(
                    viewModel = viewModel,
                    onOpenLogs = { selectedBuildForLogs = it }
                )
                CompilerNavTab.SETTINGS -> SettingsTab(viewModel = viewModel)
            }
        }
    }

    // Modal de exibição de logs
    if (selectedBuildForLogs != null) {
        BuildLogDialog(
            build = selectedBuildForLogs!!,
            onDismiss = { selectedBuildForLogs = null }
        )
    }
}
