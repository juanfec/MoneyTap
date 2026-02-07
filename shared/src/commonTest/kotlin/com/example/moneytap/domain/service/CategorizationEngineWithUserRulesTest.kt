package com.example.moneytap.domain.service

import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.MatchType
import com.example.moneytap.domain.model.RuleCondition
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.model.TransactionType
import com.example.moneytap.domain.model.UserCategorizationRule
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CategorizationEngineWithUserRulesTest {

    private lateinit var teachingEngine: CategoryTeachingEngine

    @BeforeTest
    fun setup() {
        teachingEngine = CategoryTeachingEngine()
    }

    @Test
    fun `categorize uses user rule with highest priority`() {
        val userRule = UserCategorizationRule(
            id = "rule_1",
            name = "EXITO is always groceries",
            conditions = listOf(RuleCondition.MerchantEquals("EXITO")),
            category = Category.GROCERIES,
            priority = 100,
            learnedFromExamples = null,
            enabled = true,
            createdAt = Clock.System.now(),
        )

        val engine = CategorizationEngine(
            userRules = listOf(userRule),
            categoryTeachingEngine = teachingEngine,
        )

        val transaction = TransactionInfo(
            smsId = 1L,
            type = TransactionType.DEBIT,
            amount = 50000.0,
            currency = "COP",
            balance = null,
            cardLast4 = null,
            merchant = "EXITO",
            description = null,
            reference = null,
            bankName = "Bancolombia",
            timestamp = Clock.System.now(),
            rawMessage = "Test",
        )

        val result = engine.categorize(transaction)

        assertEquals(Category.GROCERIES, result.category)
        assertEquals(MatchType.USER_RULE, result.matchType)
        assertEquals(1.0, result.confidence)
    }

    @Test
    fun `categorize falls back to built-in matching when no user rule matches`() {
        val userRule = UserCategorizationRule(
            id = "rule_1",
            name = "CARULLA is groceries",
            conditions = listOf(RuleCondition.MerchantEquals("CARULLA")),
            category = Category.GROCERIES,
            priority = 100,
            learnedFromExamples = null,
            enabled = true,
            createdAt = Clock.System.now(),
        )

        val engine = CategorizationEngine(
            userRules = listOf(userRule),
            categoryTeachingEngine = teachingEngine,
        )

        // Transaction with different merchant
        val transaction = TransactionInfo(
            smsId = 1L,
            type = TransactionType.DEBIT,
            amount = 50000.0,
            currency = "COP",
            balance = null,
            cardLast4 = null,
            merchant = "EXITO", // EXITO is in built-in dictionary
            description = null,
            reference = null,
            bankName = "Bancolombia",
            timestamp = Clock.System.now(),
            rawMessage = "Test",
        )

        val result = engine.categorize(transaction)

        // Should use built-in exact match for EXITO
        assertEquals(Category.GROCERIES, result.category)
        assertEquals(MatchType.EXACT, result.matchType)
    }

    @Test
    fun `categorize works without user rules - backward compatibility`() {
        // No user rules provided
        val engine = CategorizationEngine()

        val transaction = TransactionInfo(
            smsId = 1L,
            type = TransactionType.DEBIT,
            amount = 50000.0,
            currency = "COP",
            balance = null,
            cardLast4 = null,
            merchant = "EXITO",
            description = null,
            reference = null,
            bankName = "Bancolombia",
            timestamp = Clock.System.now(),
            rawMessage = "Test",
        )

        val result = engine.categorize(transaction)

        // Should work with built-in matching
        assertEquals(Category.GROCERIES, result.category)
        assertEquals(MatchType.EXACT, result.matchType)
    }

    @Test
    fun `categorize respects rule priority order`() {
        val lowPriorityRule = UserCategorizationRule(
            id = "rule_low",
            name = "EXITO is restaurant",
            conditions = listOf(RuleCondition.MerchantEquals("EXITO")),
            category = Category.RESTAURANT,
            priority = 50,
            learnedFromExamples = null,
            enabled = true,
            createdAt = Clock.System.now(),
        )

        val highPriorityRule = UserCategorizationRule(
            id = "rule_high",
            name = "EXITO is coffee",
            conditions = listOf(RuleCondition.MerchantEquals("EXITO")),
            category = Category.COFFEE,
            priority = 150,
            learnedFromExamples = null,
            enabled = true,
            createdAt = Clock.System.now(),
        )

        // List is NOT sorted - engine should check in order
        val engine = CategorizationEngine(
            userRules = listOf(highPriorityRule, lowPriorityRule),
            categoryTeachingEngine = teachingEngine,
        )

        val transaction = TransactionInfo(
            smsId = 1L,
            type = TransactionType.DEBIT,
            amount = 50000.0,
            currency = "COP",
            balance = null,
            cardLast4 = null,
            merchant = "EXITO",
            description = null,
            reference = null,
            bankName = "Bancolombia",
            timestamp = Clock.System.now(),
            rawMessage = "Test",
        )

        val result = engine.categorize(transaction)

        // Should use first matching rule (high priority)
        assertEquals(Category.COFFEE, result.category)
        assertEquals(MatchType.USER_RULE, result.matchType)
    }

    @Test
    fun `categorize uses AnyKeyword condition`() {
        val keywordRule = UserCategorizationRule(
            id = "rule_1",
            name = "Restaurants with keyword",
            conditions = listOf(RuleCondition.AnyKeyword(listOf("RESTAURANTE", "COMIDA"))),
            category = Category.RESTAURANT,
            priority = 100,
            learnedFromExamples = null,
            enabled = true,
            createdAt = Clock.System.now(),
        )

        val engine = CategorizationEngine(
            userRules = listOf(keywordRule),
            categoryTeachingEngine = teachingEngine,
        )

        val transaction = TransactionInfo(
            smsId = 1L,
            type = TransactionType.DEBIT,
            amount = 25000.0,
            currency = "COP",
            balance = null,
            cardLast4 = null,
            merchant = "RESTAURANTE LA PARRILLA",
            description = null,
            reference = null,
            bankName = "Nequi",
            timestamp = Clock.System.now(),
            rawMessage = "Test",
        )

        val result = engine.categorize(transaction)

        assertEquals(Category.RESTAURANT, result.category)
        assertEquals(MatchType.USER_RULE, result.matchType)
    }

    @Test
    fun `categorize ignores disabled rules`() {
        val disabledRule = UserCategorizationRule(
            id = "rule_1",
            name = "Disabled rule",
            conditions = listOf(RuleCondition.MerchantEquals("EXITO")),
            category = Category.COFFEE,
            priority = 100,
            learnedFromExamples = null,
            enabled = false, // Disabled!
            createdAt = Clock.System.now(),
        )

        val engine = CategorizationEngine(
            userRules = listOf(disabledRule),
            categoryTeachingEngine = teachingEngine,
        )

        val transaction = TransactionInfo(
            smsId = 1L,
            type = TransactionType.DEBIT,
            amount = 50000.0,
            currency = "COP",
            balance = null,
            cardLast4 = null,
            merchant = "EXITO",
            description = null,
            reference = null,
            bankName = "Bancolombia",
            timestamp = Clock.System.now(),
            rawMessage = "Test",
        )

        val result = engine.categorize(transaction)

        // Should skip disabled rule and use built-in matching
        assertEquals(Category.GROCERIES, result.category)
        assertEquals(MatchType.EXACT, result.matchType)
    }

    @Test
    fun `categorize with multiple conditions all must match`() {
        val multiConditionRule = UserCategorizationRule(
            id = "rule_1",
            name = "EXITO and amount range",
            conditions = listOf(
                RuleCondition.MerchantEquals("EXITO"),
                RuleCondition.AmountRange(min = 10000.0, max = 50000.0),
            ),
            category = Category.GROCERIES,
            priority = 100,
            learnedFromExamples = null,
            enabled = true,
            createdAt = Clock.System.now(),
        )

        val engine = CategorizationEngine(
            userRules = listOf(multiConditionRule),
            categoryTeachingEngine = teachingEngine,
        )

        // Transaction that matches merchant but not amount
        val transaction1 = TransactionInfo(
            smsId = 1L,
            type = TransactionType.DEBIT,
            amount = 100000.0, // Outside range
            currency = "COP",
            balance = null,
            cardLast4 = null,
            merchant = "EXITO",
            description = null,
            reference = null,
            bankName = "Bancolombia",
            timestamp = Clock.System.now(),
            rawMessage = "Test",
        )

        val result1 = engine.categorize(transaction1)

        // Should fall back to built-in (both conditions must match)
        assertEquals(MatchType.EXACT, result1.matchType)

        // Transaction that matches both conditions
        val transaction2 = TransactionInfo(
            smsId = 2L,
            type = TransactionType.DEBIT,
            amount = 30000.0, // Within range
            currency = "COP",
            balance = null,
            cardLast4 = null,
            merchant = "EXITO",
            description = null,
            reference = null,
            bankName = "Bancolombia",
            timestamp = Clock.System.now(),
            rawMessage = "Test",
        )

        val result2 = engine.categorize(transaction2)

        // Should use user rule
        assertEquals(MatchType.USER_RULE, result2.matchType)
    }
}
