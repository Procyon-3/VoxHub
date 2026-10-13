package name.me.voxhub.feature.playground

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import name.me.voxhub.core.tts.TtsFailure
import name.me.voxhub.core.tts.TtsVoice
import name.me.voxhub.data.repository.ProviderConfigRepository
import name.me.voxhub.domain.model.ProviderConfigId
import name.me.voxhub.domain.model.ProviderConfigRecord
import name.me.voxhub.domain.model.UsageAttribution
import name.me.voxhub.domain.model.UsageSource
import timber.log.Timber
import name.me.voxhub.domain.provider.ProviderCatalog
import name.me.voxhub.domain.provider.SynthesisResolver
import name.me.voxhub.domain.usage.UsageRecorder
import name.me.voxhub.platform.audio.PlaybackAudioSink

enum class PlaygroundPhase {
    IDLE,
    CONNECTING,
    PLAYING,
}

data class PlaygroundUiState(
    val configs: List<ProviderConfigRecord> = emptyList(),
    val activeId: ProviderConfigId? = null,
    val selectedId: ProviderConfigId? = null,
    val selectedVoiceId: String? = null,
    val text: String = "",
    val phase: PlaygroundPhase = PlaygroundPhase.IDLE,
    val lastFailure: TtsFailure? = null,
    val completed: Boolean = false,
)

class PlaygroundViewModel(
    private val stateRepository: ProviderConfigRepository,
    private val providerCatalog: ProviderCatalog,
    private val synthesisResolver: SynthesisResolver,
    private val usageRecorder: UsageRecorder,
    private val createPlaybackSink: (() -> Unit) -> PlaybackAudioSink,
    private val sampleText: String,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PlaygroundUiState(text = sampleText))
    val uiState = mutableUiState.asStateFlow()
    private var playbackJob: Job? = null
    private var playbackSink: PlaybackAudioSink? = null
    private var playbackGeneration = 0L

    init {
        viewModelScope.launch {
            stateRepository.state.collect { appState ->
                val previous = mutableUiState.value
                val updated = previous.withPlans(appState) { providerId ->
                    providerCatalog[providerId]?.voices.orEmpty().map { it.id }.toSet()
                }
                val oldRecord = previous.configs.firstOrNull { it.id == previous.selectedId }
                val newRecord = updated.configs.firstOrNull { it.id == updated.selectedId }
                if (previous.selectedId != updated.selectedId || oldRecord != newRecord) {
                    stop()
                    mutableUiState.value = updated.copy(phase = PlaygroundPhase.IDLE, lastFailure = null, completed = false)
                } else mutableUiState.value = updated
            }
        }
    }

    fun selectProvider(id: ProviderConfigId) {
        stop()
        val record = mutableUiState.value.configs.firstOrNull { it.id == id } ?: return
        mutableUiState.value = mutableUiState.value.copy(
            selectedId = id,
            selectedVoiceId = record.defaultVoiceId,
            lastFailure = null, completed = false,
        )
    }

    fun selectVoice(voiceId: String) {
        if (voicesForSelected().none { it.id == voiceId }) return
        stop()
        mutableUiState.value = mutableUiState.value.copy(selectedVoiceId = voiceId, lastFailure = null, completed = false)
    }

    fun setText(value: String) {
        mutableUiState.value = mutableUiState.value.copy(text = value, lastFailure = null, completed = false)
    }

    fun restoreSample() = setText(sampleText)

    fun speak() {
        val state = mutableUiState.value
        val configId = state.selectedId ?: return
        val voice = voicesForSelected().firstOrNull { it.id == state.selectedVoiceId } ?: return
        if (state.text.isBlank()) return
        stop()
        val generation = playbackGeneration
        mutableUiState.value = mutableUiState.value.copy(phase = PlaygroundPhase.CONNECTING, lastFailure = null, completed = false)
        Timber.i("Playground synthesis started")
        playbackJob = viewModelScope.launch {
            val sink = createPlaybackSink {
                viewModelScope.launch {
                    if (playbackGeneration == generation) {
                        mutableUiState.value = mutableUiState.value.copy(phase = PlaygroundPhase.PLAYING)
                    }
                }
            }
            playbackSink = sink
            try {
                val record = state.configs.firstOrNull { it.id == configId }
                usageRecorder.record(
                    UsageAttribution(record?.providerId, configId.value, record?.displayName.orEmpty(), UsageSource.PLAYGROUND),
                    state.text, sink,
                ) { observed ->
                    val snapshot = synthesisResolver.resolve(configId)
                        ?: throw name.me.voxhub.core.tts.TtsException(TtsFailure.SERVICE,
                            name.me.voxhub.core.tts.TtsDiagnostic(stage = name.me.voxhub.core.tts.TtsStage.REQUEST_VALIDATION,
                                reasonCode = "PROVIDER_UNAVAILABLE"))
                    name.me.voxhub.core.tts.TtsEngine(snapshot.provider).synthesize(
                        name.me.voxhub.core.tts.TtsRequest(state.text, voice.locale, voice.id), observed,
                    )
                }
                sink.error?.let { error ->
                    Timber.e("playground failed failure=%s diagnostic=%s", error.failure, error.diagnostic)
                    if (playbackGeneration == generation) {
                        mutableUiState.value = mutableUiState.value.copy(lastFailure = error.failure)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = (error as? name.me.voxhub.core.tts.TtsException)?.error
                    ?: name.me.voxhub.core.tts.TtsError(TtsFailure.SERVICE,
                        name.me.voxhub.core.tts.TtsDiagnostic(stage = name.me.voxhub.core.tts.TtsStage.REQUEST_VALIDATION,
                            reasonCode = "CONFIGURATION_READ_FAILED"))
                sink.stopNow()
                Timber.e(error, "playground failed failure=%s diagnostic=%s", failure.failure, failure.diagnostic)
                if (playbackGeneration == generation) {
                    mutableUiState.value = mutableUiState.value.copy(lastFailure = failure.failure)
                }
            } finally {
                sink.stopNow()
                if (playbackGeneration == generation) {
                    playbackSink = null
                    playbackJob = null
                    mutableUiState.value = mutableUiState.value.copy(phase = PlaygroundPhase.IDLE, completed = mutableUiState.value.lastFailure == null)
                }
            }
        }
    }

    fun stop() {
        playbackGeneration++
        playbackJob?.cancel()
        playbackSink?.stopNow()
        playbackSink = null
        playbackJob = null
        mutableUiState.value = mutableUiState.value.copy(phase = PlaygroundPhase.IDLE, completed = false)
    }

    fun voicesForSelected(): List<TtsVoice> {
        val record = mutableUiState.value.configs.firstOrNull { it.id == mutableUiState.value.selectedId }
            ?: return emptyList()
        return providerCatalog[record.providerId]?.voices.orEmpty()
    }

    override fun onCleared() {
        stop()
    }
}
