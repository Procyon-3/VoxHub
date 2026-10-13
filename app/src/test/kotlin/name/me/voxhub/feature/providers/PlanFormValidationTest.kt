package name.me.voxhub.feature.providers

import name.me.voxhub.domain.model.ProviderTestState
import name.me.voxhub.domain.model.SanitizedReason
import name.me.voxhub.provider.mimo.voiceclone.ReferenceAudio
import org.junit.Assert.*
import org.junit.Test

class PlanFormValidationTest {
    private val valid = ProviderFormUiState(fields = ProviderFormFields.Mimo("voice"), displayName = "Reading", apiKey = "test-key")
    @Test fun configurationNameIsRequiredForSave() {
        assertFalse(valid.copy(displayName = "").isValid)
    }
    @Test fun verificationIsOptionalEvenAfterFailure() {
        assertTrue(valid.isValid)
        assertTrue(valid.copy(testState = ProviderTestState.Failure(0, SanitizedReason())).isValid)
    }
    @Test fun missingCredentialsOrInvalidEndpointCannotSave() {
        assertFalse(valid.copy(apiKey = " ").isValid)
        assertFalse(valid.copy(endpoint = "http://example.com").isValid)
        assertFalse(valid.copy(endpoint = "https://user:pass@example.com").isValid)
    }
    @Test fun designAndCloneRequireTheirInputs() {
        assertFalse(valid.copy(fields = ProviderFormFields.VoiceDesign(" ")).isValid)
        assertTrue(valid.copy(fields = ProviderFormFields.VoiceDesign("Warm voice")).isValid)
        assertFalse(valid.copy(fields = ProviderFormFields.VoiceClone()).isValid)
    }
    @Test fun cloneImportBlocksTestingAndSavingEvenWithPreviousAudio() {
        val clone = ProviderFormFields.VoiceClone(referenceAudio = ReferenceAudio(byteArrayOf(1), "audio/wav"))
        assertTrue(valid.copy(fields = clone).isValid)
        val importing = valid.copy(fields = clone.copy(isImporting = true))
        assertFalse(importing.isValid)
    }
}
