# SHADOW MONEY — Roadmap

## Current Status: Phase 7 — Goals + Secret Target ✅ COMPLETE

- **Goals**: dedicated Goals screen (list / create / edit / archive / delete) reusing the Phase 2 `goals` table; progress derived from the linked account's authoritative balance.
- **Secret Target**: private personal target in app-private DataStore (Long minor units), masked by default with explicit reveal, updatable at any time (no monthly lock), excluded from Dashboard/goals/budgets/work/intelligence/logs/backups. Not encrypted (app-sandbox only) — limitation documented.

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
- 219 passing unit/database/migration/dashboard/intelligence/budget/work/goal/secret-target tests

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
| 8 | Telecom Tracker | ⬜ Planned |
| 9 | Opportunity Intelligence | ⬜ Planned |
| 10 | CSV Import | ⬜ Planned |
| 11 | Local Financial Assistant | ⬜ Planned |
| 12 | Security / Backup / Restore | ⬜ Planned |
| 13 | Real Device Testing / Release | ⬜ Planned |

## Financial Objective

Help the user work toward consistently earning at least **NPR 100,000/month**.

## Key Principles

- Local-first
- Financial truth (no fabricated data)
- Privacy and security
- Exact integer monetary representation (Long/paisa)
- Deterministic calculations
- Dark futuristic "System" aesthetic
