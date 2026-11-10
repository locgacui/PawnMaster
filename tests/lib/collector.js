/**
 * Test Result Collector and Reporter
 */

class TestCollector {
  constructor() {
    this.results = [];
    this.tierCounts = {
      tier1: { total: 0, passed: 0, failed: 0 },
      tier2: { total: 0, passed: 0, failed: 0 },
      tier3: { total: 0, passed: 0, failed: 0 },
      tier4: { total: 0, passed: 0, failed: 0 }
    };
    this.featureChecklist = {};
  }

  recordCheck({ tier, featureId = null, featureName = null, checkName, passed, actual = null, expected = null, error = null }) {
    const item = {
      tier,
      featureId,
      featureName,
      checkName,
      passed: Boolean(passed),
      actual,
      expected,
      error: error ? String(error) : null,
      timestamp: new Date().toISOString()
    };

    this.results.push(item);

    const tKey = `tier${tier}`;
    if (this.tierCounts[tKey]) {
      this.tierCounts[tKey].total++;
      if (item.passed) {
        this.tierCounts[tKey].passed++;
      } else {
        this.tierCounts[tKey].failed++;
      }
    }

    if (featureId) {
      if (!this.featureChecklist[featureId]) {
        this.featureChecklist[featureId] = {
          id: featureId,
          name: featureName || featureId,
          checks: [],
          allPassed: true
        };
      }
      this.featureChecklist[featureId].checks.push(item);
      if (!item.passed) {
        this.featureChecklist[featureId].allPassed = false;
      }
    }

    return item;
  }

  getSummary() {
    let totalChecks = 0;
    let totalPassed = 0;
    let totalFailed = 0;

    for (const key of Object.keys(this.tierCounts)) {
      totalChecks += this.tierCounts[key].total;
      totalPassed += this.tierCounts[key].passed;
      totalFailed += this.tierCounts[key].failed;
    }

    return {
      tierCounts: this.tierCounts,
      totalChecks,
      totalPassed,
      totalFailed,
      allPassed: totalFailed === 0,
      featureChecklist: this.featureChecklist
    };
  }
}

module.exports = TestCollector;
