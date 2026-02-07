package com.example.moneytap.domain.service

import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.CurrencyPosition
import com.example.moneytap.domain.model.FieldSelection
import com.example.moneytap.domain.model.FieldType
import com.example.moneytap.domain.model.PatternSegment
import com.example.moneytap.domain.model.TeachingExample
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PatternInferenceEngineTest {

    private val engine = PatternInferenceEngine()

    @Test
    fun `infers pattern from two simple examples with amount and merchant`() {
        val example1 = TeachingExample(
            id = "1",
            smsBody = "Compra por $50.000 en EXITO",
            senderId = "Bancolombia",
            selections = listOf(
                FieldSelection(FieldType.AMOUNT, 11, 18, "$50.000"),
                FieldSelection(FieldType.MERCHANT, 22, 27, "EXITO"),
            ),
            category = Category.GROCERIES,
            createdAt = Clock.System.now(),
        )

        val example2 = TeachingExample(
            id = "2",
            smsBody = "Compra por $75.000 en CARULLA",
            senderId = "Bancolombia",
            selections = listOf(
                FieldSelection(FieldType.AMOUNT, 11, 18, "$75.000"),
                FieldSelection(FieldType.MERCHANT, 22, 29, "CARULLA"),
            ),
            category = Category.GROCERIES,
            createdAt = Clock.System.now(),
        )

        val pattern = engine.inferPattern(listOf(example1, example2))

        assertNotNull(pattern)
        assertTrue(pattern.segments.size >= 3)
        assertTrue(pattern.segments.any { it is PatternSegment.FixedText && it.text.contains("Compra") })
        assertTrue(pattern.segments.any { it is PatternSegment.Variable && it.fieldType == FieldType.AMOUNT })
        assertTrue(pattern.segments.any { it is PatternSegment.Variable && it.fieldType == FieldType.MERCHANT })
        assertTrue(pattern.confidence > 0.0)
    }

    @Test
    fun `handles examples with different merchant lengths`() {
        val example1 = TeachingExample(
            id = "1",
            smsBody = "Pago en MC DONALDS por $25.000 OK",
            senderId = "Nequi",
            selections = listOf(
                FieldSelection(FieldType.MERCHANT, 8, 19, "MC DONALDS"),
                FieldSelection(FieldType.AMOUNT, 24, 31, "$25.000"),
            ),
            category = Category.RESTAURANT,
            createdAt = Clock.System.now(),
        )

        val example2 = TeachingExample(
            id = "2",
            smsBody = "Pago en KFC por $18.500 OK",
            senderId = "Nequi",
            selections = listOf(
                FieldSelection(FieldType.MERCHANT, 8, 11, "KFC"),
                FieldSelection(FieldType.AMOUNT, 16, 23, "$18.500"),
            ),
            category = Category.RESTAURANT,
            createdAt = Clock.System.now(),
        )

        val pattern = engine.inferPattern(listOf(example1, example2))

        assertNotNull(pattern)
        assertTrue(pattern.segments.any { it is PatternSegment.FixedText && it.text.contains("Pago") })
        assertTrue(pattern.segments.any { it is PatternSegment.Variable && it.fieldType == FieldType.MERCHANT })
        assertTrue(pattern.segments.any { it is PatternSegment.Variable && it.fieldType == FieldType.AMOUNT })
    }

    @Test
    fun `handles examples with balance field`() {
        val example1 = TeachingExample(
            id = "1",
            smsBody = "Retiro $100.000. Saldo $500.000",
            senderId = "Daviplata",
            selections = listOf(
                FieldSelection(FieldType.AMOUNT, 7, 15, "$100.000"),
                FieldSelection(FieldType.BALANCE, 23, 31, "$500.000"),
            ),
            category = null,
            createdAt = Clock.System.now(),
        )

        val example2 = TeachingExample(
            id = "2",
            smsBody = "Retiro $50.000. Saldo $450.000",
            senderId = "Daviplata",
            selections = listOf(
                FieldSelection(FieldType.AMOUNT, 7, 14, "$50.000"),
                FieldSelection(FieldType.BALANCE, 22, 30, "$450.000"),
            ),
            category = null,
            createdAt = Clock.System.now(),
        )

        val pattern = engine.inferPattern(listOf(example1, example2))

        assertNotNull(pattern)
        assertTrue(pattern.segments.any { it is PatternSegment.Variable && it.fieldType == FieldType.AMOUNT })
        assertTrue(pattern.segments.any { it is PatternSegment.Variable && it.fieldType == FieldType.BALANCE })
        assertTrue(pattern.segments.any { it is PatternSegment.FixedText && it.text.contains("Retiro") })
        assertTrue(pattern.segments.any { it is PatternSegment.FixedText && it.text.contains("Saldo") })
    }

    @Test
    fun `detects Colombian amount format`() {
        val example1 = TeachingExample(
            id = "1",
            smsBody = "Transferencia $1.234.567,89",
            senderId = "Banco",
            selections = listOf(
                FieldSelection(FieldType.AMOUNT, 14, 27, "$1.234.567,89"),
            ),
            category = null,
            createdAt = Clock.System.now(),
        )

        val example2 = TeachingExample(
            id = "2",
            smsBody = "Transferencia $987.654,32",
            senderId = "Banco",
            selections = listOf(
                FieldSelection(FieldType.AMOUNT, 14, 25, "$987.654,32"),
            ),
            category = null,
            createdAt = Clock.System.now(),
        )

        val pattern = engine.inferPattern(listOf(example1, example2))

        assertNotNull(pattern)
        assertEquals('.', pattern.amountFormat.thousandsSeparator)
        assertEquals(',', pattern.amountFormat.decimalSeparator)
        assertEquals("$", pattern.amountFormat.currencySymbol)
        assertEquals(CurrencyPosition.BEFORE, pattern.amountFormat.currencyPosition)
    }

    @Test
    fun `returns null for only one example`() {
        val example = TeachingExample(
            id = "1",
            smsBody = "Compra por $50.000",
            senderId = "Banco",
            selections = listOf(
                FieldSelection(FieldType.AMOUNT, 11, 18, "$50.000"),
            ),
            category = null,
            createdAt = Clock.System.now(),
        )

        val pattern = engine.inferPattern(listOf(example))

        assertNull(pattern)
    }

    @Test
    fun `returns null when examples have different fields selected`() {
        val example1 = TeachingExample(
            id = "1",
            smsBody = "Compra por $50.000 en EXITO",
            senderId = "Banco",
            selections = listOf(
                FieldSelection(FieldType.AMOUNT, 11, 18, "$50.000"),
                FieldSelection(FieldType.MERCHANT, 22, 27, "EXITO"),
            ),
            category = null,
            createdAt = Clock.System.now(),
        )

        val example2 = TeachingExample(
            id = "2",
            smsBody = "Compra por $75.000",
            senderId = "Banco",
            selections = listOf(
                FieldSelection(FieldType.AMOUNT, 11, 18, "$75.000"),
            ),
            category = null,
            createdAt = Clock.System.now(),
        )

        val pattern = engine.inferPattern(listOf(example1, example2))

        assertNull(pattern)
    }

    @Test
    fun `confidence increases with more examples`() {
        val examples2 = listOf(
            TeachingExample(
                id = "1",
                smsBody = "Pago $10.000",
                senderId = "Banco",
                selections = listOf(FieldSelection(FieldType.AMOUNT, 5, 12, "$10.000")),
                category = null,
                createdAt = Clock.System.now(),
            ),
            TeachingExample(
                id = "2",
                smsBody = "Pago $20.000",
                senderId = "Banco",
                selections = listOf(FieldSelection(FieldType.AMOUNT, 5, 12, "$20.000")),
                category = null,
                createdAt = Clock.System.now(),
            ),
        )

        val examples3 = examples2 + TeachingExample(
            id = "3",
            smsBody = "Pago $30.000",
            senderId = "Banco",
            selections = listOf(FieldSelection(FieldType.AMOUNT, 5, 12, "$30.000")),
            category = null,
            createdAt = Clock.System.now(),
        )

        val pattern2 = engine.inferPattern(examples2)
        val pattern3 = engine.inferPattern(examples3)

        assertNotNull(pattern2)
        assertNotNull(pattern3)
        assertTrue(pattern3.confidence >= pattern2.confidence)
    }
}
