package com.example.moneytap.domain.model

sealed class PermissionState {
    data object Granted : PermissionState()
    data object Denied : PermissionState()
    data object PermanentlyDenied : PermissionState()
    data object NotRequested : PermissionState()
    data object NotApplicable : PermissionState()
}
