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
                val hasParser = BankSmsParserFactory.getParser(sms.sender) != null
                val canGeneric = BankSmsParserFactory.canGenericParse(sms.body)
                println("DEBUG: SMS sender='${sms.sender}', hasParser=$hasParser, canGenericParse=$canGeneric")
            }
            val parsed = messages.mapNotNull { sms -> parseMessage(sms) }
            println("DEBUG: Parsed transactions: ${parsed.size}")
            parsed
        }
    }

    /**
     * Attempts to parse a single SMS message into a transaction.
     *
     * First tries to find a specific bank parser for the sender.
     * If no specific parser exists, falls back to the generic parser
     * which looks for transaction keywords in the message body.
     *
     * @param sms The SMS message to parse
     * @return Parsed [TransactionInfo] if the message is a recognized transaction, null otherwise
     */
    fun parseMessage(sms: SmsMessage): TransactionInfo? {
        // First, try to find a specific bank parser
        val specificParser = BankSmsParserFactory.getParser(sms.sender)
        if (specificParser != null) {
            return specificParser.parse(sms.body, sms.timestamp)?.copy(smsId = sms.id)
        }

        // Fallback: try generic parser if message contains transaction keywords
        if (BankSmsParserFactory.canGenericParse(sms.body)) {
            return BankSmsParserFactory.getGenericParser().parse(sms.body, sms.timestamp)
                ?.copy(smsId = sms.id)
        }

        return null
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
        // Try specific parser first
        val specificParser = BankSmsParserFactory.getParser(senderId)
        if (specificParser != null) {
            return specificParser.parse(body, timestamp)
        }

        // Fallback to generic parser
        if (BankSmsParserFactory.canGenericParse(body)) {
            return BankSmsParserFactory.getGenericParser().parse(body, timestamp)
        }

        return null
    }

    /**
     * Returns the list of banks that can be parsed.
     */
    fun getSupportedBanks(): List<String> = BankSmsParserFactory.getSupportedBanks()
}
