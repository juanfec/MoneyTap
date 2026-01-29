package com.example.moneytap.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UserCategorizationRule(
    val id: String,
    val name: String,
    val conditions: List<RuleCondition>,
    val category: Category,
    val priority: Int,
    val learnedFromExamples: List<String>?,
    val enabled: Boolean = true,
    val createdAt: Instant,
)

@Serializable
sealed class RuleCondition {
    @Serializable
    data class MerchantContains(val keyword: String) : RuleCondition()

    @Serializable
    data class MerchantEquals(val name: String) : RuleCondition()

    @Serializable
    data class SenderContains(val keyword: String) : RuleCondition()

    @Serializable
    data class AmountRange(val min: Double?, val max: Double?) : RuleCondition()

    @Serializable
    data class AnyKeyword(val keywords: List<String>) : RuleCondition()
}
