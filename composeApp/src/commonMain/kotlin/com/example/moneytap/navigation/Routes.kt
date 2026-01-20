package com.example.moneytap.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for the app.
 * Each route is a serializable object/class for compile-time safety.
 */
sealed interface Route {

    @Serializable
    data object SmsInbox : Route

    // Add more routes here as needed, for example:
    // @Serializable
    // data object Settings : Route
    //
    // @Serializable
    // data class SmsDetail(val messageId: Long) : Route
}
