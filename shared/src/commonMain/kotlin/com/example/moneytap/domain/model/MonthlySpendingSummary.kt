package com.example.moneytap.domain.model

/**
 * Monthly spending summary with balance information.
 *
 * @property month The year-month this summary represents
 * @property totalIncome Total credits/income for the month
 * @property totalExpenses Total debits/withdrawals for the month
 * @property balance Net balance (income - expenses)
 * @property byCategory Category breakdown (reuses existing SpendingSummary structure)
 * @property transactionCount Total transactions in the month
 */
data class MonthlySpendingSummary(
    val month: YearMonth,
    val totalIncome: Double,
    val totalExpenses: Double,
    val balance: Double = totalIncome - totalExpenses,
    val byCategory: Map<Category, CategorySpending>,
    val transactionCount: Int,
)
