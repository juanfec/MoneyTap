/**
 * Repository Interfaces
 *
 * This package contains repository interfaces that define contracts for data access.
 * Implementations live in the data layer.
 *
 * Guidelines:
 * - Define interfaces only, no implementations
 * - Use suspend functions for async operations
 * - Return domain models, not DTOs
 * - Use Result<T> or sealed classes for error handling
 */
package com.example.moneytap.domain.repository
