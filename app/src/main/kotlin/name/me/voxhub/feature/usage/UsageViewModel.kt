package name.me.voxhub.feature.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import name.me.voxhub.data.repository.UsageRepository
import name.me.voxhub.domain.model.*
import timber.log.Timber

data class UsageUiState(
    val configurations: List<UsageConfiguration> = emptyList(),
    val configuration: UsageConfiguration? = null,
    val source: UsageSource? = null,
    val days: Int = 1,
    val overview: UsageOverview = UsageOverview(),
    val entries: List<UsageEntry> = emptyList(),
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val error: Boolean = false,
    val clearError: Boolean = false,
)

class UsageViewModel(private val repository: UsageRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(UsageUiState())
    val state = mutableState.asStateFlow()
    private var load: Job? = null
    private var refreshJob: Job? = null
    private var limit = 50

    fun startRefreshing() {
        if (refreshJob != null) return
        refreshJob = viewModelScope.launch {
            launch { repository.changes.collect { reload() } }
            while (true) { delay(60_000); reload() }
        }
    }

    fun stopRefreshing() {
        refreshJob?.cancel()
        refreshJob = null
        load?.cancel()
        load = null
    }

    fun selectConfiguration(value: UsageConfiguration?) = select { copy(configuration = value) }
    fun selectSource(value: UsageSource?) = select { copy(source = value) }
    fun selectDays(value: Int) = select { copy(days = value) }
    private fun select(update: UsageUiState.() -> UsageUiState) {
        limit = 50
        mutableState.value = mutableState.value.update()
        reload()
    }
    fun loadMore() { if (state.value.hasMore && !state.value.loading) { limit += 50; reload() } }
    fun clear() {
        viewModelScope.launch {
            try {
                repository.clear()
                limit = 50
                mutableState.value = mutableState.value.copy(clearError = false)
                reload()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                Timber.e(failure, "Usage clear failed")
                mutableState.value = mutableState.value.copy(clearError = true)
            }
        }
    }
    fun reload() {
        load?.cancel()
        val selected = state.value
        mutableState.value = selected.copy(loading = true, error = false)
        load = viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val since = localStart(now, 1 - selected.days)
                val until = localStart(now, 1)
                val result = repository.snapshot(UsageFilter(since, until, selected.configuration, selected.source), limit)
                mutableState.value = mutableState.value.copy(configurations = result.configurations,
                    overview = result.overview, entries = result.entries, hasMore = result.overview.count > result.entries.size,
                    loading = false, error = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                Timber.e(failure, "Usage read failed")
                mutableState.value = mutableState.value.copy(loading = false, error = true)
            }
        }
    }
}

internal fun localStart(now: Long, dayOffset: Int = 0): Long = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_MONTH, dayOffset)
}.timeInMillis
