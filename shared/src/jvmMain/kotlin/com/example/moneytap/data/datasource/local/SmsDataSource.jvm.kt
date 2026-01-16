package com.example.moneytap.data.datasource.local

import com.example.moneytap.data.dto.SmsMessageDto

actual class SmsDataSource {
    actual suspend fun readInbox(limit: Int): Result<List<SmsMessageDto>> =
        Result.failure(UnsupportedOperationException("SMS reading is not supported on Desktop"))

    actual suspend fun readMessageById(id: Long): Result<SmsMessageDto?> =
        Result.failure(UnsupportedOperationException("SMS reading is not supported on Desktop"))

    actual fun isSupported(): Boolean = false
}
