package name.me.voxhub.feature.providers

import name.me.voxhub.domain.model.ProviderConfigRecord
import name.me.voxhub.domain.model.ProviderTestState
import name.me.voxhub.provider.mimo.protocol.MIMO_API_ENDPOINT
import name.me.voxhub.provider.mimo.protocol.normalizeMimoEndpoint
import name.me.voxhub.provider.mimo.voiceclone.ReferenceAudio

data class ProviderFormUiState(
    val fields: ProviderFormFields,
    val displayName: String = "",
    val apiKey: String = "",
    val endpoint: String = MIMO_API_ENDPOINT,
    val testState: ProviderTestState = ProviderTestState.NotTested,
    val isTesting: Boolean = false,
    val isSaving: Boolean = false,
    val revealApiKey: Boolean = false,
    val isDirty: Boolean = false,
    val activationFailed: Boolean = false,
    val savedRecord: ProviderConfigRecord? = null,
) {
    val isValid: Boolean
        get() = displayName.isNotBlank() && apiKey.isNotBlank() && isEndpointValid && fields.isValid

    val isEndpointValid: Boolean
        get() = normalizeMimoEndpoint(endpoint) != null
}

sealed interface ProviderFormFields {
    val isValid: Boolean

    data class Mimo(val defaultVoiceId: String) : ProviderFormFields {
        override val isValid: Boolean get() = defaultVoiceId.isNotBlank()
    }

    data class VoiceDesign(val prompt: String = "") : ProviderFormFields {
        override val isValid: Boolean get() = prompt.isNotBlank()
    }

    data class VoiceClone(
        val referenceAudio: ReferenceAudio? = null,
        val referenceAudioName: String = "",
        val styleInstruction: String = "",
        val isImporting: Boolean = false,
        val isPreviewing: Boolean = false,
    ) : ProviderFormFields {
        override val isValid: Boolean get() = referenceAudio != null && !isImporting
    }
}
