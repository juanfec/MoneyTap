package com.example.moneytap.domain.model

import kotlinx.datetime.Instant

/**
 * Domain model representing a parsed financial transaction from an SMS message.
 *
 * @property smsId The unique ID of the source SMS message from the system inbox
 * @property type The category of transaction (debit, credit, transfer, withdrawal)
 * @property amount The transaction amount as a positive value
 * @property currency The currency code (defaults to COP for Colombian Peso)
 * @property balance Account balance after the transaction, if available
 * @property cardLast4 Last 4 digits of the card used, if available
 * @property merchant The merchant or recipient name, if available
 * @property description Additional transaction description, if available
 * @property reference Transaction reference number, if available
 * @property bankName Name of the bank that sent the SMS
 * @property timestamp When the SMS was received
 * @property rawMessage The original SMS body for reference
 */
data class TransactionInfo(
    val smsId: Long = 0,
    val type: TransactionType,
    val amount: Double,
    val currency: String = "COP",
    val balance: Double? = null,
    val cardLast4: String? = null,
    val merchant: String? = null,
    val description: String? = null,
    val reference: String? = null,
    val bankName: String,
    val timestamp: Instant,
    val rawMessage: String,
)
