package name.me.voxhub.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import name.me.voxhub.feature.diagnostics.DiagnosticsRoute
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import name.me.voxhub.R
import name.me.voxhub.di.InitializationState
import name.me.voxhub.di.VoxHubContainer

@Composable
fun InitializationGate(container: VoxHubContainer, content: @Composable () -> Unit) {
    val initialization by container.initialization.collectAsStateWithLifecycle()
    var showDiagnostics by remember { mutableStateOf(false) }
    if (showDiagnostics) {
        val messages = remember { SnackbarHostState() }
        Scaffold(snackbarHost = { SnackbarHost(messages) }) { padding ->
            DiagnosticsRoute(padding, { showDiagnostics = false }) { messages.showSnackbar(it) }
        }
    } else if (initialization == InitializationState.READY) {
        content()
    } else {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                if (initialization == InitializationState.LOADING) CircularProgressIndicator()
                Text(stringResource(if (initialization == InitializationState.LOADING) R.string.app_loading else R.string.app_initialization_failed))
                if (initialization == InitializationState.FAILED) {
                    TextButton(onClick = { showDiagnostics = true }) { Text(stringResource(R.string.diagnostics_title)) }
                }
            }
        }
    }
}
