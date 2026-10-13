package name.me.voxhub.di

import android.app.Application
import timber.log.Timber
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import name.me.voxhub.data.audio.VoiceCloneAudioStore
import name.me.voxhub.data.database.UsageDatabase
import name.me.voxhub.data.repository.ProviderConfigRepository
import name.me.voxhub.data.repository.UsageRepository
import name.me.voxhub.domain.model.ProviderConfigId
import name.me.voxhub.domain.model.ProviderTestState
import name.me.voxhub.domain.provider.MimoProviderConfigService
import name.me.voxhub.domain.provider.MimoProviderRegistration
import name.me.voxhub.domain.provider.ProviderCatalog
import name.me.voxhub.domain.provider.SynthesisResolver
import name.me.voxhub.domain.repository.MimoConfigRepository
import name.me.voxhub.domain.usage.UsageRecorder
import name.me.voxhub.feature.playground.PlaygroundViewModel
import name.me.voxhub.feature.providers.ProviderFormFields
import name.me.voxhub.feature.providers.ProviderFormUiState
import name.me.voxhub.feature.providers.ProviderFormViewModel
import name.me.voxhub.feature.providers.ProviderViewModel
import name.me.voxhub.feature.status.StatusViewModel
import name.me.voxhub.platform.audio.PlaybackAudioSink
import name.me.voxhub.platform.security.AndroidProviderCredentialCipher
import name.me.voxhub.provider.mimo.tts.MIMO_DESCRIPTOR
import name.me.voxhub.provider.mimo.tts.MimoProviderFactory
import name.me.voxhub.provider.mimo.voiceclone.VOICECLONE_DESCRIPTOR
import name.me.voxhub.provider.mimo.voicedesign.VOICEDESIGN_DESCRIPTOR

enum class InitializationState { LOADING, READY, FAILED }

class VoxHubContainer(private val application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableInitialization = MutableStateFlow(InitializationState.LOADING)
    val initialization = mutableInitialization.asStateFlow()
    private val database = Room.databaseBuilder<UsageDatabase>(application, "request-usage.db")
        .setDriver(AndroidSQLiteDriver())
        .build()
    val usageRepository = UsageRepository(database)
    val usageRecorder = UsageRecorder(usageRepository)
    val referenceAudioImporter = name.me.voxhub.platform.audio.ReferenceAudioImporter(application)
    fun createReferenceAudioPlayer() = name.me.voxhub.platform.audio.ReferenceAudioPlayer(application)
    val configRepository = ProviderConfigRepository(application, applicationScope,
        AndroidProviderCredentialCipher(), VoiceCloneAudioStore(application))
    val mimoConfigRepository: MimoConfigRepository = configRepository
    val mimoProviderFactory = MimoProviderFactory(REQUEST_TIMEOUT_MILLIS)
    val voiceDesignProviderFactory = name.me.voxhub.provider.mimo.voicedesign.MimoVoiceDesignProviderFactory(REQUEST_TIMEOUT_MILLIS)
    val voiceCloneProviderFactory = name.me.voxhub.provider.mimo.voiceclone.MimoVoiceCloneProviderFactory(REQUEST_TIMEOUT_MILLIS)
    val providerCatalog = ProviderCatalog(
        buildList {
            add(MimoProviderRegistration(mimoProviderFactory))
            add(name.me.voxhub.domain.provider.VoiceDesignProviderRegistration(voiceDesignProviderFactory))
            add(name.me.voxhub.domain.provider.VoiceCloneProviderRegistration(voiceCloneProviderFactory))
        },
    )

    init {
        applicationScope.launch {
            try {
                usageRepository.initialize()
                configRepository.initialize(providerCatalog.stateDefinitions)
                mutableInitialization.value = InitializationState.READY
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Timber.e(failure, "local_data_initialization_failed")
                mutableInitialization.value = InitializationState.FAILED
            }
        }
    }

    val mimoConfigService = MimoProviderConfigService(mimoConfigRepository, mimoProviderFactory, usageRecorder)
    val voiceDesignConfigService = name.me.voxhub.domain.provider.VoiceDesignProviderConfigService(configRepository, voiceDesignProviderFactory, usageRecorder)
    val voiceCloneConfigService = name.me.voxhub.domain.provider.VoiceCloneProviderConfigService(configRepository, voiceCloneProviderFactory, usageRecorder)
    val synthesisResolver = SynthesisResolver(configRepository, providerCatalog)

    fun createStatusViewModel() = StatusViewModel(application, configRepository)

    fun createProviderViewModel() = ProviderViewModel(
        configRepository,
    )

    fun createPlaygroundViewModel(sampleText: String) = PlaygroundViewModel(
        configRepository, providerCatalog, synthesisResolver, usageRecorder,
        { onPlaying -> PlaybackAudioSink(application, onPlaying) }, sampleText,
    )

    fun createProviderFormViewModel(providerId: String, configId: ProviderConfigId?): ProviderFormViewModel {
        val registration = requireNotNull(providerCatalog[providerId])
        val record = configId?.let { id ->
            requireNotNull(configRepository.currentState.configs.firstOrNull { it.id == id && it.providerId == providerId })
        }
        val initial = when (providerId) {
            MIMO_DESCRIPTOR.id -> {
                val config = configId?.let(mimoConfigService::getConfig)
                ProviderFormUiState(
                    fields = ProviderFormFields.Mimo(record?.defaultVoiceId ?: registration.defaultVoiceId),
                    apiKey = config?.apiKey.orEmpty(),
                    endpoint = config?.endpoint ?: name.me.voxhub.provider.mimo.protocol.MIMO_API_ENDPOINT,
                )
            }
            VOICEDESIGN_DESCRIPTOR.id -> {
                val config = configId?.let(voiceDesignConfigService::getConfig)
                ProviderFormUiState(
                    fields = ProviderFormFields.VoiceDesign(config?.voiceDesignPrompt.orEmpty()),
                    apiKey = config?.apiKey.orEmpty(),
                    endpoint = config?.endpoint ?: name.me.voxhub.provider.mimo.protocol.MIMO_API_ENDPOINT,
                )
            }
            VOICECLONE_DESCRIPTOR.id -> {
                val config = configId?.let(voiceCloneConfigService::getStored)?.config
                ProviderFormUiState(
                    fields = ProviderFormFields.VoiceClone(styleInstruction = config?.styleInstruction.orEmpty()),
                    apiKey = config?.apiKey.orEmpty(),
                    endpoint = config?.endpoint ?: name.me.voxhub.provider.mimo.protocol.MIMO_API_ENDPOINT,
                )
            }
            else -> error("Unsupported Provider form")
        }.copy(
            displayName = record?.displayName.orEmpty(),
            testState = record?.testState ?: ProviderTestState.NotTested,
            savedRecord = record,
        )
        return ProviderFormViewModel(
            initial, registration, mimoConfigService, voiceDesignConfigService, voiceCloneConfigService,
            referenceAudioImporter::import, ::createReferenceAudioPlayer,
            { id -> configRepository.setActive(id) },
        )
    }

    suspend fun awaitInitialization(): Boolean =
        initialization.first { it != InitializationState.LOADING } == InitializationState.READY

    private companion object {
        const val REQUEST_TIMEOUT_MILLIS = 60_000L
    }
}
