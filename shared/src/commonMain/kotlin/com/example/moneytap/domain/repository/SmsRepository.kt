package com.example.moneytap.domain.repository

import com.example.moneytap.domain.Constants
import com.example.moneytap.domain.model.SmsMessage

interface SmsRepository {
    suspend fun getInboxMessages(limit: Int = Constants.DEFAULT_SMS_LIMIT): Result<List<SmsMessage>>
    suspend fun getMessageById(id: Long): Result<SmsMessage?>
    fun isPlatformSupported(): Boolean
}
