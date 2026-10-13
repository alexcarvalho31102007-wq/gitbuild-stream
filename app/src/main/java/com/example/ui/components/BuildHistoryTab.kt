package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.compiler.data.BuildRecordEntity
import com.example.compiler.data.BuildStatus
import com.example.compiler.viewmodel.CompilerViewModel
import com.example.compiler.viewmodel.HistoryFilterTab
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BuildHistoryTab(
    viewModel: CompilerViewModel,
    onOpenLogs: (BuildRecordEntity) -> Unit
) {
    val context = LocalContext.current
    val allBuilds by viewModel.allBuilds.collectAsStateWithLifecycle()
    val currentFilter by viewModel.historyFilter.collectAsStateWithLifecycle()

    var buildToDelete by remember { mutableStateOf<BuildRecordEntity?>(null) }

    val filteredBuilds = remember(allBuilds, currentFilter) {
        when (currentFilter) {
            HistoryFilterTab.ALL -> allBuilds
            HistoryFilterTab.IN_PROGRESS -> allBuilds.filter { it.status.isActive }
            HistoryFilterTab.SUCCESS -> allBuilds.filter { it.status == BuildStatus.SUCCESS }
            HistoryFilterTab.FAILED -> allBuilds.filter { it.status == BuildStatus.FAILED || it.status == BuildStatus.CANCELLED }
        }
    }

    val inProgressCount = remember(allBuilds) { allBuilds.count { it.status.isActive } }
    val successCount = remember(allBuilds) { allBuilds.count { it.status == BuildStatus.SUCCESS } }
    val failedCount = remember(allBuilds) { allBuilds.count { it.status == BuildStatus.FAILED || it.status == BuildStatus.CANCELLED } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Barra de filtros por status
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterChip(
                    selected = currentFilter == HistoryFilterTab.ALL,
                    onClick = { viewModel.setHistoryFilter(HistoryFilterTab.ALL) },
                    label = { Text("Todas (${allBuilds.size})", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = currentFilter == HistoryFilterTab.IN_PROGRESS,
                    onClick = { viewModel.setHistoryFilter(HistoryFilterTab.IN_PROGRESS) },
                    label = { Text("Em Andamento ($inProgressCount)", fontSize = 12.sp) },
                    leadingIcon = if (inProgressCount > 0) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }
            item {
                FilterChip(
                    selected = currentFilter == HistoryFilterTab.SUCCESS,
                    onClick = { viewModel.setHistoryFilter(HistoryFilterTab.SUCCESS) },
                    label = { Text("Concluídas ($successCount)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
            item {
                FilterChip(
                    selected = currentFilter == HistoryFilterTab.FAILED,
                    onClick = { viewModel.setHistoryFilter(HistoryFilterTab.FAILED) },
                    label = { Text("Com Falha ($failedCount)", fontSize = 12.sp) }
                )
            }
        }

        if (filteredBuilds.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(R.drawable.ic_history),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when (currentFilter) {
                            HistoryFilterTab.ALL -> "Nenhuma compilação registrada"
                            HistoryFilterTab.IN_PROGRESS -> "Nenhuma compilação em andamento"
                            HistoryFilterTab.SUCCESS -> "Nenhuma compilação concluída ainda"
                            HistoryFilterTab.FAILED -> "Nenhuma compilação com falha"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Inicie uma compilação na aba Nova Compilação.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredBuilds, key = { it.id }) { build ->
                    BuildHistoryCard(
                        build = build,
                        onCancel = { viewModel.cancelBuild(context, build.id) },
                        onDelete = { buildToDelete = build },
                        onDownloadApk = { viewModel.downloadApkManually(context, build) },
                        onInstallApk = { path -> viewModel.installApk(context, path) },
                        onShareApk = { path -> viewModel.shareApk(context, path) },
                        onOpenLogs = { onOpenLogs(build) }
                    )
                }
            }
        }
    }

    // Diálogo de confirmação de exclusão do histórico
    if (buildToDelete != null) {
        val target = buildToDelete!!
        AlertDialog(
            onDismissRequest = { buildToDelete = null },
            icon = {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text("Excluir do Histórico", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Deseja remover o registro da compilação #${target.id} (${target.projectName}) do histórico local?\n\nIsso não afeta compilações em andamento nem apaga arquivos no GitHub."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBuild(target.id)
                        buildToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { buildToDelete = null }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun BuildHistoryCard(
    build: BuildRecordEntity,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onDownloadApk: () -> Unit,
    onInstallApk: (String) -> Unit,
    onShareApk: (String) -> Unit,
    onOpenLogs: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateText = remember(build.createdAtTimestamp) { dateFormat.format(Date(build.createdAtTimestamp)) }

    val durationText = remember(build.createdAtTimestamp, build.finishedAtTimestamp) {
        if (build.finishedAtTimestamp != null && build.finishedAtTimestamp > build.createdAtTimestamp) {
            val seconds = (build.finishedAtTimestamp - build.createdAtTimestamp) / 1000
            val mins = seconds / 60
            val remainingSecs = seconds % 60
            if (mins > 0) "${mins}m ${remainingSecs}s" else "${remainingSecs}s"
        } else {
            null
        }
    }

    val isApkDownloadedLocally = remember(build.apkLocalPath) {
        build.apkLocalPath != null && File(build.apkLocalPath).exists()
    }

    val borderColor = when (build.status) {
        BuildStatus.SUCCESS -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        BuildStatus.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        BuildStatus.CANCELLED -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Linha superior: Nome do Projeto, ID e Badge de Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = build.projectName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "#${build.id} • $dateText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Badge de Status
                StatusBadge(status = build.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Detalhe do Status Atual
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (build.status.isActive) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = build.statusDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (build.status) {
                        BuildStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                        BuildStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Metadados adicionais: duração, tamanho do APK
            if (durationText != null || build.apkSizeBytes != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (durationText != null) {
                        Text(
                            text = "Tempo: $durationText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (build.apkSizeBytes != null && build.apkSizeBytes > 0) {
                        val mb = build.apkSizeBytes.toDouble() / (1024 * 1024)
                        Text(
                            text = "Tamanho: ${String.format("%.1f MB", mb)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (build.runNumber != null) {
                        Text(
                            text = "GitHub Run #${build.runNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Linha de ações
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ações da esquerda
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (build.status == BuildStatus.SUCCESS) {
                        if (isApkDownloadedLocally) {
                            Button(
                                onClick = { onInstallApk(build.apkLocalPath!!) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(painter = painterResource(R.drawable.ic_android), contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Instalar APK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            IconButton(
                                onClick = { onShareApk(build.apkLocalPath!!) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(painter = painterResource(R.drawable.ic_upload), contentDescription = "Compartilhar", modifier = Modifier.size(16.dp))
                            }
                        } else {
                            Button(
                                onClick = onDownloadApk,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(painter = painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Baixar APK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (build.status == BuildStatus.FAILED) {
                        OutlinedButton(
                            onClick = onOpenLogs,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(painter = painterResource(R.drawable.ic_terminal), contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ver Logs", fontSize = 12.sp)
                        }

                        IconButton(
                            onClick = {
                                val text = build.logs ?: build.errorMessage ?: "Sem logs"
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Logs #${build.id}", text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Logs copiados!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(painter = painterResource(R.drawable.ic_copy), contentDescription = "Copiar Logs", modifier = Modifier.size(16.dp))
                        }
                    }

                    if (build.status == BuildStatus.SUCCESS) {
                        OutlinedButton(
                            onClick = onOpenLogs,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(painter = painterResource(R.drawable.ic_terminal), contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Logs", fontSize = 12.sp)
                        }
                    }

                    if (build.status.isActive) {
                        Button(
                            onClick = onCancel,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancelar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Ações da direita: Link GitHub e Excluir
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (!build.htmlUrl.isNullOrBlank()) {
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(build.htmlUrl))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_github),
                                contentDescription = "Ver no GitHub",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        enabled = !build.status.isActive,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Excluir Registro",
                            tint = if (!build.status.isActive) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: BuildStatus) {
    val (bgColor, textColor, label) = when (status) {
        BuildStatus.SUCCESS -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Sucesso"
        )
        BuildStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Falha"
        )
        BuildStatus.CANCELLED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Cancelada"
        )
        else -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            status.displayLabel
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (status) {
                BuildStatus.SUCCESS -> Icon(Icons.Default.Check, contentDescription = null, tint = textColor, modifier = Modifier.size(12.dp))
                BuildStatus.FAILED -> Icon(Icons.Default.Warning, contentDescription = null, tint = textColor, modifier = Modifier.size(12.dp))
                else -> {}
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}
