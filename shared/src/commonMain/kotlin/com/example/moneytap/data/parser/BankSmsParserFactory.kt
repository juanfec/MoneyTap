package com.example.moneytap.data.parser

import com.example.moneytap.data.parser.banks.BancolombiaParser
import com.example.moneytap.data.parser.banks.BancoOccidenteParser
import com.example.moneytap.data.parser.banks.DaviplataParser
import com.example.moneytap.data.parser.banks.GenericTransactionParser
import com.example.moneytap.data.parser.banks.NequiParser

/**
 * Factory for selecting the appropriate [BankSmsParser] based on SMS sender.
 *
 * This factory maintains a registry of all available bank parsers and provides
 * methods to select the correct parser for a given SMS sender ID.
 *
 * It also provides a fallback generic parser for messages that don't match
 * any known bank but contain transaction-related keywords.
 *
 * To add support for a new bank:
 * 1. Create a new parser class implementing [BankSmsParser]
 * 2. Add an instance to the [parsers] list below
 */
object BankSmsParserFactory {

    private val parsers: List<BankSmsParser> = listOf(
        BancolombiaParser(),
        BancoOccidenteParser(),
        NequiParser(),
        DaviplataParser(),
    )

    private val genericParser = GenericTransactionParser()

    /**
     * Finds the appropriate parser for the given SMS sender ID.
     *
     * @param senderId The sender phone number or short code from the SMS
     * @return The matching parser, or null if no parser can handle this sender
     */
    fun getParser(senderId: String): BankSmsParser? =
        parsers.find { it.canHandle(senderId) }

    /**
     * Returns the generic fallback parser that can attempt to parse
     * any message containing transaction keywords.
     */
    fun getGenericParser(): GenericTransactionParser = genericParser

    /**
     * Checks if the message body can be parsed by the generic parser.
     * Use this to determine if fallback parsing should be attempted.
     *
     * @param body The SMS message body
     * @return true if the message contains transaction-related keywords and an amount
     */
    fun canGenericParse(body: String): Boolean = genericParser.canParseBody(body)

    /**
     * Returns all registered parsers (excluding generic fallback).
     *
     * Useful for testing or displaying supported banks.
     */
    fun getAllParsers(): List<BankSmsParser> = parsers

    /**
     * Returns the names of all supported banks.
     *
     * Useful for UI display.
     */
    fun getSupportedBanks(): List<String> = parsers.map { it.bankName }

    /**
     * Returns all sender IDs that are recognized by any parser.
     *
     * Useful for filtering SMS messages before attempting to parse.
     */
    fun getAllSenderIds(): List<String> = parsers.flatMap { it.senderIds }
}
