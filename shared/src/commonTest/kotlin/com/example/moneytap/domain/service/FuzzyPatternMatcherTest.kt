package com.example.moneytap.domain.service

import com.example.moneytap.domain.model.AmountFormat
import com.example.moneytap.domain.model.CurrencyPosition
import com.example.moneytap.domain.model.FieldType
import com.example.moneytap.domain.model.InferredPattern
import com.example.moneytap.domain.model.PatternSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuzzyPatternMatcherTest {

    private val matcher = FuzzyPatternMatcher(
        minConfidenceThreshold = 0.65,
        fuzzyTextThreshold = 0.75,
    )

    private val colombianFormat = AmountFormat(
        thousandsSeparator = '.',
        decimalSeparator = ',',
        currencySymbol = "$",
        currencyPosition = CurrencyPosition.BEFORE,
    )

    @Test
    fun `exact match returns high confidence`() {
        val pattern = InferredPattern(
            segments = listOf(
                PatternSegment.FixedText("Compra por"),
                PatternSegment.Variable(FieldType.AMOUNT),
                PatternSegment.FixedText("en"),
                PatternSegment.Variable(FieldType.MERCHANT),
            ),
            amountFormat = colombianFormat,
            confidence = 0.9,
        )

        val sms = "Compra por $50.000 en EXITO"
        val result = matcher.match(sms, pattern, "pattern_1")

        assertNotNull(result)
        assertEquals("pattern_1", result.patternId)
        assertTrue(result.confidence >= 0.9)
        assertNotNull(result.extractedFields[FieldType.AMOUNT])
        assertNotNull(result.extractedFields[FieldType.MERCHANT])
    }

    @Test
    fun `minor typo still matches with fuzzy matching`() {
        val pattern = InferredPattern(
            segments = listOf(
                PatternSegment.FixedText("Transferencia exitosa", fuzzyMatch = true),
                PatternSegment.Variable(FieldType.AMOUNT),
            ),
            amountFormat = colombianFormat,
            confidence = 0.85,
        )

        val sms = "Transferencia exitos $100.000"
        val result = matcher.match(sms, pattern, "pattern_2")

        assertNotNull(result)
        assertNotNull(result.extractedFields[FieldType.AMOUNT])
    }

    @Test
    fun `completely different SMS returns null`() {
        val pattern = InferredPattern(
            segments = listOf(
                PatternSegment.FixedText("Compra por"),
                PatternSegment.Variable(FieldType.AMOUNT),
                PatternSegment.FixedText("en"),
                PatternSegment.Variable(FieldType.MERCHANT),
            ),
            amountFormat = colombianFormat,
            confidence = 0.9,
        )

        val sms = "Retiro en cajero automatico por $200.000"
        val result = matcher.match(sms, pattern, "pattern_1")

        assertNull(result)
    }

    @Test
    fun `extracts amount with Colombian format`() {
        val pattern = InferredPattern(
            segments = listOf(
                PatternSegment.FixedText("Pago"),
                PatternSegment.Variable(FieldType.AMOUNT),
            ),
            amountFormat = colombianFormat,
            confidence = 0.8,
        )

        val sms = "Pago $1.234.567,89 realizado"
        val result = matcher.match(sms, pattern, "pattern_3")

        assertNotNull(result)
        val amount = result.extractedFields[FieldType.AMOUNT]
        assertNotNull(amount)
        assertTrue(amount.contains("1.234.567"))
    }

    @Test
    fun `extracts merchant with various end markers`() {
        val pattern = InferredPattern(
            segments = listOf(
                PatternSegment.FixedText("Compra en"),
                PatternSegment.Variable(FieldType.MERCHANT),
                PatternSegment.FixedText("por"),
                PatternSegment.Variable(FieldType.AMOUNT),
            ),
            amountFormat = colombianFormat,
            confidence = 0.85,
        )

        val sms = "Compra en EXITO por $50.000"
        val result = matcher.match(sms, pattern, "pattern_4")

        assertNotNull(result)
        val merchant = result.extractedFields[FieldType.MERCHANT]
        assertNotNull(merchant)
        assertTrue(merchant.contains("EXITO"))
    }

    @Test
    fun `enforces confidence threshold`() {
        val strictMatcher = FuzzyPatternMatcher(
            minConfidenceThreshold = 0.95,
            fuzzyTextThreshold = 0.75,
        )

        val pattern = InferredPattern(
            segments = listOf(
                PatternSegment.FixedText("Compra", fuzzyMatch = true),
                PatternSegment.Variable(FieldType.AMOUNT),
            ),
            amountFormat = colombianFormat,
            confidence = 0.7,
        )

        val sms = "Compro $50.000"
        val result = strictMatcher.match(sms, pattern, "pattern_5")

        if (result != null) {
            assertTrue(result.confidence >= 0.95)
        }
    }

    @Test
    fun `handles pattern with no fixed text`() {
        val pattern = InferredPattern(
            segments = listOf(
                PatternSegment.Variable(FieldType.AMOUNT),
            ),
            amountFormat = colombianFormat,
            confidence = 0.5,
        )

        val sms = "$50.000"
        val result = matcher.match(sms, pattern, "pattern_6")

        assertNotNull(result)
        assertNotNull(result.extractedFields[FieldType.AMOUNT])
    }

    @Test
    fun `handles multiple variable fields in sequence`() {
        val pattern = InferredPattern(
            segments = listOf(
                PatternSegment.FixedText("Retiro"),
                PatternSegment.Variable(FieldType.AMOUNT),
                PatternSegment.FixedText("Saldo"),
                PatternSegment.Variable(FieldType.BALANCE),
            ),
            amountFormat = colombianFormat,
            confidence = 0.85,
        )

        val sms = "Retiro $100.000. Saldo $500.000"
        val result = matcher.match(sms, pattern, "pattern_7")

        assertNotNull(result)
        assertNotNull(result.extractedFields[FieldType.AMOUNT])
        assertNotNull(result.extractedFields[FieldType.BALANCE])
    }

    @Test
    fun `fuzzy matching disabled requires exact match`() {
        val pattern = InferredPattern(
            segments = listOf(
                PatternSegment.FixedText("Compra exacta", fuzzyMatch = false),
                PatternSegment.Variable(FieldType.AMOUNT),
            ),
            amountFormat = colombianFormat,
            confidence = 0.9,
        )

        val sms = "Compra exact $50.000"
        val result = matcher.match(sms, pattern, "pattern_8")

        assertNull(result)
    }

    @Test
    fun `extracts card last 4 digits`() {
        val pattern = InferredPattern(
            segments = listOf(
                PatternSegment.FixedText("Tarjeta"),
                PatternSegment.Variable(FieldType.CARD_LAST_4),
                PatternSegment.FixedText("Compra"),
                PatternSegment.Variable(FieldType.AMOUNT),
            ),
            amountFormat = colombianFormat,
            confidence = 0.85,
        )

        val sms = "Tarjeta 1234 Compra $50.000"
        val result = matcher.match(sms, pattern, "pattern_9")

        assertNotNull(result)
        val card = result.extractedFields[FieldType.CARD_LAST_4]
        assertNotNull(card)
        assertEquals("1234", card.trim())
    }
}
