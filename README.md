# SHADOW MONEY

Personal Android financial-management and income-growth application.

## Status

**Phase 10 — Legitimate Financial Import** ✅ COMPLETE

A safe, explicit CSV import flow that turns file/paste CSV data into ordinary transactions (no scraping, no auth bypass, manual file selection only):
- Import flow: choose source → parse → read-only preview → validation/duplicate review → explicit confirmation → normal transactions
- CSV schema: required `date, description, amount, direction, account`; optional `category, external_ref` with documented header aliases
- Exact Long minor-unit amounts (never floats), ≤2 decimals, explicit Kathmandu-local date formats only
- Row states NEW / POSSIBLE_DUPLICATE / INVALID with default selections (duplicates never auto-imported)
- Atomic final write in one Room transaction (`transactions.source = IMPORT_FILE`, `externalRef` preserved); failed batch = nothing written
- Storage Access Framework only (`OpenDocument`), 5 MB input bound, no storage/network permissions, no logging of CSV contents
- Room schema v7: new `transactions.externalRef` column via additive `MIGRATION_6_7` (v6 → v7)

Previous phase: **Phase 9 — Opportunity Intelligence** ✅ COMPLETE

A practical, local-first opportunity tracking and organization system (manual tracking only — never automated job acquisition):
- Opportunities with bounded types (Freelance / Client Work / Part Time / Remote Work / Project / Repository / Other) and explicit lifecycle statuses (New / Reviewing / Applied / In Progress / Won / Lost / Archived)
- Expected opportunity amounts (Long minor units, nullable) — explicitly NOT income
- Manual source/URL references with offline GitHub reference parsing (no fetching, no scraping)
- Status/type filters + local text search; detail view with "Open reference" user action
- Strict financial separation: opportunities never create transactions, never affect balances/income/budgets
- No predictions, scoring, probability, ranking, or AI; deterministic factual summaries only

## Application ID

`com.prasbin.shadowmoney`

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3 (Dark Theme)
- Room / SQLite (v7)
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

Phase 9 → Phase 10 (`MIGRATION_6_7`) is purely additive: `ALTER TABLE transactions ADD COLUMN externalRef TEXT DEFAULT NULL`. Existing rows keep their data with a NULL reference; no destructive change, no table rebuild. Verified by a genuine populated v6 → v7 migration test.

## Testing Status

**Phase 10 Tests: 434 tests PASSING**
- CSV parser (RFC4180 quoting/blank lines/BOM) (18 tests)
- CSV schema mapping / aliases / ambiguity (11 tests)
- Import amount rules (Long exact, grouping, decimals, direction) (17 tests)
- Import date rules (Kathmandu-local formats, rejections) (13 tests)
- Import engine (row states, duplicates, mapping, reasons) (31 tests)
- Import repository (no-write preview, atomic import, FK rollback) (13 tests)
- Import security boundary (no permissions, no network/logging/credentials, 5 MB bound) (7 tests)
- Import v6→v7 migration (populated data preserved, externalRef added) (3 tests)
- Import transactions ViewModel (state machine, selection, confirmation) (12 tests)
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
- Opportunity math / deadlines / URL parsing (11 tests)
- Opportunity repository CRUD + filters + search (15 tests)
- Opportunity v5→v6 migration (2 tests)
- Opportunity financial separation (7 tests)
- Opportunities ViewModel (8 tests)
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
