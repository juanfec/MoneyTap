# CLAUDE.md

## Developer Role

You are a **Senior Kotlin Multiplatform Developer** with expertise in production-grade cross-platform applications, security best practices, and clean code principles. You prioritize code quality, maintainability, and security in every solution.

---

## Project Overview

MoneyTap is a **Kotlin Multiplatform** project targeting Android, iOS, Desktop (JVM), and a Ktor backend server. It uses Compose Multiplatform for the shared UI layer.

## Build Commands

All commands use Gradle. Run from the project root directory.

**Windows (cmd.exe from any shell):**
```bash
cmd.exe /c "cd /d <PROJECT_PATH> && .\gradlew.bat <command>"
```

**Windows (PowerShell/Terminal):**
```powershell
.\gradlew.bat <command>
```

**Mac/Linux:**
```bash
./gradlew <command>
```

> **Note:** For machine-specific paths and ready-to-use commands, see `CLAUDE.local.md` (not tracked by git).

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
:test

# Run specific module tests
:composeApp:test
:shared:test
:server:test
```

Use the appropriate Gradle wrapper command from the Build Commands section above.

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

## Architecture Patterns

### MVVM (Recommended for this project)
- **ViewModel** in `commonMain` using `androidx.lifecycle:lifecycle-viewmodel-compose`
- **State Management**: Use `StateFlow`/`SharedFlow` for reactive state
- **Single Source of Truth**: ViewModel owns UI state
- Clear separation: UI → ViewModel → Repository/UseCase → Data

### Clean Architecture Layers
- **Domain**: Business logic, use cases, domain models (in `shared/commonMain`)
- **Data**: Repositories, data sources, DTOs
- **Presentation**: ViewModels, UI state (in `composeApp/commonMain`)

### Key Principles
- **SOLID**: Single Responsibility, Open-Closed, Liskov Substitution, Interface Segregation, Dependency Inversion
- **Separation of Concerns**: Business logic, UI, and data models live separately
- **Dependency Injection**: Use Koin or Kotlin Inject for DI

## Architecture Rules

- **Shared code first**: Put as much logic as possible in `commonMain`
- **Platform code**: Only use `androidMain`/`iosMain` for platform-specific APIs (permissions, native features, secure storage)
- **UI in composeApp**: All Compose UI goes in `composeApp/commonMain`, not `shared`
- **Business logic in shared**: Domain models, repositories, use cases go in `shared/commonMain`
- Use `expect`/`actual` for platform abstractions, define interface in `commonMain`
- ViewModels should be in `commonMain` using `androidx.lifecycle:lifecycle-viewmodel-compose`
- Keep interfaces small and behavior-focused; version public interfaces instead of breaking them
- Design for gradual adoption: share focused logic subsets (validation, domain calculations) first

## Code Quality Standards

### Code Smells to Avoid

**Critical Kotlin Smells**
- ❌ **Overuse of `!!`**: Replace with safe calls `?.` or Elvis operator `?:`
- ❌ **var with Mutable Collections**: Causes double mutability - prefer `val` with mutable collections OR `var` with immutable
- ❌ **Unsafe Casting (`as`)**: Use `as?` for safe casting
- ❌ **Implicit `it` in Complex Lambdas**: Use explicit parameter names for clarity
- ❌ **Long Functions**: Keep functions small (<20 lines) and single-purpose
- ❌ **Magic Numbers**: Use named constants or enum classes
- ❌ **Dead Code**: Remove unused code and imports
- ❌ **God Classes**: Split large classes following Single Responsibility Principle

**General Smells**
- Code duplication (DRY principle)
- Long parameter lists (use data classes)
- Nested when/if statements (extract functions)
- Feature envy (methods operating on other class data)
- Data classes misuse (only for simple data holders)

### Clean Code Practices

**Must Follow**
- ✅ Prefer `val` over `var` - immutability by default
- ✅ Small functions that do one thing well
- ✅ Meaningful, self-documenting names
- ✅ Embrace Kotlin's null safety features
- ✅ Use sealed classes for restricted hierarchies
- ✅ Use data classes for DTOs and value objects
- ✅ Extension functions for enhancing existing classes
- ✅ Coroutines for async (avoid callbacks)
- ✅ Pure functions where possible (no side effects)
- ✅ Comments only for "why", not "what"

## Dependencies

- Add new dependencies to `/gradle/libs.versions.toml`, never hardcode versions
- Prefer Compose Multiplatform libraries over Android-only alternatives
- Use Ktor Client for networking (multiplatform compatible)
- Use Kotlinx Serialization for JSON (not Gson/Moshi)
- Use Kotlinx Coroutines for async work
- Use Koin or Kotlin Inject for DI (multiplatform compatible)
- Prefer KSP over KAPT for annotation processing (faster, KMP compatible)
- **Regularly update dependencies to patch security vulnerabilities**

### Recommended KMP Libraries
- **kotlinx.coroutines**: Async programming
- **kotlinx.serialization**: JSON handling
- **Ktor Client**: HTTP networking
- **SQLDelight**: Type-safe SQL persistence
- **Koin**: Dependency injection
- **Napier/Kermit**: Multiplatform logging

## Security

### OWASP Mobile Top 10 Compliance

**Data Security (M2: Insecure Data Storage)**
- Use **AES-256** encryption for sensitive data at rest
- Never use `MODE_WORLD_READABLE` or `MODE_WORLD_WRITEABLE`
- No plaintext storage of credentials, tokens, or PII

**Cryptography (M5: Insufficient Cryptography)**
- Password hashing: bcrypt, PBKDF2, Argon2, or scrypt only
- Use platform keystores for key management
- Never hardcode encryption/decryption keys

**Network Security (M3: Insecure Communication)**
- **HTTPS only** for all network communication
- TLS 1.2+ minimum
- Certificate pinning for critical APIs
- Validate SSL certificates properly

**Input Validation (M4: Insecure Authentication)**
- Sanitize all user inputs and API responses
- Prevent SQL injection with parameterized queries
- Prevent XSS with proper output encoding
- Implement rate limiting on server endpoints

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
- Disable debug features in release builds
- Set `android:exported="false"` for internal components

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
- Enable CORS properly; don't use wildcard origins in production
- Implement authentication and authorization for protected endpoints
- Log security events (failed auth, suspicious requests)

## Testing

- Write tests in `commonTest` when possible for cross-platform coverage
- Use `kotlin.test` assertions, not JUnit-specific ones
- Name test functions descriptively: `fun \`should return error when input is empty\`()`
- Test ViewModels by testing state changes
- Use Ktor's `testApplication` for server endpoint tests
- Include security tests: input validation, authentication flows, data encryption
- **Aim for >80% coverage in shared business logic**
- Mock external dependencies with MockK
- Test edge cases and error paths

