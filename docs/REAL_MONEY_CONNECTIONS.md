# Real-Money Connections — Foundation & Reconciliation

Phase documentation for the connection architecture: what official integrations
actually exist for the target providers, what was built, and what was deliberately
not built. Status: **foundation complete, reconciliation loop complete, no
production connection available** — this is an honest limitation of the providers,
not of the code.

## 1. Provider research (official sources, checked 2026-10-03)

Re-verified and extended by the Live Financial Connectivity Options
Investigation (research date 2026-10-03) — capability matrix, aggregator
screening, NRB/regulatory routes, ranked paths and the PARTNER APPLICATION
REQUIRED decision are in `docs/LIVE_CONNECTIVITY_RESEARCH.md`.

| Provider | Official public consumer API? | Balance read | Transaction read | Payment/transfer initiate | Verdict |
|---|---|---|---|---|---|
| **Sanima — Sajilo eBanking** | No | Not available | Not available | Not available | `NOT AVAILABLE THROUGH OFFICIAL PUBLIC CONSUMER API` |
| **Global IME — Global Smart Plus** | No | Not available | Not available | Not available | `NOT AVAILABLE THROUGH OFFICIAL PUBLIC CONSUMER API` |
| **eSewa** | Yes, but merchant/partner only | Not available | Merchant payment-status only | Yes, for approved merchants | `OFFICIAL API EXISTS — MERCHANT PAYMENT CATEGORY; CONSUMER WALLET DATA SYNC NOT AVAILABLE` |

### Sanima (Sanima Bank Limited)
- Customer portals only: `sajiloebanking.sanimabank.com` (internet banking),
  Sanima mobile app, `onlineservices.sanimabank.com` (statement/balance-certificate
  service forms — not a data API).
- No developer portal, no account-data API, no open-banking developer program found.
- Cross-border payment APIs exist only as bank-to-bank partnerships (e.g. the
  Standard Chartered real-time API payment service) — institutional, not consumer.
- **What would be required:** a direct bank-level partnership/agreement with Sanima
  (and compliance with Nepal Rastra Bank regulations for third-party data access).
  There is no self-serve signup.

### Global IME (Global IME Bank Limited)
- Customer app/web only: **Global Smart Plus** omni-channel banking
  (Android package `com.swifttechnology.globalsmart`, built by Swift Technology).
- The bank's own app already includes personal finance management (Budget360°) —
  but nothing is exposed to third-party developers.
- No developer portal or account-data API found.
- **What would be required:** partnership with Global IME or its app vendor
  (Swift Technology), under NRB-compliant terms.

### eSewa (eSewa Digital Pvt. Ltd.)
- Official public developer documentation exists at `developer.esewa.com.np`.
- The documentation states its intent explicitly: *"intended for partners merchant
  seeking to integrate and transact with the eSewa"* — flows are ePay (redirect),
  intent payment, token payment, and transaction status checks.
- Auth model: merchant credentials (merchant ID/secret, Basic/Bearer token).
- **No consumer wallet balance API. No consumer statement/history API.** The status
  API answers "did this merchant-bound payment complete", not "what is in my wallet".
- Users can export their own statement from the eSewa app — that lands in the app
  as **IMPORTED** (user-provided) data through the existing CSV import path.
- **What would be required for payment initiation:** eSewa merchant/partner
  onboarding. For wallet data sync: no such official interface is published.

### Regulatory context (Nepal)
- Nepal Rastra Bank regulates payment systems (Integrated Payment Systems
  Directive; a regulatory sandbox for fintechs has been effective since May 2026).
- There is **no mandated consumer open-banking data API** that an app can simply
  plug into. Global aggregators (Plaid, Tink, TrueLayer) do not cover Nepal.

## 2. Source-of-truth hierarchy

```
CONNECTED VERIFIED DATA        ← highest authority (official connected reads)
        ↓
NORMALIZED FINANCIAL SOURCE    ← provenance-tagged, never blended across tags
        ↓
UNIFIED ACTUAL MONEY VIEW      ← sums CONNECTED VERIFIED only
        ↓
INTELLIGENCE / DISCREPANCY     ← compared against baseline + recorded activity
```

Rules enforced in code:
- Only `Provenance.CONNECTED_VERIFIED` sources enter
  `ActualMoney.unifiedActualMoney()`'s total. `MANUAL_ENTRY` and `IMPORTED`
  sources are excluded by construction — manual + connected is **never** merged
  into a third stored "actual balance".
- A total is `null` (shown as `NOT AVAILABLE`) whenever nothing verified exists —
  no zero is invented to look like data.
- Stale readings (older than 15 minutes) mark the view `PARTIALLY VERIFIED`
  instead of silently passing as fresh.

## 3. What was built

New package `data/connections/` (plus one DAO and two Room entities):

