/**
 * PawnMaster Login Terminal — E2E & Design System Verification Suite
 * Validates login.html, login.css, and login.js against DESIGN.md ("The Vault Ledger")
 * and Impeccable Craft Floor rules.
 */

const fs = require('fs');
const path = require('path');
const DomInspector = require('./lib/dom_inspector');
const StyleParser = require('./lib/style_parser');
const { passesWcagAA } = require('./lib/contrast');

const PROJECT_ROOT = path.resolve(__dirname, '..');
const LOGIN_HTML_PATH = path.join(PROJECT_ROOT, 'src/main/resources/templates/login.html');
const LOGIN_CSS_PATH = path.join(PROJECT_ROOT, 'src/main/resources/static/css/login.css');
const LOGIN_JS_PATH = path.join(PROJECT_ROOT, 'src/main/resources/static/js/login.js');

function runLoginTests() {
  console.log('\n================================================================================');
  console.log('  PAWNMASTER LOGIN TERMINAL — VERIFICATION & DESIGN CRAFT AUDIT');
  console.log('================================================================================\n');

  const dom = DomInspector.fromFile(LOGIN_HTML_PATH);
  const css = StyleParser.fromFile(LOGIN_CSS_PATH);
  const jsRaw = fs.readFileSync(LOGIN_JS_PATH, 'utf8');

  const checks = [];
  function check(name, passed, expected, actual) {
    checks.push({ name, passed: Boolean(passed), expected, actual });
  }

  // 1. XML & Thymeleaf Structural Validity
  const xmlResult = dom.validateXmlStructure();
  check(
    'XML/HTML5 void tags (<meta />, <link />, <input />) are properly self-closed in login.html',
    xmlResult.isValid,
    'Valid XML structure',
    xmlResult.isValid ? 'Valid XML structure' : xmlResult.errors.join('; ')
  );

  check(
    'Dual-mode static/Thymeleaf CSS and JS links present in login.html',
    dom.contains('href="../static/css/login.css"') &&
      dom.contains('th:href="@{/css/login.css}"') &&
      dom.contains('src="../static/js/login.js"') &&
      dom.contains('th:src="@{/js/login.js}"'),
    'Both relative ../static/ and th:href/th:src attributes present',
    'Verified dual-mode attributes'
  );

  // 2. Design Tokens & WCAG AA Contrast ("The Vault Ledger")
  check(
    'Vault Cobalt (--pm-primary: #1e40af) and Vault Alabaster (--pm-bg-app: #f8fafc) tokens defined',
    css.getVariable('--pm-primary') === '#1e40af' && css.getVariable('--pm-bg-app') === '#f8fafc',
    '#1e40af & #f8fafc',
    `${css.getVariable('--pm-primary')} & ${css.getVariable('--pm-bg-app')}`
  );

  const primaryBtnContrast = passesWcagAA('#ffffff', css.getVariable('--pm-primary') || '#1e40af', false);
  check(
    'Login CTA button text (#ffffff on #1e40af) exceeds WCAG AA (>= 4.5:1)',
    primaryBtnContrast.passes,
    'Ratio >= 4.5:1',
    `Ratio: ${primaryBtnContrast.ratio}:1`
  );

  const bodyTextContrast = passesWcagAA(
    css.getVariable('--pm-text-primary') || '#0f172a',
    css.getVariable('--pm-bg-surface') || '#ffffff',
    false
  );
  check(
    'Primary Ledger Ink text (#0f172a on #ffffff) exceeds WCAG AAA (>= 7:1)',
    bodyTextContrast.passes && bodyTextContrast.ratio >= 7,
    'Ratio >= 7:1',
    `Ratio: ${bodyTextContrast.ratio}:1`
  );

  // 3. Split-Screen Ledger Terminal & Form Elements
  check(
    'Split-screen layout contains Left Vault Brief Pane (.pm-vault-brief-pane) and Right Auth Terminal (.pm-auth-terminal-pane)',
    dom.contains('pm-vault-brief-pane') && dom.contains('pm-auth-terminal-pane'),
    '2-column split terminal architecture',
    'Both panes present'
  );

  check(
    'Login form (#loginForm) includes #loginUsername, #loginPassword, #togglePasswordBtn, and #loginSubmitBtn',
    dom.hasElementById('loginForm') &&
      dom.hasElementById('loginUsername') &&
      dom.hasElementById('loginPassword') &&
      dom.hasElementById('togglePasswordBtn') &&
      dom.hasElementById('loginSubmitBtn'),
    'Complete authentication form controls',
    'All form IDs verified'
  );

  check(
    '1-Touch Quick Role Switcher (#quickRoleGrid) includes all 4 roles: MANAGER, STAFF, ACCOUNTANT, ADMIN',
    dom.hasElementById('quickRoleGrid') &&
      dom.contains('data-role="MANAGER"') &&
      dom.contains('data-role="STAFF"') &&
      dom.contains('data-role="ACCOUNTANT"') &&
      dom.contains('data-role="ADMIN"'),
    '4 role buttons present',
    'MANAGER, STAFF, ACCOUNTANT, ADMIN verified'
  );

  // 4. Anti-Pattern Forensic Checks (Craft Floor)
  check(
    'Zero nested .card elements in login.html (Single-Shell Rule)',
    dom.findNestedCards().length === 0,
    '0 nested cards',
    `${dom.findNestedCards().length} nested cards`
  );

  check(
    'Zero generic AI purple-to-blue gradients or neon glow shadows in login.css',
    !/linear-gradient\([^)]*(#667eea|#764ba2|#8b5cf6|purple)/i.test(css.raw) &&
      !/box-shadow\s*:[^;]*(rgba?\(\s*0\s*,\s*255|#00ff[a-f0-9]{2}|#39ff14)/i.test(css.raw),
    'Zero gradients and neon shadows',
    'Clean stylesheet'
  );

  check(
    'Zero raw Unicode emojis in login.html (pure Bootstrap Icons bi-*)',
    !/[💰📈⚠️🔍✏️❌➕]/.test(dom.raw),
    'Zero raw emojis',
    'Pure Bootstrap Icons used'
  );

  // 5. Auth API & Redirect Integration in login.js
  check(
    'login.js integrates with POST /api/v1/auth/login, sets pm_token cookie + localStorage, and redirects to /dashboard',
    jsRaw.includes('/api/v1/auth/login') &&
      jsRaw.includes('pm_token=') &&
      jsRaw.includes('pm_current_user') &&
      jsRaw.includes('/dashboard'),
    'Complete auth flow in login.js',
    'Verified API, cookie, localStorage, and redirect'
  );

  const passedCount = checks.filter((c) => c.passed).length;
  checks.forEach((c, idx) => {
    const icon = c.passed ? '✓' : '✗';
    console.log(`  ${icon} [${idx + 1}/${checks.length}] ${c.name}`);
    if (!c.passed) {
      console.log(`      Expected: ${c.expected}`);
      console.log(`      Actual:   ${c.actual}`);
    }
  });

  console.log('\n--------------------------------------------------------------------------------');
  console.log(`  LOGIN SUITE SUMMARY: ${passedCount} / ${checks.length} checks passed`);
  console.log('--------------------------------------------------------------------------------\n');

  if (passedCount < checks.length) {
    process.exit(1);
  }
}

if (require.main === module) {
  runLoginTests();
}

module.exports = runLoginTests;

