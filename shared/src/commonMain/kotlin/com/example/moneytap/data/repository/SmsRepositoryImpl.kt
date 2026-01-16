package com.example.moneytap.data.repository

import com.example.moneytap.data.datasource.local.SmsDataSource
import com.example.moneytap.data.mapper.toDomain
import com.example.moneytap.domain.model.SmsMessage
import com.example.moneytap.domain.repository.SmsRepository

class SmsRepositoryImpl(
    private val smsDataSource: SmsDataSource,
) : SmsRepository {

    override suspend fun getInboxMessages(limit: Int): Result<List<SmsMessage>> =
        smsDataSource.readInbox(limit).map { dtoList ->
            dtoList.toDomain()
        }

    override suspend fun getMessageById(id: Long): Result<SmsMessage?> =
        smsDataSource.readMessageById(id).map { dto ->
            dto?.toDomain()
        }

    override fun isPlatformSupported(): Boolean = smsDataSource.isSupported()
}
