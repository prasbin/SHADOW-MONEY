# SHADOW MONEY

Personal Android financial-management and income-growth application.

## Status

**Phase 4 — Transaction Intelligence** ✅ COMPLETE

Deterministic, offline financial intelligence on top of the Phase 2/3 records:
- 400-day analysis window with explicit boundaries
- Period calculations (income, outflow, net, previous-period comparison, trends)
- Rule-based recurring-outflow detection (ANALYSIS)
- Unusual-spending detection vs historical baseline (ANALYSIS)
- Monthly outflow projection with stated assumptions (PROJECTION)
- Every insight classified FACT / CALCULATION / ANALYSIS / PROJECTION
- Intelligence section added to the Dashboard; trust label retained

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

Phase 1 v1 contained only the structural `placeholder` table (no user financial data). Phase 1 → Phase 2 migration (`MIGRATION_1_2`) drops `placeholder` and creates all four entity tables with safe FK actions (`transactions.accountId` RESTRICT, `transactions.categoryId` SET NULL, `goals.accountId` SET NULL; no CASCADE). Verified by genuine v1 → v2 migration tests.

## Testing Status

**Phase 4 Tests: 93 tests PASSING**
- Money arithmetic (5 tests)
- Account database (5 tests)
- Category database (5 tests)
- Transaction database (5 tests)
- Goal database (3 tests)
- Migration (7 tests)
- Delete/archive semantics (8 tests)
- Dashboard ViewModel (10 tests)
- Period calculator (8 tests)
- Recurring detector (9 tests)
- Unusual detector (8 tests)
- Projection engine (5 tests)
- Intelligence engine (7 tests)
- Intelligence repository wiring (4 tests)
- App (4 tests)

## GitHub Recovery

If the local project is deleted or lost:

1. Clone the official repository: `git clone https://github.com/prasbin/SHADOW-MONEY.git`
2. Open the project in Android Studio
3. Configure the local development environment (Android SDK, JDK)
4. Keep signing credentials/keystore **outside** Git (never commit them)
5. Continue from the latest verified phase

## Roadmap

See [docs/ROADMAP.md](docs/ROADMAP.md) for the full feature roadmap.
