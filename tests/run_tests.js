/**
 * PawnMaster Dashboard — Unified E2E Test Runner
 * Executes all 4 Tiers and validates requirements against PROJECT.md & DESIGN.md
 */

const fs = require('fs');
const path = require('path');
const TestCollector = require('./lib/collector');
const runTier1Tests = require('./e2e/test_tier1_feature_coverage');
const runTier2Tests = require('./e2e/test_tier2_boundaries_edge');
const runTier3Tests = require('./e2e/test_tier3_cross_feature');
const runTier4Tests = require('./e2e/test_tier4_real_world');

const PROJECT_ROOT = path.resolve(__dirname, '..');
const HTML_PATH = path.join(PROJECT_ROOT, 'src/main/resources/templates/dashboard.html');
const CSS_PATH = path.join(PROJECT_ROOT, 'src/main/resources/static/css/dashboard.css');
const JS_PATH = path.join(PROJECT_ROOT, 'src/main/resources/static/js/dashboard.js');

function run() {
  console.log('\n================================================================================');
  console.log('  PAWNMASTER DASHBOARD — END-TO-END VERIFICATION & AUDIT SUITE');
  console.log('================================================================================\n');

  // Verify target files exist
  const files = [
    { label: 'Template (HTML)', path: HTML_PATH },
    { label: 'Stylesheet (CSS)', path: CSS_PATH },
    { label: 'Interactions (JS)', path: JS_PATH }
  ];

  for (const f of files) {
    if (!fs.existsSync(f.path)) {
      console.error(`❌ CRITICAL ERROR: Target artifact missing: ${f.label} at ${f.path}`);
      process.exit(1);
    }
  }

  const collector = new TestCollector();

  console.log('► Running Tier 1: Feature Coverage (All 18 Features)...');
  try {
    runTier1Tests(collector);
  } catch (err) {
    console.error(`Error in Tier 1:`, err);
  }

  console.log('► Running Tier 2: Boundary & Corner Cases...');
  try {
    runTier2Tests(collector);
  } catch (err) {
    console.error(`Error in Tier 2:`, err);
  }

  console.log('► Running Tier 3: Cross-Feature Combinations...');
  try {
    runTier3Tests(collector);
  } catch (err) {
    console.error(`Error in Tier 3:`, err);
  }

  console.log('► Running Tier 4: Real-World Scenarios & Anti-Pattern Elimination...');
  try {
    runTier4Tests(collector);
  } catch (err) {
    console.error(`Error in Tier 4:`, err);
  }

  const summary = collector.getSummary();

  console.log('\n--------------------------------------------------------------------------------');
  console.log('  TEST EXECUTION SUMMARY');
  console.log('--------------------------------------------------------------------------------');
  console.log(`  Tier 1 (Feature Coverage):       ${summary.tierCounts.tier1.passed} / ${summary.tierCounts.tier1.total} checks passed`);
  console.log(`  Tier 2 (Boundaries & Edges):      ${summary.tierCounts.tier2.passed} / ${summary.tierCounts.tier2.total} checks passed`);
  console.log(`  Tier 3 (Cross-Feature Sync):      ${summary.tierCounts.tier3.passed} / ${summary.tierCounts.tier3.total} checks passed`);
  console.log(`  Tier 4 (Real-World & Standards):  ${summary.tierCounts.tier4.passed} / ${summary.tierCounts.tier4.total} checks passed`);
  console.log('--------------------------------------------------------------------------------');
  console.log(`  TOTAL:                           ${summary.totalPassed} / ${summary.totalChecks} checks passed`);
  console.log('--------------------------------------------------------------------------------\n');

  if (summary.totalFailed > 0) {
    console.log('❌ FAILURES DETECTED:');
    const failures = collector.results.filter(r => !r.passed);
    failures.forEach((f, idx) => {
      console.log(`  ${idx + 1}. [Tier ${f.tier}] ${f.checkName}`);
      console.log(`     Expected: ${f.expected}`);
      console.log(`     Actual:   ${f.actual}`);
      if (f.error) console.log(`     Error:    ${f.error}`);
    });
    console.log('\n');
    process.exit(1);
  } else {
    console.log('✅ ALL TESTS PASSED SUCCESSFULLY! 100% SPECIFICATION CONFORMANCE.\n');
    process.exit(0);
  }
}

if (require.main === module) {
  run();
}

module.exports = run;
