---
name: PawnMaster
description: High-density fintech workspace for pawnshop counter operations, collateral custody, and audit-grade ledger management
colors:
  primary: "#1e40af"
  primary-hover: "#1d4ed8"
  primary-active: "#172554"
  primary-light: "#eff6ff"
  primary-border: "#bfdbfe"
  bg-app: "#f8fafc"
  bg-surface: "#ffffff"
  bg-subtle: "#f1f5f9"
  border-subtle: "#e2e8f0"
  border-strong: "#cbd5e1"
  border-focus: "#3b82f6"
  text-primary: "#0f172a"
  text-secondary: "#475569"
  text-muted: "#64748b"
  text-disabled: "#94a3b8"
  status-active-text: "#065f46"
  status-active-bg: "#ecfdf5"
  status-active-hover-bg: "#d1fae5"
  status-active-border: "#a7f3d0"
  status-active-dot: "#10b981"
  status-overdue-text: "#991b1b"
  status-overdue-bg: "#fef2f2"
  status-overdue-border: "#fecaca"
  status-overdue-dot: "#ef4444"
  status-liquidation-text: "#92400e"
  status-liquidation-bg: "#fffbeb"
  status-liquidation-border: "#fde68a"
  status-liquidation-dot: "#f59e0b"
  status-redeemed-text: "#115e59"
  status-redeemed-bg: "#f0fdfa"
  status-redeemed-border: "#99f6e4"
  status-redeemed-dot: "#14b8a6"
  status-liquidated-text: "#374151"
  status-liquidated-bg: "#f3f4f6"
  status-liquidated-border: "#d1d5db"
  status-liquidated-dot: "#6b7280"
  category-car: "#0284c7"
  category-gold: "#d97706"
  category-laptop: "#6366f1"
typography:
  icon-hero:
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "2.5rem"
    fontWeight: 400
    lineHeight: 1
  display:
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "1.625rem"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.01em"
    fontFeature: "tnum"
  display-compact:
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 700
    lineHeight: 1.2
    fontFeature: "tnum"
  headline:
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "1.375rem"
    fontWeight: 700
    lineHeight: 1.25
    letterSpacing: "-0.02em"
  icon-lg:
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 400
    lineHeight: 1
  brand-title:
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "1.15rem"
    fontWeight: 700
    lineHeight: 1.1
    letterSpacing: "-0.02em"
  modal-title:
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "1.05rem"
    fontWeight: 700
    lineHeight: 1.35
  subtitle-md:
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "1rem"
    fontWeight: 600
    lineHeight: 1.35
  title:
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 700
    lineHeight: 1.35
  body:
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
  body-dense:
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 500
    lineHeight: 1.4
  caption-kpi:
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "0.775rem"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "0.04em"
  label:
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "0.04em"
  micro-hint:
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "0.72rem"
    fontWeight: 600
    lineHeight: 1.25
  micro-badge:
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "0.7rem"
    fontWeight: 700
    lineHeight: 1.2
  mono:
    fontFamily: "'JetBrains Mono', 'Fira Code', Menlo, Monaco, Consolas, monospace"
    fontSize: "0.8125rem"
    fontWeight: 600
    lineHeight: 1.4
    fontFeature: "tnum"
rounded:
  sm: "6px"
  md: "8px"
  lg: "12px"
  full: "9999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  2xl: "24px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.bg-surface}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: "8.8px 16px"
    height: "40px"
  button-primary-hover:
    backgroundColor: "{colors.primary-hover}"
    textColor: "{colors.bg-surface}"
  button-primary-active:
    backgroundColor: "{colors.primary-active}"
    textColor: "{colors.bg-surface}"
  button-tonal-success:
    backgroundColor: "{colors.status-active-bg}"
    textColor: "{colors.status-active-text}"
    rounded: "{rounded.md}"
    padding: "8.8px 16px"
    height: "40px"
  button-tonal-indigo:
    backgroundColor: "{colors.primary-light}"
    textColor: "{colors.primary}"
    rounded: "{rounded.md}"
    padding: "8.8px 16px"
    height: "40px"
  button-icon:
    backgroundColor: "{colors.bg-surface}"
    textColor: "{colors.text-secondary}"
    rounded: "{rounded.sm}"
    width: "32px"
    height: "32px"
  card-surface:
    backgroundColor: "{colors.bg-surface}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.lg}"
    padding: "20px"
  input-field:
    backgroundColor: "{colors.bg-surface}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.md}"
    padding: "8px 12px"
  badge-status-active:
    backgroundColor: "{colors.status-active-bg}"
    textColor: "{colors.status-active-text}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    padding: "4px 10.4px"
