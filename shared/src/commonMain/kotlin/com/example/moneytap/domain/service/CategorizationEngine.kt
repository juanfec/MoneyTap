package com.example.moneytap.domain.service

import com.example.moneytap.data.categorization.MerchantDictionary
import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.MatchType
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.util.StringSimilarity

/**
 * Engine for categorizing transactions using a 5-layer matching approach:
 * 1. Exact match - Direct lookup in merchant dictionary
 * 2. Substring match - Known merchant contained in extracted name (e.g., "MAS POR MENOS" in "SUPERM MAS POR MENOS C")
 * 3. Fuzzy match - Levenshtein similarity > 85%
 * 4. Keyword match - Search for category keywords in merchant/description
 * 5. Default - Assign UNCATEGORIZED when no match found
 */
class CategorizationEngine {

    companion object {
        private const val EXACT_MATCH_CONFIDENCE = 0.95
        private const val SUBSTRING_MATCH_CONFIDENCE = 0.9
        private const val FUZZY_MATCH_THRESHOLD = 0.85
        private const val KEYWORD_MATCH_CONFIDENCE = 0.7
        private const val DEFAULT_CONFIDENCE = 0.0
    }

    /**
     * Categorizes a single transaction using the 4-layer matching engine.
     *
     * @param transaction The transaction to categorize
     * @return A [CategorizedTransaction] with the assigned category and confidence
     */
    fun categorize(transaction: TransactionInfo): CategorizedTransaction {
        val merchantName = transaction.merchant?.let {
            StringSimilarity.normalizeMerchantName(it)
        } ?: ""
        val description = transaction.description?.uppercase() ?: ""

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
     * Layer 3: Keyword match - search for category keywords in text.
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
}
