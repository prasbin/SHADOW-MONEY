# Live Financial Connectivity Options — Investigation

Research-only phase. **Research date: 2026-10-03.** Scope: legitimate routes to
*real* financial connectivity for **Sanima Sajilo eBanking**, **Global IME Global
Smart Plus**, and **eSewa**. This document records what official sources actually
offer today, what would have to be applied for, and what was deliberately not
done. **No production adapters, no fake adapters, no live connectivity claimed,
no code changes** — the app architecture is untouched (`PRODUCTION_ADAPTERS` stays
empty). Companion to `docs/REAL_MONEY_CONNECTIONS.md` (architecture + built
reconciliation loop).

## Method and evidence rules

- Official sources first (provider docs → NRB/government → provider API docs →
  reputable independent → community only as corroboration). Source URLs listed in
  §Sources; all checked on the research date.
- Capability cells use only `YES` / `NO` / `PARTNER ONLY` / `UNKNOWN`.
- Security flags use only `RED` / `GREEN` / `UNKNOWN`.
- Pricing is never invented; unpublished values are `NOT PUBLICLY DISCLOSED`.
- A capability is `YES` only when an official, documented interface exists — not
  when a product feature exists inside the provider's own app.
- SEO listicles, aggregator "directory" pages, and community summaries are never
  treated as authority; they are marked as corroboration or discarded.

## Part 1 — Official provider research (re-verified)

### Sanima — Sajilo eBanking

