# Real-Money Connections — Foundation, Reconciliation & Statement Ingestion

Phase documentation for the connection architecture: what official integrations
actually exist for the target providers, what was built, and what was deliberately
not built. Status: **foundation complete, reconciliation loop complete, real
statement ingestion complete (CSV + local PDF → `IMPORTED / USER-PROVIDED`
evidence), no production connection available** — this is an honest limitation of
the providers, not of the code.

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
  as **IMPORTED** (user-provided) data through the statement ingestion path (CSV
  or local PDF file, source confirmed by the user).
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

Database is now **Room schema v10** — additive `MIGRATION_9_10` creates
`imported_statements` (statement evidence: document name, detected/confirmed
source, SHA-256, format, period, counts, money in/out, document-reported balance,
notes, timestamps) and adds a nullable `statementId` column to `transactions` (no
FK, no index change to existing tables). Legacy rows keep `statementId = NULL`.
Backup `APP_SCHEMA_VERSION` follows to 10 (with `imported_statements` excluded
from the payload and cleared on restore — see §6). Verified by a genuine populated
v9 → v10 migration test plus all pre-existing migration suites re-chained.

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

## 6. Real statement ingestion (evidence stance)

Statement files the user provides are a first-class real-data path: pick a file →
detect source → parse → read-only preview → duplicate check → confirm → ordinary
transactions with `IMPORTED / USER-PROVIDED` provenance feeding reconciliation and
intelligence. This is **PATH 1** from the ranked-paths research (user-mediated
export → local import). No adapter, no endpoint, no credentials, no network.

### Architecture (new package `data/statements/` + import-layer extensions)

| File | Role |
|---|---|
| `StatementModels.kt` | `StatementSource` (`SANIMA` / `GLOBAL_IME` / `ESEWA` / `UNKNOWN`), `StatementFormat` (`CSV` / `PDF`), `StatementImportContext` (document name, source, SHA-256, format, user confirmation note, preview-level row/invalid/duplicate counts), `StatementFailureReason` (incl. `ENCRYPTED_PDF`, `NO_TEXT_EXTRACTED`, `AMBIGUOUS_COLUMNS`) |
| `StatementSourceDetector.kt` | Content-marker suggestion over the **first 8192 chars only** (lowercased): sanima / global ime|globalime|global smart / esewa|e-sewa; exactly one marker group → that source, 0 or >1 → `UNKNOWN`. **The filename is never an input.** |
| `StatementParser.kt` | `CsvStatementParser` + `StatementParserRegistry` — universal parse into a `CsvDocument` that flows through the **untouched** `ImportEngine`/`ImportRepository` validation and duplicate pipeline (one ruleset, no second engine) |
| `PdfTextExtractor.kt` | Bounded text extraction: 5 MB input, 8192 content streams, 4M chars; `/Encrypt` → `ENCRYPTED_PDF` (safe failure, message contains "never asks" — never prompts for banking passwords); FlateDecode (zlib then raw), image codecs skipped (image-only → `NO_TEXT_EXTRACTED`), text-quality ratio ≥ 0.85 else rejected (binary garbage never becomes rows), UTF-16BE ToUnicode CMap applied only when conflict-free, **no OCR** |
| `PdfStatementTable.kt` | Table reconstruction: header must carry date + description + (debit | credit | amount) — missing → `AMBIGUOUS_COLUMNS`, never guessed; same-line `Td`/`TD`/`Tm` x-moves emit tabs so empty cells are preserved; `Tj` never breaks a line (only `'`/`"` do); TJ kerning gap ≤ -100 → space; tab rows right-padded to the header count, space rows must match exactly; direction from the document (debit → OUTFLOW, credit → INCOME, signed ±, unsigned → empty → visibly invalid); unparsed lines kept for review, capped at 500; repeated headers → note |
| `StatementSummary.kt` | Preview-level summary (period from valid rows, money in/out from importable rows, document-reported ending balance or `null` — never zero-invented) + `StatementAgeClassifier` (`RECENT` ≤ 31 days from `periodEndMs ?: importedAtMs`, else `OLD`, with "period ended N day(s) ago" detail) |