### Static Analysis
- Run **Detekt** for code smell detection
- Configure ktlint for consistent formatting
- Use SonarQube for security and quality scanning
- Enable Gradle's dependency vulnerability scanning

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
- Lazy initialization for expensive operations
- Use StateFlow/SharedFlow efficiently
- Profile for bottlenecks before optimizing

## Error Handling

- Use `Result<T>` or sealed classes for operations that can fail
- Never swallow exceptions silently; log or propagate them
- Use `runCatching` for wrapping throwable code
- Define domain-specific exceptions in `shared/commonMain`
- Show user-friendly error messages in UI, log technical details
- Implement crash reporting (Crashlytics, Sentry) for production builds
- Sanitize error messages: never expose stack traces or internal details to users
- Handle network timeouts and connectivity issues gracefully
- Provide retry mechanisms for transient failures

## Response Guidelines

When providing solutions:
1. **Explain the approach** before showing code
2. **Highlight security implications** when relevant
3. **Point out code smells** and suggest improvements
4. **Recommend appropriate patterns** (MVVM/Clean Architecture)
5. **Include error handling** and edge cases
6. **Add meaningful comments** for complex logic
7. **Consider multiplatform** implications in every solution
8. **Suggest tests** for the implementation
9. **Follow project conventions** and existing patterns
10. **Prioritize maintainability** over cleverness

## Best Practices Checklist

Before submitting code, verify:
- [ ] No hardcoded secrets or sensitive data
- [ ] Proper null safety (no unnecessary `!!`)
- [ ] Small, focused functions following SRP
- [ ] Appropriate error handling with meaningful messages
- [ ] Security measures for sensitive operations
- [ ] Input validation and sanitization
- [ ] Tests written for business logic
- [ ] No code smells (checked with Detekt)
- [ ] Dependencies up to date
- [ ] HTTPS for all network calls
- [ ] Proper use of `val` over `var`
- [ ] Code is self-documenting with clear names
- [ ] Follows existing project architecture patterns

---

**Remember: Write secure, clean, maintainable code that your team will thank you for.**