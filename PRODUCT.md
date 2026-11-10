# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Primary users are internal pawnshop counter and back-office personnel operating across four role-based access tiers (`RoleEnum`):
- **Store Staff / Loan Officers (`STAFF`):** Execute front-desk counter workflows—rapid customer verification (CCCD/national ID), physical collateral appraisal, contract issuance, periodic interest collection, and collateral redemption settlements.
- **Store Managers & Owners (`MANAGER`, `ADMIN`):** Monitor active portfolio exposure, capital utilization limits, overdue/default risk, staff performance, and collateral liquidation approvals.
- **Accountants (`ACCOUNTANT`):** Reconcile daily cashbook inflows/outflows (`CASH_IN`, `CASH_OUT`), verify principal vs. interest collections, and audit physical vault inventory against ledger records.

## Product Purpose

**PawnMaster** is a comprehensive pawnshop and collateral loan management system. It exists to replace error-prone manual ledgers and spreadsheets in high-volume pawn operations with a deterministic, audit-grade workspace.

**Core Jobs to Be Done:**
1. Fast collateral appraisal, valuation, and pledge contract registration.
2. Automated interest schedule calculation, grace periods, and compounding tracking.
3. Liquidation, auctioning, and unredeemed collateral inventory management.
4. Comprehensive financial auditing, daily ledger reconciliation, and reporting.

Success means counter staff can originate, service, or settle a pledge contract in seconds with zero arithmetic errors, while managers and accountants maintain real-time, reconciled visibility into cash flow and physical vault states.

## Positioning

Unlike generic lending CRMs or consumer finance portals, PawnMaster is purpose-built for **high-speed physical counter execution paired with strict vault-to-cashbook ledger integrity**:
- Every contract lifecycle transition (disbursement, periodic interest payment, partial principal paydown, redemption, or liquidation) is atomically tied to physical collateral custody states (`IN_VAULT`, `WAITING_LIQUIDATION`, `REDEEMED`, `LIQUIDATED`) and dual-direction cashbook entries (`CASH_IN`, `CASH_OUT`).
- Supports domain-specific Vietnamese pawn interest conventions (`DAILY_PER_MILLION`, `PERCENT_MONTHLY`, `PERCENT_WEEKLY`) and instant counter calculators directly in operational modals.

## Operating Context

- **Environment:** Desktop and tablet browsers inside brick-and-mortar pawnshop branches and back offices (frequently intranet or local-network deployments requiring resilient OS font fallbacks and dual-mode static/Thymeleaf template rendering).
- **Locale & Currency:** Vietnamese (`vi-VN`) operational terminology and Vietnamese Đồng (`VND` / `₫`) monetary formatting with strict tabular numerals (`tabular-nums`) for rapid vertical scanning of high-denomination amounts (millions to billions of `₫`).
- **Artifacts & Rituals:** National ID (`CCCD`) verification, physical asset tagging and vault storage across 8 collateral categories (`MOTORBIKE`, `CAR`, `GOLD_JEWELRY`, `PHONE`, `LAPTOP`, `REAL_ESTATE`, `WATCH`, `OTHER`), daily cash drawer reconciliation, and grace-period/overdue escalation calls.

## Capabilities and Constraints

- **Confirmed Backend & Domain Modules:**
  - Spring Boot 3 / Java 17 REST & MVC architecture with SQL Server persistence, JPA auditing, and stateless JWT authentication.
  - **Contract Lifecycle (`ContractStatus`):** `DRAFT`, `ACTIVE`, `OVERDUE`, `EXTENDED`, `REDEEMED`, `LIQUIDATED`, `CANCELLED`.
  - **Payment Operations (`PaymentType`):** `INTEREST_PAYMENT`, `PARTIAL_PRINCIPAL`, `REDEMPTION`, `LIQUIDATION`.
  - **Cashbook Categories (`TransactionCategory`):** `DISBURSEMENT`, `INTEREST_COLLECTION`, `PRINCIPAL_COLLECTION`, `LIQUIDATION_INCOME`, `OPERATING_EXPENSE`, `CAPITAL_CONTRIBUTION`, `OTHER`.
  - **Customer Classification (`CustomerStatus`):** `ACTIVE`, `VIP`, `BLACKLISTED`.
- **Technical & Template Constraints:**
  - Templates (`src/main/resources/templates/dashboard.html`) must remain valid XML/Thymeleaf 3 with dual-mode attributes (`href`/`th:href`, `src`/`th:src`) so they render both via Spring Boot and as standalone static HTML previews without a running database.
  - External JS (`src/main/resources/static/js/dashboard.js`) and CSS (`src/main/resources/static/css/dashboard.css`) must preserve the 148-check E2E verification suite (`node tests/run_tests.js`).
- **Scope Boundary:** Strictly internal store and back-office operations; no customer-facing borrower portal is currently in scope.

## Brand Commitments

- **Product Name:** PawnMaster
- **Domain:** Fintech, Collateral Financing, Pawn Operations.
- **Voice & Personality:** Authoritative, trustworthy, highly legible, data-dense, and clean.
- **Operational Commitment:** Zero decorative fluff—no generic AI purple-to-blue gradients, no unmotivated neon glows, no nested card containers, and no raw emoji icons in operational UI.

## Evidence on Hand

- **Operational Dashboard UI:** `src/main/resources/templates/dashboard.html`, `src/main/resources/static/css/dashboard.css`, and `src/main/resources/static/js/dashboard.js` implementing 4 KPI summary cards, 3 counter quick-action modals (`#modalNewContract`, `#modalCollectInterest`, `#modalRedeemCollateral`), a 7-column filterable/searchable contract data grid, and 2 Chart.js visualizations (`#cashFlowChart`, `#collateralDonutChart`).
- **Verification Suite:** `tests/run_tests.js` and `TEST_READY.md` (148/148 passing checks across feature coverage, XML/boundary safety, cross-component bindings, and WCAG AA math verification).
- **Backend Domain Layer:** `src/main/java/com/pawnmaster/api/modules/` (`contract`, `collateral`, `customer`, `cashbook`, `user`, `auth`).
- **Absences Not to Fabricate:** No external customer testimonials, third-party credit bureau integrations, or public SaaS pricing tiers exist; future work must not invent them.

## Product Principles

1. **Counter Speed First:** High-frequency counter actions (new pledge, collect interest, redeem collateral) must stay reachable in one click from the primary workspace with instant inline arithmetic feedback.
2. **Audit-Grade Numerical Clarity:** Every monetary figure, contract code, phone number, and date must use tabular numerals and unambiguous semantic status encoding so operators never misread a digit or state under time pressure.
3. **Vault-to-Ledger Traceability:** Every contract action must clearly reflect its downstream impact on physical collateral custody and daily cashbook balance.
4. **Offline & Intranet Resilience:** Interfaces must degrade gracefully with local system font stacks and dual-mode static/server markup that can be inspected and tested independently of live infrastructure.

## Accessibility & Inclusion

- **WCAG 2.1 AA Minimum Contrast:** All text and semantic status badges (`ACTIVE`, `OVERDUE`, `EXTENDED`, `WAITING_LIQUIDATION`, `REDEEMED`) must maintain mathematical contrast ratios >= 4.5:1 against their background surfaces (primary text exceeds AAA at 14.5:1).
- **Keyboard & Focus Ergonomics:** Visible `:focus-visible` rings on all interactive controls, accessible modal dialogs with keyboard focus management and `Esc` dismissal, and non-color-solely status indicators (paired `.pm-badge-dot` + explicit Vietnamese status labels).