---

# Design System: PawnMaster

## Overview

**Creative North Star: "The Vault Ledger"**

PawnMaster is an authoritative, high-contrast, tabular-first operational workspace where banking precision meets rapid physical counter execution. Designed for long shifts inside brick-and-mortar pawnshop branches and accounting back offices, the visual system treats every screen as an auditable ledger instrument. Chrome stays quiet and structured—anchored by crisp 1px Slate borders, cool alabaster canvases, and high-contrast ink—so that Vietnamese Đồng (`₫`) amounts, contract identifiers, and collateral risk states command immediate visual priority.

Every interaction is engineered for speed and certainty under counter pressure. Primary counter workflows—originating a pledge contract, collecting periodic interest, and settling a collateral redemption—sit permanently in the top action bar with distinct chromatic identities. Inside tables, KPI cards, and inline calculators, numbers lock into strict vertical columns using tabular numerals (`tabular-nums`), eliminating eye-tracking fatigue across multi-million and multi-billion VND figures.

The system explicitly rejects decorative AI-era tropes: generic purple-to-blue gradients, unmotivated neon drop shadows, nested card-in-card containers, and raw emoji hacks are strictly banned. Visual energy is earned exclusively through typographic hierarchy, mathematical WCAG AA/AAA contrast, and calibrated semantic status badges.

**Key Characteristics:**
- **Operate-Mode Density:** Compact `14px` (`0.875rem`) base body scale and `13px` (`0.8125rem`) dense data grids that keep portfolio health, cash flow charts, and recent contracts visible with minimal scrolling.
- **Tabular Numeral Discipline:** Every currency value, contract code, national ID (`CCCD`), phone number, and timestamp enforces `font-variant-numeric: tabular-nums`.
- **Calibrated Semantic Triads:** Six contract and vault lifecycle states (`ACTIVE`, `OVERDUE`, `EXTENDED`, `WAITING_LIQUIDATION`, `REDEEMED`, `LIQUIDATED`), each pairing a soft tinted background, a `1px` harmonic border, dark WCAG AA-verified text, and a `6px` status indicator dot.
- **Single-Level Container Architecture:** Cards and tables sit directly on the cool slate canvas with a `1px` border and whisper elevation—never nested inside secondary card wrappers.
- **Intranet-Ready Resilience:** Google Fonts (`Inter` and `JetBrains Mono`) backed by comprehensive native OS font stacks and Bootstrap Icons `1.11.3` for deterministic rendering in restricted network environments.

## Colors

The palette balances deep institutional **Vault Cobalt** authority with a cool **Ledger Slate** neutral scale and six mathematically calibrated semantic status families, reserving high-chroma signals strictly for primary counter actions and portfolio risk states.

### Primary
- **Vault Cobalt** (`{colors.primary}`): Primary brand anchor used for the `.pm-brand-icon` shield, brand wordmark, primary `+ Cầm Đồ Mới` counter CTA, active pagination pills, and primary chart series. Achieves an **8.6:1** contrast ratio against pure white text (WCAG AAA).
- **Royal Cobalt Hover** (`{colors.primary-hover}`): Interactive hover and focus state for primary solid buttons.
- **Midnight Vault Active** (`{colors.primary-active}`): Pressed/active state for primary solid buttons and secondary chart accents.
- **Cobalt Wash** (`{colors.primary-light}`): Soft tint used for active filter tabs (`.pm-tab-btn.active`), tonal `Chuộc Đồ` buttons, user avatar chips, and `EXTENDED` contract badges.
- **Cobalt Hairline** (`{colors.primary-border}`): Crisp border stroke pairing with Cobalt Wash on active filter tabs and extended status pills.
- **Focus Azure** (`{colors.border-focus}`): Dedicated `2px` accessibility `:focus-visible` outline and input focus ring accent.

