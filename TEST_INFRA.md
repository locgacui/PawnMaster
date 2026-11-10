# TEST_INFRA.md — PawnMaster Dashboard E2E Test Infrastructure

## 1. Test Philosophy & Principles
The PawnMaster Dashboard test infrastructure is built on strict opaque-box, requirement-driven, and forensic verification principles:
- **Zero Facade Testing**: Every test inspects and asserts real DOM nodes, CSS rules, JavaScript bindings, data attributes, and semantic structures. Tests never assert trivial truthy states without verifying actual content and layout.
- **Requirement-Driven Verification**: Test expectations are derived strictly from `PROJECT.md`, `DESIGN.md`, `PRODUCT.md`, and `ORIGINAL_REQUEST.md`.
- **Dual-Mode Assurance**: The dashboard template (`dashboard.html`) must function identically as a Spring Boot Thymeleaf server-side rendered template and as a standalone browser file preview (`file:///...`).
- **Accessibility & Contrast Integrity**: Visual styling must mathematically satisfy WCAG AA contrast ratios (>= 4.5:1 for normal text, >= 3.0:1 for large text/badges).
- **Fintech Anti-Pattern Elimination**: Strict forensic checks against generic purple-to-blue gradients, neon glows, and nested cards.

---

## 2. Feature Inventory Coverage Mapping (18 Features)

| # | Feature Name | Mapped Tests | Verification Scope | Target Artifacts |
|---|--------------|--------------|-------------------|------------------|
| 1 | Design Tokens & Color Palette | `test_f01_design_tokens` | CSS custom properties `--pm-primary`, status colors, neutrals, semantic scales | `dashboard.css` |
| 2 | Tabular Numbers & Typography | `test_f02_tabular_numbers` | `tabular-nums` on currency, contract IDs, phone numbers, dates; font stacks | `dashboard.css`, `dashboard.html` |
| 3 | KPI Card 1: Hợp đồng hoạt động | `test_f03_kpi_active_contracts` | Title, active count, trend badge, icon chip, tabular font class | `dashboard.html` |
| 4 | KPI Card 2: Dư nợ giải ngân | `test_f04_kpi_disbursed_balance` | Disbursed loan amount (VND), capital limit subtext, trend badge, icon chip | `dashboard.html` |
| 5 | KPI Card 3: Lãi dự thu | `test_f05_kpi_expected_interest` | Monthly interest target, collected percentage, progress bar/badge, icon chip | `dashboard.html` |
| 6 | KPI Card 4: Quá hạn & Thanh lý | `test_f06_kpi_overdue_liquidation` | Overdue count & principal, risk breakdown, alert badge, danger icon chip | `dashboard.html` |
| 7 | Quick Action 1: Tạo HĐ mới | `test_f07_action_new_contract` | Button CTA, modal trigger `modalNewContract`, modal form fields (CCCD, collateral, loan) | `dashboard.html`, `dashboard.js` |
| 8 | Quick Action 2: Thu lãi | `test_f08_action_collect_interest` | Button trigger, modal `modalCollectInterest`, payment fields, calculation display | `dashboard.html`, `dashboard.js` |
| 9 | Quick Action 3: Chuộc đồ | `test_f09_action_redeem_collateral` | Button trigger, modal `modalRedeemCollateral`, settlement fields, warehouse release | `dashboard.html`, `dashboard.js` |
| 10 | Recent Contracts Data Grid | `test_f10_data_grid_columns` | 7 standard columns (Mã HĐ, Khách hàng, Tài sản, Số tiền, Trạng thái, Hạn, Thao tác) | `dashboard.html` |
| 11 | One-touch Status Filter Tabs | `test_f11_status_filter_tabs` | Filter tabs (Tất cả, Đang vay, Đến hạn hôm nay, Quá hạn, Chờ thanh lý) + badges | `dashboard.html`, `dashboard.js` |
| 12 | Table Search & Quick Query | `test_f12_table_search` | Search input element, placeholder (Mã HĐ, CCCD, SĐT, biển số), event wiring | `dashboard.html`, `dashboard.js` |
| 13 | Status Badges System | `test_f13_status_badges` | 6 ContractStatus classes (ACTIVE, OVERDUE, EXTENDED, WAITING_LIQUIDATION, REDEEMED, LIQUIDATED) | `dashboard.css`, `dashboard.html` |
| 14 | Chart 1: Dòng tiền Thu - Chi | `test_f14_chart_cash_flow` | Canvas element `cashFlowChart`, initialization logic, dual datasets (Thu vs Chi) | `dashboard.html`, `dashboard.js` |
| 15 | Chart 2: Cơ cấu danh mục cầm cố | `test_f15_chart_collateral_donut` | Canvas element `collateralDonutChart`, donut config, collateral categories | `dashboard.html`, `dashboard.js` |
| 16 | Responsive Grid Layout | `test_f16_responsive_grid` | Grid breakpoints (`col-xl-3`, `col-md-6`), table container `.table-responsive` | `dashboard.html`, `dashboard.css` |
| 17 | Dual-mode Rendering | `test_f17_dual_mode_rendering` | Thymeleaf attributes (`th:href`, `th:src`, `th:text`) paired with static fallback values | `dashboard.html` |
| 18 | Anti-pattern Elimination | `test_f18_anti_pattern_absence` | 0 generic purple-to-blue gradients, 0 neon glows, 0 nested `.card .card` | `dashboard.html`, `dashboard.css` |

---

## 3. Test Architecture & Directory Layout

```
tests/
├── e2e/
│   ├── test_tier1_feature_coverage.js    # Feature 1-18 verification (>=5 checks per feature)
│   ├── test_tier2_boundaries_edge.js     # XML/HTML5 parsing, tabular numbers, responsive table
│   ├── test_tier3_cross_feature.js       # Modals, Chart canvases, filter-table sync, enum badges
│   └── test_tier4_real_world.js          # Standalone preview, WCAG AA contrast, anti-patterns
├── lib/
│   ├── contrast.js                       # WCAG AA relative luminance and contrast calculator
│   ├── dom_inspector.js                  # HTML/XML parser and DOM query utilities
│   └── style_parser.js                   # CSS parser and token extractor
└── run_tests.js                          # Unified test runner with colorized TAP/JSON output
```

### Test Runner Invocation
```powershell
# Run the complete test suite
node tests/run_tests.js

# Or using Node.js built-in test runner
node --test tests/e2e/*.js
```

---

## 4. Coverage Thresholds Across 4 Tiers

| Tier | Focus Area | Minimum Check Count | Pass Threshold |
|------|------------|---------------------|----------------|
| **Tier 1: Feature Coverage** | All 18 Features (>=5 checks each) | 90 checks | 100% (90/90) |
| **Tier 2: Boundary & Corner Cases** | XML compliance, tabular nums on currency/IDs, empty states, responsive tables | 21 checks | 100% (21/21) |
| **Tier 3: Cross-Feature Combinations** | Modal triggers, Chart IDs, filter tab bindings, ContractStatus enums | 20 checks | 100% (20/20) |
| **Tier 4: Real-World Scenarios** | Standalone preview rendering, WCAG AA contrast, 0 anti-patterns | 17 checks | 100% (17/17) |
| **Total** | Full E2E Audit | **148 checks** | **100% Pass (148/148)** |
