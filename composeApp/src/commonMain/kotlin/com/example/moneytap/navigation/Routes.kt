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
}
