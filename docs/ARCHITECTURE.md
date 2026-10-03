# SHADOW MONEY — Architecture Documentation

## Phase 2 — Financial Data Model

### Overview

SHADOW MONEY is a personal Android financial-management application. Phase 2 implements the foundational financial data model.

### Package Structure

```
com.prasbin.shadowmoney
├── assistant             # Phase 11: deterministic read-only financial assistant
│   ├── IntentClassifier.kt   # Bounded deterministic intent/period recognition
│   ├── AssistantEngine.kt    # Pure response renderer (FACT/CALCULATION/ANALYSIS/PROJECTION)
│   ├── AssistantTime.kt      # Kathmandu period ranges (ISO weeks, budget months)
│   └── AssistantData.kt      # Immutable read-only snapshot models
├── data                    # Room entities, DAOs, repositories, database
│   ├── model               # Account, Category, Transaction, Goal entities
│   ├── imports             # Phase 10: CSV import (pure Kotlin)
│   ├── backup              # Phase 12: backup format/JSON/checksum/validator/restore/SAF
│   ├── repositories        # AccountRepository, CategoryRepository, etc.
│   ├── Converters.kt       # Room TypeConverters
│   ├── Money.kt            # Long minor-unit arithmetic
│   ├── SeedCategories.kt   # System category seeds
│   └── ShadowMoneyDatabase.kt  # Room Database v2 with MIGRATION_1_2
├── domain                  # Models, use cases, financial calculators
├── presentation            # Screens, components, navigation
│   ├── theme              # Material 3 dark futuristic theme
│   ├── navigation         # Navigation graph and routes
│   └── screen             # Individual screen composables
└── di                      # Dependency injection setup (future)
```

### Theme

