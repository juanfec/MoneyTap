package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.repository.TransactionRepository

/**
 * Updates the category of a transaction and marks it as user-corrected.
 *
 * This sets:
 * - category = newCategory
 * - matchType = USER_RULE
 * - confidence = 1.0
 * - userCorrected = true
 */
class UpdateTransactionCategoryUseCase(
    private val transactionRepository: TransactionRepository,
) {
    /**
     * Updates the category of a transaction.
     *
     * @param smsId The SMS ID of the transaction to update
     * @param newCategory The new category to assign
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(smsId: Long, newCategory: Category): Result<Unit> {
        return runCatching {
            transactionRepository.updateTransactionCategory(smsId, newCategory)
        }
    }
}
