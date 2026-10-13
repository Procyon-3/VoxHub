package name.me.voxhub.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import name.me.voxhub.R
import name.me.voxhub.platform.network.endpointUsesLocalNetwork

/** Requests access only when a user-triggered request targets a local address. */
@Composable
internal fun rememberLocalNetworkAction(
    showMessage: suspend (String) -> Unit,
): (String, () -> Unit) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentShowMessage by rememberUpdatedState(showMessage)
    val deniedMessage by rememberUpdatedState(stringResource(R.string.local_network_permission_denied))
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showRationale by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted) {
            action?.invoke()
        } else {
            scope.launch { currentShowMessage(deniedMessage) }
        }
    }

    if (showRationale && Build.VERSION.SDK_INT >= 37) {
        AlertDialog(
            onDismissRequest = { showRationale = false; pendingAction = null },
            title = { Text(stringResource(R.string.local_network_permission_title)) },
            text = { Text(stringResource(R.string.local_network_permission_reason)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                }) { Text(stringResource(R.string.local_network_permission_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false; pendingAction = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    return { endpoint, action ->
        if (!checking && pendingAction == null) {
            scope.launch {
                checking = true
                try {
                    if (Build.VERSION.SDK_INT >= 37 &&
                        context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED &&
                        endpointUsesLocalNetwork(endpoint)
                    ) {
                        pendingAction = action
                        showRationale = true
                    } else {
                        action()
                    }
                } finally {
                    checking = false
                }
            }
        }
    }
}
