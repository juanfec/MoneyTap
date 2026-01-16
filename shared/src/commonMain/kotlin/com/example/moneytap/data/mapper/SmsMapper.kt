package com.example.moneytap.data.mapper

import com.example.moneytap.data.dto.SmsMessageDto
import com.example.moneytap.domain.model.SmsMessage
import kotlinx.datetime.Instant

fun SmsMessageDto.toDomain(): SmsMessage = SmsMessage(
    id = id,
    sender = address ?: "Unknown",
    body = body ?: "",
    timestamp = Instant.fromEpochMilliseconds(date),
    isRead = read == 1,
)

fun List<SmsMessageDto>.toDomain(): List<SmsMessage> = map { it.toDomain() }
