# SHADOW MONEY

Personal Android financial-management and income-growth application.

## Status

**Real Money Connection Foundation** ✅ COMPLETE (provider research + honest connection architecture — no fake connectors, no credentials, app stays offline; **Room schema change: v7 → v8**; version stays `1.0.0` / versionCode 2; full write-up in `docs/REAL_MONEY_CONNECTIONS.md`):
- **Provider research (official sources)**: Sanima Sajilo eBanking → `NOT AVAILABLE THROUGH OFFICIAL PUBLIC CONSUMER API` (customer portals/apps only); Global IME Global Smart Plus → `NOT AVAILABLE THROUGH OFFICIAL PUBLIC CONSUMER API` (bank-owned customer app only); eSewa → `OFFICIAL API EXISTS — MERCHANT PAYMENT CATEGORY; CONSUMER WALLET DATA SYNC NOT AVAILABLE` (`developer.esewa.com.np` is merchant/partner-only)
- **Connection domain**: `ProviderAdapter` read-only interface with **zero production implementations** (empty adapter map by design), research-backed `ProviderCatalog`, connection/baseline Room persistence, sync coordinator with staleness downgrade, unified actual-money view (connected-verified sources only — manual/imported records stay separately provenanced and are never folded in), baseline establishment that refuses without fresh connected sources, and a pure discrepancy state machine (`NO_CONNECTED_SOURCES` → … → `ACTUAL_DISCREPANCY`)
- **Room v8**: additive `MIGRATION_7_8` creates only `financial_connections` + `balance_baselines` (KSP schema `8.json` committed); backup `APP_SCHEMA_VERSION` follows to 8; all six existing migration tests chain through it
- **System → Connections screen**: honest provider cards (UNAVAILABLE reasons, capability/approval rows), ACTUAL MONEY / BASELINE / DISCREPANCY / PROVENANCE panels with real refusal messages, security footer; transaction date lines now carry `· MANUAL ENTRY` / `· IMPORTED` provenance labels
- Verification: **759 tests / 72 suites / 0 failures / 0 errors** (55 new); lint **0 errors / 27 warnings**; `assembleDebug` + `assembleRelease` OK (apksigner v2, CN=Prasbin Dhungana); verified on Android 16 emulator (physical device disconnected this session); also fixed a month-rollover time bomb in `BudgetsViewModelTest` (hardcoded `2026-09` month key → `BudgetCalendar.currentMonthKey()`)

**v1.1 Engineering Hardening** ✅ COMPLETE (an engineering pass, not a phase — Phases 0–13 remain the complete list, no Phase 14; **no schema change, still Room v7**; version stays `1.0.0` / versionCode 2):
- **Room schema artifacts are now version-controlled**: `app/app/schemas/…ShadowMoneyDatabase/` holds genuine KSP-generated JSONs for **versions 1–7** (v2–v7 exported from their real version-bump commits via disposable git worktrees; v1 generated from its Phase-1 commit with `exportSchema` temporarily enabled in the worktree — historically `false`, entities untouched); `.gitignore` no longer excludes `app/app/`, so any future entity change produces a visible schema diff for review/CI
- **Legacy Jobs cleanup**: the dead `JobsScreen`, `Screen.Jobs`, and its NavHost registration were removed (zero navigators, tests, or dependencies ever referenced them); the Work screen is untouched
- **Transaction query bounding**: `TransactionDao.getAll()` documented as a deliberate full-dataset operation — production full-scan is only CSV-import duplicate fingerprinting; Dashboard (recent 20), Transactions (recent 100), intelligence/assistant (time windows + SQL aggregates) and backup (dedicated `BackupDao`) all stay bounded
- **Backup encryption**: architecture review only — documented as future v1.x/v2 work, **not implemented** in this pass
- Verification: **691 tests / 66 suites / 0 failures / 0 errors**; lint **0 errors / 27 warnings**; `assembleDebug` + `assembleRelease` OK; release identity unchanged (`com.prasbin.shadowmoney`, v1.0.0, versionCode 2, signed v2, CN=Prasbin Dhungana)

