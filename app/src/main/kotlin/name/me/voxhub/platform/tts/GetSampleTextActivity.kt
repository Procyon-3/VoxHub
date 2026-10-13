package name.me.voxhub.platform.tts

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import kotlinx.coroutines.launch
import name.me.voxhub.R
import name.me.voxhub.VoxHubApplication
import name.me.voxhub.core.tts.LocaleTag

class GetSampleTextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as VoxHubApplication).container
        lifecycleScope.launch {
            if (!container.awaitInitialization()) {
                setResult(TextToSpeech.LANG_NOT_SUPPORTED)
                finish()
                return@launch
            }
            val record = container.configRepository.currentState.activeConfig
            val registration = record?.let { container.providerCatalog[it.providerId] }
            val language = intent.getStringExtra(EXTRA_LANGUAGE)
            val country = intent.getStringExtra(EXTRA_COUNTRY)
            val variant = intent.getStringExtra(EXTRA_VARIANT)
            val locales = registration?.voices?.mapTo(mutableSetOf()) { it.locale }.orEmpty()
            val availability = AndroidLocaleMapper.availability(
                language,
                country,
                variant,
                locales,
                configured = record != null && (container.synthesisResolver.hasCompleteConfig(record.id)),
            )
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                val requestedLocale = AndroidLocaleMapper.fromTtsCodes(language, country, variant)
                setResult(
                    availability,
                    Intent().putExtra(
                        TextToSpeech.Engine.EXTRA_SAMPLE_TEXT,
                        getString(sampleTextResource(requestedLocale)),
                    ),
                )
            } else {
                setResult(availability)
            }
            finish()
        }
    }

    private companion object {
        const val EXTRA_LANGUAGE = "language"
        const val EXTRA_COUNTRY = "country"
        const val EXTRA_VARIANT = "variant"
    }
}

internal fun sampleTextResource(locale: LocaleTag?): Int =
    if (locale != null && Locale.forLanguageTag(locale.value).language == "en") {
        R.string.sample_text_english
    } else {
        R.string.sample_text
    }
