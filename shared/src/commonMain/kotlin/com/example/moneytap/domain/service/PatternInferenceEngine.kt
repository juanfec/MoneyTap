package com.example.moneytap.domain.service

import com.example.moneytap.domain.model.AmountFormat
import com.example.moneytap.domain.model.CurrencyPosition
import com.example.moneytap.domain.model.FieldSelection
import com.example.moneytap.domain.model.FieldType
import com.example.moneytap.domain.model.InferredPattern
import com.example.moneytap.domain.model.PatternSegment
import com.example.moneytap.domain.model.TeachingExample
import com.example.moneytap.util.StringSimilarity

/**
 * Infers SMS parsing patterns from multiple teaching examples.
 *
 * Takes 2+ examples where users have highlighted fields (amount, merchant, etc.)
 * and discovers the common structure by:
 * - Finding fixed text that appears in all examples (anchors)
 * - Identifying variable positions (amounts, merchant names, etc.)
 * - Detecting amount format (separators, currency symbols)
 */
class PatternInferenceEngine {

    /**
     * Infers a pattern from multiple teaching examples.
     *
     * @param examples List of 2+ teaching examples with field selections
     * @return InferredPattern with segments and amount format, or null if pattern cannot be inferred
     */
    fun inferPattern(examples: List<TeachingExample>): InferredPattern? {
        // Validate minimum examples
        if (examples.size < 2) return null

        // Validate all examples have same field types selected
        val firstFieldTypes = examples.first().selections.map { it.fieldType }.toSet()
        if (examples.any { example ->
                example.selections.map { it.fieldType }.toSet() != firstFieldTypes
            }) {
            return null
        }

        // Sort selections by startIndex for all examples
        val sortedExamples = examples.map { example ->
            example.copy(selections = example.selections.sortedBy { it.startIndex })
        }

        // Build pattern segments
        val segments = buildSegments(sortedExamples)

        // Detect amount format
        val amountFormat = detectAmountFormat(sortedExamples)

        // Calculate confidence based on how much fixed text was found
        val confidence = calculateConfidence(sortedExamples, segments)

        return InferredPattern(
            segments = segments,
            amountFormat = amountFormat,
            confidence = confidence,
        )
    }

    private fun buildSegments(examples: List<TeachingExample>): List<PatternSegment> {
        val segments = mutableListOf<PatternSegment>()
        val firstExample = examples.first()

        // Add prefix (text before first selection)
        val prefixTexts = examples.map { it.smsBody.substring(0, it.selections.first().startIndex) }
        val commonPrefix = findCommonText(prefixTexts)
        if (commonPrefix.isNotEmpty()) {
            segments.add(PatternSegment.FixedText(commonPrefix, fuzzyMatch = true))
        }

        // Process each field and the text after it
        for (i in firstExample.selections.indices) {
            val currentField = firstExample.selections[i]

            // Add variable segment for this field
            segments.add(PatternSegment.Variable(currentField.fieldType))

            // Find text between this field and the next (or end of message)
            val suffixTexts = examples.map { example ->
                val currentSelection = example.selections[i]
                val endPos = currentSelection.endIndex
                val startNextPos = if (i + 1 < example.selections.size) {
                    example.selections[i + 1].startIndex
                } else {
                    example.smsBody.length
                }
                example.smsBody.substring(endPos, startNextPos)
            }

            val commonSuffix = findCommonText(suffixTexts)
            if (commonSuffix.isNotEmpty()) {
                segments.add(PatternSegment.FixedText(commonSuffix, fuzzyMatch = true))
            }
        }

        return segments
    }

    private fun findCommonText(texts: List<String>): String {
        if (texts.isEmpty()) return ""
        if (texts.size == 1) return texts.first().trim()

        // Find longest common substring across all texts
        var common = texts.first()
        for (i in 1 until texts.size) {
            common = StringSimilarity.longestCommonSubstring(common, texts[i])
            if (common.isEmpty()) break
        }

        return common.trim()
    }

    private fun detectAmountFormat(examples: List<TeachingExample>): AmountFormat {
        val amountSelections = examples.mapNotNull { example ->
            example.selections.find { it.fieldType == FieldType.AMOUNT }?.selectedText
        }

        if (amountSelections.isEmpty()) {
            // Default format (Colombian pesos)
            return AmountFormat(
                thousandsSeparator = '.',
                decimalSeparator = ',',
                currencySymbol = "$",
                currencyPosition = CurrencyPosition.BEFORE,
            )
        }

        // Analyze first amount to detect format
        val firstAmount = amountSelections.first()

        // Detect separators
        val hasComma = firstAmount.contains(',')
        val hasDot = firstAmount.contains('.')

        val (thousandsSep, decimalSep) = when {
            hasComma && hasDot -> {
                // Both present - determine which is which based on position
                val lastCommaPos = firstAmount.lastIndexOf(',')
                val lastDotPos = firstAmount.lastIndexOf('.')
                if (lastCommaPos > lastDotPos) {
                    '.' to ','  // 1.234,56
                } else {
                    ',' to '.'  // 1,234.56
                }
            }
            hasComma -> '.' to ','  // Only comma - assume European format
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

    private fun calculateConfidence(
        examples: List<TeachingExample>,
        segments: List<PatternSegment>,
    ): Double {
        val fixedTextSegments = segments.filterIsInstance<PatternSegment.FixedText>()

        if (fixedTextSegments.isEmpty()) {
            // No fixed text found - low confidence
            return 0.4
        }

        // Calculate average message length
        val avgMessageLength = examples.map { it.smsBody.length }.average()

        // Sum of fixed text lengths
        val totalFixedTextLength = fixedTextSegments.sumOf { it.text.length }

        // Confidence based on ratio of fixed text to message length
        val baseConfidence = (totalFixedTextLength.toDouble() / avgMessageLength).coerceIn(0.0, 1.0)

        // Bonus for having multiple examples
        val exampleBonus = when {
            examples.size >= 3 -> 0.1
            examples.size >= 2 -> 0.05
            else -> 0.0
        }

        // Bonus for having multiple fixed text anchors
        val anchorBonus = when {
            fixedTextSegments.size >= 3 -> 0.1
            fixedTextSegments.size >= 2 -> 0.05
            else -> 0.0
        }

        return (baseConfidence + exampleBonus + anchorBonus).coerceIn(0.0, 1.0)
    }
}
