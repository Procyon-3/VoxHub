package name.me.voxhub.platform.tts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import name.me.voxhub.MainActivity

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startActivity(createIntent(this))
        finish()
    }

    companion object {
        fun createIntent(context: Context, providerConfigId: String? = null): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_START_AT_PROVIDERS, true)
                .apply {
                    providerConfigId?.let { putExtra(EXTRA_PROVIDER_CONFIG_ID, it) }
                }

        const val EXTRA_PROVIDER_CONFIG_ID = "name.me.voxhub.extra.PROVIDER_CONFIG_ID"
    }
}
