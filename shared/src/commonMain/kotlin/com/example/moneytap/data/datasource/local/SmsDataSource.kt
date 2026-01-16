package com.example.moneytap.data.datasource.local

import com.example.moneytap.data.dto.SmsMessageDto

expect class SmsDataSource {
    suspend fun readInbox(limit: Int): Result<List<SmsMessageDto>>
    suspend fun readMessageById(id: Long): Result<SmsMessageDto?>
    fun isSupported(): Boolean
}
