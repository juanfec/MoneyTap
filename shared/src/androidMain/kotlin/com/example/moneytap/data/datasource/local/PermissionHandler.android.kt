package com.example.moneytap.data.datasource.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.moneytap.domain.model.PermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class PermissionHandler(
    private val context: Context,
) {
    private val _smsPermissionState = MutableStateFlow<PermissionState>(PermissionState.NotRequested)
    actual val smsPermissionState: StateFlow<PermissionState> = _smsPermissionState.asStateFlow()

    actual fun checkSmsPermission(): PermissionState {
        val permission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        )
        val state = if (permission == PackageManager.PERMISSION_GRANTED) {
            PermissionState.Granted
        } else {
            PermissionState.Denied
        }
        _smsPermissionState.value = state
        return state
    }

    actual fun updatePermissionState(state: PermissionState) {
        _smsPermissionState.value = state
    }
}
