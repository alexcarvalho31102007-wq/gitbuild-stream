package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.viewmodel.FilterStatusMode
import com.example.viewmodel.MainViewModel

@Composable
fun CustomFilterSelectorCard(
    selected: Boolean,
    title: String,
    subtitle: String,
    painter: Painter,
    checkedColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) checkedColor.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) checkedColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    color = if (selected) checkedColor.copy(alpha = 0.22f)
                    else checkedColor.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = if (selected) checkedColor else checkedColor.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) checkedColor else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }

        Box(
            modifier = Modifier
                .size(20.dp)
                .border(
                    width = 2.dp,
                    color = if (selected) checkedColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(6.dp)
                )
                .background(
                    if (selected) checkedColor else Color.Transparent,
                    shape = RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun FilterDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val showUserApps by viewModel.filterShowUserApps.collectAsStateWithLifecycle()
    val showSystemApps by viewModel.filterShowSystemApps.collectAsStateWithLifecycle()
    val statusMode by viewModel.filterStatusMode.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_filter),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Filtrar Aplicativos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(
                        text = "TIPO DE APLICATIVO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CustomFilterSelectorCard(
                            selected = showUserApps,
                            title = "Aplicativos de Usuário",
                            subtitle = "Instalados de lojas ou APKs externos",
                            painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Person),
                            checkedColor = MaterialTheme.colorScheme.primary,
                            onClick = { viewModel.setFilterShowUserApps(!showUserApps) }
                        )

                        CustomFilterSelectorCard(
                            selected = showSystemApps,
                            title = "Aplicativos do Sistema",
                            subtitle = "Pré-instalados de fábrica no aparelho",
                            painter = painterResource(R.drawable.ic_android),
                            checkedColor = MaterialTheme.colorScheme.primary,
                            onClick = { viewModel.setFilterShowSystemApps(!showSystemApps) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Column {
                    Text(
                        text = "STATUS DE ATIVAÇÃO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CustomFilterSelectorCard(
                            selected = statusMode == FilterStatusMode.ALL,
                            title = "Todos os Status",
                            subtitle = "Exibe tanto aplicativos ativos quanto desativados",
                            painter = painterResource(R.drawable.ic_apps),
                            checkedColor = MaterialTheme.colorScheme.primary,
                            onClick = { viewModel.setFilterStatusMode(FilterStatusMode.ALL) }
                        )

                        CustomFilterSelectorCard(
                            selected = statusMode == FilterStatusMode.DISABLED_ONLY,
                            title = "Apenas Desativados",
                            subtitle = "Filtrar exclusivamente aplicativos desativados no sistema",
                            painter = painterResource(R.drawable.ic_settings),
                            checkedColor = MaterialTheme.colorScheme.error,
                            onClick = { viewModel.setFilterStatusMode(FilterStatusMode.DISABLED_ONLY) }
                        )

                        CustomFilterSelectorCard(
                            selected = statusMode == FilterStatusMode.ENABLED_ONLY,
                            title = "Apenas Ativos",
                            subtitle = "Exibir somente aplicativos em execução ou ativos",
                            painter = painterResource(R.drawable.ic_checklist),
                            checkedColor = MaterialTheme.colorScheme.primary,
                            onClick = { viewModel.setFilterStatusMode(FilterStatusMode.ENABLED_ONLY) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Aplicar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.setFilterShowUserApps(true)
                    viewModel.setFilterShowSystemApps(true)
                    viewModel.setFilterStatusMode(FilterStatusMode.ALL)
                }
            ) {
                Text("Restaurar Padrão", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
    )
}
