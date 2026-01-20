package com.example.moneytap.data.parser

/**
 * Utility object for parsing monetary amounts from Colombian bank SMS messages.
 *
 * Colombian format uses periods as thousands separators and commas as decimal separators.
 * Example: 1.234.567,89 represents 1,234,567.89 in US format.
 */
object AmountParser {

    /**
     * Parses a Colombian-formatted amount string to a Double.
     *
     * Handles formats like:
     * - "1.234.567,89" → 1234567.89
     * - "1234567" → 1234567.0
     * - "1.234.567" → 1234567.0
     * - "$1.234.567,89" → 1234567.89
     *
     * @param numStr The amount string to parse
     * @return The parsed amount as Double, or null if parsing fails
     */
    fun parseColombianAmount(numStr: String): Double? {
        val cleaned = numStr
            .replace("$", "")
            .replace(" ", "")
            .replace(".", "")
            .replace(",", ".")
            .trim()

        return cleaned.toDoubleOrNull()
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
