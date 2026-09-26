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

## Status Bar

Phase 1 Foundation — Navigation shell and theme created. Financial data models will be added in Phase 2.
