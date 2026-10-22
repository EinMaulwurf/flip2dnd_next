package dev.robin.flip_2_dnd.presentation.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.robin.flip_2_dnd.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainState,
    onDonateClick: () -> Unit,
    onToggleService: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onHistoryClick) {
                        Icon(painterResource(R.drawable.ic_history), stringResource(R.string.history))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            FilledTonalIconButton(
                onClick = onToggleService,
                modifier = Modifier.size(144.dp),
                shape = MaterialTheme.shapes.large,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (state.isServiceRunning) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Icon(
                    painterResource(R.drawable.ic_stat_flip),
                    stringResource(if (state.isServiceRunning) R.string.stop_service else R.string.start_service),
                    modifier = Modifier.size(64.dp),
                )
            }
            Text(
                stringResource(if (state.isServiceRunning) R.string.service_running else R.string.service_not_running),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            if (state.isServiceRunning) {
                Text(stringResource(state.dndMode), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.onboarding_simple_desc),
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onSettingsClick, shape = MaterialTheme.shapes.large) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings))
            }
        }
    }
}