### Secondary
- **Clearing Emerald** (`{colors.status-active-text}` on `{colors.status-active-bg}`, border `{colors.status-active-border}`, dot `{colors.status-active-dot}`): Semantic family for active contracts in good standing (`ACTIVE` / *Đang cầm*), live cashbook balance pills (`.pm-meta-pill-cash`), positive KPI trends, and the tonal `Thu Lãi` quick-action button (**6.2:1** contrast ratio).
- **Settlement Teal** (`{colors.status-redeemed-text}` on `{colors.status-redeemed-bg}`, border `{colors.status-redeemed-border}`, dot `{colors.status-redeemed-dot}`): Semantic family for settled contracts and redeemed collateral (`REDEEMED` / *Đã chuộc*).

### Tertiary
- **Forfeiture Crimson** (`{colors.status-overdue-text}` on `{colors.status-overdue-bg}`, border `{colors.status-overdue-border}`, dot `{colors.status-overdue-dot}`): High-priority risk signal for overdue interest or defaulted contracts (`OVERDUE` / *Quá hạn*), due-today alerts (`.pm-due-today`), and overdue KPI risk counters (**6.1:1** contrast ratio).
- **Grace Amber** (`{colors.status-liquidation-text}` on `{colors.status-liquidation-bg}`, border `{colors.status-liquidation-border}`, dot `{colors.status-liquidation-dot}`): Warning signal for pledges approaching forfeiture or queued for asset liquidation (`WAITING_LIQUIDATION` / *Chờ thanh lý*, **5.9:1** contrast ratio).
- **Archive Pewter** (`{colors.status-liquidated-text}` on `{colors.status-liquidated-bg}`, border `{colors.status-liquidated-border}`, dot `{colors.status-liquidated-dot}`): Neutralized archival badge for completed collateral liquidations (`LIQUIDATED` / *Đã thanh lý*).

### Neutral
- **Vault Alabaster** (`{colors.bg-app}`): Root application canvas (`body`) and resting background for the table search input, minimizing glare during all-day shifts.
- **Ledger Paper White** (`{colors.bg-surface}`): Elevated surface for topbar navigation, KPI cards, chart containers, data table rows, and modal dialogs.
- **Slate Wash** (`{colors.bg-subtle}`): Secondary surface for sticky table headers (`th`), modal headers/footers, table pagination footers, inline calculation boxes (`.pm-calculation-box`), and metadata pills.
- **Hairline Slate** (`{colors.border-subtle}`): Standard `1px` structural divider for cards, table rows, and section boundaries.
- **Structural Slate** (`{colors.border-strong}`): Emphasized `1px` border for form inputs, selects, hovered KPI cards, and dashed calculation totals.
- **Ledger Ink** (`{colors.text-primary}`): Primary text for headings, KPI values, customer names, and monetary figures (**14.5:1** AAA contrast on white).
- **Counter Slate** (`{colors.text-secondary}`): Secondary text for form labels, table column headers, and KPI subtext (**6.5:1** AA contrast on white).
- **Timestamp Slate** (`{colors.text-muted}`): Muted helper text for timestamps, phone numbers, contract codes, and input hints (**4.6:1** AA contrast on white).
- **Dormant Slate** (`{colors.text-disabled}`): Reserved strictly for disabled controls and empty-state illustration icons.

### Named Rules
**The Chromatic Scarcity Rule.** High-saturation fills are reserved strictly for the primary `+ Cầm Đồ Mới` CTA and semantic status indicators; structural chrome, cards, headers, and table rows must remain on the Ledger Slate neutral ramp.

**The Semantic Triad Rule.** Every status badge and tonal alert must pair three coordinated tokens—a `50`-level tinted background, a `200`-level border, and an `800`-level text color (>= `4.5:1` contrast)—plus a `500`-level `.pm-badge-dot` so state is never communicated by hue alone.

## Typography

**Display Font:** `'Inter'` (with `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif` fallback)
**Body Font:** `'Inter'` (with system sans-serif fallback)
**Label/Mono Font:** `'JetBrains Mono'` (with `'Fira Code', Menlo, Monaco, Consolas, monospace` fallback for code/reference tokens)

