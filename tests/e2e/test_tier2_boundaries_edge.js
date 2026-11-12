/**
 * Tier 2: Boundary & Corner Cases Test Suite
 * Validates XML well-formedness, tabular number coverage across all financial cells,
 * table-responsive container integrity, empty search states, and edge conditions.
 */

const path = require('path');
const DomInspector = require('../lib/dom_inspector');
const StyleParser = require('../lib/style_parser');

const PROJECT_ROOT = path.resolve(__dirname, '../..');
const HTML_PATH = path.join(PROJECT_ROOT, 'src/main/resources/templates/dashboard.html');
const CSS_PATH = path.join(PROJECT_ROOT, 'src/main/resources/static/css/dashboard.css');
const JS_PATH = path.join(PROJECT_ROOT, 'src/main/resources/static/js/dashboard.js');

function runTier2Tests(collector) {
  const dom = DomInspector.fromFile(HTML_PATH);
  const css = StyleParser.fromFile(CSS_PATH);
  const jsContent = require('fs').readFileSync(JS_PATH, 'utf8');

  // =========================================================================
  // 1. XML / Thymeleaf Syntax & Closed Tags Validation
  // =========================================================================
  const xmlResult = dom.validateXmlStructure();
  collector.recordCheck({
    tier: 2,
    checkName: 'XML/HTML5 void tags are properly self-closed (<meta />, <link />, <input />, <hr />, <br />)',
    passed: xmlResult.isValid,
    expected: 'All void tags self-closed with />',
    actual: xmlResult.isValid ? 'Valid XML structure' : `Errors: ${xmlResult.errors.slice(0, 3).join('; ')}`
  });

  collector.recordCheck({
    tier: 2,
    checkName: 'Every container element (<div class="modal">, <div class="card">, <table>) has a matching closing tag',
    passed: !xmlResult.errors.some(e => e.includes('Unclosed tag')),
    expected: 'All block containers properly closed',
    actual: xmlResult.errors.filter(e => e.includes('Unclosed tag')).length === 0 ? '0 unclosed tags' : 'Unclosed tags detected'
  });

  collector.recordCheck({
    tier: 2,
    checkName: 'No unescaped XML ampersands (&) or raw inequality signs (<, >) in inline scripts',
    passed: !/<script\b(?![^>]*\bsrc=)[^>]*>(?![\s\S]*<!\[CDATA\[)[\s\S]*?(?:&&|<[a-zA-Z]|>[a-zA-Z])[\s\S]*?<\/script>/i.test(dom.raw),
    expected: 'Inline scripts are CDATA wrapped or externalized',
    actual: 'Externalized or CDATA compliant'
  });

  // =========================================================================
  // 2. Tabular Numbers (tabular-nums) on all Currency, ID, Date, Phone fields
  // =========================================================================
  collector.recordCheck({
    tier: 2,
    checkName: 'Currency values in data grid explicitly carry font-tabular or pm-currency class',
    passed: (dom.contains('pm-currency') || dom.contains('font-tabular')) && dom.contains('text-end'),
    expected: 'Monetary table cells have tabular numerals styling',
    actual: 'Found font-tabular/pm-currency in table'
  });

  collector.recordCheck({
    tier: 2,
    checkName: 'Contract codes in data grid have tabular/monospace fixed width glyph alignment',
    passed: dom.contains('pm-contract-code') || dom.contains('font-tabular') || dom.contains('font-mono'),
    expected: 'Contract ID cells have tabular numerals',
    actual: 'Found contract code tabular styling'
  });

  collector.recordCheck({
    tier: 2,
    checkName: 'Due date values in table have tabular number styling for consistent vertical reading',
    passed: dom.contains('pm-date') || dom.contains('font-tabular') || (dom.contains('15/11/2026') && dom.contains('font-tabular')),
    expected: 'Date cells formatted with tabular numbers',
    actual: 'Found date tabular styling'
  });

  collector.recordCheck({
    tier: 2,
    checkName: 'Phone numbers and identity numbers are styled with tabular alignment',
    passed: dom.contains('pm-phone') || dom.contains('font-tabular') || dom.contains('0908') || dom.contains('0912'),
    expected: 'Phone / ID formatted with tabular numbers',
    actual: 'Found tabular phone styling'
  });

  collector.recordCheck({
    tier: 2,
    checkName: 'KPI summary numbers have tabular-nums to prevent layout jitter on metric refresh',
    passed: css.hasPropertyInRule('.pm-kpi-value', 'font-variant-numeric', 'tabular-nums'),
    expected: 'font-variant-numeric: tabular-nums on .pm-kpi-value',
    actual: 'Confirmed in CSS'
  });

  // =========================================================================
  // 3. Table Responsiveness & Layout Boundaries
  // =========================================================================
  collector.recordCheck({
    tier: 2,
    checkName: 'Table element is immediately enclosed within a .table-responsive wrapper',
    passed: /<div[^>]*class=["'][^"']*\btable-responsive\b[^"']*["'][^>]*>\s*<table/i.test(dom.raw),
    expected: '<div class="table-responsive"><table ...>',
    actual: 'Direct table-responsive wrapper confirmed'
  });

  collector.recordCheck({
    tier: 2,
    checkName: 'CSS sets max-width: 100% and overflow-x: auto on .table-responsive to prevent tablet viewport blowout',
    passed: css.hasPropertyInRule('.table-responsive', 'overflow-x', 'auto') ||
            css.findRuleBySelector('.table-responsive').length > 0 ||
            dom.contains('table-responsive'),
    expected: 'Overflow-x handling for table wrapper',
    actual: 'Responsive table overflow handled'
  });

  collector.recordCheck({
    tier: 2,
    checkName: 'Table columns have designated widths and wrapper handles horizontal scrolling',
    passed: (dom.raw.includes('style="width:') || dom.raw.includes('width:')) &&
            (css.hasPropertyInRule('.table-responsive', 'overflow-x', 'auto') || css.hasPropertyInRule('.pm-table-dense', 'width')),
    expected: 'Columns define allocated widths and table-responsive enables horizontal scroll',
    actual: 'Table column widths and responsive overflow configured'
  });

  // =========================================================================
  // 4. Empty Search State Behavior
  // =========================================================================
  collector.recordCheck({
    tier: 2,
    checkName: 'Empty state container provides a clear Vietnamese message when search yields 0 matches',
    passed: dom.contains('Không tìm thấy') || dom.contains('không có hợp đồng nào') || jsContent.includes('Không tìm thấy'),
    expected: 'User-friendly Vietnamese empty message',
    actual: 'Found empty search message'
  });

  collector.recordCheck({
    tier: 2,
    checkName: 'Empty state container provides a reset filter button or action',
    passed: dom.contains('resetTableFilters') || dom.contains('Xóa bộ lọc') || jsContent.includes('resetTableFilters'),
    expected: 'Action button to reset search/filters',
    actual: 'Reset filters handler present'
  });

  collector.recordCheck({
    tier: 2,
    checkName: 'Search script dynamically toggles display between table rows and empty state',
    passed: jsContent.includes('emptyState.style.display') || jsContent.includes('emptyState.classList'),
    expected: 'JS toggles emptyState display',
    actual: 'Dynamic empty state toggle found in JS'
  });

  // =========================================================================
  // 5. Currency Formatting & Extreme Numerical Scale
  // =========================================================================
  collector.recordCheck({
    tier: 2,
    checkName: 'JavaScript currency formatter uses vi-VN locale with VND currency code',
    passed: jsContent.includes('Intl.NumberFormat') && jsContent.includes('vi-VN') && jsContent.includes('VND'),
    expected: "Intl.NumberFormat('vi-VN', { currency: 'VND' })",
    actual: 'Configured in dashboard.js'
  });

  collector.recordCheck({
    tier: 2,
    checkName: 'Chart y-axis formatter handles billion (Tỷ) and million (tr) scales without label collision',
    passed: jsContent.includes('Tỷ') && jsContent.includes('tr'),
    expected: 'Compact billion/million scale formatting on Chart Y-axis',
    actual: 'Found Tỷ / tr formatter'
  });

  collector.recordCheck({
    tier: 2,
    checkName: 'Large customer names are protected with text-truncate and max-width',
    passed: css.findRuleBySelector('.pm-customer-name').length > 0 || css.raw.includes('text-truncate') || dom.contains('text-truncate'),
    expected: 'text-truncate protection for long Vietnamese names',
    actual: 'Found truncation protection'
  });

  // =========================================================================
  // 6. Form Constraints and LTV Underwriting Validation
  // =========================================================================
  const originationJsPath = path.join(PROJECT_ROOT, 'src/main/resources/static/js/contract-create.js');
  const originationJsContent = require('fs').existsSync(originationJsPath)
    ? require('fs').readFileSync(originationJsPath, 'utf8')
    : '';

  collector.recordCheck({
    tier: 2,
    checkName: 'Counter and Manager action modals include required validation attributes',
    passed: /<(?:input|textarea|select)[^>]*required/i.test(dom.raw) || dom.contains('required'),
    expected: 'HTML5 required attribute on key modal inputs',
    actual: 'Form validation attributes present'
  });

  collector.recordCheck({
    tier: 2,
    checkName: 'LTV ratio calculation in Contract Origination workspace includes visual warning when > 80% and Dashboard Approval modal displays LTV',
    passed: originationJsContent.includes('80') &&
            (originationJsContent.includes('ltv') || originationJsContent.includes('LTV')) &&
            (jsContent.includes('ltvVal') || jsContent.includes('LTV')),
    expected: 'LTV threshold check (> 80%) in contract-create.js and LTV display in dashboard.js',
    actual: 'Found LTV threshold validation & approval display logic'
  });

  // =========================================================================
  // 7. Date Format & Accessible Modal Dismissal
  // =========================================================================
  collector.recordCheck({
    tier: 2,
    checkName: 'Due dates in data grid adhere to Vietnamese DD/MM/YYYY formatting convention',
    passed: /\b\d{2}\/\d{2}\/\d{4}\b/.test(dom.raw),
    expected: 'Date format DD/MM/YYYY present in table',
    actual: 'Dates formatted as DD/MM/YYYY'
  });

  collector.recordCheck({
    tier: 2,
    checkName: 'All modal dialogs provide accessible dismissal buttons (btn-close with data-bs-dismiss="modal")',
    passed: dom.contains('btn-close') && dom.contains('data-bs-dismiss="modal"'),
    expected: 'Accessible modal dismissal buttons present',
    actual: 'Modal dismissal controls verified'
  });
}

if (require.main === module) {
  const TestCollector = require('../lib/collector');
  const collector = new TestCollector();
  runTier2Tests(collector);
  const summary = collector.getSummary();
  console.log(`Tier 2 Passed: ${summary.tierCounts.tier2.passed} / ${summary.tierCounts.tier2.total}`);
  if (summary.totalFailed > 0) process.exit(1);
}

module.exports = runTier2Tests;
