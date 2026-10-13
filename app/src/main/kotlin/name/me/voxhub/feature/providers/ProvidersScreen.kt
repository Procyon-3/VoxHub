package name.me.voxhub.feature.providers

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import name.me.voxhub.R
import name.me.voxhub.VoxHubApplication
import name.me.voxhub.domain.model.ProviderConfigId
import name.me.voxhub.domain.model.ProviderConfigRecord
import name.me.voxhub.domain.provider.ProviderCatalog
import name.me.voxhub.domain.provider.ProviderRegistration
import name.me.voxhub.provider.mimo.tts.MIMO_DESCRIPTOR
import name.me.voxhub.provider.mimo.voicedesign.VOICEDESIGN_DESCRIPTOR
import name.me.voxhub.provider.mimo.voiceclone.VOICECLONE_DESCRIPTOR
import name.me.voxhub.ui.components.*
import name.me.voxhub.ui.viewModelFactory

// The Modifier is applied only to the add button, not the route's root layout.
@SuppressLint("ModifierParameter")
@Composable
fun ProvidersRoute(
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onEdit: (ProviderConfigRecord) -> Unit,
    showMessage: suspend (String) -> Unit,
    addButtonModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources by rememberUpdatedState(LocalResources.current)
    val container = (context.applicationContext as VoxHubApplication).container
    val viewModel: ProviderViewModel = viewModel(factory = viewModelFactory { container.createProviderViewModel() })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel.events, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { showMessage(resources.getString(it)) }
        }
    }

    ProvidersScreen(state, container.providerCatalog, contentPadding, onAdd, viewModel::setActive, onEdit, addButtonModifier)
}

// Keep the specific parameter name to avoid implying this modifies the whole screen.
@SuppressLint("ModifierParameter")
@Composable
internal fun ProvidersScreen(
    state: ProviderUiState,
    catalog: ProviderCatalog,
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onSetActive: (ProviderConfigId?) -> Unit,
    onEdit: (ProviderConfigRecord) -> Unit,
    addButtonModifier: Modifier = Modifier,
) {
    ProviderPage(
        title = stringResource(R.string.providers_title),
        contentPadding = contentPadding,
    ) {
        Box(Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.appState.configs.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(painterResource(R.drawable.ic_plans), null, Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.no_saved_provider), style = MaterialTheme.typography.titleMedium)
                            SecondaryText(stringResource(R.string.providers_empty_hint))
                        }
                    }
                } else {
                    if (state.appState.activeConfig == null) {
                        item { SecondaryText(stringResource(R.string.no_active_provider_detail)) }
                    }
                    // Preserve list order when toggling: the switch must not jump under the user's finger.
                    items(state.appState.configs, key = { it.id.value }) { record ->
                        ProviderConfigItem(
                            record = record,
                            catalog = catalog,
                            active = record.id == state.appState.activeProviderConfigId,
                            switching = state.isSwitching,
                            onEdit = { onEdit(record) },
                            onSetActive = { enabled -> onSetActive(if (enabled) record.id else null) },
                        )
                    }
                }
            }
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp)
                    .then(addButtonModifier),
            ) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.provider_add_config))
            }
        }
    }
}

@Composable
private fun ProviderConfigItem(
    record: ProviderConfigRecord,
    catalog: ProviderCatalog,
    active: Boolean,
    switching: Boolean,
    onEdit: () -> Unit,
    onSetActive: (Boolean) -> Unit,
) {
    val cardColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "Plan card background",
    )
    val switchDescription = stringResource(
        if (active) R.string.provider_disable_label else R.string.provider_enable_label, record.displayName,
    )
    Surface(onClick = onEdit, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = cardColor) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(record.displayName, style = MaterialTheme.typography.titleMedium,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                Text(providerConfigSummary(record, catalog), style = MaterialTheme.typography.bodyMedium,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = active, onCheckedChange = onSetActive, enabled = !switching,
                modifier = Modifier.semantics { contentDescription = switchDescription })
        }
    }
}

@Composable
fun ChooseProviderRoute(contentPadding: PaddingValues, onBack: () -> Unit, onChoose: (String) -> Unit) {
    val container = (LocalContext.current.applicationContext as VoxHubApplication).container
    ChooseProviderScreen(container.providerCatalog.registrations, contentPadding, onBack, onChoose)
}

private data class ProviderTemplateDetails(val groupRes: Int, val descriptionRes: Int)

private val providerTemplates = mapOf(
    MIMO_DESCRIPTOR.id to ProviderTemplateDetails(R.string.provider_group_mimo, R.string.mimo_description),
    VOICEDESIGN_DESCRIPTOR.id to ProviderTemplateDetails(R.string.provider_group_mimo, R.string.voicedesign_description),
    VOICECLONE_DESCRIPTOR.id to ProviderTemplateDetails(R.string.provider_group_mimo, R.string.voiceclone_description),
)

@Composable
internal fun ChooseProviderScreen(
    sources: List<ProviderRegistration>,
    contentPadding: PaddingValues, onBack: () -> Unit, onChoose: (String) -> Unit,
) {
    val groups = sources.groupBy { providerTemplates.getValue(it.descriptor.id).groupRes }
    ProviderPage(
        title = stringResource(R.string.choose_provider_title),
        contentPadding = contentPadding,
        onBack = onBack,
        smallTopAppBar = true,
    ) {
        LazyColumn(Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            groups.forEach { (groupRes, registrations) ->
                item(key = "group-$groupRes") {
                    Text(stringResource(groupRes),
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                            .semantics { heading() },
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(registrations, key = { it.descriptor.id }) { registration ->
                    Surface(onClick = { onChoose(registration.descriptor.id) }, modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(registration.descriptor.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(providerTemplates.getValue(registration.descriptor.id).descriptionRes),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(painterResource(R.drawable.ic_chevron_right), null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
