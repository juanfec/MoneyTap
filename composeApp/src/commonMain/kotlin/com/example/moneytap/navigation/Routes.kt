package com.example.moneytap.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for the app.
 * Each route is a serializable object/class for compile-time safety.
 */
sealed interface Route {

    @Serializable
    data object SmsInbox : Route

    @Serializable
    data object SpendingSummary : Route

    @Serializable
    data class CategoryTransactions(val categoryName: String) : Route

    @Serializable
    data class TransactionDetail(
        val categoryName: String,
        val transactionIndex: Int,
    ) : Route

    @Serializable
    data object Teaching : Route

    @Serializable
    data object CategoryTeaching : Route
}
