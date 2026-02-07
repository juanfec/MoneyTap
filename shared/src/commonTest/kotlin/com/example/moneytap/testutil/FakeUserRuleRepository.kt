package com.example.moneytap.testutil

import com.example.moneytap.domain.model.UserCategorizationRule
import com.example.moneytap.domain.repository.UserRuleRepository

class FakeUserRuleRepository : UserRuleRepository {

    private val rules = mutableMapOf<String, UserCategorizationRule>()

    var saveRuleCallCount = 0
        private set

    override suspend fun saveRule(rule: UserCategorizationRule) {
        saveRuleCallCount++
        rules[rule.id] = rule
    }

    override suspend fun getEnabledRules(): List<UserCategorizationRule> =
        rules.values
            .filter { it.enabled }
            .sortedByDescending { it.priority }

    override suspend fun deleteRule(id: String) {
        rules.remove(id)
    }

    override suspend fun updateRulePriority(id: String, priority: Int) {
        rules[id]?.let { rule ->
            rules[id] = rule.copy(priority = priority)
        }
    }

    fun clear() {
        rules.clear()
        saveRuleCallCount = 0
    }
}
