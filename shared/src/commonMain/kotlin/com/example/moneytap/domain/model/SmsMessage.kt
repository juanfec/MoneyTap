package com.example.moneytap.domain.model

import kotlinx.datetime.Instant

data class SmsMessage(
    val id: Long,
    val sender: String,
    val body: String,
    val timestamp: Instant,
    val isRead: Boolean,
)
