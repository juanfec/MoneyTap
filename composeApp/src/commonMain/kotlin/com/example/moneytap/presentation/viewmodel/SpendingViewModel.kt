package com.example.moneytap.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneytap.data.datasource.local.PermissionHandler
import com.example.moneytap.domain.model.PermissionState
import com.example.moneytap.domain.model.SmsError
import com.example.moneytap.domain.model.SmsException
import com.example.moneytap.domain.repository.SmsRepository
import com.example.moneytap.domain.usecase.GetSpendingByCategoryUseCase
import com.example.moneytap.presentation.state.SpendingError
import com.example.moneytap.presentation.state.SpendingUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpendingViewModel(
    private val getSpendingByCategoryUseCase: GetSpendingByCategoryUseCase,
    private val permissionHandler: PermissionHandler,
    private val smsRepository: SmsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpendingUiState())
    val uiState: StateFlow<SpendingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SpendingEvent>()
    val events: SharedFlow<SpendingEvent> = _events.asSharedFlow()

    init {
        checkPlatformSupport()
        observePermissionState()
    }

    private fun checkPlatformSupport() {
        val isSupported = smsRepository.isPlatformSupported()
        _uiState.update { it.copy(isPlatformSupported = isSupported) }
        if (!isSupported) {
            _uiState.update { it.copy(error = SpendingError.PlatformNotSupported) }
        }
    }

    private fun observePermissionState() {
        viewModelScope.launch {
            permissionHandler.smsPermissionState.collect { state ->
                _uiState.update { it.copy(permissionState = state) }
                if (state == PermissionState.Granted) {
                    loadSpending()
                }
            }
        }
    }

    fun checkPermission() {
        val state = permissionHandler.checkSmsPermission()
        if (state == PermissionState.Granted) {
            loadSpending()
        } else {
            viewModelScope.launch {
                _events.emit(SpendingEvent.RequestPermission)
            }
        }
    }

    fun onPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
        val newState = when {
            granted -> PermissionState.Granted
            shouldShowRationale -> PermissionState.Denied
            else -> PermissionState.PermanentlyDenied
        }
        permissionHandler.updatePermissionState(newState)

        _uiState.update {
            it.copy(
                error = when (newState) {
                    PermissionState.Denied -> SpendingError.PermissionDenied
                    PermissionState.PermanentlyDenied -> SpendingError.PermissionPermanentlyDenied
                    else -> null
                },
            )
        }
    }

    fun loadSpending(limit: Int = 100) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            getSpendingByCategoryUseCase(limit)
                .onSuccess { summary ->
                    val error = if (summary.transactionCount == 0) {
                        SpendingError.NoTransactions
                    } else {
                        null
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            summary = summary,
                            error = error,
                        )
                    }
                }
                .onFailure { exception ->
                    val error = when (exception) {
                        is SmsException -> {
                            when (val smsError = exception.error) {
                                is SmsError.PermissionDenied -> SpendingError.PermissionDenied
                                is SmsError.PlatformNotSupported -> SpendingError.PlatformNotSupported
                                is SmsError.PermissionPermanentlyDenied -> SpendingError.PermissionPermanentlyDenied
                                is SmsError.EmptyInbox -> SpendingError.NoTransactions
                                is SmsError.Unknown -> SpendingError.Generic(smsError.message)
                            }
                        }
                        else -> SpendingError.Generic(exception.message ?: "Unknown error")
                    }
                    _uiState.update { it.copy(isLoading = false, error = error) }
                }
        }
    }

    fun refresh() {
        if (_uiState.value.permissionState == PermissionState.Granted) {
            loadSpending()
        } else {
            checkPermission()
        }
    }
}

sealed class SpendingEvent {
    data object RequestPermission : SpendingEvent()
    data object OpenSettings : SpendingEvent()
}
