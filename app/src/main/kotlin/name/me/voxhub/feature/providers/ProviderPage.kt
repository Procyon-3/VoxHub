package name.me.voxhub.feature.providers

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import name.me.voxhub.R
import name.me.voxhub.ui.components.TopLevelPageHeader

/** Keeps form actions reachable, including when a landscape IME leaves very little height. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderPage(
    title: String,
    contentPadding: PaddingValues,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    footer: (@Composable () -> Unit)? = null,
    smallTopAppBar: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.(compact: Boolean) -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding).imePadding(),
    ) {
        val compact = maxHeight < 400.dp
        val sideActions = maxHeight < 180.dp && maxWidth >= 480.dp && footer != null
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // Preserve the form's composition and focus as IME insets change.
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!sideActions || smallTopAppBar) {
                    if (smallTopAppBar) {
                        TopAppBar(
                            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            navigationIcon = {
                                if (onBack != null) IconButton(onClick = onBack) {
                                    Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                                }
                            },
                            actions = actions,
                            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                        )
                    } else if (onBack == null) {
                        TopLevelPageHeader(title, compact = compact, actions = actions)
                    } else {
                        Row(
                            Modifier.widthIn(max = 720.dp).fillMaxWidth()
                                .heightIn(min = if (compact) 48.dp else 76.dp).padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                            }
                            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            actions()
                        }
                    }
                    header?.invoke()
                }
                content(compact)
                if (!sideActions) footer?.invoke()
            }
            if (sideActions) {
                Box(Modifier.width(208.dp), contentAlignment = Alignment.Center) { footer.invoke() }
            }
        }
    }
}
