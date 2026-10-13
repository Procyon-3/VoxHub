package name.me.voxhub.ui.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import name.me.voxhub.R
import name.me.voxhub.core.tts.TtsFailure
import name.me.voxhub.ui.theme.LocalStatusColors

internal val PageMaxWidth = 600.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DropdownField(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = !expanded }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled).fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
internal fun PageColumn(
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = Modifier
        .fillMaxSize()
        .padding(contentPadding)
        .consumeWindowInsets(contentPadding)
        .verticalScroll(rememberScrollState())
    Column(base, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier
                .widthIn(max = PageMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

/** Shared title layout for the three root tabs; child screens keep their own app bars. */
@Composable
internal fun TopLevelPageHeader(
    title: String,
    compact: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.widthIn(max = 720.dp).fillMaxWidth()
            .heightIn(min = if (compact) 48.dp else 76.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageTitle(title, Modifier.weight(1f))
        actions()
    }
}

@Composable
internal fun PageTitle(titleRes: Int, modifier: Modifier = Modifier) {
    PageTitle(stringResource(titleRes), modifier)
}

@Composable
internal fun PageTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
internal fun SecondaryText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun EmptyState(title: String, detail: String?) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (detail != null) {
            Spacer(Modifier.height(6.dp))
            SecondaryText(detail)
        }
    }
}

internal enum class BadgeKind { Success, Warning, Error, Neutral }

@Composable
internal fun StatusBadge(text: String, kind: BadgeKind) {
    val statusColors = LocalStatusColors.current
    val foreground = when (kind) {
        BadgeKind.Success -> statusColors.success
        BadgeKind.Warning -> statusColors.warning
        BadgeKind.Error -> MaterialTheme.colorScheme.error
        BadgeKind.Neutral -> MaterialTheme.colorScheme.primary
    }
    val background = when (kind) {
        BadgeKind.Success -> statusColors.successContainer
        BadgeKind.Warning -> statusColors.warningContainer
        BadgeKind.Error -> MaterialTheme.colorScheme.errorContainer
        BadgeKind.Neutral -> MaterialTheme.colorScheme.primaryContainer
    }
    Surface(color = background, contentColor = foreground, shape = CircleShape) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            StatusMark(kind, foreground)
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun StatusMark(kind: BadgeKind, color: Color) {
    Canvas(Modifier.size(10.dp)) {
        val stroke = size.minDimension * 0.16f
        when (kind) {
            BadgeKind.Success -> {
                drawLine(color, Offset(size.width * .1f, size.height * .55f), Offset(size.width * .4f, size.height * .85f), stroke)
                drawLine(color, Offset(size.width * .4f, size.height * .85f), Offset(size.width * .95f, size.height * .15f), stroke)
            }
            BadgeKind.Warning -> {
                drawLine(color, Offset(size.width * .5f, size.height * .1f), Offset(size.width * .5f, size.height * .62f), stroke)
                drawCircle(color, radius = stroke * .7f, center = Offset(size.width * .5f, size.height * .88f))
            }
            BadgeKind.Error -> {
                drawLine(color, Offset(size.width * .15f, size.height * .15f), Offset(size.width * .85f, size.height * .85f), stroke)
                drawLine(color, Offset(size.width * .85f, size.height * .15f), Offset(size.width * .15f, size.height * .85f), stroke)
            }
            BadgeKind.Neutral -> drawCircle(color, radius = size.minDimension * .28f)
        }
    }
}

@Composable
internal fun problemTitle(failure: TtsFailure): String = stringResource(
    when (failure) {
        TtsFailure.INVALID_REQUEST -> R.string.problem_invalid_request
        TtsFailure.NETWORK -> R.string.problem_network
        TtsFailure.NETWORK_TIMEOUT -> R.string.problem_timeout
        TtsFailure.SERVICE -> R.string.problem_service
        TtsFailure.SYNTHESIS -> R.string.problem_synthesis
        TtsFailure.OUTPUT -> R.string.problem_output
        TtsFailure.CANCELLED -> R.string.problem_cancelled
    },
)

internal fun localDateTime(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(epochMillis))