| File | Role |
|---|---|
| `ConnectionModels.kt` | `Provider`, `ConnectionStatus`, `ConnectionCapability`, `Provenance`, `FinancialConnection`, `VerifiedFinancialSnapshot`, `NormalizedFinancialSource`, `BalanceBaseline`, provenance mapping/labels |
| `ProviderAdapter.kt` | Read-only interface real integrations implement; `ProviderReadResult` (Verified/Unavailable/AuthRequired/Failed). **No production implementation exists.** |
| `ProviderCatalog.kt` | Research-backed availability facts per provider (the honest status text shown in the UI) |
| `ConnectionRepository.kt` | Persistence for connection metadata + baselines; stable per-provider row id (no duplicate sources) |
| `ConnectionSyncCoordinator.kt` | Catalog bootstrap, adapter sync, staleness downgrade, connected-source projection. Production adapter map is empty |
| `ActualMoney.kt` | Unified actual-money view — tri-state verification (`FULLY_VERIFIED` / `PARTIALLY_VERIFIED` / `NOT_AVAILABLE`), failed sources excluded and counted (never zero) |
| `BaselineCalculator.kt` | Baseline establishment — refuses without fresh connected sources; per-row audit trail (`sourceVerifiedAtMs`, `sourceSet`); `BaselineCalculator.summarize` deterministically reproduces the original balance |
| `DiscrepancyEngine.kt` | Pure reconciliation engine: `NO_CONNECTED_SOURCES`, `CONNECTION_ERROR`, `REAUTH_REQUIRED`, `CONNECTION_STALE`, `NO_BASELINE`, `EXPECTED_CHANGE`, `UNEXPLAINED_REDUCTION`, `ACTUAL_DISCREPANCY`, with original/current/difference/explained/unexplained figures and affected sources |
| `ReconciliationActivity.kt` | `LedgerReconciliationActivity` — bounded ledger aggregate per source since the baseline, split into verified / imported / manual net movement |
| `data/ConnectionDao.kt`, `data/model/FinancialConnection.kt` | Room DAO (incl. atomic `replaceBaselines`) + entities (`financial_connections`, `balance_baselines` with nullable audit columns) |

Database: **Room schema v9** — additive `MIGRATION_8_9` adds two nullable audit
columns to `balance_baselines` (`sourceVerifiedAtMs`, `sourceSet`); no existing
table, column, or row changes (legacy rows simply read NULL). The stored baseline
set is now replaced atomically (`ConnectionDao.replaceBaselines`, `@Transaction`),
so a stale row from a previous set can never inflate the original balance.
Migration verified by a genuine populated v8 → v9 test (legacy rows preserved with
NULL audit columns, new rows round-trip audit fields).

UI: `Screen.Connections` (`connections` route) — a SYSTEM → Connections row opens
the Connections screen with: provider cards (status chip + interface / data-source
type / balance / transaction / payment / auth / approval facts + supported
capabilities + last verification/sync timestamp + honest note + safe next step —
eSewa states `MERCHANT API — NOT A PERSONAL WALLET SYNC INTERFACE`), actual-money
panel (tri-state chip, fresh/stale/unverified/failed counts), baseline panel
(`NO VERIFIED BASELINE AVAILABLE` or original balance + source set + per-row audit,
`SET BASELINE FROM CONNECTED SOURCES` refusing honestly while no connected source
exists), a reconciliation panel (actual-money state chip, status chip incl.
`MONEY BELOW ORIGINAL BALANCE` / `UNEXPLAINED REDUCTION`, original/current/
difference/explained/unexplained figures, affected sources, explanation,
connection-health counts), provenance-tag panel, and a security footer. The
Activity list now labels each record's provenance (`MANUAL ENTRY` / `IMPORTED`)
on its date line.

## 4. Security boundaries (unchanged, by design)

- No network permission is used or needed; the app remains offline/local-first.
- No banking passwords, PINs, OTPs, tokens, sessions, or provider account
  identifiers are ever requested or stored (the connection tables hold only
  provider/status/capability/snapshot metadata).
- No scraping, no reverse engineering, no MFA bypass, no notification interference,
  no Accessibility-service harvesting.
- No fake "Connect" buttons, no demo balances, no invented API endpoints.

## 5. Tests

- `ConnectionArchitectureTest` — status/capability model, catalog honesty for all
  three providers, provenance mapping (manual/imported/connected never collide).
- `ActualMoneyTest` — combined verified balance, manual/imported exclusion,
  stale and partial verification, no-invented-total, threshold boundary.
- `DiscrepancyEngineTest` — every state incl. explained vs unexplained reduction,
  unrecorded inflow, priority order.
- `ConnectionSyncCoordinatorTest` — fake adapters (test-only): connected status,
  capabilities, failed/auth/unavailable handling, catalog honesty, duplicate-row
  prevention, staleness refresh, baseline establish + all refusal paths, end-to-end
  discrepancy pipeline.
