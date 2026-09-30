# SHADOW MONEY — Roadmap

## Current Status: v1.0 Release Candidate Audit ✅ COMPLETE

An audit pass (not a new phase — Phases 0–13 remain the complete list, no Phase 14; no architecture/financial-calculation changes, **no schema change — still Room v7**), baseline `7651f07`:
- Full audit across product / financial integrity / security / database / UI / release / performance / tests / docs; one release blocker found and fixed: `MoneyViewModel` and `TransactionsViewModel` lacked `onCleared()` scope cancellation (leaked Room Flow collectors) — fixed with `ViewModelStore.clear()` regression tests
- Release APK installed and exercised on a **physical Android 16 device (24094RAD4G)** (first install, first-launch empty state, Goals empty state, crash-free) plus a full release-APK route-walk on the Android 16 emulator (all empty states, Add Account dialog, Transactions, Assistant, Settings `v1.0.0`, zero logcat crashes)
- Verification: **691 tests / 66 suites / 0 failures / 0 errors** (+2 scope-cancellation tests); lint **0 errors / 27 warnings**; `assembleDebug`/`assembleRelease` OK; `apksigner verify` → Verifies, v2, CN=Prasbin Dhungana; docs updated, work committed and pushed

Previous status: Post-Phase 13 Hardening — v1.0 Readiness ✅ COMPLETE

A verification-and-hardening pass (not a new phase — Phases 0–13 remain the complete list, no Phase 14; no architecture/financial-calculation changes, **no schema change — still Room v7**):
- `versionName = "1.0.0"` set (was `0.1.0-phase2`); `versionCode` stays 2; Settings label renders `BuildConfig.VERSION_NAME`
- Manual entry implemented end-to-end: Transactions `Add Transaction` dialog (exact Long amounts via `ImportAmount`, Kathmandu dates via `ImportDate`, active account/category/work dropdowns, direction validation, `source = ""` ordinary rows feeding balances/budgets/dashboard/intelligence/backup) and Money accounts screen (create/edit/archive, exact opening balances, per-account balances) — the old Money placeholder screen removed
- Assistant session hardening: busy submits rejected (no duplicate send), clear-while-busy drops the stale reply (session generation), input clears only on accepted submits
- Security/release re-review: no backup-content logging, Secret Target exclusion re-confirmed, signing secrets external-only, `.gitignore` + `signing.properties`, no network deps; **encryption at rest documented as a future hardening item**
- UI review: 8-item bottom nav dense but functional; Jobs bar item documented as an intentionally unwired legacy route (left in place)
- Verification: **689 tests / 66 suites / 0 failures / 0 errors** (33 new); lint **0 errors / 27 warnings** (+1 = `kotlinx-coroutines-test` newer-version advisory); `assembleDebug`/`assembleRelease` OK; `apksigner verify` → Verifies, v2, CN=Prasbin Dhungana; docs updated, work committed and pushed

Previous phase: Phase 13 — Real Device Testing / Release ✅ COMPLETE

Final phase, executed on an Android 16 (API 36) emulator via ADB (no physical device was available — documented honestly):
- All 27 phases exercised end-to-end on-device (navigation, financial core, intelligence, budgets, work, goals + Secret Target, telecom, opportunities, CSV import, assistant, backup/restore/export)
- Verified blockers fixed (no new features, no schema change — still v7): dead Money/Settings bottom-bar wiring, Money placeholder → Transactions route, Settings version label → `BuildConfig.VERSION_NAME`
- Release APK signed with an external keystore (outside the repo), `apksigner verify` → Verifies (v2 scheme), versionCode 2 / versionName 1.0.0
- Secret Target device-tested: set/mask/reveal/edit/clear, absent from Dashboard, assistant and exports, survives relaunch
- Tampered backup rejected on-device with existing data unchanged; export/restore checksum round-trip verified
- Permissions: 4 install-time (library-merged), 0 runtime, no INTERNET; no network APIs/deps in the codebase
- 656 tests passing, lint 0 errors / 26 warnings (unchanged); docs updated; all work committed and pushed

Previous phase: Phase 12 — Security / Backup / Restore / Export / Import ✅ COMPLETE

