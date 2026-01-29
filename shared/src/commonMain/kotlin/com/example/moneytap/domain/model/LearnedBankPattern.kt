package com.example.moneytap.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class LearnedBankPattern(
    val id: String,
    val bankName: String,
    val senderIds: List<String>,
    val examples: List<TeachingExample>,
    val inferredPattern: InferredPattern,
    val defaultCategory: Category?,
    val enabled: Boolean = true,
    val successCount: Int = 0,
    val failCount: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)
