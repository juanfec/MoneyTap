package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.CategorySpending
import com.example.moneytap.domain.model.SpendingSummary
import com.example.moneytap.domain.model.TransactionType
import com.example.moneytap.domain.repository.TransactionRepository

/**
 * Use case for aggregating spending by category.
 *
 * Uses the SMS ID to determine which messages have already been processed.
 * Only new (unseen) SMS messages are parsed and categorized; previously
 * processed transactions are loaded directly from the local database.
 *
 * @property parseSmsTransactionsUseCase Use case for parsing SMS into transactions
 * @property categorizeTransactionsUseCase Use case for categorizing transactions
 * @property transactionRepository Repository for persisting categorized transactions
 */
class GetSpendingByCategoryUseCase(
    private val parseSmsTransactionsUseCase: ParseSmsTransactionsUseCase,
    private val categorizeTransactionsUseCase: CategorizeTransactionsUseCase,
    private val transactionRepository: TransactionRepository,
) {
    /**
     * Retrieves and aggregates spending by category.
     *
     * Parses SMS messages, checks which ones are already stored in the database
     * by their SMS ID, categorizes only the new ones, saves them, and returns
     * an aggregated summary of all transactions (existing + newly categorized).
     *
     * @param limit Maximum number of SMS messages to process
     * @param spendingOnly If true, only includes DEBIT/WITHDRAWAL. If false, includes all transactions.
     * @return [Result] containing the spending summary, or an error
     */
    suspend operator fun invoke(
        limit: Int = 100,
        spendingOnly: Boolean = false,
    ): Result<SpendingSummary> {
        return parseSmsTransactionsUseCase(limit).map { parsed ->
            val storedIds = transactionRepository.getStoredSmsIds()
            val newTransactions = parsed.filter { it.smsId !in storedIds }

            if (newTransactions.isNotEmpty()) {
                val categorized = categorizeTransactionsUseCase(newTransactions)
                transactionRepository.insertTransactions(categorized)
            }

            val allTransactions = transactionRepository.getAllTransactions()
            aggregateSpending(allTransactions, spendingOnly)
        }
    }

    /**
     * Aggregates a list of categorized transactions into a spending summary.
     */
    private fun aggregateSpending(
        transactions: List<CategorizedTransaction>,
        spendingOnly: Boolean,
    ): SpendingSummary {
        val filteredTransactions = if (spendingOnly) {
            transactions.filter { isSpendingTransaction(it) }
        } else {
            transactions
        }

        val byCategory = filteredTransactions
            .groupBy { it.category }
            .mapValues { (category, categoryTransactions) ->
                CategorySpending(
                    category = category,
                    totalAmount = categoryTransactions.sumOf { it.transaction.amount },
                    transactionCount = categoryTransactions.size,
                    transactions = categoryTransactions,
                )
            }

        val sortedByCategory = byCategory.entries
            .sortedByDescending { it.value.totalAmount }
            .associate { it.key to it.value }

        return SpendingSummary(
            totalSpending = filteredTransactions.sumOf { it.transaction.amount },
            byCategory = sortedByCategory,
            transactionCount = filteredTransactions.size,
        )
    }

    private fun isSpendingTransaction(transaction: CategorizedTransaction): Boolean =
        transaction.transaction.type in listOf(TransactionType.DEBIT, TransactionType.WITHDRAWAL)
}
