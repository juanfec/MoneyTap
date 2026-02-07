package com.example.moneytap.util

import kotlin.math.min

/**
 * Utility object for string similarity calculations using Levenshtein distance.
 * Used for fuzzy merchant name matching in transaction categorization.
 */
object StringSimilarity {

    /**
     * Calculates the Levenshtein edit distance between two strings.
     * This is the minimum number of single-character edits (insertions, deletions, substitutions)
     * required to change one string into the other.
     *
     * @param s1 First string
     * @param s2 Second string
     * @return The edit distance between the two strings
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val str1 = s1.lowercase()
        val str2 = s2.lowercase()

        if (str1 == str2) return 0
        if (str1.isEmpty()) return str2.length
        if (str2.isEmpty()) return str1.length

        val len1 = str1.length
        val len2 = str2.length

        var prevRow = IntArray(len2 + 1) { it }
        var currRow = IntArray(len2 + 1)

        for (i in 1..len1) {
            currRow[0] = i
            for (j in 1..len2) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                currRow[j] = min(
                    min(currRow[j - 1] + 1, prevRow[j] + 1),
                    prevRow[j - 1] + cost,
                )
            }
            val temp = prevRow
            prevRow = currRow
            currRow = temp
        }

        return prevRow[len2]
    }

    /**
     * Calculates the similarity ratio between two strings.
     * Returns a value between 0.0 (completely different) and 1.0 (identical).
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Similarity ratio between 0.0 and 1.0
     */
    fun similarity(s1: String, s2: String): Double {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val maxLen = maxOf(s1.length, s2.length)
        val distance = levenshteinDistance(s1, s2)

        return 1.0 - (distance.toDouble() / maxLen)
    }

    /**
     * Checks if string [s1] contains [s2] as a substring (case-insensitive).
     *
     * @param s1 String to search in
     * @param s2 Substring to find
     * @return true if s1 contains s2
     */
    fun containsIgnoreCase(s1: String, s2: String): Boolean =
        s1.lowercase().contains(s2.lowercase())

    /**
     * Normalizes a merchant name for comparison by:
     * - Converting to uppercase
     * - Removing common suffixes (S.A., SAS, LTDA, etc.)
     * - Trimming whitespace
     *
     * @param merchantName The merchant name to normalize
     * @return Normalized merchant name
     */
    fun normalizeMerchantName(merchantName: String): String {
        return merchantName
            .uppercase()
            .replace(Regex("""\s+(S\.?A\.?S?|LTDA\.?|INC\.?|LLC\.?|CO\.?)$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Finds the longest common substring between two strings.
     * Used for pattern inference to identify fixed text anchors in SMS messages.
     *
     * @param s1 First string
     * @param s2 Second string
     * @return The longest substring that appears in both strings (case-insensitive)
     */
    fun longestCommonSubstring(s1: String, s2: String): String {
        if (s1.isEmpty() || s2.isEmpty()) return ""

        val str1 = s1.lowercase()
        val str2 = s2.lowercase()

        val len1 = str1.length
        val len2 = str2.length

        var maxLen = 0
        var endIndex = 0

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 1..len1) {
            for (j in 1..len2) {
                if (str1[i - 1] == str2[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                    if (dp[i][j] > maxLen) {
                        maxLen = dp[i][j]
                        endIndex = i
                    }
                }
            }
        }

        return if (maxLen > 0) {
            s1.substring(endIndex - maxLen, endIndex)
        } else {
            ""
        }
    }
}
