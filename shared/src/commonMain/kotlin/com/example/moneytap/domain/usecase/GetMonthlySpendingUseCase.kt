package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.CategorySpending
import com.example.moneytap.domain.model.MonthlySpendingSummary
import com.example.moneytap.domain.model.TransactionType
import com.example.moneytap.domain.model.YearMonth
import com.example.moneytap.domain.repository.TransactionRepository

/**
 * Use case for getting spending summary for a specific month.
 *
 * Unlike GetSpendingByCategoryUseCase which processes all transactions,
 * this filters by the selected month for faster queries on historical data.
 */
class GetMonthlySpendingUseCase(
    private val transactionRepository: TransactionRepository,
) {
    /**
     * Returns spending summary for a specific month.
     */
    suspend operator fun invoke(month: YearMonth): Result<MonthlySpendingSummary> {
        return runCatching {
            val transactions = transactionRepository.getTransactionsByDateRange(
                startDate = month.startOfMonth(),
                endDate = month.endOfMonth(),
            )
            aggregateMonthlySpending(month, transactions)
        }
    }

    private fun aggregateMonthlySpending(
        month: YearMonth,
        transactions: List<CategorizedTransaction>,
    ): MonthlySpendingSummary {
        println("DEBUG GetMonthlySpending: Processing ${transactions.size} transactions for month $month")
        
        // Log all transaction types
        val typeBreakdown = transactions.groupBy { it.transaction.type }
        println("DEBUG GetMonthlySpending: Transaction types: ${typeBreakdown.mapValues { it.value.size }}")
        
        val incomeTransactions = transactions
            .filter { it.transaction.type == TransactionType.CREDIT }
        
        val incomeExcluded = incomeTransactions
            .filter { it.category.excludeFromSpending }
        
        val totalIncome = incomeTransactions
            .filter { !it.category.excludeFromSpending }
            .sumOf { it.transaction.amount }
        
        println("DEBUG GetMonthlySpending: Income - ${incomeTransactions.size} total, ${incomeExcluded.size} excluded (${incomeExcluded.map { "${it.category.displayName}: ${it.transaction.amount}" }}), final: $totalIncome")

        val expenseTransactions = transactions
            .filter { it.transaction.type in listOf(TransactionType.DEBIT, TransactionType.WITHDRAWAL, TransactionType.TRANSFER) }
        
        val expensesExcluded = expenseTransactions
            .filter { it.category.excludeFromSpending }
        
        val totalExpenses = expenseTransactions
            .filter { !it.category.excludeFromSpending }
            .sumOf { it.transaction.amount }
        
        println("DEBUG GetMonthlySpending: Expenses - ${expenseTransactions.size} total, ${expensesExcluded.size} excluded (${expensesExcluded.map { "${it.category.displayName}: ${it.transaction.amount}" }}), final: $totalExpenses")
        
        // Log skipped transactions
        val processedCount = incomeTransactions.size + expenseTransactions.size
        val skippedCount = transactions.size - processedCount
        if (skippedCount > 0) {
            val skipped = transactions.filter { 
                it.transaction.type != TransactionType.CREDIT && 
                it.transaction.type !in listOf(TransactionType.DEBIT, TransactionType.WITHDRAWAL, TransactionType.TRANSFER)
            }
            println("DEBUG GetMonthlySpending: WARNING - $skippedCount transactions skipped! Types: ${skipped.map { "${it.transaction.type}: ${it.transaction.amount} (${it.category.displayName})" }}")
        }

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
        
        val balance = totalIncome - totalExpenses
        println("DEBUG GetMonthlySpending: Balance = $totalIncome (income) - $totalExpenses (expenses) = $balance")

        return MonthlySpendingSummary(
            month = month,
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            byCategory = byCategory,
            transactionCount = transactions.size,
        )
    }
}
