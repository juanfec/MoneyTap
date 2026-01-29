package com.example.moneytap.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class InferredPattern(
    val segments: List<PatternSegment>,
    val amountFormat: AmountFormat,
    val confidence: Double,
)

@Serializable
sealed class PatternSegment {
    @Serializable
    data class FixedText(val text: String, val fuzzyMatch: Boolean = true) : PatternSegment()

    @Serializable
    data class Variable(val fieldType: FieldType) : PatternSegment()
}
