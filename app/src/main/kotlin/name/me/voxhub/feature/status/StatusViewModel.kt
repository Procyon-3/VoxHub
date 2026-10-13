package name.me.voxhub.feature.status

import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import name.me.voxhub.data.repository.ProviderConfigRepository
import name.me.voxhub.domain.model.AppState

data class SystemTtsState(
    val engineAvailable: Boolean = true,
    val isDefaultEngine: Boolean = false,
    val defaultEnginePackage: String? = null,
    val defaultEngineName: String? = null,
)

data class StatusUiState(
    val appState: AppState = AppState(),
    val system: SystemTtsState = SystemTtsState(),
)

class StatusViewModel(
    private val application: Application,
    stateRepository: ProviderConfigRepository,
) : ViewModel() {
    private val systemState = MutableStateFlow(readSystemState())
    val uiState: StateFlow<StatusUiState> = combine(
        stateRepository.state,
        systemState,
        ::StatusUiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatusUiState(stateRepository.currentState, systemState.value))

    fun refreshSystemState() {
        systemState.value = readSystemState()
    }

    @Suppress("DEPRECATION")
    private fun readSystemState(): SystemTtsState {
        val services = application.packageManager.queryIntentServices(
            Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
            0,
        )
        val engineAvailable = services.any {
            it.serviceInfo.packageName == application.packageName && it.serviceInfo.enabled
        }
        val defaultEngine = Settings.Secure.getString(
            application.contentResolver,
            Settings.Secure.TTS_DEFAULT_SYNTH,
        )
        val defaultEngineName = services.firstOrNull { it.serviceInfo.packageName == defaultEngine }
            ?.loadLabel(application.packageManager)?.toString()?.takeIf { it.isNotBlank() }
        return SystemTtsState(engineAvailable, defaultEngine == application.packageName, defaultEngine, defaultEngineName)
    }
}
