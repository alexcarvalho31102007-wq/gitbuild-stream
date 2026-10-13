package com.example.ui.screens

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.GitHubRepo
import com.example.data.model.GitHubUser
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.CompilerViewModel
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: CompilerViewModel,
    uiState: UiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var showPassword by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf(uiState.token) }
    var showHelpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.token) {
        if (uiState.token.isNotEmpty() && tokenInput.isEmpty()) {
            tokenInput = uiState.token
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Ícone Terminal",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "GitCompile",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.testTag("help_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = "Como obter o Token"
                        )
                    }
                    if (uiState.currentUser != null) {
                        IconButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier.testTag("logout_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Desconectar Conta",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Cloud CI",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Compilação Remota via GitHub Actions",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "O app envia seu código compactado em ZIP para o GitHub e monitora o build em tempo real sem sobrecarregar seu dispositivo.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            // If already authenticated
            if (uiState.currentUser != null) {
                UserConnectedCard(
                    user = uiState.currentUser,
                    selectedRepo = uiState.selectedRepoFullName,
                    userRepos = uiState.userRepos,
                    isLoadingRepos = uiState.isLoadingRepos,
                    isCreatingRepo = uiState.isCreatingRepo,
                    targetBranch = uiState.targetBranch,
                    onSelectRepo = { viewModel.onSelectRepo(it) },
                    onBranchChanged = { viewModel.onBranchChanged(it) },
                    onCreateRepo = { viewModel.createDedicatedBuildRepo() },
                    onContinue = { viewModel.navigateTo(AppScreen.SELECT_ZIP) },
                    onSwitchAccount = { viewModel.logout() }
                )
            } else {
                // Token Input Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "Token Key",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Token de Acesso do GitHub",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Insira um Personal Access Token (PAT) com as permissões 'repo' e 'workflow':",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = {
                                tokenInput = it
                                viewModel.onTokenChanged(it)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_token_input"),
                            label = { Text("ghp_... ou github_pat_...") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                viewModel.submitToken(tokenInput)
                            }),
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showPassword) "Ocultar" else "Mostrar"
                                        )
                                    }
                                    if (tokenInput.isEmpty()) {
                                        IconButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val clipData = clipboard?.primaryClip
                                            if (clipData != null && clipData.itemCount > 0) {
                                                val text = clipData.getItemAt(0).text?.toString() ?: ""
                                                if (text.isNotBlank()) {
                                                    tokenInput = text
                                                    viewModel.onTokenChanged(text)
                                                }
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.ContentPaste,
                                                contentDescription = "Colar da área de transferência"
                                            )
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )

                        AnimatedVisibility(visible = uiState.authError != null) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                Text(
                                    text = uiState.authError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.submitToken(tokenInput)
                            },
                            enabled = tokenInput.isNotBlank() && !uiState.isCheckingAuth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("connect_github_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GitHubGreenAccent,
                                contentColor = Color.Black
                            )
                        ) {
                            if (uiState.isCheckingAuth) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Validando Token...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Login, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Autenticar e Continuar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = { showHelpDialog = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Como gerar este token no GitHub?", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Help, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gerando seu Token do GitHub", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "1. Acesse github.com no seu navegador.\n" +
                                "2. Vá em Configurações (Settings) > Developer Settings > Personal access tokens > Tokens (classic).\n" +
                                "3. Clique em 'Generate new token (classic)'.\n" +
                                "4. Dê um nome (ex: GitCompile App).\n" +
                                "5. Marque as permissões:\n" +
                                "   • 'repo' (acesso aos repositórios para enviar o código)\n" +
                                "   • 'workflow' (para gerenciar e ler logs do GitHub Actions)\n" +
                                "6. Copie o token gerado e cole aqui no aplicativo.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Entendido", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserConnectedCard(
    user: GitHubUser,
    selectedRepo: String,
    userRepos: List<com.example.data.model.GitHubRepo>,
    isLoadingRepos: Boolean,
    isCreatingRepo: Boolean,
    targetBranch: String,
    onSelectRepo: (String) -> Unit,
    onBranchChanged: (String) -> Unit,
    onCreateRepo: () -> Unit,
    onContinue: () -> Unit,
    onSwitchAccount: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var customRepoText by remember { mutableStateOf(selectedRepo) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // User identity row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = "Avatar de ${user.login}",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.name ?: user.login,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "@${user.login}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GitHubGreenAccent.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GitHubGreenAccent)
                ) {
                    Text(
                        text = "Conectado",
                        color = GitHubGreenAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Repositório de Destino para Compilação",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Dropdown or repo selector
            ExposedDropdownMenuBox(
                expanded = isExpanded,
                onExpandedChange = { isExpanded = !isExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = customRepoText,
                    onValueChange = {
                        customRepoText = it
                        onSelectRepo(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                        .testTag("repo_dropdown_input"),
                    readOnly = false,
                    label = { Text("Selecione ou digite usuario/repositorio") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = isExpanded,
                    onDismissRequest = { isExpanded = false }
                ) {
                    if (isLoadingRepos) {
                        DropdownMenuItem(
                            text = { Text("Carregando seus repositórios...") },
                            onClick = {}
                        )
                    } else if (userRepos.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Nenhum repositório encontrado.") },
                            onClick = {}
                        )
                    } else {
                        userRepos.forEach { repo ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(repo.fullName, fontWeight = FontWeight.Medium)
                                        if (repo.isPrivate) {
                                            Text("Privado", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                },
                                onClick = {
                                    customRepoText = repo.fullName
                                    onSelectRepo(repo.fullName)
                                    isExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Create Dedicated Repo Button
            OutlinedButton(
                onClick = onCreateRepo,
                enabled = !isCreatingRepo,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isCreatingRepo) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Criando no GitHub...", fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Criar repositório 'git-compile-runner' automaticamente", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Branch selector
            OutlinedTextField(
                value = targetBranch,
                onValueChange = onBranchChanged,
                label = { Text("Branch de Compilação (Recomendado: main)") },
                supportingText = { Text("Use a branch principal para o GitHub Actions registrar os workflows automaticamente.", fontSize = 11.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("target_branch_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onContinue,
                enabled = customRepoText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("continue_to_zip_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Avançar para Seleção de ZIP", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null)
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onSwitchAccount,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Trocar de Token / Conta", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}
