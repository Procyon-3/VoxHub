package name.me.voxhub.feature.usage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import name.me.voxhub.R
import name.me.voxhub.VoxHubApplication
import name.me.voxhub.domain.model.*
import name.me.voxhub.ui.components.*
import name.me.voxhub.ui.viewModelFactory

@Composable
fun UsageRoute(contentPadding: PaddingValues, onBack: () -> Unit) {
    val application = LocalContext.current.applicationContext as VoxHubApplication
    val model: UsageViewModel = viewModel(factory = viewModelFactory { UsageViewModel(application.container.usageRepository) })
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(model) {
        model.startRefreshing()
        onPauseOrDispose { model.stopRefreshing() }
    }
    UsageScreen(state, contentPadding, onBack, model::clear, model::selectConfiguration,
        model::selectSource, model::selectDays, model::loadMore, model::reload)
}

@Composable
internal fun UsageScreen(
    state: UsageUiState, contentPadding: PaddingValues, onBack: () -> Unit, onClear: () -> Unit,
    onConfiguration: (UsageConfiguration?) -> Unit, onSource: (UsageSource?) -> Unit,
    onDays: (Int) -> Unit, onLoadMore: () -> Unit, onRetry: () -> Unit,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showExplanation by rememberSaveable { mutableStateOf(false) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var expandedIds by rememberSaveable(state.configuration, state.source, state.days) {
        mutableStateOf<List<String>>(emptyList())
    }
    var hasLoaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.loading, state.error) {
        if (!state.loading && !state.error) hasLoaded = true
    }
    val placeholder = !hasLoaded && state.entries.isEmpty() && state.overview.count == 0L && (state.loading || state.error)
    if (showExplanation) AlertDialog(
        onDismissRequest = { showExplanation = false },
        title = { Text(stringResource(R.string.usage_explanation_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.usage_metric_explanation))
                Text(stringResource(R.string.usage_retention))
            }
        },
        confirmButton = { TextButton(onClick = { showExplanation = false }) { Text(stringResource(R.string.usage_close)) } },
    )
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.usage_clear_title)) },
        text = { Text(stringResource(R.string.usage_clear_all_message)) },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text(stringResource(R.string.usage_clear)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
    )
    val direction = LocalLayoutDirection.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().consumeWindowInsets(contentPadding),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(direction) + 20.dp,
                end = contentPadding.calculateEndPadding(direction) + 20.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PageTitle(stringResource(R.string.usage_title))
                        Text(stringResource(R.string.usage_local_summary), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.usage_more))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.usage_explanation_title)) },
                                onClick = { showMenu = false; showExplanation = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.usage_clear), color = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; confirmClear = true })
                        }
                    }
                }
            }
            item(key = "filters") {
                UsageFilters(state, filtersExpanded, { filtersExpanded = !filtersExpanded },
                    onDays, onConfiguration, onSource)
                // Reserve this space so periodic refreshes do not shift the content.
                Box(Modifier.fillMaxWidth().height(8.dp), contentAlignment = Alignment.BottomCenter) {
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if (state.error) item(key = "read_error") {
                UsageError(R.string.usage_load_failed, R.string.usage_retry, onRetry)
            }
            if (state.clearError) item(key = "clear_error") {
                UsageError(R.string.usage_clear_failed, R.string.usage_clear) { confirmClear = true }
            }
            item(key = "overview") { UsageOverviewCard(state.overview, placeholder) }
            item(key = "history_title") { SectionTitle(stringResource(R.string.usage_history)) }
            if (state.entries.isEmpty() && !state.loading && !state.error) item(key = "empty") {
                EmptyState(stringResource(R.string.usage_empty), stringResource(R.string.usage_empty_hint))
            }
            items(state.entries, key = { it.id }) { entry ->
                UsageEntryCard(entry, entry.id in expandedIds) {
                    expandedIds = if (entry.id in expandedIds) expandedIds - entry.id else expandedIds + entry.id
                }
            }
            if (state.hasMore) item(key = "load_more") {
                TextButton(onClick = onLoadMore, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.usage_load_more))
                }
            }
        }
    }
}

@Composable
private fun UsageFilters(
    state: UsageUiState, expanded: Boolean, onToggle: () -> Unit,
    onDays: (Int) -> Unit,
    onConfiguration: (UsageConfiguration?) -> Unit, onSource: (UsageSource?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to R.string.usage_today, 7 to R.string.usage_7_days, 30 to R.string.usage_30_days).forEach { (days, label) ->
                FilterChip(state.days == days, { onDays(days) }, { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        val allConfigs = stringResource(R.string.usage_all_configs)
        val allSources = stringResource(R.string.usage_all_sources)
        val configLabel = state.configuration?.let { configurationLabel(it) } ?: allConfigs
        val sourceLabel = state.source?.let { sourceLabel(it) } ?: allSources
        SelectionRow(stringResource(R.string.usage_filter), configLabel, sourceLabel, onToggle, expanded = expanded)
        if (expanded) {
            val configurations = (state.configurations + listOfNotNull(state.configuration)).distinctBy {
                Triple(it.providerId, it.configId, it.configName.takeIf { _ -> it.configId == null })
            }
            DropdownField(stringResource(R.string.usage_configuration), configLabel,
                listOf("all" to allConfigs) + configurations.mapIndexed { index, config -> index.toString() to configurationLabel(config) },
                { onConfiguration(it.toIntOrNull()?.let(configurations::get)) })
            DropdownField(stringResource(R.string.usage_source), sourceLabel,
                listOf("all" to allSources) + UsageSource.entries.map { it.name to sourceLabel(it) },
                { value -> onSource(UsageSource.entries.firstOrNull { it.name == value }) })
        }
    }
}

@Composable
private fun UsageOverviewCard(overview: UsageOverview, placeholder: Boolean) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricGrid(listOf(
                R.string.usage_requests_label to if (placeholder) "—" else number(overview.count),
                R.string.usage_success_rate to (if (placeholder) null else overview.successRate)?.let {
                    NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }.format(it)
                }.orEmpty().ifEmpty { "—" },
            ), prominent = true)
            Text(stringResource(R.string.usage_not_billing), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MetricGrid(listOf(
                R.string.usage_characters to if (placeholder) "—" else number(overview.textCharacters),
                R.string.usage_first_audio to duration(if (placeholder) null else overview.averageFirstAudioMillis),
                R.string.usage_total_duration to duration(if (placeholder) null else overview.averageTotalMillis),
            ), prominent = false)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    UsageOutcome.SUCCEEDED to overview.succeeded,
                    UsageOutcome.FAILED to overview.failed,
                    UsageOutcome.CANCELLED to overview.cancelled,
                    UsageOutcome.RUNNING to overview.running,
                    UsageOutcome.INTERRUPTED to overview.interrupted,
                ).forEach { (outcome, count) ->
                    StatusBadge(stringResource(R.string.usage_status_count, outcomeLabel(outcome), if (placeholder) "—" else number(count)), outcomeKind(outcome))
                }
            }
        }
    }
}

