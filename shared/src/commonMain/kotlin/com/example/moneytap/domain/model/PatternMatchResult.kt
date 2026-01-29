package com.example.moneytap.domain.model

data class PatternMatchResult(
    val extractedFields: Map<FieldType, String>,
    val confidence: Double,
    val patternId: String,
)
