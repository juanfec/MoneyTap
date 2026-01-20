package com.example.moneytap.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS implementation of SMS permission state.
 * iOS does not provide access to SMS inbox, so this returns no-op callbacks.
 * The ViewModel will handle platform support checks separately.
 */
@Composable
actual fun rememberSmsPermissionState(
    onPermissionResult: (granted: Boolean, shouldShowRationale: Boolean) -> Unit,
): SmsPermissionState {
    return remember {
        SmsPermissionState(
            launchPermissionRequest = {
                // iOS doesn't support SMS inbox access
                // The permission result will indicate not granted
                onPermissionResult(false, false)
            },
            openSettings = {
                // Could open iOS Settings app if needed in the future
            },
        )
    }
}
