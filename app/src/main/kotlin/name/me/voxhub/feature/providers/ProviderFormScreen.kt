package name.me.voxhub.feature.providers

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.sensitiveContent
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import name.me.voxhub.R
import name.me.voxhub.VoxHubApplication
import name.me.voxhub.domain.model.ProviderConfigId
import name.me.voxhub.domain.model.ProviderTestState
import name.me.voxhub.provider.mimo.protocol.MIMO_API_ENDPOINT
import name.me.voxhub.ui.components.*
import name.me.voxhub.ui.viewModelFactory
import name.me.voxhub.ui.rememberLocalNetworkAction

@Composable
fun ProviderFormRoute(
    providerId: String,
    configId: ProviderConfigId?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSavedNew: (ProviderConfigId, Boolean) -> Unit,
    showMessage: suspend (String) -> Unit,
) {
    val context = LocalContext.current
    val resources by rememberUpdatedState(LocalResources.current)
    val container = (context.applicationContext as VoxHubApplication).container
    val viewModel: ProviderFormViewModel = viewModel(
        key = "plan-$providerId-${configId?.value ?: "new"}",
        factory = viewModelFactory { container.createProviderFormViewModel(providerId, configId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appState by container.configRepository.state.collectAsStateWithLifecycle(container.configRepository.currentState)
    val lifecycleOwner = LocalLifecycleOwner.current
    val withNetworkAccess = rememberLocalNetworkAction(showMessage)

    LaunchedEffect(viewModel.events, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is ProviderFormEvent.SavedNew -> onSavedNew(event.id, event.activated)
                    ProviderFormEvent.Deleted -> {
                        onBack()
                        showMessage(resources.getString(R.string.provider_deleted))
                    }
                    is ProviderFormEvent.Message -> showMessage(resources.getString(event.stringRes))
                }
            }
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importReference)
    }
    LifecycleStartEffect(viewModel) {
        onStopOrDispose { viewModel.stopPreview() }
    }

    ProviderFormScreen(
        state = state,
        providerName = viewModel.descriptor.displayName,
        voices = viewModel.voices,
        contentPadding = contentPadding,
        onBack = onBack,
        onDisplayNameChange = viewModel::setDisplayName,
        onApiKeyChange = viewModel::setApiKey,
        onEndpointChange = viewModel::setEndpoint,
        onPromptChange = viewModel::setVoiceDesignPrompt,
        onStyleChange = viewModel::setStyleInstruction,
        onPickReference = { audioPicker.launch(arrayOf("audio/*")) },
        onPreviewReference = viewModel::previewReference,
        onRevealChange = viewModel::setRevealApiKey,
        onVoiceChange = viewModel::setDefaultVoice,
        onTest = { withNetworkAccess(state.endpoint, viewModel::test) },
        onSave = { viewModel.save() },
        onSaveActivate = { viewModel.save(activate = true) },
        isCreation = configId == null,
        onDelete = viewModel::delete,
        isActive = state.savedRecord?.let { it.id == appState.activeProviderConfigId } == true,
    )
}

