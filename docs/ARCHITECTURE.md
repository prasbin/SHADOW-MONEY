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
