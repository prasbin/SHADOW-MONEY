# SHADOW MONEY — Architecture Documentation

## Phase 2 — Financial Data Model

### Overview

SHADOW MONEY is a personal Android financial-management application. Phase 2 implements the foundational financial data model.

### Package Structure

```
com.prasbin.shadowmoney
├── data                    # Room entities, DAOs, repositories, database
│   ├── model               # Account, Category, Transaction, Goal entities
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
