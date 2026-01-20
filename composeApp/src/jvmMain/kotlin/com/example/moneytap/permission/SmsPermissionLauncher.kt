package com.example.moneytap.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Desktop (JVM) implementation of SMS permission state.
 * Desktop does not support SMS inbox access, so this returns no-op callbacks.
 * The ViewModel will handle platform support checks separately.
 */
@Composable
actual fun rememberSmsPermissionState(
    onPermissionResult: (granted: Boolean, shouldShowRationale: Boolean) -> Unit,
): SmsPermissionState {
    return remember {
        SmsPermissionState(
            launchPermissionRequest = {
                // Desktop doesn't support SMS inbox access
                onPermissionResult(false, false)
            },
            openSettings = {
                // No-op on Desktop
            },
        )
    }
}
