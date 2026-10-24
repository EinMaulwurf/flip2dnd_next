package dev.flip2dnd.next.presentation.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.flip2dnd.next.R
import dev.flip2dnd.next.diagnostics.DiagnosticLog
import dev.flip2dnd.next.diagnostics.DiagnosticSharing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun DiagnosticsSettings() {
    val context = LocalContext.current
    val recorder = remember(context) { DiagnosticLog.get(context) }
    val enabled by recorder.enabled.collectAsState()
    val storageError by recorder.storageError.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { Toast.makeText(context, R.string.diagnostics_action_failed, Toast.LENGTH_LONG).show() }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.diagnostics_title), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.diagnostics_record), modifier = Modifier.weight(1f))
            Switch(checked = enabled, enabled = !busy, onCheckedChange = { value -> run { recorder.setEnabled(value) } })
        }
        Text(stringResource(R.string.diagnostics_description), style = MaterialTheme.typography.bodySmall)
        if (enabled) Text(stringResource(R.string.diagnostics_instructions), style = MaterialTheme.typography.bodySmall)
        if (storageError) Text(stringResource(R.string.diagnostics_storage_error), color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = { run {
                val report = recorder.export()
                context.startActivity(Intent.createChooser(DiagnosticSharing.intent(context, report),
                    context.getString(R.string.diagnostics_export)))
            } }) { Text(stringResource(R.string.diagnostics_export)) }
            TextButton(enabled = !busy, onClick = { run {
                recorder.clear()
                Toast.makeText(context, R.string.diagnostics_cleared, Toast.LENGTH_SHORT).show()
            } }) { Text(stringResource(R.string.diagnostics_clear)) }
        }
    }
}
