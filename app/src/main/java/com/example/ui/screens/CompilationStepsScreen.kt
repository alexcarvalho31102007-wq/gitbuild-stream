package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.BuildStatus
import com.example.ui.viewmodel.CompilerViewModel
import com.example.ui.viewmodel.StepStatus
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompilationStepsScreen(
    viewModel: CompilerViewModel,
    uiState: UiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showErrorLogsDialog by remember { mutableStateOf(false) }

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

    val fileName = uiState.selectedFileName.ifBlank {
        uiState.zipScan?.fileName ?: "codigo-fonte.zip"
    }

    BackHandler {
        if (isRunning) {
            Toast.makeText(context, "A compilação continua em execução no GitHub Actions.", Toast.LENGTH_SHORT).show()
        }
        viewModel.resetCompilation()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.resetCompilation() },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ETAPA 1
            StepRowItem(
                stepNumber = 1,
                title = "Preparação do projeto",
                status = uiState.step1Status,
                detail = uiState.step1Detail
            )

            StepConnectorLine(isActive = uiState.step1Status == StepStatus.SUCCESS)

            // ETAPA 2
            StepRowItem(
                stepNumber = 2,
                title = "Envio do código-fonte",
                status = uiState.step2Status,
                detail = uiState.step2Detail
            )

            StepConnectorLine(isActive = uiState.step2Status == StepStatus.SUCCESS)

            // ETAPA 3
            StepRowItem(
                stepNumber = 3,
                title = "Compilação do APK",
                status = uiState.step3Status,
                detail = uiState.step3Detail
            )

            StepConnectorLine(isActive = uiState.step3Status == StepStatus.SUCCESS)

            // ETAPA 4
            StepRowItem(
                stepNumber = 4,
                title = "Finalização",
                status = uiState.step4Status,
                detail = uiState.step4Detail
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Action area based on completion status
            when (uiState.buildStatus) {
                BuildStatus.SUCCESS -> {
                    // Botão para baixar diretamente o APK gerado
                    Button(
                        onClick = {
                            if (uiState.downloadedApkFile != null) {
                                viewModel.installDownloadedApk(context)
                            } else {
                                viewModel.downloadLatestApk(context)
                            }
                        },
                        enabled = !uiState.isDownloadingApk,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("download_apk_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32),
                            contentColor = Color.White
                        )
                    ) {
                        if (uiState.isDownloadingApk) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            val pct = (uiState.downloadProgress * 100).toInt()
                            Text(
                                text = if (pct > 0) "Baixando APK ($pct%)..." else "Baixando APK...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (uiState.downloadedApkFile != null) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Instalar APK", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Baixar APK", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { viewModel.openRunInBrowser(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Baixar pelo GitHub Actions", fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { viewModel.resetCompilation() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Compilar outro projeto", fontSize = 14.sp)
                    }
                }

                BuildStatus.FAILURE -> {
                    // Opção para visualizar e copiar os logs do erro
                    Button(
                        onClick = { showErrorLogsDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("view_error_logs_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Visualizar e copiar logs do erro", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { viewModel.resetCompilation() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Tentar novamente", fontSize = 14.sp)
                    }
                }

                BuildStatus.CANCELLED -> {
                    OutlinedButton(
                        onClick = { viewModel.resetCompilation() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Voltar ao início", fontSize = 14.sp)
                    }
                }

                else -> {
                    // Running
                    OutlinedButton(
                        onClick = { viewModel.cancelCompilation() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancelar compilação", fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // Modal para visualizar e copiar logs de erro
    if (showErrorLogsDialog) {
        val errorLogs = remember(uiState.liveLogs, uiState.buildErrorMessage) {
            val combined = StringBuilder()
            if (!uiState.buildErrorMessage.isNullOrBlank()) {
                combined.append("MOTIVO DO ERRO:\n").append(uiState.buildErrorMessage).append("\n\n")
            }
            if (uiState.liveLogs.isNotBlank()) {
                combined.append("LOGS DO GITHUB ACTIONS:\n").append(uiState.liveLogs)
            } else {
                combined.append("Nenhum log adicional retornado pelo runner.")
            }
            combined.toString()
        }

        AlertDialog(
            onDismissRequest = { showErrorLogsDialog = false },
            title = {
                Text(
                    text = "Logs do Erro",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = errorLogs,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Logs de Erro GitCompile", errorLogs)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Logs copiados para a área de transferência!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copiar Logs")
                }
            },
            dismissButton = {
                TextButton(onClick = { showErrorLogsDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}

@Composable
private fun StepRowItem(
    stepNumber: Int,
    title: String,
    status: StepStatus,
    detail: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            when (status) {
                StepStatus.IN_PROGRESS -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                StepStatus.SUCCESS -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Sucesso",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(26.dp)
                    )
                }
                StepStatus.FAILURE -> {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "Falha",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(26.dp)
                    )
                }
                StepStatus.PENDING -> {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "ETAPA $stepNumber — $title",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = when (status) {
                    StepStatus.FAILURE -> Color(0xFFD32F2F)
                    StepStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )

            AnimatedVisibility(visible = detail.isNotBlank()) {
                Text(
                    text = detail,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun StepConnectorLine(isActive: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(start = 15.dp)
                .width(2.dp)
                .height(28.dp)
                .background(
                    if (isActive) Color(0xFF2E7D32)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
        )
    }
}
