/**
 * ViewModels
 *
 * This package contains ViewModels that manage UI state and business logic coordination.
 *
 * Guidelines:
 * - Extend ViewModel from androidx.lifecycle:lifecycle-viewmodel-compose
 * - Use StateFlow for UI state (_uiState private, uiState public)
 * - Use SharedFlow for one-time events (navigation, snackbars)
 * - Inject use cases, not repositories directly
 * - Keep ViewModels testable with constructor injection
 */
package com.example.moneytap.presentation.viewmodel
