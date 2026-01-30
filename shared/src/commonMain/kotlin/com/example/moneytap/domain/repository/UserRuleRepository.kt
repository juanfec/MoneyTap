package com.example.moneytap.domain.repository

import com.example.moneytap.domain.model.UserCategorizationRule

interface UserRuleRepository {
    suspend fun saveRule(rule: UserCategorizationRule)
    suspend fun getEnabledRules(): List<UserCategorizationRule>
    suspend fun deleteRule(id: String)
    suspend fun updateRulePriority(id: String, priority: Int)
}