- `ConnectionMigrationTest` — populated v7 → v8 (data preserved, new tables,
  round-trip, unique provider) and populated v8 → v9 (baseline rows preserved,
  nullable audit columns added, audit fields round-trip).
- `BaselineSummaryTest` — per-row audit fields (`sourceVerifiedAtMs`, `sourceSet`)
  and deterministic `BaselineCalculator.summarize` (original balance = sum of rows,
  order-independent, legacy-row fallback).
- `LedgerReconciliationActivityTest` — provenance-split ledger aggregation over the
  bounded SQL group-by query; verified/imported/manual net movement kept separate;
  rows before the window excluded; unknown source strings treated as imported.
- `ActualMoneyTest` additionally covers the tri-state verification states and
  failed-source counting (excluded, never counted as zero).
- `DiscrepancyEngineTest` additionally covers reconciliation figures (original /
  current / difference / explained / unexplained), explained reductions flagged
  below the original balance, provenance-split movement, and affected sources.
- Existing migration tests chained through `MIGRATION_7_8` and `MIGRATION_8_9`.

## 6. Import as a first-class source (evidence stance)

- **SUPPORTED — generic CSV import**: the app's format-agnostic CSV importer
  accepts user-provided files with a header row and standard columns (date,
  amount, direction, description). It assumes **no per-provider format**: nothing
  about Sanima/Global IME/eSewa statement layouts is encoded anywhere, because no
  provider export format was ever reverse-engineered or claimed. Imported rows are
  tagged `IMPORTED` (`source = "IMPORT_FILE"`) at the row level and feed the
  reconciliation loop only through `importedNetChangeMinor` — visible as explained
  movement, never as verified data.
- **NOT SUPPORTED — PDF statements**: there is no PDF parser; bank/wallet PDF
  statements cannot be imported. This is a documented limitation, not a silent
  failure — no partial or guessed parsing exists.
- **Provenance is never upgraded**: an imported row stays `IMPORTED` forever, no
  matter how consistent its numbers are with a connected baseline. Import evidence
  explains movement; it never makes a source "connected" or a baseline "verified".

## 7. Future path (when a provider publishes an official consumer API)

1. Implement `ProviderAdapter` for that provider using only its official,
   approved interface.
2. Register it in the coordinator's adapter map (production map is currently empty
   by design).
3. Nothing else changes: sync → snapshot → baseline → discrepancy already work.

Until then, every provider honestly reports `UNAVAILABLE` with the reason from the
research table above.

## 8. Verification (2026-10-03)

Foundation phase (previous session):
- Tests: **759 / 72 suites / 0 failures / 0 errors** (55 new across the five
  connection suites listed in §5; `BudgetsViewModelTest` also fixed — its hardcoded
  `monthKey = "2026-09"` was a month-rollover time bomb, now
  `BudgetCalendar.currentMonthKey()`).

Reconciliation phase (this session):
- Tests: **781 / 74 suites / 0 failures / 0 errors** (22 new: `BaselineSummaryTest`
  and `LedgerReconciliationActivityTest` suites plus extensions to
  `DiscrepancyEngineTest`, `ActualMoneyTest`, `ConnectionSyncCoordinatorTest`,
  `ConnectionArchitectureTest`; populated v8 → v9 migration test added to
  `ConnectionMigrationTest`; all six pre-existing migration tests re-chained through
  `MIGRATION_8_9`).
- Lint: 0 errors / 27 warnings (unchanged). `assembleDebug` + `assembleRelease` OK;
  `apksigner verify` → v2 scheme, CN=Prasbin Dhungana. KSP schema `9.json` committed.
- Physical device (YPA6RWNB7L7HPBRK) remained disconnected this session; verification
  on the Android 16 emulator (fresh install, `pm clear` first): Connections screen —
  enriched provider cards (`DATA SOURCE TYPE`, eSewa
  `MERCHANT API — NOT A PERSONAL WALLET SYNC INTERFACE`, `LAST VERIFICATION/SYNC:
  Never — no official consumer interface`, supported-capabilities line, `Next step:`
  safe action), `ACTUAL MONEY NOT AVAILABLE` tri-state chip, baseline panel
  `NO VERIFIED BASELINE AVAILABLE` + honest refusal message ("…official consumer data
  connections are not currently available"), full `RECONCILIATION` panel (status
  chips, `ORIGINAL BALANCE: NO VERIFIED BASELINE AVAILABLE`, explanation,
  `CONNECTION HEALTH 0 connected · 0 stale · 0 error · 0 reauth`), provenance panel
  + security footer, `Settings → Backup & Restore → Database schema: v9` (now derived
  from `APP_SCHEMA_VERSION` instead of a hardcoded string), all five tabs in pristine
  empty state, no crashes (logcat clean), no `INTERNET` permission
  (`dumpsys package`), test data cleared afterwards (`pm clear` → pristine first-run
  state restored).
