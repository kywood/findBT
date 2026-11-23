package com.gamenuri.btscanner24.core.ui.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Runtime-permission helper shared by every screen that needs Bluetooth.
 * Accompanist is isolated here so it can be replaced without touching features.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Stable
class BlePermissionState internal constructor(
    private val delegate: MultiplePermissionsState,
    private val context: Context,
    private val requested: () -> Boolean,
) {
    val isGranted: Boolean get() = delegate.allPermissionsGranted

    /** True after the user has answered the system dialog at least once and did not grant. */
    val showRationale: Boolean get() = requested() && !isGranted

    /** The system will no longer show the dialog ("don't ask again"); only Settings can grant it. */
    val isPermanentlyDenied: Boolean
        get() = showRationale && delegate.permissions.none { it.status.shouldShowRationale }

    fun request() {
        if (isPermanentlyDenied) openAppSettings(context) else delegate.launchMultiplePermissionRequest()
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberBlePermissionState(permissions: List<String>, onGranted: () -> Unit = {}): BlePermissionState {
    val context = LocalContext.current
    var requested by rememberSaveable { mutableStateOf(false) }
    val delegate = rememberMultiplePermissionsState(permissions) { result ->
        requested = true
        if (result.values.all { it }) onGranted()
    }
    return remember(delegate, context) { BlePermissionState(delegate, context) { requested } }
}

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
