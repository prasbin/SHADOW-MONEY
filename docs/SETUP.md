# SHADOW MONEY — Setup Instructions

## Requirements

- Android Studio (Hedgehog or later)
- Android SDK 34, 36, or 36.1
- Build Tools 34.0.0, 35.0.0, 36.0.0, or 36.1.0
- Java 17+ (for compilation); Gradle JVM can use Java 27
- Android Emulator or physical device
- Minimum 10 GB free disk space

## Android SDK Path

```
C:\Users\User\AppData\Local\Android\Sdk
```

## Build Instructions

### Gradle Wrapper

```bash
gradlew assembleDebug
gradlew test
gradlew lint
```

### Using Android Studio

1. Open `E:\Desktop\AGENTS-UP v2.0\SHADOW MONEY`
2. Select "Open an existing Android Studio project"
3. Gradle will sync automatically
4. Build the app using the Build menu

### JDK Configuration

The project uses Java 17 for compilation (`compileOptions` and `kotlinOptions`).
Gradle can run on Java 27 (the bundled JRE on this machine).

## Dependency Repository

Google Maven mirror is configured for faster dependency resolution:
```
https://maven-central.storage-download.googleapis.com/maven2
```

## Application ID

`com.prasbin.shadowmoney`

## Testing

```bash
gradlew test
```

Tests use Robolectric 4.14.1 and JUnit 4.13.2.

## Current Status

**Phase 1 — Android Foundation**
- Application shell created
- Dark futuristic theme implemented
- Navigation skeleton established
- Room database foundation ready
- Financial data models pending (Phase 2)

## Roadmap

- Phase 2: Financial Data Model (accounts, transactions, categories, goals)
- Phase 3: Dashboard
- Phase 4: Transaction Intelligence
- Phase 5: Budgets
- Phase 6: Work/Income/Project Tracker
- Phase 7: Goals + Secret Target
- Phase 8: Telecom Tracker
- Phase 9: Opportunity Intelligence
- Phase 10: CSV Import
- Phase 11: Local Financial Assistant
- Phase 12: Security/Backup/Restore
- Phase 13: Testing/Release
