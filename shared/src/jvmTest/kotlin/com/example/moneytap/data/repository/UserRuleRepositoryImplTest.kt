package com.example.moneytap.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.moneytap.data.database.MoneyTapDatabase
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.RuleCondition
import com.example.moneytap.domain.model.UserCategorizationRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserRuleRepositoryImplTest {

    private lateinit var database: MoneyTapDatabase
    private lateinit var repository: UserRuleRepositoryImpl

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MoneyTapDatabase.Schema.create(driver)
        database = MoneyTapDatabase(driver)
        repository = UserRuleRepositoryImpl(database, Dispatchers.Unconfined)
    }

    private fun createTestRule(
        id: String = "rule_1",
        name: String = "Test Rule",
        conditions: List<RuleCondition> = listOf(
            RuleCondition.MerchantEquals("EXITO"),
        ),
        category: Category = Category.GROCERIES,
        priority: Int = 100,
        learnedFromExamples: List<String>? = null,
        enabled: Boolean = true,
    ): UserCategorizationRule {
        return UserCategorizationRule(
            id = id,
            name = name,
            conditions = conditions,
            category = category,
            priority = priority,
            learnedFromExamples = learnedFromExamples,
            enabled = enabled,
            createdAt = Clock.System.now(),
        )
    }

    @Test
    fun `saveRule stores and retrieves a rule`() = runTest {
        val rule = createTestRule()

        repository.saveRule(rule)
        val result = repository.getEnabledRules()

        assertEquals(1, result.size)
        assertEquals(rule.id, result[0].id)
        assertEquals(rule.name, result[0].name)
        assertEquals(rule.category, result[0].category)
        assertEquals(rule.priority, result[0].priority)
    }

    @Test
    fun `saveRule replaces existing rule with same id`() = runTest {
        val rule1 = createTestRule(id = "rule_1", name = "Rule A")
        val rule2 = createTestRule(id = "rule_1", name = "Rule B")

        repository.saveRule(rule1)
        repository.saveRule(rule2)
        val result = repository.getEnabledRules()

        assertEquals(1, result.size)
        assertEquals("Rule B", result[0].name)
    }

    @Test
    fun `getEnabledRules returns only enabled rules`() = runTest {
        val enabledRule = createTestRule(id = "rule_1", enabled = true)
        val disabledRule = createTestRule(id = "rule_2", enabled = false)

        repository.saveRule(enabledRule)
        repository.saveRule(disabledRule)
        val result = repository.getEnabledRules()

        assertEquals(1, result.size)
        assertEquals("rule_1", result[0].id)
    }

    @Test
    fun `getEnabledRules returns rules sorted by priority descending`() = runTest {
        val lowPriority = createTestRule(id = "rule_1", priority = 50)
        val highPriority = createTestRule(id = "rule_2", priority = 150)
        val mediumPriority = createTestRule(id = "rule_3", priority = 100)

        repository.saveRule(lowPriority)
        repository.saveRule(highPriority)
        repository.saveRule(mediumPriority)

        val result = repository.getEnabledRules()

        assertEquals(3, result.size)
        assertEquals(150, result[0].priority) // Highest first
        assertEquals(100, result[1].priority)
        assertEquals(50, result[2].priority) // Lowest last
    }

    @Test
    fun `deleteRule removes rule from database`() = runTest {
        val rule = createTestRule(id = "rule_1")

        repository.saveRule(rule)
        repository.deleteRule("rule_1")
        val result = repository.getEnabledRules()

        assertEquals(0, result.size)
    }

    @Test
    fun `updateRulePriority changes rule priority`() = runTest {
        val rule = createTestRule(id = "rule_1", priority = 100)

        repository.saveRule(rule)
        repository.updateRulePriority("rule_1", 200)

        val result = repository.getEnabledRules().first()

        assertEquals(200, result.priority)
    }

    @Test
    fun `rule with MerchantEquals condition is preserved`() = runTest {
        val rule = createTestRule(
            conditions = listOf(RuleCondition.MerchantEquals("EXITO")),
        )

        repository.saveRule(rule)
        val result = repository.getEnabledRules().first()

        assertEquals(1, result.conditions.size)
        assertTrue(result.conditions[0] is RuleCondition.MerchantEquals)
        assertEquals("EXITO", (result.conditions[0] as RuleCondition.MerchantEquals).name)
    }

    @Test
    fun `rule with MerchantContains condition is preserved`() = runTest {
        val rule = createTestRule(
            conditions = listOf(RuleCondition.MerchantContains("RESTAURANT")),
        )

        repository.saveRule(rule)
        val result = repository.getEnabledRules().first()

        assertEquals(1, result.conditions.size)
        assertTrue(result.conditions[0] is RuleCondition.MerchantContains)
        assertEquals("RESTAURANT", (result.conditions[0] as RuleCondition.MerchantContains).keyword)
    }

    @Test
    fun `rule with SenderContains condition is preserved`() = runTest {
        val rule = createTestRule(
            conditions = listOf(RuleCondition.SenderContains("Bancolombia")),
        )

        repository.saveRule(rule)
        val result = repository.getEnabledRules().first()

        assertEquals(1, result.conditions.size)
        assertTrue(result.conditions[0] is RuleCondition.SenderContains)
        assertEquals("Bancolombia", (result.conditions[0] as RuleCondition.SenderContains).keyword)
    }

    @Test
    fun `rule with AmountRange condition is preserved`() = runTest {
        val rule = createTestRule(
            conditions = listOf(RuleCondition.AmountRange(min = 10000.0, max = 50000.0)),
        )

        repository.saveRule(rule)
        val result = repository.getEnabledRules().first()

        assertEquals(1, result.conditions.size)
        assertTrue(result.conditions[0] is RuleCondition.AmountRange)
        assertEquals(10000.0, (result.conditions[0] as RuleCondition.AmountRange).min)
        assertEquals(50000.0, (result.conditions[0] as RuleCondition.AmountRange).max)
    }

    @Test
    fun `rule with AnyKeyword condition is preserved`() = runTest {
        val rule = createTestRule(
            conditions = listOf(RuleCondition.AnyKeyword(listOf("CAFE", "COFFEE", "STARBUCKS"))),
        )

        repository.saveRule(rule)
        val result = repository.getEnabledRules().first()

        assertEquals(1, result.conditions.size)
        assertTrue(result.conditions[0] is RuleCondition.AnyKeyword)
        assertEquals(3, (result.conditions[0] as RuleCondition.AnyKeyword).keywords.size)
    }

    @Test
    fun `rule with multiple conditions is preserved`() = runTest {
        val rule = createTestRule(
            conditions = listOf(
                RuleCondition.MerchantEquals("EXITO"),
                RuleCondition.AmountRange(min = 10000.0, max = 50000.0),
                RuleCondition.SenderContains("Bancolombia"),
            ),
        )

        repository.saveRule(rule)
        val result = repository.getEnabledRules().first()

        assertEquals(3, result.conditions.size)
    }

    @Test
    fun `rule with learnedFromExamples is preserved`() = runTest {
        val rule = createTestRule(
            learnedFromExamples = listOf("tx_1", "tx_2", "tx_3"),
        )

        repository.saveRule(rule)
        val result = repository.getEnabledRules().first()

        assertEquals(3, result.learnedFromExamples?.size)
        assertEquals(listOf("tx_1", "tx_2", "tx_3"), result.learnedFromExamples)
    }

    @Test
    fun `rule with null learnedFromExamples is preserved`() = runTest {
        val rule = createTestRule(
            learnedFromExamples = null,
        )

        repository.saveRule(rule)
        val result = repository.getEnabledRules().first()

        assertEquals(null, result.learnedFromExamples)
    }

    @Test
    fun `multiple rules can be stored and retrieved`() = runTest {
        val rule1 = createTestRule(id = "rule_1", name = "Rule A", priority = 100)
        val rule2 = createTestRule(id = "rule_2", name = "Rule B", priority = 150)
        val rule3 = createTestRule(id = "rule_3", name = "Rule C", priority = 75)

        repository.saveRule(rule1)
        repository.saveRule(rule2)
        repository.saveRule(rule3)

        val result = repository.getEnabledRules()

        assertEquals(3, result.size)
        // Should be sorted by priority descending
        assertEquals("Rule B", result[0].name)
        assertEquals("Rule A", result[1].name)
        assertEquals("Rule C", result[2].name)
    }
}
