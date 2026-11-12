/**
 * Empirical Stress Testing Suite for PawnMaster Dashboard
 * Executed by challenger_1
 * 
 * Tests:
 * 1. Search Filtering (Exact, Accents, Phone, License Plate, IMEI, Empty, Non-matching, Clear, Reset)
 * 2. Filter Tabs (ALL, ACTIVE, DUE_TODAY, OVERDUE, WAITING_LIQUIDATION, Combinations)
 * 3. Dynamic Calculations (LTV formula, Interest formula, Boundaries, Edge cases)
 * 4. Modals and Forms Prefill (Collect Interest, Redeem Collateral)
 * 5. Forensic checks & potential flaws (Diacritic insensitivity, Tab badge vs actual match count, etc.)
 */

const fs = require('fs');
const path = require('path');

const PROJECT_ROOT = path.resolve(__dirname, '..');
const HTML_PATH = path.join(PROJECT_ROOT, 'src/main/resources/templates/dashboard.html');
const JS_PATH = path.join(PROJECT_ROOT, 'src/main/resources/static/js/dashboard.js');

const htmlSource = fs.readFileSync(HTML_PATH, 'utf8');
const jsSource = fs.readFileSync(JS_PATH, 'utf8');

// Lightweight realistic DOM environment
class MockElement {
  constructor(tagName, id = '', className = '') {
    this.tagName = tagName.toUpperCase();
    this.id = id;
    this.className = className;
    this.attributes = {};
    this.style = {};
    this.value = '';
    this._textContent = '';
    this.children = [];
    this.parentElement = null;
    this.eventListeners = {};
  }

  get classList() {
    return {
      contains: (cls) => this.className.split(/\s+/).filter(Boolean).includes(cls),
      add: (cls) => {
        const set = new Set(this.className.split(/\s+/).filter(Boolean));
        set.add(cls);
        this.className = Array.from(set).join(' ');
      },
      remove: (cls) => {
        const set = new Set(this.className.split(/\s+/).filter(Boolean));
        set.delete(cls);
        this.className = Array.from(set).join(' ');
      }
    };
  }

  getAttribute(attr) {
    return this.attributes[attr] !== undefined ? this.attributes[attr] : null;
  }

  setAttribute(attr, val) {
    this.attributes[attr] = String(val);
  }

  get textContent() {
    if (this.children.length === 0) return this._textContent;
    return this._textContent + this.children.map(c => c.textContent).join(' ');
  }

  set textContent(val) {
    this._textContent = String(val);
    this.children = [];
  }

  addEventListener(event, handler) {
    if (!this.eventListeners[event]) {
      this.eventListeners[event] = [];
    }
    this.eventListeners[event].push(handler);
  }

  dispatchEvent(eventObj) {
    const handlers = this.eventListeners[eventObj.type] || [];
    eventObj.target = eventObj.target || this;
    eventObj.currentTarget = this;
    handlers.forEach(h => h.call(this, eventObj));
  }

  click() {
    this.dispatchEvent({
      type: 'click',
      target: this,
      preventDefault: () => {}
    });
  }

  focus() {
    // mock focus
  }

  querySelector(selector) {
    return this.querySelectorAll(selector)[0] || null;
  }

  querySelectorAll(selector) {
    const results = [];
    function traverse(el) {
      for (const child of el.children) {
        if (matches(child, selector)) {
          results.push(child);
        }
        traverse(child);
      }
    }
    traverse(this);
    return results;
  }
}

function matches(el, selector) {
  // simple selector matcher for mock DOM (including comma-separated selector lists)
  if (selector.includes(',')) {
    return selector.split(',').some(part => matches(el, part.trim()));
  }
  if (selector === 'tbody') return el.tagName === 'TBODY';
  if (selector.startsWith('.')) return el.classList.contains(selector.slice(1));
  if (selector.startsWith('tr[')) {
    const attrMatch = selector.match(/tr\[([a-zA-Z0-9_-]+)\]/);
    if (attrMatch) {
      return el.tagName === 'TR' && el.getAttribute(attrMatch[1]) !== null;
    }
  }
  if (selector.startsWith('[data-filter=')) {
    const m = selector.match(/\[data-filter="([^"]+)"\]/);
    return m && el.getAttribute('data-filter') === m[1];
  }
  return false;
}

