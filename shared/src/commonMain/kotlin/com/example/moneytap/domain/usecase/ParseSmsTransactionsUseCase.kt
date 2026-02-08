package com.example.moneytap.domain.usecase

import com.example.moneytap.data.parser.BankSmsParserFactory
import com.example.moneytap.domain.Constants
import com.example.moneytap.domain.model.FieldType
import com.example.moneytap.domain.model.SmsMessage
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.model.TransactionType
import com.example.moneytap.domain.repository.SmsRepository
import com.example.moneytap.domain.repository.UserPatternRepository
import com.example.moneytap.domain.service.FuzzyPatternMatcher
import kotlinx.coroutines.Dispatchers

/**
 * Use case for parsing bank transaction SMS messages.
 *
 * This use case orchestrates the retrieval of SMS messages and their parsing
 * into structured [TransactionInfo] objects using the appropriate bank parser.
 * Supports user-defined patterns that are tried before built-in parsers.
 *
 * @property smsRepository Repository for accessing SMS messages
 * @property userPatternRepository Optional repository for user-defined patterns
 * @property fuzzyMatcher Optional matcher for user patterns
 */
class ParseSmsTransactionsUseCase(
    private val smsRepository: SmsRepository,
    private val userPatternRepository: UserPatternRepository? = null,
    private val fuzzyMatcher: FuzzyPatternMatcher? = null,
) {
    /**
     * Retrieves and parses transaction SMS messages from the inbox.
     *
     * @param limit Maximum number of SMS messages to retrieve
     * @param excludeIds Set of SMS IDs to skip (already processed)
     * @return [Result] containing a list of parsed transactions, or an error
     */
    suspend operator fun invoke(
        limit: Int = Constants.DEFAULT_SMS_LIMIT,
        excludeIds: Set<Long> = emptySet(),
    ): Result<List<TransactionInfo>> {
        return smsRepository.getInboxMessages(limit).map { messages ->
            println("DEBUG: Total SMS messages fetched: ${messages.size}")
            
            // Filter out already-processed messages BEFORE parsing
            val newMessages = if (excludeIds.isNotEmpty()) {
                messages.filter { it.id !in excludeIds }
            } else {
                messages
            }
            println("DEBUG: Filtering out ${messages.size - newMessages.size} already-processed messages")
            println("DEBUG: Parsing ${newMessages.size} new messages")
            
            newMessages.take(10).forEach { sms ->
                val hasParser = BankSmsParserFactory.getParser(sms.sender) != null
                val canGeneric = BankSmsParserFactory.canGenericParse(sms.body)
                println("DEBUG: SMS sender='${sms.sender}', hasParser=$hasParser, canGenericParse=$canGeneric")
            }
            val parsed = newMessages.mapNotNull { sms -> parseMessage(sms) }
            println("DEBUG: Parsed transactions: ${parsed.size}")
            parsed
        }
    }

    /**
     * Attempts to parse a single SMS message into a transaction.
     *
     * Tries user-defined patterns first (if available), then falls back to
     * specific bank parsers, and finally to the generic parser.
     *
     * @param sms The SMS message to parse
     * @return Parsed [TransactionInfo] if the message is a recognized transaction, null otherwise
     */
    suspend fun parseMessage(sms: SmsMessage): TransactionInfo? {
        // Layer 0: Try user-defined patterns first (highest priority)
        if (userPatternRepository != null && fuzzyMatcher != null) {
            tryUserPatterns(sms)?.let { return it }
        }

        // Layer 1: Try to find a specific bank parser
        val specificParser = BankSmsParserFactory.getParser(sms.sender)
        if (specificParser != null) {
            return specificParser.parse(sms.body, sms.timestamp)?.copy(smsId = sms.id)
        }

        // Layer 2: Fallback to generic parser if message contains transaction keywords
        if (BankSmsParserFactory.canGenericParse(sms.body)) {
            return BankSmsParserFactory.getGenericParser().parse(sms.body, sms.timestamp)
                ?.copy(smsId = sms.id)
        }

        return null
    }

    /**
     * Attempts to match SMS against user-defined patterns.
     * Updates pattern statistics on success or failure.
     */
    private suspend fun tryUserPatterns(sms: SmsMessage): TransactionInfo? {
        val pattern = userPatternRepository?.getPatternBySenderId(sms.sender) ?: return null
        val matchResult = fuzzyMatcher?.match(sms.body, pattern.inferredPattern, pattern.id)

        // If pattern match fails, update fail statistics and return null
        if (matchResult == null) {
            kotlinx.coroutines.withContext(Dispatchers.Default) {
                userPatternRepository?.updatePatternStats(
                    id = pattern.id,
                    successCount = pattern.successCount,
                    failCount = pattern.failCount + 1,
                )
            }
            return null
        }

        // Extract fields from match result
        val amount = matchResult.extractedFields[FieldType.AMOUNT]?.let {
            com.example.moneytap.data.parser.AmountParser.extractAmount(it)
        } ?: return null

        val merchant = matchResult.extractedFields[FieldType.MERCHANT]
        val balance = matchResult.extractedFields[FieldType.BALANCE]?.let {
            com.example.moneytap.data.parser.AmountParser.extractAmount(it)
        }
        val cardLast4 = matchResult.extractedFields[FieldType.CARD_LAST_4]

        // Update success statistics
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            userPatternRepository?.updatePatternStats(
                id = pattern.id,
                successCount = pattern.successCount + 1,
                failCount = pattern.failCount,
            )
        }

        return TransactionInfo(
            smsId = sms.id,
            type = TransactionType.DEBIT, // Default, could be enhanced
            amount = amount,
            currency = "COP", // Default
            balance = balance,
            cardLast4 = cardLast4,
            merchant = merchant,
            description = null,
            reference = null,
            bankName = pattern.bankName,
            timestamp = sms.timestamp,
            rawMessage = sms.body,
        )
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
