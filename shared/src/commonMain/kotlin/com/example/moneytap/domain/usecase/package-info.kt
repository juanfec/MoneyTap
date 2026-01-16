/**
 * Use Cases (Interactors)
 *
 * This package contains use cases that encapsulate business logic.
 * Each use case represents a single action the user can perform.
 *
 * Guidelines:
 * - Single Responsibility: one use case = one action
 * - Use operator fun invoke() for cleaner call sites
 * - Inject repository interfaces, not implementations
 * - Keep use cases small and focused
 */
package com.example.moneytap.domain.usecase
