package name.me.voxhub.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import name.me.voxhub.R
import name.me.voxhub.VoxHubApplication
import name.me.voxhub.feature.status.StatusViewModel
import name.me.voxhub.feature.status.StatusUiState
import name.me.voxhub.ui.components.*
import name.me.voxhub.ui.viewModelFactory

@Composable
fun SettingsRoute(contentPadding: PaddingValues, onUsage: () -> Unit, onDiagnostics: () -> Unit,
    showMessage: suspend (String) -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as VoxHubApplication).container
    val model: StatusViewModel = viewModel(factory = viewModelFactory { container.createStatusViewModel() })
    val state by model.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.operation_failed)
    LifecycleResumeEffect(model) { model.refreshSystemState(); onPauseOrDispose {} }
    SettingsScreen(state, contentPadding, onUsage, onDiagnostics) {
        runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
            .onFailure { scope.launch { showMessage(failed) } }
    }
}

@Composable
internal fun SettingsScreen(state: StatusUiState, contentPadding: PaddingValues,
    onUsage: () -> Unit, onDiagnostics: () -> Unit, onSystem: () -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding),
        contentAlignment = androidx.compose.ui.Alignment.TopCenter,
    ) {
        val compactHeader = maxHeight < 400.dp
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            TopLevelPageHeader(stringResource(R.string.nav_status), compact = compactHeader)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item { SettingsGroup(stringResource(R.string.system_tts)) {
                SettingsItem(stringResource(R.string.status_engine_registration),
                    stringResource(if (state.system.engineAvailable) R.string.status_registered_hint else R.string.status_unregistered_hint),
                    R.drawable.ic_status, first = true, last = false, onClick = onSystem)
                SettingsItem(stringResource(R.string.default_tts),
                    state.system.defaultEngineName ?: stringResource(if (state.system.isDefaultEngine) R.string.engine_name else R.string.status_default_unset),
                    R.drawable.ic_plans, first = false, last = true, onClick = onSystem)
            } }
            item { SettingsGroup(stringResource(R.string.settings_usage_group)) {
                SettingsItem(stringResource(R.string.usage_title), stringResource(R.string.status_usage_hint),
                    R.drawable.ic_usage, onClick = onUsage)
            } }
            item { SettingsGroup(stringResource(R.string.settings_help_group)) {
                SettingsItem(stringResource(R.string.diagnostics_title), stringResource(R.string.status_diagnostics_hint),
                    R.drawable.ic_diagnostics, onClick = onDiagnostics)
            } }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, Modifier.padding(start = 4.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun SettingsItem(title: String, detail: String, icon: Int,
    first: Boolean = true, last: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = if (first) 20.dp else 4.dp, topEnd = if (first) 20.dp else 4.dp,
            bottomStart = if (last) 20.dp else 4.dp, bottomEnd = if (last) 20.dp else 4.dp)) {
        ListItem(headlineContent = { Text(title) }, supportingContent = { Text(detail) },
            leadingContent = { Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
    }
}
