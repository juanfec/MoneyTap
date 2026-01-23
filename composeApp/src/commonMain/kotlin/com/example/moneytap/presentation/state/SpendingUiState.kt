package com.example.moneytap.presentation.state

import androidx.compose.runtime.Immutable
import com.example.moneytap.domain.model.PermissionState
import com.example.moneytap.domain.model.SpendingSummary

@Immutable
data class SpendingUiState(
    val isLoading: Boolean = false,
    val summary: SpendingSummary? = null,
    val error: SpendingError? = null,
    val permissionState: PermissionState = PermissionState.NotRequested,
    val isPlatformSupported: Boolean = true,
)

@Immutable
sealed class SpendingError {
    data object PermissionDenied : SpendingError()
    data object PermissionPermanentlyDenied : SpendingError()
    data object PlatformNotSupported : SpendingError()
    data object NoTransactions : SpendingError()
    data class Generic(val message: String) : SpendingError()
}
