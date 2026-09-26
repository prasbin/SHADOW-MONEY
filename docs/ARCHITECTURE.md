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

- Accounts: Archive/inactive only (no cascade-delete with transactions)
- Categories: SET_NULL on transactions if deleted; historical data preserved
- Transactions: Retain historical record; no cascade-delete
- Goals: Independent; archive/inactive only

### Migration (Phase 1 → Phase 2)

`MIGRATION_1_2` drops the `placeholder` table and creates all four entity tables. Tested via `MigrationTest`.

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