Local-first full backup with integrity verification and atomic full-replacement restore (no cloud, no network, no auto-backup):
- Backup scope: all ten entity groups (accounts, categories, transactions, budgets, goals, work items, telecom SIMs/packages/subscriptions, opportunities) with IDs, relationships, exact Long minor units, timestamps, archived states, `source`, `externalRef`, budget month keys
- Deterministic versioned JSON (`shadow-money-backup` v1, `appSchemaVersion` 7), integer-only amounts, byte-identical output for identical data; 10 MB bound; plain JSON (no encryption — documented, never called encryption)
- SHA-256 checksum over the canonical payload bytes (sorted keys, no whitespace); tampered/malformed/unsupported/incompatible files rejected before any restore
- Restore pipeline: read → strict JSON → format → schema → checksum → record/ref validation → read-only preview with counts → mandatory destructive warning → explicit confirmation → single-transaction full replacement (failure = exact rollback, verified)
- Existing-data policy: full replacement (no merge); CSV import stays a separate additive flow with duplicate review
- SAF only (`CreateDocument` export / `OpenDocument` restore), zero manifest permissions, no network/logging of backup contents
- Secret Target: never in JSON, checksum input, previews or restore files; restore never modifies it
- Settings Backup & Restore section with preview/confirm dialogs and explicit error states; **no database schema change (still v7)**

Phase 11 Local Financial Assistant:

A deterministic, offline, read-only assistant for bounded questions about recorded financial data (not a general-purpose AI assistant):
- Bounded intents (A–K): balance, income, outflow, budget, goals, work, telecom, opportunities, transactions, import, help — plus SECRET_TARGET_REFUSAL / AMBIGUOUS / CLARIFY_PERIOD / UNSUPPORTED for anything outside the set; never guesses
- Deterministic keyword/phrase classification — no model, no randomness, no network; ambiguous ties answered with an honest "which one do you mean?"
- Periods today / yesterday / this week / last week / this month / last month / recent computed in Asia/Kathmandu (ISO Monday-start week, documented); future and unsupported periods get clarification instead of fabricated data
- Response labels reuse `intelligence.InsightKind` (FACT / CALCULATION / ANALYSIS / PROJECTION) with a `SOURCE: Local financial records.` line; existing projections re-used with explicit "projection only — not a guarantee" wording
- Read-only: `AssistantRepository` composes existing repositories/DAO aggregates — no write methods, **no database schema change (still v7)**
- Secret Target boundary: no `SecretTargetStore` dependency anywhere in assistant code; fixed refusal text; value never derivable from or present in responses (verified by set-and-compare tests)
- Session-local conversation history (bounded to 40 messages, never persisted); dashboard "Ask" entry + dedicated assistant screen
- Not a chatbot: no free-form generation, no advice beyond recorded-data answers, deterministic output for identical input

Phase 10 Legitimate Financial Import:

A safe, explicit CSV import flow: file/paste → parse → read-only preview → validation → duplicate review → explicit confirmation → ordinary transactions (manual file selection only — no scraping, no auth bypass, no credential/OTP handling):
- CSV schema with required `date, description, amount, direction, account` and optional `category, external_ref`, documented header aliases, explicit ambiguity/missing-column rejection
- Exact Long minor-unit amounts (never floats), ≤2 decimals, Kathmandu-local unambiguous date formats
- Row states NEW / POSSIBLE_DUPLICATE / INVALID; duplicates default to unselected; unmatched account/category names require explicit mapping
- Atomic confirmed import in one Room transaction (`source = IMPORT_FILE`, `externalRef` preserved); failed batch writes nothing; preview never writes
- Storage Access Framework only (`OpenDocument`), 5 MB bound, zero app permissions, no network/logging of CSV contents
- Room v7: additive `MIGRATION_6_7` adds `transactions.externalRef`; all existing data preserved

Phase 9 Opportunity Intelligence:
- A practical, local-first opportunity tracking and organization system (manual tracking only — never automated job acquisition)
- Opportunities with bounded types (Freelance / Client Work / Part Time / Remote Work / Project / Repository / Other) and explicit lifecycle statuses (New / Reviewing / Applied / In Progress / Won / Lost / Archived)
- Expected opportunity amounts (Long minor units, nullable) — explicitly NOT income
- Manual source/URL references with offline GitHub reference parsing (no fetching, no scraping)
- Status/type filters + local text search; detail view with "Open reference" user action
- Strict financial separation: opportunities never create transactions, never affect balances/income/budgets
- No predictions, scoring, probability, ranking, or AI; deterministic factual summaries only

