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

class CheckVoiceDataActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as VoxHubApplication).container
        lifecycleScope.launch {
            if (!container.awaitInitialization()) {
                setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL)
                finish()
                return@launch
            }
            val record = container.configRepository.currentState.activeConfig
            val registration = record?.let { container.providerCatalog[it.providerId] }
            val configured = record != null && registration != null &&
                (container.synthesisResolver.hasCompleteConfig(record.id))
            val localeNames = (registration?.voices?.map { it.locale } ?: container.providerCatalog.locales)
                .mapNotNull(AndroidLocaleMapper::toTtsCodes)
                .map(AndroidTtsLocaleCodes::toVoiceDataName)
                .distinct()
            val result = Intent().apply {
                putStringArrayListExtra(
                    TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                    ArrayList(if (configured) localeNames else emptyList()),
                )
                putStringArrayListExtra(
                    TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES,
                    ArrayList(if (configured) emptyList() else localeNames),
                )
            }
            setResult(
                if (configured) TextToSpeech.Engine.CHECK_VOICE_DATA_PASS
                else TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL,
                result,
            )
            finish()
        }
    }
}
