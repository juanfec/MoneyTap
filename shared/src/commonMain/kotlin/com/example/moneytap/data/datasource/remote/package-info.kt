/**
 * Remote Data Sources
 *
 * This package contains remote data source implementations (API clients).
 *
 * Guidelines:
 * - Use Ktor Client for HTTP requests
 * - HTTPS only for all network communication
 * - Handle network errors and timeouts gracefully
 * - Return DTOs, not domain models
 * - Implement certificate pinning for sensitive APIs
 */
package com.example.moneytap.data.datasource.remote
