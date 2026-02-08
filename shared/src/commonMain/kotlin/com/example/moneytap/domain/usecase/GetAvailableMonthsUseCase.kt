package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.model.YearMonth
import com.example.moneytap.domain.repository.TransactionRepository

/**
 * Returns the list of months that have transaction data.
 * Used to populate the month selector and determine navigation bounds.
 */
class GetAvailableMonthsUseCase(
    private val transactionRepository: TransactionRepository,
) {
    /**
     * Returns available months sorted newest first.
     */
    suspend operator fun invoke(): Result<List<YearMonth>> {
        return runCatching {
            transactionRepository.getMonthlyTotals()
                .map { YearMonth(it.month) }
                .sortedDescending()
        }
    }
}
