package com.example.moneytap.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneytap.data.datasource.local.PermissionHandler
import com.example.moneytap.domain.model.PermissionState
import com.example.moneytap.domain.model.SmsError
import com.example.moneytap.domain.model.SmsException
import com.example.moneytap.domain.repository.SmsRepository
import com.example.moneytap.domain.usecase.GetSmsMessagesUseCase
import com.example.moneytap.presentation.state.SmsUiError
import com.example.moneytap.presentation.state.SmsUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SmsViewModel(
    private val getSmsMessagesUseCase: GetSmsMessagesUseCase,
    private val permissionHandler: PermissionHandler,
    private val smsRepository: SmsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmsUiState())
    val uiState: StateFlow<SmsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SmsEvent>()
    val events: SharedFlow<SmsEvent> = _events.asSharedFlow()

    init {
        checkPlatformSupport()
        observePermissionState()
    }

    private fun checkPlatformSupport() {
        val isSupported = smsRepository.isPlatformSupported()
        _uiState.update { it.copy(isPlatformSupported = isSupported) }
        if (!isSupported) {
            _uiState.update { it.copy(error = SmsUiError.PlatformNotSupported) }
        }
    }

    private fun observePermissionState() {
        viewModelScope.launch {
            permissionHandler.smsPermissionState.collect { state ->
                _uiState.update { it.copy(permissionState = state) }
                if (state == PermissionState.Granted) {
                    loadMessages()
                }
            }
        }
    }

    fun checkPermission() {
        val state = permissionHandler.checkSmsPermission()
        if (state == PermissionState.Granted) {
            loadMessages()
        } else {
            viewModelScope.launch {
                _events.emit(SmsEvent.RequestPermission)
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
                    PermissionState.Denied -> SmsUiError.PermissionDenied
                    PermissionState.PermanentlyDenied -> SmsUiError.PermissionPermanentlyDenied
                    else -> null
                },
            )
        }
    }

    fun loadMessages(limit: Int = 100) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            getSmsMessagesUseCase(limit)
                .onSuccess { messages ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            messages = messages,
                            error = null,
                        )
                    }
                }
                .onFailure { exception ->
                    val error = when (exception) {
                        is SmsException -> {
                            val smsError = exception.error
                            when (smsError) {
                                is SmsError.PermissionDenied -> SmsUiError.PermissionDenied
                                is SmsError.PlatformNotSupported -> SmsUiError.PlatformNotSupported
                                is SmsError.PermissionPermanentlyDenied -> SmsUiError.PermissionPermanentlyDenied
                                is SmsError.EmptyInbox -> null
                                is SmsError.Unknown -> SmsUiError.Generic(smsError.message)
                            }
                        }
                        else -> SmsUiError.Generic(exception.message ?: "Unknown error")
                    }
                    _uiState.update { it.copy(isLoading = false, error = error) }
                }
        }
    }

    fun refresh() {
        if (_uiState.value.permissionState == PermissionState.Granted) {
            loadMessages()
        } else {
            checkPermission()
        }
    }
}

sealed class SmsEvent {
    data object RequestPermission : SmsEvent()
    data object OpenSettings : SmsEvent()
}
