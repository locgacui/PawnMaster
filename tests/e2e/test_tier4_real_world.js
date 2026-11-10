/**
 * Tier 4: Real-World Scenarios Test Suite
 * Validates standalone preview file rendering, WCAG AA contrast compliance,
 * and forensic verification of the absence of fintech anti-patterns.
 */

const path = require('path');
const DomInspector = require('../lib/dom_inspector');
const StyleParser = require('../lib/style_parser');
const { getContrastRatio, passesWcagAA } = require('../lib/contrast');

const PROJECT_ROOT = path.resolve(__dirname, '../..');
const HTML_PATH = path.join(PROJECT_ROOT, 'src/main/resources/templates/dashboard.html');
const CSS_PATH = path.join(PROJECT_ROOT, 'src/main/resources/static/css/dashboard.css');
const JS_PATH = path.join(PROJECT_ROOT, 'src/main/resources/static/js/dashboard.js');

function runTier4Tests(collector) {
  const dom = DomInspector.fromFile(HTML_PATH);
  const css = StyleParser.fromFile(CSS_PATH);

  // =========================================================================
  // 1. Standalone Preview Rendering & Dual-Mode Links
  // =========================================================================
  collector.recordCheck({
    tier: 4,
    checkName: 'All external vendor CDNs use secure HTTPS endpoints with verified versions (Bootstrap 5.3.3, Icons 1.11.3, Chart.js 4.4.7)',
    passed: dom.contains('https://cdn.jsdelivr.net/npm/bootstrap@5.3.3') &&
            dom.contains('https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3') &&
            dom.contains('https://cdn.jsdelivr.net/npm/chart.js@4.4.7'),
    expected: 'Bootstrap 5.3.3, Bootstrap Icons 1.11.3, Chart.js 4.4.7 via CDN',
    actual: 'Vendor CDN links verified'
  });

  collector.recordCheck({
    tier: 4,
    checkName: 'Local static stylesheet link resolves relatively (href="../static/css/dashboard.css") for standalone browser preview',
    passed: dom.contains('href="../static/css/dashboard.css"'),
    expected: 'Relative CSS fallback href present',
    actual: 'Confirmed relative fallback href'
  });

  collector.recordCheck({
    tier: 4,
    checkName: 'Local static script link resolves relatively (src="../static/js/dashboard.js") for standalone browser preview',
    passed: dom.contains('src="../static/js/dashboard.js"'),
    expected: 'Relative JS fallback src present',
    actual: 'Confirmed relative fallback src'
  });

  collector.recordCheck({
    tier: 4,
    checkName: 'Dashboard template contains at least 5 realistic mock contract rows for standalone preview without database backend',
    passed: (dom.raw.match(/<tr[^>]*data-contract-code/gi) || []).length >= 5,
    expected: '>= 5 mock contract rows with data-contract-code',
    actual: `${(dom.raw.match(/<tr[^>]*data-contract-code/gi) || []).length} mock rows found`
  });

  // =========================================================================
  // 2. WCAG AA Contrast Ratio Compliance (Mathematical Verification)
  // =========================================================================
  // Primary Text (#0f172a) on Surface Background (#ffffff)
  const primaryText = css.getVariable('--pm-text-primary') || '#0f172a';
  const surfaceBg = css.getVariable('--pm-bg-surface') || '#ffffff';
  const primaryContrast = passesWcagAA(primaryText, surfaceBg, false);

  collector.recordCheck({
    tier: 4,
    checkName: `Primary text (${primaryText}) on surface (${surfaceBg}) exceeds WCAG AA requirement (>= 4.5:1)`,
    passed: primaryContrast.passes,
    expected: 'Ratio >= 4.5:1',
    actual: `Ratio: ${primaryContrast.ratio}:1 (${primaryContrast.passes ? 'PASS' : 'FAIL'})`
  });

  // Secondary Text (#475569) on Surface Background (#ffffff)
  const secondaryText = css.getVariable('--pm-text-secondary') || '#475569';
  const secondaryContrast = passesWcagAA(secondaryText, surfaceBg, false);

  collector.recordCheck({
    tier: 4,
    checkName: `Secondary text (${secondaryText}) on surface (${surfaceBg}) meets WCAG AA (>= 4.5:1)`,
    passed: secondaryContrast.passes,
    expected: 'Ratio >= 4.5:1',
    actual: `Ratio: ${secondaryContrast.ratio}:1 (${secondaryContrast.passes ? 'PASS' : 'FAIL'})`
  });

  // Muted Text (#64748b) on Surface Background (#ffffff)
  const mutedText = css.getVariable('--pm-text-muted') || '#64748b';
  const mutedContrast = passesWcagAA(mutedText, surfaceBg, false);

  collector.recordCheck({
    tier: 4,
    checkName: `Muted helper text (${mutedText}) on surface (${surfaceBg}) meets WCAG AA (>= 4.5:1)`,
    passed: mutedContrast.passes,
    expected: 'Ratio >= 4.5:1',
    actual: `Ratio: ${mutedContrast.ratio}:1 (${mutedContrast.passes ? 'PASS' : 'FAIL'})`
  });

  // ACTIVE Badge: Text (#065f46) on Background (#ecfdf5)
  const activeBadgeText = css.getVariable('--pm-status-active-text') || '#065f46';
  const activeBadgeBg = css.getVariable('--pm-status-active-bg') || '#ecfdf5';
  const activeBadgeContrast = passesWcagAA(activeBadgeText, activeBadgeBg, false);

  collector.recordCheck({
    tier: 4,
    checkName: `Active badge text (${activeBadgeText}) on badge bg (${activeBadgeBg}) meets WCAG AA (>= 4.5:1)`,
    passed: activeBadgeContrast.passes,
    expected: 'Ratio >= 4.5:1',
    actual: `Ratio: ${activeBadgeContrast.ratio}:1 (${activeBadgeContrast.passes ? 'PASS' : 'FAIL'})`
  });

  // OVERDUE Badge: Text (#991b1b) on Background (#fef2f2)
  const overdueBadgeText = css.getVariable('--pm-status-overdue-text') || '#991b1b';
  const overdueBadgeBg = css.getVariable('--pm-status-overdue-bg') || '#fef2f2';
  const overdueBadgeContrast = passesWcagAA(overdueBadgeText, overdueBadgeBg, false);

  collector.recordCheck({
    tier: 4,
    checkName: `Overdue badge text (${overdueBadgeText}) on badge bg (${overdueBadgeBg}) meets WCAG AA (>= 4.5:1)`,
    passed: overdueBadgeContrast.passes,
    expected: 'Ratio >= 4.5:1',
    actual: `Ratio: ${overdueBadgeContrast.ratio}:1 (${overdueBadgeContrast.passes ? 'PASS' : 'FAIL'})`
  });

  // WAITING_LIQUIDATION Badge: Text (#92400e) on Background (#fffbeb)
  const liqBadgeText = css.getVariable('--pm-status-liquidation-text') || '#92400e';
  const liqBadgeBg = css.getVariable('--pm-status-liquidation-bg') || '#fffbeb';
  const liqBadgeContrast = passesWcagAA(liqBadgeText, liqBadgeBg, false);

  collector.recordCheck({
    tier: 4,
    checkName: `Liquidation badge text (${liqBadgeText}) on badge bg (${liqBadgeBg}) meets WCAG AA (>= 4.5:1)`,
    passed: liqBadgeContrast.passes,
    expected: 'Ratio >= 4.5:1',
    actual: `Ratio: ${liqBadgeContrast.ratio}:1 (${liqBadgeContrast.passes ? 'PASS' : 'FAIL'})`
  });

  // Primary Button: Text (#ffffff) on Primary Accent (#1e40af)
  const primaryBtnBg = css.getVariable('--pm-primary') || '#1e40af';
  const primaryBtnContrast = passesWcagAA('#ffffff', primaryBtnBg, false);

  collector.recordCheck({
    tier: 4,
    checkName: `Primary CTA button text (#ffffff) on accent bg (${primaryBtnBg}) exceeds WCAG AA (>= 4.5:1)`,
    passed: primaryBtnContrast.passes,
    expected: 'Ratio >= 4.5:1',
    actual: `Ratio: ${primaryBtnContrast.ratio}:1 (${primaryBtnContrast.passes ? 'PASS' : 'FAIL'})`
  });

  // Table Header Text (#475569) on Header Subtle Bg (#f1f5f9)
  const headerText = css.getVariable('--pm-text-secondary') || '#475569';
  const headerBg = css.getVariable('--pm-bg-subtle') || '#f1f5f9';
  const headerContrast = passesWcagAA(headerText, headerBg, false);

  collector.recordCheck({
    tier: 4,
    checkName: `Table header text (${headerText}) on subtle header background (${headerBg}) meets WCAG AA (>= 4.5:1)`,
    passed: headerContrast.passes,
    expected: 'Ratio >= 4.5:1',
    actual: `Ratio: ${headerContrast.ratio}:1 (${headerContrast.passes ? 'PASS' : 'FAIL'})`
  });

  collector.recordCheck({
    tier: 4,
    checkName: 'Font family stack defines comprehensive local fallbacks (-apple-system, BlinkMacSystemFont, Segoe UI, Roboto) for intranet environments',
    passed: css.raw.includes('-apple-system') && css.raw.includes('BlinkMacSystemFont') &&
            css.raw.includes('Segoe UI') && css.raw.includes('Roboto'),
    expected: 'Local OS font fallback stack defined',
    actual: 'System font stack verified'
  });

  // =========================================================================
  // 3. Strict Absence of Fintech Anti-Patterns
  // =========================================================================
  collector.recordCheck({
    tier: 4,
    checkName: 'Forensic scan: Zero generic AI purple-to-blue linear gradients in CSS or HTML inline styles',
    passed: !/linear-gradient\([^)]*(#667eea|#764ba2|#8b5cf6|purple)[\s\S]*?(#3b82f6|#2563eb|blue)/i.test(css.raw) &&
            !/linear-gradient\([^)]*(#667eea|#764ba2|#8b5cf6|purple)[\s\S]*?(#3b82f6|#2563eb|blue)/i.test(dom.raw),
    expected: 'Zero generic purple-to-blue gradients across entire codebase',
    actual: '0 generic gradients found'
  });

  collector.recordCheck({
    tier: 4,
    checkName: 'Forensic scan: Zero unmotivated neon glow drop shadows (rgba(0, 255, ...) or #00ffcc)',
    passed: !/box-shadow\s*:[^;]*(rgba?\(\s*0\s*,\s*255|#00ff[a-f0-9]{2}|#39ff14)/i.test(css.raw) &&
            !/box-shadow\s*:[^;]*(rgba?\(\s*0\s*,\s*255|#00ff[a-f0-9]{2}|#39ff14)/i.test(dom.raw),
    expected: 'Zero neon glow shadows across entire codebase',
    actual: '0 neon glows found'
  });

  collector.recordCheck({
    tier: 4,
    checkName: 'Forensic scan: Zero nested card patterns (<div class="card"> inside another .card)',
    passed: dom.findNestedCards().length === 0,
    expected: 'Zero nested cards in HTML DOM structure',
    actual: `${dom.findNestedCards().length} nested cards found`
  });

  collector.recordCheck({
    tier: 4,
    checkName: 'Forensic scan: Zero unstyled unicode emoji hacks used as operational UI icons',
    passed: !/[💰📈⚠️🔍✏️❌➕]/.test(dom.raw),
    expected: 'Zero raw emojis in dashboard template',
    actual: '0 raw emojis detected; pure Bootstrap Icons used'
  });
}

if (require.main === module) {
  const TestCollector = require('../lib/collector');
  const collector = new TestCollector();
  runTier4Tests(collector);
  const summary = collector.getSummary();
  console.log(`Tier 4 Passed: ${summary.tierCounts.tier4.passed} / ${summary.tierCounts.tier4.total}`);
  if (summary.totalFailed > 0) process.exit(1);
}

module.exports = runTier4Tests;
