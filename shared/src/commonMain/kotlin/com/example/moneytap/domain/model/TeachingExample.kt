package com.example.moneytap.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class TeachingExample(
    val id: String,
    val smsBody: String,
    val senderId: String,
    val selections: List<FieldSelection>,
    val category: Category?,
    val createdAt: Instant,
)
