package name.me.voxhub.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import name.me.voxhub.core.tts.TtsDiagnostic
import name.me.voxhub.core.tts.TtsException
import name.me.voxhub.core.tts.TtsFailure
import name.me.voxhub.core.tts.TtsStage
import name.me.voxhub.data.audio.VoiceCloneAudioStore
import name.me.voxhub.data.datastore.ProviderConfigSerializer
import name.me.voxhub.data.datastore.ProviderSettings
import name.me.voxhub.data.datastore.StoredProviderConfig
import name.me.voxhub.data.datastore.StoredProviderConfigs
import name.me.voxhub.domain.model.AppState
import name.me.voxhub.domain.model.MimoConfigAggregate
import name.me.voxhub.domain.model.ProviderConfigAggregate
import name.me.voxhub.domain.model.ProviderConfigId
import name.me.voxhub.domain.model.ProviderConfigRecord
import name.me.voxhub.domain.model.ProviderStateDefinition
import name.me.voxhub.domain.model.ProviderTestState
import name.me.voxhub.domain.model.VoiceCloneConfigAggregate
import name.me.voxhub.domain.model.VoiceCloneStoredAggregate
import name.me.voxhub.domain.model.VoiceCloneStoredConfig
import name.me.voxhub.domain.model.VoiceDesignConfigAggregate
import name.me.voxhub.domain.repository.MimoConfigRepository
import name.me.voxhub.domain.repository.SnapshotConfigRepository
import name.me.voxhub.domain.repository.VoiceCloneConfigRepository
import name.me.voxhub.domain.repository.VoiceDesignConfigRepository
import name.me.voxhub.platform.security.ProviderCredentialCipher
import name.me.voxhub.provider.mimo.protocol.normalizeMimoEndpoint
import name.me.voxhub.provider.mimo.tts.MIMO_DESCRIPTOR
import name.me.voxhub.provider.mimo.tts.MimoProviderConfig
import name.me.voxhub.provider.mimo.voiceclone.MimoVoiceCloneProviderConfig
import name.me.voxhub.provider.mimo.voiceclone.VOICECLONE_DEFAULT_VOICE_ID
import name.me.voxhub.provider.mimo.voiceclone.VOICECLONE_DESCRIPTOR
import name.me.voxhub.provider.mimo.voicedesign.MimoVoiceDesignProviderConfig
import name.me.voxhub.provider.mimo.voicedesign.VOICEDESIGN_DEFAULT_VOICE_ID
import name.me.voxhub.provider.mimo.voicedesign.VOICEDESIGN_DESCRIPTOR

