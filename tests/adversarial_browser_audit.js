/**
 * PawnMaster Dashboard — Empirical Adversarial Browser & Layout Stress Test
 * Uses Headless Chrome to simulate viewports, measure DOM layout, detect horizontal
 * blowouts, inspect touch target sizes, and verify standalone file:/// execution.
 */

const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const PROJECT_ROOT = path.resolve(__dirname, '..');
const HTML_SRC_PATH = path.join(PROJECT_ROOT, 'src/main/resources/templates/dashboard.html');
const CSS_SRC_PATH = path.join(PROJECT_ROOT, 'src/main/resources/static/css/dashboard.css');
const JS_SRC_PATH = path.join(PROJECT_ROOT, 'src/main/resources/static/js/dashboard.js');
const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';

const VIEWPORTS = [
  { name: 'Desktop Full HD (1920x1080)', width: 1920, height: 1080, tier: 'desktop' },
  { name: 'Desktop Standard (1440x900)', width: 1440, height: 900, tier: 'desktop' },
  { name: 'Desktop Breakpoint (1200x800)', width: 1200, height: 800, tier: 'desktop' },
  { name: 'Tablet Landscape (1024x768)', width: 1024, height: 768, tier: 'tablet_landscape' },
  { name: 'Tablet Portrait (768x1024)', width: 768, height: 1024, tier: 'tablet_portrait' },
  { name: 'Mobile Large (480x800)', width: 480, height: 800, tier: 'mobile' },
  { name: 'Mobile Standard (390x844)', width: 390, height: 844, tier: 'mobile' },
  { name: 'Mobile Compact (360x740)', width: 360, height: 740, tier: 'mobile' }
];

// Offline Bootstrap 5.3.3 Grid & Core Utilities definition
const BOOTSTRAP_5_GRID_CSS = `
<style id="__BOOTSTRAP_GRID_FALLBACK__">
:root {
  --bs-gutter-x: 1.5rem;
  --bs-gutter-y: 0;
}
.container-fluid {
  width: 100%;
  padding-right: var(--bs-gutter-x, 0.75rem);
  padding-left: var(--bs-gutter-x, 0.75rem);
  margin-right: auto;
  margin-left: auto;
}
.row {
  display: flex;
  flex-wrap: wrap;
  margin-top: calc(-1 * var(--bs-gutter-y));
  margin-right: calc(-0.5 * var(--bs-gutter-x));
  margin-left: calc(-0.5 * var(--bs-gutter-x));
}
.row > * {
  flex-shrink: 0;
  width: 100%;
  max-width: 100%;
  padding-right: calc(var(--bs-gutter-x) * 0.5);
  padding-left: calc(var(--bs-gutter-x) * 0.5);
  margin-top: var(--bs-gutter-y);
}
.col-12 { flex: 0 0 auto; width: 100%; }
.col-6 { flex: 0 0 auto; width: 50%; }
@media (min-width: 576px) {
  .col-sm-6 { flex: 0 0 auto; width: 50%; }
}
@media (min-width: 768px) {
  .col-md-4 { flex: 0 0 auto; width: 33.33333333%; }
  .col-md-6 { flex: 0 0 auto; width: 50%; }
  .d-md-inline-flex { display: inline-flex !important; }
  .justify-content-md-end { justify-content: flex-end !important; }
}
@media (min-width: 1200px) {
  .col-xl-3 { flex: 0 0 auto; width: 25%; }
  .col-xl-4 { flex: 0 0 auto; width: 33.33333333%; }
  .col-xl-8 { flex: 0 0 auto; width: 66.66666667%; }
}
.d-flex { display: flex !important; }
.d-none { display: none !important; }
.align-items-center { align-items: center !important; }
.justify-content-between { justify-content: space-between !important; }
.gap-1 { gap: 0.25rem !important; }
.gap-2 { gap: 0.5rem !important; }
.gap-3 { gap: 1rem !important; }
.text-end { text-align: right !important; }
.text-center { text-align: center !important; }
.g-2 { --bs-gutter-x: 0.5rem; --bs-gutter-y: 0.5rem; }
.g-3 { --bs-gutter-x: 1rem; --bs-gutter-y: 1rem; }
.gy-3 { --bs-gutter-y: 1rem; }
@media (min-width: 1200px) {
  .g-xl-4 { --bs-gutter-x: 1.5rem; --bs-gutter-y: 1.5rem; }
}
.table-responsive {
  max-width: 100%;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}
.px-3 { padding-left: 1rem !important; padding-right: 1rem !important; }
@media (min-width: 768px) {
  .px-md-4 { padding-left: 1.5rem !important; padding-right: 1.5rem !important; }
}
.py-3 { padding-top: 1rem !important; padding-bottom: 1rem !important; }
.mb-4 { margin-bottom: 1.5rem !important; }
</style>
`;

