package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WorkflowStep
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.BuildStatus
import com.example.ui.viewmodel.CompilerViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompilationLiveScreen(
    viewModel: CompilerViewModel,
    uiState: UiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var autoScroll by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchField by remember { mutableStateOf(false) }

    val isRunning = uiState.buildStatus == BuildStatus.UPLOADING ||
            uiState.buildStatus == BuildStatus.QUEUED ||
            uiState.buildStatus == BuildStatus.IN_PROGRESS

    BackHandler {
        if (!isRunning) {
            viewModel.resetCompilation()
        } else {
            Toast.makeText(context, "A compilação ainda está em andamento no GitHub Actions.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Logs de Compilação",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = uiState.selectedRepoFullName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!isRunning) {
                                viewModel.resetCompilation()
                            } else {
                                viewModel.navigateTo(AppScreen.SELECT_ZIP)
                            }
                        },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchField = !showSearchField }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar nos Logs")
                    }
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Logs de Compilação", uiState.liveLogs)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Logs copiados para a área de transferência!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("copy_logs_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar Logs")
                    }
                    if (uiState.selectedRepoFullName.isNotBlank()) {
                        val actionsUrl = uiState.activeRun?.htmlUrl?.ifBlank { null }
                            ?: "https://github.com/${uiState.selectedRepoFullName}/actions"
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(actionsUrl))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Abrir no GitHub Actions")
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
                tonalElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRunning) {
                        OutlinedButton(
                            onClick = { viewModel.cancelCompilation() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancelar Execução", fontSize = 13.sp)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.resetCompilation() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("new_compilation_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Nova Compilação", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    FilledTonalIconButton(
                        onClick = { autoScroll = !autoScroll },
                        shape = RoundedCornerShape(10.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (autoScroll) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (autoScroll) Icons.Default.VerticalAlignBottom else Icons.Default.Pause,
                            contentDescription = "Auto-Scroll",
                            tint = if (autoScroll) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live Status Banner
            StatusBanner(
                status = uiState.buildStatus,
                uploadProgress = uiState.uploadProgress,
                uploadStageText = uiState.uploadStageText,
                activeRun = uiState.activeRun
            )

            // Step Progress Timeline
            val primaryJob = uiState.activeJobs.firstOrNull()
            if (primaryJob != null && primaryJob.steps.isNotEmpty()) {
                JobStepsHorizontalList(steps = primaryJob.steps)
            }

            // Search in logs bar
            AnimatedVisibility(visible = showSearchField) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filtrar logs...", fontSize = 13.sp) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // Artifacts Section (if available)
            if (uiState.artifacts.isNotEmpty()) {
                ArtifactsCard(
                    artifacts = uiState.artifacts,
                    onOpenUrl = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                )
            }

            // Terminal Logs Viewer
            TerminalLogsView(
                logs = uiState.liveLogs,
                autoScroll = autoScroll,
                searchQuery = searchQuery,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun StatusBanner(
    status: BuildStatus,
    uploadProgress: Float,
    uploadStageText: String,
    activeRun: com.example.data.model.WorkflowRun?
) {
    val (bgColor, textColor, title, icon) = when (status) {
        BuildStatus.UPLOADING -> Quadruple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Enviando Código-Fonte para o GitHub...",
            Icons.Default.CloudUpload
        )
        BuildStatus.QUEUED -> Quadruple(
            Color(0xFF3B2E10),
            GitHubYellow,
            "Aguardando Runner do GitHub Actions...",
            Icons.Default.HourglassTop
        )
        BuildStatus.IN_PROGRESS -> Quadruple(
            Color(0xFF0C2D6B),
            GitHubBlueAccent,
            "Compilação em Andamento no GitHub Actions",
            Icons.Default.Sync
        )
        BuildStatus.SUCCESS -> Quadruple(
            Color(0xFF0F3818),
            GitHubGreenAccent,
            "Compilação Concluída com Sucesso!",
            Icons.Default.CheckCircle
        )
        BuildStatus.FAILURE -> Quadruple(
            Color(0xFF3E1416),
            GitHubRedAccent,
            "Falha na Compilação",
            Icons.Default.Cancel
        )
        BuildStatus.CANCELLED -> Quadruple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Compilação Cancelada",
            Icons.Default.Block
        )
        BuildStatus.IDLE -> Quadruple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Aguardando Início",
            Icons.Default.Schedule
        )
    }

    Surface(
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textColor
                    )
                    if (activeRun != null) {
                        Text(
                            text = "Execução #${activeRun.runNumber} • Branch: ${activeRun.headBranch} • SHA: ${activeRun.headSha.take(7)}",
                            fontSize = 11.sp,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            if (status == BuildStatus.UPLOADING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = GitHubBlueAccent,
                    trackColor = textColor.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uploadStageText,
                    fontSize = 11.sp,
                    color = textColor.copy(alpha = 0.9f)
                )
            }
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun JobStepsHorizontalList(steps: List<WorkflowStep>) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEach { step ->
                val (stepIcon, stepColor) = when {
                    step.status == "completed" && step.conclusion == "success" ->
                        Pair(Icons.Default.CheckCircle, GitHubGreenAccent)
                    step.status == "completed" && step.conclusion == "failure" ->
                        Pair(Icons.Default.Cancel, GitHubRedAccent)
                    step.status == "completed" && step.conclusion == "skipped" ->
                        Pair(Icons.Default.RemoveCircleOutline, Color.Gray)
                    step.status == "in_progress" ->
                        Pair(Icons.Default.PlayCircle, GitHubBlueAccent)
                    else ->
                        Pair(Icons.Outlined.Circle, Color.Gray)
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, stepColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = stepIcon,
                            contentDescription = null,
                            tint = stepColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = step.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtifactsCard(
    artifacts: List<com.example.data.model.ArtifactItem>,
    onOpenUrl: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Artefatos Compilados (${artifacts.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            artifacts.forEach { artifact ->
                val sizeFormatted = DecimalFormat("#.##").format(artifact.sizeInBytes.toDouble() / (1024 * 1024))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artifact.name,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "$sizeFormatted MB",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { onOpenUrl(artifact.archiveDownloadUrl) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Baixar", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalLogsView(
    logs: String,
    autoScroll: Boolean,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val logLines = remember(logs) {
        logs.lines()
    }

    val filteredLines = remember(logLines, searchQuery) {
        if (searchQuery.isBlank()) {
            logLines
        } else {
            logLines.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    LaunchedEffect(filteredLines.size, autoScroll) {
        if (autoScroll && filteredLines.isNotEmpty()) {
            listState.animateScrollToItem(filteredLines.size - 1)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(GitHubDarkBg)
            .border(1.dp, GitHubBorder, RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        if (filteredLines.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Aguardando logs da compilação...",
                    color = GitHubTextSecondary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredLines) { line ->
                    LogLineText(line = line)
                }
            }
        }
    }
}

@Composable
fun LogLineText(line: String) {
    val lower = line.lowercase()
    val textColor = when {
        lower.contains("error") || lower.contains("fail") || lower.contains("fatal") || lower.contains("exception") ->
            GitHubRedAccent
        lower.contains("success") || lower.contains("successful") || lower.contains("concluído") || lower.contains("done") ->
            GitHubGreenAccent
        lower.contains("warning") || lower.contains("warn") ->
            GitHubYellow
        lower.startsWith(">>>") || lower.startsWith("===") || lower.contains("compiling") || lower.contains("building") ->
            GitHubBlueAccent
        else ->
            GitHubTextPrimary
    }

    Text(
        text = line,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.5.sp,
        color = textColor,
        lineHeight = 16.sp
    )
}