**Character:** Clean, high-x-height neo-grotesque geometry engineered for dense Vietnamese diacritics and rapid numerical scanning, paired with crisp monospace precision for contract identifiers.

### Hierarchy
- **Display** (`700`, `1.625rem` / `26px`, line-height `1.2`, letter-spacing `-0.01em`, `tabular-nums`): Primary KPI card metrics (`.pm-kpi-value`); scales down to `1.5rem` (`24px`) at `≤1200px` and `1.375rem` (`22px`) at `≤992px`.
- **Headline** (`700`, `1.375rem` / `22px`, line-height `1.25`, letter-spacing `-0.02em`): Workspace page titles (`.pm-page-title`) and brand header title (`1.15rem` / `18.4px`).
- **Title** (`700`, `0.9375rem` / `15px` to `1.05rem` / `16.8px`, line-height `1.35`): Chart card headings (`.pm-chart-title`), modal dialog titles (`.pm-modal-title`), and inline settlement total rows.
- **Body** (`400`–`600`, `0.875rem` / `14px` global base; `0.8125rem` / `13px` in dense tables and form controls, line-height `1.5`): Operational table cells, customer names (`600`), modal form inputs, and subtext.
- **Label** (`700`, `0.75rem` / `12px` to `0.775rem` / `12.4px`, letter-spacing `0.04em`, `uppercase`): KPI card headers (`.pm-kpi-title`), dense table column headers (`.pm-table-dense th`), and status badge text (`600`, sentence case).

### Named Rules
**The Tabular Lock Rule.** Every element displaying currency (`.pm-currency`), contract IDs (`.pm-contract-code`), dates (`.pm-date`), phone numbers (`.pm-phone`), or KPI counters (`.pm-kpi-value`) must apply `.font-tabular` (`font-variant-numeric: tabular-nums; letter-spacing: -0.01em`) so digits align vertically across rows.

**The Diacritic Breathing Rule.** Vietnamese uppercase labels (`HỢP ĐỒNG ĐANG HOẠT ĐỘNG`, `QUÁ HẠN & CẦN THANH LÝ`) must maintain at least `1.2` line-height and `0.04em` positive tracking so stacked tonal marks never clip or collide.

## Layout

PawnMaster uses a fluid, full-viewport operational layout (`container-fluid` with `16px` mobile and `24px` desktop horizontal padding) organized on an `8px` spatial rhythm. The vertical architecture follows a strict four-tier counter hierarchy:
1. **Sticky Topbar (`.pm-topbar`):** Fixed at `top: 0` (`z-index: 1020`, `12px 24px` padding) housing brand identity, live branch status, real-time cashbook balance pill, live clock, and operator role chip.
2. **Workspace Header & Counter Action Bar (`.pm-page-header`):** Splits page context on the left (`col-md-6`) and the three primary counter modal triggers on the right (`gap: 10.4px` / `0.65rem`).
3. **4-Column KPI Summary Row (`row g-3 g-xl-4 mb-4`):** Responsive 4-up grid (`col-12 col-sm-6 col-xl-3`) with equal-height flex cards (`height: 100%`) and `20px` (`1.25rem`) internal padding.
4. **2-Column Analytics Split & Full-Width Operations Grid:** An asymmetric `8:4` desktop split (`col-xl-8` cash flow area chart + `col-xl-4` collateral portfolio donut chart), followed by the full-width 7-column contract operations table card (`.pm-table-card`).

**Responsive Breakpoints & Overflow Safeguards:**
- **Desktop (`>1200px`):** Full 4-column KPI row, `8:4` chart split, visible topbar telemetry pills, and `26px` KPI values.
- **Small Desktop / Landscape Tablet (`≤1200px`):** KPI row wraps to a `2×2` grid (`col-sm-6`); KPI value font size steps down to `24px` (`1.5rem`).
- **Tablet (`≤992px`):** Topbar telemetry pills (`.pm-header-meta`) hide to preserve brand bar legibility; table search input expands to `100%` width; KPI values step down to `22px` (`1.375rem`).
- **Mobile (`≤768px`):** Counter action bar wraps to full width (`justify-content: flex-start`); data tables scroll horizontally inside `.table-responsive` (`min-width: 860px` on `.pm-table-dense`) with zero horizontal viewport blowout.

