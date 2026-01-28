package com.example.moneytap.domain.model

/**
 * Aggregated income and expense totals for a single month.
 *
 * @property month The month in "YYYY-MM" format
 * @property expenses Total spending (debits and withdrawals) for the month
 * @property income Total income (credits) for the month
 */
data class MonthlyTotal(
    val month: String,
    val expenses: Double,
    val income: Double,
)
