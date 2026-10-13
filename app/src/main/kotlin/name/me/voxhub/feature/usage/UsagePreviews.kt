package name.me.voxhub.feature.usage

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import name.me.voxhub.domain.model.*
import name.me.voxhub.ui.theme.VoxHubTheme

private val previewEntries = UsageOutcome.entries.mapIndexed { index, outcome ->
    UsageEntry(
        id = "preview-$index", startedAt = 1791158400000L - index * 60_000L,
        attribution = UsageAttribution("mimo", "personal", "日常朗读", UsageSource.entries[index % 3]),
        textCharacters = 1280, outcome = outcome, firstAudioMillis = 420, totalMillis = 3400,
        failure = if (outcome == UsageOutcome.FAILED) "NETWORK_TIMEOUT" else null,
        failureStage = if (outcome == UsageOutcome.FAILED) "NETWORK_CONNECT" else null,
    )
}

internal class UsagePreviewStates : PreviewParameterProvider<UsageUiState> {
    private val populated = UsageUiState(
        loading = false,
        overview = UsageOverview(count = 128, textCharacters = 42680, succeeded = 120, failed = 3,
            cancelled = 2, running = 1, interrupted = 2, averageFirstAudioMillis = 420.0, averageTotalMillis = 3400.0),
        entries = previewEntries, hasMore = true,
    )
    override val values = sequenceOf(
        populated,
        UsageUiState(loading = false),
        UsageUiState(),
        UsageUiState(loading = false, error = true),
        populated.copy(clearError = true),
        populated.copy(loading = true),
        populated.copy(
            configuration = UsageConfiguration("mimo", "personal", "用于多语言长文本朗读的个人服务商配置"),
            source = UsageSource.PLAYGROUND,
            overview = populated.overview.copy(textCharacters = 123456789012L),
            entries = previewEntries.map { it.copy(attribution = it.attribution.copy(configName = "用于多语言长文本朗读的个人服务商配置")) },
        ),
    )
}

@Preview(name = "Light", widthDp = 360, heightDp = 800)
@Preview(name = "Dark", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Narrow", widthDp = 320, heightDp = 640)
@Preview(name = "Large text", widthDp = 320, heightDp = 640, fontScale = 2f)
@Preview(name = "Tablet", widthDp = 840, heightDp = 1000)
@Composable
internal fun UsagePreview(@PreviewParameter(UsagePreviewStates::class) state: UsageUiState) {
    VoxHubTheme {
        Surface {
            UsageScreen(state, PaddingValues(), onBack = {}, onClear = {}, onConfiguration = {},
                onSource = {}, onDays = {}, onLoadMore = {}, onRetry = {})
        }
    }
}

@Preview(name = "Expanded outcomes", widthDp = 360, heightDp = 1200)
@Preview(name = "Expanded dark", widthDp = 360, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Expanded large text", widthDp = 320, heightDp = 1200, fontScale = 2f)
@Composable
internal fun UsageEntryPreview() {
    VoxHubTheme {
        Surface {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                previewEntries.forEach { UsageEntryCard(it, expanded = true, onToggle = {}) }
            }
        }
    }
}
