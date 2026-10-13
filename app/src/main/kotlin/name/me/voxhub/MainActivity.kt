package name.me.voxhub

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import name.me.voxhub.platform.tts.SettingsActivity
import name.me.voxhub.ui.InitializationGate
import name.me.voxhub.ui.navigation.VoxHubApp
import name.me.voxhub.ui.theme.VoxHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val startAtProviders = intent.getBooleanExtra(EXTRA_START_AT_PROVIDERS, false)
        val requestedConfigId = intent.getStringExtra(SettingsActivity.EXTRA_PROVIDER_CONFIG_ID)
        val container = (application as VoxHubApplication).container
        setContent {
            VoxHubTheme {
                InitializationGate(container) {
                    val requestedRecord = container.configRepository.currentState.configs
                        .firstOrNull { it.id.value == requestedConfigId }
                    VoxHubApp(
                        startAtProviders = startAtProviders,
                        initialProviderId = requestedRecord?.providerId,
                        initialProviderConfigId = requestedRecord?.id?.value,
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_START_AT_PROVIDERS = "name.me.voxhub.extra.START_AT_PROVIDERS"
    }
}