Dark futuristic "System" aesthetic:
- Dark backgrounds (#0A0A1A, #12121F, #1A1A2E)
- Neon accents (Cyan #00D4FF, Purple #7B68EE)
- Monospace font for financial numbers
- Professional cards/panels with borders
- Readable, financial-focused UX

### Database Schema (v2)

**Accounts** (`accounts`):
- `id` (Long, PK) | `name` | `type` (AccountType enum) | `openingBalanceMinor` (Long) | `isActive` | `createdTimestamp`

**Categories** (`categories`):
- `id` (Long, PK) | `name` | `direction` (CategoryDirection enum) | `isActive` | `isSystem` | `createdTimestamp`
- 17 seeded system categories (5 income, 12 outflow)

**Transactions** (`transactions`):
- `id` (Long, PK) | `accountId` (FK→accounts) | `categoryId` (FK→categories, SET_NULL) | `amountMinor` (Long) | `direction` (INCOME/OUTFLOW) | `transactionTimestamp` | `note` | `createdTimestamp` | `source`

**Goals** (`goals`):
- `id` (Long, PK) | `name` | `targetAmountMinor` (Long) | `accountId` (FK→accounts, SET_NULL) | `deadlineTimestamp` | `isActive` | `isCompleted` | `createdTimestamp` | `updatedTimestamp`

### Financial Truth

Stored financial records are the source of truth.
Account balance is DERIVED: `openingBalanceMinor + SUM(income) - SUM(outflow)`
Uses Long arithmetic (100 paisa = 1 NPR). Never Float or Double.

### Delete Semantics

- Accounts: Archive/inactive preferred (`AccountDao.archive`). FK `transactions.accountId → accounts.id` is `RESTRICT`: deleting an account with historical transactions is blocked, history can never cascade-disappear. Empty accounts may still be deleted.
- Categories: Archive preferred (`CategoryDao.archive`); FK `transactions.categoryId → categories.id` is `SET NULL`, so deleting a category nulls the reference and preserves the transaction.
- Transactions: Historical records are never cascade-deleted; only explicit `TransactionDao.delete` removes one, leaving its account/category intact.
- Goals: FK `goals.accountId → accounts.id` is `SET NULL`; archive/inactive preferred.

### Migration (Phase 1 → Phase 2)

Phase 1 v1 contained only the structural `placeholder(id, name)` table with no user financial data. `MIGRATION_1_2` drops `placeholder` (intentional structural removal, no user-data loss) and creates `accounts`, `categories`, `transactions`, `goals` with camelCase Room columns, safe FK actions (`RESTRICT`/`SET NULL`, no `CASCADE`), and indices (`index_transactions_accountId`, `index_transactions_categoryId`, `index_transactions_transactionTimestamp`, `index_goals_accountId`). Verified by genuine v1 → v2 tests in `MigrationTest` (creates a v1 file, runs the actual `MIGRATION_1_2`, reopens via Room) and `DeleteArchiveSemanticsTest`.

### Migration (Phase 2 → Phase 3 / Budgets)

`MIGRATION_2_3` (Room version 2 → 3) creates the `budgets` table with the unique index `index_budgets_categoryId_monthKey`, the query index `index_budgets_monthKey`, and the `RESTRICT` foreign key to `categories`. Non-destructive; all existing tables and data are preserved. Verified by `BudgetMigrationTest` (real v2 database file → actual `MIGRATION_2_3` → schema, unique-index, and FK enforcement checks plus data survival).

### Migration (Phase 5 → Phase 6 / Work Tracker)

`MIGRATION_3_4` (Room version 3 → 4) creates the `work_items` table with `index_work_items_status`, adds the nullable `workItemId` column to `transactions` (`INTEGER DEFAULT NULL REFERENCES work_items(id) ON DELETE SET NULL`), and creates `index_transactions_workItemId`. Non-destructive; all existing tables (accounts, categories, transactions, goals, budgets) and data are preserved. Verified by `WorkMigrationTest` (real v3 database → actual `MIGRATION_3_4` → all tables/data survive, transaction values unchanged, `workItemId` nullable, linked transaction survives work-item deletion with SET NULL).

### Migration (Phase 7 → Phase 8 / Telecom Tracker)

`MIGRATION_4_5` (Room version 4 → 5) creates `telecom_sims`, `telecom_packages` (with `index_telecom_packages_carrier`), and `telecom_subscriptions` (with RESTRICT foreign keys to sims/packages and indices on `simId`, `packageId`, `renewalTimestamp`). Non-destructive; all existing tables and data are preserved. Verified by `TelecomMigrationTest` (real v4 database → actual `MIGRATION_4_5` → all financial/work/goal/budget data survives, telecom tables/indices/FKs created, FK enforcement tested).

### Migration (Phase 8 → Phase 9 / Opportunity Intelligence)

`MIGRATION_5_6` (Room version 5 → 6) creates the `opportunities` table with indices `index_opportunities_status`, `index_opportunities_type`, `index_opportunities_deadlineTimestamp`. Non-destructive; all existing tables and data are preserved. Verified by `OpportunityMigrationTest` (real v5 database → actual `MIGRATION_5_6` → all financial/goal/budget/work/telecom data survives, opportunity table/indices created).

### Migration (Phase 9 → Phase 10 / CSV Import)

`MIGRATION_6_7` (Room version 6 → 7) is a single additive statement: `ALTER TABLE transactions ADD COLUMN externalRef TEXT DEFAULT NULL`. No table rebuild, no destructive change; every existing row keeps its values and gets `externalRef = NULL` (Room `@ColumnInfo(defaultValue = "NULL")` matches SQLite's stored default exactly). Imported rows may carry a user-provided external reference (bank export id) in this column; manually created transactions keep it `NULL`. All five pre-existing migration tests were extended to chain `MIGRATION_6_7`. Verified by `ImportMigrationTest` (genuinely populated v6 database with accounts/categories/transactions/goals/budgets/work/telecom/opportunities → actual `MIGRATION_6_7` → all data survives, column exists and is NULL for old rows, new imported transaction with `externalRef` round-trips).

### Dashboard (Phase 3)

The Dashboard route renders real data from the Phase 2 Room model via Room → DAO/repository → ViewModel/state → Compose UI.

**Data sources** (`DashboardRepository`):
- Summary totals: `TransactionDao.getTotalIncomeMinorForActiveAccounts()` / `getTotalOutflowMinorForActiveAccounts()` (active accounts only) + summed `openingBalanceMinor` of active accounts → derived via `Money.balanceMinor(opening, income, outflow)`. No stored/second balance.
- Account balances: every account listed (archived dimmed); per-account balance = `openingBalanceMinor + income − outflow` reusing the Phase 2 authoritative per-account totals.
- Recent transactions: bounded `TransactionDao.getRecent(20)` (newest first, `ORDER BY transactionTimestamp DESC, id DESC`).
- Outflow by category: single aggregate `SELECT categoryId, SUM(amountMinor) ... WHERE direction = 1 GROUP BY categoryId` executed through `SupportSQLiteOpenHelper.readableDatabase` (Room 2.8.3 + KSP 2.1.20 cannot process non-entity POJO projection return types; the raw SupportSQLite path is a first-class Room API and keeps exact SQL aggregation). Deleted categories surface as `categoryId = null` → displayed as "Uncategorized" (amber).
- Goal progress: active goals only; progress = linked account's derived balance × 100 / `targetAmountMinor`, coerced to 0–100; goals without a linked account show "No account linked".

**State handling** (`DashboardUiState`): `Loading` → `Empty` (no accounts AND no transactions AND no goals — never a silent zero) → `Content` / `Error`. Database failures are caught and surfaced as `Error` with a message; per-section empty states exist for accounts, outflow, transactions, and goals. The UI state is a `StateFlow` rebuilt from a Room-invalidated snapshot (`accounts` / `transaction count` / `categories` / `active goals` flows), so the dashboard reacts to every insert/update/delete.

**Trust label**: "Local records only · not a bank balance." is shown in the TopAppBar and the empty state.

### Transaction Intelligence (Phase 4)

Deterministic, offline intelligence computed from stored Room records. No external AI, no network, no stored intelligence tables — all insights are recomputed transiently from the existing data model.

**Architecture**: pure-Kotlin domain package `com.prasbin.shadowmoney.intelligence` (no Android dependencies, plain-JUnit testable) + `data/IntelligenceRepository` (Room-backed loading) + `IntelligenceViewModel` (state) + Intelligence section on the Dashboard.

**Analysis window**: 400 days, `[now − 400 days, now]` (both inclusive), based on `transactionTimestamp`. The previous comparable period is the 400 days before it (`[now − 800 days, now − 400 days)`). The repository loads an 800-day slice (bounded) and the engine applies the exact boundaries. Transactions outside the window never contaminate period calculations.

**Period calculations** (`PeriodCalculator`): window income/outflow/net, previous-period stats, transaction counts, per-category and per-account outflow, trend percent `(current − previous) × 100 / previous` (null when previous is 0; integer division truncates toward zero), history span in days. All Long minor-unit arithmetic.

**Recurring-outflow detection** (`RecurringDetector`, ANALYSIS): groups outflows by normalized note (lowercase, trimmed, whitespace-collapsed). A group qualifies only when ALL hold: ≥ 3 occurrences in window; amounts within 10% (`max × 100 ≤ min × 110`); ≥ 2 inter-transaction gaps, each ≥ 1 day, with `maxGap ≤ 2 × minGap`. Typical amount/interval = median (lower middle for even counts). Insufficient evidence → no insight. Never implies future payment certainty.

**Unusual-spending detection** (`UnusualDetector`, ANALYSIS): (a) category-level — last-30-days category outflow vs the typical 30-day baseline derived from days 31–400 of the window; flagged only when baseline has ≥ 2 transactions, recent total ≥ NPR 500 (50,000 minor), and recent > 150% of baseline; (b) transaction-level — a single outflow > 3× the category's average transaction, with ≥ 3 category transactions and amount ≥ NPR 500. Neutral wording only ("materially above baseline", "statistical comparison, not a judgement"). Insufficient baseline → no insight.

**Projection** (`ProjectionEngine`, PROJECTION): projected monthly outflow = `windowOutflow × 30 / historyDays` (integer division, truncation), only when history spans ≥ 7 days and window outflow is non-zero. Assumptions are attached to the insight ("assumes the observed daily spending rate continues", "projection only — not a guarantee"). Not an income promise; no job/investment/market prediction.

**Classification**: every `Insight` carries `InsightKind` FACT / CALCULATION / ANALYSIS / PROJECTION. FACT = direct record facts; CALCULATION = deterministic arithmetic over records; ANALYSIS = detected patterns/statistical comparisons; PROJECTION = forward estimates with assumptions. The UI renders a colored kind badge on every insight and never presents analysis/projection as fact.

**Insufficient data**: empty DB, too few transactions, or < 7 days history → `sufficientData = false`, detectors return nothing, UI shows "Insufficient data…" / "No insight found". Insights are never manufactured.

### Budgets (Phase 5)

Local budgeting on the existing financial model. New `budgets` table (Room v3, migration `MIGRATION_2_3`); all other tables unchanged.

**Budget model** (`Budget` entity): `amountMinor` (Long), `monthKey` (`yyyy-MM`), `categoryId` (nullable — null = overall budget), `createdTimestamp`, `updatedTimestamp`. FK `categoryId → categories.id` with `ON DELETE RESTRICT` (a category with budgets cannot be deleted — budgets must be deleted first; financial history is never cascade-deleted). Unique index on (`categoryId`, `monthKey`) enforces one category budget per category per month at the database level; one overall budget per month is enforced at the repository level (SQLite unique indexes treat NULLs as distinct).

**Month definition**: Asia/Kathmandu (UTC+05:45, no DST). `monthStart(monthKey)` = first instant of the month in Kathmandu; `monthEndExclusive(monthKey)` = first instant of the next month. Spending queries use `transactionTimestamp >= start AND < end`.

**Spending derivation**: budget spending = `SUM(amountMinor)` of `direction = 1` (OUTFLOW) transactions within the month, via SQL aggregation (`getOutflowTotalForPeriod`, `getOutflowTotalForCategoryPeriod`, and a `GROUP BY categoryId` raw query). INCOME, opening balances, account balances, and projections are never counted. Archived accounts' historical transactions still count (budgets operate on transaction records, not account state). Uncategorized transactions count toward the overall budget only.

**Status thresholds** (`BudgetStatus`): `NORMAL` when `spent × 100 < budget × 50`; `APPROACHING` when `< 100`; `OVER_BUDGET` at `≥ 100`. Zero/invalid budget (≤ 0): zero spending → NORMAL, any spending → OVER_BUDGET. `percentUsed = spent × 100 / budget` (integer division, 0 when budget ≤ 0 — never divide by zero). `remaining = budget − spent` (may be negative; not capped). Progress bars are capped at 100% for display only; real values are retained separately.

**Overall vs category interaction**: shown independently; category budgets are sub-limits, not additional money; no summing of unrelated limits.

**UI**: dedicated Budgets screen (navigation route `budgets`, bottom-bar entry on the Dashboard) with month stepper, overall budget panel, category budget panels, create/edit dialog (scope toggle, category dropdown, month stepper, NPR amount parsed to minor units with exact integer arithmetic), delete action, and explicit Loading / Empty / Content / Error states. Budget changes, transaction changes, and category changes all refresh the display via Room Flow → StateFlow.

### Work / Income / Project Tracker (Phase 6)

A local tracker for jobs, freelance work, and projects. **The tracker is separate from financial truth**: a work item is an opportunity record; a transaction is actual money.

**Work item model** (`WorkItem` entity, `work_items` table): `title`, `description`, `status` (ACTIVE / PAUSED / COMPLETED / ARCHIVED), `expectedAmountMinor` (Long), `deadlineTimestamp` (0 = none), `client`, `createdTimestamp`, `updatedTimestamp`.

**Expected vs actual**: expected amount is a work/project estimate — it is NOT income. It never increases account balance, dashboard income, or budget figures. Received amount is derived from actual linked transactions: `received = SUM(amountMinor)` of `direction = 0` (INCOME) transactions where `workItemId` matches. OUTFLOW, expected amounts, projections, and unrelated income are never counted. If no linked income exists, received = 0. `remainingExpected = expected − received` (may go negative — never clamped).

**Transaction link**: `transactions.workItemId → work_items.id`, nullable FK with `ON DELETE SET NULL`. Deleting a work item keeps all linked transactions with `workItemId = null` — financial history always survives. Linking/unlinking never changes a transaction amount. Linking an INCOME transaction counts it toward the work item's received total; linking an OUTFLOW transaction does not.

**Status meanings**: ACTIVE (in progress), PAUSED (temporarily halted), COMPLETED (done), ARCHIVED (kept for history, hidden from default views). Archive is preferred for records with meaningful history; deletion is allowed and safe (SET NULL).

**Deadlines**: Kathmandu-date comparison — NO_DEADLINE (0), UPCOMING (future), DUE_TODAY (same Kathmandu date), OVERDUE (past). Neutral wording only; no predictive claims.

**UI**: Work screen (route `work`, bottom-bar entry) with status filter chips (All/Active/Paused/Completed/Archived), title search, work cards (status badge, Expected / Received / Remaining expected, deadline), FAB + form dialog (title, description, expected amount, optional deadline `yyyy-MM-dd`, client). Work detail screen (route `work/{id}`) with full details, linked transactions (unlink), and linkable transactions (link). Explicit Loading / Empty / Content / Error states. Received totals use one grouped SQL query (no N+1).

**Financial integrity** (regression-tested in `FinancialIntegrityTest`): expected amounts do not change account balance, income totals, or budget figures; linked actual income remains normal financial income; linked outflow remains budget spending; deleting/archiving a work item never deletes or alters transactions.

### Goals + Secret Target (Phase 7)

**Ordinary goals** reuse the Phase 2 `goals` table (no duplicate goal store). `GoalRepository` (extended) provides CRUD with validation (name required, target > 0) and `loadGoalViews`: progress = linked account's authoritative derived balance (`opening + income − outflow` via `Money.balanceMinor`), `remaining = target − balance`, `percent = balance × 100 / target` (0 when target ≤ 0). Progress > 100% is truthful (bar capped visually only). Account relationship: Phase 2 FK `SET NULL` — account deletion nulls the goal's `accountId` and the UI shows "No account linked"; archived accounts still derive progress from their records. Goals screen (route `goals`, bottom-bar entry) with list/create/edit/archive/delete, form dialog (name, target, optional account, optional deadline), and explicit Loading/Empty/Content/Error states. Dashboard goal section unchanged and reactive (same DAO/flows).

**Secret Target** is a private personal target, NOT an ordinary goal:
- **Storage**: app-private DataStore Preferences (`secret_target_preferences`), single Long key `secret_target_minor` (exact minor units, never Float/Double). No Room table — the value never enters the database.
- **Privacy boundary**: excluded from Dashboard totals, ordinary goal lists, transactions, budgets, work tracker, intelligence engine, logs, analytics, and network. The store contains no logging; the value never enters shared repository state.
- **UI**: private section on the Goals screen — masked by default (`••••••••`), explicit reveal toggle, edit dialog, clear with confirmation. Wording makes the private nature clear.
- **Update rule**: the user may update the Secret Target at any time — no monthly-change lock.
- **Encryption status**: NOT encrypted. Stored in app-private DataStore (sandboxed app storage, `MODE_PRIVATE` semantics). No Keystore/AES-GCM is implemented in this phase; the exact limitation is that the value is protected only by Android app sandboxing, not by encryption at rest.
- **Backup/export exclusion**: the Secret Target lives outside Room, so any future DB backup/SAF export cannot include it by construction. No backup functionality is implemented in this phase.

### Telecom Tracker (Phase 8)

A local/manual telecom tracking system. It is a tracker, never a telecom-control system: no device/SIM access, no carrier APIs, no SMS/phone permissions, no automatic carrier actions. All data is user-entered.

**Model** (new tables in Room v5, migration `MIGRATION_4_5`):
- `telecom_sims`: label, carrier, phoneNumber (user-entered, optional — never auto-read), status (ACTIVE/ARCHIVED), notes, timestamps.
- `telecom_packages`: name, carrier, category, `priceMinor` (Long), `period` (weekly/monthly/quarterly/yearly), notes, `isActive`, timestamps; index on `carrier`.
- `telecom_subscriptions`: `simId` FK → sims (RESTRICT), `packageId` FK → packages (RESTRICT), `startTimestamp`, `renewalTimestamp`, optional custom `monthlyCostMinor`, `isActive`, timestamps; indices on `simId`, `packageId`, `renewalTimestamp`. RESTRICT protects subscriptions — a SIM/package with subscriptions cannot be deleted (archive it instead); no financial history is ever cascade-deleted.

**TelecomMath** (pure Kotlin, exact Long arithmetic): expected monthly cost normalization — weekly `price × 52 ÷ 12`, monthly as-is, quarterly `÷ 3`, yearly `÷ 12`; non-positive price or unknown period → 0; division truncates toward zero. Labeled "expected monthly telecom cost" — never a carrier bill. Subscription cost = custom `monthlyCostMinor` if set, else derived from its package.

**Renewals**: active subscriptions with `renewalTimestamp` in the next 60 days (bounded horizon), sorted by renewal date, capped at 5 displayed items. Uses stored renewal dates only; no fake renewals, no carrier prediction.

**Financial separation** (regression-tested in `TelecomFinancialSeparationTest`): creating a package or subscription never creates a transaction; expected monthly cost never changes account balance; telecom data never affects budget spending; telecom records never become income/outflow. Actual payments remain normal financial transactions entered through the financial system.

**UI**: Telecom screen (route `telecom`, bottom-bar entry) with Summary panel (active SIMs, active subscriptions, expected monthly cost, next renewal, ≤5 upcoming renewals), SIMs panel (add/edit/archive/activate), Packages panel (add/edit/archive), Subscriptions panel (create/edit/deactivate/delete), and explicit Loading/Empty/Content/Error states. Kathmandu timezone for date handling. No logging of phone numbers or sensitive telecom data.

### Opportunity Intelligence (Phase 9)

A local-first opportunity tracking and organization system for manually tracked income opportunities (freelance jobs, client work, part-time, remote work, projects, repositories). It is for tracking/organization only — never automated job acquisition.

**Model** (`Opportunity` entity, `opportunities` table, Room v6 / `MIGRATION_5_6`): `title`, `description`, `type` (bounded: Freelance / Client Work / Part Time / Remote Work / Project / Repository / Other), `source`, `sourceUrl` (plain user-entered data), `expectedAmountMinor` (Long, nullable), `status` (New / Reviewing / Applied / In Progress / Won / Lost / Archived), `deadlineTimestamp` (0 = none), `client`, timestamps; indices on `status`, `type`, `deadlineTimestamp`.

**Expected vs actual**: an opportunity amount is an expected opportunity amount — NOT income. It never increases account balance, income totals, or budget figures; never creates a transaction. A `WON` status is still only an opportunity record until an actual financial transaction is separately recorded. No probability, successRate, predictedIncome, or speculative fields exist.

**No predictions/scoring**: the app provides deterministic factual summaries only (active count, total stored expected amounts, needs-review count, upcoming deadlines). No ranking, "best opportunity", "most likely to pay", or AI.

**URL handling**: URLs are stored as plain data and validated for basic structure (http/https, no spaces). Offline GitHub reference parsing extracts owner/repository/issue-reference from `https://github.com/owner/repo[/issues/123]` — no fetching, no scraping, no GitHub API calls. The detail view offers a user-initiated "Open reference" action (Android `ACTION_VIEW` intent); the app never fetches the URL itself.

**Financial separation** (regression-tested in `OpportunityFinancialSeparationTest`): creating an opportunity creates no transaction; expected amount does not change account balance; expected amount does not count as income; expected amount does not affect budgets; status changes (including WON) create no financial records.

**UI**: Opportunities screen (route `opportunities`, bottom-bar entry) with Summary panel (active count, total stored expected amount, needs-review count, ≤5 upcoming deadlines), status/type filter chips, title/client/source/notes search, opportunity cards, create/edit form dialogs, detail dialog with Open-reference action, and explicit Loading/Empty/Content/Error states. Kathmandu timezone for deadlines (No deadline / Upcoming / Due today / Overdue).

### CSV Import / Integration (Phase 10)

A legitimate, user-driven CSV import flow that turns file-or-paste CSV data into ordinary transactions. Manual file selection only — no scraping, no bank/email/API access, no authentication, no credential/OTP/PIN/CVV handling.

**Flow** (one-way state machine, `ImportTransactionsViewModel`, package `presentation.screen.csvimport`, route `import`): `Source` (paste text or SAF file picker) → `Loading` → `PreviewReady` (read-only preview) → user reviews rows/duplicates/mappings → `Importing` → `Success` / `Error`. Alternative outcomes: `PreviewRejected` (bad header/required columns), `EmptyInput` (no data rows), `Error` (oversize input, unreadable file, database failure). Entries: "Import Transactions (CSV)" buttons on the Transactions and Money screens.

**Package structure** — `data/imports` (pure Kotlin, no Android APIs):
- `CsvParser` — deterministic RFC4180-style parser: quoted fields, escaped quotes, embedded commas/newlines, blank-line skip, BOM strip, per-row structural errors.
- `CsvStreamReader` — bounded read (≤ `MAX_IMPORT_BYTES` = 5 MB): `Ok` / `TooLarge` / `ReadError`. Content lives in memory only during preview; never persisted, never logged.
- `ImportSchema` — header normalization (trim, lowercase, non-alphanumeric → `_`) + alias resolution. Required: `date`, `description`, `amount`, `direction`, `account`. Optional: `category`, `external_ref`. Aliases: `transaction_date`, `note`, `type`, `reference`/`transaction_id`. Two columns mapping to one field → explicit "Ambiguous" rejection; zero recognized columns → "Missing header row"; missing required → rejection naming them; unknown columns ignored.
- `ImportAmount` — exact integer parsing into Long minor units (never Float/Double): optional sign, valid thousands grouping only, ≤ 2 decimal places (more → rejection, never silently rounded), overflow rejection; direction column is authoritative (`income`/`credit`/`cr`/`deposit`/`received`/`in`, `outflow`/`debit`/`dr`/`withdrawal`/`expense`/`spent`/`out`); INCOME must be positive (negative → amount/direction conflict), OUTFLOW stores the absolute magnitude, zero amount is invalid.
- `ImportDate` — only unambiguous formats (`yyyy-MM-dd`, `yyyy/MM/dd`, `yyyy-MM-dd HH:mm[:ss]`, `yyyy-MM-ddTHH:mm[:ss]`) interpreted in `Asia/Kathmandu` (`BudgetCalendar.KATHMANDU_ZONE`); timezone-qualified input, ambiguous `dd/MM/yyyy`, and impossible calendar dates are rejected with explicit reasons.
- `ImportModel` / `ImportFingerprint` — immutable preview rows: `NEW` / `POSSIBLE_DUPLICATE` / `INVALID` states plus separate flags for unmatched account/category names; reasons list per invalid row; `isImportable()` gate.
- `ImportEngine` — pure `buildPreview(document, reference, accountMappings, categoryMappings)`; performs zero database writes.
- `ImportRepository` — loads reference data (accounts, categories, existing fingerprints) and performs the confirmed write.

**Duplicate detection**: fingerprint = `normalizeText(accountName) | KathmanduDate | amountMinor | direction | normalizeText(categoryName) | normalizeText(note) | normalizeText(externalRef)` (joined by `|`), checked against existing transactions first, then earlier rows inside the same file. `POSSIBLE_DUPLICATE` rows default to **unselected**; importing one requires an explicit per-row decision. No fingerprint ever becomes a database key.

**Confirmation & atomicity**: nothing is written during preview (verified by test). Final import runs inside a single `database.withTransaction { ... }` over the user's explicit selection — an FK violation mid-batch rolls everything back (verified by simulated-failure test). Imported rows are normal `transactions` rows: `source = TRANSACTION_SOURCE_IMPORT_FILE` ("IMPORT_FILE"), optional `externalRef` preserved, same account/category FKs, same balance/income/outflow/budget derivation. There is no second ledger and no permanent import table.

**Mapping**: rows whose account/category name matches (case/whitespace-insensitive) resolve automatically; unmatched names are surfaced and require an explicit `setAccountMapping` / `setCategoryMapping` choice before the row becomes importable. A present-but-empty category/account cell is INVALID (missing), an absent optional category column means `categoryId = null`. Changing a mapping rebuilds the preview and resets default selections.

**UI**: preview screen with counts (total/valid/duplicate/invalid/unmatched), per-row status chips and reasons, duplicate review with per-row import decisions, mapping dropdowns, selection checkboxes, and a confirm action labeled with the selected count ("No write yet — N selected"). SAF `OpenDocument` with `text/csv`, `text/comma-separated-values`, `text/plain`, `text/*`, `application/vnd.ms-excel` mime types; single-document read via `contentResolver`, no persisted permissions, no storage permissions.

**Security boundary** (regression-tested in `ImportSecurityTest`): manifest declares zero permissions (no storage, no network); import sources contain no `java.net`/HTTP/socket code, no logging (`android.util.Log`, `println`) of CSV contents, and no password/OTP/CVV/pin/credential/token/secret handling; 5 MB bound enforced.

**Financial separation**: import never fabricates data — amounts, directions, and dates come only from the file; duplicates are never silently collapsed or auto-imported; preview shows only what already exists plus what the file would add; the normal financial source of truth (transactions table) remains the single ledger.

### Local Financial Assistant (Phase 11)

A deterministic, offline, read-only assistant that answers **bounded** questions about the user's own recorded financial records. It is not a general-purpose AI assistant: no model, no network, no free-form generation, no advice beyond what the recorded data supports, and no writes of any kind. **Phase 11 does not change the database schema (still v7).**

**Package structure** — pure Kotlin domain under `assistant/` + one read-only data composer:
- `assistant/AssistantIntent.kt` — bounded intent set (`BALANCE`, `INCOME`, `OUTFLOW`, `BUDGET`, `GOALS`, `WORK`, `TELECOM`, `OPPORTUNITIES`, `TRANSACTIONS`, `IMPORT`, `HELP`) plus explicit non-answer states (`SECRET_TARGET_REFUSAL`, `AMBIGUOUS`, `CLARIFY_PERIOD`, `UNSUPPORTED`); bounded periods (`TODAY`, `YESTERDAY`, `THIS_WEEK`, `LAST_WEEK`, `THIS_MONTH`, `LAST_MONTH`, `RECENT`); `requiresData` / `defaultPeriod` extensions and `ClassifiedQuestion.resolvedPeriod()` (explicit period, else deterministic per-intent default).
- `assistant/IntentClassifier.kt` — deterministic normalization (lowercase → collapse whitespace → trim → strip trailing punctuation) and priority-ordered classification: blank → HELP; Secret Target phrases → `SECRET_TARGET_REFUSAL` (hard privacy boundary checked before anything else); exact help phrasings → HELP; future-period phrases → `CLARIFY_PERIOD` (never fabricate future data); unsupported-period phrases → `CLARIFY_PERIOD`; import/CSV tokens → IMPORT (workflow before scoring); then scored matching (strong phrase = 3 points, whole-token keyword = 1 point) where a tie between top scorers → `AMBIGUOUS` and no scorer → `UNSUPPORTED` (never guess); finally period extraction (multiple distinct periods → `CLARIFY_PERIOD`) and intent-specific period validation (budgets are monthly; income/spending need a real range for "recent"). No randomness — identical input yields identical classification (regression-tested).
- `assistant/AssistantTime.kt` — half-open `[start, end)` period ranges computed in `Asia/Kathmandu` (`BudgetCalendar.KATHMANDU_ZONE`, UTC+05:45, no DST). "This week" = current ISO week starting **Monday** in Kathmandu, "last week" = the week before (documented convention). Month boundaries reuse `BudgetCalendar` exactly (`monthStart` / `monthEndExclusive` / `currentMonthKey` / `shiftMonth` / `monthLabel`), so budget month keys and assistant periods can never disagree.
- `assistant/AssistantData.kt` — immutable read-only snapshot models (accounts, period totals, window/recent transactions, budget views, goals, work, telecom, opportunities, projection insight). Contains **no** Secret Target value and no reference to secret-target storage.
- `assistant/AssistantEngine.kt` — pure renderer: `answer(question, data) → AssistantResponse(sections, source)`. Data-intent answers are composed from the provided snapshot only; non-answer states render fixed guidance strings. Sections reuse `intelligence.InsightKind` labels (`FACT` / `CALCULATION` / `ANALYSIS` / `PROJECTION`) or `kind = null` for plain guidance; every response carries `source = "Local financial records."`. Output is bounded (accounts ≤ 8 lines, transactions/goals/work/opportunities/categories/renewals/budget categories ≤ 5 lines, with an explicit "…and N more" line). The Phase 4 projection insight, when present, is re-rendered with an explicit `PROJECTION —` prefix and "Projection only — not a guarantee." wording. Amount formatting always uses `Money.formatNpr` (existing helper).
- `data/AssistantRepository.kt` — **read-only** composer with **no write methods**: `load(question)` assembles `AssistantData` from existing authoritative sources only — `DashboardRepository` (balances, recent transactions), `BudgetRepository` (budget math), `GoalRepository` (goal progress), `WorkRepository` (expected vs received), `TelecomRepository` (expected monthly cost), `OpportunityRepository` (summaries), `IntelligenceRepository` (existing projections), plus scalar period aggregates on `TransactionDao` (`getIncomeTotalForPeriod`, `getOutflowTotalForPeriod`, `getCountInPeriod`, `getCountInPeriodByDirection` — the genuinely missing period sums/counts) and a per-category period breakdown via the same deterministic SQL aggregation `BudgetRepository` uses for monthly spending, parameterized for assistant periods. It has no `SecretTargetStore` dependency and never touches secret-target storage (verified by constructor-reflection and source-scan tests).

**Presentation** — `presentation/screen/assistant/AssistantViewModel` + `AssistantScreen` (route `assistant`, dashboard top-bar "Ask" entry): chat-style session where each submit runs classify → (if `requiresData`) `repository.load` → `engine.answer`, displayed with kind chips and a `SOURCE:` line. Conversation history is **session-local only** — bounded to `MAX_MESSAGES = 40`, held in the ViewModel, never persisted, never written to the database, and discarded when the screen/ViewModel goes away. Repository failures surface the fixed `ASSISTANT_READ_ERROR_TEXT` ("Your records were not changed.") and recover; blank input is ignored; only one request is processed at a time.

**Secret Target boundary** (`AssistantPrivacyTest`, 9 tests): assistant sources contain no `SecretTargetStore` reference and no logging; the repository constructor has no secret-store parameter; `AssistantData` has no secret fields; secret-target questions classify to `SECRET_TARGET_REFUSAL` and render the fixed refusal text with `kind = null` regardless of any stored secret; the secret value (raw or formatted) never appears in any financial answer; changing the stored secret before/while answering produces byte-identical responses (set-and-compare); asking questions never mutates the database.

**Read-only guarantee** (`AssistantRepositoryTest`, `everyIntentAnswer_isReadOnly_databaseNeverChanges`): full table dumps (accounts, categories, transactions, goals, work items, opportunities, telecom entities, budgets) are captured before and after answering every intent — including clarification/refusal/unsupported paths — and compared for equality. `AssistantViewModelTest` repeats the guarantee at the ViewModel level.

**Limitations (documented, by design)**: bounded intents only — anything else gets an honest unsupported/ambiguous response; periods are the seven deterministic options above (no custom date ranges yet); "recent" lists the latest 20 dashboard transactions (5 shown); budget answers are monthly; projections are re-used Phase 4 outputs, never newly generated; identical input always yields identical output (no personalization or learning).

### Backup / Restore / Export / Import (Phase 12)

Local-first full backup with integrity verification and atomic full-replacement restore. No cloud, no network, no auto-backup, no background scheduling — the user explicitly exports to a location they choose and explicitly restores from a file they pick. **Phase 12 does not change the database schema (still v7).**

**Package structure** — `data/backup/` (file I/O isolated behind one interface, pure logic JVM-testable):
- `BackupModels.kt` — format constants (`BACKUP_FORMAT_NAME = "shadow-money-backup"`, `BACKUP_FORMAT_VERSION = 1`, `APP_SCHEMA_VERSION = 7`, `BACKUP_CHECKSUM_ALGORITHM = "SHA-256"`, `MAX_BACKUP_BYTES = 10 MB`), the destructive-restore warning text, the `BackupErrorCode` taxonomy (NO_DATA, INVALID_JSON, UNSUPPORTED_FORMAT, INCOMPATIBLE_SCHEMA, CHECKSUM_MISMATCH, MALFORMED_RECORDS, MISSING_REFERENCE, FILE_TOO_LARGE, STORAGE_ERROR, RESTORE_FAILED, UNEXPECTED), explicit backup DTOs (one per entity — Room entities are never serialized blindly), `BackupPayload`, `BackupEnvelope`, `RestoreCandidate`, and the outcome sealed types.
- `BackupJson.kt` — minimal strict JSON parser/writer. Parser rejects fractional/exponent numbers (integer-only, so money can never become floating point), duplicate object keys (checksum ambiguity), leading zeros, trailing content, raw control characters, and nesting deeper than 32 levels; Long values round-trip exactly (including `Long.MIN/MAX`). Writer is canonical: object keys sorted, no insignificant whitespace, plain digit integers, minimal escaping — the same value always serializes to the same bytes.
- `BackupChecksum.kt` — SHA-256 (via `java.security.MessageDigest`) over the UTF-8 bytes of the canonical payload JSON; lowercase hex output, case-insensitive comparison.
- `BackupMappers.kt` — DTO ↔ Room-entity conversions for all ten entity groups.
- `BackupSerializer.kt` — DTO tree builders, envelope `write` (deterministic), and the intake `read(text, expectedSchemaVersion)` pipeline: strict parse → format name → format version → installed-schema comparison → checksum structure/algorithm → **SHA-256 verification over the canonicalized payload subtree** → typed record decoding with path-precise errors (`transactions[3].amountMinor must be a whole number`). The checksum hashes only the `payload` subtree, so there is no circularity; the envelope (format/versions/timestamp/checksum field) is excluded by design.
- `BackupBuilder.kt` — pure envelope assembly: same payload + timestamp + schema ⇒ byte-identical document.
- `BackupValidator.kt` — semantic validation of a decoded payload: positive unique primary keys per group; enum ranges (account type 0–3, category direction 0–2, transaction direction 0–1, work status 0–3, SIM status 0–1, billing period 0–3, opportunity type/status 0–6); `YYYY-MM` month keys parsed with `YearMonth`; non-negative minor-unit amounts (except account opening balance, which may be negative); duplicate budget slots rejected per (monthKey, categoryId); referential integrity for every foreign key (transaction account/category/work, goal account, budget category, subscription SIM/package) → `MISSING_REFERENCE`. Nothing is repaired, skipped, or relaxed — the whole file is rejected.
- `BackupDao.kt` — query-only DAO (full-table reads, `COUNT(*)` per table, `DELETE` per table) registered on `ShadowMoneyDatabase`; adds no tables/columns/indices, so the schema stays at v7.
- `RestoreEngine.kt` — single `database.withTransaction { ... }`: delete children-first (transactions → goals → budgets → telecom_subscriptions → opportunities → work_items → telecom_packages → telecom_sims → categories → accounts), then insert parents-first with **original primary keys** (SQLite AUTOINCREMENT advances from explicit ids, so subsequent app inserts never collide). Any exception rolls the whole transaction back.
- `BackupFileIo.kt` — `BackupTextReader` (bounded read ≤ `MAX_BACKUP_BYTES`, `Ok`/`TooLarge`/`ReadError`) + the `BackupFileIo` interface + `SafBackupFileIo` (`ContentResolver.openInputStream`/`openOutputStream` on the user-picked document URI only).
- `BackupRepository.kt` — orchestration: `createBackupJson` (load → build → serialize → size check), `prepareRestoreFromUri`/`prepareRestoreText` (read → full intake pipeline → validation → `RestoreCandidate`, zero writes), `restore` (re-validate → atomic engine write), `restoreFromText`, `loadCounts`, `writeBackupToUri`.

**Backup format** (deterministic, versioned):
```json
{"appSchemaVersion":7,"checksum":{"algorithm":"SHA-256","value":"<64 hex>"},
 "createdAtEpochMillis":1800000000000,"format":"shadow-money-backup",
 "formatVersion":1,"payload":{"accounts":[…],"budgets":[…],"categories":[…],
 "goals":[…],"opportunities":[…],"telecomPackages":[…],"telecomSims":[…],
 "telecomSubscriptions":[…],"transactions":[…],"workItems":[…]}}
```
Payload group keys and every record's field keys are emitted in sorted order; the whole document is whitespace-free.

**Entities included** (all ten groups, every column): accounts, categories, transactions (incl. `workItemId`, `source`, `externalRef`), goals, budgets (incl. `monthKey`), work_items, telecom_sims, telecom_packages, telecom_subscriptions, opportunities (incl. nullable `expectedAmountMinor`). IDs, relationships, exact Long minor units, timestamps, archived/inactive states are preserved exactly — verified by field-equality roundtrip tests.

**Checksum / integrity**: SHA-256 over the canonical payload bytes only (representation documented above; no circularity). Verification re-canonicalizes the received payload subtree and compares against the declared value (case-insensitive hex, 64 chars). Whitespace reformatting and key reordering still verify; any value tampering, checksum tampering, malformed checksum, or unsupported algorithm is rejected **before validation and before any restore** — never a partial restore. The checksum detects corruption/tampering; it is not authentication and not encryption.

**Export (SAF)**: Settings → "Export backup" → `ActivityResultContracts.CreateDocument("application/json")` → user chooses destination → repository serializes → `SafBackupFileIo` writes UTF-8. Success shows record counts, created timestamp, and the checksum; write failures surface as errors (never a false success). Empty database exports as an explicit empty backup (valid, all groups present, zero counts).

**Import / restore (SAF)**: Settings → "Restore backup" → `ActivityResultContracts.OpenDocument` (mime `application/json`, `text/plain`, `text/*`, `application/octet-stream`) → bounded read → intake pipeline (JSON → format → schema → checksum → records → refs) → **read-only preview** with per-group record counts → mandatory warning dialog showing `RESTORE_WARNING_TEXT` ("Restoring this backup will replace the current SHADOW MONEY financial records.") → explicit "Replace records" confirmation → atomic full replacement → success with counts, or an error explaining that nothing changed. No database write of any kind happens before the confirmation (preview-zero-writes verified by test).

**Validation order** (documented and tested): parse (INVALID_JSON, includes fractional-amount rejection) → format name/formatVersion (UNSUPPORTED_FORMAT) → `appSchemaVersion` vs installed schema read from the database (`INCOMPATIBLE_SCHEMA`) → checksum (CHECKSUM_MISMATCH) → record decoding (MALFORMED_RECORDS with field path) → semantic validation (MALFORMED_RECORDS / MISSING_REFERENCE) → preview.

**Atomicity**: the restore deletes and re-inserts every backup-scope table inside one Room transaction. A failure at any point (tested with a payload whose FK violation occurs mid-insert) rolls back to the exact prior state — row-for-row equality asserted.

**Existing data policy**: **full replacement**, never merge/additive. Pre-existing rows are deleted first; the restored database equals the backup exactly (no duplicates, no renamed leftovers). Restoring the same backup twice is idempotent. No second ledger and no backup tables exist (`sqlite_master` compared before/after restore).

**CSV import separation**: Phase 10 CSV import remains an additive, per-row flow (duplicates reviewed, skipped by default, `source = IMPORT_FILE`) that never deletes anything. Backup restore is the destructive full-replacement flow. Both write only the existing `transactions` table; neither has its own ledger. The UI keeps them in different screens with different confirmations.

**Security**: manifest declares **zero permissions** (SAF grants URI access per document); backup sources contain no network APIs, no logging/println of backup contents, no credential/password/OTP/CVV/token handling; file access uses only `ContentResolver` document streams (no `Environment`/external-storage APIs); 10 MB bound enforced on read **and** export; `.gitignore` keeps `local.properties`/`*.jks`/`*.keystore` out and no backup export files exist in the repository (regression-tested in `BackupSecurityTest`).

**Secret Target exclusion**: the Secret Target lives in DataStore, outside Room; backup DTOs are explicit field lists with no secret field; the checksum input is the payload subtree only (byte-identical checksums with/without a set secret); restore touches only Room tables (secret verified unchanged after full and empty restores); the string never appears in backup JSON (regression-tested in `BackupSecretTargetTest`).

**Encryption status**: **NOT implemented.** Backup files are plain JSON. The SHA-256 checksum provides integrity only, not confidentiality; users must protect exported files themselves. The UI states "Backups are plain JSON files; they are not encrypted." The checksum must never be described as encryption. Encryption at rest remains a **future hardening item** (re-reviewed in the post-Phase-13 hardening pass; intentionally not implemented now).

**UI**: Settings gains a "Backup & Restore" card: format/schema/integrity/plain-text notices, live record counts, "Export backup" / "Restore backup" buttons, progress states (Exporting/Preparing/Restoring), export success with checksum, the preview confirmation dialog with counts and the destructive warning, and explicit error states — each error names the cause (empty file, invalid JSON, unsupported format, incompatible schema, checksum mismatch, malformed records, missing references, oversized file, storage failure, restore rollback) and never claims success it didn't achieve. State machine: `Idle → Exporting → ExportSuccess|ExportError`, `Idle → Preparing → PreviewReady|PreviewInvalid`, `PreviewReady → Restoring → RestoreSuccess|RestoreError`; `confirmRestore` is a no-op unless the state is `PreviewReady`.

**Limitations (documented, by design)**: no encryption at rest for exports; no cloud/Drive/network sync or automatic scheduled backups; no partial/selected restore (full replacement only, no merge); no cross-version forward compatibility beyond rejecting unknown format versions; checksum is integrity verification, not authentication; 10 MB export bound (larger datasets must be split externally); restore always targets the whole backup scope (restoring a subset requires restoring then re-entering data manually).

## Phase 13 — Real Device Testing / Release Verification

Final phase: verification-only. No new features, no database schema change (still Room v7). All 27 phases were exercised end-to-end on an Android 16 (API 36, x86_64) emulator through ADB + `uiautomator dump` UI exploration; **no physical device was available** (documented as an honest limitation).

**Verified-blocker fixes** (all pre-existing issues found by device testing):
- `DashboardScreen` bottom bar: the Money and Settings `NavigationBarItem`s had `onClick = { }` (dead taps) — now navigate to `Screen.Money` / `Screen.Settings`.
- `MoneyPlaceholderScreen`: added a "View Transactions" button navigating to `Screen.Transactions` (the Transactions hub was previously unreachable from the UI); the Jobs bar item remains an intentionally unwired legacy placeholder — the Work screen (Phase 6) is the supported work tracker. (Post-Phase-13 hardening: this placeholder screen was **removed** — `Screen.Money` now renders the real `MoneyScreen`, and `Screen.Transactions` renders the manual-entry `TransactionsScreen`.)
- `SettingsScreen`: the version label was hardcoded `v0.1.0-phase1` — now renders `BuildConfig.VERSION_NAME` so it can never drift from `app/build.gradle.kts` (`versionName = "1.0.0"` since the post-Phase-13 hardening pass).

**Release signing**: `app/build.gradle.kts` defines a `release` signing config that reads an **external, machine-local** properties file (default `C:/Users/User/SHADOW-MONEY-KEYS/signing.properties`, overridable via `-DSHADOW_MONEY_SIGNING_PROPS` / env `SHADOW_MONEY_SIGNING_PROPS`) containing `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. The keystore and properties file live **outside the repository** (covered by `.gitignore` rules `*.jks` / `*.keystore` / `*.apk`), values are never logged, and a missing file falls back to an unsigned build instead of failing or embedding credentials. The release `buildType` uses `signingConfigs.findByName("release")`.

**Verified on-device**: navigation across all 13 routes; exact financial arithmetic against the synthetic fixture; intelligence labels and empty states; CSV import preview→discard (zero writes)→confirm→duplicates→malformed rejection; assistant answering all 14 tested topics with correct labels, Secret Target refusal, zero writes, and session-only history; export/restore with checksum round-trip and tampered-file rejection; Secret Target set/mask/reveal/edit/clear with absence from Dashboard, assistant and exports; persistence across force-stop/relaunch.

**Permissions/network**: merged manifest carries only four library-merged install-time permissions (`FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`), zero runtime permissions, no `INTERNET` permission, zero network APIs in source and zero network dependencies; logcat from the app PID shows no network activity.

## Post-Phase 13 Hardening - v1.0 Readiness

A verification-and-hardening pass (not a new phase; Phases 0-13 remain the complete phase list, no Phase 14). No architecture change, no financial-calculation change, **no database schema change (still Room v7)**.

- **Version**: `versionName = "1.0.0"` (was `0.1.0-phase2`); `versionCode = 2` unchanged; the Settings label keeps rendering `BuildConfig.VERSION_NAME` so it cannot drift.
- **Manual entry (Transactions)**: `TransactionsScreen` + `TransactionsViewModel` replace the former "Not Implemented Yet" hub with a working Add Transaction dialog - direction selection, Kathmandu-ISO date (defaults to today via `ImportDate`), exact amount parsing through `ImportAmount` (integer minor units, max 2 decimals, must be > 0), active-only account/category/work-item dropdowns, direction-conflicting category rejection ("Category does not match the selected direction"), optional trimmed note. Rows are written to the existing `transactions` table as **ordinary transactions** (`source = ""`, `externalRef = null`, never `IMPORT_FILE`), so balances, budgets, dashboard totals, intelligence and backups pick them up through the existing aggregates with no new ledger.
- **Accounts (Money)**: `MoneyScreen` + `MoneyViewModel` replace the removed `MoneyPlaceholderScreen` - account list with per-account balances (`Money.balanceMinor` over per-account income/outflow), create/edit dialog (trimmed name, type dropdown, exact opening balance parsed by `ImportAmount`, blank = 0), archive/restore toggle (archived accounts stay listed with balances but leave all active selection), and a top-bar jump to Transactions.
- **Assistant session**: `AssistantViewModel.submit(raw): Boolean` is an acceptance contract - blank input or an already-busy session is rejected before any state change, so a double-send can never duplicate the session; `clearHistory()` bumps a session generation so a late reply belonging to a cleared session is dropped instead of resurrecting stale content; `AssistantScreen` clears the input only when the submit was accepted. History stays session-local (bounded, never persisted) and the assistant stays read-only.
- **Backup/security re-review**: no backup-content logging/println anywhere in main source; Secret Target remains excluded from JSON, checksum input and restore (regression `BackupSecretTargetTest`); `.gitignore` extended with `signing.properties`; signing secrets stay outside the repository (property names only in gradle); **encryption at rest remains a documented future hardening item**.
- **Release config**: no credentials or keys in gradle/source (external properties file only, unsigned fallback preserved); no network/analytics/crash-reporting dependencies; `versionCode`/`versionName` as above; release APK `apksigner verify` -> Verifies, v2 scheme, signer CN=Prasbin Dhungana.
- **UI notes (documented, not "fixed")**: the Dashboard bottom navigation carries 8 items - dense but functional on-device; the Jobs bar item is an intentionally unwired legacy route superseded by the Work screen (Phase 6) and was left in place, not removed.
- **Verification**: 689 tests / 66 suites / 0 failures / 0 errors (33 new tests: `ManualEntryTest`, `MoneyViewModelTest`, `TransactionsViewModelTest`, assistant busy/generation regression); lint 0 errors / 27 warnings (the +1 warning is a newer-version advisory on the newly added `kotlinx-coroutines-test` dependency); `assembleDebug` + `assembleRelease` successful.

## v1.0 Release Candidate Audit

An audit pass (not a new phase; no architecture/financial-calculation change, **no schema change - still Room v7**), baseline `7651f07`:
- **Blocker found and fixed**: `MoneyViewModel` and `TransactionsViewModel` created private `CoroutineScope(SupervisorJob() + Dispatchers.Default)` for Room Flow collection but never overrode `onCleared()`, so collectors leaked every time the screen was destroyed - both now call `scope.cancel()` in `onCleared()` (the pattern already used by the other 11 ViewModels), each guarded by a `viewModelScope_isCancelledAfterViewModelStoreClear` regression test using `ViewModelStore.put`/`clear`.
- **On-device**: release APK (`com.prasbin.shadowmoney`, v1.0.0) first-installed and exercised on a physical Android 16 device (24094RAD4G): first-launch empty state, Goals/Secret-Target empty state, crash-free; full release-APK route-walk on the Android 16 emulator covering all 8 bottom-nav screens, Transactions, Assistant, Add Account dialog open/cancel and the Settings `SHADOW MONEY v1.0.0` label, with zero logcat crashes.
- **Verification**: 691 tests / 66 suites / 0 failures / 0 errors (+2 scope-cancellation regressions); lint 0 errors / 27 warnings; `assembleDebug` + `assembleRelease` successful; `apksigner verify` -> Verifies, v2 scheme, CN=Prasbin Dhungana.

## v1.1 Engineering Hardening

An engineering pass (not a phase; Phases 0-13 remain the complete list, no Phase 14). **No database schema change (still Room v7)**; `versionName` stays `1.0.0`, `versionCode` stays 2; no financial behavior changed.

- **Room schema artifacts (now version-controlled)**: `app/app/schemas/com.prasbin.shadowmoney.data.ShadowMoneyDatabase/` contains genuine Room/KSP-generated JSONs for **all versions 1-7**, tracked in git (the `.gitignore` `app/app/` exclusion was removed intentionally). Generation method: each version-bump commit (`d769c91`=v1, `9c8b835`=v2, `915335f`=v3, `c535501`=v4, `e3669d3`=v5, `d747cd3`=v6) was checked out in a disposable `git worktree`, built with the project's real KSP configuration (`room.schemaLocation` = `$projectDir/app/schemas`, unchanged since Phase 1), and the exported JSON copied back — v1's worktree had `exportSchema = false` flipped to `true` *only in the disposable checkout* (it was historically `false`, so no v1 JSON was ever produced; the entities were not touched). v7 was regenerated from HEAD and is byte-identical to the pre-existing artifact (no entity drift). Validation: every file parses, `database.version` matches its filename, identity hashes are unique per version, and tables evolve exactly along the migration path (v1 `placeholder` -> v2 four financial tables -> +budgets -> +work_items -> +telecom -> +opportunities -> v7 `externalRef`). Export configuration remains `exportSchema = true` + `ksp { arg("room.schemaLocation", ...) }`. A future entity change rewrites the current JSON during compilation, producing a **visible git diff** for review/CI. Migration tests remain independent (they build legacy databases with raw SQL, never reading these JSONs). The files contain only table/column/index/DDL structure — no user data, no credentials.
- **Legacy Jobs cleanup (removed)**: `presentation/screen/jobs/JobsScreen.kt` (the "Jobs — Not Implemented Yet" placeholder), the `object Jobs` route and its `composable(Screen.Jobs.route)` registration, and the NavHost import were deleted (-7 lines + 1 file). Pre-removal audit proved zero dependencies: no `navigate(Screen.Jobs)` anywhere, no tests, no manifest/deep-link references, bottom navigation never included it (Work superseded it in Phase 6). The `IntentClassifier` "jobs" keywords are assistant language mapped to the WORK intent and were deliberately left untouched; the Work screen, route, and database behavior are unchanged.
- **Transaction query bounding (documented, no behavior change)**: `TransactionDao.getAll()` is now KDocumented as a deliberate full-dataset operation. Production full-scan usage is exactly one place — `ImportRepository.loadReference()` fingerprinting during CSV preview, which must compare against every existing record for duplicate correctness (user-initiated, one-shot). All interactive paths stay bounded: Dashboard `getRecent(20)` + SQL aggregates; Transactions screen `getRecent(100)`; intelligence `getTransactionsInWindow` (800-day slice); assistant period aggregates/windows; budgets/summary sums are SQL `SUM/COUNT` (never row loads). Backup export deliberately uses its own dedicated full-table queries in query-only `BackupDao` (explicit full-data path, separate from `getAll()`). `TransactionRepository.getAll()` facade is retained for tests/facades; no screen may collect it (stated in the KDoc contract). No query was modified and no speculation-driven optimization was performed.
- **Backup encryption (DESIGN ONLY — NOT implemented)**: reviewed what an eventual encrypted-backup design must preserve: SAF-only I/O (no storage/network permissions), SHA-256 checksum integrity, atomic single-transaction full-replacement restore, total Secret Target exclusion (it lives outside Room and outside the payload, so encryption adds nothing there), byte-deterministic **unencrypted** backups (format v1 must keep working forever), and explicit user confirmation before restore — with **no credential/key storage** anywhere in the app. Sketch for a future v1.x/v2: opt-in passphrase-protected export producing a `formatVersion = 2` envelope whose `payload` becomes AES-256-GCM ciphertext (random per-export 16-byte salt + 12-byte IV, passphrase stretched with a memory-hard KDF or high-iteration PBKDF2, GCM tag for integrity+confidentiality); restore pipeline gains one gate — parse envelope -> detect v2 -> passphrase -> GCM decrypt -> then the *existing* canonical-payload checksum verification, validation, preview, warning and atomic restore run unchanged; format v1 plaintext backups remain accepted; export UI gains passphrase+confirm with an irrecoverable-loss warning (no recovery/escrow possible), restore gains a passphrase prompt; wrong passphrase and tampered ciphertext both surface as one honest GCM failure message; salt/IV randomness means encrypted exports are intentionally NOT byte-identical (documented divergence from plaintext determinism); zero logging of passphrase/ciphertext; the 10 MB bound applies to the encrypted file. Future work requires the full test matrix (roundtrip, wrong-passphrase, tamper, v1-plaintext compatibility, determinism note, Secret Target absence, atomicity, no-credentials-in-logs) before any cryptography ships. **This pass introduces no cryptography, no new format version, and no code changes to backup/restore.**

### Dependencies

Same versions as SHADOW LEARN project for consistency:
- AGP 8.13.2
- Kotlin 2.1.20
- Compose BOM 2026.04.01
- Room 2.8.3
- Navigation Compose 2.7.7
- DataStore Preferences 1.1.1
- WorkManager 2.9.0
- kotlinx-coroutines-core 1.7.3

### Security

- No secrets in source
- No API keys
- No analytics SDK
- Local-only data
- Exact integer monetary representation
- No floating-point arithmetic
- Assistant is read-only, offline, and deterministic; no `SecretTargetStore` access and no writes (Phase 11)
- Backup/restore is SAF-only with zero manifest permissions, no network, no logging, a 10 MB bound, atomic full-replacement restore, plain-JSON (unencrypted) exports, and total Secret Target exclusion (Phase 12)
- Release signing uses an external keystore + properties file outside the repository; never committed, never logged; missing file degrades to an unsigned build (Phase 13)
- No `INTERNET` permission, no runtime permissions, no network APIs or network dependencies anywhere in the app (verified on-device, Phase 13)


## Real Money Connection Foundation (Room v7 → v8)

**Purpose**: make the app ready for genuine bank/wallet connections without fabricating anything. Official-source research (checked 2026-10-03, `docs/REAL_MONEY_CONNECTIONS.md`) found no official public consumer API for Sanima Sajilo eBanking or Global IME Global Smart Plus (`NOT AVAILABLE THROUGH OFFICIAL PUBLIC CONSUMER API`), and eSewa's official developer docs (`developer.esewa.com.np`) cover the merchant/partner payment category only (`CONSUMER WALLET DATA SYNC NOT AVAILABLE`). Therefore **no production provider adapter exists**: `ConnectionSyncCoordinator.PRODUCTION_ADAPTERS` is an empty map by design, every provider renders `UNAVAILABLE` with the research-backed reason, and there are no fake Connect buttons, demo balances, or invented endpoints anywhere.

**Source-of-truth hierarchy**: `CONNECTED_VERIFIED` data (future adapters only) outranks everything; `ManualEntryTest`/CSV `IMPORTED` records keep their own provenance tags (shown as `· MANUAL ENTRY` / `· IMPORTED` on Activity date lines) and are **never folded into** a combined "actual balance". `ActualMoney.unifiedActualMoney()` sums only connected-verified, timestamped, non-stale sources — with no sources it reports `NOT AVAILABLE`/`null`, never an invented zero. `BaselineCalculator` refuses to establish a baseline without at least one fresh connected source (explicit `Refused` reasons). `DiscrepancyEngine` is a pure state machine with fixed priority: `NO_CONNECTED_SOURCES` → `CONNECTION_ERROR` → `REAUTH_REQUIRED` → `CONNECTION_STALE` → `NO_BASELINE` → `EXPECTED_CHANGE` / `UNEXPLAINED_REDUCTION` (unrecorded outflow) / `ACTUAL_DISCREPANCY`.

**Schema v8**: `ShadowMoneyDatabase` version 7 → 8 adds exactly two entities — `FinancialConnectionEntity` (`financial_connections`, unique index per provider) and `BalanceBaselineEntity` (`balance_baselines`) — via purely additive `MIGRATION_7_8` (two `CREATE TABLE IF NOT EXISTS` + two `CREATE UNIQUE INDEX IF NOT EXISTS` statements; no existing table touched; chained onto `MIGRATION_6_7` in main). KSP exports `app/app/schemas/…/8.json` (committed). Backup `APP_SCHEMA_VERSION` follows the Room version to 8 so restore validation stays truthful. Verified by `ConnectionMigrationTest` (genuinely populated v7 database built with raw SQL → actual `MIGRATION_7_8` → all prior data preserved, both new tables present, connection/baseline round-trip, unique provider enforced) plus all six pre-existing migration tests re-chained through `MIGRATION_7_8`.

**Security boundaries (unchanged)**: no `INTERNET` permission, no credential/PIN/OTP/token storage (connection rows hold only provider/status/capability/snapshot metadata), no scraping/MFA bypass/Accessibility harvesting, app remains offline local-first — verified on-device (`dumpsys package` shows no `INTERNET`; Settings copy unchanged).

**UI**: `System → Connections` (`ConnectionsScreen`/`ConnectionsViewModel`) shows honest provider cards (availability reason, capability rows, approval requirements), the `ACTUAL MONEY` / `BASELINE` / `DISCREPANCY` / `PROVENANCE` panels, real refusal messages (e.g., baseline button → "Baseline requires at least one connected, verified source…"), and a security footer. Verified on Android 16 emulator (physical device not connected this session).

**Verification**: 759 tests / 72 suites / 0 failures / 0 errors (55 new: connection architecture, actual money, discrepancy, sync coordinator, migration); lint 0 errors / 27 warnings; `assembleDebug` + `assembleRelease` OK; `apksigner verify` → v2, CN=Prasbin Dhungana. Also fixed a pre-existing month-rollover time bomb in `BudgetsViewModelTest` (hardcoded `monthKey = "2026-09"` → `BudgetCalendar.currentMonthKey()`).

## Real Money Reconciliation & Provider Data Activation (Room v8 → v9)

**Purpose**: turn the connection foundation into an auditable reconciliation loop. The original balance (baseline), the current verified reading, and the recorded activity between them must add up — or the app must say exactly what cannot be explained, per provenance.

**Reconciliation model**: `DiscrepancyEngine` now computes against the full arithmetic instead of a single delta. Inputs carry `verifiedNetChangeMinor` (connected-verified ledger activity), `importedNetChangeMinor` and `manualNetChangeMinor` (user-provided activity, tracked separately — imported numbers never become verified because they fit), plus `affectedProviders`. Results carry `originalMinor` / `currentMinor` / `differenceMinor` / `explainedMovementMinor` / `unexplainedMinor` / `belowOriginalBalance` / `isExplained`. Residual = current − (original + explained movement): zero → `EXPECTED_CHANGE` (with `belowOriginalBalance` flagged whenever current < original, surfaced in the UI as `MONEY BELOW ORIGINAL BALANCE`), negative → `UNEXPLAINED_REDUCTION`, positive → `ACTUAL_DISCREPANCY`. Enum order/priority unchanged.

**Ledger activity source**: `ReconciliationActivity` (fun interface) + production `LedgerReconciliationActivity` backed by a new bounded SQL aggregate in `TransactionDao.netChangeBySourceSince` (`SUM(CASE WHEN direction = 0 THEN amountMinor ELSE -amountMinor END) GROUP BY source`, time-windowed — one aggregate, no row loading; query addition only, no schema change). Rows map to provenance with `provenanceOfTransactionSource`, so `""` → manual, `CONNECTED` → verified, `IMPORT_FILE`/anything else → imported. `ConnectionsViewModel` combines connection/baseline/message flows with `flatMapLatest` and only queries activity when a baseline exists (window starts at the baseline's `setAtMs`).

**Baseline audit trail**: `BalanceBaselineEntity` gains two nullable columns — `sourceVerifiedAtMs` (the verified reading time behind the row) and `sourceSet` (the complete source set used, enum-name sorted) — via purely additive `MIGRATION_8_9` (two `ALTER TABLE … ADD COLUMN`; legacy rows read NULL). `BaselineCalculator.setFromConnectedSources` fills both; new `BaselineCalculator.summarize` deterministically reproduces the **original balance** as the sorted-row sum with the shared `setAtMs` — fixing the previous `baselines.firstOrNull()?.baselineMinor` bug that treated the first provider's row as the whole original balance. `ConnectionRepository.setBaselines` now delegates to `ConnectionDao.replaceBaselines` (`@Transaction` clear + insert), fixing the stale-row bug where upserting a smaller set left the previous set's rows inflating the sum.

**Actual-money tri-state**: `MoneyVerificationState { FULLY_VERIFIED, PARTIALLY_VERIFIED, NOT_AVAILABLE }` on `ActualMoneyView` (plus `failedSourceCount`). No connected total → `NOT_AVAILABLE` (total null, never zero); any stale/missing-timestamp/failed source → `PARTIALLY_VERIFIED`; failed sources are excluded from the total, counted, and named in the reason ("never counted as zero"). `isFullyVerified` remains derived for compatibility.

**Provider activation (honest)**: `ProviderCatalog` entries gain `dataSourceType` (eSewa: `MERCHANT API — NOT A PERSONAL WALLET SYNC INTERFACE`; Sanima/Global IME: no official consumer data source) and `safeNextAction`, plus a `supportedCapabilitiesLabel()`. Provider cards show these, the stored `lastVerifiedAtMs` (`Never — no official consumer interface` when null), and no fabricated connection status. `PRODUCTION_ADAPTERS` stays empty; no new network permission.

**UI**: `ConnectionsScreen` reconciliation panel — actual-money state chip, reconciliation status chip (incl. `MONEY BELOW ORIGINAL BALANCE` amber when explained, `UNEXPLAINED REDUCTION` red when not), original/current/difference/explained/unexplained/affected-source figures, explanation, connection-health counts; baseline panel shows `NO VERIFIED BASELINE AVAILABLE` or `ORIGINAL BALANCE` + source set + Kathmandu-local set time + per-row audit; timestamps formatted `yyyy-MM-dd HH:mm` (`Asia/Kathmandu`).

**Schema v9**: `ShadowMoneyDatabase` version 8 → 9 via additive `MIGRATION_8_9`; chained onto `MIGRATION_7_8` in main; KSP exports `app/app/schemas/…/9.json` (committed); backup `APP_SCHEMA_VERSION` follows to 9; all pre-existing migration tests re-chained through `MIGRATION_8_9`.

**Verification**: 781 tests / 74 suites / 0 failures / 0 errors (22 new: `BaselineSummaryTest`, `LedgerReconciliationActivityTest` + extensions to engine/actual-money/coordinator/catalog suites; populated v8 → v9 migration test); lint 0 errors / 27 warnings; `assembleDebug` + `assembleRelease` OK; `apksigner verify` → v2, CN=Prasbin Dhungana.