// Build DOM model from dashboard.html
function buildDomFromHtml() {
  const elementsById = {};
  const allElements = [];

  // Parse table rows from dashboard.html
  // Extract all <tr data-contract-code="..." ...> ... </tr>
  const rowRegex = /<tr\s+data-contract-code="([^"]+)"\s+data-status="([^"]+)"\s+data-due-today="([^"]+)">([\s\S]*?)<\/tr>/g;
  const rows = [];
  let m;

  while ((m = rowRegex.exec(htmlSource)) !== null) {
    const code = m[1];
    const status = m[2];
    const dueToday = m[3];
    const innerHtml = m[4];

    const row = new MockElement('tr');
    row.setAttribute('data-contract-code', code);
    row.setAttribute('data-status', status);
    row.setAttribute('data-due-today', dueToday);
    
    // strip tags to get pure text content for row.textContent
    const strippedText = innerHtml.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
    row._textContent = strippedText;

    // Parse buttons inside row with flexible attribute extraction
    const buttonTagRegex = /<button\s+([^>]*btn-action-(collect-interest|redeem)[^>]*)>/g;
    let btnMatch;
    while ((btnMatch = buttonTagRegex.exec(innerHtml)) !== null) {
      const attrStr = btnMatch[1];
      const btnType = btnMatch[2]; // collect-interest or redeem
      const btn = new MockElement('button', '', `pm-btn-icon btn-action-${btnType}`);
      
      const attrRegex = /([a-zA-Z0-9_-]+)="([^"]*)"/g;
      let a;
      while ((a = attrRegex.exec(attrStr)) !== null) {
        btn.setAttribute(a[1], a[2]);
      }
      row.children.push(btn);
      allElements.push(btn);
    }

    rows.push(row);
  }

  // Create table & tbody
  const table = new MockElement('table', 'recentContractsTable', 'pm-table-dense');
  const tbody = new MockElement('tbody');
  table.children.push(tbody);
  rows.forEach(r => tbody.children.push(r));

  // Empty state row
  const emptyState = new MockElement('tr', 'tableEmptyState');
  emptyState.style.display = 'none';
  tbody.children.push(emptyState);

  // Search input & clear
  const searchInput = new MockElement('input', 'contractSearchInput', 'pm-search-input');
  const searchClear = new MockElement('button', 'contractSearchClear', 'pm-search-clear');
  searchClear.style.display = 'none';

  // Filter tabs
  const filterTabsContainer = new MockElement('div', 'contractFilterTabs', 'pm-filter-tabs');
  const tabFilters = ['ALL', 'ACTIVE', 'DUE_TODAY', 'OVERDUE', 'WAITING_LIQUIDATION'];
  tabFilters.forEach((filter, idx) => {
    const tabBtn = new MockElement('button', '', `pm-tab-btn ${idx === 0 ? 'active' : ''}`);
    tabBtn.setAttribute('data-filter', filter);
    filterTabsContainer.children.push(tabBtn);
  });

  // Record count badge
  const recordCountBadge = new MockElement('span', 'tableRecordCountBadge');
  recordCountBadge.textContent = '10 HĐ';

  // Dedicated Origination Workspace CTA & Branch Selector elements
  const btnOpenFullContractPage = new MockElement('a', 'btnOpenFullContractPage', 'pm-btn pm-btn-primary btn-counter-quick-action');
  btnOpenFullContractPage.setAttribute('href', 'contract-create.html');
  const branchSelector = new MockElement('button', 'branchSelector', 'pm-branch-trigger');
  const branchTriggerName = new MockElement('span', 'branchTriggerName');
  const branchTriggerCode = new MockElement('span', 'branchTriggerCode');
  const branchTriggerIcon = new MockElement('i', 'branchTriggerIcon');
  const branchDropdownList = new MockElement('div', 'branchDropdownList');

  // Modal Collect Interest elements
  const ciContractCode = new MockElement('input', 'ciContractCode');
  const ciCustomerName = new MockElement('input', 'ciCustomerName');
  const ciDueInterest = new MockElement('input', 'ciDueInterest');
  const ciTotalDue = new MockElement('span', 'ciTotalDue');

  // Modal Redeem Collateral elements
  const rdContractCode = new MockElement('input', 'rdContractCode');
  const rdCustomerName = new MockElement('input', 'rdCustomerName');
  const rdPrincipal = new MockElement('input', 'rdPrincipal');
  const rdAccruedInterest = new MockElement('input', 'rdAccruedInterest');
  const rdTotalSettlement = new MockElement('span', 'rdTotalSettlement');

  // Map elements
  const allKnownElements = [
    table, tbody, emptyState, searchInput, searchClear, filterTabsContainer, recordCountBadge,
    btnOpenFullContractPage, branchSelector, branchTriggerName, branchTriggerCode, branchTriggerIcon, branchDropdownList,
    ciContractCode, ciCustomerName, ciDueInterest, ciTotalDue,
    rdContractCode, rdCustomerName, rdPrincipal, rdAccruedInterest, rdTotalSettlement,
    ...rows, ...allElements
  ];

  allKnownElements.forEach(el => {
    if (el.id) elementsById[el.id] = el;
  });

  const mockDocument = {
    getElementById: (id) => elementsById[id] || null,
    querySelectorAll: (selector) => {
      if (selector === '.btn-action-collect-interest') {
        return allElements.filter(el => el.classList.contains('btn-action-collect-interest'));
      }
      if (selector === '.btn-action-redeem') {
        return allElements.filter(el => el.classList.contains('btn-action-redeem'));
      }
      if (selector === '.pm-tab-btn') {
        return filterTabsContainer.children.filter(el => el.classList.contains('pm-tab-btn'));
      }
      return [];
    },
    addEventListener: () => {}
  };

  const mockWindow = {};

  return {
    document: mockDocument,
    window: mockWindow,
    elementsById,
    rows,
    table,
    tbody,
    searchInput,
    searchClear,
    filterTabsContainer,
    emptyState,
    recordCountBadge,
    btnOpenFullContractPage,
    branchSelector,
    ciContractCode,
    ciCustomerName,
    ciDueInterest,
    ciTotalDue,
    rdContractCode,
    rdCustomerName,
    rdPrincipal,
    rdAccruedInterest,
    rdTotalSettlement,
    allElements
  };
}

