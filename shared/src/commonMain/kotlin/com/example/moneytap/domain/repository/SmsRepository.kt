package com.example.moneytap.domain.repository

import com.example.moneytap.domain.model.SmsMessage

interface SmsRepository {
    suspend fun getInboxMessages(limit: Int = 100): Result<List<SmsMessage>>
    suspend fun getMessageById(id: Long): Result<SmsMessage?>
    fun isPlatformSupported(): Boolean
}
