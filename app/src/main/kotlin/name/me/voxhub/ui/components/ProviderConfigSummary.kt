package name.me.voxhub.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import name.me.voxhub.R
import name.me.voxhub.domain.model.ProviderConfigRecord
import name.me.voxhub.domain.provider.ProviderCatalog

@Composable
internal fun providerConfigSummary(record: ProviderConfigRecord, catalog: ProviderCatalog): String {
    val registration = catalog[record.providerId]
    val voice = when (record.providerId) {
        "mimo-voicedesign" -> stringResource(R.string.provider_voice_design)
        "mimo-voiceclone" -> stringResource(R.string.provider_voice_clone)
        else -> registration?.voices?.firstOrNull { it.id == record.defaultVoiceId }?.displayName.orEmpty()
    }
    return stringResource(R.string.provider_config_summary, registration?.descriptor?.displayName.orEmpty(), voice)
}
