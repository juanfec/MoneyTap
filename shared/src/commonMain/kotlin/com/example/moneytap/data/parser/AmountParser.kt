package com.example.moneytap.data.parser

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
}
