package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ArtifactItem
import com.example.data.model.WorkflowJob
import com.example.data.model.WorkflowRun
import com.example.data.model.WorkflowStep
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.BuildStatus
import com.example.ui.viewmodel.CompilerViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.delay
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompilationStepsScreen(
    viewModel: CompilerViewModel,
    uiState: UiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCancelDialog by remember { mutableStateOf(false) }

    // Request notification permission on Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { /* granted */ }
        LaunchedEffect(Unit) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val isRunning = uiState.buildStatus == BuildStatus.UPLOADING ||
            uiState.buildStatus == BuildStatus.QUEUED ||
            uiState.buildStatus == BuildStatus.IN_PROGRESS

    // Elapsed timer calculation
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isRunning) {
        if (isRunning) {
            elapsedSeconds = 0L
            while (true) {
                delay(1000L)
                elapsedSeconds++
            }
        }
    }

    val formattedTime = remember(elapsedSeconds) {
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        String.format("%02d:%02d", minutes, seconds)
    }

    BackHandler {
        if (isRunning) {
            Toast.makeText(context, "A compilação continua executando em nuvem no GitHub Actions.", Toast.LENGTH_SHORT).show()
            viewModel.navigateTo(AppScreen.SELECT_ZIP)
        } else {
            viewModel.resetCompilation()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Compilação em Etapas",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = uiState.selectedRepoFullName.ifBlank { "GitHub Actions" },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isRunning) {
                                viewModel.navigateTo(AppScreen.SELECT_ZIP)
                            } else {
                                viewModel.resetCompilation()
                            }
                        },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    // Quick Logs Button
                    FilledTonalButton(
                        onClick = { viewModel.navigateTo(AppScreen.COMPILATION_LOGS) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("open_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Ver Logs", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (uiState.selectedRepoFullName.isNotBlank()) {
                        val actionsUrl = uiState.activeRun?.htmlUrl?.ifBlank { null }
                            ?: "https://github.com/${uiState.selectedRepoFullName}/actions"
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(actionsUrl))
                                context.startActivity(intent)
                            }
                        ) {
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
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRunning) {
                        OutlinedButton(
                            onClick = { showCancelDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("cancel_execution_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cancelar Execução", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = { viewModel.navigateTo(AppScreen.COMPILATION_LOGS) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("inspect_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Logs em Tempo Real",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.navigateTo(AppScreen.COMPILATION_LOGS) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ver Logs do Build", fontSize = 13.sp)
                        }

                        Button(
                            onClick = { viewModel.resetCompilation() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("new_compilation_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Nova Compilação", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Clean Status Hero Card with file name and step
                CleanStatusHeroCard(
                    fileName = uiState.zipScan?.fileName ?: "Projeto.zip",
                    status = uiState.buildStatus,
                    uploadProgress = uiState.uploadProgress,
                    uploadStageText = uiState.uploadStageText,
                    activeRun = uiState.activeRun,
                    formattedTime = formattedTime
                )
            }

            // Download Card if Success and Artifacts exist
            if (uiState.artifacts.isNotEmpty()) {
                item {
                    CleanArtifactDownloadCard(
                        artifacts = uiState.artifacts,
                        isDownloading = uiState.isDownloadingApk,
                        downloadProgress = uiState.downloadProgress,
                        downloadedFile = uiState.downloadedApkFile,
                        downloadError = uiState.downloadErrorMessage,
                        onDownload = { artifact -> viewModel.downloadArtifact(artifact) },
                        onInstall = { viewModel.installDownloadedApk(context) },
                        onOpenUrl = { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // Clean Failure Summary Card if Failure
            if (uiState.buildStatus == BuildStatus.FAILURE) {
                item {
                    CleanFailureCard(
                        errorMessage = uiState.buildErrorMessage,
                        onViewLogs = { viewModel.navigateTo(AppScreen.COMPILATION_LOGS) }
                    )
                }
            }

            // Header for Stages
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Etapas do Processo",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = getStageProgressBadge(uiState.buildStatus),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Stage 1: File Analysis & Preparation
            item {
                CleanStepItem(
                    stepNumber = 1,
                    title = "Análise do Projeto & Workflow",
                    description = if (uiState.zipScan != null) {
                        val sizeMb = DecimalFormat("#.##").format(uiState.zipScan.totalSize.toDouble() / (1024 * 1024))
                        "${uiState.zipScan.fileName} • ${uiState.zipScan.files.size} arquivos ($sizeMb MB) • Configurado para Release"
                    } else {
                        "Validação dos arquivos e injeção do workflow oficial de compilação"
                    },
                    state = StepVisualState.COMPLETED,
                    isLast = false
                )
            }

            // Stage 2: Push to GitHub
            item {
                val step2State = when (uiState.buildStatus) {
                    BuildStatus.UPLOADING -> StepVisualState.RUNNING
                    BuildStatus.IDLE -> StepVisualState.PENDING
                    else -> StepVisualState.COMPLETED
                }
                CleanStepItem(
                    stepNumber = 2,
                    title = "Sincronização com o Repositório GitHub",
                    description = when (uiState.buildStatus) {
                        BuildStatus.UPLOADING -> uiState.uploadStageText.ifBlank { "Criando commit e enviando árvores Git..." }
                        BuildStatus.IDLE -> "Aguardando envio..."
                        else -> "Código-fonte sincronizado com sucesso no branch '${uiState.targetBranch}'."
                    },
                    state = step2State,
                    progress = if (uiState.buildStatus == BuildStatus.UPLOADING) uiState.uploadProgress else null,
                    isLast = false
                )
            }

            // Stage 3: Runner Allocation
            item {
                val step3State = when {
                    uiState.buildStatus == BuildStatus.UPLOADING || uiState.buildStatus == BuildStatus.IDLE -> StepVisualState.PENDING
                    uiState.buildStatus == BuildStatus.QUEUED -> StepVisualState.RUNNING
                    uiState.buildStatus == BuildStatus.CANCELLED -> StepVisualState.CANCELLED
                    else -> StepVisualState.COMPLETED
                }
                CleanStepItem(
                    stepNumber = 3,
                    title = "Alocação do Runner do GitHub Actions",
                    description = when (step3State) {
                        StepVisualState.RUNNING -> "Aguardando fila e provisionamento da máquina virtual Ubuntu..."
                        StepVisualState.COMPLETED -> "Máquina virtual Ubuntu 22.04 LTS provisionada e conectada."
                        StepVisualState.PENDING -> "Será iniciado após o envio do código."
                        StepVisualState.CANCELLED -> "Cancelado na fila de espera."
                        StepVisualState.FAILED -> "Falha ao alocar máquina virtual."
                    },
                    state = step3State,
                    isLast = false
                )
            }

            // Stage 4: Workflow Execution (Gradle Build & Sub-steps)
            item {
                val step4State = when (uiState.buildStatus) {
                    BuildStatus.UPLOADING, BuildStatus.QUEUED, BuildStatus.IDLE -> StepVisualState.PENDING
                    BuildStatus.IN_PROGRESS -> StepVisualState.RUNNING
                    BuildStatus.SUCCESS -> StepVisualState.COMPLETED
                    BuildStatus.FAILURE -> StepVisualState.FAILED
                    BuildStatus.CANCELLED -> StepVisualState.CANCELLED
                }

                val primaryJob = uiState.activeJobs.firstOrNull()
                val liveSteps = primaryJob?.steps ?: emptyList()

                CleanStepItem(
                    stepNumber = 4,
                    title = "Compilação do Projeto com Gradle",
                    description = when (step4State) {
                        StepVisualState.RUNNING -> "Executando build oficial do Gradle, compilação de código e recursos..."
                        StepVisualState.COMPLETED -> "Build concluído com sucesso. Tarefas do Gradle executadas sem erros."
                        StepVisualState.FAILED -> "Falha durante as tarefas de compilação do Gradle."
                        StepVisualState.CANCELLED -> "Compilação cancelada pelo usuário."
                        StepVisualState.PENDING -> "Aguardando runner iniciar o job de compilação."
                    },
                    state = step4State,
                    subSteps = liveSteps,
                    isLast = false
                )
            }

            // Stage 5: Artifacts & Final APK
            item {
                val step5State = when (uiState.buildStatus) {
                    BuildStatus.SUCCESS -> StepVisualState.COMPLETED
                    BuildStatus.FAILURE -> StepVisualState.FAILED
                    BuildStatus.CANCELLED -> StepVisualState.CANCELLED
                    BuildStatus.IN_PROGRESS -> StepVisualState.PENDING
                    else -> StepVisualState.PENDING
                }
                CleanStepItem(
                    stepNumber = 5,
                    title = "Empacotamento & Entrega do APK",
                    description = when (step5State) {
                        StepVisualState.COMPLETED -> if (uiState.artifacts.isNotEmpty()) {
                            "${uiState.artifacts.size} artefato(s) disponível(is) para download imediato."
                        } else {
                            "Execução concluída com sucesso."
                        }
                        StepVisualState.FAILED -> "Não foi possível gerar os artefatos devido a erro nas etapas anteriores."
                        StepVisualState.CANCELLED -> "Processo cancelado."
                        StepVisualState.RUNNING -> "Finalizando empacotamento..."
                        StepVisualState.PENDING -> "Disponível logo após o término da compilação."
                    },
                    state = step5State,
                    isLast = true
                )
            }

            // Quick Banner to View Terminal Logs
            item {
                Card(
                    onClick = { viewModel.navigateTo(AppScreen.COMPILATION_LOGS) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Acessar Terminal de Logs",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Veja o terminal completo, busca de linhas, avisos e detalhes técnicos.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancelar Compilação?") },
            text = { Text("Isso solicitará a interrupção do workflow no GitHub Actions.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        viewModel.cancelCompilation()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sim, Cancelar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Manter Executando")
                }
            }
        )
    }
}

enum class StepVisualState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

private fun getStageProgressBadge(status: BuildStatus): String {
    return when (status) {
        BuildStatus.IDLE -> "Etapa 0 de 5"
        BuildStatus.UPLOADING -> "Etapa 2 de 5"
        BuildStatus.QUEUED -> "Etapa 3 de 5"
        BuildStatus.IN_PROGRESS -> "Etapa 4 de 5"
        BuildStatus.SUCCESS -> "Concluído (5/5)"
        BuildStatus.FAILURE -> "Falha no processo"
        BuildStatus.CANCELLED -> "Cancelado"
    }
}

@Composable
fun CleanStatusHeroCard(
    fileName: String,
    status: BuildStatus,
    uploadProgress: Float,
    uploadStageText: String,
    activeRun: WorkflowRun?,
    formattedTime: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val (bgGradient, borderColor, icon, title, subtitle) = when (status) {
        BuildStatus.UPLOADING -> Quintuple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            Icons.Default.CloudUpload,
            "Enviando Código-Fonte para o GitHub",
            if (uploadStageText.isNotBlank()) uploadStageText else "Preparando commit e arquivos..."
        )
        BuildStatus.QUEUED -> Quintuple(
            Color(0xFF261D0C),
            GitHubYellow.copy(alpha = 0.5f),
            Icons.Default.HourglassTop,
            "Aguardando Runner na Fila",
            "O GitHub Actions está preparando uma máquina virtual para o build..."
        )
        BuildStatus.IN_PROGRESS -> Quintuple(
            Color(0xFF0D1E3A),
            GitHubBlueAccent.copy(alpha = 0.5f),
            Icons.Default.Sync,
            "Compilação em Andamento",
            "O Gradle está processando o código e gerando o APK..."
        )
        BuildStatus.SUCCESS -> Quintuple(
            Color(0xFF0F2B18),
            GitHubGreenAccent.copy(alpha = 0.6f),
            Icons.Default.CheckCircle,
            "Compilação Concluída com Sucesso!",
            "O APK foi gerado e assinado com sucesso. Pronto para baixar."
        )
        BuildStatus.FAILURE -> Quintuple(
            Color(0xFF2F0F12),
            GitHubRedAccent.copy(alpha = 0.6f),
            Icons.Default.Cancel,
            "Falha na Compilação",
            "Ocorreu um erro durante o build no GitHub Actions."
        )
        BuildStatus.CANCELLED -> Quintuple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            Icons.Default.Block,
            "Compilação Cancelada",
            "A execução foi cancelada antes do término."
        )
        BuildStatus.IDLE -> Quintuple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            Icons.Default.Schedule,
            "Pronto para Compilar",
            "Aguardando comando de inicialização..."
        )
    }

    val isRunning = status == BuildStatus.UPLOADING || status == BuildStatus.QUEUED || status == BuildStatus.IN_PROGRESS

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgGradient,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            when (status) {
                                BuildStatus.SUCCESS -> GitHubGreenAccent.copy(alpha = 0.2f)
                                BuildStatus.FAILURE -> GitHubRedAccent.copy(alpha = 0.2f)
                                BuildStatus.QUEUED -> GitHubYellow.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = when (status) {
                            BuildStatus.SUCCESS -> GitHubGreenAccent
                            BuildStatus.FAILURE -> GitHubRedAccent
                            BuildStatus.QUEUED -> GitHubYellow
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier
                            .size(24.dp)
                            .then(if (isRunning) Modifier.alpha(pulseAlpha) else Modifier)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (fileName.isNotBlank()) fileName else "Projeto.zip",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (status) {
                            BuildStatus.IDLE -> "Etapa 0 de 5 • Pronto para Iniciar"
                            BuildStatus.UPLOADING -> "Etapa 2 de 5 • Enviando Código para o GitHub"
                            BuildStatus.QUEUED -> "Etapa 3 de 5 • Aguardando Runner na Fila"
                            BuildStatus.IN_PROGRESS -> "Etapa 4 de 5 • Compilando APK no GitHub Actions"
                            BuildStatus.SUCCESS -> "Etapa 5 de 5 • APK Compilado com Sucesso!"
                            BuildStatus.FAILURE -> "Falha na Etapa de Compilação"
                            BuildStatus.CANCELLED -> "Processo Cancelado"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = when (status) {
                            BuildStatus.SUCCESS -> GitHubGreenAccent
                            BuildStatus.FAILURE -> GitHubRedAccent
                            BuildStatus.QUEUED -> GitHubYellow
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metadata Row: Timer + Branch + Run ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRunning || status == BuildStatus.SUCCESS || status == BuildStatus.FAILURE) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formattedTime,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (activeRun != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = "Run #${activeRun.runNumber}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (activeRun != null && activeRun.headSha.isNotBlank()) {
                    Text(
                        text = "SHA: ${activeRun.headSha.take(7)}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Linear Progress Indicator when Uploading
            if (status == BuildStatus.UPLOADING) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
fun CleanArtifactDownloadCard(
    artifacts: List<ArtifactItem>,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadedFile: java.io.File?,
    downloadError: String?,
    onDownload: (ArtifactItem) -> Unit,
    onInstall: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF102D1D)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, GitHubGreenAccent.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(GitHubGreenAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (downloadedFile != null) Icons.Default.CheckCircle else Icons.Default.Android,
                        contentDescription = null,
                        tint = GitHubGreenAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (downloadedFile != null) "APK Pronto para Instalar!" else "APK Gerado e Assinado!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        text = if (downloadedFile != null)
                            "Arquivo: ${downloadedFile.name}"
                        else
                            "Compilado com sucesso a partir do código-fonte",
                        fontSize = 12.sp,
                        color = Color(0xFFB5E4C2)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = GitHubGreenAccent.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))

            if (isDownloading) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Baixando e extraindo APK no aparelho...",
                            fontSize = 12.sp,
                            color = Color(0xFFB5E4C2)
                        )
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GitHubGreenAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = GitHubGreenAccent,
                        trackColor = Color(0xFF1E462B)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            if (!downloadError.isNullOrBlank()) {
                Text(
                    text = downloadError,
                    fontSize = 12.sp,
                    color = GitHubRedAccent,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (downloadedFile != null) {
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GitHubGreenAccent,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("INSTALAR APK AGORA", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

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
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            text = "$sizeFormatted MB • Artefato Oficial do Build",
                            fontSize = 11.sp,
                            color = Color(0xFFA5D6A7)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onDownload(artifact) },
                            enabled = !isDownloading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (downloadedFile != null) Color(0xFF1E462B) else GitHubGreenAccent,
                                contentColor = if (downloadedFile != null) Color.White else Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (downloadedFile != null) Icons.Default.Refresh else Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (downloadedFile != null) "Baixar de Novo" else "Baixar APK",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        IconButton(
                            onClick = { onOpenUrl(artifact.archiveDownloadUrl) }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Abrir Link Web",
                                tint = Color(0xFFB5E4C2),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CleanFailureCard(
    errorMessage: String?,
    onViewLogs: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1114)),
        border = androidx.compose.foundation.BorderStroke(1.dp, GitHubRedAccent.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = GitHubRedAccent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "A compilação falhou no GitHub Actions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = GitHubRedAccent
                )
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onViewLogs,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GitHubRedAccent, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver Detalhes do Erro nos Logs", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun CleanStepItem(
    stepNumber: Int,
    title: String,
    description: String,
    state: StepVisualState,
    progress: Float? = null,
    subSteps: List<WorkflowStep> = emptyList(),
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Step indicator & vertical connector line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            StepCircleIcon(state = state, stepNumber = stepNumber)

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .heightIn(min = if (subSteps.isNotEmpty()) 120.dp else 46.dp)
                        .background(
                            when (state) {
                                StepVisualState.COMPLETED -> GitHubGreenAccent.copy(alpha = 0.5f)
                                StepVisualState.RUNNING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            }
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Step Content Card
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (state) {
                    StepVisualState.RUNNING -> MaterialTheme.colorScheme.surfaceVariant
                    StepVisualState.COMPLETED -> MaterialTheme.colorScheme.surface
                    StepVisualState.FAILED -> Color(0xFF261013)
                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                }
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                when (state) {
                    StepVisualState.RUNNING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    StepVisualState.COMPLETED -> GitHubGreenAccent.copy(alpha = 0.25f)
                    StepVisualState.FAILED -> GitHubRedAccent.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                }
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = when (state) {
                            StepVisualState.RUNNING -> MaterialTheme.colorScheme.primary
                            StepVisualState.COMPLETED -> MaterialTheme.colorScheme.onSurface
                            StepVisualState.FAILED -> GitHubRedAccent
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    StepStateChip(state = state)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                if (progress != null && state == StepVisualState.RUNNING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                // If this step has granular GitHub Actions sub-steps
                if (subSteps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Sub-tarefas do GitHub Actions:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    subSteps.forEach { step ->
                        val (sIcon, sColor) = when {
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = sIcon,
                                contentDescription = null,
                                tint = sColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = step.name,
                                fontSize = 11.sp,
                                maxLines = 1,
                                color = if (step.status == "in_progress") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StepCircleIcon(state: StepVisualState, stepNumber: Int) {
    val (bgColor, contentColor, icon) = when (state) {
        StepVisualState.COMPLETED -> Triple(GitHubGreenAccent, Color.Black, Icons.Default.Check)
        StepVisualState.RUNNING -> Triple(MaterialTheme.colorScheme.primary, Color.Black, Icons.Default.Sync)
        StepVisualState.FAILED -> Triple(GitHubRedAccent, Color.White, Icons.Default.Close)
        StepVisualState.CANCELLED -> Triple(Color.Gray, Color.White, Icons.Default.Block)
        StepVisualState.PENDING -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, null)
    }

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Text(
                text = stepNumber.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
fun StepStateChip(state: StepVisualState) {
    val (label, chipColor, textColor) = when (state) {
        StepVisualState.COMPLETED -> Triple("Concluído", GitHubGreenAccent.copy(alpha = 0.15f), GitHubGreenAccent)
        StepVisualState.RUNNING -> Triple("Em Execução", MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), MaterialTheme.colorScheme.primary)
        StepVisualState.FAILED -> Triple("Falhou", GitHubRedAccent.copy(alpha = 0.15f), GitHubRedAccent)
        StepVisualState.CANCELLED -> Triple("Cancelado", Color.Gray.copy(alpha = 0.15f), Color.Gray)
        StepVisualState.PENDING -> Triple("Pendente", MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = chipColor
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
