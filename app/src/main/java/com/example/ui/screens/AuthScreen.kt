package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.CompilerViewModel
import com.example.ui.viewmodel.UiState

@Composable
fun AuthScreen(
    viewModel: CompilerViewModel,
    uiState: UiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var tokenInput by remember { mutableStateOf(uiState.token) }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.token) {
        if (uiState.token.isNotEmpty() && tokenInput.isEmpty()) {
            tokenInput = uiState.token
        }
    }

    Scaffold(modifier = modifier) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "GitCompile",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(36.dp))

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = {
                        tokenInput = it
                        viewModel.onTokenChanged(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_token_input"),
                    label = { Text("Token do GitHub") },
                    placeholder = { Text("ghp_... ou github_pat_...") },
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
                    Text(
                        text = uiState.authError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.submitToken(tokenInput)
                    },
                    enabled = tokenInput.isNotBlank() && !uiState.isCheckingAuth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_token_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isCheckingAuth) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Validando...", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Salvar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