@Composable
private fun MetricGrid(metrics: List<Pair<Int, String>>, prominent: Boolean) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + 8.dp) / (140.dp * fontScale + 8.dp)).toInt().coerceIn(1, metrics.size)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            metrics.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(label), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = if (prominent) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                                color = if (prominent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun UsageEntryCard(entry: UsageEntry, expanded: Boolean, onToggle: () -> Unit) {
    val expansionLabel = stringResource(if (expanded) R.string.selection_expanded else R.string.selection_collapsed)
    Surface(onClick = onToggle, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = expansionLabel }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(entry.attribution.configName.ifBlank { stringResource(R.string.usage_no_config) },
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(24.dp).rotate(if (expanded) 270f else 90f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically) {
                StatusBadge(outcomeLabel(entry.outcome), outcomeKind(entry.outcome))
                Text(stringResource(R.string.usage_entry_summary, sourceLabel(entry.attribution.source), number(entry.textCharacters.toLong())),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(entry.startedAt)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.usage_entry_provider, entry.attribution.providerId ?: stringResource(R.string.usage_no_provider)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.usage_entry_duration, duration(entry.firstAudioMillis?.toDouble()), duration(entry.totalMillis?.toDouble())),
                    style = MaterialTheme.typography.bodyMedium)
                if (entry.failure != null) Text(stringResource(R.string.usage_entry_failure,
                    problemTitle(name.me.voxhub.core.tts.TtsFailure.valueOf(entry.failure)), stageLabel(entry.failureStage)),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun outcomeKind(outcome: UsageOutcome): BadgeKind = when (outcome) {
    UsageOutcome.SUCCEEDED -> BadgeKind.Success
    UsageOutcome.FAILED -> BadgeKind.Error
    UsageOutcome.INTERRUPTED -> BadgeKind.Warning
    UsageOutcome.CANCELLED, UsageOutcome.RUNNING -> BadgeKind.Neutral
}

@Composable
private fun UsageError(message: Int, action: Int, onAction: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(message), color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onAction) { Text(stringResource(action), color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}
private fun number(value: Long) = NumberFormat.getIntegerInstance().format(value)
@Composable
private fun duration(millis: Double?): String = millis?.let {
    stringResource(R.string.usage_seconds, NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(it / 1000))
} ?: "—"
@Composable
private fun configurationLabel(config: UsageConfiguration): String {
    val name = config.configName.ifBlank { stringResource(R.string.usage_no_config) }
    return stringResource(R.string.usage_config_label, name, config.providerId ?: stringResource(R.string.usage_no_provider))
}
@Composable
private fun sourceLabel(source: UsageSource): String = stringResource(when (source) {
    UsageSource.SYSTEM_TTS -> R.string.usage_source_system
    UsageSource.PLAYGROUND -> R.string.usage_source_playground
    UsageSource.ACTIVE_TEST -> R.string.usage_source_test
})
@Composable
private fun outcomeLabel(outcome: UsageOutcome): String = stringResource(when (outcome) {
    UsageOutcome.SUCCEEDED -> R.string.usage_succeeded
    UsageOutcome.FAILED -> R.string.usage_failed
    UsageOutcome.CANCELLED -> R.string.usage_cancelled
    UsageOutcome.RUNNING -> R.string.usage_running
    UsageOutcome.INTERRUPTED -> R.string.usage_interrupted
})

@Composable
private fun stageLabel(stage: String?): String = if (stage == null) "—" else stringResource(when (name.me.voxhub.core.tts.TtsStage.valueOf(stage)) {
    name.me.voxhub.core.tts.TtsStage.REQUEST_VALIDATION -> R.string.usage_stage_validation
    name.me.voxhub.core.tts.TtsStage.NETWORK_CONNECT -> R.string.usage_stage_connect
    name.me.voxhub.core.tts.TtsStage.HTTP_RESPONSE -> R.string.usage_stage_response
    name.me.voxhub.core.tts.TtsStage.STREAM_READ -> R.string.usage_stage_read
    name.me.voxhub.core.tts.TtsStage.STREAM_PARSE -> R.string.usage_stage_parse
    name.me.voxhub.core.tts.TtsStage.OUTPUT -> R.string.usage_stage_output
})
