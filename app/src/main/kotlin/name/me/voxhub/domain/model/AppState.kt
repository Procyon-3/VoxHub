package name.me.voxhub.domain.model


data class AppState(
    val configs: List<ProviderConfigRecord> = emptyList(),
    val activeProviderConfigId: ProviderConfigId? = null,
) {
    val activeConfig: ProviderConfigRecord?
        get() = configs.firstOrNull { it.id == activeProviderConfigId }
}
