package name.me.voxhub.platform.tts

import android.speech.tts.TextToSpeech
import java.util.IllformedLocaleException
import java.util.Locale
import java.util.MissingResourceException
import name.me.voxhub.core.tts.LocaleTag

internal data class AndroidTtsLocaleCodes(
    val language: String,
    val country: String,
    val variant: String,
) {
    fun toArray() = arrayOf(language, country, variant)
    fun toVoiceDataName() = listOf(language, country, variant).filter(String::isNotEmpty).joinToString("-")
}

internal object AndroidLocaleMapper {
    val simplifiedChinese = LocaleTag("zh-CN")

    fun fromTtsCodes(language: String?, country: String?, variant: String?): LocaleTag? {
        val rawLanguage = language?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val languageCode = languageCode(rawLanguage) ?: return null
        val countryCode = country?.trim().orEmpty().let { raw ->
            when {
                raw.isEmpty() -> ""
                raw.length == 2 && raw.all(Char::isLetter) -> raw.uppercase(Locale.ROOT)
                else -> countryCode(raw, languageCode) ?: return null
            }
        }
        return try {
            val builder = Locale.Builder().setLanguage(languageCode)
            if (countryCode.isNotEmpty()) builder.setRegion(countryCode)
            if (!variant.isNullOrBlank()) builder.setVariant(variant.trim())
            LocaleTag(builder.build().toLanguageTag())
        } catch (_: IllformedLocaleException) {
            null
        }
    }

    fun toTtsCodes(locale: LocaleTag): AndroidTtsLocaleCodes? {
        return try {
            val platform = Locale.forLanguageTag(locale.value)
            if (platform.language.isBlank()) null else AndroidTtsLocaleCodes(
                platform.isO3Language,
                if (platform.country.isEmpty()) "" else platform.isO3Country,
                platform.variant,
            )
        } catch (_: MissingResourceException) {
            null
        }
    }

    fun availability(
        language: String?,
        country: String?,
        variant: String?,
        supportedLocales: Set<LocaleTag>,
        configured: Boolean,
    ): Int {
        val requested = fromTtsCodes(language, country, variant) ?: return TextToSpeech.LANG_NOT_SUPPORTED
        val requestedLocale = Locale.forLanguageTag(requested.value)
        val matched = supportedLocales.any { supported ->
            val candidate = Locale.forLanguageTag(supported.value)
            candidate.language == requestedLocale.language &&
                (requestedLocale.country.isEmpty() || candidate.country == requestedLocale.country) &&
                (requestedLocale.variant.isEmpty() || candidate.variant == requestedLocale.variant)
        }
        if (!matched) return TextToSpeech.LANG_NOT_SUPPORTED
        if (!configured) return TextToSpeech.LANG_MISSING_DATA
        return when {
            !variant.isNullOrBlank() -> TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            !country.isNullOrBlank() -> TextToSpeech.LANG_COUNTRY_AVAILABLE
            else -> TextToSpeech.LANG_AVAILABLE
        }
    }

    private fun languageCode(raw: String): String? {
        if (raw.length in 2..3) {
            val direct = Locale.forLanguageTag(raw)
            if (direct.language.length == 2) return direct.language
        }
        return Locale.getAvailableLocales().firstOrNull {
            runCatching { it.isO3Language.equals(raw, ignoreCase = true) }.getOrDefault(false)
        }?.language?.takeIf(String::isNotBlank)
    }

    private fun countryCode(raw: String, language: String): String? = Locale.getAvailableLocales()
        .firstOrNull {
            it.language == language && runCatching { it.isO3Country.equals(raw, ignoreCase = true) }.getOrDefault(false)
        }
        ?.country
        ?: Locale.getAvailableLocales().firstOrNull {
            runCatching { it.isO3Country.equals(raw, ignoreCase = true) }.getOrDefault(false)
        }?.country
}