**v1.0 Release Candidate Audit** ✅ COMPLETE (an audit pass, not a new phase; Phases 0–13 remain the complete list):
- Read-only audit of product, financial integrity, security, database, UI/UX, release config, performance, tests and documentation against baseline `7651f07`; one release blocker found and fixed: `MoneyViewModel` and `TransactionsViewModel` created private `CoroutineScope`s without overriding `onCleared`, leaking Room Flow collectors after navigation — both now cancel their scope like every other ViewModel, each guarded by a `ViewModelStore.clear()` regression test
- Release APK (`com.prasbin.shadowmoney`, v1.0.0) installed and exercised on a **physical Android 16 device (24094RAD4G)** — first install, first-launch empty state, Goals/Secret-Target empty state, crash-free; full route-walk of the release APK completed on the Android 16 emulator (all 8 bottom-nav screens, Transactions, Assistant, Add Account dialog open/cancel, Settings `SHADOW MONEY v1.0.0`, zero logcat crashes)
- Verification: **691 tests / 66 suites / 0 failures / 0 errors** (2 new scope-cancellation regressions); lint **0 errors / 27 warnings**; `assembleDebug` + `assembleRelease` OK; `apksigner verify` → Verifies, v2 scheme, CN=Prasbin Dhungana

**Post-Phase 13 Hardening — v1.0 Readiness** ✅ COMPLETE (a hardening pass, not a new phase; Phases 0–13 remain the complete list):
- Version set to `versionName = "1.0.0"` (`versionCode` stays 2); the Settings label keeps rendering `BuildConfig.VERSION_NAME`
- Manual entry implemented: the Transactions screen now has a working Add Transaction dialog (exact Long minor-unit amounts, Kathmandu-ISO dates, active account/category/work-item dropdowns, direction validation, `source = ""` ordinary rows), and the Money screen manages accounts (create/edit/archive with exact opening balances) — the old "Not Implemented Yet" Money placeholder screen was removed
- Assistant session hardening: busy submits rejected without duplicating the session, clear-while-busy drops the stale late reply, input clears only on accepted sends (regression-tested with a deterministic test dispatcher)
- Security/release re-review: no backup-content logging, Secret Target still absent from exports, signing secrets external-only (`.gitignore` also covers `signing.properties`), no network/analytics dependencies; backup **encryption at rest remains a documented future hardening item** (plain JSON by design)
- UI notes (documented, not silently changed): 8-item bottom navigation is dense but works; the Jobs bar item is an intentionally unwired legacy route superseded by Work
- Verification: **689 tests / 66 suites / 0 failures / 0 errors** (33 new); lint **0 errors / 27 warnings** (+1 = newer-version advisory on the added `kotlinx-coroutines-test` test dependency); `assembleDebug` + `assembleRelease` OK; `apksigner verify` → Verifies, v2 scheme, CN=Prasbin Dhungana

**Phase 13 — Real Device Testing / Release Verification** ✅ COMPLETE

