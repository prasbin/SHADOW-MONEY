# SHADOW MONEY

Personal Android financial-management and income-growth application.

## Status

**Phase 8 — Telecom Tracker** ✅ COMPLETE

A practical local-first telecom tracker (tracking only — never a telecom-control system):
- SIM cards, packages/plans, and subscriptions (Room v5, migration `MIGRATION_4_5`)
- Expected monthly telecom cost via exact Long normalization (weekly ×52÷12, quarterly ÷3, yearly ÷12)
- Upcoming renewals (60-day horizon, max 5, deterministic ordering)
- Summary: active SIMs, active subscriptions, expected monthly cost, next renewal
- Strict financial separation: telecom data never creates transactions or alters financial truth
- No device/SIM access, no carrier APIs, no SMS/phone permissions — all data manually entered

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

**Phase 8 Tests: 266 tests PASSING**
- Money arithmetic (5 tests)
- Account database (5 tests)
- Category database (5 tests)
- Transaction database (5 tests)
- Goal database (3 tests)
- Migration (7 tests)
- Delete/archive semantics (8 tests)
- Dashboard ViewModel (10 tests)
- Intelligence repository wiring (4 tests)
- Period calculator (8 tests)
- Recurring detector (9 tests)
- Unusual detector (8 tests)
- Projection engine (5 tests)
- Intelligence engine (7 tests)
- Budget calendar / Kathmandu timezone (8 tests)
- Budget math / status thresholds (11 tests)
- Budget repository CRUD + spending (18 tests)
- Budget v2→v3 migration (2 tests)
- Budgets ViewModel (9 tests)
- Work math / deadlines (8 tests)
- Work repository CRUD + linking (17 tests)
- Work v3→v4 migration (3 tests)
- Financial integrity (8 tests)
- Work ViewModel (8 tests)
- Goal repository CRUD + progress (11 tests)
- Goals ViewModel (7 tests)
- Secret Target store (9 tests)
- Secret Target privacy boundary (7 tests)
- Telecom math (10 tests)
- Telecom renewals (9 tests)
- Telecom repository CRUD + summary (18 tests)
- Telecom v4→v5 migration (3 tests)
- Telecom financial separation (7 tests)
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
