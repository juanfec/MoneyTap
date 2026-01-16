package com.example.moneytap.data.datasource.local

import com.example.moneytap.domain.model.PermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class PermissionHandler {
    private val _smsPermissionState = MutableStateFlow<PermissionState>(PermissionState.NotApplicable)
    actual val smsPermissionState: StateFlow<PermissionState> = _smsPermissionState.asStateFlow()

    actual fun checkSmsPermission(): PermissionState = PermissionState.NotApplicable

    actual fun updatePermissionState(state: PermissionState) {
        // No-op on iOS
    }
}
