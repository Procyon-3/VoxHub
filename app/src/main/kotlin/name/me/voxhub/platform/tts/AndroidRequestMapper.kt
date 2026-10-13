package name.me.voxhub.platform.tts

import android.speech.tts.SynthesisRequest
import name.me.voxhub.core.tts.LocaleTag
import name.me.voxhub.core.tts.TtsRequest

internal fun SynthesisRequest.toCoreRequest(
    currentLocale: LocaleTag,
    supportedLocales: Set<LocaleTag>,
    currentVoiceId: String,
    knownVoiceIds: Set<String>,
    reportUnknownVoice: (String) -> Unit,
): TtsRequest {
    // Current providers do not support Android speech rate or pitch adjustments.
    return mapToCoreRequest(
        text = charSequenceText.toString(),
        language = language,
        country = country,
        variant = variant,
        requestedVoiceName = voiceName,
        currentLocale = currentLocale,
        supportedLocales = supportedLocales,
        currentVoiceId = currentVoiceId,
        knownVoiceIds = knownVoiceIds,
        reportUnknownVoice = reportUnknownVoice,
    )
}

internal fun mapToCoreRequest(
    text: String,
    language: String?,
    country: String?,
    variant: String?,
    requestedVoiceName: String?,
    currentLocale: LocaleTag,
    supportedLocales: Set<LocaleTag>,
    currentVoiceId: String,
    knownVoiceIds: Set<String>,
    reportUnknownVoice: (String) -> Unit,
): TtsRequest {
    val requestedVoice = requestedVoiceName?.takeIf { it.isNotBlank() }
    val resolvedVoice = requestedVoice?.takeIf { it in knownVoiceIds } ?: currentVoiceId
    if (requestedVoice != null && requestedVoice !in knownVoiceIds) {
        reportUnknownVoice(requestedVoice)
    }

    return TtsRequest(
        text = text,
        locale = AndroidLocaleMapper.fromTtsCodes(language, country, variant)
            ?.let { requested -> resolveSupportedLocale(requested, supportedLocales, currentLocale) }
            ?: currentLocale,
        voiceId = resolvedVoice,
    )
}

private fun resolveSupportedLocale(
    requested: LocaleTag,
    supportedLocales: Set<LocaleTag>,
    defaultLocale: LocaleTag,
): LocaleTag {
    if (requested in supportedLocales) return requested
    val requestedPlatform = java.util.Locale.forLanguageTag(requested.value)
    val matches = supportedLocales.filter { candidate ->
        val platform = java.util.Locale.forLanguageTag(candidate.value)
        platform.language == requestedPlatform.language &&
            (requestedPlatform.country.isEmpty() || platform.country == requestedPlatform.country) &&
            (requestedPlatform.variant.isEmpty() || platform.variant == requestedPlatform.variant)
    }
    return defaultLocale.takeIf { it in matches } ?: matches.singleOrNull() ?: requested
}
