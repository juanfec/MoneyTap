package com.example.moneytap.data.parser

import com.example.moneytap.domain.model.AmountFormat
import com.example.moneytap.domain.model.CurrencyPosition

/**
 * Utility object for parsing monetary amounts from Colombian bank SMS messages.
 *
 * Colombian format uses periods as thousands separators and commas as decimal separators.
 * Example: 1.234.567,89 represents 1,234,567.89 in US format.
 */
object AmountParser {

    /**
     * Parses an amount string to a Double, auto-detecting the format.
     *
     * Handles both Colombian and US formats:
     * - Colombian: "1.234.567,89" → 1234567.89 (period=thousands, comma=decimal)
     * - US: "1,234,567.89" → 1234567.89 (comma=thousands, period=decimal)
     * - Plain: "1234567" → 1234567.0
     *
     * @param numStr The amount string to parse
     * @return The parsed amount as Double, or null if parsing fails
     */
    fun parseColombianAmount(numStr: String): Double? {
        val cleaned = numStr
            .replace("$", "")
            .replace(" ", "")
            .trim()

        if (cleaned.isEmpty()) return null

        // Detect format by checking last separator position
        val lastComma = cleaned.lastIndexOf(',')
        val lastPeriod = cleaned.lastIndexOf('.')

        val normalized = when {
            // No separators - plain number
            lastComma == -1 && lastPeriod == -1 -> cleaned

            // Only periods (Colombian thousands or US decimal)
            lastComma == -1 -> {
                // If period is followed by exactly 2 digits, treat as decimal
                if (cleaned.length - lastPeriod == 3) {
                    cleaned // US decimal format, keep as is
                } else {
                    cleaned.replace(".", "") // Colombian thousands separator
                }
            }

            // Only commas (US thousands or Colombian decimal)
            lastPeriod == -1 -> {
                // If comma is followed by exactly 2 digits, treat as decimal (Colombian)
                if (cleaned.length - lastComma == 3) {
                    cleaned.replace(",", ".") // Colombian decimal
                } else {
                    cleaned.replace(",", "") // US thousands separator
                }
            }

            // Both separators present - determine by position
            lastPeriod > lastComma -> {
                // Period is last separator → US format (comma=thousands, period=decimal)
                cleaned.replace(",", "")
            }

            else -> {
                // Comma is last separator → Colombian format (period=thousands, comma=decimal)
                cleaned.replace(".", "").replace(",", ".")
            }
        }

        return normalized.toDoubleOrNull()
    }

    /**
     * Extracts and parses an amount from a string that may contain currency symbols
     * and other text.
     *
     * @param text The text containing an amount
     * @return The parsed amount as Double, or null if no valid amount found
     */
    fun extractAmount(text: String): Double? {
        val amountPattern = Regex("""\$?\s*[\d.,]+""")
        val match = amountPattern.find(text) ?: return null
        return parseColombianAmount(match.value)
    }

    /**
     * Detects the amount format from a list of amount strings.
     * Analyzes separators and currency symbols to determine formatting.
     *
     * @param amountStrings List of amount strings to analyze
     * @return Detected AmountFormat
     */
    fun detectFormat(amountStrings: List<String>): AmountFormat {
        if (amountStrings.isEmpty()) {
            // Default Colombian format
            return AmountFormat(
                thousandsSeparator = '.',
                decimalSeparator = ',',
                currencySymbol = "$",
                currencyPosition = CurrencyPosition.BEFORE,
            )
        }

        val firstAmount = amountStrings.first()

        // Detect separators
        val hasComma = firstAmount.contains(',')
        val hasDot = firstAmount.contains('.')

        val (thousandsSep, decimalSep) = when {
            hasComma && hasDot -> {
                val lastCommaPos = firstAmount.lastIndexOf(',')
                val lastDotPos = firstAmount.lastIndexOf('.')
                if (lastCommaPos > lastDotPos) {
                    '.' to ','  // 1.234,56 (Colombian)
                } else {
                    ',' to '.'  // 1,234.56 (US)
                }
            }
            hasComma -> '.' to ','  // Only comma - assume European/Colombian format
            hasDot -> ',' to '.'    // Only dot - assume US format
            else -> '.' to ','      // No separators - default to Colombian
        }

        // Detect currency symbol and position
        val currencySymbols = listOf("$", "USD", "COP", "€", "£", "R$")
        var currencySymbol: String? = null
        var currencyPosition = CurrencyPosition.NONE

        for (symbol in currencySymbols) {
            if (firstAmount.startsWith(symbol)) {
                currencySymbol = symbol
                currencyPosition = CurrencyPosition.BEFORE
                break
            } else if (firstAmount.endsWith(symbol)) {
                currencySymbol = symbol
                currencyPosition = CurrencyPosition.AFTER
                break
            } else if (firstAmount.contains(symbol)) {
                currencySymbol = symbol
                currencyPosition = CurrencyPosition.BEFORE
                break
            }
        }

        return AmountFormat(
            thousandsSeparator = thousandsSep,
            decimalSeparator = decimalSep,
            currencySymbol = currencySymbol,
            currencyPosition = currencyPosition,
        )
    }

    /**
     * Formats a Double amount for display according to the specified format.
     *
     * @param amount The amount to format
     * @param format The AmountFormat specifying how to format
     * @return Formatted amount string
     */
    fun formatForDisplay(amount: Double, format: AmountFormat): String {
        // Split into integer and decimal parts
        val integerPart = amount.toLong()
        val decimalPart = ((amount - integerPart) * 100).toInt()

        // Format integer part with thousands separators
        val integerStr = integerPart.toString()
        val formattedInteger = buildString {
            val reversedDigits = integerStr.reversed()
            reversedDigits.forEachIndexed { index, digit ->
                if (index > 0 && index % 3 == 0) {
                    append(format.thousandsSeparator)
                }
                append(digit)
            }
        }.reversed()

        // Build final string
        val amountStr = if (decimalPart > 0) {
            "$formattedInteger${format.decimalSeparator}${decimalPart.toString().padStart(2, '0')}"
        } else {
            formattedInteger
        }

        return when (format.currencyPosition) {
            CurrencyPosition.BEFORE -> "${format.currencySymbol ?: ""}$amountStr"
            CurrencyPosition.AFTER -> "$amountStr${format.currencySymbol ?: ""}"
            CurrencyPosition.NONE -> amountStr
        }
    }
}
