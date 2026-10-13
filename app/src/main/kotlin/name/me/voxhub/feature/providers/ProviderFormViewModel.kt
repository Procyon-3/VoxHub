package name.me.voxhub.feature.providers

import android.net.Uri
import timber.log.Timber
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import name.me.voxhub.R
import name.me.voxhub.domain.model.ProviderTestState
import name.me.voxhub.domain.provider.MimoProviderConfigService
import name.me.voxhub.domain.provider.MimoProviderFormData
import name.me.voxhub.domain.provider.ProviderRegistration
import name.me.voxhub.domain.provider.VoiceCloneProviderConfigService
import name.me.voxhub.domain.provider.VoiceCloneProviderFormData
import name.me.voxhub.domain.provider.VoiceDesignProviderConfigService
import name.me.voxhub.domain.provider.VoiceDesignProviderFormData
import name.me.voxhub.platform.audio.ImportedReferenceAudio
import name.me.voxhub.platform.audio.ReferenceAudioPlayer

sealed interface ProviderFormEvent {
    data class SavedNew(val id: name.me.voxhub.domain.model.ProviderConfigId, val activated: Boolean) : ProviderFormEvent
    data object Deleted : ProviderFormEvent
    data class Message(val stringRes: Int) : ProviderFormEvent
}

