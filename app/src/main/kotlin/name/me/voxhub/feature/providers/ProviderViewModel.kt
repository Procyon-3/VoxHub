package name.me.voxhub.feature.providers

import timber.log.Timber
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import name.me.voxhub.R
import name.me.voxhub.data.repository.ProviderConfigRepository
import name.me.voxhub.domain.model.AppState
import name.me.voxhub.domain.model.ProviderConfigId

data class ProviderUiState(
    val appState: AppState = AppState(),
    val isSwitching: Boolean = false,
)

class ProviderViewModel(private val stateRepository: ProviderConfigRepository) : ViewModel() {
    private val isSwitching = MutableStateFlow(false)
    val uiState = combine(stateRepository.state, isSwitching, ::ProviderUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderUiState(stateRepository.currentState))

    private val eventChannel = Channel<Int>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun setActive(id: ProviderConfigId?) {
        if (isSwitching.value || stateRepository.currentState.activeProviderConfigId == id) return
        isSwitching.value = true
        viewModelScope.launch {
            try {
                stateRepository.setActive(id)
                Timber.i("Active provider changed enabled=%s", id != null)
                eventChannel.send(if (id == null) R.string.provider_disabled else R.string.provider_activated)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Timber.e(failure, "Active provider change failed")
                eventChannel.send(R.string.operation_failed)
            } finally {
                isSwitching.value = false
            }
        }
    }
}
