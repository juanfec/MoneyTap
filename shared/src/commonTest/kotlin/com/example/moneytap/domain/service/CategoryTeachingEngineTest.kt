package com.example.moneytap.domain.service

import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.MatchType
import com.example.moneytap.domain.model.RuleCondition
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.model.TransactionType
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryTeachingEngineTest {

    private val engine = CategoryTeachingEngine()

    private fun createTransaction(
        smsId: Long,
        merchant: String?,
        rawMessage: String = "Bancolombia: Compra",
        category: Category = Category.UNCATEGORIZED,
    ) = CategorizedTransaction(
        transaction = TransactionInfo(
            smsId = smsId,
            type = TransactionType.DEBIT,
            amount = 50000.0,
            currency = "COP",
            balance = null,
            cardLast4 = null,
            merchant = merchant,
            description = null,
            reference = null,
            bankName = "Bancolombia",
            timestamp = Clock.System.now(),
            rawMessage = rawMessage,
        ),
        category = category,
        confidence = 0.8,
        matchType = MatchType.EXACT,
    )

    @Test
    fun `two transactions with same merchant create MerchantEquals rule`() {
        val transactions = listOf(
            createTransaction(1, "EXITO"),
            createTransaction(2, "EXITO"),
        )

        val rule = engine.learnRule(transactions, Category.GROCERIES, "EXITO rule")

        assertNotNull(rule)
        assertEquals(Category.GROCERIES, rule.category)
        assertEquals("EXITO rule", rule.name)
        assertTrue(rule.enabled)
        assertEquals(2, rule.learnedFromExamples?.size)

        val merchantEquals = rule.conditions.filterIsInstance<RuleCondition.MerchantEquals>()
        assertEquals(1, merchantEquals.size)
        assertEquals("EXITO", merchantEquals.first().name)
    }

    @Test
    fun `two transactions with different merchants but common keyword create AnyKeyword rule`() {
        val transactions = listOf(
            createTransaction(1, "RESTAURANTE LA PARRILLA"),
            createTransaction(2, "RESTAURANTE EL CORRAL"),
        )

        val rule = engine.learnRule(transactions, Category.RESTAURANT)

        assertNotNull(rule)
        assertEquals(Category.RESTAURANT, rule.category)

        val anyKeyword = rule.conditions.filterIsInstance<RuleCondition.AnyKeyword>()
        assertTrue(anyKeyword.isNotEmpty())
        assertTrue(anyKeyword.first().keywords.contains("RESTAURANTE"))
    }

    @Test
    fun `filters out generic words correctly`() {
        val transactions = listOf(
            createTransaction(1, "ALMACEN LA CASA SAS"),
            createTransaction(2, "ALMACEN EL HOGAR LTDA"),
        )

        val rule = engine.learnRule(transactions, Category.GROCERIES)

        assertNotNull(rule)

        val anyKeyword = rule.conditions.filterIsInstance<RuleCondition.AnyKeyword>()
        if (anyKeyword.isNotEmpty()) {
            val keywords = anyKeyword.first().keywords
            assertFalse(keywords.contains("LA"))
            assertFalse(keywords.contains("EL"))
            assertFalse(keywords.contains("SAS"))
            assertFalse(keywords.contains("LTDA"))
            assertFalse(keywords.contains("DE"))
            assertTrue(keywords.contains("ALMACEN"))
        }
    }

    @Test
    fun `handles empty merchant names`() {
        val transactions = listOf(
            createTransaction(1, null),
            createTransaction(2, null),
        )

        val rule = engine.learnRule(transactions, Category.UNCATEGORIZED)

        assertNotNull(rule)
        assertTrue(rule.conditions.isNotEmpty())
    }

    @Test
    fun `returns null for single transaction`() {
        val transactions = listOf(
            createTransaction(1, "EXITO"),
        )

        val rule = engine.learnRule(transactions, Category.GROCERIES)

        assertNull(rule)
    }

    @Test
    fun `adds sender condition when all from same sender`() {
        val transactions = listOf(
            createTransaction(1, "EXITO", "Bancolombia Compra en EXITO"),
            createTransaction(2, "CARULLA", "Bancolombia Compra en CARULLA"),
        )

        val rule = engine.learnRule(transactions, Category.GROCERIES)

        assertNotNull(rule)
        val senderConditions = rule.conditions.filterIsInstance<RuleCondition.SenderContains>()
        assertTrue(senderConditions.isNotEmpty())
        assertEquals("BANCOLOMBIA", senderConditions.first().keyword)
    }

    @Test
    fun `matchesRule returns true when all conditions match`() {
        val transaction = createTransaction(1, "EXITO")

        val rule = engine.learnRule(
            listOf(
                createTransaction(2, "EXITO"),
                createTransaction(3, "EXITO"),
            ),
            Category.GROCERIES,
        )

        assertNotNull(rule)
        assertTrue(engine.matchesRule(transaction, rule))
    }

    @Test
    fun `matchesRule returns false when merchant does not match`() {
        val transaction = createTransaction(1, "CARULLA")

        val rule = engine.learnRule(
            listOf(
                createTransaction(2, "EXITO"),
                createTransaction(3, "EXITO"),
            ),
            Category.GROCERIES,
        )

        assertNotNull(rule)
        assertFalse(engine.matchesRule(transaction, rule))
    }

    @Test
    fun `matchesRule returns false when rule is disabled`() {
        val transaction = createTransaction(1, "EXITO")

        val rule = engine.learnRule(
            listOf(
                createTransaction(2, "EXITO"),
                createTransaction(3, "EXITO"),
            ),
            Category.GROCERIES,
        )

        assertNotNull(rule)
        val disabledRule = rule.copy(enabled = false)
        assertFalse(engine.matchesRule(transaction, disabledRule))
    }

    @Test
    fun `matchesRule with AnyKeyword condition`() {
        val transaction = createTransaction(1, "RESTAURANTE LA PARRILLA")

        val rule = engine.learnRule(
            listOf(
                createTransaction(2, "RESTAURANTE EL CORRAL"),
                createTransaction(3, "RESTAURANTE DON JEDIONDO"),
            ),
            Category.RESTAURANT,
        )

        assertNotNull(rule)
        assertTrue(engine.matchesRule(transaction, rule))
    }

    @Test
    fun `generates descriptive rule name`() {
        val transactions = listOf(
            createTransaction(1, "EXITO"),
            createTransaction(2, "EXITO"),
        )

        val rule = engine.learnRule(transactions, Category.GROCERIES)

        assertNotNull(rule)
        assertTrue(rule.name.contains("EXITO", ignoreCase = true))
        assertTrue(rule.name.contains("groceries", ignoreCase = true))
    }
}
