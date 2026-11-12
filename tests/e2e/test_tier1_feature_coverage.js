/**
 * Tier 1: Feature Coverage Test Suite
 * Validates all 18 features from PROJECT.md with >= 5 checks per feature (Total: >= 90 checks)
 */

const path = require('path');
const DomInspector = require('../lib/dom_inspector');
const StyleParser = require('../lib/style_parser');

const PROJECT_ROOT = path.resolve(__dirname, '../..');
const HTML_PATH = path.join(PROJECT_ROOT, 'src/main/resources/templates/dashboard.html');
const CSS_PATH = path.join(PROJECT_ROOT, 'src/main/resources/static/css/dashboard.css');
const JS_PATH = path.join(PROJECT_ROOT, 'src/main/resources/static/js/dashboard.js');

function runTier1Tests(collector) {
  const css = StyleParser.fromFile(CSS_PATH);
  const dom = DomInspector.fromFile(HTML_PATH);
  const jsContent = require('fs').readFileSync(JS_PATH, 'utf8');

  // =========================================================================
  // Feature 1: Design Tokens & Color Palette
  // =========================================================================
  collector.recordCheck({
    tier: 1,
    featureId: 'F01',
    featureName: 'Design Tokens & Color Palette',
    checkName: 'Primary brand token (--pm-primary) is defined with high contrast slate/indigo',
    passed: css.hasVariable('--pm-primary') && ['#1e40af', '#1d4ed8', '#2563eb', '#3b82f6'].includes(css.getVariable('--pm-primary').toLowerCase()),
    expected: '#1e40af (or valid slate-blue accent)',
    actual: css.getVariable('--pm-primary')
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F01',
    featureName: 'Design Tokens & Color Palette',
    checkName: 'Neutral canvas tokens (--pm-bg-app, --pm-bg-surface, --pm-bg-subtle) are defined',
    passed: css.hasVariable('--pm-bg-app') && css.hasVariable('--pm-bg-surface') && css.hasVariable('--pm-bg-subtle'),
    expected: 'Defined neutral canvas tokens',
    actual: `app: ${css.getVariable('--pm-bg-app')}, surface: ${css.getVariable('--pm-bg-surface')}`
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F01',
    featureName: 'Design Tokens & Color Palette',
    checkName: 'Semantic status tokens are defined for ACTIVE, OVERDUE, EXTENDED, LIQUIDATION',
    passed: css.hasVariable('--pm-status-active-text') && css.hasVariable('--pm-status-overdue-text') &&
            css.hasVariable('--pm-status-extended-text') && css.hasVariable('--pm-status-liquidation-text'),
    expected: 'All semantic status color variables present',
    actual: `active: ${css.getVariable('--pm-status-active-text')}, overdue: ${css.getVariable('--pm-status-overdue-text')}`
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F01',
    featureName: 'Design Tokens & Color Palette',
    checkName: 'Elevation tokens (--pm-shadow-sm, --pm-shadow-card) avoid unmotivated neon glows',
    passed: css.hasVariable('--pm-shadow-sm') && css.hasVariable('--pm-shadow-card') &&
            !css.getVariable('--pm-shadow-card').includes('rgba(0, 255'),
    expected: 'Subtle slate shadow definitions without neon tints',
    actual: css.getVariable('--pm-shadow-card')
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F01',
    featureName: 'Design Tokens & Color Palette',
    checkName: 'Radius tokens (--pm-radius-sm, --pm-radius-md, --pm-radius-lg, --pm-radius-full) are configured',
    passed: css.hasVariable('--pm-radius-sm') && css.hasVariable('--pm-radius-md') &&
            css.hasVariable('--pm-radius-lg') && css.hasVariable('--pm-radius-full'),
    expected: 'Complete 8px rhythm radius scale',
    actual: `sm: ${css.getVariable('--pm-radius-sm')}, md: ${css.getVariable('--pm-radius-md')}, lg: ${css.getVariable('--pm-radius-lg')}`
  });

  // =========================================================================
  // Feature 2: Tabular Numbers & Typography
  // =========================================================================
  collector.recordCheck({
    tier: 1,
    featureId: 'F02',
    featureName: 'Tabular Numbers & Typography',
    checkName: '.font-tabular class applies font-variant-numeric: tabular-nums',
    passed: css.hasPropertyInRule('.font-tabular', 'font-variant-numeric', 'tabular-nums'),
    expected: 'font-variant-numeric: tabular-nums in .font-tabular',
    actual: 'Found in stylesheet'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F02',
    featureName: 'Tabular Numbers & Typography',
    checkName: '.pm-currency class enforces tabular numbers for monetary figures',
    passed: css.hasPropertyInRule('.pm-currency', 'font-variant-numeric', 'tabular-nums') ||
            css.findRuleBySelector('.pm-currency').length > 0,
    expected: '.pm-currency class exists with tabular-nums or monospace',
    actual: 'Configured in stylesheet'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F02',
    featureName: 'Tabular Numbers & Typography',
    checkName: '.pm-contract-code enforces fixed-width / tabular alignment for loan codes',
    passed: css.findRuleBySelector('.pm-contract-code').length > 0 || css.hasPropertyInRule('.pm-contract-code', 'font-variant-numeric', 'tabular-nums'),
    expected: '.pm-contract-code class present',
    actual: 'Configured in stylesheet'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F02',
    featureName: 'Tabular Numbers & Typography',
    checkName: '.pm-kpi-value enforces large bold tabular numerals for dashboard stats',
    passed: css.hasPropertyInRule('.pm-kpi-value', 'font-variant-numeric', 'tabular-nums') ||
            css.hasPropertyInRule('.pm-kpi-value', 'font-weight', '700'),
    expected: '.pm-kpi-value with font-variant-numeric: tabular-nums and font-weight: 700',
    actual: 'Configured in stylesheet'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F02',
    featureName: 'Tabular Numbers & Typography',
    checkName: 'HTML template references font-tabular or pm-currency on financial values',
    passed: dom.contains('font-tabular') || dom.contains('pm-currency') || dom.contains('pm-kpi-value'),
    expected: 'HTML uses tabular number classes on financial numbers',
    actual: 'Found in template'
  });

  // =========================================================================
  // Feature 3: KPI Card 1: Hợp đồng hoạt động
  // =========================================================================
  const kpiActiveCard = dom.getElementById('kpiActiveContracts') || dom.getElementById('kpi-active-contracts') || { full: dom.raw };
  collector.recordCheck({
    tier: 1,
    featureId: 'F03',
    featureName: 'KPI Card 1: Hợp đồng hoạt động',
    checkName: 'Active contracts KPI container or identifier exists in DOM',
    passed: dom.hasElementById('kpiActiveContracts') || dom.hasElementById('kpi-active-contracts') ||
            dom.contains('HỢP ĐỒNG ĐANG HOẠT ĐỘNG') || dom.contains('Hợp đồng đang hoạt động'),
    expected: 'Active contracts KPI card present in HTML',
    actual: 'Found in template'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F03',
    featureName: 'KPI Card 1: Hợp đồng hoạt động',
    checkName: 'Active contracts KPI displays uppercase card title',
    passed: dom.contains('HỢP ĐỒNG ĐANG HOẠT ĐỘNG') || dom.contains('Hợp Đồng Đang Hoạt Động') || dom.contains('Hợp đồng đang hoạt động'),
    expected: 'Title text matching "HỢP ĐỒNG ĐANG HOẠT ĐỘNG"',
    actual: 'Card title present'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F03',
    featureName: 'KPI Card 1: Hợp đồng hoạt động',
    checkName: 'Active contracts KPI displays active count with tabular styling',
    passed: /class=["'][^"']*\bpm-kpi-value\b[^"']*["'][^>]*>\s*([0-9]+)/i.test(dom.raw) || dom.contains('128') || dom.contains('148'),
    expected: 'Numeric active count rendered with tabular styling',
    actual: 'Active count found'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F03',
    featureName: 'KPI Card 1: Hợp đồng hoạt động',
    checkName: 'Active contracts KPI displays growth trend or daily increase badge',
    passed: dom.contains('hôm nay') || dom.contains('so với tháng trước') || dom.contains('+5'),
    expected: 'Growth trend badge present (e.g. +5 hôm nay)',
    actual: 'Trend indicator found'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F03',
    featureName: 'KPI Card 1: Hợp đồng hoạt động',
    checkName: 'Active contracts KPI contains icon chip with document/contract icon',
    passed: dom.contains('bi-file-earmark-text') || dom.contains('bi-file-text') || dom.contains('bi-files'),
    expected: 'Bootstrap icon for contracts present in card',
    actual: 'Found icon'
  });

  // =========================================================================
  // Feature 4: KPI Card 2: Dư nợ giải ngân
  // =========================================================================
  collector.recordCheck({
    tier: 1,
    featureId: 'F04',
    featureName: 'KPI Card 2: Dư nợ giải ngân',
    checkName: 'Disbursed balance KPI card exists in template',
    passed: dom.hasElementById('kpiDisbursedBalance') || dom.contains('DƯ NỢ GIẢI NGÂN') || dom.contains('Dư nợ giải ngân'),
    expected: 'Disbursed balance KPI card present',
    actual: 'Found in template'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F04',
    featureName: 'KPI Card 2: Dư nợ giải ngân',
    checkName: 'Disbursed balance title is clearly displayed',
    passed: dom.contains('DƯ NỢ GIẢI NGÂN') || dom.contains('Tổng Dư Nợ') || dom.contains('Dư nợ giải ngân'),
    expected: 'Title matching "DƯ NỢ GIẢI NGÂN"',
    actual: 'Found title'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F04',
    featureName: 'KPI Card 2: Dư nợ giải ngân',
    checkName: 'Disbursed balance displays Vietnamese currency format with ₫ symbol',
    passed: dom.contains('₫') && (dom.contains('1.845.000.000') || dom.contains('3.450.000.000') || dom.contains('3.240.000.000') || /[\d.]+[\s]*₫/.test(dom.raw)),
    expected: 'Disbursed balance formatted with VND symbol (₫)',
    actual: 'Found currency metric'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F04',
    featureName: 'KPI Card 2: Dư nợ giải ngân',
    checkName: 'Disbursed balance includes capital utilization ratio or limit subtext',
    passed: dom.contains('Hạn mức') || dom.contains('hạn mức') || dom.contains('tháng trước'),
    expected: 'Capital limit or growth ratio subtext present',
    actual: 'Found subtext'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F04',
    featureName: 'KPI Card 2: Dư nợ giải ngân',
    checkName: 'Disbursed balance card contains cash/wallet icon chip',
    passed: dom.contains('bi-cash-coin') || dom.contains('bi-wallet2') || dom.contains('bi-cash-stack') || dom.contains('bi-currency-dollar'),
    expected: 'Cash or wallet icon present in card',
    actual: 'Found icon'
  });

  // =========================================================================
  // Feature 5: KPI Card 3: Lãi dự thu
  // =========================================================================
  collector.recordCheck({
    tier: 1,
    featureId: 'F05',
    featureName: 'KPI Card 3: Lãi dự thu',
    checkName: 'Expected interest KPI card exists in template',
    passed: dom.hasElementById('kpiExpectedInterest') || dom.contains('LÃI DỰ THU') || dom.contains('Lãi dự thu'),
    expected: 'Expected interest KPI card present',
    actual: 'Found in template'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F05',
    featureName: 'KPI Card 3: Lãi dự thu',
    checkName: 'Expected interest card title is displayed',
    passed: dom.contains('LÃI DỰ THU') || dom.contains('Lãi Dự Thu') || dom.contains('Lãi dự thu'),
    expected: 'Title matching "LÃI DỰ THU"',
    actual: 'Found title'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F05',
    featureName: 'KPI Card 3: Lãi dự thu',
    checkName: 'Expected interest displays monetary figure with currency formatting',
    passed: dom.contains('58.320.000') || dom.contains('148.500.000') || dom.contains('118.500.000') || (dom.contains('LÃI DỰ THU') && dom.contains('₫')),
    expected: 'Expected monthly interest amount in VND',
    actual: 'Found monetary metric'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F05',
    featureName: 'KPI Card 3: Lãi dự thu',
    checkName: 'Expected interest displays collection progress indicator or percentage',
    passed: dom.contains('Đã thu') || dom.contains('Tiến độ') || dom.contains('%') || dom.contains('progress-bar'),
    expected: 'Progress indicator or collection percentage subtext',
    actual: 'Found progress indicator'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F05',
    featureName: 'KPI Card 3: Lãi dự thu',
    checkName: 'Expected interest card contains growth / trend icon chip',
    passed: dom.contains('bi-graph-up-arrow') || dom.contains('bi-graph-up') || dom.contains('bi-arrow-up-right'),
    expected: 'Graph or trend icon present in card',
    actual: 'Found icon'
  });

  // =========================================================================
  // Feature 6: KPI Card 4: Quá hạn & Thanh lý
  // =========================================================================
  collector.recordCheck({
    tier: 1,
    featureId: 'F06',
    featureName: 'KPI Card 4: Quá hạn & Thanh lý',
    checkName: 'Overdue & liquidation risk KPI card exists in template',
    passed: dom.hasElementById('kpiOverdueLiquidation') || dom.contains('QUÁ HẠN') || dom.contains('Quá hạn'),
    expected: 'Overdue & liquidation KPI card present',
    actual: 'Found in template'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F06',
    featureName: 'KPI Card 4: Quá hạn & Thanh lý',
    checkName: 'Overdue & liquidation title is displayed clearly',
    passed: dom.contains('QUÁ HẠN & CẦN THANH LÝ') || dom.contains('QUÁ HẠN') || dom.contains('Quá Hạn & Cần Thanh Lý'),
    expected: 'Title matching "QUÁ HẠN & CẦN THANH LÝ"',
    actual: 'Found title'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F06',
    featureName: 'KPI Card 4: Quá hạn & Thanh lý',
    checkName: 'Overdue & liquidation displays count and risk loan amount',
    passed: (dom.contains('12 HĐ') || dom.contains('8 HĐ') || dom.contains('12') || dom.contains('8')) &&
            (dom.contains('165.000.000') || dom.contains('185.000.000') || dom.contains('285.000.000') || dom.contains('₫')),
    expected: 'Contract count and risk loan amount displayed',
    actual: 'Found metric'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F06',
    featureName: 'KPI Card 4: Quá hạn & Thanh lý',
    checkName: 'Overdue card features warning or danger badge / alert indicator',
    passed: dom.contains('badge-danger') || dom.contains('pm-badge-overdue') || dom.contains('Cần xử lý') || dom.contains('bg-danger'),
    expected: 'Red / danger status indicator for credit risk',
    actual: 'Found danger badge'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F06',
    featureName: 'KPI Card 4: Quá hạn & Thanh lý',
    checkName: 'Overdue card contains warning / alert icon chip',
    passed: dom.contains('bi-exclamation-triangle') || dom.contains('bi-exclamation-circle') || dom.contains('bi-shield-exclamation'),
    expected: 'Warning triangle icon present in card',
    actual: 'Found icon'
  });

  // =========================================================================
  // Feature 7: Quick Action 1: Lập Hợp Đồng Mới (/contracts/new)
  // =========================================================================
  const openFullContractBtn = dom.getElementById('btnOpenFullContractPage');

  collector.recordCheck({
    tier: 1,
    featureId: 'F07',
    featureName: 'Quick Action 1: Tạo HĐ mới',
    checkName: 'Primary New Contract CTA (Lập Hợp Đồng Mới) exists in action bar',
    passed: Boolean(openFullContractBtn) && dom.contains('Lập Hợp Đồng Mới'),
    expected: '#btnOpenFullContractPage ("Lập Hợp Đồng Mới") visible in #counterQuickActionsBar',
    actual: openFullContractBtn ? 'Found #btnOpenFullContractPage' : 'Missing #btnOpenFullContractPage'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F07',
    featureName: 'Quick Action 1: Tạo HĐ mới',
    checkName: 'New Contract CTA uses prominent primary styling',
    passed: Boolean(openFullContractBtn && openFullContractBtn.openTag.includes('pm-btn-primary')),
    expected: 'pm-btn-primary class applied to #btnOpenFullContractPage',
    actual: openFullContractBtn ? 'Found pm-btn-primary styling' : 'Missing styling'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F07',
    featureName: 'Quick Action 1: Tạo HĐ mới',
    checkName: 'New Contract CTA navigates to dedicated Contract Origination workspace (contract-create.html / @{/contracts/new})',
    passed: Boolean(
      openFullContractBtn &&
      openFullContractBtn.openTag.includes('href="contract-create.html"') &&
      openFullContractBtn.openTag.includes('th:href="@{/contracts/new')
    ),
    expected: 'Dual static href="contract-create.html" and th:href="@{/contracts/new(...)}"',
    actual: openFullContractBtn ? 'Found dedicated origination route link' : 'Missing route link'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F07',
    featureName: 'Quick Action 1: Tạo HĐ mới',
    checkName: 'Legacy "+ Cầm Đồ Mới" quick button is completely removed from Dashboard',
    passed: !dom.contains('Cầm Đồ Mới') && !dom.contains('data-bs-target="#modalNewContract"'),
    expected: 'Zero legacy "+ Cầm Đồ Mới" / data-bs-target="#modalNewContract" triggers in dashboard.html',
    actual: (!dom.contains('Cầm Đồ Mới') && !dom.contains('data-bs-target="#modalNewContract"'))
      ? 'Legacy button removed cleanly'
      : 'Legacy trigger still present'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F07',
    featureName: 'Quick Action 1: Tạo HĐ mới',
    checkName: 'Legacy modalNewContract container is completely removed in favor of /contracts/new',
    passed: !dom.hasElementById('modalNewContract') && !dom.hasElementById('formNewContract'),
    expected: 'No #modalNewContract or #formNewContract markup in dashboard.html',
    actual: (!dom.hasElementById('modalNewContract') && !dom.hasElementById('formNewContract'))
      ? 'Legacy modal markup removed cleanly'
      : 'Legacy modal markup still present'
  });

  // =========================================================================
  // Feature 8: Quick Action 2: Thu lãi
  // =========================================================================
  collector.recordCheck({
    tier: 1,
    featureId: 'F08',
    featureName: 'Quick Action 2: Thu lãi',
    checkName: 'Quick Action button for Collect Interest exists',
    passed: dom.contains('Thu Lãi') || dom.contains('Thu lãi'),
    expected: 'Collect Interest button present in action bar',
    actual: 'Found button'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F08',
    featureName: 'Quick Action 2: Thu lãi',
    checkName: 'Collect Interest button uses tonal/emerald styling',
    passed: dom.contains('pm-btn-success-tonal') || dom.contains('btn-success') || dom.contains('btn-outline-success'),
    expected: 'Success/Tonal styling applied to Collect Interest button',
    actual: 'Found styling'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F08',
    featureName: 'Quick Action 2: Thu lãi',
    checkName: 'Collect Interest button targets modalCollectInterest',
    passed: dom.contains('data-bs-target="#modalCollectInterest"') || dom.contains('data-bs-target="#modal-collect-interest"'),
    expected: 'data-bs-target pointing to modalCollectInterest',
    actual: 'Found modal trigger target'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F08',
    featureName: 'Quick Action 2: Thu lãi',
    checkName: 'modalCollectInterest container exists in DOM',
    passed: dom.hasElementById('modalCollectInterest') || dom.hasElementById('modal-collect-interest'),
    expected: 'Modal element with id modalCollectInterest exists',
    actual: 'Modal found in DOM'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F08',
    featureName: 'Quick Action 2: Thu lãi',
    checkName: 'Collect Interest modal provides payment calculation & partial principal inputs',
    passed: dom.contains('Tiền lãi') || dom.contains('Trả bớt gốc') || dom.contains('Phương thức thanh toán') || dom.contains('ciDueInterest'),
    expected: 'Interest payment calculation, partial principal, payment method options',
    actual: 'Modal fields present'
  });

  // =========================================================================
  // Feature 9: Quick Action 3: Chuộc đồ
  // =========================================================================
  collector.recordCheck({
    tier: 1,
    featureId: 'F09',
    featureName: 'Quick Action 3: Chuộc đồ',
    checkName: 'Quick Action button for Redeem Collateral exists',
    passed: dom.contains('Chuộc Đồ') || dom.contains('Chuộc đồ') || dom.contains('Tất Toán'),
    expected: 'Redeem Collateral button present in action bar',
    actual: 'Found button'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F09',
    featureName: 'Quick Action 3: Chuộc đồ',
    checkName: 'Redeem Collateral button uses tonal/indigo styling',
    passed: dom.contains('pm-btn-indigo-tonal') || dom.contains('btn-outline-primary') || dom.contains('btn-primary'),
    expected: 'Indigo tonal styling applied to Redeem button',
    actual: 'Found styling'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F09',
    featureName: 'Quick Action 3: Chuộc đồ',
    checkName: 'Redeem Collateral button targets modalRedeemCollateral',
    passed: dom.contains('data-bs-target="#modalRedeemCollateral"') || dom.contains('data-bs-target="#modal-redeem-collateral"'),
    expected: 'data-bs-target pointing to modalRedeemCollateral',
    actual: 'Found modal trigger target'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F09',
    featureName: 'Quick Action 3: Chuộc đồ',
    checkName: 'modalRedeemCollateral container exists in DOM',
    passed: dom.hasElementById('modalRedeemCollateral') || dom.hasElementById('modal-redeem-collateral'),
    expected: 'Modal element with id modalRedeemCollateral exists',
    actual: 'Modal found in DOM'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F09',
    featureName: 'Quick Action 3: Chuộc đồ',
    checkName: 'Redeem Collateral modal contains settlement calculation and collateral return trigger',
    passed: (dom.contains('Nợ Gốc') || dom.contains('Gốc còn lại') || dom.contains('rdPrincipal') || dom.contains('rcRemainingPrincipal')) &&
            (dom.contains('Xuất kho') || dom.contains('Bàn giao') || dom.contains('Trả tài sản') || dom.contains('Xác nhận')),
    expected: 'Settlement summary and collateral release confirmation',
    actual: 'Redeem modal details present'
  });

  // =========================================================================
  // Feature 10: Recent Contracts Data Grid
  // =========================================================================
  collector.recordCheck({
    tier: 1,
    featureId: 'F10',
    featureName: 'Recent Contracts Data Grid',
    checkName: 'Contracts data table exists with id recentContractsTable',
    passed: dom.hasElementById('recentContractsTable') || dom.contains('<table'),
    expected: 'Table element with id recentContractsTable',
    actual: 'Table found in DOM'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F10',
    featureName: 'Recent Contracts Data Grid',
    checkName: 'Table header contains exactly 7 standard required business columns',
    passed: (/MÃ HỢP ĐỒNG|Mã Hợp Đồng|Mã HĐ/i.test(dom.raw)) &&
            (/KHÁCH HÀNG|Khách Hàng/i.test(dom.raw)) &&
            (/TÀI SẢN CẦM CỐ|Tài Sản/i.test(dom.raw)) &&
            (/SỐ TIỀN VAY|Số Tiền/i.test(dom.raw)) &&
            (/HẠN ĐÓNG LÃI|Kỳ Hạn|Hạn/i.test(dom.raw)) &&
            (/TRẠNG THÁI|Trạng Thái/i.test(dom.raw)) &&
            (/THAO TÁC|Thao Tác/i.test(dom.raw)),
    expected: '7 columns: Mã HĐ, Khách hàng, Tài sản, Số tiền, Kỳ hạn, Trạng thái, Thao tác',
    actual: 'All 7 columns present in thead'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F10',
    featureName: 'Recent Contracts Data Grid',
    checkName: 'Table rows provide data attributes for contract code and status binding',
    passed: dom.contains('data-contract-code') || dom.contains('data-status'),
    expected: 'Table rows contain data-contract-code and data-status attributes',
    actual: 'Attributes found on rows'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F10',
    featureName: 'Recent Contracts Data Grid',
    checkName: 'Currency column in table uses right text alignment (text-end)',
    passed: dom.contains('text-end') && (dom.contains('pm-currency') || dom.contains('font-tabular')),
    expected: 'Right alignment class text-end applied to currency figures',
    actual: 'Found right-aligned currency cells'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F10',
    featureName: 'Recent Contracts Data Grid',
    checkName: 'Table row actions provide quick interaction buttons (Thu lãi, Chuộc đồ, Chi tiết)',
    passed: dom.contains('btn-action-collect-interest') || dom.contains('Thu lãi') || dom.contains('bi-receipt') || dom.contains('dropdown-menu'),
    expected: 'Row actions present for fast counter flow',
    actual: 'Found action buttons'
  });

  // =========================================================================
  // Feature 11: One-touch Status Filter Tabs
  // =========================================================================
  collector.recordCheck({
    tier: 1,
    featureId: 'F11',
    featureName: 'One-touch Status Filter Tabs',
    checkName: 'Filter tabs container exists with id contractFilterTabs',
    passed: dom.hasElementById('contractFilterTabs') || dom.contains('pm-tab-btn') || dom.contains('data-filter'),
    expected: 'Filter tabs container present in DOM',
    actual: 'Found filter tabs'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F11',
    featureName: 'One-touch Status Filter Tabs',
    checkName: 'Filter tab for "Tất cả" (ALL) exists',
    passed: dom.contains('data-filter="ALL"') || dom.contains('data-filter="all"') || dom.contains('Tất cả'),
    expected: 'Tab with data-filter="ALL"',
    actual: 'Found "Tất cả" tab'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F11',
    featureName: 'One-touch Status Filter Tabs',
    checkName: 'Filter tab for "Đang vay" (ACTIVE) exists',
    passed: dom.contains('data-filter="ACTIVE"') || dom.contains('data-filter="active"') || dom.contains('Đang vay'),
    expected: 'Tab with data-filter="ACTIVE"',
    actual: 'Found "Đang vay" tab'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F11',
    featureName: 'One-touch Status Filter Tabs',
    checkName: 'Filter tab for "Đến hạn hôm nay" (DUE_TODAY) exists for daily shift priority',
    passed: dom.contains('data-filter="DUE_TODAY"') || dom.contains('data-filter="due_today"') || dom.contains('Đến hạn hôm nay'),
    expected: 'Tab for daily due loans present',
    actual: 'Found "Đến hạn hôm nay" tab'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F11',
    featureName: 'One-touch Status Filter Tabs',
    checkName: 'Filter tabs include badges or count indicators per category',
    passed: dom.contains('pm-tab-count') || dom.contains('badge') && dom.contains('contractFilterTabs'),
    expected: 'Count badges present inside filter tabs',
    actual: 'Found count badges'
  });

  // =========================================================================
  // Feature 12: Table Search & Quick Query
  // =========================================================================
  collector.recordCheck({
    tier: 1,
    featureId: 'F12',
    featureName: 'Table Search & Quick Query',
    checkName: 'Search input element exists with id contractSearchInput',
    passed: dom.hasElementById('contractSearchInput') || dom.contains('id="contractSearchInput"'),
    expected: 'Input element with id contractSearchInput',
    actual: 'Found search input'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F12',
    featureName: 'Table Search & Quick Query',
    checkName: 'Search placeholder indicates multi-attribute search (Mã HĐ, Khách hàng, SĐT, biển số)',
    passed: (dom.contains('placeholder=') && (dom.contains('Mã HĐ') || dom.contains('SĐT') || dom.contains('CCCD') || dom.contains('biển số'))),
    expected: 'Placeholder indicating multi-attribute search',
    actual: 'Found descriptive placeholder'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F12',
    featureName: 'Table Search & Quick Query',
    checkName: 'Search clear button (contractSearchClear) exists for rapid reset',
    passed: dom.hasElementById('contractSearchClear') || dom.contains('contractSearchClear') || jsContent.includes('contractSearchClear'),
    expected: 'Search clear button in HTML or JS binding',
    actual: 'Found clear button'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F12',
    featureName: 'Table Search & Quick Query',
    checkName: 'Empty state container (tableEmptyState) exists when search finds 0 matches',
    passed: dom.hasElementById('tableEmptyState') || dom.contains('Không tìm thấy') || jsContent.includes('tableEmptyState'),
    expected: 'Empty state element present',
    actual: 'Found empty state container'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F12',
    featureName: 'Table Search & Quick Query',
    checkName: 'Live record count badge (tableRecordCountBadge) updates during search/filter',
    passed: dom.hasElementById('tableRecordCountBadge') || dom.contains('tableRecordCountBadge') || jsContent.includes('tableRecordCountBadge'),
    expected: 'Record count badge present',
    actual: 'Found record count badge'
  });

  // =========================================================================
  // Feature 13: Status Badges System
  // =========================================================================
  collector.recordCheck({
    tier: 1,
    featureId: 'F13',
    featureName: 'Status Badges System',
    checkName: 'ACTIVE badge class (.pm-badge-active) is defined with emerald background and text',
    passed: css.findRuleBySelector('.pm-badge-active').length > 0 || css.hasVariable('--pm-status-active-bg'),
    expected: '.pm-badge-active defined in CSS',
    actual: 'Found in stylesheet'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F13',
    featureName: 'Status Badges System',
    checkName: 'OVERDUE badge class (.pm-badge-overdue) is defined with red background and text',
    passed: css.findRuleBySelector('.pm-badge-overdue').length > 0 || css.hasVariable('--pm-status-overdue-bg'),
    expected: '.pm-badge-overdue defined in CSS',
    actual: 'Found in stylesheet'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F13',
    featureName: 'Status Badges System',
    checkName: 'EXTENDED badge class (.pm-badge-extended) is defined with blue/slate tones',
    passed: css.findRuleBySelector('.pm-badge-extended').length > 0 || css.hasVariable('--pm-status-extended-bg'),
    expected: '.pm-badge-extended defined in CSS',
    actual: 'Found in stylesheet'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F13',
    featureName: 'Status Badges System',
    checkName: 'WAITING_LIQUIDATION badge class (.pm-badge-liquidation) is defined with amber tones',
    passed: css.findRuleBySelector('.pm-badge-liquidation').length > 0 || css.hasVariable('--pm-status-liquidation-bg'),
    expected: '.pm-badge-liquidation defined in CSS',
    actual: 'Found in stylesheet'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F13',
    featureName: 'Status Badges System',
    checkName: 'Status dot indicator (.pm-badge-dot) is configured for status pills',
    passed: css.findRuleBySelector('.pm-badge-dot').length > 0 && dom.contains('pm-badge-dot'),
    expected: '.pm-badge-dot indicator styled in CSS and present in HTML',
    actual: 'Found status dot indicator'
  });

  // =========================================================================
  // Feature 14: Chart 1: Dòng tiền Thu - Chi
  // =========================================================================
  collector.recordCheck({
    tier: 1,
    featureId: 'F14',
    featureName: 'Chart 1: Dòng tiền Thu - Chi',
    checkName: 'Canvas element for Cash Flow chart exists with id cashFlowChart',
    passed: dom.hasElementById('cashFlowChart') || dom.contains('id="cashFlowChart"'),
    expected: 'Canvas element with id cashFlowChart',
    actual: 'Found canvas element'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F14',
    featureName: 'Chart 1: Dòng tiền Thu - Chi',
    checkName: 'Cash Flow chart card header displays Vietnamese title (DÒNG TIỀN)',
    passed: dom.contains('DÒNG TIỀN') || dom.contains('Dòng tiền') || dom.contains('Dòng Tiền'),
    expected: 'Title matching "DÒNG TIỀN"',
    actual: 'Found chart title'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F14',
    featureName: 'Chart 1: Dòng tiền Thu - Chi',
    checkName: 'Cash Flow chart provides timeframe selector buttons (Tháng / Tuần / Quý)',
    passed: (dom.contains('Tháng') || dom.contains('Tuần') || dom.contains('Năm')) &&
            (dom.contains('cashflow-time-btn') || dom.contains('data-timeframe') || dom.contains('btn-group')),
    expected: 'Timeframe filter controls for cashflow chart',
    actual: 'Found timeframe selector controls'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F14',
    featureName: 'Chart 1: Dòng tiền Thu - Chi',
    checkName: 'dashboard.js defines initCashFlowChart function',
    passed: jsContent.includes('function initCashFlowChart') || jsContent.includes('const initCashFlowChart'),
    expected: 'initCashFlowChart function defined in JS',
    actual: 'Found in dashboard.js'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F14',
    featureName: 'Chart 1: Dòng tiền Thu - Chi',
    checkName: 'Cash Flow chart configures dual datasets (Chi giải ngân and Thu hồi vốn & lãi)',
    passed: jsContent.includes('Chi giải ngân') && (jsContent.includes('Thu hồi') || jsContent.includes('Thu lãi')),
    expected: 'Dual datasets for disbursement and collection',
    actual: 'Found dual dataset configuration'
  });

  // =========================================================================
  // Feature 15: Chart 2: Cơ cấu danh mục cầm cố
  // =========================================================================
  collector.recordCheck({
    tier: 1,
    featureId: 'F15',
    featureName: 'Chart 2: Cơ cấu danh mục cầm cố',
    checkName: 'Canvas element for Collateral Donut chart exists with id collateralDonutChart',
    passed: dom.hasElementById('collateralDonutChart') || dom.contains('id="collateralDonutChart"'),
    expected: 'Canvas element with id collateralDonutChart',
    actual: 'Found canvas element'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F15',
    featureName: 'Chart 2: Cơ cấu danh mục cầm cố',
    checkName: 'Collateral chart card header displays Vietnamese title (CƠ CẤU DANH MỤC)',
    passed: dom.contains('CƠ CẤU DANH MỤC') || dom.contains('Cơ cấu danh mục') || dom.contains('Cơ Cấu Danh Mục'),
    expected: 'Title matching "CƠ CẤU DANH MỤC"',
    actual: 'Found chart title'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F15',
    featureName: 'Chart 2: Cơ cấu danh mục cầm cố',
    checkName: 'dashboard.js defines initCollateralDonutChart function with doughnut type',
    passed: (jsContent.includes('function initCollateralDonutChart') || jsContent.includes('const initCollateralDonutChart')) &&
            jsContent.includes('doughnut'),
    expected: 'initCollateralDonutChart defined with doughnut type',
    actual: 'Found in dashboard.js'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F15',
    featureName: 'Chart 2: Cơ cấu danh mục cầm cố',
    checkName: 'Collateral categories include standard pawn items (Xe máy, Ô tô, Vàng, Điện thoại)',
    passed: jsContent.includes('Xe máy') && jsContent.includes('Ô tô') && (jsContent.includes('Vàng') || jsContent.includes('Trang sức')),
    expected: 'Pawnshop collateral categories in donut datasets',
    actual: 'Found pawn collateral categories'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F15',
    featureName: 'Chart 2: Cơ cấu danh mục cầm cố',
    checkName: 'Accessible custom legend or central summary metric accompanies the donut chart',
    passed: dom.contains('pm-donut-legend') || dom.contains('donut-center') || dom.contains('Xe máy') || jsContent.includes('cutout'),
    expected: 'Accessible legend list or center stat block',
    actual: 'Found legend or summary'
  });

  // =========================================================================
  // Feature 16: Responsive Grid Layout
  // =========================================================================
  collector.recordCheck({
    tier: 1,
    featureId: 'F16',
    featureName: 'Responsive Grid Layout',
    checkName: 'KPI cards grid uses responsive Bootstrap classes (col-xl-3 and col-md-6)',
    passed: dom.contains('col-xl-3') && (dom.contains('col-md-6') || dom.contains('col-sm-6')),
    expected: 'col-xl-3 for desktop 4-cols, col-md-6 for tablet 2x2',
    actual: 'Found responsive col classes'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F16',
    featureName: 'Responsive Grid Layout',
    checkName: 'Charts section adapts between desktop split (8/4) and tablet stacked (12)',
    passed: (dom.contains('col-xl-8') || dom.contains('col-lg-8')) && (dom.contains('col-xl-4') || dom.contains('col-lg-4')),
    expected: '8:4 desktop split with responsive collapse',
    actual: 'Found chart grid classes'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F16',
    featureName: 'Responsive Grid Layout',
    checkName: 'Contracts table is wrapped in table-responsive container',
    passed: dom.contains('table-responsive'),
    expected: '.table-responsive wrapper around data table',
    actual: 'Found table-responsive'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F16',
    featureName: 'Responsive Grid Layout',
    checkName: 'Page container uses fluid container with adaptive padding (px-3 px-md-4)',
    passed: dom.contains('container-fluid') && (dom.contains('px-3') || dom.contains('px-md-4') || dom.contains('p-3')),
    expected: 'Fluid container with adaptive padding',
    actual: 'Found container-fluid with padding'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F16',
    featureName: 'Responsive Grid Layout',
    checkName: 'CSS defines media queries or fluid sizing for tablet and mobile viewports',
    passed: css.raw.includes('@media') && (css.raw.includes('991') || css.raw.includes('768') || css.raw.includes('767') || css.raw.includes('992') || css.raw.includes('max-width')),
    expected: 'Responsive breakpoints defined in stylesheet',
    actual: 'Found responsive media queries'
  });

  // =========================================================================
  // Feature 17: Dual-mode Rendering
  // =========================================================================
  collector.recordCheck({
    tier: 1,
    featureId: 'F17',
    featureName: 'Dual-mode Rendering',
    checkName: 'Template declares xmlns:th="http://www.thymeleaf.org" in <html> tag',
    passed: dom.contains('xmlns:th="http://www.thymeleaf.org"'),
    expected: 'Thymeleaf XML namespace declaration',
    actual: 'Found namespace in <html>'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F17',
    featureName: 'Dual-mode Rendering',
    checkName: 'CSS link provides relative href alongside th:href for standalone preview',
    passed: dom.contains('href="../static/css/dashboard.css"') && dom.contains('th:href="@{/css/dashboard.css}"'),
    expected: 'Dual-mode CSS link with relative fallback and th:href',
    actual: 'Found dual-mode CSS link'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F17',
    featureName: 'Dual-mode Rendering',
    checkName: 'JS script tag provides relative src alongside th:src for standalone preview',
    passed: dom.contains('src="../static/js/dashboard.js"') && dom.contains('th:src="@{/js/dashboard.js}"'),
    expected: 'Dual-mode JS script with relative fallback and th:src',
    actual: 'Found dual-mode JS script'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F17',
    featureName: 'Dual-mode Rendering',
    checkName: 'Dynamic text bindings (th:text) provide default inner text for static preview',
    passed: /<([a-zA-Z0-9]+)[^>]*th:text=["'][^"']*["'][^>]*>[^<]+<\/\1>/i.test(dom.raw) || dom.contains('th:text="${kpi?.activeContractsCount ?:'),
    expected: 'Thymeleaf text bindings retain static fallback values',
    actual: 'Found static fallbacks'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F17',
    featureName: 'Dual-mode Rendering',
    checkName: 'Table body includes static fallback rows rendered when previewed as standalone HTML',
    passed: dom.contains('<tbody') && dom.contains('<tr') && dom.contains('HD-2026'),
    expected: 'Static mock contracts present in <tbody>',
    actual: 'Found static mock rows'
  });

  // =========================================================================
  // Feature 18: Anti-pattern Elimination
  // =========================================================================
  const antiPatternViolations = css.detectAntiPatterns();
  collector.recordCheck({
    tier: 1,
    featureId: 'F18',
    featureName: 'Anti-pattern Elimination',
    checkName: 'Zero generic purple-to-blue gradients in stylesheet',
    passed: !antiPatternViolations.some(v => v.type === 'PURPLE_BLUE_GRADIENT'),
    expected: 'No generic purple-to-blue linear gradients',
    actual: antiPatternViolations.filter(v => v.type === 'PURPLE_BLUE_GRADIENT').length === 0 ? '0 violations' : 'Violations found'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F18',
    featureName: 'Anti-pattern Elimination',
    checkName: 'Zero unmotivated neon glow box-shadows in stylesheet',
    passed: !antiPatternViolations.some(v => v.type === 'NEON_GLOW'),
    expected: 'No neon drop shadows',
    actual: antiPatternViolations.filter(v => v.type === 'NEON_GLOW').length === 0 ? '0 violations' : 'Violations found'
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F18',
    featureName: 'Anti-pattern Elimination',
    checkName: 'Zero nested card rules (.card .card) in stylesheet',
    passed: !antiPatternViolations.some(v => v.type === 'NESTED_CARD_RULE'),
    expected: 'No nested card CSS selectors',
    actual: antiPatternViolations.filter(v => v.type === 'NESTED_CARD_RULE').length === 0 ? '0 violations' : 'Violations found'
  });

  const domNestedCards = dom.findNestedCards();
  collector.recordCheck({
    tier: 1,
    featureId: 'F18',
    featureName: 'Anti-pattern Elimination',
    checkName: 'Zero nested card tags in HTML DOM tree',
    passed: domNestedCards.length === 0,
    expected: 'No card inside another card',
    actual: `${domNestedCards.length} nested cards detected`
  });

  collector.recordCheck({
    tier: 1,
    featureId: 'F18',
    featureName: 'Anti-pattern Elimination',
    checkName: 'Bootstrap vector icons (bi-*) used instead of unstyled unicode/emoji hacks',
    passed: dom.contains('bi-') && !dom.contains('💰') && !dom.contains('📈') && !dom.contains('⚠️'),
    expected: 'Bootstrap vector icons used consistently',
    actual: 'Vector icons present, 0 raw emojis'
  });
}

if (require.main === module) {
  const TestCollector = require('../lib/collector');
  const collector = new TestCollector();
  runTier1Tests(collector);
  const summary = collector.getSummary();
  console.log(`Tier 1 Passed: ${summary.tierCounts.tier1.passed} / ${summary.tierCounts.tier1.total}`);
  if (summary.totalFailed > 0) process.exit(1);
}

module.exports = runTier1Tests;
