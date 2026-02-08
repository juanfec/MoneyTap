package com.example.moneytap.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneytap.data.datasource.local.PermissionHandler
import com.example.moneytap.domain.Constants
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.CategorySpending
import com.example.moneytap.domain.model.PermissionState
import com.example.moneytap.domain.model.SmsError
import com.example.moneytap.domain.model.SmsException
import com.example.moneytap.domain.model.SpendingSummary
import com.example.moneytap.domain.model.TransactionType
import com.example.moneytap.domain.model.YearMonth
import com.example.moneytap.domain.repository.SmsRepository
import com.example.moneytap.domain.repository.TransactionRepository
import com.example.moneytap.domain.usecase.GetAvailableMonthsUseCase
import com.example.moneytap.domain.usecase.GetMonthlySpendingUseCase
import com.example.moneytap.domain.usecase.GetSpendingByCategoryUseCase
import com.example.moneytap.domain.usecase.UpdateTransactionCategoryUseCase
import com.example.moneytap.domain.usecase.UpdateTransactionTypeUseCase
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
    private val getMonthlySpendingUseCase: GetMonthlySpendingUseCase,
    private val getAvailableMonthsUseCase: GetAvailableMonthsUseCase,
    private val updateTransactionCategoryUseCase: UpdateTransactionCategoryUseCase,
    private val updateTransactionTypeUseCase: UpdateTransactionTypeUseCase,
    private val transactionRepository: TransactionRepository,
    private val permissionHandler: PermissionHandler,
    private val smsRepository: SmsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpendingUiState())
    val uiState: StateFlow<SpendingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SpendingEvent>()
    val events: SharedFlow<SpendingEvent> = _events.asSharedFlow()

    private var hasLoadedData = false

    init {
        checkPlatformSupport()
        loadFromDatabase() // Load existing data from DB on init
        loadAvailableMonths()
        
        // Check current permission state and load if already granted
        val currentPermissionState = permissionHandler.checkSmsPermission()
        _uiState.update { it.copy(permissionState = currentPermissionState) }
        
        if (currentPermissionState == PermissionState.Granted) {
            // Load data from DB if available, or parse SMS if DB is empty
            viewModelScope.launch {
                val transactionCount = transactionRepository.getTransactionCount()
                if (transactionCount == 0L && !hasLoadedData) {
                    // No data in DB, need to parse SMS for the first time
                    loadSpending()
                    hasLoadedData = true
                }
            }
        }
        
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
                val previousState = _uiState.value.permissionState
                _uiState.update { it.copy(permissionState = state) }
                
                // Only load spending when permission CHANGES from non-granted to granted
                if (state == PermissionState.Granted && 
                    previousState != PermissionState.Granted && 
                    !hasLoadedData) {
                    loadSpending() // Parse SMS only on first permission grant
                    hasLoadedData = true
                }
            }
        }
    }

    fun checkPermission() {
        val state = permissionHandler.checkSmsPermission()
        if (state == PermissionState.Granted && !hasLoadedData) {
            loadSpending() // Parse SMS only on first permission grant
            hasLoadedData = true
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

    /**
     * Load spending data from database only (no SMS parsing).
     * Called on init to show existing data immediately.
     */
    private fun loadFromDatabase() {
        viewModelScope.launch {
            runCatching {
                val allTransactions = transactionRepository.getAllTransactions()
                if (allTransactions.isNotEmpty()) {
                    // Aggregate spending from database
                    val summary = aggregateSpendingFromDb(allTransactions)
                    _uiState.update {
                        it.copy(
                            summary = summary,
                            error = null,
                        )
                    }
                }
            }.onFailure { exception ->
                println("DEBUG: Failed to load from database: ${exception.message}")
            }
        }
    }

    /**
     * Helper to aggregate spending from database transactions.
     */
    private fun aggregateSpendingFromDb(transactions: List<CategorizedTransaction>): SpendingSummary {
        val byCategory = transactions
            .groupBy { it.category }
            .mapValues { (category, txs) ->
                CategorySpending(
                    category = category,
                    totalAmount = txs.sumOf { it.transaction.amount },
                    transactionCount = txs.size,
                    transactions = txs.sortedByDescending { it.transaction.timestamp },
                )
            }
            .entries
            .sortedByDescending { it.value.totalAmount }
            .associate { it.key to it.value }

        val totalSpending = transactions
            .filter { it.transaction.type in listOf(TransactionType.DEBIT, TransactionType.WITHDRAWAL, TransactionType.TRANSFER) }
            .filter { !it.category.excludeFromSpending }
            .sumOf { it.transaction.amount }

        return SpendingSummary(
            byCategory = byCategory,
            totalSpending = totalSpending,
            transactionCount = transactions.size,
        )
    }

    fun loadSpending(limit: Int = Constants.DEFAULT_SMS_LIMIT) {
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
            hasLoadedData = true // Mark as loaded to prevent auto-loading on navigation
            loadSpending()
            loadAvailableMonths()
        } else {
            checkPermission()
        }
    }

    /**
     * Load available months and set initial selection to current month.
     */
    private fun loadAvailableMonths() {
        viewModelScope.launch {
            getAvailableMonthsUseCase()
                .onSuccess { months ->
                    val currentMonth = YearMonth.current()
                    val selectedMonth = if (months.contains(currentMonth)) {
                        currentMonth
                    } else {
                        months.firstOrNull()
                    }

                    _uiState.update {
                        it.copy(
                            availableMonths = months,
                            selectedMonth = selectedMonth,
                        )
                    }

                    selectedMonth?.let { loadMonthlySpending(it) }
                }
        }
    }

    /**
     * Navigate to previous month (if available).
     */
    fun previousMonth() {
        val current = _uiState.value.selectedMonth ?: return
        val available = _uiState.value.availableMonths
        val currentIndex = available.indexOf(current)

        // Previous month = next in list (since sorted descending)
        if (currentIndex < available.lastIndex) {
            selectMonth(available[currentIndex + 1])
        }
    }

    /**
     * Navigate to next month (if available).
     */
    fun nextMonth() {
        val current = _uiState.value.selectedMonth ?: return
        val available = _uiState.value.availableMonths
        val currentIndex = available.indexOf(current)

        // Next month = previous in list (since sorted descending)
        if (currentIndex > 0) {
            selectMonth(available[currentIndex - 1])
        }
    }

    /**
     * Jump to a specific month.
     */
    fun selectMonth(month: YearMonth) {
        _uiState.update { it.copy(selectedMonth = month) }
        loadMonthlySpending(month)
    }

    private fun loadMonthlySpending(month: YearMonth) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            getMonthlySpendingUseCase(month)
                .onSuccess { summary ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            monthlySummary = summary,
                            error = if (summary.transactionCount == 0) {
                                SpendingError.NoTransactions
                            } else {
                                null
                            },
                        )
                    }
                }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = SpendingError.Generic(exception.message ?: "Unknown error"),
                        )
                    }
                }
        }
    }

    /**
     * Check if can navigate to previous month.
     */
    val canGoToPreviousMonth: Boolean
        get() {
            val current = _uiState.value.selectedMonth ?: return false
            val available = _uiState.value.availableMonths
            val currentIndex = available.indexOf(current)
            return currentIndex < available.lastIndex
        }

    /**
     * Check if can navigate to next month.
     */
    val canGoToNextMonth: Boolean
        get() {
            val current = _uiState.value.selectedMonth ?: return false
            val available = _uiState.value.availableMonths
            val currentIndex = available.indexOf(current)
            return currentIndex > 0
        }

    /**
     * Manually change the category of a transaction.
     * This marks it as user-corrected and reloads all views to stay synchronized.
     */
    fun changeTransactionCategory(smsId: Long, newCategory: Category) {
        viewModelScope.launch {
            updateTransactionCategoryUseCase(smsId, newCategory)
                .onSuccess {
                    // Reload from database to reflect the category change
                    loadFromDatabase()
                    _uiState.value.selectedMonth?.let { loadMonthlySpending(it) }
                }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(error = SpendingError.Generic(exception.message ?: "Failed to update category"))
                    }
                }
        }
    }

    /**
     * Manually change the type of a transaction (e.g., DEBIT to CREDIT).
     * This marks it as user-corrected and reloads all views to stay synchronized.
     */
    fun changeTransactionType(smsId: Long, newType: TransactionType) {
        viewModelScope.launch {
            updateTransactionTypeUseCase(smsId, newType)
                .onSuccess {
                    // Reload from database to reflect the type change
                    loadFromDatabase()
                    _uiState.value.selectedMonth?.let { loadMonthlySpending(it) }
                }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(error = SpendingError.Generic(exception.message ?: "Failed to update type"))
                    }
                }
        }
    }
}

sealed class SpendingEvent {
    data object RequestPermission : SpendingEvent()
    data object OpenSettings : SpendingEvent()
}
