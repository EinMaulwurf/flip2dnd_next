package dev.flip2dnd.next.presentation.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.flip2dnd.next.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenRoute(onBack: () -> Unit, onDonateClick: () -> Unit) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    val sections = listOf(R.string.flip_behavior, R.string.feedback, R.string.advanced_filters,
        R.string.app_settings, R.string.extras)
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.settings)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sections.forEachIndexed { index, label ->
                    FilterChip(selected = section == index, onClick = { section = index },
                        label = { Text(stringResource(label)) })
                }
            }
            key(section) {
                SettingsContent(onDonateClick = onDonateClick, section = section)
            }
        }
    }
}
