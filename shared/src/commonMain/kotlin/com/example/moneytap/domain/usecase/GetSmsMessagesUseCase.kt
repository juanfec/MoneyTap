package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.model.SmsError
import com.example.moneytap.domain.model.SmsException
import com.example.moneytap.domain.model.SmsMessage
import com.example.moneytap.domain.repository.SmsRepository

class GetSmsMessagesUseCase(
    private val smsRepository: SmsRepository,
) {
    suspend operator fun invoke(limit: Int = 100): Result<List<SmsMessage>> {
        if (!smsRepository.isPlatformSupported()) {
            return Result.failure(SmsException(SmsError.PlatformNotSupported))
        }
        return smsRepository.getInboxMessages(limit)
    }
}
