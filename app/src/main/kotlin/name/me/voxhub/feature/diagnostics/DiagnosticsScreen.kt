package name.me.voxhub.feature.diagnostics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import name.me.voxhub.R
import name.me.voxhub.VoxHubApplication
import name.me.voxhub.diagnostics.DiagnosticSummary
import name.me.voxhub.ui.components.PageColumn
import name.me.voxhub.ui.components.PageTitle
import name.me.voxhub.ui.components.SecondaryText

@Composable
fun DiagnosticsRoute(contentPadding: PaddingValues, onBack: () -> Unit, showMessage: suspend (String) -> Unit) {
    val context = LocalContext.current
    val diagnostics = (context.applicationContext as VoxHubApplication).diagnostics
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf(DiagnosticSummary()) }
    var available by remember { mutableStateOf(diagnostics.isAvailable) }
    var busy by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val exportedMessage = stringResource(R.string.diagnostics_exported)
    val clearedMessage = stringResource(R.string.diagnostics_cleared)
    val failedMessage = stringResource(R.string.diagnostics_operation_failed)

    suspend fun refresh() {
        summary = diagnostics.summary()
        available = diagnostics.isAvailable
    }
    suspend fun perform(success: String, action: suspend () -> Unit) {
        busy = true
        try {
            action()
            refresh()
            showMessage(success)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            showMessage(failedMessage)
        } finally {
            busy = false
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            perform(exportedMessage) { diagnostics.export(uri) }
        }
    }
    LaunchedEffect(diagnostics) {
        try {
            refresh()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            available = false
        }
    }
    PageColumn(contentPadding) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
            }
            PageTitle(R.string.diagnostics_title, Modifier.weight(1f))
        }
        SecondaryText(stringResource(R.string.diagnostics_description))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.diagnostics_size, android.text.format.Formatter.formatFileSize(context, summary.bytes)))
                Text(stringResource(R.string.diagnostics_reports, summary.reportCount))
            }
        }
        if (!available) SecondaryText(stringResource(R.string.diagnostics_unavailable))
        Button(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !busy, onClick = { export.launch("voxhub-diagnostics-${System.currentTimeMillis()}.zip") }) {
            Text(stringResource(R.string.diagnostics_export))
        }
        TextButton(enabled = !busy, onClick = { confirmClear = true }) {
            Text(stringResource(R.string.diagnostics_clear), color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.diagnostics_clear), color = MaterialTheme.colorScheme.error) },
        text = { Text(stringResource(R.string.diagnostics_clear_confirmation)) },
        confirmButton = {
            TextButton(onClick = {
                confirmClear = false
                scope.launch { perform(clearedMessage) { diagnostics.clear() } }
            }) { Text(stringResource(R.string.diagnostics_clear), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = { confirmClear = false }) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
