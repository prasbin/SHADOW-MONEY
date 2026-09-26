# SHADOW MONEY

Personal Android financial-management and income-growth application.

## Status

**Phase 2 — Financial Data Model** ✅ COMPLETE

The foundational financial data model has been implemented. The database now contains:
- `accounts` — user financial accounts/wallets
- `categories` — income/outflow classification
- `transactions` — financial events with exact Long minor units
- `goals` — financial goal tracking

## Application ID

`com.prasbin.shadowmoney`

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3 (Dark Theme)
- Room / SQLite (v2)
- DataStore (preferences)
- WorkManager
- Android Storage Access Framework
- Long minor-unit monetary representation (100 paisa = 1 NPR)

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

## Database Schema (Phase 2)

| Table | Purpose |
|-------|---------|
| accounts | User accounts/wallets with opening balance |
| categories | Income/outflow classification |
| transactions | Financial events with direction |
| goals | Financial goal tracking |

**Financial Truth**: Account balance is derived as `openingBalance + income - outflow` using Long arithmetic.

## Migration

Phase 1 → Phase 2 migration (`MIGRATION_1_2`) drops the `placeholder` table and creates all four entity tables. Tested and verified.

## Testing Status

**Phase 2 Tests: 17 tests PASSING**
- Money arithmetic (5 tests)
- Account database (5 tests)
- Category database (5 tests)
- Transaction database (5 tests)
- Goal database (3 tests)
- Migration (4 tests)

## GitHub Recovery

If the local project is deleted or lost:

1. Clone the official repository: `git clone https://github.com/prasbin/SHADOW-MONEY.git`
2. Open the project in Android Studio
3. Configure the local development environment (Android SDK, JDK)
4. Keep signing credentials/keystore **outside** Git (never commit them)
5. Continue from the latest verified phase

## Roadmap

See [docs/ROADMAP.md](docs/ROADMAP.md) for the full feature roadmap.
