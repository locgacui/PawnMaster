# Project: PawnMaster Dashboard

## Architecture
PawnMaster Dashboard is an enterprise-grade fintech dashboard for pawnshop owners and counter loan officers.
- **Frontend Stack**: HTML5, Bootstrap 5.3.3, Bootstrap Icons 1.11.3, Chart.js 4.4.7.
- **Template Engine**: Thymeleaf 3 (XML/HTML5 compliant) supporting dual-mode execution:
  - *Server-side rendering*: Dynamic data binding via `th:text`, `th:each`, `th:classappend`, `th:href`, `th:src`.
  - *Standalone Preview*: Static fallback preview via standard HTML attributes without server dependencies.
- **Styling Architecture**: Custom fintech design tokens (`--pm-*`) adhering to `DESIGN.md` and UI/UX Pro Max (OPERATE mode), strictly avoiding fintech anti-patterns (no purple-to-blue generic gradients, no neon glows, no nested cards).
- **Data Flow**:
  - Topbar & Quick Actions -> Trigger modal dialogs with form validation.
  - KPI Overview Cards -> Display key portfolio health metrics with tabular numerals.
  - Interactive Visualizations -> Cash flow trend line/area chart + Collateral category donut chart.
  - Operations Data Grid -> 7-column table with multi-criteria search, status tabs, and row-level quick actions.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Design Tokens & Color Palette | High-contrast fintech tokens (Slate Blue primary `#1e40af`, Emerald success `#10b981`, Amber warning `#f59e0b`, Rose danger `#ef4444`, Slate neutrals). | M1 | DESIGN.md & UI/UX Pro Max |
| 2 | Tabular Numbers & Typography | Fixed-width glyphs (`font-variant-numeric: tabular-nums`) for currency amounts, contract IDs, phone numbers, and dates. | M1 | DESIGN.md & typography.csv |
| 3 | KPI Card 1: Hợp đồng hoạt động | Thẻ tổng hợp đồng cầm đồ đang hoạt động kèm badge tăng trưởng và dư nợ phụ trợ. | M2 | ORIGINAL_REQUEST.md R2 |
| 4 | KPI Card 2: Dư nợ giải ngân | Thẻ tổng dư nợ cho vay thực tế ngoài thị trường kèm tỷ lệ giải ngân trên hạn mức vốn. | M2 | ORIGINAL_REQUEST.md R2 |
| 5 | KPI Card 3: Lãi dự thu | Thẻ tổng tiền lãi và phí lưu kho dự kiến thu trong kỳ kèm tỷ lệ hoàn thành kế hoạch. | M2 | ORIGINAL_REQUEST.md R2 |
| 6 | KPI Card 4: Quá hạn & Thanh lý | Thẻ cảnh báo số lượng và dư nợ các khoản vay quá hạn, rủi ro nợ xấu cần xử lý gấp. | M2 | ORIGINAL_REQUEST.md R2 |
| 7 | Quick Action 1: Tạo HĐ mới | Nút thao tác nhanh nổi bật + Modal đăng ký hợp đồng cầm đồ mới, định giá tài sản và giải ngân. | M2 | ORIGINAL_REQUEST.md R2 |
| 8 | Quick Action 2: Thu lãi | Nút thao tác nhanh + Modal thu tiền lãi định kỳ, dời hạn đóng lãi và cho phép trả bớt gốc. | M2 | ORIGINAL_REQUEST.md R2 |
| 9 | Quick Action 3: Chuộc đồ | Nút thao tác nhanh + Modal tất toán khoản vay gốc + lãi phát sinh và xuất kho trả tài sản. | M2 | ORIGINAL_REQUEST.md R2 |
| 10 | Recent Contracts Data Grid | Bảng danh sách 7 cột: Mã HĐ, Khách hàng, Tài sản cầm cố, Số tiền vay, Trạng thái, Hạn đóng lãi, Thao tác. | M2 | ORIGINAL_REQUEST.md R2 |
| 11 | One-touch Status Filter Tabs | Thanh tab lọc nhanh một chạm: Tất cả, Đang vay, Đến hạn hôm nay, Quá hạn, Chờ thanh lý. | M2 | PRODUCT.md & OPERATE mode |
| 12 | Table Search & Quick Query | Ô tìm kiếm đa năng tức thì theo Mã HĐ, Tên khách hàng, SĐT, CCCD, Biển số xe, IMEI. | M2 | PRODUCT.md |
| 13 | Status Badges System | 6 huy hiệu trạng thái chuẩn: ACTIVE (Đang vay), OVERDUE (Quá hạn), EXTENDED (Gia hạn), WAITING_LIQUIDATION (Chờ thanh lý), REDEEMED (Đã chuộc), LIQUIDATED (Đã thanh lý). | M1, M2 | ContractStatus.java & DESIGN.md |
| 14 | Chart 1: Dòng tiền Thu - Chi | Biểu đồ vùng mượt mà thể hiện dòng tiền chi giải ngân vs thu hồi vốn/lãi theo thời gian. | M1, M2 | ORIGINAL_REQUEST.md R2 |
| 15 | Chart 2: Cơ cấu danh mục cầm cố | Biểu đồ Donut thể hiện tỷ lệ % các loại tài sản cầm cố (Xe máy, Ô tô, Vàng, Điện thoại/Laptop, Khác). | M1, M2 | ORIGINAL_REQUEST.md R2 |
| 16 | Responsive Grid Layout | Bố cục lưới Bootstrap 5 co giãn tự nhiên từ desktop (4 cột) đến tablet (2x2) không gây vỡ giao diện hay cuộn ngang. | M1, M2 | ux-guidelines.csv |
| 17 | Dual-mode Rendering | Template Thymeleaf chuẩn cú pháp XML/HTML5 hoạt động mượt mà cả khi chạy Spring Boot lẫn khi mở Standalone file HTML. | M2 | explorer_codebase_1 report |
| 18 | Anti-pattern Elimination | Đảm bảo 0 generic purple-to-blue gradients, 0 neon glows, 0 nested cards, 0 unicode icon hacks. | M1, M2, M3 | DESIGN.md & craft-floor.md |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Design System & Assets | Xây dựng bộ Design Tokens CSS (`dashboard.css`) và thư viện tương tác JS (`dashboard.js`) trong `src/main/resources/static/`. | none | DONE |
| M2 | Dashboard Thymeleaf View | Hiện thực hóa template `src/main/resources/templates/dashboard.html` hoàn chỉnh với đầy đủ 4 KPI, 3 Quick Actions & Modals, Data Table 7 cột, Dual Charts. | M1 | DONE |
| M3 | E2E Verification & Forensic Integrity Audit | Kiểm tra toàn diện cú pháp HTML/Thymeleaf, giao diện standalone preview, kiểm toán chống gian lận/anti-patterns và nghiệm thu sản phẩm. | M1, M2 | DONE |