Phase 8 Telecom Tracker:
- SIM cards, packages/plans, subscriptions (Room v5, migration `MIGRATION_4_5`)
- Expected monthly telecom cost via exact Long normalization
- Upcoming renewals (60-day horizon, max 5, deterministic)
- Summary: active SIMs, active subscriptions, expected monthly cost, next renewal
- Strict financial separation: telecom data never creates transactions or alters financial truth
- No device/SIM access, no carrier APIs, no SMS/phone permissions — all data manually entered

Phase 7 Goals + Secret Target:
- Dedicated Goals screen (list / create / edit / archive / delete) reusing the Phase 2 `goals` table; progress derived from the linked account's authoritative balance
- Secret Target: private personal target in app-private DataStore (Long minor units), masked by default with explicit reveal, updatable at any time (no monthly lock), excluded from Dashboard/goals/budgets/work/intelligence/logs/backups. Not encrypted (app-sandbox only) — limitation documented.

Phase 6 Work / Income / Project Tracker:
- Work items: ACTIVE / PAUSED / COMPLETED / ARCHIVED
- Expected amounts (estimates, never income) vs Received (from actual linked INCOME transactions)
- Transaction linking: transactions.workItemId → work_items.id, ON DELETE SET NULL
- Work list (status filter + title search) and work detail (linked transactions, link/unlink)
- Room migration v3 → v4; all existing data preserved

Phase 5 Budgets:
- Overall + per-category budgets (Long minor units, exact integer arithmetic)
- Kathmandu (Asia/Kathmandu) calendar month boundaries
- Spending derived from actual OUTFLOW transactions only
- Status: Under budget (<50%) / Approaching limit (50–<100%) / Over budget (≥100%)
- Create / edit / delete via dedicated Budgets screen with month navigation
- Duplicate prevention (one overall per month, one category per category per month)

Phase 4 Transaction Intelligence:
- 400-day analysis window with explicit boundaries
- Period calculations (income, outflow, net, previous-period trends)
- Recurring-outflow detection (ANALYSIS)
- Unusual-spending detection vs baseline (ANALYSIS)
- Monthly outflow projection with assumptions (PROJECTION)
- FACT / CALCULATION / ANALYSIS / PROJECTION classification on every insight

Phase 3 Dashboard:
- Financial summary (derived balance, income, outflow) with empty state
- Account balances (derived, archived marked)
- Recent transactions (latest 20)
- Outflow by category (uncategorized handled)
- Goal progress (linked-account based)

Phase 2 foundation:
- Dark futuristic Material 3 theme
- Navigation skeleton
- Room database v2 (accounts, categories, transactions, goals)
- Long minor-unit monetary representation
- Explicit migration from Phase 1 to Phase 2
- 656 passing unit/database/migration/dashboard/intelligence/budget/work/goal/secret-target/telecom/opportunity/import/security/assistant/backup tests

## Roadmap

| Phase | Name | Status |
|-------|------|--------|
| 0 | Environment Audit | ✅ Complete |
| 1 | Android Foundation | ✅ Complete |
| 2 | Financial Data Model | ✅ Complete |
| 3 | Dashboard | ✅ Complete |
| 4 | Transaction Intelligence | ✅ Complete |
| 5 | Budgets | ✅ Complete |
| 6 | Work / Income / Project Tracker | ✅ Complete |
| 7 | Goals + Secret Target | ✅ Complete |
| 8 | Telecom Tracker | ✅ Complete |
| 9 | Opportunity Intelligence | ✅ Complete |
| 10 | CSV Import | ✅ Complete |
| 11 | Local Financial Assistant | ✅ Complete |
| 12 | Security / Backup / Restore | ✅ Complete |
| 13 | Real Device Testing / Release | ✅ Complete |

## Financial Objective

Help the user work toward consistently earning at least **NPR 100,000/month**.

## Key Principles

- Local-first
- Financial truth (no fabricated data)
- Privacy and security
- Exact integer monetary representation (Long/paisa)
- Deterministic calculations
- Dark futuristic "System" aesthetic
