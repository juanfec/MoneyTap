/**
 * Domain Models (Entities)
 *
 * This package contains domain entities that represent core business objects.
 * These models are independent of any framework and contain only business logic.
 *
 * Guidelines:
 * - Use data classes for simple value objects
 * - Use sealed classes for restricted hierarchies
 * - Keep models immutable (use val, not var)
 * - No framework dependencies (no Android, no Ktor, etc.)
 */
package com.example.moneytap.domain.model