Final verification phase: everything below was exercised end-to-end on a real Android runtime (Android 16 / API 36 emulator, ADB-driven UI exploration with `uiautomator dump`), verified blockers fixed, release build signed and signature-verified (no new features, no schema change, still Room v7):
- Environment: Android 16 (API 36, x86_64) emulator device — **no physical device was available** (honest limitation, documented); app package `com.prasbin.shadowmoney.debug` installed, cold/warm launched and exercised entirely on-device
- Verified-blocker fixes (all pre-existing, found by device testing): dead Money and Settings bottom-bar items were wired to their routes; the Money placeholder gained a "View Transactions" route to the otherwise-unreachable Transactions hub; the Settings version label now reads `BuildConfig.VERSION_NAME` instead of a hardcoded string
- Navigation: all 13 registered routes reachable and alive (Dashboard, Goals, Budgets, Work, Telecom, Opps, Money, Transactions, Import, Assistant, Settings, Backup & Restore)
- Financial core on-device: dashboard balance/income/outflow/category math exact against the synthetic fixture (NPR 1,260.00 / 800.00 / 540.00), goal progress account-linked, budget states including OVER_BUDGET with the "not additional money" note, work Expected/Received/Remaining correct, telecom and opportunity amounts never entering financial totals
- Intelligence: FACT / CALCULATION / ANALYSIS / PROJECTION labels correct, empty state ("No financial records yet") observed, projections carry disclaimers, no fabricated insights
- CSV import on-device: read-only preview banner → Discard leaves counts untouched (zero-write proven) → confirm imports exactly 2 rows → duplicate rows reported against existing records, never auto-merged → malformed CSV rejected with per-row validation errors
- Local assistant on-device: 14/14 topics answered with correct labels (balance, income, spending, budget, goals, work, telecom, opportunities, transactions, import, help, unsupported→bounded refusal, ambiguous→clarification, **Secret Target→fixed refusal**); settings counts unchanged after all sessions (read-only proven); session history cleared by process death as designed
- Backup/restore on-device: deterministic export with checksum, full restore with the verbatim destructive warning and exact counts, tampered file rejected ("The backup checksum does not match; the file was modified or damaged") with existing data untouched
- Secret Target on-device: set → masked bullets by default → reveal → edit (pre-filled) → clear (confirmation dialog), value absent from Dashboard, assistant and the exported JSON (re-scanned while the value was set), survives force-stop/relaunch
- Persistence: force-stop + relaunch preserves all 19 records and every screen's data; assistant conversation does not persist (session-only, by design)
- Release: signed `app-release.apk` (external keystore + properties file outside the repo, never logged/committed), `apksigner verify` → Verifies, v2 scheme true, signer CN=Prasbin Dhungana, versionCode 2 / versionName 1.0.0
- Permissions: 4 install-time library-merged permissions (FOREGROUND_SERVICE, RECEIVE_BOOT_COMPLETED, ACCESS_NETWORK_STATE, WAKE_LOCK), 0 runtime permissions, no INTERNET, 0 network APIs in source and 0 network dependencies; logcat from the app PID shows no network activity
- Security: tracked-file scan clean (no keystore/APK/backup/CSV artifacts), diff reviewed, `.gitignore` covers `*.apk`/`*.jks`/`*.keystore`
- Tests: **656 passing / 63 suites / 0 failures / 0 errors**; lint **0 errors, 26 warnings** (unchanged baseline)

Previous phase: **Phase 12 — Backup / Restore / Export / Import** ✅ COMPLETE

Local-first full backup with integrity verification and atomic restore (no cloud, no network, no auto-backup):
- Backup scope: accounts, categories, transactions, budgets, goals, work items, telecom SIMs/packages/subscriptions, opportunities — IDs, relationships, exact Long minor units, timestamps, archived states, `source`, `externalRef` and budget month keys are preserved
- Deterministic versioned JSON (`shadow-money-backup` format v1, `appSchemaVersion` 7); identical data always produces byte-identical output; amounts are integer minor units only (never floats)
- SHA-256 checksum over the canonical `payload` bytes (sorted keys, no whitespace) — a corruption/tamper detector, **not** encryption; malformed JSON, unsupported format, incompatible schema and checksum mismatches are rejected before any restore
- Restore pipeline: read → strict JSON parse → format → schema version → checksum → record validation (positive unique IDs, enum ranges, YYYY-MM month keys, referential integrity, duplicate budget slots — rejected as a whole, never repaired) → read-only preview with record counts → mandatory destructive warning ("Restoring this backup will replace the current SHADOW MONEY financial records.") → explicit confirmation → single-transaction full replacement (a failed transaction rolls back to the exact prior state)
- Existing-data policy: full replacement (no merge, no duplicate rows); CSV import remains a separate additive flow with duplicate review
- Export uses SAF `CreateDocument`, restore uses SAF `OpenDocument` — 10 MB file bound, zero manifest permissions, no filesystem scanning
- Secret Target: never present in backup JSON, checksum input, previews, logs or restore files, and restore never modifies it
- Encryption status: backups are plain JSON files; the checksum verifies integrity and provides no confidentiality — the user must protect the exported file
- Settings gains a Backup & Restore section: export, restore, live backup info (format/schema/checksum/record counts) and the preview/confirmation dialogs

