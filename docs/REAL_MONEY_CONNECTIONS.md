# Real-Money Connections — Foundation

Phase documentation for the connection architecture: what official integrations
actually exist for the target providers, what was built, and what was deliberately
not built. Status: **foundation complete, no production connection available** —
this is an honest limitation of the providers, not of the code.

## 1. Provider research (official sources, checked 2026-10-03)

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
| `ActualMoney.kt` | Unified actual-money view with partial/stale marking |
| `BaselineCalculator.kt` | Baseline establishment — refuses without fresh connected sources |
| `DiscrepancyEngine.kt` | Pure state machine: `NO_CONNECTED_SOURCES`, `CONNECTION_ERROR`, `REAUTH_REQUIRED`, `CONNECTION_STALE`, `NO_BASELINE`, `EXPECTED_CHANGE`, `UNEXPLAINED_REDUCTION`, `ACTUAL_DISCREPANCY` |
| `data/ConnectionDao.kt`, `data/model/FinancialConnection.kt` | Room DAO + entities (`financial_connections`, `balance_baselines`) |

Database: **Room schema v8** — additive `MIGRATION_7_8` creates only the two new
tables; no existing table, column, or row changes. Migration verified by a genuine
populated v7 → v8 test.

UI: `Screen.Connections` (`connections` route) — a SYSTEM → Connections row opens
the Connections screen with: provider cards (status chip + interface/balance/
transaction/payment/auth/approval facts + honest note), actual-money panel,
baseline panel (`SET BASELINE FROM CONNECTED SOURCES` — refuses honestly while no
connected source exists), discrepancy panel, provenance-tag panel, and a security
footer. The Activity list now labels each record's provenance (`MANUAL ENTRY` /
`IMPORTED`) on its date line.

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
- `ConnectionMigrationTest` — populated v7 → v8: all data preserved, new tables
  exist, connection/baseline round-trip, unique provider enforced.
- Existing migration tests chained through `MIGRATION_7_8`.

## 6. Future path (when a provider publishes an official consumer API)

1. Implement `ProviderAdapter` for that provider using only its official,
   approved interface.
2. Register it in the coordinator's adapter map (production map is currently empty
   by design).
3. Nothing else changes: sync → snapshot → baseline → discrepancy already work.

Until then, every provider honestly reports `UNAVAILABLE` with the reason from the
research table above.

## 7. Verification (2026-10-03)

- Tests: **759 / 72 suites / 0 failures / 0 errors** (55 new across the five
  connection suites listed in §5; `BudgetsViewModelTest` also fixed — its hardcoded
  `monthKey = "2026-09"` was a month-rollover time bomb, now
  `BudgetCalendar.currentMonthKey()`).
- Lint: 0 errors / 27 warnings (unchanged). `assembleDebug` + `assembleRelease` OK;
  `apksigner verify` → v2 scheme, CN=Prasbin Dhungana.
- Physical device (YPA6RWNB7L7HPBRK) was disconnected this session; verification was
  performed on the Android 16 emulator instead: cold start, all five tabs, Settings →
  Connections (provider cards + all panels + baseline refusal message), `· MANUAL ENTRY`
  label on a recorded transaction, no crashes (logcat clean), no `INTERNET` permission
  (`dumpsys package`), test data cleared afterwards (`pm clear` → pristine first-run
  state restored).
