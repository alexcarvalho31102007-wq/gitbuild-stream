package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.compiler.viewmodel.CompilerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(viewModel: CompilerViewModel) {
    val prefs = viewModel.preferences
    var tokenInput by remember { mutableStateOf(prefs.githubToken) }
    var isTokenVisible by remember { mutableStateOf(false) }

    var repoOwnerInput by remember { mutableStateOf(prefs.repoOwner) }
    var repoNameInput by remember { mutableStateOf(prefs.repoName) }

    var isSoundEnabled by remember { mutableStateOf(prefs.isSoundEnabled) }
    var isVibEnabled by remember { mutableStateOf(prefs.isVibrationEnabled) }
    var isAutoDownload by remember { mutableStateOf(prefs.isAutoDownloadApk) }

    val githubUser by viewModel.githubUser.collectAsStateWithLifecycle()
    val userRepos by viewModel.userRepos.collectAsStateWithLifecycle()
    val isTestingToken by viewModel.isTestingToken.collectAsStateWithLifecycle()
    val tokenTestResult by viewModel.tokenTestResult.collectAsStateWithLifecycle()
    val isCreatingRepo by viewModel.isCreatingRepo.collectAsStateWithLifecycle()

    var showCreateRepoDialog by remember { mutableStateOf(false) }
    var newRepoName by remember { mutableStateOf("android-build-runner") }
    var isNewRepoPrivate by remember { mutableStateOf(true) }

    var repoDropdownExpanded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Seção 1: Autenticação com GitHub (PAT)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_github),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Autenticação GitHub",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Informe um Personal Access Token (PAT) clássico ou fine-grained com permissões de 'repo' e 'workflow'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("GitHub Token (ghp_...)") },
                    singleLine = true,
                    visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_info),
                                contentDescription = if (isTokenVisible) "Ocultar" else "Mostrar",
                                tint = if (isTokenVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.testGitHubToken(tokenInput) },
                        enabled = tokenInput.isNotBlank() && !isTestingToken,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isTestingToken) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Validando...")
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Validar e Salvar")
                        }
                    }
                }

                if (githubUser != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Conectado: @${githubUser!!.login}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${githubUser!!.publicRepos} repositórios disponíveis",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (tokenTestResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = tokenTestResult!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Seção 2: Repositório de Compilação
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Repositório do GitHub",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "O repositório onde os códigos serão carregados e compilados via GitHub Actions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (userRepos.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = repoDropdownExpanded,
                        onExpandedChange = { repoDropdownExpanded = !repoDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = if (repoOwnerInput.isNotBlank() && repoNameInput.isNotBlank()) "$repoOwnerInput/$repoNameInput" else "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Selecionar da Conta") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = repoDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = repoDropdownExpanded,
                            onDismissRequest = { repoDropdownExpanded = false }
                        ) {
                            userRepos.forEach { repo ->
                                DropdownMenuItem(
                                    text = { Text(repo.fullName) },
                                    onClick = {
                                        repoOwnerInput = repo.owner
                                        repoNameInput = repo.name
                                        viewModel.setRepository(repo.owner, repo.name)
                                        repoDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = repoOwnerInput,
                        onValueChange = {
                            repoOwnerInput = it
                            viewModel.setRepository(repoOwnerInput, repoNameInput)
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Proprietário") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = repoNameInput,
                        onValueChange = {
                            repoNameInput = it
                            viewModel.setRepository(repoOwnerInput, repoNameInput)
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Repositório") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { showCreateRepoDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(painter = painterResource(R.drawable.ic_upload), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Criar Novo Repositório Dedicado")
                }
            }
        }

        // Seção 3: Preferências de Compilação e Notificações
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Preferências de Execução",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Auto Download
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Download Automático do APK", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Salva o APK gerado diretamente em Downloads ao concluir a compilação.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isAutoDownload,
                        onCheckedChange = {
                            isAutoDownload = it
                            prefs.isAutoDownloadApk = it
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sons de Notificação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sons de Status", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Toca som padrão ao concluir e som de alerta caso ocorra falha.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isSoundEnabled,
                        onCheckedChange = {
                            isSoundEnabled = it
                            prefs.isSoundEnabled = it
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Vibração
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vibração do Dispositivo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Padrão de vibração curto para sucesso e duplo para falha.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isVibEnabled,
                        onCheckedChange = {
                            isVibEnabled = it
                            prefs.isVibrationEnabled = it
                        }
                    )
                }
            }
        }
    }

    // Diálogo para criação de novo repositório
    if (showCreateRepoDialog) {
        AlertDialog(
            onDismissRequest = { showCreateRepoDialog = false },
            title = { Text("Novo Repositório de Compilação", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Criar um repositório exclusivo na sua conta do GitHub para organizar as compilações.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newRepoName,
                        onValueChange = { newRepoName = it },
                        label = { Text("Nome do Repositório") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Repositório Privado", fontSize = 13.sp)
                        Switch(
                            checked = isNewRepoPrivate,
                            onCheckedChange = { isNewRepoPrivate = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createDedicatedRepository(newRepoName, isNewRepoPrivate)
                        showCreateRepoDialog = false
                    },
                    enabled = newRepoName.isNotBlank() && !isCreatingRepo
                ) {
                    if (isCreatingRepo) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Criar Repositório")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateRepoDialog = false }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }
}
