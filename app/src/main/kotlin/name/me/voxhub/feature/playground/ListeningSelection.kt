package name.me.voxhub.feature.playground

import name.me.voxhub.domain.model.AppState

/** Follow system changes until the user explicitly previews another plan or voice. */
internal fun PlaygroundUiState.withPlans(
    app: AppState,
    availableVoices: (String) -> Set<String>,
): PlaygroundUiState {
    val previous = configs.firstOrNull { it.id == selectedId }
    val followingSystem = selectedId == null ||
        (selectedId == activeId && selectedVoiceId == previous?.defaultVoiceId)
    val selected = (if (followingSystem) app.activeConfig else null)
        ?: app.configs.firstOrNull { it.id == selectedId }
        ?: app.activeConfig
        ?: app.configs.firstOrNull()
    val voice = selectedVoiceId?.takeIf {
        previous?.id == selected?.id && previous?.defaultVoiceId == selected?.defaultVoiceId &&
            selected != null && it in availableVoices(selected.providerId)
    } ?: selected?.defaultVoiceId
    return copy(configs = app.configs, activeId = app.activeProviderConfigId,
        selectedId = selected?.id, selectedVoiceId = voice)
}