Previous phase: **Phase 11 — Local Financial Assistant** ✅ COMPLETE

A deterministic, offline, read-only assistant that answers bounded questions about the user's own recorded financial data (not a general-purpose AI assistant):
- Intents: BALANCE / INCOME / OUTFLOW / BUDGET / GOALS / WORK / TELECOM / OPPORTUNITIES / TRANSACTIONS / IMPORT / HELP plus SECRET_TARGET_REFUSAL, AMBIGUOUS, CLARIFY_PERIOD, UNSUPPORTED — anything outside the bounded set gets an honest refusal/clarification, never a guess
- Deterministic keyword/phrase classification (no model, no randomness, no network); ambiguous ties and unknown questions are never guessed
- Periods: today / yesterday / this week / last week / this month / last month / recent — all computed in Asia/Kathmandu (ISO weeks start Monday, documented); future and unsupported periods get clarification, never fabricated data
- Responses reuse the existing `InsightKind` labels FACT / CALCULATION / ANALYSIS / PROJECTION with a `SOURCE: Local financial records.` line; projections are explicitly prefixed and marked as not guarantees
- Read-only architecture: `AssistantRepository` composes existing repositories only — no write methods, **no database schema change** (still v7)
- Secret Target boundary: assistant code has no `SecretTargetStore` dependency; secret-target questions get a fixed refusal and the value never appears in any response
- Session-local bounded conversation history (40 messages, never persisted); dashboard "Ask" entry + dedicated screen

Previous phase: **Phase 10 — Legitimate Financial Import** ✅ COMPLETE

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
- Room / SQLite (v8)
- DataStore (preferences)
- WorkManager
- Android Storage Access Framework
- Long minor-unit monetary representation (100 paisa = 1 NPR)

## Navigation

- Dashboard
- Financial Assistant
- Transactions
- Money
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

**Phase 13 / Phase 12 Tests: 656 tests PASSING** (Phase 13 added no new tests — it verified the existing 656 on-device; unit suite, lint and release build all re-run after the Phase 13 fixes with identical results)
- Backup JSON parser/writer (strict integers, duplicate keys, escapes, depth, canonical output) (14 tests)
- Backup serialization (full roundtrip, exact Longs, nulls/externalRef, archived states, determinism, envelope structure, rejection codes) (16 tests)
- Backup checksum (SHA-256 vectors, tampering, canonicalization, reordering, algorithm) (13 tests)
- Backup validation (dangling refs, duplicate/non-positive IDs, enum ranges, month keys, budget slots) (24 tests)
- Backup restore end-to-end (full replacement, ID/relationship preservation, atomic rollback, preview-no-writes, empty backup, idempotence, no parallel ledger) (16 tests)
- Backup Secret Target exclusion (JSON/checksum/restore invariance, no model field) (5 tests)
- Backup file I/O (bounded reader limits, fake SAF layer roundtrip) (7 tests)
- Backup security boundary (no permissions/network/logging/credentials, SAF-only file access, limits, ignore rules) (9 tests)
- Backup ViewModel (export/preview/confirm state machine, errors, live info) (13 tests)

**Previous total: Phase 11 Tests: 539 tests PASSING**
- Intent classifier (intents, periods, priorities, ambiguity, determinism) (23 tests)
- Assistant time ranges (Kathmandu, ISO weeks, budget months) (12 tests)
- Assistant engine (responses, labels, limits, Secret Target refusal) (34 tests)
- Assistant repository (period aggregates, read-only, determinism) (16 tests)
- Assistant privacy boundary (no Secret Target dependency/leakage) (9 tests)
- Assistant ViewModel (session, errors, bounded history) (11 tests)
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
