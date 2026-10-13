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

class InstallTtsDataActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as VoxHubApplication).container
        lifecycleScope.launch {
            container.awaitInitialization()
            val activeId = container.configRepository.currentState.activeProviderConfigId
            startActivity(SettingsActivity.createIntent(this@InstallTtsDataActivity, activeId?.value))
            finish()
        }
    }
}
