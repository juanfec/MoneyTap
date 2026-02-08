package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.Constants
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
     * Optimized to skip parsing SMS messages that are already in the database.
     * Only new (unseen) SMS messages are parsed and categorized.
     *
     * @param limit Maximum number of SMS messages to process
     * @param spendingOnly If true, only includes DEBIT/WITHDRAWAL. If false, includes all transactions.
     * @return [Result] containing the spending summary, or an error
     */
    suspend operator fun invoke(
        limit: Int = Constants.DEFAULT_SMS_LIMIT,
        spendingOnly: Boolean = false,
    ): Result<SpendingSummary> {
        // Get already stored SMS IDs first to avoid re-parsing
        val storedIds = transactionRepository.getStoredSmsIds()
        println("DEBUG GetSpendingByCategory: ${storedIds.size} SMS IDs already in database")

        // Parse only new SMS messages (those not in database)
        return parseSmsTransactionsUseCase(limit, storedIds).map { parsed ->
            println("DEBUG GetSpendingByCategory: Parsed ${parsed.size} NEW transactions from SMS")

            if (parsed.isNotEmpty()) {
                val categorized = categorizeTransactionsUseCase(parsed)
                transactionRepository.insertTransactions(categorized)
                println("DEBUG GetSpendingByCategory: Saved ${categorized.size} categorized transactions")
            }

            val allTransactions = transactionRepository.getAllTransactions()
            println("DEBUG GetSpendingByCategory: Total ${allTransactions.size} transactions in database")
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

        val totalSpending = filteredTransactions
            .filter { !it.category.excludeFromSpending }
            .sumOf { it.transaction.amount }

        return SpendingSummary(
            totalSpending = totalSpending,
            byCategory = sortedByCategory,
            transactionCount = filteredTransactions.size,
        )
    }

    private fun isSpendingTransaction(transaction: CategorizedTransaction): Boolean =
        transaction.transaction.type in listOf(TransactionType.DEBIT, TransactionType.WITHDRAWAL, TransactionType.TRANSFER)
}