@Composable
private fun ProviderFormScreen(
    state: ProviderFormUiState,
    providerName: String,
    voices: List<name.me.voxhub.core.tts.TtsVoice>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onEndpointChange: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onStyleChange: (String) -> Unit,
    onPickReference: () -> Unit,
    onPreviewReference: () -> Unit,
    onRevealChange: (Boolean) -> Unit,
    onVoiceChange: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onSaveActivate: () -> Unit,
    isCreation: Boolean,
    onDelete: () -> Unit,
    isActive: Boolean,
) {
    var discardAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var selector by rememberSaveable { mutableStateOf<String?>(null) }
    val enabled = !state.isSaving && !state.isTesting
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun action(block: () -> Unit) { keyboard?.hide(); focus.clearFocus(); block() }
    val back = {
        if (enabled) {
            if (state.isDirty) { discardAction = onBack; confirmDiscard = true } else onBack()
        }
    }
    BackHandler(onBack = back)
    ProviderPage(
        title = stringResource(if (isCreation) R.string.provider_new_title else R.string.provider_edit_title),
        contentPadding = contentPadding,
        onBack = back,
        smallTopAppBar = true,
        header = {
            Row(Modifier.widthIn(max = 720.dp).fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)) {
                Surface(shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                    Text(providerName, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        actions = {
            if (!isCreation && state.savedRecord != null) {
                TextButton(onClick = { action { confirmDelete = true } }, enabled = !isActive && enabled) {
                    Text(stringResource(R.string.delete), color = if (!isActive && enabled) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        footer = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isCreation) {
                        Button(onClick = { action(onSaveActivate) }, enabled = state.isValid && enabled,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(when {
                                state.isSaving -> R.string.saving
                                state.activationFailed -> R.string.plan_retry_activate
                                else -> R.string.plan_save_activate
                            }))
                        }
                        TextButton(onClick = { action(onSave) }, enabled = state.isValid && enabled,
                            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.plan_save_only)) }
                    } else {
                        Button(onClick = { action(onSave) }, enabled = state.isValid && enabled,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(if (state.isSaving) R.string.saving else R.string.save))
                        }
                    }
                }
            }
        },
    ) { compact ->
        Column(
            Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
        ) {
            OutlinedTextField(
                value = state.displayName, onValueChange = onDisplayNameChange,
                modifier = Modifier.fillMaxWidth(), enabled = enabled,
                label = { Text(stringResource(R.string.config_name)) },
                supportingText = { if (state.displayName.isBlank()) Text(stringResource(R.string.plan_required_name)) },
                placeholder = { Text(stringResource(R.string.config_name_hint)) }, singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            if (isActive) SecondaryText(stringResource(R.string.provider_active_edit_hint))
            if (state.activationFailed) {
                Text(stringResource(R.string.plan_activation_failed), color = MaterialTheme.colorScheme.error)
            }
            ProviderFormSection(stringResource(R.string.provider_voice_section)) {
                when (val fields = state.fields) {
                    is ProviderFormFields.Mimo -> SelectionRow(
                        stringResource(R.string.default_voice_label),
                        voices.firstOrNull { it.id == fields.defaultVoiceId }?.displayName.orEmpty(),
                        stringResource(R.string.provider_default_voice_hint),
                        onClick = { action { selector = "voice" } }, enabled = enabled,
                    )
                    is ProviderFormFields.VoiceDesign -> {
                        val templates = voiceDesignPromptTemplates.map { stringResource(it.promptRes) to stringResource(it.summaryRes) }
                        SelectionRow(stringResource(R.string.voice_design_template),
                            templates.firstOrNull { it.first == fields.prompt }?.second ?: stringResource(R.string.voice_design_template_choose),
                            null, onClick = { action { selector = "template" } }, enabled = enabled)
                        OutlinedTextField(
                            value = fields.prompt, onValueChange = onPromptChange,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).sensitiveContent(), enabled = enabled,
                            label = { Text(stringResource(R.string.voice_design_prompt)) },
                            supportingText = { if (fields.prompt.isBlank()) Text(stringResource(R.string.plan_required_prompt)) },
                            placeholder = { Text(stringResource(R.string.voice_design_prompt_hint)) },
                            shape = MaterialTheme.shapes.medium,
                        )
                        SecondaryText(stringResource(R.string.voicedesign_notice))
                    }
                    is ProviderFormFields.VoiceClone -> {
                        Text(stringResource(R.string.reference_audio), style = MaterialTheme.typography.titleSmall)
                        Text(fields.referenceAudioName.ifBlank { stringResource(R.string.reference_not_selected) },
                            modifier = Modifier.sensitiveContent())
                        fields.referenceAudio?.let { SecondaryText(stringResource(R.string.reference_audio_details, it.mimeType, it.size)) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { action(onPickReference) }, enabled = !fields.isImporting && enabled) {
                                Text(stringResource(if (fields.isImporting) R.string.reference_loading else R.string.reference_choose))
                            }
                            OutlinedButton(onClick = onPreviewReference,
                                enabled = fields.referenceAudio != null && !fields.isImporting && enabled) {
                                Text(stringResource(if (fields.isPreviewing) R.string.reference_stop else R.string.reference_preview))
                            }
                        }
                        SecondaryText(stringResource(R.string.reference_formats))
                        if (fields.referenceAudio == null) SecondaryText(stringResource(R.string.plan_required_audio))
                        OutlinedTextField(
                            value = fields.styleInstruction, onValueChange = onStyleChange,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).sensitiveContent(), enabled = enabled,
                            label = { Text(stringResource(R.string.clone_style_instruction)) },
                            shape = MaterialTheme.shapes.medium,
                        )
                    }
                }
            }
            ProviderFormSection(stringResource(R.string.provider_connection_section)) {
                OutlinedTextField(
                    value = state.apiKey, onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth().sensitiveContent(), enabled = enabled,
                    label = { Text(stringResource(R.string.api_key)) }, singleLine = true,
                    supportingText = { if (state.apiKey.isBlank()) Text(stringResource(R.string.plan_required_key)) },
                    visualTransformation = if (state.revealApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { onRevealChange(!state.revealApiKey) }) {
                            Text(stringResource(if (state.revealApiKey) R.string.provider_hide_key else R.string.provider_show_key))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    shape = MaterialTheme.shapes.medium,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { action(onTest) }, enabled = state.isValid && enabled) {
                    if (state.isTesting) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(stringResource(if (state.isTesting) R.string.testing else R.string.provider_test_connection))
                }
                TestStateBadge(state.testState)
                when (val tested = state.testState) {
                    is ProviderTestState.Success -> SecondaryText(stringResource(R.string.plan_test_time, localDateTime(tested.testedAtEpochMillis)))
                    is ProviderTestState.Failure -> {
                        SecondaryText(stringResource(R.string.plan_test_time, localDateTime(tested.testedAtEpochMillis)))
                    }
                    else -> Unit
                }
                SecondaryText(stringResource(R.string.provider_test_notice))
                if (!state.isValid) SecondaryText(stringResource(R.string.complete_required_fields))
            }
            if (isActive) SecondaryText(stringResource(R.string.provider_active_delete_hint))
            SelectionRow(stringResource(R.string.plan_advanced),
                stringResource(when {
                    !state.isEndpointValid -> R.string.api_endpoint_invalid
                    state.endpoint == MIMO_API_ENDPOINT -> R.string.provider_official_endpoint
                    else -> R.string.provider_custom_endpoint
                }),
                null, onClick = { action { showAdvanced = !showAdvanced } }, enabled = enabled, expanded = showAdvanced)
            if (showAdvanced) {
                OutlinedTextField(
                    value = state.endpoint, onValueChange = onEndpointChange,
                    modifier = Modifier.fillMaxWidth(), enabled = enabled,
                    label = { Text(stringResource(R.string.external_host)) },
                    supportingText = { Text(stringResource(if (state.isEndpointValid) R.string.api_endpoint_hint else R.string.api_endpoint_invalid)) },
                    isError = !state.isEndpointValid, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    shape = MaterialTheme.shapes.medium,
                )
            }
        }
    }
    if (selector == "voice") {
        SelectionSheet(stringResource(R.string.default_voice_label), stringResource(R.string.provider_default_voice_hint),
            (state.fields as? ProviderFormFields.Mimo)?.defaultVoiceId,
            voices.map { SelectionOption(it.id, it.displayName) },
            onDismiss = { selector = null }, onSelect = onVoiceChange)
    } else if (selector == "template") {
        val options = voiceDesignPromptTemplates.map { SelectionOption(stringResource(it.promptRes), stringResource(it.summaryRes)) }
        SelectionSheet(stringResource(R.string.voice_design_template), null,
            (state.fields as? ProviderFormFields.VoiceDesign)?.prompt, options,
            onDismiss = { selector = null }, onSelect = onPromptChange)
    }
    if (confirmDiscard) {
        AlertDialog(onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.provider_discard_title)) },
            text = { Text(stringResource(R.string.provider_discard_message)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; (discardAction ?: onBack)() }) { Text(stringResource(R.string.provider_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.provider_keep_editing)) } })
    }
    if (confirmDelete && state.savedRecord != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_provider_title, state.savedRecord.displayName)) },
            text = { Text(stringResource(R.string.delete_provider_message)) },
            confirmButton = {
                TextButton(enabled = !isActive && enabled, onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ProviderFormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title)
        content()
    }
}

@Composable
private fun TestStateBadge(state: ProviderTestState) {
    when (state) {
        ProviderTestState.NotTested -> StatusBadge(stringResource(R.string.current_form_not_tested), BadgeKind.Neutral)
        is ProviderTestState.Success -> StatusBadge(stringResource(R.string.current_form_test_succeeded), BadgeKind.Success)
        is ProviderTestState.Failure -> StatusBadge(stringResource(R.string.current_form_test_failed), BadgeKind.Error)
    }
}