## Elevation & Depth

PawnMaster follows a **Structural Border-First with Whisper Elevation** model. Spatial containment is established primarily by crisp `1px solid` Slate borders (`{colors.border-subtle}`) and three-tier tonal surface layering (`{colors.bg-app}` canvas → `{colors.bg-surface}` card → `{colors.bg-subtle}` header/footer). Shadows are neutral, low-opacity Slate casts (`rgba(15, 23, 42, 0.04..0.08)`) that provide subtle tactile separation at rest and responsive lift on hover or modal overlay.

### Shadow Vocabulary
- **Topbar Whisper** (`--pm-shadow-sm: 0 1px 2px 0 rgba(15, 23, 42, 0.04)`): Applied to the sticky `.pm-topbar` to separate scrolling workspace content from persistent navigation.
- **Resting Card Depth** (`--pm-shadow-card: 0 1px 3px 0 rgba(15, 23, 42, 0.05), 0 1px 2px -1px rgba(15, 23, 42, 0.05)`): Default elevation for `.pm-kpi-card`, `.pm-chart-card`, and `.pm-table-card`.
- **Interactive Hover Lift** (`--pm-shadow-hover: 0 4px 6px -1px rgba(15, 23, 42, 0.08), 0 2px 4px -2px rgba(15, 23, 42, 0.05)`): Paired with border darkening (`{colors.border-strong}`) when hovering over KPI summary cards.
- **Modal & Overlay Float** (`--pm-shadow-dropdown: 0 10px 15px -3px rgba(15, 23, 42, 0.08), 0 4px 6px -4px rgba(15, 23, 42, 0.04)`): Applied to operational modal dialogs (`.modal-content.pm-modal`) and floating overlays.

### Named Rules
**The Zero-Neon Rule.** Colored or luminescent drop shadows (`rgba(0, 255, ...)`, `#00ffcc`, or saturated brand glows) are strictly prohibited; all elevation shadows must derive from neutral Slate Ink (`rgb(15, 23, 42)`) at `≤8%` opacity.

**The Single-Shell Rule.** A shadowed `.pm-kpi-card`, `.pm-chart-card`, or `.pm-table-card` may never contain a nested `.card` child; internal groupings (such as `.pm-calculation-box` inside modals) use flat `{colors.bg-subtle}` fills and `1px` borders with `box-shadow: none`.

## Shapes

The form language uses a disciplined four-step corner radius scale calibrated to component hierarchy:
- **Compact Control Radius** (`{rounded.sm}` / `6px`): Used for dense inline elements—table row action icon buttons (`.pm-btn-icon`), collateral category thumbnails (`.pm-collateral-icon`), KPI trend pills (`.pm-kpi-trend`), due-date tags (`.pm-due-badge`), chart timeframe group containers, and pagination buttons.
- **Interactive Control Radius** (`{rounded.md}` / `8px`): Used for primary and tonal counter buttons (`.pm-btn`), form inputs and selects (`.pm-form-control`), table filter tabs (`.pm-tab-btn`), search inputs, KPI icon chips (`.pm-icon-chip`), brand shield icon, and modal calculation boxes.
- **Container Surface Radius** (`{rounded.lg}` / `12px`): Used for top-level structural containers—KPI cards (`.pm-kpi-card`), chart cards (`.pm-chart-card`), the data table card (`.pm-table-card` with `overflow: hidden`), and modal dialog shells (`.pm-modal`).
- **Pill & Indicator Radius** (`{rounded.full}` / `9999px`): Used for contract status badges (`.pm-badge`), topbar metadata pills (`.pm-meta-pill`), user profile chips (`.pm-user-chip`), filter counter badges (`.pm-tab-badge`), and `6px`/`8px` circular status dots.

Borders are uniformly `1px solid` across all surfaces, switching to `1px dashed {colors.border-strong}` exclusively for the final settlement total divider inside `.pm-calculation-box`.

## Components

Components are crisp, high-utility, and unmistakable—built with `40px` minimum counter touch targets, semantic tonal pairings, and deterministic `150ms–200ms cubic-bezier(0.4, 0, 0.2, 1)` state transitions.

