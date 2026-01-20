package com.example.moneytap.permission

import androidx.compose.runtime.Composable

/**
 * Platform-agnostic permission state holder for SMS permissions.
 */
class SmsPermissionState(
    val launchPermissionRequest: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * Remembers and returns an [SmsPermissionState] for handling SMS permission requests.
 *
 * @param onPermissionResult Callback invoked when permission result is received.
 *        - granted: true if permission was granted
 *        - shouldShowRationale: true if the system recommends showing rationale
 */
@Composable
expect fun rememberSmsPermissionState(
    onPermissionResult: (granted: Boolean, shouldShowRationale: Boolean) -> Unit,
): SmsPermissionState
