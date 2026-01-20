package com.example.moneytap.data.parser

import com.example.moneytap.domain.model.TransactionInfo
import kotlinx.datetime.Instant

/**
 * Strategy interface for parsing bank-specific SMS transaction messages.
 *
 * Each bank implementation handles its own SMS format and patterns.
 * Implementations should be registered in [BankSmsParserFactory].
 */
interface BankSmsParser {

    /**
     * Human-readable name of the bank this parser handles.
     */
    val bankName: String

    /**
     * List of sender IDs (phone numbers or short codes) that identify
     * SMS messages from this bank. Used for parser selection.
     */
    val senderIds: List<String>

    /**
     * Attempts to parse a transaction from the given SMS body.
     *
     * @param smsBody The full text content of the SMS message
     * @param timestamp When the SMS was received
     * @return Parsed [TransactionInfo] if the message is a recognized transaction, null otherwise
     */
    fun parse(smsBody: String, timestamp: Instant): TransactionInfo?

    /**
     * Checks if this parser can handle messages from the given sender.
     *
     * @param senderId The sender phone number or short code
     * @return true if this parser should handle messages from this sender
     */
    fun canHandle(senderId: String): Boolean =
        senderIds.any { senderId.contains(it, ignoreCase = true) }
}
