package name.me.voxhub.domain.provider

import name.me.voxhub.core.tts.PcmEncoding
import name.me.voxhub.core.tts.TtsProvider
import name.me.voxhub.core.tts.TtsProviderDescriptor
import name.me.voxhub.core.tts.TtsVoice
import name.me.voxhub.domain.model.ProviderStateDefinition
import name.me.voxhub.provider.mimo.tts.MIMO_DEFAULT_VOICE_ID
import name.me.voxhub.provider.mimo.tts.MIMO_DESCRIPTOR
import name.me.voxhub.provider.mimo.tts.MIMO_VOICES
import name.me.voxhub.provider.mimo.tts.MimoProviderConfig
import name.me.voxhub.provider.mimo.tts.MimoProviderFactory

interface ProviderRegistration {
    val descriptor: TtsProviderDescriptor
    val voices: List<TtsVoice>
    val defaultVoiceId: String
}

class MimoProviderRegistration(
    private val factory: MimoProviderFactory,
) : ProviderRegistration {
    override val descriptor = MIMO_DESCRIPTOR
    override val voices = MIMO_VOICES
    override val defaultVoiceId = MIMO_DEFAULT_VOICE_ID

    internal fun createProvider(config: MimoProviderConfig): TtsProvider = factory.create(config)
}

class ProviderCatalog(registrations: List<ProviderRegistration>) {
    val registrations: List<ProviderRegistration> = registrations.toList()
    private val byId = registrations.associateBy { it.descriptor.id }

    val stateDefinitions: List<ProviderStateDefinition> = registrations.map {
        ProviderStateDefinition(it.descriptor.id, it.voices.mapTo(mutableSetOf()) { voice -> voice.id })
    }

    val locales = registrations.flatMap { it.voices }.mapTo(mutableSetOf()) { it.locale }

    init {
        require(byId.size == registrations.size) { "Provider ids must be unique" }
        val voiceIds = registrations.flatMap { it.voices }.map { it.id }
        require(voiceIds.size == voiceIds.toSet().size) { "Voice ids must be globally unique" }
        registrations.forEach { registration ->
            val descriptor = registration.descriptor
            require(descriptor.displayName == descriptor.displayName.trim()) {
                "Provider display name is invalid"
            }
            require(descriptor.outputFormat.encoding == PcmEncoding.S16_LE && descriptor.outputFormat.channelCount == 1) {
                "Provider output must be PCM16LE mono"
            }
            require(registration.voices.isNotEmpty()) { "Provider must declare at least one voice" }
            require(registration.voices.all { it.displayName == it.displayName.trim() }) {
                "Voice display name is invalid"
            }
            require(registration.defaultVoiceId in registration.voices.map { it.id }) {
                "Default voice must belong to its Provider"
            }
            require(registration.voices.all { voice ->
                val parts = voice.id.split('/')
                parts.size == 2 && parts[0] == descriptor.id && parts[1].isNotEmpty()
            }) { "Voice id must use exactly one Provider namespace segment" }
        }
    }

    operator fun get(providerId: String): ProviderRegistration? = byId[providerId]
}