// Test Probe Script
const PROBE_SCRIPT = `
<script>
window.__AUDIT_ERRORS = [];
window.addEventListener('error', function(e) {
  window.__AUDIT_ERRORS.push({ message: e.message, filename: e.filename, lineno: e.lineno });
});
window.addEventListener('unhandledrejection', function(e) {
  window.__AUDIT_ERRORS.push({ message: 'Unhandled promise rejection: ' + e.reason });
});

window.addEventListener('DOMContentLoaded', function() {
  setTimeout(function() {
    try {
      var audit = {};
      var docEl = document.documentElement;
      var body = document.body;

      // 1. Viewport & Scroll Metrics
      var docClientWidth = docEl.clientWidth;
      var docScrollWidth = docEl.scrollWidth;
      var bodyScrollWidth = body.scrollWidth;
      var windowWidth = window.innerWidth;

      audit.viewport = {
        innerWidth: windowWidth,
        innerHeight: window.innerHeight,
        docClientWidth: docClientWidth,
        docScrollWidth: docScrollWidth,
        bodyScrollWidth: bodyScrollWidth,
        hasHorizontalBlowout: (docScrollWidth > windowWidth + 1) || (bodyScrollWidth > windowWidth + 1),
        scrollDelta: Math.max(docScrollWidth - windowWidth, bodyScrollWidth - windowWidth)
      };

      // 2. Elements causing horizontal overflow
      var overflowing = [];
      var allEls = document.querySelectorAll('body *');
      for (var i = 0; i < allEls.length; i++) {
        var el = allEls[i];
        var parentScroll = el.closest('.table-responsive, [style*="overflow-x: auto"]');
        if (parentScroll && parentScroll !== el) continue;

        var rect = el.getBoundingClientRect();
        if (rect.right > windowWidth + 2 && rect.width > 0 && rect.height > 0) {
          overflowing.push({
            tag: el.tagName.toLowerCase(),
            id: el.id || '',
            className: (el.className || '').toString().slice(0, 100),
            rectRight: Math.round(rect.right),
            width: Math.round(rect.width),
            overflowAmount: Math.round(rect.right - windowWidth)
          });
        }
      }
      audit.overflowingElements = overflowing.slice(0, 10);

      // 3. Grid & Breakpoint Behavior
      // KPI cards
      var kpiCards = document.querySelectorAll('.pm-kpi-card');
      var kpiMetrics = [];
      for (var k = 0; k < kpiCards.length; k++) {
        var kRect = kpiCards[k].getBoundingClientRect();
        kpiMetrics.push({
          index: k + 1,
          left: Math.round(kRect.left),
          top: Math.round(kRect.top),
          width: Math.round(kRect.width),
          height: Math.round(kRect.height)
        });
      }
      var kpiRows = {};
      kpiMetrics.forEach(function(m) {
        var rowKey = Math.round(m.top / 15) * 15;
        if (!kpiRows[rowKey]) kpiRows[rowKey] = [];
        kpiRows[rowKey].push(m);
      });
      var kpiRowCount = Object.keys(kpiRows).length;
      audit.kpiGrid = {
        cardCount: kpiCards.length,
        metrics: kpiMetrics,
        rowCount: kpiRowCount,
        cardsPerRow: Math.round(kpiMetrics.length / kpiRowCount)
      };

      // Visualizations layout (Cash Flow vs Donut)
      var cashFlow = document.getElementById('cashFlowChart');
      var collateral = document.getElementById('collateralDonutChart');
      if (cashFlow && collateral) {
        var cfCard = cashFlow.closest('.pm-chart-card');
        var cdCard = collateral.closest('.pm-chart-card');
        if (cfCard && cdCard) {
          var cfRect = cfCard.getBoundingClientRect();
          var cdRect = cdCard.getBoundingClientRect();
          var sameRow = Math.abs(cfRect.top - cdRect.top) < 25;
          audit.chartsLayout = {
            sameRow: sameRow,
            cashFlowWidth: Math.round(cfRect.width),
            donutWidth: Math.round(cdRect.width),
            cashFlowTop: Math.round(cfRect.top),
            donutTop: Math.round(cdRect.top)
          };
        }
      }

      // 4. Table & Table-responsive
      var tableResp = document.querySelector('.table-responsive');
      var table = document.getElementById('recentContractsTable');
      if (tableResp && table) {
        var trStyle = window.getComputedStyle(tableResp);
        var tStyle = window.getComputedStyle(table);
        var trRect = tableResp.getBoundingClientRect();
        var tRect = table.getBoundingClientRect();
        audit.tableResponsive = {
          overflowX: trStyle.overflowX,
          containerClientWidth: tableResp.clientWidth,
          containerScrollWidth: tableResp.scrollWidth,
          tableWidth: Math.round(tRect.width),
          tableMinWidth: tStyle.minWidth,
          isScrolling: tableResp.scrollWidth > tableResp.clientWidth,
          containedWithinViewport: trRect.right <= windowWidth + 1
        };
      }

      // 5. Touch Target Sizes Evaluation
      var touchElements = [
        { selector: '.pm-btn', category: 'action-button' },
        { selector: '.pm-tab-btn', category: 'filter-tab' },
        { selector: '.pm-page-link', category: 'pagination-link' },
        { selector: '.pm-btn-icon', category: 'table-action-icon' },
        { selector: '.pm-form-control', category: 'form-input' },
        { selector: '.pm-form-select', category: 'form-select' },
        { selector: '.pm-search-input', category: 'search-input' },
        { selector: '.pm-chart-tab', category: 'chart-tab' }
      ];

      var touchResults = [];
      touchElements.forEach(function(group) {
        var els = document.querySelectorAll(group.selector);
        var minH = Infinity, maxH = -Infinity, minW = Infinity, maxW = -Infinity;
        var sample = [];
        for (var j = 0; j < els.length; j++) {
          var r = els[j].getBoundingClientRect();
          if (r.width === 0 && r.height === 0) continue;
          var h = Math.round(r.height);
          var w = Math.round(r.width);
          if (h < minH) minH = h;
          if (h > maxH) maxH = h;
          if (w < minW) minW = w;
          if (w > maxW) maxW = w;
          if (sample.length < 3) {
            sample.push({
              tag: els[j].tagName.toLowerCase(),
              text: (els[j].textContent || '').trim().slice(0, 30),
              width: w,
              height: h
            });
          }
        }
        touchResults.push({
          category: group.category,
          selector: group.selector,
          count: els.length,
          minHeight: minH === Infinity ? 0 : minH,
          maxHeight: maxH === -Infinity ? 0 : maxH,
          minWidth: minW === Infinity ? 0 : minW,
          maxWidth: maxW === -Infinity ? 0 : maxW,
          meets40px: minH >= 40,
          meets44px: minH >= 44,
          sample: sample
        });
      });
      audit.touchAccessibility = touchResults;

      // 6. Runtime errors captured
      audit.runtimeErrors = window.__AUDIT_ERRORS;

      // 7. Dynamic components state
      audit.dynamicComponents = {
        clockUpdated: (document.getElementById('liveClock') || {}).textContent !== 'Thứ Hai, 09/11/2026 - 10:45:00',
        clockText: (document.getElementById('liveClock') || {}).textContent || '',
        cashFlowCanvasRendered: !!document.getElementById('cashFlowChart'),
        donutCanvasRendered: !!document.getElementById('collateralDonutChart'),
        rowsRendered: document.querySelectorAll('#recentContractsTable tbody tr[data-contract-code]').length,
        modalsInDom: document.querySelectorAll('.modal').length
      };

      var scriptTag = document.createElement('script');
      scriptTag.id = '__AUDIT_DATA__';
      scriptTag.type = 'application/json';
      scriptTag.textContent = JSON.stringify(audit);
      document.body.appendChild(scriptTag);
    } catch (err) {
      var errTag = document.createElement('script');
      errTag.id = '__AUDIT_DATA__';
      errTag.type = 'application/json';
      errTag.textContent = JSON.stringify({ auditCrash: err.message, stack: err.stack });
      document.body.appendChild(errTag);
    }
  }, 600);
});
</script>
`;

