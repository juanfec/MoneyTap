# MoneyTap

A **Kotlin Multiplatform** application for managing SMS messages and financial transactions. Built with Compose Multiplatform for a shared UI across Android, iOS, and Desktop platforms.

## Features

- SMS Inbox reading and display (Android)
- Cross-platform UI with Compose Multiplatform
- MVVM architecture with clean separation of concerns
- Type-safe navigation with Kotlin Serialization
- Dependency injection with Koin

## Tech Stack

| Technology | Version |
|------------|---------|
| Kotlin | 2.3.0 |
| Compose Multiplatform | 1.10.0 |
| Navigation Compose | 2.9.1 |
| Lifecycle ViewModel | 2.9.6 |
| Koin | 4.0.0 |
| Ktor (Server) | 3.3.3 |
| Android minSdk | 24 |
| Android targetSdk | 36 |

## Project Structure

```
MoneyTap/
├── composeApp/              # Compose Multiplatform UI application
│   └── src/
│       ├── commonMain/      # Shared UI, ViewModels, Navigation
│       ├── androidMain/     # Android entry point (MainActivity)
│       ├── iosMain/         # iOS Compose controller
│       └── jvmMain/         # Desktop entry point
├── shared/                  # Common business logic library
│   └── src/
│       ├── commonMain/      # Domain models, repositories, use cases
│       ├── androidMain/     # Android-specific implementations
│       └── iosMain/         # iOS-specific implementations
├── server/                  # Ktor HTTP server (port 8080)
└── iosApp/                  # Native SwiftUI wrapper for iOS
```

## Getting Started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (latest stable)
- [JDK 11+](https://adoptium.net/)
- [Xcode](https://developer.apple.com/xcode/) (for iOS development, macOS only)

### Local Configuration

Before building, you need to create two local configuration files that are not tracked by git:

#### 1. `local.properties`

Create `local.properties` in the project root with your Android SDK path:

```properties
# Windows
sdk.dir=C:\\Users\\<USERNAME>\\AppData\\Local\\Android\\Sdk

# macOS
sdk.dir=/Users/<USERNAME>/Library/Android/sdk

# Linux
sdk.dir=/home/<USERNAME>/Android/Sdk
```

#### 2. `CLAUDE.local.md` (Optional - for Claude Code users)

If you use [Claude Code](https://claude.ai/claude-code), create `CLAUDE.local.md` with your machine-specific build commands:

```markdown
# Local Development Settings

## Project Path
<YOUR_PROJECT_PATH>

## Build Commands (Windows with cmd.exe)
cmd.exe /c "cd /d <YOUR_PROJECT_PATH> && .\gradlew.bat :composeApp:assembleDebug --no-daemon"
```

See `CLAUDE.md` for project guidelines and coding standards.

## Build Commands

### Android

```bash
# macOS/Linux
./gradlew :composeApp:assembleDebug

# Windows (PowerShell)
.\gradlew.bat :composeApp:assembleDebug

# Windows (from any shell via cmd.exe)
cmd.exe /c "cd /d <PROJECT_PATH> && .\gradlew.bat :composeApp:assembleDebug"
```

### Desktop (JVM)

```bash
# macOS/Linux
./gradlew :composeApp:run

# Windows
.\gradlew.bat :composeApp:run
```

### Server

```bash
# macOS/Linux
./gradlew :server:run

# Windows
.\gradlew.bat :server:run
```

### iOS

Open the `iosApp/` directory in Xcode and run from there.

## Testing

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :composeApp:test
./gradlew :shared:test
./gradlew :server:test
```

## Architecture

The project follows **MVVM** with **Clean Architecture** principles:

- **Presentation Layer** (`composeApp/commonMain`): Compose UI, ViewModels, Navigation
- **Domain Layer** (`shared/commonMain`): Use cases, domain models, repository interfaces
- **Data Layer** (`shared/<platform>Main`): Repository implementations, data sources

### Key Patterns

- `expect`/`actual` for platform-specific implementations
- `StateFlow`/`SharedFlow` for reactive state management
- Type-safe navigation with `@Serializable` routes
- Dependency injection with Koin modules

## Permissions

### Android

The app requires the following permissions (declared in `AndroidManifest.xml`):

- `READ_SMS` - To read SMS messages from the inbox

## Contributing

Please read `CLAUDE.md` for coding standards, security guidelines, and best practices before contributing.

## License

[Add your license here]
