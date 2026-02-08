package com.example.moneytap.domain.repository

import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.MonthlyTotal
import com.example.moneytap.domain.model.TransactionType
import kotlinx.datetime.Instant

/**
 * Repository for persisting and querying categorized transactions.
 */
interface TransactionRepository {

    /**
     * Inserts or replaces a single categorized transaction.
     */
    suspend fun insertTransaction(transaction: CategorizedTransaction)

    /**
     * Inserts or replaces a batch of categorized transactions.
     */
    suspend fun insertTransactions(transactions: List<CategorizedTransaction>)

    /**
     * Returns all stored transactions, ordered by timestamp descending.
     */
    suspend fun getAllTransactions(): List<CategorizedTransaction>

    /**
     * Returns transactions within a date range, ordered by timestamp descending.
     */
    suspend fun getTransactionsByDateRange(
        startDate: Instant,
        endDate: Instant,
    ): List<CategorizedTransaction>

    /**
     * Returns monthly income and expense totals for the last 12 months.
     */
    suspend fun getMonthlyTotals(): List<MonthlyTotal>

    /**
     * Returns the set of SMS IDs already stored in the database.
     * Used to skip re-processing of previously categorized messages.
     */
    suspend fun getStoredSmsIds(): Set<Long>

    /**
     * Returns the total number of stored transactions.
     */
    suspend fun getTransactionCount(): Long

    /**
     * Deletes all stored transactions.
     */
    suspend fun deleteAllTransactions()

    /**
     * Returns a single transaction by SMS ID, or null if not found.
     */
    suspend fun getTransactionBySmsId(smsId: Long): CategorizedTransaction?

    /**
     * Updates the category of a transaction and marks it as user-corrected.
     */
    suspend fun updateTransactionCategory(smsId: Long, newCategory: Category)

    /**
     * Updates the type of a transaction (e.g., DEBIT to CREDIT).
     */
    suspend fun updateTransactionType(smsId: Long, newType: TransactionType)
}
