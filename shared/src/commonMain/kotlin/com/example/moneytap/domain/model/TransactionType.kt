package com.example.moneytap.domain.model

/**
 * Represents the type of financial transaction parsed from bank SMS messages.
 */
enum class TransactionType {
    /** Purchase or payment (money going out for goods/services) */
    DEBIT,

    /** Deposit or income (money coming in) */
    CREDIT,

    /** Money movement between accounts */
    TRANSFER,

    /** ATM or cash withdrawal */
    WITHDRAWAL,
}
