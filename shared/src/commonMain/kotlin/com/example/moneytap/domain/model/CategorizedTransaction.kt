package com.example.moneytap.domain.model

/**
 * A transaction that has been categorized by the categorization engine.
 *
 * @property transaction The original parsed transaction
 * @property category The assigned spending category
 * @property confidence Confidence score (0.0 to 1.0) of the categorization
 * @property matchType How the category was determined
 */
data class CategorizedTransaction(
    val transaction: TransactionInfo,
    val category: Category,
    val confidence: Double,
    val matchType: MatchType,
)

/**
 * Indicates how a transaction was matched to its category.
 */
enum class MatchType {
    /** Direct match against known merchant names */
    EXACT,

    /** Fuzzy string matching (Levenshtein similarity) */
    FUZZY,

    /** Matched via category keywords in description */
    KEYWORD,

    /** Matched via user-defined SMS parsing pattern */
    USER_PATTERN,

    /** Matched via user-defined categorization rule */
    USER_RULE,

    /** No match found, assigned default category */
    DEFAULT,
}
