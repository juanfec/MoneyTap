package com.example.moneytap.domain.model

/**
 * Aggregated spending summary across all categories.
 *
 * @property totalSpending Total amount spent across all transactions
 * @property byCategory Spending breakdown by category
 * @property transactionCount Total number of categorized transactions
 */
data class SpendingSummary(
    val totalSpending: Double,
    val byCategory: Map<Category, CategorySpending>,
    val transactionCount: Int,
)

/**
 * Spending totals for a single category.
 *
 * @property category The spending category
 * @property totalAmount Total amount spent in this category
 * @property transactionCount Number of transactions in this category
 * @property transactions List of all transactions in this category
 */
data class CategorySpending(
    val category: Category,
    val totalAmount: Double,
    val transactionCount: Int,
    val transactions: List<CategorizedTransaction>,
)
