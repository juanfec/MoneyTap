package com.example.moneytap.testutil

import com.example.moneytap.domain.model.SmsMessage
import com.example.moneytap.domain.repository.SmsRepository

class FakeSmsRepository : SmsRepository {

    var messages: List<SmsMessage> = emptyList()
    var shouldFail: Boolean = false
    var failureException: Exception = RuntimeException("SMS read failed")
    var platformSupported: Boolean = true

    override suspend fun getInboxMessages(limit: Int): Result<List<SmsMessage>> {
        if (shouldFail) return Result.failure(failureException)
        return Result.success(messages.take(limit))
    }

    override suspend fun getMessageById(id: Long): Result<SmsMessage?> {
        if (shouldFail) return Result.failure(failureException)
        return Result.success(messages.find { it.id == id })
    }

    override fun isPlatformSupported(): Boolean = platformSupported
}
