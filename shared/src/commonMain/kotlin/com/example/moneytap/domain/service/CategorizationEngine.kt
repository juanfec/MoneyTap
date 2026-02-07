package com.example.moneytap.domain.service

import com.example.moneytap.data.categorization.MerchantDictionary
import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.MatchType
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.model.UserCategorizationRule
import com.example.moneytap.util.StringSimilarity

/**
 * Engine for categorizing transactions using a 6-layer matching approach:
 * 0. User rules - Custom rules defined by the user (highest priority)
 * 1. Exact match - Direct lookup in merchant dictionary
 * 2. Substring match - Known merchant contained in extracted name
 * 3. Fuzzy match - Levenshtein similarity > 85%
 * 4. Keyword match - Search for category keywords in merchant/description
 * 5. Default - Assign UNCATEGORIZED when no match found
 *
 * @property userRules Optional list of user-defined categorization rules
 * @property categoryTeachingEngine Optional engine for matching user rules
 */
class CategorizationEngine(
    private val userRules: List<UserCategorizationRule> = emptyList(),
    private val categoryTeachingEngine: CategoryTeachingEngine? = null,
) {

    companion object {
        private const val EXACT_MATCH_CONFIDENCE = 0.95
        private const val SUBSTRING_MATCH_CONFIDENCE = 0.9
        private const val FUZZY_MATCH_THRESHOLD = 0.85
        private const val KEYWORD_MATCH_CONFIDENCE = 0.7
        private const val DEFAULT_CONFIDENCE = 0.0
    }

    /**
     * Categorizes a single transaction using the 6-layer matching engine.
     *
     * @param transaction The transaction to categorize
     * @return A [CategorizedTransaction] with the assigned category and confidence
     */
    fun categorize(transaction: TransactionInfo): CategorizedTransaction {
        val merchantName = transaction.merchant?.let {
            StringSimilarity.normalizeMerchantName(it)
        } ?: ""
        val description = transaction.description?.uppercase() ?: ""

        // Layer 0: User rules (highest priority)
        if (userRules.isNotEmpty() && categoryTeachingEngine != null) {
            matchUserRule(transaction)?.let { return it }
        }

        // Layer 1: Exact match
        exactMatch(merchantName)?.let { category ->
            return CategorizedTransaction(
                transaction = transaction,
                category = category,
                confidence = EXACT_MATCH_CONFIDENCE,
                matchType = MatchType.EXACT,
            )
        }

        // Layer 2: Substring match - check if any known merchant is contained in the extracted name
        substringMatch(merchantName)?.let { category ->
            return CategorizedTransaction(
                transaction = transaction,
                category = category,
                confidence = SUBSTRING_MATCH_CONFIDENCE,
                matchType = MatchType.FUZZY, // Using FUZZY since it's a close match
            )
        }

        // Layer 3: Fuzzy match
        fuzzyMatch(merchantName)?.let { (category, similarity) ->
            return CategorizedTransaction(
                transaction = transaction,
                category = category,
                confidence = similarity * 0.9,
                matchType = MatchType.FUZZY,
            )
        }

        // Layer 4: Keyword match
        keywordMatch(merchantName, description)?.let { category ->
            return CategorizedTransaction(
                transaction = transaction,
                category = category,
                confidence = KEYWORD_MATCH_CONFIDENCE,
                matchType = MatchType.KEYWORD,
            )
        }

        // Layer 5: Default
        return CategorizedTransaction(
            transaction = transaction,
            category = Category.UNCATEGORIZED,
            confidence = DEFAULT_CONFIDENCE,
            matchType = MatchType.DEFAULT,
        )
    }

    /**
     * Categorizes a list of transactions.
     *
     * @param transactions The transactions to categorize
     * @return List of categorized transactions
     */
    fun categorizeAll(transactions: List<TransactionInfo>): List<CategorizedTransaction> =
        transactions.map { categorize(it) }

    /**
     * Layer 1: Exact match lookup in merchant dictionary.
     */
    private fun exactMatch(merchantName: String): Category? {
        if (merchantName.isBlank()) return null
        return MerchantDictionary.merchantToCategory[merchantName]
    }

    /**
     * Layer 2: Substring match - finds if any known merchant name is contained
     * within the extracted merchant name, or vice versa.
     *
     * This handles cases like:
     * - "SUPERM MAS POR MENOS C" containing "SUPERM MAS POR MENOS"
     * - "MAS POR MENOS" being contained in "SUPERM MAS POR MENOS"
     * 
     * Returns the longest matching merchant to prefer more specific matches.
     */
    private fun substringMatch(merchantName: String): Category? {
        if (merchantName.isBlank()) return null

        var bestMatch: Category? = null
        var bestMatchLength = 0

        for (knownMerchant in MerchantDictionary.getAllMerchantNames()) {
            // Check bidirectional substring match
            val isMatch = merchantName.contains(knownMerchant, ignoreCase = true) || 
                         knownMerchant.contains(merchantName, ignoreCase = true)
            
            if (isMatch) {
                // Prefer longer matches (more specific)
                if (knownMerchant.length > bestMatchLength) {
                    bestMatchLength = knownMerchant.length
                    bestMatch = MerchantDictionary.merchantToCategory[knownMerchant]
                }
            }
        }

        return bestMatch
    }

    /**
     * Layer 3: Fuzzy match using Levenshtein similarity.
     * Returns the best match above the threshold, if any.
     */
    private fun fuzzyMatch(merchantName: String): Pair<Category, Double>? {
        if (merchantName.isBlank()) return null

        var bestMatch: Pair<Category, Double>? = null
        var bestSimilarity = 0.0

        for (knownMerchant in MerchantDictionary.getAllMerchantNames()) {
            val similarity = StringSimilarity.similarity(merchantName, knownMerchant)
            if (similarity >= FUZZY_MATCH_THRESHOLD && similarity > bestSimilarity) {
                bestSimilarity = similarity
                MerchantDictionary.merchantToCategory[knownMerchant]?.let { category ->
                    bestMatch = category to similarity
                }
            }
        }

        return bestMatch
    }

    /**
     * Layer 4: Keyword match - search for category keywords in text.
     */
    private fun keywordMatch(merchantName: String, description: String): Category? {
        val combinedText = "$merchantName $description"

        for ((keyword, category) in MerchantDictionary.keywordToCategory) {
            if (StringSimilarity.containsIgnoreCase(combinedText, keyword)) {
                return category
            }
        }

        return null
    }

    /**
     * Layer 0: Check if transaction matches any user-defined rule.
     * Rules are checked in priority order (highest first).
     */
    private fun matchUserRule(transaction: TransactionInfo): CategorizedTransaction? {
        val teachingEngine = categoryTeachingEngine ?: return null

        // Rules are already sorted by priority in the repository
        for (rule in userRules) {
            val categorizedTx = CategorizedTransaction(
                transaction = transaction,
                category = Category.UNCATEGORIZED,
                confidence = 0.0,
                matchType = MatchType.DEFAULT,
            )

            if (teachingEngine.matchesRule(categorizedTx, rule)) {
                return CategorizedTransaction(
                    transaction = transaction,
                    category = rule.category,
                    confidence = 1.0, // User rules have highest confidence
                    matchType = MatchType.USER_RULE,
                )
            }
        }

        return null
    }
}
