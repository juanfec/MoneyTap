package com.example.moneytap.domain.usecase

import com.example.moneytap.data.parser.BankSmsParserFactory
import com.example.moneytap.domain.model.SmsMessage
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.repository.SmsRepository

/**
 * Use case for parsing bank transaction SMS messages.
 *
 * This use case orchestrates the retrieval of SMS messages and their parsing
 * into structured [TransactionInfo] objects using the appropriate bank parser.
 *
 * @property smsRepository Repository for accessing SMS messages
 */
class ParseSmsTransactionsUseCase(
    private val smsRepository: SmsRepository,
) {
    /**
     * Retrieves and parses transaction SMS messages from the inbox.
     *
     * @param limit Maximum number of SMS messages to retrieve
     * @return [Result] containing a list of parsed transactions, or an error
     */
    suspend operator fun invoke(limit: Int = 100): Result<List<TransactionInfo>> {
        return smsRepository.getInboxMessages(limit).map { messages ->
            println("DEBUG: Total SMS messages fetched: ${messages.size}")
            messages.take(10).forEach { sms ->
                println("DEBUG: SMS sender='${sms.sender}', canParse=${BankSmsParserFactory.getParser(sms.sender) != null}")
            }
            val parsed = messages.mapNotNull { sms -> parseMessage(sms) }
            println("DEBUG: Parsed transactions: ${parsed.size}")
            parsed
        }
    }

    /**
     * Attempts to parse a single SMS message into a transaction.
     *
     * @param sms The SMS message to parse
     * @return Parsed [TransactionInfo] if the message is a recognized bank transaction, null otherwise
     */
    fun parseMessage(sms: SmsMessage): TransactionInfo? {
        val parser = BankSmsParserFactory.getParser(sms.sender) ?: return null
        return parser.parse(sms.body, sms.timestamp)
    }

    /**
     * Parses a raw SMS body from a known sender.
     *
     * Useful for testing or when the SMS metadata is already known.
     *
     * @param senderId The sender ID or phone number
     * @param body The SMS body text
     * @param timestamp When the SMS was received
     * @return Parsed [TransactionInfo] if successful, null otherwise
     */
    fun parseRawMessage(
        senderId: String,
        body: String,
        timestamp: kotlinx.datetime.Instant,
    ): TransactionInfo? {
        val parser = BankSmsParserFactory.getParser(senderId) ?: return null
        return parser.parse(body, timestamp)
    }

    /**
     * Returns the list of banks that can be parsed.
     */
    fun getSupportedBanks(): List<String> = BankSmsParserFactory.getSupportedBanks()
}