Import layer: `ImportSchema.ImportColumn.BALANCE` + `ImportRow.rawBalance`/
`balanceMinor` (malformed balance cell rejects the row visibly — never guessed);
`ImportEngine.buildPreview(..., defaultAccountId)` assigns accountless PDF rows
only to the user-chosen per-import default; `ImportRepository.importSelected` kept
for the paste flow and `importSelectedWithStatement(rows, StatementImportContext?)`
records `imported_statements` + per-row `statementId` (counts come from
**preview-level** metadata, never from the selected-row subset); CSV input larger
than the bound is refused via `CsvStreamReader.readBytesBounded` (`TooLarge` vs
`ReadError` — the paste path's semantics unchanged).

### Flow

1. **Entry**: `IMPORT REAL STATEMENT` (Money / Dashboard / Transactions / Settings),
   SAF `OpenDocument` with `text/*` + `application/pdf` mime types (plus paste-CSV,
   unchanged). Display name from `OpenableColumns`, else fallback.
2. **Detect**: PDF magic (`%PDF`) → PDF pipeline, else CSV parser. Detection result
   shown as a **suggestion** on a dedicated source-confirm step (radio rows for
   Sanima / Global IME / eSewa / Unknown + evidence line + unparsed-line preview).
   Nothing writes until the user confirms the source.
3. **Preview**: read-only rows with row states (`NEW` / `POSSIBLE_DUPLICATE` /
   `INVALID`), statement evidence panel (period, counts, money in/out,
   `IMPORTED / USER-PROVIDED BALANCE`), account-assignment dropdown when rows lack
   an account, default selection = importable NEW rows.
4. **Confirm**: atomic write; `StatementImportContext` stores document name,
   detected source, confirmed source ("source confirmed by user", or
   "source confirmed by user — detection was unknown"), SHA-256, format and
   preview-level counts. Success step shows the evidence + `VIEW RECONCILIATION`.
5. **Evidence**: `imported_statements` rows render on the Connections screen below
   the reconciliation panel: age chip (`IMPORTED — RECENT` cyan /
   `IMPORTED — OLD` amber), `IMPORTED / USER-PROVIDED` chip, source/format/age
   detail, period, `IMPORTED / USER-PROVIDED BALANCE: NPR X` (or "not stated"),
   **`CONNECTED BALANCE: NOT AVAILABLE — this file is not a live connection`**,
   row counts, money in/out, and "evidence only: never merged into connected or
   verified totals, and never used as a verified baseline".

### Rules enforced

- **Provenance is never upgraded**: imported rows stay `IMPORTED` forever; they
  feed the reconciliation loop only through `importedNetChangeMinor` (explained
  movement, never verified data) and never enter `ActualMoney`'s unified total.
- **Never labelled connected**: no `CONNECTED` / `VERIFIED` claim appears anywhere
  in the statement path; the Connections evidence panel always shows the
  `CONNECTED BALANCE: NOT AVAILABLE` line.
- **Duplicates**: fingerprint-based; re-importing the same document (or rows
  matching existing ledger rows) surfaces `POSSIBLE_DUPLICATE` for review —
  never silently skipped. Missing-account rows resolve their fingerprint under the
  RESOLVED account name after assignment.
- **Pasted CSV is not a statement**: the paste flow creates no
  `imported_statements` record (no document, no digest) — only file-picked inputs
  do.
- **Source is content + user confirmation**: detection is a suggestion over
  content markers only; the stored source is whatever the user explicitly
  confirmed (including `UNKNOWN`).

### Backup limitation (documented)

`imported_statements` is intentionally **excluded from the backup payload** and
**cleared on restore** (statement evidence is device-local provenance for the
current install). Consequently `APP_SCHEMA_VERSION` follows the Room version to
**10**, and strict schema equality rejects backups written by older builds (v9) —
by design, never a silent partial restore.

### Documented limitations

- **No OCR**: image-only or scanned PDFs report `NO_TEXT_EXTRACTED` honestly.
- **No provider-specific PDF claims**: no per-provider layout is encoded; exports
  whose table does not expose date + description + amount are rejected
  (`AMBIGUOUS_COLUMNS`), and any row the table cannot fit stays visible as an
  unparsed line — never silently dropped. Actual Sanima/Global IME/eSewa PDF
  exports have **not** been tested (no real personal statements are used as test
  fixtures, by policy); support is format-based, not vendor-certified.
- **Encrypted PDFs** fail safely with a message containing "never asks" — the app
  never asks for banking passwords and never attempts decryption.
- **Non-ISO dates inside PDF content** are visibly invalid rows (the ISO-only
  `ImportDate` rule is unchanged) — surfaced for review, not guessed.

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

Statement ingestion phase (this session):
- Tests: **858 / 82 suites / 0 failures / 0 errors** (77 new across 8 new suites —
  `StatementSourceDetectorTest`, `PdfTextExtractorTest`, `PdfStatementTableTest`,
  `StatementParserTest`, `StatementSummaryTest`, `ImportedStatementMigrationTest`,
  `ImportEngineStatementTest`, `ImportRepositoryStatementTest` — plus 5 new
  statement tests in `ImportTransactionsViewModelTest`, empty-statement fixture,
  assistant honesty tests, and 7 migration suites re-chained through
  `MIGRATION_9_10`).
- Lint: 0 errors / 27 warnings (unchanged). `assembleDebug` + `assembleRelease` OK;
  `apksigner verify` → v2 scheme, CN=Prasbin Dhungana. KSP schema `10.json` committed.
- On-device: debug APK installed and cold-launched on the Android 16 emulator
  (fresh install, `pm clear` first) with zero logcat crashes; physical device
  (YPA6RWNB7L7HPBRK) remained disconnected this session — honest limitation.