- Customer channels only: `sajiloebanking.sanimabank.com` (internet banking),
  mobile banking app, and `onlineservices.sanimabank.com` — the latter is a
  **forms portal** (Account Statement request, Balance Certificate, KYC, cheque
  and card services), not a data API. `sanimabank.com/digital-banking` publishes
  product pages and download forms only (incl. "Enable transaction feature in
  Sanima Sajilo eBanking"); the only contact route for integration-type questions
  is the **Digital Banking Inquiry** form ("we shall contact you in 24 hrs").
- **No developer portal, no public API documentation, no open-banking developer
  program found** across repeated searches of official channels.
- The bank's only public API activity is **bank-to-bank**: the Sanima × Standard
  Chartered *real-time API-based INR payment* service (reported July 2026) —
  institutional cross-border settlement, not consumer account data.
- Verdict: balance `NO`, transactions `NO`, payment initiation `NO` (no consumer
  or third-party interface), statement export `YES` (user-driven download from
  the bank portal).

### Global IME — Global Smart Plus

- Customer channels only: `www.globalonline.gibl.com.np` (web login) and the
  **Global Smart Plus** app (Play Store package `com.swifttechnology.globalsmart`,
  built by Swift Technology) — balance view, statements, transfers, QR, bills all
  live *inside the bank's own app*.
- **No developer portal, no public account-data API found.** PCI DSS compliance
  and an AI/ML Hackathon are published (innovation interest), but neither is an
  API access program.
- Verdict: balance `NO`, transactions `NO`, payment initiation `NO` for third
  parties, statement export `YES` (user-driven, in-app/web).

### eSewa

- `developer.esewa.com.np` was fetched directly. Self-description: *"Welcome to
  eSewa Payment API documentation… Whom this is for: Business house and
  Organization interested on adding online payment facilities."* Documented
  surfaces: ePay (redirect), Intent (deeplink booking), Token (inquiry/payment/
  status), status check, and CMS plugins — all **merchant payment flows**.
- Auth model: merchant credentials (client ID/secret, Basic/Bearer token).
- **No consumer wallet balance API. No consumer statement/history API.** The
  status API answers "did this merchant-bound payment complete", never "what is
  in this wallet". Merchant balance lives in the web portal, not an API
  (corroborated by community research listing the same limitations).
- Users can export their own statement from the eSewa app (PDF); in this app
  that lands as `IMPORTED` data through the existing import path (CSV supported;
  PDF is documented as not parsed).
- Verdict: balance `NO`, transactions `NO`, payment initiation `YES` (merchant
  category), statement export `YES` (user-driven).

| Provider | Official public consumer API? | Balance read | Transaction read | Payment initiate | Statement export (user-driven) |
|---|---|---|---|---|---|
| Sanima — Sajilo eBanking | No | `NO` | `NO` | `NO` | `YES` |
| Global IME — Global Smart Plus | No | `NO` | `NO` | `NO` | `YES` |
| eSewa (consumer wallet data) | No | `NO` | `NO` | `NO` | `YES` |
| eSewa (merchant payments) | Yes — merchant only | `NO` | `NO` (status only) | `YES` | `NO` |

## Part 2 — Aggregators and third-party platforms (legitimacy screening)

Applied checklist: official docs? legal identity published (PAN/address)?
credential model; capability actually covers balance/history? pricing published?
Nepal coverage evidence? data handling stated? Press/registry corroboration?

| Candidate | What it actually is | Balance/transactions capability | Credential model | Verdict for this app |
|---|---|---|---|---|
| **APINepal** (`apinepal.com`) | Payment gateway — one checkout API over Fonepay QR / eSewa / Khalti | `NO` — checkout and webhooks only | Merchant bearer keys (`sk_…`) — `GREEN` | Payments only; **not** an account-information provider; not relevant to balance/history |
| **Swippee** (`swippee.com`) | Nepali **statement parser** (PDF/Excel/image → JSON), Kathmandu, founded 2026, unfunded, "early access" | `YES` but **file-derived only** (user uploads a statement the user already has); live sync `NO`; supported: Global IME + eSewa (+ Nabil, NIC Asia, NIMB, NMB, Kumari, Siddhartha, Nepal Bank, Prabhu, Garima, Manjushree, Khalti) — **Sanima not listed** | **No bank credentials ever** — `GREEN`; raw files deleted within 24 h; signed webhooks | Optional helper only. Its only capability this app lacks is PDF parsing — achievable only by uploading statements to a third party (data leaves device). Company age/trust: `UNKNOWN` |
| **Global aggregators** (Plaid, Tink, TrueLayer, Yapily, Fintoc, Mastercard Open Finance) | Open-banking/data aggregators | `NO` Nepal coverage in any of them | Consent-token models (mature abroad) | Out of scope — none cover Nepali institutions |
| **openbankingtracker.com** "NIC Asia" page | SEO directory (boilerplate Plaid/Tink/TrueLayer text) | `UNKNOWN` — not evidence | n/a | **Discarded** as authority |
| **PayBridgeNP** | Payment-gateway reseller (eSewa/Khalti/Fonepay) | `NO` | Merchant keys | Payments only |

## Part 3 — NRB, regulation, and payment infrastructure

- **Licensing**: Payment and Settlement Act 2019 + Unified Directive on Payment
  Systems (2081 / 2025, 15th amendment) govern PSOs/PSPs (~10 PSOs, 27 PSPs
  licensed). eSewa is an NRB-licensed PSP.
- **NRB Fintech Strategy for Digital Financial Services (2026/27–2030/31)** —
  official *Strategic Framework* PDF published **2026-09-15** by the Payment
  Systems Department (§Sources). Three pillars: Infrastructure; Policy, Regulation
  & Governance (incl. "level playing field"); Fintech Ecosystem Development. It
  is an enabling strategy — **it contains no account-information/open-banking
  data-sharing mandate**.
- **Regulatory Sandbox**: NRB *Guidelines on the Regulatory Sandbox* effective
  **2026-05-14** (replaces the 2025 draft); applicants must be **incorporated in
  Nepal** (banks, PSOs/PSPs, remittance companies, fintech firms); 45-working-day
  application windows, ~120 working days to outcome; first cohort admitted in
  2026 (8 admitted, 4 graduated per World Payments Monitor). This is a genuine
  route for a licensed/incorporated entity to test products — **not a self-serve
  API for an individual app**.
- **Open banking**: no formal statutory open-banking framework exists in Nepal
  (independent legal analyses say so explicitly, and Nepali commentary urges NRB
  to *create* one). A single low-authority SEO page claims a "draft Open Banking
  Framework" — **not confirmed** on `nrb.org.np` or by reputable news; treated as
  `UNKNOWN`/unverified.
- **NCHL / connectIPS** (`doc.connectips.com`): National Payment Interface APIs
  use OAuth2 and *do* include a **balance-enquiry endpoint — but only for
  whitelisted technical members**; docs are behind a member/partner login and the
  stated audience is "partners of the BFIs, members of NCHL". → `PARTNER ONLY`,
  institutional membership required. connectIPS itself is a *payment* rail
  (link account → pay), not a consumer data-sharing service.
- **In-country partner-API precedent**: Laxmi Sunrise's **LxB Connect** publishes
  a genuine partner API program — Account Inquiry (Balance Enquiry, Mini
  Statement, Statement View), Link Account, Fund Transfer — with an application
  form. This proves `PARTNER ONLY` account-inquiry APIs exist in Nepal *at some
  banks*, but **not at Sanima or Global IME**, and applying is a business
  relationship, not a signup.
- **Global aggregators do not cover Nepal** (re-verified); there is no
  India-style Account Aggregator regime.

## Part 4 — Personal-use routes

- **No official personal or third-party API exists** for the three target
  providers (Part 1). There is no self-serve signup anywhere that yields balance
  or transaction data for a personal account.
- **The legitimate route available today**: the user exports their own statement
  (Sanima portal Account Statement, Global Smart Plus statement, eSewa app
  statement) and imports it locally. Imported rows keep `IMPORTED` provenance
  forever and feed reconciliation only as explained movement — never as verified
  data. CSV import is supported; PDF statements were documented as **not parsed**
  at research time — superseded by the subsequent Real Statement Ingestion phase,
  which added on-device PDF parsing (still `IMPORTED` provenance, never connected;
  see `docs/REAL_MONEY_CONNECTIONS.md` §6).
- **Rejected routes (prohibited, not evaluated as options)**: app → bank
  username/password automated login; scraping or reverse-engineering private
  endpoints; certificate-pinning/MFA bypass; OTP/PIN/password capture;
  Accessibility-service harvesting; automation of protected banking apps;
  unauthorized aggregators.

## Part 5 — Capability matrix

| Interface | Balance read | Transactions read | Statement export | Payment initiation | Auth model | Evidence |
|---|---|---|---|---|---|---|
| Sanima customer portals | `NO` (view in own portal only) | `NO` | `YES` (user download) | `NO` | Bank login (user's own session) | Official site |
| Global Smart Plus (app/web) | `NO` (view in own app only) | `NO` | `YES` (user download) | `NO` | Bank login (user's own session) | Official site |
| eSewa consumer | `NO` | `NO` | `YES` (user download, PDF) | `NO` | eSewa login (user's own session) | Official docs |
| eSewa merchant API | `NO` | `NO` (payment status only) | `NO` | `YES` | Merchant credentials | `developer.esewa.com.np` |
| NCHL connectIPS NPI | `PARTNER ONLY` (whitelisted members) | `PARTNER ONLY` | `PARTNER ONLY` | `YES` (members) | OAuth2 (member) | `doc.connectips.com` |
| LxB Connect (Laxmi Sunrise — reference precedent) | `PARTNER ONLY` | `PARTNER ONLY` | `PARTNER ONLY` | `PARTNER ONLY` | Partner application + OTP flows | Official bank site |
| APINepal | `NO` | `NO` | `NO` | `YES` (checkout) | Merchant bearer key | Official docs |
| Swippee | `YES` (from uploaded file) | `YES` (from uploaded file) | n/a (is the reader) | `NO` | API key; **no bank credentials** | Official docs |
| Plaid/Tink/TrueLayer/Yapily/Fintoc | `NO` (no Nepal coverage) | `NO` | `NO` | `NO` | n/a | Official coverage pages |

## Part 6 — Ranked connectivity paths

1. **PATH 1 — User-mediated export → local import (AVAILABLE NOW).** The only
   route that works today without any partner: user downloads their own
   statement from Sanima / Global Smart Plus / eSewa and imports it (CSV; PDF
   unsupported). Data stays on device, app stays offline, provenance honest.
2. **PATH 2 — Partner/institutional application (THE LIVE-CONNECTIVITY ROUTE).**
   A direct application to Sanima (Digital Banking Inquiry → FI/IT department)
   and/or Global IME (bank or its vendor Swift Technology) for an
   account-inquiry API in the LxB Connect mold; NCHL technical-member access for
   whitelisted balance enquiry; eSewa merchant onboarding **only** for payment
   flows (it does not unlock wallet data). Longer-term: NRB Regulatory Sandbox
   (requires a Nepal-incorporated entity). Availability for our three providers:
   not published → every step is `PARTNER ONLY`.
3. **PATH 3 — Third-party statement parsing (OPTIONAL, NOT RECOMMENDED).**
   Swippee can parse PDF statements (including Global IME/eSewa; not Sanima)
   without credentials, but requires uploading statements off-device and the
   company is new (`UNKNOWN` trust). The app's local CSV importer already covers
   the same provenance model without data leaving the device.

Explicitly not a path: any credential-based automation or unofficial API —
`RED`, prohibited regardless of apparent convenience.

## Part 7 — Cost

| Item | Cost |
|---|---|
| Sanima account-inquiry API (if a partnership were granted) | `NOT PUBLICLY DISCLOSED` (no product page exists) |
| Global IME account-inquiry API (if granted) | `NOT PUBLICLY DISCLOSED` |
| eSewa merchant API | `NOT PUBLICLY DISCLOSED` (fee schedule not published on official docs) |
| NCHL connectIPS technical membership | `NOT PUBLICLY DISCLOSED` |
| LxB Connect partner API | `NOT PUBLICLY DISCLOSED` (application form only) |
| NRB Regulatory Sandbox application | `NOT PUBLICLY DISCLOSED` |
| Swippee (only costed option found, official site) | Sandbox free (100 parses); Growth **NPR 4,999/mo**; Scale **NPR 14,999/mo**; overage NPR 6 / NPR 4 per parse |
| PATH 1 (export → import) | **Free** |

## Part 8 — Security assessment

| Route / model | Flag | Reason |
|---|---|---|
| App → bank credentials, automated login (any form) | `RED` | Credential capture + ToS violation; prohibited |
| Scraping / reverse-engineered private endpoints / pinning bypass / OTP capture / Accessibility harvesting | `RED` | Explicitly prohibited |
| Unofficial "Nepal open banking" aggregators without verifiable licensing | `RED` | Unverified custody of financial data |
| eSewa official merchant payment API | `GREEN` | Official, documented, credential scope = merchant payments (capability simply doesn't include wallet data) |
| Bank partner APIs (LxB mold, NCHL whitelisted) | `GREEN` | Consent-based, OTP-accompanied, official — but `PARTNER ONLY` |
| User-mediated export → local import | `GREEN` | No credentials leave the device; app remains offline; user stays in their own provider session |
| Swippee upload | `GREEN` (credential model) / `UNKNOWN` (vendor trust) | No credentials involved, but statements leave the device; company founded 2026 |
| Storing any password/PIN/OTP/token in the app | `RED` | Never implemented; connection tables hold metadata only |

## Part 9 — Integration points with the existing architecture

- **No code changes are required or justified.** `ProviderAdapter` is the
  designed seam: if PATH 2 ever yields an official account-inquiry API, a real
  adapter is implemented against that official interface and registered in
  `ConnectionSyncCoordinator.PRODUCTION_ADAPTERS` (still empty); sync → snapshot →
  baseline → discrepancy already work unchanged.
- `ProviderCatalog` already renders the honest facts this investigation
  re-confirmed (incl. eSewa `MERCHANT API — NOT A PERSONAL WALLET SYNC
  INTERFACE`).
- The import system (Phase 10) already implements PATH 1 with `IMPORTED`
  provenance; the PDF gap is documented, not silently faked.
- No financial logic, schema (Room stays **v9**), or UI changes in this phase.

## Part 10 — Implementation decision

### **B. PARTNER APPLICATION REQUIRED**

Not **A** (Ready for implementation): no official account-information interface
is obtainable today for Sanima, Global IME, or the eSewa consumer wallet — there
is nothing to build an adapter against, and inventing one is forbidden. Not **C**
(No viable route): viable routes exist — they simply require applications and
relationships rather than a signup, and PATH 1 already delivers real data flow
now.

**What the user must obtain for live connectivity:**

1. **Sanima**: a granted account-inquiry API agreement via direct application
   (Digital Banking Inquiry form / bank relationship) — precedent for such APIs
   exists in Nepal (LxB Connect) but Sanima publishes none.
2. **Global IME**: the same, via the bank or its vendor Swift Technology.
3. **eSewa**: merchant onboarding (`merchant.esewa.com.np`) — this unlocks
   **payments only**; a consumer wallet-data API does not exist to apply for.
4. **NCHL connectIPS**: technical-member status with whitelisted accounts for
   balance enquiry (institutional).
5. **Optionally**: NRB Regulatory Sandbox participation — requires a company
   incorporated in Nepal; no fee published.

**Caveat (honest):** even after application, these institutions may decline or
offer payments-only terms; account-information APIs for personal budgeting are
not a published product anywhere in Nepal as of the research date. Until such an
agreement exists, PATH 1 (export → local import) is the sanctioned real-data
route, and every provider card keeps reporting `UNAVAILABLE` with the reason.

## Sources (all checked 2026-10-03)

Official — providers:
- `https://developer.esewa.com.np/` (+ `/pages/Introduction`, `/pages/Token`, `/pages/Epay`, `/pages/Intent`) — eSewa merchant payment API
- `https://esewa.com.np/` — eSewa consumer site
- `https://onlineservices.sanimabank.com/` — Sanima online services (forms)
- `https://www.sanimabank.com/digital-banking` (+ `/downloads`, `/digital-banking/inquiry`) — Sanima digital banking + inquiry form
- `https://www.globalimebank.com/` (+ `/products/digital-payments/global-smart-plus`) — Global IME / Global Smart Plus
- `https://globalonline.gibl.com.np/` — Global Smart Plus web login
- `https://play.google.com/store/apps/details?id=com.swifttechnology.globalsmart` — Global Smart Plus app (Swift Technology)
- `https://www.laxmisunrise.com/lxbconnect` — LxB Connect partner API precedent

Official — regulator / infrastructure:
- `https://www.nrb.org.np/contents/uploads/2026/09/Strategic-Framework-of-the-Fintech-Strategy-for-Digital-Financial-Services.pdf` — NRB Fintech Strategy (2026/27–2030/31), published 2026-09-15
- `https://www.nrb.org.np/psd` — NRB Payment Systems Department notices
- `https://www.nrb.org.np/category/directives/` — NRB directives (Unified Payment Systems Directive 2081/2025)
- NRB *Guidelines on the Regulatory Sandbox* PDF (`nrb.org.np/psd/…regulatory-sandbox…`), effective 2026-05-14
- `https://doc.connectips.com/` + `/docs/NPI/NPI_Specification/api_specifications` — NCHL NPI APIs (OAuth2; whitelisted balance enquiry)
- `https://nchl.com.np/` — Nepal Clearing House Limited

Reputable independent:
- `https://kathmandupost.com/money/2026/06/24/regulatory-sandbox-guidelines-aim-to-foster-nepal-fintech-innovation`
- `https://english.nepalnews.com/s/explainers/everything-you-should-know-about-nrbs-new-regulatory-sandbox` (2026-06-19)
- `https://english.nepalnews.com/s/business/nepal-news-evening-economic-brief-september-15-2026` (Fintech Strategy launch)
- `https://www.sharesansar.com/newsdetail/nrb-unveils-five-year-fintech-strategy-to-drive-digital-finance-and-innovation-2026-09-15`
- `https://payments.gi/jurisdictions/nepal` — World Payments Monitor (licensing counts, sandbox cohort, Fintech Strategy summary), updated 2026-09-29
- `https://www.fiscalnepal.com/2026/06/24/26698/nrb-moves-to-launch-regulatory-sandbox-to-accelerate-fintech-and-digital-banking-innovation`
- `https://ictframe.com/api-based-inr-payment-solution` (+ ShareHub mirror) — Sanima × Standard Chartered API payments, 2026-07-04
- `https://www.myrepublica.nagariknetwork.com/news/nrb-introduces-new-guidelines-to-encourage-financial-innovation-81-48.html`

Third-party platforms (screened, not authoritative for capability claims):
- `https://apinepal.com/api-documentation` — APINepal (payment gateway)
- `https://swippee.com/` + `/products` + `https://docs.swippee.com/introduction.md` — Swippee statement parsing
- `https://paybridgenp.com/blog/esewa-merchant-account-api-guide` — corroboration that eSewa merchant fees are unpublished

Discarded as authority: `openbankingtracker.com` (SEO boilerplate);
`nepaldatabase.com` "draft Open Banking Framework" (unverified single source);
community GitHub research docs (used only as corroboration of eSewa's stated
limitations).

## What was NOT done (scope)

- No adapter code, no fake adapter, no endpoint invented, no connectivity claimed.
- No credential/OTP/PIN requested, stored, or tested; no scraping, no app
  automation, no traffic interception, no Accessibility harvesting.
- No production device or app data touched; no schema, financial-logic, or UI
  change; `INTERNET` permission absent and remains absent.
- No pricing invented; unknowns are labeled `NOT PUBLICLY DISCLOSED` / `UNKNOWN`.
