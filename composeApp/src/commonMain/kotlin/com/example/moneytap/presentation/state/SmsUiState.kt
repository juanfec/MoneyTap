package com.example.moneytap.presentation.state

import androidx.compose.runtime.Immutable
import com.example.moneytap.domain.model.PermissionState
import com.example.moneytap.domain.model.SmsMessage

@Immutable
data class SmsUiState(
    val isLoading: Boolean = false,
    val messages: List<SmsMessage> = emptyList(),
    val permissionState: PermissionState = PermissionState.NotRequested,
    val error: SmsUiError? = null,
    val isPlatformSupported: Boolean = true,
)

@Immutable
sealed class SmsUiError {
    data object PermissionDenied : SmsUiError()
    data object PermissionPermanentlyDenied : SmsUiError()
    data object PlatformNotSupported : SmsUiError()
    data class Generic(val message: String) : SmsUiError()
}