### Buttons
- **Shape:** Medium interactive radius (`8px` / `{rounded.md}`) for standard buttons (`min-height: 40px`, `padding: 0.55rem 1rem`, `gap: 0.5rem`, `font-weight: 600`); compact radius (`6px` / `{rounded.sm}`) for `32px × 32px` table row icon buttons (`.pm-btn-icon`).
- **Primary (`.pm-btn-primary`):** Solid Vault Cobalt (`{colors.primary}`) background and `1px` border with pure white text (`#ffffff`); used exclusively for the primary counter action (`+ Cầm Đồ Mới`) and primary modal submissions.
- **Hover / Focus:** Transitions in `150ms` to Royal Cobalt (`{colors.primary-hover}`) on hover and Midnight Vault (`{colors.primary-active}`) on press; keyboard focus displays a `2px solid {colors.border-focus}` ring with `2px` offset.
- **Tonal Secondary Variants:**
  - **Tonal Success (`.pm-btn-success-tonal`):** Clearing Emerald tint (`{colors.status-active-bg}` fill, `{colors.status-active-text}` label, `{colors.status-active-border}` border; hovers to `#d1fae5` fill / `#6ee7b7` border) for `Thu Lãi` (Collect Interest).
  - **Tonal Indigo (`.pm-btn-indigo-tonal`):** Cobalt Wash tint (`{colors.primary-light}` fill, `{colors.primary}` label, `{colors.primary-border}` border; hovers to `#dbeafe` fill / `#93c5fd` border) for `Chuộc Đồ` (Redeem Collateral).
  - **Row Icon Action (`.pm-btn-icon`):** `32px × 32px` surface button with `{colors.border-subtle}` border and `{colors.text-secondary}` icon; hovers to `{colors.bg-subtle}` fill and `{colors.primary}` icon color.

### Chips & Status Badges
- **Contract Status Badges (`.pm-badge`):** Full-pill radius (`9999px`), `padding: 0.25rem 0.65rem`, `font-size: 0.75rem`, `font-weight: 600`, `gap: 0.375rem`, leading with a `6px × 6px` circular `.pm-badge-dot`. Variants cover `.pm-badge-active`, `.pm-badge-overdue`, `.pm-badge-extended`, `.pm-badge-liquidation`, `.pm-badge-redeemed`, and `.pm-badge-liquidated`.
- **Topbar Meta Pills (`.pm-meta-pill`):** Full-pill radius (`9999px`), `padding: 0.4rem 0.85rem`, `font-size: 0.8125rem`, `{colors.bg-subtle}` background with `{colors.border-subtle}` border; `.pm-meta-pill-cash` overrides to Clearing Emerald (`#ecfdf5` bg, `#a7f3d0` border, `#065f46` text) to highlight available vault cash.
- **Filter Tabs (`.pm-tab-btn`):** `8px` radius, `padding: 0.4rem 0.85rem`, `font-size: 0.8125rem`, `font-weight: 600` with embedded `.pm-tab-badge` counter pill. Unselected tabs sit transparent; `.active` shifts to `{colors.primary-light}` background, `{colors.primary}` text, and `{colors.primary-border}` border, inverting the counter badge to solid `{colors.primary}` with white text.

### Cards / Containers
- **Corner Style:** Large container radius (`12px` / `{rounded.lg}`).
- **Background:** Pure white (`{colors.bg-surface}`) for card bodies; `{colors.bg-subtle}` for table headers, table footers, and modal headers/footers.
- **Shadow Strategy:** Resting `--pm-shadow-card`; `.pm-kpi-card` elevates to `--pm-shadow-hover` with `{colors.border-strong}` border on hover (`200ms` transition).
- **Border:** `1px solid {colors.border-subtle}`, never nested.
- **Internal Padding:** `1.15rem 1.25rem` (`18.4px 20px`) on KPI cards; `1.25rem` (`20px`) on chart cards; `1rem 1.25rem` (`16px 20px`) on table toolbars; `0.75rem 1rem` (`12px 16px`) on dense table cells.