async function runChromeDump(url, width, height, virtualTime = 2500) {
  return new Promise((resolve, reject) => {
    const args = [
      '--headless=new',
      '--no-sandbox',
      '--disable-gpu',
      `--window-size=${width},${height}`,
      `--virtual-time-budget=${virtualTime}`,
      '--dump-dom',
      url
    ];

    const proc = spawn(CHROME_PATH, args);
    let stdout = '';
    let stderr = '';

    proc.stdout.on('data', d => stdout += d.toString());
    proc.stderr.on('data', d => stderr += d.toString());

    proc.on('close', code => {
      resolve({ code, stdout, stderr });
    });

    proc.on('error', err => reject(err));
  });
}

async function audit() {
  console.log('================================================================================');
  console.log('  PAWNMASTER DASHBOARD — EMPIRICAL BROWSER & LAYOUT STRESS AUDIT');
  console.log('  Engine: Google Chrome Headless (Blink Render Engine)');
  console.log('================================================================================\n');

  if (!fs.existsSync(HTML_SRC_PATH)) {
    console.error(`Artifact not found: ${HTML_SRC_PATH}`);
    process.exit(1);
  }

  const rawHtml = fs.readFileSync(HTML_SRC_PATH, 'utf8');
  const cssContent = fs.readFileSync(CSS_SRC_PATH, 'utf8');
  const jsContent = fs.readFileSync(JS_SRC_PATH, 'utf8');

  // Prepare offline self-contained HTML for comprehensive browser test
  let auditHtml = rawHtml;
  // Inject Bootstrap Grid Fallback
  auditHtml = auditHtml.replace('</head>', `${BOOTSTRAP_5_GRID_CSS}\n<style id="__DASHBOARD_CSS_INLINE__">\n${cssContent}\n</style>\n</head>`);
  // Inject dashboard.js inline as fallback
  auditHtml = auditHtml.replace('</body>', `<script id="__DASHBOARD_JS_INLINE__">\n${jsContent}\n</script>\n${PROBE_SCRIPT}\n</body>`);

  const tmpAuditPath = path.join(PROJECT_ROOT, 'tests', '__tmp_audit_runner.html');
  fs.writeFileSync(tmpAuditPath, auditHtml, 'utf8');

  const fileUrl = 'file:///' + tmpAuditPath.replace(/\\/g, '/');
  const originalFileUrl = 'file:///' + HTML_SRC_PATH.replace(/\\/g, '/');

  console.log(`Auditing target URL: ${fileUrl}\n`);

  const resultsByViewport = [];

  for (const vp of VIEWPORTS) {
    process.stdout.write(`► Testing Viewport: ${vp.name}... `);
    try {
      const dump = await runChromeDump(fileUrl, vp.width, vp.height, 2500);
      const match = dump.stdout.match(/<script id="__AUDIT_DATA__" type="application\/json">([\s\S]*?)<\/script>/);

      if (!match) {
        console.log('❌ FAILED (No audit JSON output extracted)');
        resultsByViewport.push({ viewport: vp, error: 'Probe script did not execute or output' });
        continue;
      }

      const data = JSON.parse(match[1]);
      if (data.auditCrash) {
        console.log(`❌ CRASHED: ${data.auditCrash}`);
        resultsByViewport.push({ viewport: vp, error: data.auditCrash });
        continue;
      }

      console.log('✅ OK');
      resultsByViewport.push({ viewport: vp, data });
    } catch (err) {
      console.log(`❌ ERROR: ${err.message}`);
      resultsByViewport.push({ viewport: vp, error: err.message });
    }
  }

  // Standalone file:/// preview test on ORIGINAL unmodified dashboard.html
  console.log('\n► Testing Standalone file:/// preview of ORIGINAL dashboard.html...');
  const origDump = await runChromeDump(originalFileUrl, 1440, 900, 2000);
  const origOk = origDump.code === 0 && origDump.stdout.includes('PawnMaster') && origDump.stdout.includes('HD-2026-0128');
  console.log(origOk ? '✅ Standalone preview rendered cleanly without fatal errors!' : '❌ Standalone preview failed');

  // Clean up temporary audit HTML
  try {
    fs.unlinkSync(tmpAuditPath);
  } catch (_) {}

  // Print Detailed Forensic Findings
  console.log('\n--------------------------------------------------------------------------------');
  console.log('  EMPIRICAL FORENSIC AUDIT FINDINGS');
  console.log('--------------------------------------------------------------------------------\n');

  console.log('1. HORIZONTAL BLOWOUT CHECK (Body / Doc Scroll Width vs Window Inner Width):');
  let allBlowoutsPass = true;
  for (const r of resultsByViewport) {
    if (r.data && r.data.viewport) {
      const vp = r.data.viewport;
      const pass = !vp.hasHorizontalBlowout;
      if (!pass) allBlowoutsPass = false;
      const status = pass ? '✅ PASS' : '❌ BLOWOUT DETECTED';
      console.log(`  - [${r.viewport.name}]: ${status} (Window: ${vp.innerWidth}px, DocScroll: ${vp.docScrollWidth}px, BodyScroll: ${vp.bodyScrollWidth}px, Delta: +${vp.scrollDelta}px)`);
      if (r.data.overflowingElements && r.data.overflowingElements.length > 0) {
        console.log(`    Elements overflowing:`);
        r.data.overflowingElements.forEach(o => {
          console.log(`      * <${o.tag} class="${o.className}"> width=${o.width}px, overflow=+${o.overflowAmount}px`);
        });
      }
    }
  }

  console.log('\n2. BOOTSTRAP 5 GRID BREAKPOINT BEHAVIORS:');
  for (const r of resultsByViewport) {
    if (r.data && r.data.kpiGrid) {
      const kg = r.data.kpiGrid;
      const cl = r.data.chartsLayout;
      console.log(`  - [${r.viewport.name}]:`);
      console.log(`    * KPI Cards: ${kg.cardCount} cards in ${kg.rowCount} row(s) (~${kg.cardsPerRow} cards/row). Card width: ${kg.metrics[0].width}px`);
      if (cl) {
        console.log(`    * Charts: CashFlow=${cl.cashFlowWidth}px, Donut=${cl.donutWidth}px, Side-by-side: ${cl.sameRow ? 'YES' : 'NO (Stacked)'}`);
      }
    }
  }

  console.log('\n3. TABLE CONTAINMENT & OVERFLOW:');
  for (const r of resultsByViewport) {
    if (r.data && r.data.tableResponsive) {
      const tr = r.data.tableResponsive;
      console.log(`  - [${r.viewport.name}]: Container ClientWidth=${tr.containerClientWidth}px, Container ScrollWidth=${tr.containerScrollWidth}px, Table Width=${tr.tableWidth}px, Overflow-X=${tr.overflowX}, ScrollActive=${tr.isScrolling ? 'YES' : 'NO'}, Contained=${tr.containedWithinViewport ? 'YES' : 'NO'}`);
    }
  }

  console.log('\n4. TOUCH ACCESSIBILITY TARGET SIZES (WCAG 2.5.5 / 2.5.8):');
  const desktopSample = resultsByViewport.find(r => r.data && r.data.touchAccessibility);
  if (desktopSample) {
    const list = desktopSample.data.touchAccessibility;
    list.forEach(t => {
      const mark40 = t.meets40px ? '✅ >=40px' : '⚠️ <40px';
      const mark44 = t.meets44px ? '✅ >=44px' : '⚠️ <44px';
      console.log(`  - ${t.category} (${t.selector}): Height=${t.minHeight}px-${t.maxHeight}px, Width=${t.minWidth}px-${t.maxWidth}px | 40px: ${mark40} | 44px: ${mark44}`);
      if (t.sample && t.sample.length > 0) {
        t.sample.slice(0, 2).forEach(s => {
          console.log(`      * "${s.text}" (${s.width}x${s.height}px)`);
        });
      }
    });
  }

  console.log('\n5. RUNTIME ERRORS & JAVASCRIPT CONSOLE EXCEPTIONS:');
  let anyErrors = false;
  for (const r of resultsByViewport) {
    if (r.data && r.data.runtimeErrors && r.data.runtimeErrors.length > 0) {
      anyErrors = true;
      console.log(`  ❌ [${r.viewport.name}] Errors logged:`, r.data.runtimeErrors);
    }
  }
  if (!anyErrors) {
    console.log('  ✅ Zero runtime errors or unhandled promise rejections detected across all viewports!');
  }

  // Save report data for handoff
  const reportPath = path.join(PROJECT_ROOT, '.agents/teamwork/challenger_2/empirical_audit_data.json');
  fs.writeFileSync(reportPath, JSON.stringify({
    timestamp: new Date().toISOString(),
    viewports: resultsByViewport,
    originalFileOk: origOk
  }, null, 2));

  console.log(`\nDetailed empirical data exported to: ${reportPath}`);
  console.log('================================================================================\n');
}

audit();
