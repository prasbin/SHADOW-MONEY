# SHADOW MONEY — Roadmap

## Current Status: Phase 11 — Local Financial Assistant ✅ COMPLETE

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
- 539 passing unit/database/migration/dashboard/intelligence/budget/work/goal/secret-target/telecom/opportunity/import/security/assistant tests

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