/** A single immutable configuration snapshot; audio is loaded only when requested. */
class ProviderConfigRepository(
    context: Context,
    scope: CoroutineScope,
    private val credentialCipher: ProviderCredentialCipher,
    private val audioStore: VoiceCloneAudioStore,
) : MimoConfigRepository, VoiceDesignConfigRepository, VoiceCloneConfigRepository, SnapshotConfigRepository {
    private val dataStore: DataStore<StoredProviderConfigs> = DataStoreFactory.create(
        serializer = ProviderConfigSerializer,
        scope = scope,
        produceFile = { File(context.filesDir, "provider-configs.json") },
    )
    private data class Entry(val stored: StoredProviderConfig, val apiKey: String) {
        val record get() = stored.record
    }
    private data class Snapshot(val entries: List<Entry> = emptyList(), val activeId: ProviderConfigId? = null) {
        val state get() = AppState(entries.map { it.record }, activeId)
        val persisted get() = StoredProviderConfigs(entries.map { it.stored }, activeId)
        fun entry(id: ProviderConfigId) = entries.firstOrNull { it.record.id == id }
    }
    private val mutex = Mutex()
    private val snapshot = MutableStateFlow(Snapshot())
    val state = snapshot.map { it.state }
    val currentState: AppState get() = snapshot.value.state
    fun getEndpoint(id: ProviderConfigId): String? = snapshot.value.entry(id)?.stored?.endpoint
    private var initialized = false

    suspend fun initialize(definitions: Collection<ProviderStateDefinition>) = mutex.withLock {
        check(!initialized)
        val stored = dataStore.data.first()
        val byId = definitions.associateBy { it.providerId }
        require(stored.configs.map { it.record.id }.distinct().size == stored.configs.size)
        val entries = stored.configs.map { config ->
            val record = config.record
            val definition = requireNotNull(byId[record.providerId]) { "Unknown provider" }
            require(record.displayName.isNotBlank() && record.defaultVoiceId in definition.voiceIds)
            require(normalizeMimoEndpoint(config.endpoint) == config.endpoint)
            when (val settings = config.settings) {
                ProviderSettings.Preset -> require(record.providerId == MIMO_DESCRIPTOR.id)
                is ProviderSettings.Design -> require(record.providerId == VOICEDESIGN_DESCRIPTOR.id && settings.prompt.isNotBlank())
                is ProviderSettings.Clone -> {
                    require(record.providerId == VOICECLONE_DESCRIPTOR.id)
                    require(UUID.fromString(settings.audioVersion).toString() == settings.audioVersion)
                    require(settings.mimeType in setOf("audio/wav", "audio/mpeg") && settings.audioSize in 1..7_500_000)
                }
            }
            Entry(config, requireNotNull(credentialCipher.decryptOrNull(config.encryptedApiKey)) { "Provider credential unavailable" })
        }
        require(stored.activeId == null || entries.any { it.record.id == stored.activeId })
        withContext(Dispatchers.IO) { audioStore.cleanExcept(entries.audioVersions()) }
        snapshot.value = Snapshot(entries, stored.activeId)
        initialized = true
    }

    override fun hasCompleteConfig(id: ProviderConfigId) = snapshot.value.entry(id) != null

    override fun getAggregate(id: ProviderConfigId): MimoConfigAggregate? {
        val entry = snapshot.value.entry(id) ?: return null
        if (entry.stored.settings != ProviderSettings.Preset) return null
        return MimoConfigAggregate(entry.record, MimoProviderConfig(entry.apiKey, entry.stored.endpoint))
    }

    override fun getDesignAggregate(id: ProviderConfigId): VoiceDesignConfigAggregate? {
        val entry = snapshot.value.entry(id) ?: return null
        val settings = entry.stored.settings as? ProviderSettings.Design ?: return null
        return VoiceDesignConfigAggregate(entry.record, MimoVoiceDesignProviderConfig(entry.apiKey, settings.prompt, entry.stored.endpoint))
    }

    override fun getCloneStored(id: ProviderConfigId): VoiceCloneStoredAggregate? {
        val entry = snapshot.value.entry(id) ?: return null
        return entry.cloneStored()
    }

    private fun Entry.cloneStored(): VoiceCloneStoredAggregate? {
        val settings = stored.settings as? ProviderSettings.Clone ?: return null
        return VoiceCloneStoredAggregate(record, VoiceCloneStoredConfig(apiKey, settings.audioVersion, settings.mimeType,
            settings.audioName, settings.audioSize, settings.styleInstruction, stored.endpoint))
    }

    override suspend fun getSnapshotAggregate(id: ProviderConfigId): ProviderConfigAggregate? = mutex.withLock {
        check(initialized)
        resolve(snapshot.value.entry(id) ?: return@withLock null)
    }

    private suspend fun resolve(entry: Entry): ProviderConfigAggregate = when (val settings = entry.stored.settings) {
        ProviderSettings.Preset -> MimoConfigAggregate(entry.record, MimoProviderConfig(entry.apiKey, entry.stored.endpoint))
        is ProviderSettings.Design -> VoiceDesignConfigAggregate(entry.record,
            MimoVoiceDesignProviderConfig(entry.apiKey, settings.prompt, entry.stored.endpoint))
        is ProviderSettings.Clone -> {
            val audio = try {
                withContext(Dispatchers.IO) { audioStore.read(settings.audioVersion, settings.mimeType) }.also {
                    check(it.size == settings.audioSize)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw TtsException(TtsFailure.SERVICE,
                    TtsDiagnostic(stage = TtsStage.REQUEST_VALIDATION, reasonCode = "REFERENCE_AUDIO_UNAVAILABLE"))
            }
            VoiceCloneConfigAggregate(entry.record,
                MimoVoiceCloneProviderConfig(entry.apiKey, audio, settings.styleInstruction, entry.stored.endpoint),
                requireNotNull(entry.cloneStored()))
        }
    }

    override suspend fun getCloneAggregate(id: ProviderConfigId) = getSnapshotAggregate(id) as? VoiceCloneConfigAggregate

    override suspend fun create(displayName: String, defaultVoiceId: String, testState: ProviderTestState,
        config: MimoProviderConfig): ProviderConfigRecord = mutex.withLock {
        val record = newRecord(MIMO_DESCRIPTOR.id, displayName, defaultVoiceId, testState)
        put(entry(record, config.apiKey, config.endpoint, ProviderSettings.Preset))
        record
    }

    override suspend fun update(record: ProviderConfigRecord, config: MimoProviderConfig) = mutex.withLock {
        require(getAggregate(record.id) != null)
        put(entry(record.copy(displayName = record.displayName.trim()), config.apiKey, config.endpoint, ProviderSettings.Preset))
    }

    override suspend fun createDesign(displayName: String, testState: ProviderTestState,
        config: MimoVoiceDesignProviderConfig): ProviderConfigRecord = mutex.withLock {
        require(config.voiceDesignPrompt.isNotBlank())
        val record = newRecord(VOICEDESIGN_DESCRIPTOR.id, displayName, VOICEDESIGN_DEFAULT_VOICE_ID, testState)
        put(entry(record, config.apiKey, config.endpoint, ProviderSettings.Design(config.voiceDesignPrompt)))
        record
    }

    override suspend fun updateDesign(record: ProviderConfigRecord, config: MimoVoiceDesignProviderConfig) = mutex.withLock {
        require(getDesignAggregate(record.id) != null && config.voiceDesignPrompt.isNotBlank())
        put(entry(record.copy(displayName = record.displayName.trim()), config.apiKey, config.endpoint,
            ProviderSettings.Design(config.voiceDesignPrompt)))
    }

    override suspend fun saveClone(existing: ProviderConfigRecord?, displayName: String, audioName: String,
        config: MimoVoiceCloneProviderConfig, testState: ProviderTestState): ProviderConfigRecord = mutex.withLock {
        check(initialized)
        if (existing != null) require(getCloneStored(existing.id) != null)
        val record = existing?.copy(displayName = displayName.trim(), defaultVoiceId = VOICECLONE_DEFAULT_VOICE_ID, testState = testState)
            ?: newRecord(VOICECLONE_DESCRIPTOR.id, displayName, VOICECLONE_DEFAULT_VOICE_ID, testState)
        val oldVersion = (snapshot.value.entry(record.id)?.stored?.settings as? ProviderSettings.Clone)?.audioVersion
        withContext(NonCancellable + Dispatchers.IO) {
            val version = audioStore.write(config.referenceAudio)
            try {
                put(entry(record, config.apiKey, config.endpoint, ProviderSettings.Clone(version,
                    config.referenceAudio.mimeType, audioName, config.referenceAudio.size, config.styleInstruction)))
            } catch (error: Exception) {
                audioStore.delete(version)
                throw error
            }
            oldVersion?.let(audioStore::delete)
        }
        record
    }

    suspend fun setActive(id: ProviderConfigId?) = mutex.withLock {
        require(id == null || snapshot.value.entry(id) != null)
        commit(snapshot.value.copy(activeId = id))
    }

    override suspend fun delete(id: ProviderConfigId) = mutex.withLock {
        val current = snapshot.value
        require(current.activeId != id) { "The active Provider config cannot be deleted" }
        val entry = requireNotNull(current.entry(id))
        withContext(NonCancellable + Dispatchers.IO) {
            commit(current.copy(entries = current.entries.filterNot { it.record.id == id }))
            (entry.stored.settings as? ProviderSettings.Clone)?.audioVersion?.let(audioStore::delete)
        }
        Unit
    }

    private fun newRecord(providerId: String, name: String, voiceId: String, state: ProviderTestState): ProviderConfigRecord {
        require(name.isNotBlank())
        return ProviderConfigRecord(ProviderConfigId(UUID.randomUUID().toString()), providerId, name.trim(), voiceId, state)
    }

    private suspend fun entry(record: ProviderConfigRecord, apiKey: String, endpoint: String, settings: ProviderSettings): Entry {
        require(record.displayName.isNotBlank())
        val expectedProvider = when (settings) {
            ProviderSettings.Preset -> MIMO_DESCRIPTOR.id
            is ProviderSettings.Design -> VOICEDESIGN_DESCRIPTOR.id
            is ProviderSettings.Clone -> VOICECLONE_DESCRIPTOR.id
        }
        require(record.providerId == expectedProvider)
        val key = apiKey.trim()
        val normalizedEndpoint = requireNotNull(normalizeMimoEndpoint(endpoint))
        val encryptedKey = withContext(Dispatchers.IO) { credentialCipher.encrypt(key) }
        return Entry(StoredProviderConfig(record, normalizedEndpoint, encryptedKey, settings), key)
    }

    private suspend fun put(entry: Entry) {
        val current = snapshot.value
        val entries = if (current.entry(entry.record.id) == null) current.entries + entry
            else current.entries.map { if (it.record.id == entry.record.id) entry else it }
        commit(current.copy(entries = entries))
    }

    private suspend fun commit(next: Snapshot) = withContext(NonCancellable) {
        check(initialized)
        dataStore.updateData { next.persisted }
        snapshot.value = next
    }

    private fun List<Entry>.audioVersions() = mapNotNull { (it.stored.settings as? ProviderSettings.Clone)?.audioVersion }.toSet()
}
