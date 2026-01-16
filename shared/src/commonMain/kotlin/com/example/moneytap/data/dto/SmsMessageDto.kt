package com.example.moneytap.data.dto

data class SmsMessageDto(
    val id: Long,
    val address: String?,
    val body: String?,
    val date: Long,
    val read: Int,
)