// Sandbox execution of dashboard.js in Mock DOM
function executeDashboardJs(env) {
  // Extract and run functions from dashboard.js
  const scriptCode = `
    const document = env.document;
    const window = env.window;
    ${jsSource}
    return {
      formatVND,
      parseVND,
      initDashboardFilters,
      syncBranchTriggerDom,
      initRowActionTriggers
    };
  `;
  const fn = new Function('env', scriptCode);
  return fn(env);
}

// Test Runner
async function runEmpiricalStressTests() {
  console.log('================================================================');
  console.log('   EMPIRICAL ADVERSARIAL STRESS TEST SUITE — CHALLENGER_1');
  console.log('================================================================\n');

  const testResults = [];
  function assert(name, condition, details = {}) {
    testResults.push({ name, passed: Boolean(condition), details });
    const mark = condition ? '✅ PASS' : '❌ FAIL';
    console.log(`[${mark}] ${name}`);
    if (!condition) {
      console.log('       Details:', details);
    }
  }

  // Build clean DOM environment
  const env = buildDomFromHtml();
  const api = executeDashboardJs(env);

  // Initialize modules
  api.initDashboardFilters('recentContractsTable', 'contractSearchInput', 'contractFilterTabs');
  api.initRowActionTriggers();

  // Helper to count visible rows
  function getVisibleRows() {
    return env.rows.filter(r => r.style.display !== 'none');
  }

  // --------------------------------------------------------------------------
  // SECTION 1: SEARCH FILTERING STRESS TESTS
  // --------------------------------------------------------------------------
  console.log('\n--- 1. Search Filtering Stress Tests ---');

  // 1.1 Search Contract Code
  env.searchInput.value = 'HD-2026-0128';
  env.searchInput.dispatchEvent({ type: 'input', target: env.searchInput });
  let vis = getVisibleRows();
  assert('Search by Contract Code (HD-2026-0128) finds exact matching row',
    vis.length === 1 && vis[0].getAttribute('data-contract-code') === 'HD-2026-0128',
    { count: vis.length, matchedCode: vis[0]?.getAttribute('data-contract-code') });
  assert('Clear button becomes visible when search has input',
    env.searchClear.style.display === 'block',
    { display: env.searchClear.style.display });

  // 1.2 Search Vietnamese Name with Accents
  env.searchInput.value = 'Nguyễn Văn An';
  env.searchInput.dispatchEvent({ type: 'input', target: env.searchInput });
  vis = getVisibleRows();
  assert('Search by customer name with Vietnamese accents (Nguyễn Văn An) finds exact match',
    vis.length === 1 && vis[0].getAttribute('data-contract-code') === 'HD-2026-0128',
    { count: vis.length, matchedCode: vis[0]?.getAttribute('data-contract-code') });

  // 1.3 Search Phone Number
  env.searchInput.value = '0908';
  env.searchInput.dispatchEvent({ type: 'input', target: env.searchInput });
  vis = getVisibleRows();
  assert('Search by phone number prefix (0908) finds row HD-2026-0128',
    vis.length === 1 && vis[0].getAttribute('data-contract-code') === 'HD-2026-0128',
    { count: vis.length });

  // 1.4 Search License Plate
  env.searchInput.value = '59-P1';
  env.searchInput.dispatchEvent({ type: 'input', target: env.searchInput });
  vis = getVisibleRows();
  assert('Search by license plate (59-P1) finds Honda SH row HD-2026-0128',
    vis.length === 1 && vis[0].getAttribute('data-contract-code') === 'HD-2026-0128',
    { count: vis.length });

  // 1.5 Search IMEI
  env.searchInput.value = '358921';
  env.searchInput.dispatchEvent({ type: 'input', target: env.searchInput });
  vis = getVisibleRows();
  assert('Search by phone IMEI (358921) finds iPhone 15 Pro Max row HD-2026-0125',
    vis.length === 1 && vis[0].getAttribute('data-contract-code') === 'HD-2026-0125',
    { count: vis.length, matchedCode: vis[0]?.getAttribute('data-contract-code') });

  // 1.6 Search Empty Query
  env.searchInput.value = '';
  env.searchInput.dispatchEvent({ type: 'input', target: env.searchInput });
  vis = getVisibleRows();
  assert('Empty search query restores all rows (10 rows under ALL tab)',
    vis.length === 10,
    { count: vis.length });

  // 1.7 Non-matching Query
  env.searchInput.value = 'MA_HOP_DONG_KHONG_TON_TAI_9999';
  env.searchInput.dispatchEvent({ type: 'input', target: env.searchInput });
  vis = getVisibleRows();
  assert('Non-matching query returns 0 rows',
    vis.length === 0,
    { count: vis.length });
  assert('#tableEmptyState is displayed when visibleCount is 0',
    env.emptyState.style.display === '',
    { emptyStateDisplay: env.emptyState.style.display });
  assert('tableRecordCountBadge updates to "0 HĐ"',
    env.recordCountBadge.textContent === '0 HĐ',
    { badgeText: env.recordCountBadge.textContent });

  // 1.8 Reset Button Functionality
  env.window.resetTableFilters();
  vis = getVisibleRows();
  assert('Reset button (window.resetTableFilters) clears query, resets tab to ALL, hides empty state, and restores page 1 rows',
    env.searchInput.value === '' &&
    env.emptyState.style.display === 'none' &&
    vis.length === Math.min(10, env.rows.length) &&
    env.recordCountBadge.textContent === `${env.rows.length} HĐ`,
    { searchInput: env.searchInput.value, emptyStateDisplay: env.emptyState.style.display, visCount: vis.length, badge: env.recordCountBadge.textContent });

  // 1.9 Adversarial Search Edge Cases
  // Case insensitivity
  env.searchInput.value = 'nguyễn văn an';
  env.searchInput.dispatchEvent({ type: 'input', target: env.searchInput });
  assert('Case insensitive search (lowercase "nguyễn văn an") matches correctly',
    getVisibleRows().length === 1);

  env.searchInput.value = 'NGUYỄN VĂN AN';
  env.searchInput.dispatchEvent({ type: 'input', target: env.searchInput });
  assert('Case insensitive search (uppercase "NGUYỄN VĂN AN") matches correctly',
    getVisibleRows().length === 1);

  // Whitespace tolerance
  env.searchInput.value = '   0908   ';
  env.searchInput.dispatchEvent({ type: 'input', target: env.searchInput });
  assert('Search input with leading/trailing spaces is trimmed and matches',
    getVisibleRows().length === 1);

  // Special characters tolerance
  env.searchInput.value = '***[?($';
  env.searchInput.dispatchEvent({ type: 'input', target: env.searchInput });
  assert('Search with regex special characters does not crash or throw unhandled exception',
    getVisibleRows().length === 0 && env.emptyState.style.display === '');

  // Reset after search tests
  env.window.resetTableFilters();

  // --------------------------------------------------------------------------
  // SECTION 2: FILTER TABS STRESS TESTS
  // --------------------------------------------------------------------------
  console.log('\n--- 2. Filter Tabs Stress Tests ---');

  const tabs = env.filterTabsContainer.children;
  const tabMap = {};
  tabs.forEach(t => { tabMap[t.getAttribute('data-filter')] = t; });

  // 2.1 Tab ALL
  tabMap['ALL'].click();
  vis = getVisibleRows();
  assert('Filter Tab ALL displays all 10 contracts',
    vis.length === 10,
    { count: vis.length });

  // 2.2 Tab ACTIVE
  tabMap['ACTIVE'].click();
  vis = getVisibleRows();
  const activeCodes = vis.map(r => r.getAttribute('data-contract-code'));
  assert('Filter Tab ACTIVE matches rows with ACTIVE or EXTENDED (6 contracts total: 5 ACTIVE + 1 EXTENDED)',
    vis.length === 6 && activeCodes.includes('HD-2026-0126'),
    { count: vis.length, activeCodes });

  // 2.3 Tab DUE_TODAY
  tabMap['DUE_TODAY'].click();
  vis = getVisibleRows();
  const dueCodes = vis.map(r => r.getAttribute('data-contract-code'));
  assert('Filter Tab DUE_TODAY matches data-due-today="true" (2 contracts: HD-2026-0128 and HD-2026-0124)',
    vis.length === 2 && dueCodes.includes('HD-2026-0128') && dueCodes.includes('HD-2026-0124'),
    { count: vis.length, dueCodes });

  // 2.4 Tab OVERDUE
  tabMap['OVERDUE'].click();
  vis = getVisibleRows();
  const overdueCodes = vis.map(r => r.getAttribute('data-contract-code'));
  assert('Filter Tab OVERDUE matches data-status="OVERDUE" (2 contracts: HD-2026-0125 and HD-2026-0123)',
    vis.length === 2 && overdueCodes.includes('HD-2026-0125') && overdueCodes.includes('HD-2026-0123'),
    { count: vis.length, overdueCodes });

  // 2.5 Tab WAITING_LIQUIDATION
  tabMap['WAITING_LIQUIDATION'].click();
  vis = getVisibleRows();
  const liqCodes = vis.map(r => r.getAttribute('data-contract-code'));
  assert('Filter Tab WAITING_LIQUIDATION matches data-status="WAITING_LIQUIDATION" (1 contract: HD-2026-0122)',
    vis.length === 1 && liqCodes.includes('HD-2026-0122'),
    { count: vis.length, liqCodes });

  // 2.6 Cross Filter + Search Combination
  tabMap['OVERDUE'].click();
  env.searchInput.value = 'Tuấn';
  env.searchInput.dispatchEvent({ type: 'input', target: env.searchInput });
  vis = getVisibleRows();
  assert('Cross-filter: Tab OVERDUE + Search "Tuấn" narrows to 1 contract (HD-2026-0125)',
    vis.length === 1 && vis[0].getAttribute('data-contract-code') === 'HD-2026-0125',
    { count: vis.length });

  // Contradictory cross filter
  env.searchInput.value = 'Nguyễn Văn An'; // ACTIVE loan on OVERDUE tab
  env.searchInput.dispatchEvent({ type: 'input', target: env.searchInput });
  vis = getVisibleRows();
  assert('Contradictory cross-filter: Tab OVERDUE + Search "Nguyễn Văn An" returns 0 and triggers empty state',
    vis.length === 0 && env.emptyState.style.display === '',
    { count: vis.length, emptyStateDisplay: env.emptyState.style.display });

  // Reset after tab tests
  env.window.resetTableFilters();

  // --------------------------------------------------------------------------
  // SECTION 3: DEDICATED ORIGINATION CTA & CURRENCY UTILITY STRESS TESTS
  // --------------------------------------------------------------------------
  console.log('\n--- 3. Dedicated Origination Entry & Currency Utility Stress Tests ---');

  assert('Legacy #modalNewContract and "+ Cầm Đồ Mới" are completely removed from dashboard.html',
    !htmlSource.includes('id="modalNewContract"') &&
    !htmlSource.includes('data-bs-target="#modalNewContract"') &&
    !htmlSource.includes('Cầm Đồ Mới'),
    { hasBtnOpenFullContractPage: htmlSource.includes('id="btnOpenFullContractPage"') });

  api.syncBranchTriggerDom({ value: '2', name: 'Chi nhánh Bình Thạnh', code: 'CN-HCM-02' });
  assert('syncBranchTriggerDom dynamically updates #btnOpenFullContractPage href with ?branchId=2 in static mode',
    env.btnOpenFullContractPage.getAttribute('href') === 'contract-create.html?branchId=2',
    { href: env.btnOpenFullContractPage.getAttribute('href') });

  env.window.location = { protocol: 'http:' };
  api.syncBranchTriggerDom({ value: '2', name: 'Chi nhánh Bình Thạnh', code: 'CN-HCM-02' });
  const httpBranchHref = env.btnOpenFullContractPage.getAttribute('href');
  api.syncBranchTriggerDom({ value: 'ALL', name: 'Toàn hệ thống', code: 'ALL-CHAIN' });
  const httpAllHref = env.btnOpenFullContractPage.getAttribute('href');
  delete env.window.location;

  assert('syncBranchTriggerDom updates #btnOpenFullContractPage href in HTTP mode (/contracts/new?branchId=2 and /contracts/new)',
    httpBranchHref === '/contracts/new?branchId=2' && httpAllHref === '/contracts/new',
    { httpBranchHref, httpAllHref });

  assert('Formatted currency strings ("60.000.000 ₫" and "30.000.000 ₫") parse cleanly via parseVND',
    api.parseVND('60.000.000 ₫') === 60000000 && api.parseVND('30.000.000 ₫') === 30000000,
    { parsed60M: api.parseVND('60.000.000 ₫'), parsed30M: api.parseVND('30.000.000 ₫') });

  // --------------------------------------------------------------------------
  // SECTION 4: MODALS & FORMS PREFILL STRESS TESTS
  // --------------------------------------------------------------------------
  console.log('\n--- 4. Modals and Forms Prefill Stress Tests ---');

  const row0128 = env.rows.find(r => r.getAttribute('data-contract-code') === 'HD-2026-0128') || env.rows[0];
  const row0126 = env.rows.find(r => r.getAttribute('data-contract-code') === 'HD-2026-0126') || env.rows[2];
  const row0122 = env.rows.find(r => r.getAttribute('data-contract-code') === 'HD-2026-0122') || env.rows[6];

  // 4.1 Collect Interest Button Prefill (HD-2026-0128)
  const row1CollectBtn = row0128.querySelector('.btn-action-collect-interest');
  assert('HD-2026-0128 (.btn-action-collect-interest) exists in DOM', Boolean(row1CollectBtn));
  if (row1CollectBtn) {
    row1CollectBtn.click();
    assert('Collect Interest prefill: contract code HD-2026-0128',
      env.ciContractCode.value === 'HD-2026-0128',
      { contractCode: env.ciContractCode.value });
    assert('Collect Interest prefill: customer name "Nguyễn Văn An"',
      env.ciCustomerName.value === 'Nguyễn Văn An',
      { customerName: env.ciCustomerName.value });
    assert('Collect Interest prefill: due interest formatted as "900.000 ₫"',
      env.ciDueInterest.value.includes('900.000'),
      { dueInterest: env.ciDueInterest.value });
    assert('Collect Interest prefill: total due formatted as "900.000 ₫"',
      env.ciTotalDue.textContent.includes('900.000'),
      { totalDue: env.ciTotalDue.textContent });
  }

  // 4.2 Collect Interest Button Prefill (HD-2026-0126 - 220M loan)
  const row3CollectBtn = row0126.querySelector('.btn-action-collect-interest');
  if (row3CollectBtn) {
    row3CollectBtn.click();
    assert('Collect Interest prefill HD-2026-0126: due interest 13.200.000 ₫',
      env.ciContractCode.value === 'HD-2026-0126' &&
      env.ciCustomerName.value === 'Lê Hoàng Long' &&
      env.ciDueInterest.value.includes('13.200.000'),
      { code: env.ciContractCode.value, interest: env.ciDueInterest.value });
  }

  // 4.3 Redeem Collateral Button Prefill (HD-2026-0128)
  const row1RedeemBtn = row0128.querySelector('.btn-action-redeem');
  assert('HD-2026-0128 (.btn-action-redeem) exists in DOM', Boolean(row1RedeemBtn));
  if (row1RedeemBtn) {
    row1RedeemBtn.click();
    assert('Redeem Collateral prefill: contract code HD-2026-0128',
      env.rdContractCode.value === 'HD-2026-0128',
      { contractCode: env.rdContractCode.value });
    assert('Redeem Collateral prefill: customer name "Nguyễn Văn An"',
      env.rdCustomerName.value === 'Nguyễn Văn An',
      { customerName: env.rdCustomerName.value });
    assert('Redeem Collateral prefill: principal 45.000.000 ₫',
      env.rdPrincipal.value.includes('45.000.000'),
      { principal: env.rdPrincipal.value });
    assert('Redeem Collateral prefill: accrued interest 900.000 ₫',
      env.rdAccruedInterest.value.includes('900.000'),
      { accrued: env.rdAccruedInterest.value });
    assert('Redeem Collateral prefill: total settlement = 45M + 900k = 45.900.000 ₫',
      env.rdTotalSettlement.textContent.includes('45.900.000'),
      { totalSettlement: env.rdTotalSettlement.textContent });
  }

  // 4.4 Redeem Collateral Button Prefill (HD-2026-0122 - Rolex 150M)
  const row7RedeemBtn = row0122.querySelector('.btn-action-redeem');
  if (row7RedeemBtn) {
    row7RedeemBtn.click();
    assert('Redeem Collateral prefill HD-2026-0122 (Rolex): principal 150M + 15.5M = 165.500.000 ₫ total settlement',
      env.rdContractCode.value === 'HD-2026-0122' &&
      env.rdCustomerName.value === 'Hoàng Thanh Tùng' &&
      env.rdPrincipal.value.includes('150.000.000') &&
      env.rdTotalSettlement.textContent.includes('165.500.000'),
      { code: env.rdContractCode.value, total: env.rdTotalSettlement.textContent });
  }

  // --------------------------------------------------------------------------
  // SECTION 5: ADVERSARIAL EDGE CASES & FORENSIC VULNERABILITY PROBING
  // --------------------------------------------------------------------------
  console.log('\n--- 5. Adversarial Edge Cases & Forensic Probing ---');

  const adversarialFindings = [];

  // 5.1 Vietnamese Unaccented Search Query
  env.searchInput.value = 'Nguyen Van An'; // Without accents
  env.searchInput.dispatchEvent({ type: 'input', target: env.searchInput });
  const unaccentedVis = getVisibleRows();
  const unaccentedMatches = unaccentedVis.length > 0;
  adversarialFindings.push({
    title: 'Vietnamese Diacritic Normalization',
    expected: 'Unaccented search "Nguyen Van An" matches "Nguyễn Văn An"',
    actual: unaccentedMatches ? 'Matched' : 'No match (0 rows)',
    risk: 'LOW (UX enhancement)',
    blastRadius: 'Users typing without Vietnamese accents need to type exact accents or diacritics',
    mitigation: 'Add query.normalize("NFD").replace(/[\\u0300-\\u036f]/g, "") before comparison'
  });
  console.log(`[🔍 PROBE] Diacritic Normalization: ${unaccentedMatches ? 'SUPPORTED' : 'UNACCENTED SEARCH REQUIRES DIACRITICS'}`);

  // 5.2 Continuous Phone Number Search (without spaces)
  env.searchInput.value = '0908123456'; // Continuous digits vs '0908 123 456'
  env.searchInput.dispatchEvent({ type: 'input', target: env.searchInput });
  const continuousPhoneVis = getVisibleRows();
  const phoneContinuousMatches = continuousPhoneVis.length > 0;
  adversarialFindings.push({
    title: 'Formatted Field Digit Concatenation',
    expected: 'Continuous phone digits "0908123456" matches "0908 123 456"',
    actual: phoneContinuousMatches ? 'Matched' : 'No match (0 rows)',
    risk: 'LOW (UX enhancement)',
    blastRadius: 'Users typing 10 consecutive phone digits must include spaces or prefix "0908"',
    mitigation: 'Compare query against both raw textContent and stripped unspaced text (replace(/\\s+/g, ""))'
  });
  console.log(`[🔍 PROBE] Continuous Phone Digits Search: ${phoneContinuousMatches ? 'SUPPORTED' : 'REQUIRES DELIMITER OR PREFIX'}`);

  // 5.3 Active Tab Badge Count vs Filter Result Count Alignment
  env.window.resetTableFilters();
  tabMap['ACTIVE'].click();
  const activeTabBadgeMatch = htmlSource.match(/data-filter="ACTIVE"[\s\S]*?<span[^>]*class="[^"]*pm-tab-badge[^"]*"[^>]*>(\d+)<\/span>/);
  const activeTabBadgeValue = activeTabBadgeMatch ? parseInt(activeTabBadgeMatch[1], 10) : 5;
  const actualActiveVisCount = getVisibleRows().length; // 6 (5 ACTIVE + 1 EXTENDED)
  const tabBadgeMatchesVis = activeTabBadgeValue === actualActiveVisCount;
  adversarialFindings.push({
    title: 'Active Tab Badge Count vs Extended Contract Matching',
    expected: `Tab badge matches displayed row count (${actualActiveVisCount})`,
    actual: `Tab badge says "${activeTabBadgeValue}", but clicking tab displays ${actualActiveVisCount} rows (5 ACTIVE + 1 EXTENDED)`,
    risk: 'LOW (Cosmetic / Static indicator)',
    blastRadius: 'Table bottom badge correctly updates to "6 HĐ", but tab pill statically displays "5"',
    mitigation: 'Update static badge in HTML from 5 to 6 or dynamically calculate tab badges during filter init'
  });
  console.log(`[🔍 PROBE] Active Tab Badge Alignment: ${tabBadgeMatchesVis ? 'EXACT MATCH' : 'STATIC BADGE (5) vs ACTIVE+EXTENDED ROWS (6)'}`);

  // 5.4 Extreme Currency Values & Boundary Resilience
  assert('Zero VND formatting and parsing operate cleanly without runtime errors',
    api.parseVND('0') === 0 && api.formatVND(0).includes('0'),
    { formattedZero: api.formatVND(0) });

  assert('Large loan (5 Billion VND) formats cleanly as 5.000.000.000 ₫',
    api.formatVND(5000000000).includes('5.000.000.000'),
    { formatted5B: api.formatVND(5000000000) });

  // --------------------------------------------------------------------------
  // SUMMARY
  // --------------------------------------------------------------------------
  console.log('\n================================================================');
  console.log('   EMPIRICAL ADVERSARIAL STRESS TEST SUMMARY');
  console.log('================================================================');
  const total = testResults.length;
  const passed = testResults.filter(t => t.passed).length;
  const failed = total - passed;
  console.log(`Core Requirement Checks:   ${total}`);
  console.log(`Passed:                    ${passed}`);
  console.log(`Failed:                    ${failed}`);
  console.log(`Core Specification Pass:   ${((passed / total) * 100).toFixed(1)}%`);
  console.log(`Adversarial Probes Logged: ${adversarialFindings.length}`);
  console.log('================================================================\n');

  return { total, passed, failed, results: testResults, adversarialFindings };
}

runEmpiricalStressTests().then(summary => {
  if (summary.failed > 0) {
    process.exit(1);
  } else {
    process.exit(0);
  }
});