## Interface Contracts
### M1 (Static Assets) ↔ M2 (Template View)
- CSS classes & variables:
  - Custom properties: `--pm-primary`, `--pm-bg-surface`, `--pm-border-subtle`, `--pm-text-primary`, `--pm-status-*`.
  - Utility classes: `.pm-kpi-card`, `.pm-icon-chip`, `.pm-btn-primary`, `.pm-btn-success-tonal`, `.pm-btn-indigo-tonal`, `.pm-badge-*`, `.font-tabular`, `.pm-table-dense`.
- JavaScript hooks:
  - Chart initialization: `initCashFlowChart(canvasId, options)`, `initCollateralDonutChart(canvasId, options)`.
  - Search & Filter handlers: `initDashboardFilters(tableId, searchInputId, filterTabsId)`.
  - Modal workflows: Event handlers for `modalNewContract`, `modalCollectInterest`, `modalRedeemCollateral`.

### M2 (Template View) ↔ Backend Controller / Standalone
- Static links:
  `<link rel="stylesheet" href="../static/css/dashboard.css" th:href="@{/css/dashboard.css}" />`
  `<script src="../static/js/dashboard.js" th:src="@{/js/dashboard.js}"></script>`
- Data fallback:
  `<span class="pm-kpi-value font-tabular" th:text="${kpi?.activeContractsCount ?: '128'}">128</span>`

## Code Layout
- `src/main/resources/templates/dashboard.html` (Thymeleaf view template chính)
- `src/main/resources/static/css/dashboard.css` (Design tokens & stylesheet tùy biến)
- `src/main/resources/static/js/dashboard.js` (Khởi tạo Chart.js, bộ lọc bảng, logic modal)
- `tests/run_tests.js` (Bộ test runner E2E kiểm thử tự động toàn diện 4 Tiers)
