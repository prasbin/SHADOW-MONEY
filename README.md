# SHADOW MONEY

Personal Android financial-management and income-growth application.

## Status

**Phase 1 — Android Foundation**

The application shell has been created. Core financial features are planned for subsequent phases.

## Application ID

`com.prasbin.shadowmoney`

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3 (Dark Theme)
- Room / SQLite
- DataStore (preferences)
- WorkManager (scheduled for Phase 3+)
- Android Storage Access Framework (for later file import)

## Navigation

- Dashboard
- Transactions
- Money
- Jobs
- Work
- Opportunities
- Settings

## Financial Objective

Help the user work toward consistently earning at least **NPR 100,000/month**.

## Principles

- Local-first
- Financial truth (no fabricated data)
- Privacy and security
- Exact integer monetary representation (Long/paisa)
- Dark futuristic "System" aesthetic

## Roadmap

See [docs/ROADMAP.md](docs/ROADMAP.md) for the full feature roadmap.

## Build

```bash
gradlew assembleDebug
gradlew test
gradlew lint
```

### Testing Status

**Phase 1 Tests: 4/4 PASSING**
- `packageName_isCorrect`
- `database_isConstructible`
- `dao_isInterface`
- `versionCode_isOne`

## GitHub Recovery

If the local project is deleted or lost:

1. Clone the official repository: `git clone https://github.com/prasbin/SHADOW-MONEY.git`
2. Open the project in Android Studio
3. Configure the local development environment (Android SDK, JDK)
4. Keep signing credentials/keystore **outside** Git (never commit them)
5. Continue from the latest verified phase

## Status Bar

Phase 1 Foundation — Navigation shell and theme created. Financial data models will be added in Phase 2.
