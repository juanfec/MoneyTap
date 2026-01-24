package com.example.moneytap.domain.service

import com.example.moneytap.data.parser.banks.BancoOccidenteParser
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.MatchType
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CategorizationEngineTest {

    private val engine = CategorizationEngine()
    private val testTimestamp = Instant.parse("2026-01-19T18:26:00Z")

    private fun createTransaction(
        merchant: String?,
        description: String? = null,
        type: TransactionType = TransactionType.DEBIT,
        amount: Double = 10000.0,
    ) = TransactionInfo(
        type = type,
        amount = amount,
        balance = null,
        cardLast4 = "1234",
        merchant = merchant,
        description = description,
        reference = null,
        bankName = "Test Bank",
        timestamp = testTimestamp,
        rawMessage = "Test message",
    )

    // ===================
    // Layer 1: Exact Match
    // ===================

    @Test
    fun `exact match categorizes EXITO as GROCERIES`() {
        val transaction = createTransaction(merchant = "EXITO")
        val result = engine.categorize(transaction)

        assertEquals(Category.GROCERIES, result.category)
        assertEquals(MatchType.EXACT, result.matchType)
    }

    @Test
    fun `exact match categorizes RAPPI as RESTAURANT`() {
        val transaction = createTransaction(merchant = "RAPPI")
        val result = engine.categorize(transaction)

        assertEquals(Category.RESTAURANT, result.category)
        assertEquals(MatchType.EXACT, result.matchType)
    }

    @Test
    fun `substring match categorizes MAS POR MENOS as GROCERIES`() {
        // "MAS POR MENOS" is contained in "SUPERM MAS POR MENOS" from the dictionary
        val transaction = createTransaction(merchant = "MAS POR MENOS")
        val result = engine.categorize(transaction)

        assertEquals(Category.GROCERIES, result.category)
        assertEquals(MatchType.FUZZY, result.matchType) // Substring matches use FUZZY match type
    }

    // ===================
    // Layer 2: Substring Match
    // ===================

    @Test
    fun `substring match categorizes SUPERM MAS POR MENOS C as GROCERIES`() {
        // This is the actual merchant name extracted from:
        // "Ud realizo una compra en SUPERM MAS POR MENOS C por $11.440.T. Credencial *4115, 2026/01/19, 18:26."
        val transaction = createTransaction(merchant = "SUPERM MAS POR MENOS C")
        val result = engine.categorize(transaction)

        assertEquals(Category.GROCERIES, result.category)
        // Substring match uses FUZZY match type
        assertEquals(MatchType.FUZZY, result.matchType)
    }

    @Test
    fun `substring match categorizes ALMACENES EXITO BOGOTA as GROCERIES`() {
        val transaction = createTransaction(merchant = "ALMACENES EXITO BOGOTA")
        val result = engine.categorize(transaction)

        assertEquals(Category.GROCERIES, result.category)
    }

    @Test
    fun `substring match prefers longer matches`() {
        // "ALMACENES EXITO" should match over "EXITO" because it's longer
        val transaction = createTransaction(merchant = "ALMACENES EXITO")
        val result = engine.categorize(transaction)

        assertEquals(Category.GROCERIES, result.category)
        // Should be exact match since "ALMACENES EXITO" is in dictionary
        assertEquals(MatchType.EXACT, result.matchType)
    }

    @Test
    fun `substring match finds JUAN VALDEZ in longer merchant name`() {
        val transaction = createTransaction(merchant = "JUAN VALDEZ CAFE CENTRO")
        val result = engine.categorize(transaction)

        assertEquals(Category.COFFEE, result.category)
    }

    // ===================
    // Layer 4: Keyword Match
    // ===================

    @Test
    fun `keyword match categorizes unknown SUPERMERCADO as GROCERIES`() {
        val transaction = createTransaction(merchant = "SUPERMERCADO NUEVO")
        val result = engine.categorize(transaction)

        assertEquals(Category.GROCERIES, result.category)
        assertEquals(MatchType.KEYWORD, result.matchType)
    }

    @Test
    fun `keyword match categorizes RESTAURANTE as RESTAURANT`() {
        val transaction = createTransaction(merchant = "RESTAURANTE LA ESQUINA")
        val result = engine.categorize(transaction)

        assertEquals(Category.RESTAURANT, result.category)
        assertEquals(MatchType.KEYWORD, result.matchType)
    }

    @Test
    fun `keyword match uses description when merchant has no match`() {
        val transaction = createTransaction(
            merchant = "LUGAR DESCONOCIDO",
            description = "Compra en FARMACIA",
        )
        val result = engine.categorize(transaction)

        assertEquals(Category.PHARMACY, result.category)
        assertEquals(MatchType.KEYWORD, result.matchType)
    }

    // ===================
    // Layer 5: Default
    // ===================

    @Test
    fun `default returns UNCATEGORIZED for unknown merchant`() {
        // Use a merchant name that doesn't match any dictionary entries or keywords
        val transaction = createTransaction(merchant = "RANDOM PLACE XYZ")
        val result = engine.categorize(transaction)

        assertEquals(Category.UNCATEGORIZED, result.category)
        assertEquals(MatchType.DEFAULT, result.matchType)
    }

    @Test
    fun `default returns UNCATEGORIZED for null merchant`() {
        val transaction = createTransaction(merchant = null)
        val result = engine.categorize(transaction)

        assertEquals(Category.UNCATEGORIZED, result.category)
        assertEquals(MatchType.DEFAULT, result.matchType)
    }

    // ===================
    // Integration: Full Flow (Parser → Categorization)
    // ===================

    @Test
    fun `end-to-end test parses SMS and categorizes SUPERM MAS POR MENOS as GROCERIES`() {
        // This is the exact SMS message that should be parsed and categorized as GROCERIES
        val rawSms = "Ud realizo una compra en SUPERM MAS POR MENOS C por \$11.440.T. Credencial *4115, 2026/01/19, 18:26."

        // Step 1: Parse the SMS using BancoOccidenteParser
        val parser = BancoOccidenteParser()
        val parsedTransaction = parser.parse(rawSms, testTimestamp)

        // Verify parsing succeeded
        assertNotNull(parsedTransaction, "SMS should be parsed successfully")
        assertEquals(11440.0, parsedTransaction.amount, "Amount should be 11440.0")
        assertEquals("SUPERM MAS POR MENOS C", parsedTransaction.merchant, "Merchant should be extracted correctly")
        assertEquals(TransactionType.DEBIT, parsedTransaction.type, "Type should be DEBIT")
        assertEquals("4115", parsedTransaction.cardLast4, "Card last 4 digits should be 4115")

        // Step 2: Categorize the parsed transaction
        val categorizedTransaction = engine.categorize(parsedTransaction)

        // Verify categorization - THIS IS THE KEY ASSERTION
        assertEquals(
            Category.GROCERIES,
            categorizedTransaction.category,
            "SUPERM MAS POR MENOS C should be categorized as GROCERIES (via substring match with 'MAS POR MENOS')",
        )
        assertEquals(MatchType.FUZZY, categorizedTransaction.matchType, "Should match via substring (reported as FUZZY)")
        assertEquals(true, categorizedTransaction.confidence >= 0.9, "Confidence should be >= 0.9 for substring match")
    }

    @Test
    fun `categorizeAll processes multiple transactions correctly`() {
        val transactions = listOf(
            createTransaction(merchant = "EXITO"),
            createTransaction(merchant = "SUPERM MAS POR MENOS"),
            createTransaction(merchant = "RANDOM PLACE XYZ"),
        )

        val results = engine.categorizeAll(transactions)

        assertEquals(3, results.size)
        assertEquals(Category.GROCERIES, results[0].category) // Exact match
        assertEquals(Category.GROCERIES, results[1].category) // Substring match
        assertEquals(Category.UNCATEGORIZED, results[2].category) // Default
    }
}
