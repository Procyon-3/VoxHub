package name.me.voxhub.domain.model

import name.me.voxhub.core.tts.LocaleTag
import name.me.voxhub.core.tts.TtsProvider

data class ProviderSnapshot(
    val configId: ProviderConfigId,
    val providerId: String,
    val displayName: String,
    val provider: TtsProvider,
    val defaultVoiceId: String,
    val defaultLocale: LocaleTag,
    val supportedLocales: Set<LocaleTag>,
    val voiceIds: Set<String>,
)
