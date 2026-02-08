package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.model.TransactionType
import com.example.moneytap.domain.repository.TransactionRepository

/**
 * Use case for updating the type of a transaction (e.g., DEBIT to CREDIT).
 */
class UpdateTransactionTypeUseCase(
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(smsId: Long, newType: TransactionType): Result<Unit> {
        return runCatching {
            transactionRepository.updateTransactionType(smsId, newType)
        }
    }
}
