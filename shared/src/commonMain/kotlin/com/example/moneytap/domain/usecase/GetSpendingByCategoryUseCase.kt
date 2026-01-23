package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.CategorySpending
import com.example.moneytap.domain.model.SpendingSummary
import com.example.moneytap.domain.model.TransactionType

/**
 * Use case for aggregating spending by category.
 *
 * Fetches SMS transactions, parses them, categorizes them, and aggregates
 * spending totals by category.
 *
 * @property parseSmsTransactionsUseCase Use case for parsing SMS into transactions
 * @property categorizeTransactionsUseCase Use case for categorizing transactions
 */
class GetSpendingByCategoryUseCase(
    private val parseSmsTransactionsUseCase: ParseSmsTransactionsUseCase,
    private val categorizeTransactionsUseCase: CategorizeTransactionsUseCase,
) {
    /**
     * Retrieves and aggregates spending by category.
     *
     * @param limit Maximum number of SMS messages to process
     * @param spendingOnly If true, only includes DEBIT/WITHDRAWAL. If false, includes all transactions.
     * @return [Result] containing the spending summary, or an error
     */
    suspend operator fun invoke(
        limit: Int = 100,
        spendingOnly: Boolean = false,
    ): Result<SpendingSummary> {
        return parseSmsTransactionsUseCase(limit).map { transactions ->
            val categorized = categorizeTransactionsUseCase(transactions)
            aggregateSpending(categorized, spendingOnly)
        }
    }

    /**
     * Aggregates a list of categorized transactions into a spending summary.
     *
     * @param transactions The categorized transactions to aggregate
     * @param spendingOnly If true, only includes DEBIT/WITHDRAWAL transactions
     * @return The aggregated spending summary
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

    /**
     * Determines if a transaction represents spending (money going out).
     */
    private fun isSpendingTransaction(transaction: CategorizedTransaction): Boolean =
        transaction.transaction.type in listOf(TransactionType.DEBIT, TransactionType.WITHDRAWAL)
}
