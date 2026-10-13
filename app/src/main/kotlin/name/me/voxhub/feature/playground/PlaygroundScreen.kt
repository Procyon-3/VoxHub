package name.me.voxhub.feature.playground

import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import name.me.voxhub.feature.status.StatusViewModel
import name.me.voxhub.feature.status.SystemTtsState
import name.me.voxhub.ui.components.*
import name.me.voxhub.ui.components.SelectionOption
import name.me.voxhub.ui.components.SelectionSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import name.me.voxhub.R
import name.me.voxhub.VoxHubApplication
import name.me.voxhub.core.tts.TtsVoice
import name.me.voxhub.domain.model.ProviderConfigId
import name.me.voxhub.ui.components.PageMaxWidth
import name.me.voxhub.ui.components.problemTitle
import name.me.voxhub.ui.rememberLocalNetworkAction
import name.me.voxhub.ui.viewModelFactory

@Composable
fun PlaygroundRoute(
    contentPadding: PaddingValues,
    onManageProviders: () -> Unit,
    showMessage: suspend (String) -> Unit,
    expanded: Boolean = false,
    requestedConfigId: String? = null,
    onRequestConsumed: () -> Unit = {},
    onSettings: () -> Unit = {},
    onEdit: (ProviderConfigId) -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as VoxHubApplication
    val sample = stringResource(R.string.sample_text)
    val viewModel: PlaygroundViewModel = viewModel(
        factory = viewModelFactory { application.container.createPlaygroundViewModel(sample) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val systemModel: StatusViewModel = viewModel(factory = viewModelFactory { application.container.createStatusViewModel() })
    val systemState by systemModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.operation_failed)
    val activated = stringResource(R.string.provider_activated)
    var activating by remember { mutableStateOf(false) }
    LifecycleResumeEffect(systemModel) { systemModel.refreshSystemState(); onPauseOrDispose {} }
    LaunchedEffect(requestedConfigId, state.configs) {
        if (requestedConfigId != null && state.configs.any { it.id.value == requestedConfigId }) {
            viewModel.selectProvider(ProviderConfigId(requestedConfigId))
            onRequestConsumed()
        }
    }
    val withNetworkAccess = rememberLocalNetworkAction(showMessage)
    LifecycleStartEffect(viewModel) {
        onStopOrDispose { viewModel.stop() }
    }
    PlaygroundScreen(
        state = state,
        voices = viewModel.voicesForSelected(),
        providerNames = application.container.providerCatalog.registrations.associate {
            it.descriptor.id to it.descriptor.displayName
        },
        sampleText = sample,
        contentPadding = contentPadding,
        onManageProviders = onManageProviders,
        onProviderChange = viewModel::selectProvider,
        onVoiceChange = viewModel::selectVoice,
        onTextChange = viewModel::setText,
        onClear = { viewModel.setText("") },
        onRestore = viewModel::restoreSample,
        onSpeak = {
            val endpoint = state.selectedId?.let(application.container.configRepository::getEndpoint)
            if (endpoint == null) viewModel.speak() else withNetworkAccess(endpoint, viewModel::speak)
        },
        onStop = viewModel::stop,
        system = systemState.system,
        expanded = expanded,
        onSettings = onSettings,
        onOpenSystem = {
            runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                .onFailure { scope.launch { showMessage(failed) } }
        },
        activating = activating,
        onActivate = {
            val id = state.selectedId
            if (id != null && !activating) {
                activating = true
                scope.launch {
                    try { application.container.configRepository.setActive(id); showMessage(activated) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { showMessage(failed) }
                    finally { activating = false }
                }
            }
        },
        onEdit = { state.selectedId?.let(onEdit) },
    )
}

private enum class PlaygroundPicker { Provider, Voice }

@Composable
internal fun PlaygroundScreen(
    state: PlaygroundUiState,
    voices: List<TtsVoice>,
    providerNames: Map<String, String>,
    sampleText: String,
    contentPadding: PaddingValues,
    onManageProviders: () -> Unit,
    onProviderChange: (ProviderConfigId) -> Unit,
    onVoiceChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onClear: () -> Unit,
    onRestore: () -> Unit,
    onSpeak: () -> Unit,
    onStop: () -> Unit,
    expanded: Boolean = false,
    system: SystemTtsState = SystemTtsState(),
    onSettings: () -> Unit = {},
    onOpenSystem: () -> Unit = {},
    onActivate: () -> Unit = {},
    onEdit: () -> Unit = {},
    activating: Boolean = false,
) {
    var picker by rememberSaveable { mutableStateOf<PlaygroundPicker?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val selected = state.configs.firstOrNull { it.id == state.selectedId }
    val voice = voices.firstOrNull { it.id == state.selectedVoiceId }
    val isDesign = selected?.providerId == "mimo-voicedesign"
    val isClone = selected?.providerId == "mimo-voiceclone"
    val temporary = selected != null && (selected.id != state.activeId || state.selectedVoiceId != selected.defaultVoiceId)
    fun dismissKeyboard() { keyboard?.hide(); focusManager.clearFocus() }

    val summary: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.current_plan), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            Text("·", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
                            Text(stringResource(if (temporary) R.string.temporary_listening else R.string.playground_system_reading),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                        }
                        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.listen_edit_plan),
                                modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Text(selected?.displayName.orEmpty(), style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(R.drawable.ic_waveform), null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f))
                        Text(when {
                            isDesign -> stringResource(R.string.plan_design)
                            isClone -> stringResource(R.string.plan_clone)
                            else -> stringResource(R.string.builtin_voice_summary, voice?.displayName.orEmpty())
                        }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                        if (selected?.id != state.activeId) {
                            FilledTonalButton(
                                onClick = { dismissKeyboard(); onActivate() },
                                enabled = !activating,
                                modifier = Modifier.heightIn(min = 40.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    contentColor = MaterialTheme.colorScheme.primary),
                            ) {
                                Text(stringResource(R.string.provider_activate),
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    FlowRow(Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(
                            onClick = { dismissKeyboard(); picker = PlaygroundPicker.Provider },
                            modifier = Modifier.heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.09f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        ) {
                            Icon(painterResource(R.drawable.ic_swap_horizontal), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.playground_select_config))
                        }
                        if (!isDesign && !isClone) {
                            TextButton(
                                onClick = { dismissKeyboard(); picker = PlaygroundPicker.Voice },
                                modifier = Modifier.heightIn(min = 44.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            ) {
                                Text(stringResource(R.string.playground_change_voice))
                                Spacer(Modifier.width(4.dp))
                                Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            if (isDesign) SecondaryText(stringResource(R.string.voicedesign_notice))
            if (!system.engineAvailable || !system.isDefaultEngine) {
                Surface(onClick = onOpenSystem, color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(if (system.engineAvailable) R.string.system_setup_hint else R.string.status_engine_unavailable_title),
                            style = MaterialTheme.typography.titleSmall)
                        SecondaryText(stringResource(if (system.engineAvailable) R.string.system_setup_detail else R.string.status_unregistered_hint))
                        Text(stringResource(R.string.system_setup_action), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding).imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val wide = expanded || maxWidth >= 840.dp
        val compactHeight = maxHeight < 300.dp
        val compactHeader = maxHeight < 400.dp
        val editorHeight = if (wide) (maxHeight - 220.dp).coerceAtLeast(260.dp) else 260.dp
        Column(Modifier.widthIn(max = 1200.dp).fillMaxSize()) {
            if (!compactHeight) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TopLevelPageHeader(
                        title = stringResource(R.string.playground_title),
                        compact = compactHeader,
                        actions = {
                            TextButton(onClick = onSettings) {
                                Text(stringResource(when {
                                    !system.engineAvailable -> R.string.system_unavailable
                                    system.isDefaultEngine -> R.string.system_connected
                                    else -> R.string.system_needs_setup
                                }))
                            }
                        },
                    )
                }
            }
            if (state.configs.isEmpty()) {
                PlaygroundEmptyState(onManageProviders, Modifier.weight(1f))
            } else {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (wide) {
                        Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 20.dp)) {
                            summary()
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                            if (!wide) { summary(); Spacer(Modifier.height(16.dp)) }
                            PlaygroundTextEditor(state.text, sampleText, editorHeight, onTextChange, onClear, onRestore)
                            Spacer(Modifier.height(16.dp))
                        }
                        PlaygroundPlaybackControls(state, selected != null && voice != null && state.text.isNotBlank(),
                            onSpeak = { dismissKeyboard(); onSpeak() }, onStop = onStop)
                    }
                }
            }
        }
    }

    if (picker != null && state.configs.isNotEmpty()) {
        val selectingProvider = picker == PlaygroundPicker.Provider
        SelectionSheet(
            title = stringResource(if (selectingProvider) R.string.playground_select_config else R.string.playground_select_voice),
            description = if (selectingProvider) stringResource(R.string.temporary_provider_hint) else null,
            selectedId = if (selectingProvider) state.selectedId?.value else state.selectedVoiceId,
            options = if (selectingProvider) state.configs.map { record ->
                val name = providerNames[record.providerId].orEmpty()
                SelectionOption(
                    id = record.id.value,
                    label = record.displayName,
                    detail = if (record.id == state.activeId) {
                        stringResource(R.string.playground_active_config, name)
                    } else name,
                )
            } else voices.map { SelectionOption(it.id, it.displayName) },
            onDismiss = { picker = null },
            onSelect = {
                if (selectingProvider) onProviderChange(ProviderConfigId(it)) else onVoiceChange(it)
            },
        )
    }
}

@Composable
private fun PlaygroundTextEditor(
    text: String,
    sampleText: String,
    minHeight: androidx.compose.ui.unit.Dp,
    onTextChange: (String) -> Unit,
    onClear: () -> Unit,
    onRestore: () -> Unit,
) {
    val label = stringResource(R.string.playground_text)
    val density = LocalDensity.current
    var headerHeight by remember { mutableIntStateOf(0) }
    Row(Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onRestore, enabled = text != sampleText) {
            Text(stringResource(R.string.playground_sample))
        }
        TextButton(onClick = onClear, enabled = text.isNotEmpty()) { Text(stringResource(R.string.clear)) }
    }
    TextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = (minHeight - with(density) { headerHeight.toDp() }).coerceAtLeast(200.dp)).semantics { contentDescription = label },
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text(stringResource(R.string.playground_text_placeholder)) },
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun PlaygroundPlaybackControls(
    state: PlaygroundUiState,
    canSpeak: Boolean,
    onSpeak: () -> Unit,
    onStop: () -> Unit,
) {
    val busy = state.phase != PlaygroundPhase.IDLE
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 24.dp).semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center,
        ) {
            if (state.lastFailure != null) {
                Text(
                    stringResource(R.string.playground_failed, problemTitle(state.lastFailure)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (state.completed && !busy) {
                Text(stringResource(R.string.listen_done), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.phase == PlaygroundPhase.CONNECTING) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        stringResource(if (state.phase == PlaygroundPhase.CONNECTING) R.string.playground_connecting else R.string.playground_playing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Button(
            onClick = if (busy) onStop else onSpeak,
            enabled = busy || canSpeak,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            colors = if (busy) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) else ButtonDefaults.buttonColors(),
        ) {
            Icon(
                painterResource(if (busy) R.drawable.ic_stop else R.drawable.ic_play),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(when {
                state.phase == PlaygroundPhase.CONNECTING -> R.string.cancel
                state.phase == PlaygroundPhase.PLAYING -> R.string.stop
                state.lastFailure != null -> R.string.playground_retry
                else -> R.string.playground_listen
            }))
        }
    }
}

@Composable
private fun PlaygroundEmptyState(onManageProviders: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Icon(
                painterResource(R.drawable.ic_reading),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(20.dp).size(32.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.no_provider_for_playground), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.no_provider_for_playground_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onManageProviders, modifier = Modifier.heightIn(min = 52.dp)) {
            Text(stringResource(R.string.playground_add_config))
        }
    }
}
