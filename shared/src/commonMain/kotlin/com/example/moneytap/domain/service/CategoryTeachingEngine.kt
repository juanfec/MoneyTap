package com.example.moneytap.domain.service

import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.RuleCondition
import com.example.moneytap.domain.model.UserCategorizationRule
import com.example.moneytap.util.StringSimilarity
import kotlinx.datetime.Clock

/**
 * Learns categorization rules from user-provided transaction examples.
 *
 * Analyzes common patterns across multiple transactions to generate rules
 * that automatically categorize similar future transactions.
 */
class CategoryTeachingEngine {

    private val genericWords = setOf(
        "DE", "LA", "EL", "LOS", "LAS", "DEL", "AL",
        "SAS", "S.A.S", "SA", "S.A", "LTDA", "LIMITADA",
        "INC", "LLC", "CO", "CORPORATION", "CORP",
        "Y", "E", "O", "EN", "CON", "POR", "PARA",
        "THE", "AND", "OR", "OF", "TO", "IN", "FOR",
    )

    /**
     * Learns a categorization rule from multiple transaction examples.
     *
     * @param transactions List of 2+ transactions that should be categorized the same way
     * @param category The category to assign to matching transactions
     * @param ruleName Optional name for the rule (generated if not provided)
     * @return UserCategorizationRule, or null if no common pattern found
     */
    fun learnRule(
        transactions: List<CategorizedTransaction>,
        category: Category,
        ruleName: String? = null,
    ): UserCategorizationRule? {
        if (transactions.size < 2) return null

        val conditions = mutableListOf<RuleCondition>()
        val exampleIds = transactions.map { it.transaction.smsId.toString() }

        // Extract and normalize merchants
        val merchants = transactions.mapNotNull { it.transaction.merchant }.map { normalizeMerchant(it) }

        if (merchants.isNotEmpty()) {
            // Check if all merchants are exactly the same
            val uniqueMerchants = merchants.toSet()
            if (uniqueMerchants.size == 1) {
                // Exact match - use MerchantEquals
                conditions.add(RuleCondition.MerchantEquals(uniqueMerchants.first()))
            } else {
                // Different merchants - find common keywords
                val commonKeywords = findCommonKeywords(merchants)
                if (commonKeywords.isNotEmpty()) {
                    conditions.add(RuleCondition.AnyKeyword(commonKeywords))
                }
            }
        }

        // Check if all transactions are from the same sender
        val senderIds = transactions.map { it.transaction.rawMessage }
            .mapNotNull { extractSenderId(it) }

        if (senderIds.isNotEmpty()) {
            val uniqueSenders = senderIds.toSet()
            if (uniqueSenders.size == 1) {
                conditions.add(RuleCondition.SenderContains(uniqueSenders.first()))
            }
        }

        // If no conditions found, rule cannot be created
        if (conditions.isEmpty()) return null

        // Generate rule name if not provided
        val generatedName = ruleName ?: generateRuleName(conditions, category)

        // Generate a simple ID using timestamp
        val ruleId = "rule_${Clock.System.now().toEpochMilliseconds()}"

        return UserCategorizationRule(
            id = ruleId,
            name = generatedName,
            conditions = conditions,
            category = category,
            priority = 100, // Default priority
            learnedFromExamples = exampleIds,
            enabled = true,
            createdAt = Clock.System.now(),
        )
    }

    private fun normalizeMerchant(merchant: String): String {
        return StringSimilarity.normalizeMerchantName(merchant)
    }

    private fun findCommonKeywords(merchants: List<String>): List<String> {
        if (merchants.isEmpty()) return emptyList()

        // Tokenize each merchant into words
        val tokenSets = merchants.map { merchant ->
            tokenize(merchant).toSet()
        }

        // Find tokens that appear in ALL merchants
        val commonTokens = tokenSets.reduce { acc, tokens -> acc.intersect(tokens) }

        // Filter out generic words
        val meaningfulTokens = commonTokens.filter { token ->
            token.length >= 3 && token !in genericWords
        }

        return meaningfulTokens.toList()
    }

    private fun tokenize(text: String): List<String> {
        return text
            .split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun extractSenderId(rawMessage: String): String? {
        // Simple heuristic - in production, parse sender from SMS metadata
        // For now, extract first word (often the bank name)
        val firstWord = rawMessage.split(Regex("""\s+""")).firstOrNull()
        return if (firstWord != null && firstWord.length >= 3) {
            firstWord.uppercase()
        } else {
            null
        }
    }

    private fun generateRuleName(conditions: List<RuleCondition>, category: Category): String {
        val conditionDesc = when (val firstCondition = conditions.firstOrNull()) {
            is RuleCondition.MerchantEquals -> "\"${firstCondition.name}\""
            is RuleCondition.AnyKeyword -> firstCondition.keywords.joinToString(", ")
            is RuleCondition.MerchantContains -> "\"${firstCondition.keyword}\""
            is RuleCondition.SenderContains -> "from ${firstCondition.keyword}"
            is RuleCondition.AmountRange -> "amounts ${firstCondition.min}-${firstCondition.max}"
            null -> "transactions"
        }

        return "Categorize $conditionDesc as ${category.name.lowercase()}"
    }

    /**
     * Evaluates if a transaction matches a user-defined rule.
     *
     * @param transaction The transaction to evaluate
     * @param rule The rule to check against
     * @return true if the transaction matches all conditions in the rule
     */
    fun matchesRule(transaction: CategorizedTransaction, rule: UserCategorizationRule): Boolean {
        if (!rule.enabled) return false

        // All conditions must match
        return rule.conditions.all { condition ->
            matchesCondition(transaction, condition)
        }
    }

    private fun matchesCondition(transaction: CategorizedTransaction, condition: RuleCondition): Boolean {
        return when (condition) {
            is RuleCondition.MerchantEquals -> {
                val merchant = transaction.transaction.merchant ?: return false
                normalizeMerchant(merchant) == normalizeMerchant(condition.name)
            }

            is RuleCondition.MerchantContains -> {
                val merchant = transaction.transaction.merchant ?: return false
                StringSimilarity.containsIgnoreCase(merchant, condition.keyword)
            }

            is RuleCondition.AnyKeyword -> {
                val merchant = transaction.transaction.merchant ?: return false
                val normalizedMerchant = normalizeMerchant(merchant)
                condition.keywords.any { keyword ->
                    normalizedMerchant.contains(keyword.uppercase())
                }
            }

            is RuleCondition.SenderContains -> {
                val senderId = extractSenderId(transaction.transaction.rawMessage) ?: return false
                senderId.contains(condition.keyword.uppercase())
            }

            is RuleCondition.AmountRange -> {
                val amount = transaction.transaction.amount
                val min = condition.min ?: Double.MIN_VALUE
                val max = condition.max ?: Double.MAX_VALUE
                amount in min..max
            }
        }
    }
}