class ProviderFormViewModel(
    initialState: ProviderFormUiState,
    registration: ProviderRegistration,
    private val mimoConfigService: MimoProviderConfigService,
    private val voiceDesignConfigService: VoiceDesignProviderConfigService,
    private val voiceCloneConfigService: VoiceCloneProviderConfigService,
    private val importReferenceAudio: suspend (Uri) -> ImportedReferenceAudio,
    createReferencePlayer: () -> ReferenceAudioPlayer,
    private val activateConfig: suspend (name.me.voxhub.domain.model.ProviderConfigId) -> Unit,
) : ViewModel() {
    private val isCreation = initialState.savedRecord == null
    private val mutableUiState = MutableStateFlow(initialState)
    val uiState = mutableUiState.asStateFlow()
    val voices = registration.voices
    val descriptor = registration.descriptor

    private val eventChannel = Channel<ProviderFormEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val referencePlayer by lazy { createReferencePlayer() }

    private fun updateClone(transform: ProviderFormFields.VoiceClone.() -> ProviderFormFields.VoiceClone) {
        val state = mutableUiState.value
        val fields = state.fields as? ProviderFormFields.VoiceClone ?: return
        mutableUiState.value = state.copy(fields = fields.transform())
    }

    init {
        if (initialState.fields is ProviderFormFields.VoiceClone && initialState.savedRecord != null) {
            val configId = initialState.savedRecord.id
            updateClone { copy(isImporting = true) }
            viewModelScope.launch {
                try {
                    val snapshot = requireNotNull(voiceCloneConfigService.load(configId))
                    updateClone { copy(referenceAudio = snapshot.config.referenceAudio,
                        referenceAudioName = snapshot.stored.config.audioName) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    Timber.e(failure, "Reference audio load failed")
                    eventChannel.send(ProviderFormEvent.Message(R.string.reference_load_failed))
                }
                finally { updateClone { copy(isImporting = false) } }
            }
        }
    }

    fun importReference(uri: Uri) {
        val fields = mutableUiState.value.fields as? ProviderFormFields.VoiceClone ?: return
        if (fields.isImporting || mutableUiState.value.isSaving || mutableUiState.value.isTesting) return
        stopPreview()
        updateClone { copy(isImporting = true) }
        viewModelScope.launch {
            try {
                val imported = importReferenceAudio(uri)
                mutateClone { copy(referenceAudio = imported.audio, referenceAudioName = imported.displayName) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                Timber.e(failure, "Reference audio import failed")
                eventChannel.send(ProviderFormEvent.Message(R.string.reference_import_failed))
            }
            finally { updateClone { copy(isImporting = false) } }
        }
    }

    fun setStyleInstruction(value: String) = mutateClone { copy(styleInstruction = value) }

    fun previewReference() {
        val state = mutableUiState.value
        val fields = state.fields as? ProviderFormFields.VoiceClone ?: return
        if (fields.isPreviewing) { stopPreview(); return }
        if (fields.isImporting || state.isSaving || state.isTesting) return
        val audio = fields.referenceAudio ?: return
        updateClone { copy(isPreviewing = true) }
        referencePlayer.play(audio) { failed ->
            updateClone { copy(isPreviewing = false) }
            if (failed) {
                Timber.e("Reference audio preview failed")
                eventChannel.trySend(ProviderFormEvent.Message(R.string.reference_preview_failed))
            }
        }
    }

    fun stopPreview() {
        if (mutableUiState.value.fields is ProviderFormFields.VoiceClone) referencePlayer.stop()
    }

    override fun onCleared() { stopPreview() }

    fun setDisplayName(value: String) = mutate { copy(displayName = value) }
    fun setApiKey(value: String) = mutate { copy(apiKey = value) }
    fun setEndpoint(value: String) = mutate { copy(endpoint = value) }
    fun setVoiceDesignPrompt(value: String) = mutate {
        val design = fields as? ProviderFormFields.VoiceDesign ?: return@mutate this
        copy(fields = design.copy(prompt = value))
    }
    fun setDefaultVoice(value: String) = mutate {
        val mimo = fields as? ProviderFormFields.Mimo ?: return@mutate this
        copy(fields = mimo.copy(defaultVoiceId = value))
    }
    fun setRevealApiKey(value: Boolean) {
        mutableUiState.value = mutableUiState.value.copy(revealApiKey = value)
    }

    fun test() {
        stopPreview()
        val state = mutableUiState.value
        if (!state.isValid || state.isTesting || state.isSaving) {
            viewModelScope.launch {
                eventChannel.send(ProviderFormEvent.Message(R.string.complete_required_fields))
            }
            return
        }
        mutableUiState.value = mutableUiState.value.copy(isTesting = true)
        viewModelScope.launch {
            val result = try {
                when (val fields = state.fields) {
                    is ProviderFormFields.Mimo -> mimoConfigService.testDraft(state.toMimoFormData(fields))
                    is ProviderFormFields.VoiceDesign -> voiceDesignConfigService.testDraft(state.toDesignFormData(fields))
                    is ProviderFormFields.VoiceClone -> voiceCloneConfigService.testDraft(state.toCloneFormData(fields))
                }
            } finally {
                mutableUiState.value = mutableUiState.value.copy(isTesting = false)
            }
            mutableUiState.value = mutableUiState.value.copy(testState = result)
            eventChannel.send(
                ProviderFormEvent.Message(
                    if (result is ProviderTestState.Success) R.string.test_succeeded else R.string.test_failed,
                ),
            )
        }
    }

    fun save(activate: Boolean = false) {
        stopPreview()
        val state = mutableUiState.value
        if (!state.isValid || state.isSaving || state.isTesting) {
            viewModelScope.launch { eventChannel.send(ProviderFormEvent.Message(R.string.complete_required_fields)) }
            return
        }
        mutableUiState.value = state.copy(isSaving = true, activationFailed = false)
        viewModelScope.launch {
            runCatching {
                val existing = state.savedRecord
                when (val fields = state.fields) {
                    is ProviderFormFields.Mimo -> if (existing == null) {
                        mimoConfigService.create(state.toMimoFormData(fields), state.testState)
                    } else mimoConfigService.update(existing, state.toMimoFormData(fields), state.testState)
                    is ProviderFormFields.VoiceDesign -> if (existing == null) {
                        voiceDesignConfigService.create(state.toDesignFormData(fields), state.testState)
                    } else voiceDesignConfigService.update(existing, state.toDesignFormData(fields), state.testState)
                    is ProviderFormFields.VoiceClone -> voiceCloneConfigService.save(existing, state.toCloneFormData(fields), state.testState)
                }
            }.onSuccess { saved ->
                Timber.i("Provider configuration saved")
                mutableUiState.value = mutableUiState.value.copy(
                    isDirty = false,
                    savedRecord = saved,
                )
                if (activate) {
                    try { activateConfig(saved.id) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        Timber.e(failure, "Saved plan activation failed")
                        mutableUiState.value = mutableUiState.value.copy(isSaving = false, activationFailed = true)
                        return@launch
                    }
                }
                mutableUiState.value = mutableUiState.value.copy(isSaving = false)
                if (isCreation) eventChannel.send(ProviderFormEvent.SavedNew(saved.id, activate))
                else eventChannel.send(ProviderFormEvent.Message(R.string.settings_saved))
            }.onFailure {
                if (it is CancellationException) throw it
                Timber.e(it, "Provider configuration save failed")
                mutableUiState.value = mutableUiState.value.copy(isSaving = false)
                eventChannel.send(ProviderFormEvent.Message(R.string.operation_failed))
            }
        }
    }

    fun delete() {
        val state = mutableUiState.value
        val record = state.savedRecord ?: return
        if (state.isSaving || state.isTesting) return
        stopPreview()
        mutableUiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            try {
                when (state.fields) {
                    is ProviderFormFields.Mimo -> mimoConfigService.delete(record.id)
                    is ProviderFormFields.VoiceDesign -> voiceDesignConfigService.delete(record.id)
                    is ProviderFormFields.VoiceClone -> voiceCloneConfigService.delete(record.id)
                }
                eventChannel.send(ProviderFormEvent.Deleted)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Timber.e(failure, "Provider configuration delete failed")
                mutableUiState.value = mutableUiState.value.copy(isSaving = false)
                eventChannel.send(ProviderFormEvent.Message(R.string.operation_failed))
            }
        }
    }

    private fun mutate(transform: ProviderFormUiState.() -> ProviderFormUiState) {
        val previous = mutableUiState.value
        if (previous.isSaving || previous.isTesting) return
        val updated = previous.transform()
        if (updated != previous) {
            mutableUiState.value = updated.copy(
                isDirty = true,
                testState = ProviderTestState.NotTested,
            )
        }
    }

    private fun mutateClone(transform: ProviderFormFields.VoiceClone.() -> ProviderFormFields.VoiceClone) = mutate {
        val clone = fields as? ProviderFormFields.VoiceClone ?: return@mutate this
        copy(fields = clone.transform())
    }

    private fun ProviderFormUiState.toCloneFormData(fields: ProviderFormFields.VoiceClone) =
        VoiceCloneProviderFormData(displayName, apiKey, requireNotNull(fields.referenceAudio),
            fields.referenceAudioName, fields.styleInstruction, endpoint)

    private fun ProviderFormUiState.toDesignFormData(fields: ProviderFormFields.VoiceDesign) =
        VoiceDesignProviderFormData(displayName, apiKey, fields.prompt, endpoint)

    private fun ProviderFormUiState.toMimoFormData(fields: ProviderFormFields.Mimo) =
        MimoProviderFormData(displayName, apiKey, fields.defaultVoiceId, endpoint)
}
