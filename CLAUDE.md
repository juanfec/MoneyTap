# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MoneyTap is a **Kotlin Multiplatform** project targeting Android, iOS, Desktop (JVM), and a Ktor backend server. It uses Compose Multiplatform for the shared UI layer.

## Build Commands

All commands use Gradle. On Windows use `.\gradlew.bat`, on Mac/Linux use `./gradlew`.

| Target | Command |
|--------|---------|
| Android APK | `:composeApp:assembleDebug` |
| Android Release | `:composeApp:assembleRelease` |
| Desktop | `:composeApp:run` |
| Server | `:server:run` |
| iOS | Open `iosApp/` in Xcode |

## Testing

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :composeApp:test
./gradlew :shared:test
./gradlew :server:test
```

Server tests use Ktor test client (`testApplication`).

## Architecture

```
MoneyTap/
├── composeApp/          # Compose Multiplatform UI application
│   └── src/
│       ├── commonMain/  # Shared Compose UI (App.kt)
│       ├── androidMain/ # Android entry (MainActivity)
│       ├── iosMain/     # iOS Compose controller
│       └── jvmMain/     # Desktop entry
├── shared/              # Common business logic library
│   └── src/
│       ├── commonMain/  # Platform interface, Constants, Greeting
│       └── <target>Main/# Platform-specific implementations
├── server/              # Ktor HTTP server (port 8080)
└── iosApp/              # Native SwiftUI wrapper for iOS
```

**Platform abstraction pattern**: `Platform` interface defined in `shared/commonMain` with `expect` function, implemented via `actual` declarations per target.

## Key Technologies

- **Kotlin** 2.3.0
- **Compose Multiplatform** 1.10.0
- **Ktor** 3.3.3 (server)
- **AGP** 8.11.2
- Android: minSdk 24, targetSdk 36

## Configuration Files

- `/gradle/libs.versions.toml` - Centralized version catalog
- `/composeApp/src/androidMain/AndroidManifest.xml` - Android app config
- `/server/src/main/kotlin/Application.kt` - Server routes

## Code Style

- Use `camelCase` for functions and variables, `PascalCase` for classes and composables
- Composable functions should be named as nouns (e.g., `ProfileScreen`, `UserCard`)
- Use `_uiState` for private MutableStateFlow, expose as `uiState`
- Prefer expression bodies for single-expression functions
- Use trailing commas in multi-line parameter lists
- Max line length: 120 characters

## Architecture Rules

- **Shared code first**: Put as much logic as possible in `commonMain`
- **Platform code**: Only use `androidMain`/`iosMain` for platform-specific APIs (permissions, native features, secure storage)
- **UI in composeApp**: All Compose UI goes in `composeApp/commonMain`, not `shared`
- **Business logic in shared**: Domain models, repositories, use cases go in `shared/commonMain`
- Use `expect`/`actual` for platform abstractions, define interface in `commonMain`
- ViewModels should be in `commonMain` using `androidx.lifecycle:lifecycle-viewmodel-compose`
- Keep interfaces small and behavior-focused; version public interfaces instead of breaking them
- Design for gradual adoption: share focused logic subsets (validation, domain calculations) first

## Dependencies

- Add new dependencies to `/gradle/libs.versions.toml`, never hardcode versions
- Prefer Compose Multiplatform libraries over Android-only alternatives
- Use Ktor Client for networking (multiplatform compatible)
- Use Kotlinx Serialization for JSON (not Gson/Moshi)
- Use Kotlinx Coroutines for async work
- Use Koin or Kotlin Inject for DI (multiplatform compatible)
- Prefer KSP over KAPT for annotation processing (faster, KMP compatible)
- Regularly update dependencies to patch security vulnerabilities

## Security

### General Principles
- **Never store secrets in code**: No API keys, tokens, or credentials in source files
- **Minimize sensitive data access**: If you don't need to store user data, don't
- **Use non-reversible forms**: Prefer hashes over storing raw sensitive data when possible
- **Design for compromise**: Assume devices can be inspected; keep secrets server-side

### Network Security
- **HTTPS only**: Never use HTTP for any network communication
- **Certificate pinning**: Implement for high-risk API connections with planned rotation
- Use Ktor's `HttpsRedirect` plugin on server; enforce TLS 1.2+ minimum

### Secure Storage (expect/actual pattern)
```kotlin
// In commonMain - define interface
expect class SecureStorage {
    fun store(key: String, value: String)
    fun retrieve(key: String): String?
    fun delete(key: String)
}
```

### Android Security
- Use **Android Keystore** for cryptographic keys (hardware-backed when available)
- Use **EncryptedSharedPreferences** for sensitive key-value data (or DataStore with encryption)
- Configure **Network Security Config** (`res/xml/network_security_config.xml`) to enforce HTTPS
- Enable **R8/ProGuard** for release builds with proper obfuscation rules
- Implement **biometric authentication** via BiometricPrompt for sensitive operations
- Consider **Credential Manager** with Passkeys for passwordless authentication
- Never log sensitive data; use `BuildConfig.DEBUG` checks for debug logging

### iOS Security
- Use **Keychain Services** for credentials, tokens, and sensitive data
- Leverage **Secure Enclave** for biometric-protected keys when available
- Ensure **App Transport Security (ATS)** is enabled; avoid broad exceptions
- Use `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` for sensitive Keychain items

### Server Security (Ktor)
- Validate and sanitize all input; never trust client data
- Use parameterized queries to prevent SQL injection
- Implement rate limiting and request validation
- Store passwords with bcrypt or Argon2, never plain text
- Use environment variables for secrets, not config files in repo

## Testing

- Write tests in `commonTest` when possible for cross-platform coverage
- Use `kotlin.test` assertions, not JUnit-specific ones
- Name test functions descriptively: `fun \`should return error when input is empty\`()`
- Test ViewModels by testing state changes
- Use Ktor's `testApplication` for server endpoint tests
- Include security tests: input validation, authentication flows, data encryption

## Compose UI Patterns

- Use `remember` and `derivedStateOf` to avoid unnecessary recompositions
- Extract reusable components into separate files
- Use `Modifier` as first optional parameter in composables
- Prefer `LazyColumn`/`LazyRow` over `Column`/`Row` with `forEach` for lists
- Use `collectAsStateWithLifecycle()` for StateFlow in composables
- Keep composables small and focused; extract logic to ViewModel
- **State hoisting**: Lift state up to make composables reusable and testable
- **Slot APIs**: Use lambda parameters for flexible content composition
- Mark classes as `@Stable` or `@Immutable` when appropriate to help compiler optimize
- Avoid allocations in composition; move object creation to `remember` blocks

## Performance

### Android
- Generate **Baseline Profiles** for critical user journeys
- Use R8 full mode for maximum optimization in release builds
- Profile with Android Studio Profiler before optimizing

### Compose
- Use `key()` in `LazyColumn`/`LazyRow` for stable item identity
- Avoid passing unstable lambdas; use `remember` for lambda references
- Defer reads with `derivedStateOf` to minimize recomposition scope
- Use `Modifier.drawWithCache` for expensive drawing operations

### General
- Measure before optimizing; avoid premature optimization
- Use Kotlin's inline functions for high-frequency callbacks

## Error Handling

- Use `Result<T>` or sealed classes for operations that can fail
- Never swallow exceptions silently; log or propagate them
- Use `runCatching` for wrapping throwable code
- Define domain-specific exceptions in `shared/commonMain`
- Show user-friendly error messages in UI, log technical details
- Implement crash reporting (Crashlytics, Sentry) for production builds
- Sanitize error messages: never expose stack traces or internal details to users
