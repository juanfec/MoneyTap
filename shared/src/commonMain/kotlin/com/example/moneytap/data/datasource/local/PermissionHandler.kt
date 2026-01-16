package com.example.moneytap.data.datasource.local

import com.example.moneytap.domain.model.PermissionState
import kotlinx.coroutines.flow.StateFlow

expect class PermissionHandler {
    val smsPermissionState: StateFlow<PermissionState>
    fun checkSmsPermission(): PermissionState
    fun updatePermissionState(state: PermissionState)
}
