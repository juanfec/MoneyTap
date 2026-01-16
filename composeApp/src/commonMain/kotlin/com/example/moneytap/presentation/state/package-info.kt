/**
 * UI State Classes
 *
 * This package contains UI state data classes and sealed classes for screen states.
 *
 * Guidelines:
 * - Use data classes for screen state
 * - Use sealed classes for loading/success/error states
 * - Keep state immutable (all val properties)
 * - Include all UI-relevant data in state objects
 * - Mark with @Immutable or @Stable for Compose optimization
 */
package com.example.moneytap.presentation.state