### Inputs / Fields
- **Style:** `1px solid {colors.border-strong}` border on standard modal inputs/selects (`.pm-form-control`, `.pm-form-select`), `8px` radius (`{rounded.md}`), `padding: 0.5rem 0.75rem`, `font-size: 0.8125rem`, pure white background. The toolbar search input (`.pm-search-input`) rests on `{colors.bg-app}` with `{colors.border-subtle}` border and a left-anchored `.pm-search-icon`.
- **Focus:** Shifts background to `#ffffff`, border to `{colors.border-focus}` (`#3b82f6`), and applies a soft focus ring (`box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15)`).
- **Labels & Hints:** Labels (`.pm-form-label`) use `0.8125rem` (`13px`), `font-weight: 600`, `{colors.text-secondary}`; helper hints (`.pm-form-hint`) use `0.72rem` (`11.5px`), `{colors.text-muted}`.

### Navigation
- **Sticky Topbar (`.pm-topbar`):** Single horizontal bar (`background: #ffffff`, `border-bottom: 1px solid #e2e8f0`, `padding: 0.75rem 1.5rem`). Left cluster holds the `38px × 38px` Vault Cobalt shield icon (`.pm-brand-icon`), bold `PawnMaster` title, uppercase `Hệ Thống Quản Lý Cầm Đồ` subtitle, and live branch status pill (`.pm-live-dot` with `2px` emerald ring). Right cluster holds the cashbook balance pill, live tabular clock, and `.pm-user-chip` with a `32px` circular initials avatar.

### Counter Calculation Box & Operational Modals
- **Operational Modals (`.pm-modal`):** Three core counter dialogs (`#modalNewContract`, `#modalCollectInterest`, `#modalRedeemCollateral`) framed with `{colors.bg-subtle}` headers and footers (`padding: 1.1rem 1.5rem` / `1rem 1.5rem`) and a `1.5rem` (`24px`) body.
- **Inline Calculation Box (`.pm-calculation-box`):** Embedded inside counter modals on a `{colors.bg-subtle}` surface (`1px solid {colors.border-subtle}`, `8px` radius, `padding: 0.85rem 1rem`). Displays itemized fee/interest rows (`.pm-calc-row`) in `13px` secondary text and separates the final settlement amount with a `1px dashed {colors.border-strong}` top border in `15px` bold `{colors.text-primary}` tabular numerals.

## Do's and Don'ts

### Do:
- **Do** apply `.font-tabular` (`font-variant-numeric: tabular-nums`) to every element containing VND currency amounts, contract codes (`HD-2026-...`), dates, phone numbers, national IDs (`CCCD`), and KPI figures.
- **Do** right-align (`text-end`) monetary columns and their matching `<th>` headers inside `.pm-table-dense` so decimal/thousand separators (`1.845.000.000 ₫`) align vertically.
- **Do** pair every status badge (`.pm-badge-*`) with both its `.pm-badge-dot` indicator and explicit Vietnamese status label to preserve WCAG AA comprehension for color-vision-deficient operators.
- **Do** maintain XML-valid self-closing void tags (`<meta />`, `<link />`, `<input />`, `<hr />`, `<br />`) and dual static/Thymeleaf resource attributes (`href="../static/..."` alongside `th:href="@{/...}"`) in HTML templates.
- **Do** wrap dense operational tables in `.table-responsive` with `min-width: 860px` on `.pm-table-dense` so tablet and mobile viewports scroll horizontally within the table card rather than blowing out the page width.

### Don't:
- **Don't** use generic AI purple-to-blue linear gradients (`#667eea`, `#764ba2`, `#8b5cf6`) anywhere in backgrounds, buttons, badges, or text fills.
- **Don't** use neon glow drop shadows (`rgba(0, 255, ...)`, `#00ffcc`, `#39ff14`) or high-opacity colored shadows on cards, buttons, or charts.
- **Don't** nest `.card` inside another `.card` (`.card .card`); use flat `{colors.bg-subtle}` panels with `1px` borders (such as `.pm-calculation-box`) when grouping elements inside a modal or card.
- **Don't** use raw Unicode emojis (`💰`, `📈`, `⚠️`, `🔍`, `✏️`, `❌`, `➕`) as UI icons; use Bootstrap Icons (`bi-*`) exclusively.
- **Don't** place body, label, or badge text on any background that drops below a **4.5:1** WCAG AA contrast ratio (`{colors.text-disabled}` is forbidden for readable copy or placeholders).
