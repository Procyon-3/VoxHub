package name.me.voxhub.feature.playground

import name.me.voxhub.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ListeningSelectionTest {
    private val first = ProviderConfigRecord(ProviderConfigId("first"), "mimo", "First", "a", ProviderTestState.NotTested)
    private val second = first.copy(id = ProviderConfigId("second"), displayName = "Second", defaultVoiceId = "b")
    private fun state() = PlaygroundUiState(configs = listOf(first, second), activeId = first.id,
        selectedId = first.id, selectedVoiceId = "a", text = "Keep my text")
    private fun app(active: ProviderConfigId?, configs: List<ProviderConfigRecord> = listOf(first, second)) =
        AppState(configs = configs, activeProviderConfigId = active)

    @Test fun systemSwitchUpdatesDefaultListeningPlan() {
        val result = state().withPlans(app(second.id)) { setOf("a", "b") }
        assertEquals(second.id, result.selectedId)
        assertEquals("b", result.selectedVoiceId)
        assertEquals("Keep my text", result.text)
    }
    @Test fun temporaryPlanSurvivesRepositoryRefreshWithoutActivatingIt() {
        val preview = state().copy(selectedId = second.id, selectedVoiceId = "b")
        val result = preview.withPlans(app(first.id)) { setOf("a", "b") }
        assertEquals(second.id, result.selectedId)
        assertEquals(first.id, result.activeId)
    }
    @Test fun temporaryVoiceSurvivesSystemSwitch() {
        val result = state().copy(selectedVoiceId = "b").withPlans(app(second.id)) { setOf("a", "b") }
        assertEquals(first.id, result.selectedId)
        assertEquals("b", result.selectedVoiceId)
        assertEquals(second.id, result.activeId)
    }
    @Test fun deletingPreviewedPlanSelectsRemainingActivePlan() {
        val result = state().copy(selectedId = second.id, selectedVoiceId = "b")
            .withPlans(app(first.id, listOf(first))) { setOf("a", "b") }
        assertEquals(first.id, result.selectedId)
        assertEquals("a", result.selectedVoiceId)
    }
    @Test fun editingDefaultVoiceRefreshesSelection() {
        val result = state().withPlans(app(first.id, listOf(first.copy(defaultVoiceId = "b")))) { setOf("a", "b") }
        assertEquals("b", result.selectedVoiceId)
    }
    @Test fun noPlansClearsSelectionButPreservesText() {
        val result = state().withPlans(app(null, emptyList())) { emptySet() }
        assertNull(result.selectedId)
        assertNull(result.selectedVoiceId)
        assertEquals("Keep my text", result.text)
    }
}
