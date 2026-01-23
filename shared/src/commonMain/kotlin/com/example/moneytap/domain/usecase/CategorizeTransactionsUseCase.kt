package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.service.CategorizationEngine

/**
 * Use case for categorizing a list of transactions.
 *
 * @property categorizationEngine The engine used for transaction categorization
 */
class CategorizeTransactionsUseCase(
    private val categorizationEngine: CategorizationEngine,
) {
    /**
     * Categorizes a list of transactions.
     *
     * @param transactions The transactions to categorize
     * @return List of categorized transactions
     */
    operator fun invoke(transactions: List<TransactionInfo>): List<CategorizedTransaction> =
        categorizationEngine.categorizeAll(transactions)

    /**
     * Categorizes a single transaction.
     *
     * @param transaction The transaction to categorize
     * @return The categorized transaction
     */
    fun categorize(transaction: TransactionInfo): CategorizedTransaction =
        categorizationEngine.categorize(transaction)
}
