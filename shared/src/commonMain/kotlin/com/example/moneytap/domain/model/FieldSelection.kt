package com.example.moneytap.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FieldSelection(
    val fieldType: FieldType,
    val startIndex: Int,
    val endIndex: Int,
    val selectedText: String,
)
