package com.example.moneytap.domain.service

import com.example.moneytap.data.parser.AmountParser
import com.example.moneytap.domain.model.FieldType
import com.example.moneytap.domain.model.InferredPattern
import com.example.moneytap.domain.model.PatternMatchResult
import com.example.moneytap.domain.model.PatternSegment
import com.example.moneytap.util.StringSimilarity

/**
 * Matches SMS messages against learned patterns using fuzzy text matching.
 *
 * Uses Levenshtein similarity for flexible matching of fixed text anchors,
 * allowing patterns to work even when bank messages have slight variations.
 */
class FuzzyPatternMatcher(
    private val minConfidenceThreshold: Double = 0.65,
    private val fuzzyTextThreshold: Double = 0.75,
) {

    /**
     * Attempts to match an SMS message against a learned pattern.
     *
     * @param smsBody The SMS message text to match
     * @param pattern The learned pattern to match against
     * @param patternId The ID of the pattern (for tracking)
     * @return PatternMatchResult if match succeeds with confidence >= threshold, null otherwise
     */
    fun match(smsBody: String, pattern: InferredPattern, patternId: String): PatternMatchResult? {
        val extractedFields = mutableMapOf<FieldType, String>()
        var position = 0
        val confidenceScores = mutableListOf<Double>()

        for (i in pattern.segments.indices) {
            val segment = pattern.segments[i]

            when (segment) {
                is PatternSegment.FixedText -> {
                    // Try to find this fixed text starting from current position
                    val matchResult = findFixedText(
                        smsBody = smsBody,
                        startPos = position,
                        fixedText = segment.text,
                        fuzzyMatch = segment.fuzzyMatch,
                    )

                    if (matchResult == null) {
                        // Failed to find required fixed text
                        return null
                    }

                    position = matchResult.endPos
                    confidenceScores.add(matchResult.confidence)
                }

                is PatternSegment.Variable -> {
                    // Extract variable field value
                    val nextSegment = pattern.segments.getOrNull(i + 1)
                    val fieldValue = extractVariableField(
                        smsBody = smsBody,
                        startPos = position,
                        fieldType = segment.fieldType,
                        nextSegment = nextSegment,
                    )

                    if (fieldValue == null) {
                        // Failed to extract required field
                        return null
                    }

                    extractedFields[segment.fieldType] = fieldValue.value
                    position = fieldValue.endPos
                    confidenceScores.add(1.0) // Variable extraction success = 100% confidence
                }
            }
        }

        // Calculate average confidence
        val avgConfidence = if (confidenceScores.isNotEmpty()) {
            confidenceScores.average()
        } else {
            0.0
        }

        // Check if confidence meets threshold
        if (avgConfidence < minConfidenceThreshold) {
            return null
        }

        return PatternMatchResult(
            extractedFields = extractedFields,
            confidence = avgConfidence,
            patternId = patternId,
        )
    }

    private fun findFixedText(
        smsBody: String,
        startPos: Int,
        fixedText: String,
        fuzzyMatch: Boolean,
    ): FixedTextMatch? {
        if (startPos >= smsBody.length) return null

        val searchText = smsBody.substring(startPos)
        val trimmedFixed = fixedText.trim()

        if (trimmedFixed.isEmpty()) {
            // Empty fixed text - skip
            return FixedTextMatch(endPos = startPos, confidence = 1.0)
        }

        // Try exact match (case-insensitive)
        val exactIndex = searchText.indexOf(trimmedFixed, ignoreCase = true)
        if (exactIndex != -1) {
            return FixedTextMatch(
                endPos = startPos + exactIndex + trimmedFixed.length,
                confidence = 1.0,
            )
        }

        // If fuzzy matching is disabled, fail
        if (!fuzzyMatch) return null

        // Try fuzzy match - scan ahead looking for similar text
        val maxScanDistance = minOf(100, searchText.length)
        var bestMatch: FixedTextMatch? = null
        var bestSimilarity = 0.0

        for (offset in 0 until maxScanDistance) {
            val endPos = minOf(offset + trimmedFixed.length + 5, searchText.length)
            val candidate = searchText.substring(offset, endPos)

            val similarity = StringSimilarity.similarity(trimmedFixed, candidate)
            if (similarity > bestSimilarity && similarity >= fuzzyTextThreshold) {
                bestSimilarity = similarity
                bestMatch = FixedTextMatch(
                    endPos = startPos + offset + trimmedFixed.length,
                    confidence = similarity,
                )
            }
        }

        return bestMatch
    }

    private fun extractVariableField(
        smsBody: String,
        startPos: Int,
        fieldType: FieldType,
        nextSegment: PatternSegment?,
    ): VariableFieldValue? {
        if (startPos >= smsBody.length) return null

        // Determine where this field ends
        val endPos = when (nextSegment) {
            is PatternSegment.FixedText -> {
                // Find next fixed text anchor
                val searchText = smsBody.substring(startPos)
                val anchorIndex = searchText.indexOf(nextSegment.text.trim(), ignoreCase = true)
                if (anchorIndex != -1) {
                    startPos + anchorIndex
                } else {
                    // Try fuzzy match for next anchor
                    val fuzzyMatch = findFixedText(smsBody, startPos, nextSegment.text, nextSegment.fuzzyMatch)
                    fuzzyMatch?.endPos?.minus(nextSegment.text.length) ?: smsBody.length
                }
            }
            is PatternSegment.Variable -> {
                // Next is also a variable - use heuristic based on field type
                findFieldEnd(smsBody, startPos, fieldType)
            }
            null -> {
                // Last field - take rest of message
                smsBody.length
            }
        }

        val fieldText = smsBody.substring(startPos, endPos).trim()
        if (fieldText.isEmpty()) return null

        // Validate field based on type
        val isValid = when (fieldType) {
            FieldType.AMOUNT -> AmountParser.extractAmount(fieldText) != null
            FieldType.MERCHANT -> fieldText.isNotBlank()
            FieldType.BALANCE -> AmountParser.extractAmount(fieldText) != null
            FieldType.CARD_LAST_4 -> fieldText.matches(Regex("""\d{4}"""))
            FieldType.DATE -> fieldText.isNotBlank()
            FieldType.TRANSACTION_TYPE -> fieldText.isNotBlank()
        }

        return if (isValid) {
            VariableFieldValue(value = fieldText, endPos = endPos)
        } else {
            null
        }
    }

    private fun findFieldEnd(smsBody: String, startPos: Int, fieldType: FieldType): Int {
        val searchText = smsBody.substring(startPos)

        return when (fieldType) {
            FieldType.AMOUNT, FieldType.BALANCE -> {
                // Amount ends at first non-digit/separator character
                val amountPattern = Regex("""^[\d.,\s$]+""")
                val match = amountPattern.find(searchText)
                startPos + (match?.value?.length ?: 10)
            }
            FieldType.CARD_LAST_4 -> {
                // Exactly 4 digits
                startPos + 4
            }
            FieldType.MERCHANT, FieldType.TRANSACTION_TYPE -> {
                // Take up to next punctuation or number
                val endPattern = Regex("""[.;:\-\d]""")
                val match = endPattern.find(searchText)
                startPos + (match?.range?.first ?: minOf(30, searchText.length))
            }
            FieldType.DATE -> {
                // Date pattern - take up to 15 characters
                startPos + minOf(15, searchText.length)
            }
        }
    }

    private data class FixedTextMatch(val endPos: Int, val confidence: Double)
    private data class VariableFieldValue(val value: String, val endPos: Int)
}
