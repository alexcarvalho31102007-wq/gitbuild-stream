package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CompilationLogsScreen
import com.example.ui.screens.CompilationStepsScreen
import com.example.ui.screens.SelectZipScreen
import com.example.ui.theme.GitCompilerTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.CompilerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GitCompilerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GitCompilerApp()
                }
            }
        }
    }
}

@Composable
fun GitCompilerApp(
    viewModel: CompilerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Crossfade(targetState = uiState.currentScreen, label = "ScreenTransition") { screen ->
        when (screen) {
            AppScreen.AUTH -> AuthScreen(viewModel = viewModel, uiState = uiState)
            AppScreen.SELECT_ZIP -> SelectZipScreen(viewModel = viewModel, uiState = uiState)
            AppScreen.COMPILATION_STEPS -> CompilationStepsScreen(viewModel = viewModel, uiState = uiState)
            AppScreen.COMPILATION_LOGS -> CompilationLogsScreen(viewModel = viewModel, uiState = uiState)
        }
    }
}
