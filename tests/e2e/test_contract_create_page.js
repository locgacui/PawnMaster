/**
 * PawnMaster — Dedicated Contract Origination Workspace (/contracts/new)
 * Comprehensive Static DOM/CSS/JS Contract, Craft Floor, WCAG & Interactive Simulation Audit
 */

const fs = require('fs');
const path = require('path');
const vm = require('vm');
const TestCollector = require('../lib/collector');
const DomInspector = require('../lib/dom_inspector');
const StyleParser = require('../lib/style_parser');
const { passesWcagAA } = require('../lib/contrast');

const PROJECT_ROOT = path.resolve(__dirname, '../..');
const DASHBOARD_HTML_PATH = path.join(PROJECT_ROOT, 'src/main/resources/templates/dashboard.html');
const DASHBOARD_CSS_PATH = path.join(PROJECT_ROOT, 'src/main/resources/static/css/dashboard.css');
const HTML_PATH = path.join(PROJECT_ROOT, 'src/main/resources/templates/contract-create.html');
const CSS_PATH = path.join(PROJECT_ROOT, 'src/main/resources/static/css/contract-create.css');
const JS_PATH = path.join(PROJECT_ROOT, 'src/main/resources/static/js/contract-create.js');

/**
 * Lightweight Mini-DOM Simulator for End-to-End Interactive Behavioral Verification
 * Parses elements with IDs, classes, options, and event listeners from contract-create.html
 * so contract-create.js can run real event flows in pure Node.js without external npm packages.
 */
function createInteractiveDomSandbox(htmlRaw, fetchHandler) {
  const elementsById = new Map();
  const allElements = [];

  class MockClassList {
    constructor(el, initial = '') {
      this._el = el;
      this._set = new Set(initial.split(/\s+/).filter(Boolean));
    }
    _sync() {
      this._el.className = Array.from(this._set).join(' ');
    }
    add(...names) {
      names.forEach(n => { if (n) this._set.add(n); });
      this._sync();
    }
    remove(...names) {
      names.forEach(n => this._set.delete(n));
      this._sync();
    }
    toggle(name, force) {
      if (force === undefined) {
        if (this._set.has(name)) {
          this._set.delete(name);
          this._sync();
          return false;
        }
        this._set.add(name);
        this._sync();
        return true;
      }
      if (force) this._set.add(name);
      else this._set.delete(name);
      this._sync();
      return Boolean(force);
    }
    contains(name) {
      return this._set.has(name);
    }
  }

  class MockElement {
    constructor(tagName, attrs = {}, innerHtml = '') {
      this.tagName = String(tagName || 'DIV').toUpperCase();
      this._attrs = Object.assign({}, attrs);
      this.id = this._attrs.id || '';
      this.type = this._attrs.type || (this.tagName === 'SELECT' ? 'select-one' : 'text');
      this.value = this._attrs.value !== undefined ? this._attrs.value : '';
      this.checked = Object.prototype.hasOwnProperty.call(this._attrs, 'checked');
      this.disabled = Object.prototype.hasOwnProperty.call(this._attrs, 'disabled');
      this.className = this._attrs.class || '';
      this.classList = new MockClassList(this, this.className);
      this.style = {};
      this.dataset = {};
      this.listeners = {};
      this.children = [];
      this.options = [];
      this.files = [];
      this.selectedIndex = 0;
      this.offsetWidth = 100;
      this.focused = false;
      this.clicked = false;
      this._innerHTML = innerHtml || '';
      this.textContent = innerHtml ? innerHtml.replace(/<[^>]+>/g, '').trim() : '';

      Object.keys(this._attrs).forEach(k => {
        if (k.startsWith('data-')) {
          const camel = k.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase());
          this.dataset[camel] = this._attrs[k];
        }
      });
    }

    get innerHTML() {
      return this._innerHTML;
    }

    set innerHTML(val) {
      this._innerHTML = String(val || '');
      this.textContent = this._innerHTML.replace(/<[^>]+>/g, '').trim();
      if (this.tagName === 'SELECT') {
        this._parseSelectOptions(this._innerHTML);
      } else {
        this._parseDynamicLinks(this._innerHTML);
      }
    }

    _parseSelectOptions(html) {
      const optRegex = /<option([^>]*)>([\s\S]*?)<\/option>/gi;
      const opts = [];
      let m;
      let selectedIdx = 0;
      while ((m = optRegex.exec(html)) !== null) {
        const attrStr = m[1] || '';
        const text = m[2].replace(/<[^>]+>/g, '').replace(/&amp;/g, '&').trim();
        const attrs = parseTagAttributes(attrStr);
        const optEl = new MockElement('OPTION', attrs, text);
        optEl.textContent = text;
        optEl.value = attrs.value !== undefined ? attrs.value : text;
        optEl.remove = () => {
          const idx = this.options.indexOf(optEl);
          if (idx >= 0) this.options.splice(idx, 1);
        };
        if (Object.prototype.hasOwnProperty.call(attrs, 'selected')) {
          selectedIdx = opts.length;
        }
        opts.push(optEl);
      }
      this.options = opts;
      if (opts.length > 0) {
        this.selectedIndex = selectedIdx;
        this._value = opts[selectedIdx].value;
      } else {
        this.selectedIndex = -1;
        this._value = '';
      }
    }

    _parseDynamicLinks(html) {
      this.children = [];
      const linkRegex = /<(a|button)([^>]*)>([\s\S]*?)<\/\1>/gi;
      let m;
      while ((m = linkRegex.exec(html)) !== null) {
        const attrs = parseTagAttributes(m[2] || '');
        const childEl = new MockElement(m[1].toUpperCase(), attrs, m[3]);
        childEl.parentElement = this;
        this.children.push(childEl);
      }
    }

    get value() {
      return this._value !== undefined ? this._value : '';
    }

    set value(v) {
      const str = String(v == null ? '' : v);
      this._value = str;
      if (this.tagName === 'SELECT' && Array.isArray(this.options)) {
        const idx = this.options.findIndex(o => String(o.value) === str);
        if (idx >= 0) {
          this.selectedIndex = idx;
        }
      }
    }

    getAttribute(name) {
      if (name === 'class') return this.className;
      if (name === 'value') return this.value;
      return Object.prototype.hasOwnProperty.call(this._attrs, name) ? this._attrs[name] : null;
    }

    setAttribute(name, val) {
      const str = String(val);
      this._attrs[name] = str;
      if (name === 'class') {
        this.className = str;
        this.classList = new MockClassList(this, str);
      } else if (name === 'id') {
        this.id = str;
        elementsById.set(str, this);
      }
    }

    removeAttribute(name) {
      delete this._attrs[name];
    }

    hasAttribute(name) {
      return Object.prototype.hasOwnProperty.call(this._attrs, name);
    }

    addEventListener(type, fn) {
      if (!this.listeners[type]) this.listeners[type] = [];
      this.listeners[type].push(fn);
    }

    async dispatchEvent(eventObj) {
      const evt = Object.assign({
        type: 'click',
        target: this,
        defaultPrevented: false,
        preventDefault() { this.defaultPrevented = true; }
      }, eventObj);
      const handlers = this.listeners[evt.type] || [];
      for (const fn of handlers) {
        await fn.call(this, evt);
      }
      await new Promise(resolve => setTimeout(resolve, 10));
      return evt;
    }

    focus() {
      this.focused = true;
    }

    click() {
      this.clicked = true;
      const handlers = (this.listeners && this.listeners['click']) || [];
      const evt = { type: 'click', target: this, preventDefault() {}, stopPropagation() {} };
      handlers.forEach(fn => fn.call(this, evt));
    }

    reset() {}

    closest(selector) {
      if (selector === '[data-media-action]') {
        return this.hasAttribute('data-media-action') ? this : null;
      }
      if (selector === '[data-lightbox-index]') {
        return this.hasAttribute('data-lightbox-index') ? this : null;
      }
      if (selector && selector.startsWith('.') && this.classList.contains(selector.slice(1))) {
        return this;
      }
      return this.parentElement && typeof this.parentElement.closest === 'function'
        ? this.parentElement.closest(selector)
        : null;
    }

    scrollIntoView() {}

    getBoundingClientRect() {
      return { top: 0, left: 0, width: 800, height: 400 };
    }

    insertAdjacentHTML(pos, html) {
      this._innerHTML += html;
      const idMatch = html.match(/id="([^"]+)"/);
      if (idMatch) {
        const el = new MockElement('DIV', { id: idMatch[1] }, html);
        el.remove = () => elementsById.delete(idMatch[1]);
        elementsById.set(idMatch[1], el);
      }
    }

    appendChild(child) {
      if (child && child.textContent) {
        this.textContent += child.textContent;
      }
      this.children.push(child);
      return child;
    }

    querySelectorAll(selector) {
      if (selector === 'a[data-error-target]') {
        return this.children.filter(c => c.tagName === 'A' && c.hasAttribute('data-error-target'));
      }
      if (selector === '.is-invalid') {
        return allElements.filter(e => e.classList.contains('is-invalid'));
      }
      if (selector === '.pm-autofill-target') {
        return allElements.filter(e => e.classList.contains('pm-autofill-target'));
      }
      if (selector === 'input, select, textarea') {
        return this._groupControls || [];
      }
      return queryFromList(allElements, selector);
    }

    querySelector(selector) {
      const list = this.querySelectorAll(selector);
      return list[0] || null;
    }
  }

  function parseTagAttributes(attrStr) {
    const attrs = {};
    const regex = /([a-zA-Z0-9_:-]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'))?/g;
    let m;
    while ((m = regex.exec(attrStr)) !== null) {
      const key = m[1];
      if (key === '/') continue;
      const val = m[2] !== undefined ? m[2] : (m[3] !== undefined ? m[3] : '');
      attrs[key] = val;
    }
    return attrs;
  }

  function queryFromList(list, selector) {
    const sel = selector.trim();
    if (sel === '#ccCategoryPills .pm-category-pill[data-category]') {
      return list.filter(e => e.classList.contains('pm-category-pill') && e.hasAttribute('data-category'));
    }
    if (sel === '.pm-cccd-sample-chip[data-sample-cccd]') {
      return list.filter(e => e.classList.contains('pm-cccd-sample-chip') && e.hasAttribute('data-sample-cccd'));
    }
    if (sel === '.pm-storage-chip[data-storage]') {
      return list.filter(e => e.classList.contains('pm-storage-chip') && e.hasAttribute('data-storage'));
    }
    if (sel === '.pm-condition-chip[data-condition]') {
      return list.filter(e => e.classList.contains('pm-condition-chip') && e.hasAttribute('data-condition'));
    }
    if (sel === '.pm-ltv-preset-btn[data-ltv-ratio]') {
      return list.filter(e => e.classList.contains('pm-ltv-preset-btn') && e.hasAttribute('data-ltv-ratio'));
    }
    if (sel === '#ccStepperNav .pm-step-item[data-step-target]') {
      return list.filter(e => e.classList.contains('pm-step-item') && e.hasAttribute('data-step-target'));
    }
    if (sel === '[data-bs-toggle="tooltip"]') {
      return list.filter(e => e.getAttribute('data-bs-toggle') === 'tooltip');
    }
    if (sel.startsWith('#')) {
      const el = elementsById.get(sel.slice(1));
      return el ? [el] : [];
    }
    if (sel.startsWith('.')) {
      const cls = sel.slice(1);
      return list.filter(e => e.classList.contains(cls));
    }
    return [];
  }

  // Parse body attributes
  const bodyMatch = htmlRaw.match(/<body([^>]*)>/i);
  const bodyEl = new MockElement('BODY', bodyMatch ? parseTagAttributes(bodyMatch[1]) : {});

  // Parse selects with their inner options first
  const selectRegex = /<select([^>]*)>([\s\S]*?)<\/select>/gi;
  let sm;
  while ((sm = selectRegex.exec(htmlRaw)) !== null) {
    const attrs = parseTagAttributes(sm[1]);
    const selEl = new MockElement('SELECT', attrs, '');
    selEl._parseSelectOptions(sm[2]);
    allElements.push(selEl);
    if (selEl.id) elementsById.set(selEl.id, selEl);
  }

  // Parse textareas
  const taRegex = /<textarea([^>]*)>([\s\S]*?)<\/textarea>/gi;
  let tm;
  while ((tm = taRegex.exec(htmlRaw)) !== null) {
    const attrs = parseTagAttributes(tm[1]);
    const textVal = tm[2].trim();
    const taEl = new MockElement('TEXTAREA', Object.assign({}, attrs, { value: textVal }), textVal);
    allElements.push(taEl);
    if (taEl.id) elementsById.set(taEl.id, taEl);
  }

  // Parse all other tags with id or interactive classes
  const openTagRegex = /<([a-zA-Z0-9]+)(\s+[^>]*?)?(\/?)>/g;
  let om;
  while ((om = openTagRegex.exec(htmlRaw)) !== null) {
    const tag = om[1].toUpperCase();
    if (tag === 'SELECT' || tag === 'TEXTAREA' || tag === 'OPTION' || tag === 'BODY') continue;
    const attrStr = om[2] || '';
    if (!/\b(id|data-category|data-sample-cccd|data-storage|data-condition|data-ltv-ratio|data-step-target)=/.test(attrStr)) {
      continue;
    }
    const attrs = parseTagAttributes(attrStr);
    if (attrs.id && elementsById.has(attrs.id)) continue;

    let innerText = '';
    if (!om[3]) {
      const afterIdx = om.index + om[0].length;
      const closeIdx = htmlRaw.indexOf(`</${om[1]}>`, afterIdx);
      if (closeIdx !== -1 && closeIdx - afterIdx < 600) {
        innerText = htmlRaw.slice(afterIdx, closeIdx);
      }
    }
    const el = new MockElement(tag, attrs, innerText);
    allElements.push(el);
    if (el.id) elementsById.set(el.id, el);
  }

  // Wire dynamic collateral group child controls for toggleDynamicCollateralGroups()
  const groupControlIds = {
    ccVehicleFieldsGroup: ['ccLicensePlate', 'ccFrameNumber', 'ccVehicleRegNumber', 'ccVehicleOdometer'],
    ccTechFieldsGroup: ['ccSerialOrImei', 'ccTechPasscodeStatus', 'ccTechAccessories'],
    ccGoldFieldsGroup: ['ccGoldPurity', 'ccGoldWeightChi', 'ccGemCertificateCode'],
    ccOtherFieldsGroup: ['ccOtherAssetSpec']
  };
  Object.entries(groupControlIds).forEach(([groupId, ctrlIds]) => {
    const grp = elementsById.get(groupId);
    if (grp) {
      grp._groupControls = ctrlIds.map(id => elementsById.get(id)).filter(Boolean);
    }
  });

  const storageMap = new Map();
  const mockLocalStorage = {
    getItem: k => (storageMap.has(k) ? storageMap.get(k) : null),
    setItem: (k, v) => storageMap.set(k, String(v)),
    removeItem: k => storageMap.delete(k)
  };

  let printTriggeredCount = 0;
  let shownModals = [];
  let initializedTooltips = [];
  const windowListeners = {};
  const documentListeners = {};
  const scrollHistory = [];
  const rootCssVars = {};

  const documentMock = {
    readyState: 'complete',
    body: bodyEl,
    documentElement: {
      style: {
        setProperty: (k, v) => { rootCssVars[k] = String(v); },
        getPropertyValue: k => rootCssVars[k] || ''
      }
    },
    cookie: '',
    getElementById: id => elementsById.get(id) || null,
    querySelectorAll: sel => queryFromList(allElements, sel),
    querySelector: sel => queryFromList(allElements, sel)[0] || null,
    createElement: tag => new MockElement(tag),
    createTextNode: txt => ({ textContent: String(txt) }),
    addEventListener: (type, fn) => {
      if (!documentListeners[type]) documentListeners[type] = [];
      documentListeners[type].push(fn);
    },
    dispatchDocumentEvent: async evt => {
      const e = Object.assign({ preventDefault() { this.defaultPrevented = true; }, stopPropagation() {} }, evt);
      for (const fn of (documentListeners[e.type] || [])) {
        await fn(e);
      }
      return e;
    }
  };

  const windowMock = {
    location: { protocol: 'http:', pathname: '/contracts/new', href: 'http://localhost:8080/contracts/new', search: '' },
    document: documentMock,
    localStorage: mockLocalStorage,
    sessionStorage: mockLocalStorage,
    scrollY: 0,
    pageYOffset: 0,
    scrollTo: opts => {
      scrollHistory.push(opts);
      if (opts && typeof opts.top === 'number') {
        windowMock.scrollY = opts.top;
        windowMock.pageYOffset = opts.top;
      }
    },
    addEventListener: (type, fn) => {
      if (!windowListeners[type]) windowListeners[type] = [];
      windowListeners[type].push(fn);
    },
    dispatchWindowEvent: type => {
      (windowListeners[type] || []).forEach(fn => fn({ type }));
    },
    matchMedia: () => ({ matches: false }),
    print: () => { printTriggeredCount++; }
  };

  const bootstrapMock = {
    Modal: class {
      constructor(el) { this.el = el; }
      static getInstance() { return null; }
      show() { shownModals.push(this.el ? this.el.id : 'unknown'); }
      hide() {}
    },
    Toast: class {
      constructor(el) { this.el = el; }
      show() {}
    },
    Tooltip: class {
      constructor(el, opts) {
        this.el = el;
        this.opts = opts || {};
        initializedTooltips.push(el ? el.id || 'tooltip' : 'unknown');
      }
    }
  };

  let blobCounter = 0;
  const urlMock = {
    createObjectURL: file => `blob:mock-${++blobCounter}-${(file && file.name) || 'image'}`,
    revokeObjectURL: () => {}
  };

  class MockFormData {
    constructor() {
      this._entries = [];
    }
    append(key, value, filename) {
      this._entries.push({ key, value, filename: filename || (value && value.name) || null });
    }
    get(key) {
      const hit = this._entries.find(e => e.key === key);
      return hit ? hit.value : null;
    }
    getAll(key) {
      return this._entries.filter(e => e.key === key).map(e => e.value);
    }
    entries() {
      return this._entries;
    }
  }

  const sandbox = {
    window: windowMock,
    document: documentMock,
    localStorage: mockLocalStorage,
    sessionStorage: mockLocalStorage,
    bootstrap: bootstrapMock,
    FormData: MockFormData,
    URL: urlMock,
    Intl,
    Date,
    Math,
    Number,
    String,
    Boolean,
    Array,
    Object,
    Map,
    Set,
    RegExp,
    JSON,
    Promise,
    Error,
    URLSearchParams,
    parseInt,
    parseFloat,
    isNaN,
    encodeURIComponent,
    setTimeout: fn => {
      // Do not auto-fire debounced background timers unless needed
      return 1;
    },
    clearTimeout: () => {},
    setInterval: () => 1,
    clearInterval: () => {},
    requestAnimationFrame: fn => fn(),
    fetch: fetchHandler,
    console
  };

  return {
    sandbox,
    windowMock,
    rootCssVars,
    getScrollHistory: () => scrollHistory,
    elementsById,
    mockLocalStorage,
    documentMock,
    getPrintCount: () => printTriggeredCount,
    getShownModals: () => shownModals,
    getInitializedTooltips: () => initializedTooltips
  };
}

async function runContractCreatePageTests() {
  console.log('\n================================================================================');
  console.log('  PAWNMASTER CONTRACT ORIGINATION WORKSPACE (/contracts/new) — E2E & CRAFT SUITE');
  console.log('================================================================================\n');

  const collector = new TestCollector();

  const dashboardDom = DomInspector.fromFile(DASHBOARD_HTML_PATH);
  const dashboardCss = StyleParser.fromFile(DASHBOARD_CSS_PATH);
  const dom = DomInspector.fromFile(HTML_PATH);
  const css = StyleParser.fromFile(CSS_PATH);
  const jsRaw = fs.readFileSync(JS_PATH, 'utf8');
  const ccExports = require(JS_PATH);

  // ===========================================================================
  // TIER 1: XML VALIDITY, SECTIONS & CROSS-FILE DOM/CSS/JS CONTRACT PARITY
  // ===========================================================================
  console.log('► Tier 1: XML Validity, 4-Step Workspace Sections & 100% DOM/CSS/JS Parity...');

  const xmlCheck = dom.validateXmlStructure();
  collector.recordCheck({
    tier: 1,
    checkName: 'contract-create.html has strictly valid XML self-closing void tags (<meta />, <link />, <input />, <hr />, <br />) & balanced tags',
    passed: xmlCheck.isValid,
    expected: '0 XML tag balance or void-tag errors',
    actual: xmlCheck.isValid ? '0 XML errors' : xmlCheck.errors.slice(0, 3).join('; ')
  });

  collector.recordCheck({
    tier: 1,
    checkName: 'contract-create.html includes dual static + Thymeleaf links for dashboard.css, contract-create.css, and contract-create.js',
    passed: dom.contains('href="../static/css/dashboard.css"') &&
            dom.contains('th:href="@{/css/dashboard.css') &&
            dom.contains('href="../static/css/contract-create.css"') &&
            dom.contains('th:href="@{/css/contract-create.css') &&
            dom.contains('src="../static/js/contract-create.js"') &&
            dom.contains('th:src="@{/js/contract-create.js'),
    expected: 'Dual static/Thymeleaf CSS and JS links present',
    actual: 'Verified dual-mode asset paths'
  });

  const openFullBtn = dashboardDom.getElementById('btnOpenFullContractPage');
  const backDashBtn = dom.getElementById('btnBackToDashboard');
  const succDashBtn = dom.getElementById('btnGoToDashboardAfterSuccess');

  collector.recordCheck({
    tier: 1,
    checkName: 'Dual Static/Thymeleaf navigation links: dashboard.html (#btnOpenFullContractPage only, legacy #modalNewContract removed) <-> contract-create.html',
    passed: Boolean(openFullBtn && openFullBtn.openTag.includes('href="contract-create.html"') && openFullBtn.openTag.includes('@{/contracts/new')) &&
            !dashboardDom.hasElementById('modalNewContract') &&
            !dashboardDom.hasElementById('btnExpandFullContractWorkspace') &&
            !dashboardDom.contains('data-bs-target="#modalNewContract"') &&
            !dashboardDom.contains('Cầm Đồ Mới') &&
            Boolean(backDashBtn && backDashBtn.openTag.includes('href="dashboard.html"') && backDashBtn.openTag.includes('@{/dashboard')) &&
            Boolean(succDashBtn && succDashBtn.openTag.includes('href="dashboard.html"') && succDashBtn.openTag.includes('@{/dashboard')) &&
            !dom.contains('href="/dashboard"') &&
            jsRaw.includes('function isStaticPreviewMode()') &&
            jsRaw.includes('function resolvePageRoute('),
    expected: 'Single canonical #btnOpenFullContractPage link (legacy #modalNewContract removed) + Dual href="dashboard.html"/th:href="@{/dashboard}"',
    actual: 'Verified canonical origination entry point & Dual Static/Thymeleaf page links'
  });

  // Parse EVERY getElementById('...') in contract-create.js
  const staticGetByIdMatches = [...jsRaw.matchAll(/getElementById\(\s*['"]([^'"]+)['"]\s*\)/g)].map(m => m[1]);
  const draftArrayMatch = jsRaw.match(/const\s+DRAFT_FIELD_IDS\s*=\s*\[([\s\S]*?)\];/);
  const draftFieldIds = draftArrayMatch
    ? [...draftArrayMatch[1].matchAll(/['"]([^'"]+)['"]/g)].map(m => m[1])
    : [];
  const allReferencedIds = Array.from(new Set([...staticGetByIdMatches, ...draftFieldIds]));
  const missingHtmlIds = allReferencedIds.filter(id => !dom.hasElementById(id));

  collector.recordCheck({
    tier: 1,
    checkName: `100% of static IDs referenced in contract-create.js (${allReferencedIds.length} unique IDs) exist in contract-create.html`,
    passed: missingHtmlIds.length === 0 && allReferencedIds.length >= 90,
    expected: '0 missing IDs between contract-create.js and contract-create.html',
    actual: missingHtmlIds.length === 0
      ? `${allReferencedIds.length}/${allReferencedIds.length} IDs matched (0 missing)`
      : `Missing IDs: ${missingHtmlIds.join(', ')}`
  });

  // Parse EVERY querySelector / querySelectorAll in contract-create.js
  const querySelectors = [...jsRaw.matchAll(/querySelector(?:All)?\(\s*['"]([^'"]+)['"]\s*\)/g)].map(m => m[1]);
  const uniqueStaticSelectors = Array.from(new Set(querySelectors)).filter(sel =>
    sel !== '.is-invalid' &&
    sel !== 'a[data-error-target]' &&
    sel !== 'input, select, textarea'
  );
  const missingSelectors = uniqueStaticSelectors.filter(sel => {
    const classMatches = [...sel.matchAll(/\.([a-zA-Z0-9_-]+)/g)].map(m => m[1]);
    const idMatches = [...sel.matchAll(/#([a-zA-Z0-9_-]+)/g)].map(m => m[1]);
    const attrMatches = [...sel.matchAll(/\[([a-zA-Z0-9_-]+)\]/g)].map(m => m[1]);
    const idsOk = idMatches.every(id => dom.hasElementById(id));
    const classesOk = classMatches.every(cls => dom.getElementsByClassName(cls).length > 0);
    const attrsOk = attrMatches.every(attr => dom.contains(`${attr}=`));
    return !(idsOk && classesOk && attrsOk);
  });

  collector.recordCheck({
    tier: 1,
    checkName: `100% of static querySelector/querySelectorAll selectors in contract-create.js (${uniqueStaticSelectors.length}) exist in contract-create.html`,
    passed: missingSelectors.length === 0,
    expected: '0 unmatched selectors',
    actual: missingSelectors.length === 0
      ? `${uniqueStaticSelectors.length}/${uniqueStaticSelectors.length} selectors matched`
      : `Unmatched: ${missingSelectors.join(', ')}`
  });

  // Parse EVERY pm-* class in contract-create.html and verify CSS coverage across dashboard.css + contract-create.css
  const combinedCssRaw = `${dashboardCss.raw}\n${css.raw}`;
  const htmlClassAttrs = dom.getAttributes('class');
  const pmClassesInHtml = Array.from(new Set(
    htmlClassAttrs
      .flatMap(c => c.split(/\s+/))
      .filter(c => c.startsWith('pm-'))
  ));
  const unstyledPmClasses = pmClassesInHtml.filter(cls => {
    const escaped = cls.replace(/[-/\\^$*+?.()|[\]{}]/g, '\\$&');
    return !new RegExp(`\\.${escaped}\\b`).test(combinedCssRaw);
  });

  collector.recordCheck({
    tier: 1,
    checkName: `100% of custom Vault Ledger classes (${pmClassesInHtml.length} pm-* classes) in contract-create.html are styled in CSS`,
    passed: unstyledPmClasses.length === 0,
    expected: '0 unstyled pm-* classes',
    actual: unstyledPmClasses.length === 0
      ? `${pmClassesInHtml.length}/${pmClassesInHtml.length} pm-* classes styled`
      : `Unstyled classes: ${unstyledPmClasses.join(', ')}`
  });

  // Verify all 4 sections, sticky HUD, sticky ledger, back-to-top, and modals exist
  const coreSections = [
    'ccStickyWorkspaceHud', 'ccStepperNav', 'formCreateContract', 'ccFormErrorSummary',
    'ccErrorSummaryCountBadge', 'btnToggleErrorSummary',
    'sectionCustomer', 'ccPermanentAddressBox', 'ccProvinceCode', 'ccDistrictCode', 'ccWardCode', 'ccAddressStreet', 'ccPermanentAddressFull',
    'sectionCollateral', 'ccCategoryPills', 'ccVehicleFieldsGroup', 'ccTechFieldsGroup', 'ccGoldFieldsGroup', 'ccOtherFieldsGroup',
    'sectionLoanTerms', 'ccPaymentScheduleTable', 'ccPaymentScheduleBody',
    'sectionCompliance', 'chkVerifyOriginalCccd', 'chkVerifyAssetSeal', 'chkCustomerAgreedTerms',
    'ccStickyLedger', 'ccLiveCalculationBox', 'btnBackToTop', 'modalContractPrintPreview', 'modalOriginationSuccess'
  ];
  const missingCore = coreSections.filter(id => !dom.hasElementById(id));

  // Verify #ccStickyWorkspaceHud wraps BOTH #ccStepperNav and #ccFormErrorSummary inside #formCreateContract,
  // and #ccStickyLedger has pinned .pm-ledger-footer containing #btnSubmitOrigination
  const formStartIdx = dom.raw.indexOf('id="formCreateContract"');
  const hudStartIdx = dom.raw.indexOf('id="ccStickyWorkspaceHud"');
  const stepperIdx = dom.raw.indexOf('id="ccStepperNav"');
  const errorSumIdx = dom.raw.indexOf('id="ccFormErrorSummary"');
  const gridIdx = dom.raw.indexOf('pm-origination-grid');
  const ledgerFooterIdx = dom.raw.indexOf('class="pm-ledger-footer"');
  const submitBtnIdx = dom.raw.indexOf('id="btnSubmitOrigination"');

  collector.recordCheck({
    tier: 1,
    checkName: 'All 4 form sections, sticky workspace HUD (#ccStepperNav + #ccFormErrorSummary), pinned sticky ledger footer, #btnBackToTop, and modals exist',
    passed: missingCore.length === 0 &&
            formStartIdx > 0 &&
            hudStartIdx > formStartIdx &&
            stepperIdx > hudStartIdx &&
            errorSumIdx > stepperIdx &&
            gridIdx > errorSumIdx &&
            ledgerFooterIdx > gridIdx &&
            submitBtnIdx > ledgerFooterIdx,
    expected: 'All 31 landmarks present; #ccStickyWorkspaceHud wraps stepper + error summary above grid; .pm-ledger-footer wraps #btnSubmitOrigination',
    actual: missingCore.length === 0 ? '31/31 landmarks & sticky hierarchy verified' : `Missing: ${missingCore.join(', ')}`
  });

  // Verify Phase 10: Local file pickers + Lightbox exist, and "Link Hình Ảnh" / "Link Ảnh" prefix is 100% removed
  const mediaLandmarks = [
    'ccDocumentFileInput', 'ccVerificationDocBox', 'ccVerificationDocGallery', 'btnPickVerificationDocs', 'ccDocumentUrl',
    'ccIdCardFrontFileInput', 'ccIdCardFrontSlot', 'ccIdCardFrontUrl',
    'ccIdCardBackFileInput', 'ccIdCardBackSlot', 'ccIdCardBackUrl',
    'ccPortraitFileInput', 'ccPortraitSlot', 'ccPortraitUrl',
    'ccCollateralFileInput', 'ccCollateralImageBox', 'ccCollateralImageGallery', 'ccCollateralImageUrls',
    'modalImagePreviewLightbox', 'lightboxMainImage', 'lightboxFilmstrip',
    'btnLightboxZoomIn', 'btnLightboxZoomOut', 'btnLightboxRotateRight', 'btnLightboxPrev', 'btnLightboxNext'
  ];
  const missingMediaIds = mediaLandmarks.filter(id => !dom.hasElementById(id));
  const legacyLinkPrefixRegex = /Link\s+(?:Hình\s+Ảnh|hình\s+ảnh|Ảnh|ảnh)/i;
  const hasLegacyLinkInHtml = legacyLinkPrefixRegex.test(dom.raw);
  const hasLegacyLinkInJs = legacyLinkPrefixRegex.test(jsRaw);

  collector.recordCheck({
    tier: 1,
    checkName: 'Vault Evidence Desk UI (.pm-kyc-identity-grid + .pm-evidence-board + #modalImagePreviewLightbox) replaces raw URLs and removes "Link" prefix 100%',
    passed: missingMediaIds.length === 0 &&
            !hasLegacyLinkInHtml &&
            !hasLegacyLinkInJs &&
            dom.contains('pm-kyc-media-desk') &&
            dom.contains('pm-kyc-identity-grid') &&
            dom.contains('pm-evidence-board') &&
            dom.contains('pm-evidence-grid') &&
            css.raw.includes('.pm-evidence-tile') &&
            css.raw.includes('.pm-kyc-frame-card'),
    expected: '21/21 media/lightbox landmarks + .pm-kyc-identity-grid/.pm-evidence-board/.pm-evidence-tile/.pm-kyc-frame-card present & 0 "Link" prefix',
    actual: missingMediaIds.length === 0
      ? `All landmarks & Vault Evidence Desk classes present, legacyLinkHtml=${hasLegacyLinkInHtml}, legacyLinkJs=${hasLegacyLinkInJs}`
      : `Missing IDs: ${missingMediaIds.join(', ')}`
  });

  // ===========================================================================
  // TIER 2: CASCADING ADDRESS, CURRENCY-TO-WORDS, LTV MATRIX & SCHEDULE MATH
  // ===========================================================================
  console.log('► Tier 2: Cascading Address Catalog, Vietnamese Currency Words, LTV & Schedule Math...');

  collector.recordCheck({
    tier: 2,
    checkName: 'contract-create.js wires all 3 REST cascading address endpoints (/provinces, /provinces/{code}/districts, /districts/{code}/wards) and binds fullName || name',
    passed: jsRaw.includes('/api/v1/branches/provinces') &&
            jsRaw.includes('/districts') &&
            jsRaw.includes('/wards') &&
            /fullName\s*\|\|\s*[a-zA-Z0-9_.]+\.name/.test(jsRaw),
    expected: 'All 3 cascading endpoints + fullName || name option binding',
    actual: 'Verified API paths and fullName || name binding'
  });

  const hcmDistricts = ccExports.OFFLINE_ADMIN_CATALOG.districts['79'] || [];
  const q1District = hcmDistricts.find(d => d.code === '760');
  const q1Wards = ccExports.OFFLINE_ADMIN_CATALOG.wards['760'] || [];
  const benNgheWard = q1Wards.find(w => w.code === '26740');
  const offlineCust1 = ccExports.OFFLINE_CUSTOMER_PROFILES && ccExports.OFFLINE_CUSTOMER_PROFILES['079201004821'];
  const offlineCust2 = ccExports.OFFLINE_CUSTOMER_PROFILES && ccExports.OFFLINE_CUSTOMER_PROFILES['079198005432'];

  collector.recordCheck({
    tier: 2,
    checkName: 'Offline GSO catalog & OFFLINE_CUSTOMER_PROFILES match data.sql ("01", "79", "760" -> "Quận 1", "26740" -> "Phường Bến Nghé", "27139" -> "Phường Võ Thị Sáu")',
    passed: Boolean(q1District && q1District.fullName === 'Quận 1' && benNgheWard && benNgheWard.fullName === 'Phường Bến Nghé') &&
            ccExports.OFFLINE_ADMIN_CATALOG.provinces.some(p => p.code === '01' && p.fullName === 'Thành phố Hà Nội') &&
            Boolean(offlineCust1 && !('branchId' in offlineCust1) && offlineCust1.provinceCode === '79' && offlineCust1.districtCode === '760' && offlineCust1.wardCode === '26740' && offlineCust1.addressDetail === '88 Nguyễn Huệ') &&
            Boolean(offlineCust2 && !('branchId' in offlineCust2) && offlineCust2.provinceCode === '79' && offlineCust2.districtCode === '770' && offlineCust2.wardCode === '27139' && offlineCust2.addressDetail === '154 Nguyễn Đình Chiểu'),
    expected: '760 -> "Quận 1", 26740 -> "Phường Bến Nghé", 01 -> "Thành phố Hà Nội" + OFFLINE_CUSTOMER_PROFILES wardCode/districtCode/addressDetail',
    actual: `${q1District ? q1District.fullName : 'null'} / ${benNgheWard ? benNgheWard.fullName : 'null'} | cust1.ward=${offlineCust1 ? offlineCust1.wardCode : 'null'}`
  });

  // Vietnamese Currency-to-Words checks
  const wordCases = [
    { amount: 0, expected: 'Không đồng' },
    { amount: 60000000, expected: 'Sáu mươi triệu đồng chẵn' },
    { amount: 45000000, expected: 'Bốn mươi lăm triệu đồng chẵn' },
    { amount: 1050000, expected: 'Một triệu không trăm năm mươi nghìn đồng chẵn' },
    { amount: 1004000, expected: 'Một triệu không trăm linh bốn nghìn đồng chẵn' },
    { amount: 21500500, expected: 'Hai mươi mốt triệu năm trăm nghìn năm trăm đồng' }
  ];
  const failedWords = wordCases.filter(tc => ccExports.readVndInWords(tc.amount) !== tc.expected);

  collector.recordCheck({
    tier: 2,
    checkName: 'readVndInWords() converts VND amounts into grammatically exact Vietnamese legal words (linh, mốt, tư, lăm, đồng chẵn/đồng)',
    passed: failedWords.length === 0,
    expected: '6/6 Vietnamese currency-to-words edge cases exact match',
    actual: failedWords.length === 0
      ? '6/6 exact matches verified'
      : failedWords.map(f => `${f.amount}: got "${ccExports.readVndInWords(f.amount)}", expected "${f.expected}"`).join(' | ')
  });

  // Verify 48-cell LTV Matrix parity with LtvPolicyDataInitializer
  const occKeys = Object.keys(ccExports.LTV_MATRIX);
  const totalCells = occKeys.reduce((acc, k) => acc + Object.keys(ccExports.LTV_MATRIX[k]).length, 0);
  collector.recordCheck({
    tier: 2,
    checkName: 'LTV_MATRIX defines all 48 (6 occupations × 8 categories) policy ceilings matching LtvPolicyDataInitializer (50% to 92%)',
    passed: occKeys.length === 6 &&
            totalCells === 48 &&
            ccExports.LTV_MATRIX.OFFICE_WORKER.GOLD_JEWELRY === 0.92 &&
            ccExports.LTV_MATRIX.OFFICE_WORKER.MOTORBIKE === 0.90 &&
            ccExports.LTV_MATRIX.SMALL_MERCHANT.CAR === 0.80 &&
            ccExports.LTV_MATRIX.FREELANCER.OTHER === 0.50,
    expected: '48 cells exact match (0.92 max, 0.50 min)',
    actual: `${totalCells} cells verified`
  });

  // Verify Schedule Calculation for DAILY_PER_MILLION & MONTHLY_PERCENT
  const dailyCycles = ccExports.buildPaymentScheduleCycles('2026-11-11', 30, 10, 45000000, 'DAILY_PER_MILLION', 2000, 50000);
  const monthlyCycles = ccExports.buildPaymentScheduleCycles('2026-11-11', 30, 15, 45000000, 'MONTHLY_PERCENT', 3, 0);

  collector.recordCheck({
    tier: 2,
    checkName: 'buildPaymentScheduleCycles() computes exact cycle interest & storage fees for DAILY_PER_MILLION and MONTHLY_PERCENT',
    passed: dailyCycles.length === 3 &&
            dailyCycles[0].interestAmt === 900000 &&
            dailyCycles[0].storageAmt === 50000 &&
            dailyCycles[0].totalAmt === 950000 &&
            dailyCycles[2].label === 'Kỳ 3 (Đáo hạn)' &&
            monthlyCycles.length === 2 &&
            monthlyCycles[0].interestAmt === 675000,
    expected: 'Daily: 3×(900k+50k=950k); Monthly 3%: 2×675k',
    actual: `Daily cycle 1=${dailyCycles[0]?.totalAmt}, Monthly cycle 1=${monthlyCycles[0]?.interestAmt}`
  });

  // Verify System-Locale Date Formatting across locales + Dashboard & Rule parity
  const dashboardJsRaw = fs.readFileSync(path.join(PROJECT_ROOT, 'src/main/resources/static/js/dashboard.js'), 'utf8');
  const designMdRaw = fs.readFileSync(path.join(PROJECT_ROOT, 'DESIGN.md'), 'utf8');
  const dateRuleExists = fs.existsSync(path.join(PROJECT_ROOT, '.agents/rules/date-locale.md'));
  const fmtVi = ccExports.formatSystemDate('1992-05-14', 'vi-VN');
  const fmtUs = ccExports.formatSystemDate('1992-05-14', 'en-US');
  const fmtJp = ccExports.formatSystemDate('1992-05-14', 'ja-JP');
  const patVi = ccExports.getSystemDateFormatPattern('vi-VN');
  const patUs = ccExports.getSystemDateFormatPattern('en-US');
  const parsedVi = ccExports.parseSystemDateToIso('14/05/1992', 'vi-VN');
  const parsedVi8 = ccExports.parseSystemDateToIso('19082022', 'vi-VN');
  const parsedUs = ccExports.parseSystemDateToIso('05/14/92', 'en-US');
  const parsedInvalid = ccExports.parseSystemDateToIso('31/02/2026', 'vi-VN');

  collector.recordCheck({
    tier: 2,
    checkName: 'System-locale date formatting & input adapter (.pm-locale-date-wrap + parseSystemDateToIso) synchronize #ccDateOfBirth, #ccIdentityIssueDate, and #ccStartDate across vi-VN, en-US, ja-JP',
    passed: fmtVi === '14/05/1992' &&
            fmtUs === '05/14/1992' &&
            fmtJp === '1992/05/14' &&
            patVi === 'dd/mm/yyyy' &&
            patUs === 'mm/dd/yyyy' &&
            parsedVi === '1992-05-14' &&
            parsedVi8 === '2022-08-19' &&
            parsedUs === '1992-05-14' &&
            parsedInvalid === '' &&
            dashboardCss.raw.includes('.pm-locale-date-wrap') &&
            !jsRaw.includes("toLocaleDateString('vi-VN'") &&
            !dashboardJsRaw.includes("toLocaleDateString('vi-VN'") &&
            dashboardJsRaw.includes('function formatSystemDate') &&
            dashboardJsRaw.includes('function parseSystemDateToIso') &&
            dashboardJsRaw.includes('function syncSystemDateInputsLocale') &&
            dashboardJsRaw.includes('function hydrateStaticDatesToSystemLocale') &&
            designMdRaw.includes('The System-Locale Date Rule') &&
            dateRuleExists,
    expected: 'vi-VN=14/05/1992 (parse->1992-05-14), en-US=05/14/1992 (05/14/92->1992-05-14), invalid 31/02->empty, .pm-locale-date-wrap CSS & JS active',
    actual: `vi-VN=${fmtVi} (parsed=${parsedVi}, 8digit=${parsedVi8}), en-US=${fmtUs} (2digitYr=${parsedUs}), invalid="${parsedInvalid}"`
  });

  // Verify CCCD Issue Place Dropdown + Smart Auto-Select Date Ranges (suggestIdentityIssuePlaceByDate)
  const issuePlaceDom = dom.getElementById('ccIdentityIssuePlace');
  const issuePlaceCustomWrapDom = dom.getElementById('ccIdentityIssuePlaceCustomWrap');
  const issuePlaceCustomInputDom = dom.getElementById('ccIdentityIssuePlaceCustom');
  const fnSuggestPlace = typeof ccExports.suggestIdentityIssuePlaceByDate === 'function'
    ? ccExports.suggestIdentityIssuePlaceByDate
    : () => null;
  const sug2024Start = fnSuggestPlace('2024-07-01');
  const sug2025 = fnSuggestPlace('2025-03-20');
  const sug2018Start = fnSuggestPlace('2018-10-10');
  const sug2024End = fnSuggestPlace('2024-06-30');
  const sug2016Start = fnSuggestPlace('2016-01-01');
  const sug2018End = fnSuggestPlace('2018-10-09');
  const sugBefore2016 = fnSuggestPlace('2015-12-31');

  collector.recordCheck({
    tier: 2,
    checkName: 'Nơi Cấp CCCD (#ccIdentityIssuePlace) is a 4-option dropdown with free-text #ccIdentityIssuePlaceCustom and exact legal date-range auto-suggestion (>=01/07/2024, 10/10/2018..30/06/2024, 01/01/2016..09/10/2018)',
    passed: Boolean(issuePlaceDom && issuePlaceDom.tagName === 'select') &&
            Boolean(issuePlaceCustomWrapDom && issuePlaceCustomInputDom) &&
            dom.contains('Bộ Công an') &&
            dom.contains('Cục Cảnh sát quản lý hành chính về trật tự xã hội') &&
            dom.contains('Cục Cảnh sát ĐKQL cư trú và DLQG về dân cư') &&
            dom.contains('Khác / Công an Tỉnh, Thành phố') &&
            sug2024Start === 'Bộ Công an' &&
            sug2025 === 'Bộ Công an' &&
            sug2018Start === 'Cục Cảnh sát quản lý hành chính về trật tự xã hội' &&
            sug2024End === 'Cục Cảnh sát quản lý hành chính về trật tự xã hội' &&
            sug2016Start === 'Cục Cảnh sát ĐKQL cư trú và DLQG về dân cư' &&
            sug2018End === 'Cục Cảnh sát ĐKQL cư trú và DLQG về dân cư' &&
            sugBefore2016 === 'OTHER',
    expected: '<select id="ccIdentityIssuePlace"> + custom input + exact boundary matches for 2024-07-01, 2018-10-10..2024-06-30, 2016-01-01..2018-10-09',
    actual: `tag=${issuePlaceDom ? issuePlaceDom.tagName : 'null'}, >=2024-07-01="${sug2024Start}", 2018..2024="${sug2018Start}"/"${sug2024End}", 2016..2018="${sug2016Start}"/"${sug2018End}"`
  });

  // Verify PERCENT_WEEKLY interest math, 7/14 day cycles, and 35-bank catalog + ASCII holder normalization
  const bankSelectDom = dom.getElementById('ccBankCode');
  const cycleDom = dom.getElementById('ccPaymentCycle');
  const intTypeDom = dom.getElementById('ccInterestType');
  const bankOptCount = (dom.raw.match(/<option value="[A-Z0-9]+" data-short-name="/g) || []).length;
  const asciiHolderSample = typeof ccExports.toBankAccountHolderAscii === 'function'
    ? ccExports.toBankAccountHolderAscii('Đặng Thị Bích Ngọc')
    : '';
  const weeklyCalc7 = typeof ccExports.computeCycleAmounts === 'function'
    ? ccExports.computeCycleAmounts(20000000, 'PERCENT_WEEKLY', 1.4, 0, 7, 7)
    : { interestAmt: 0 };
  const weeklyCalc14 = typeof ccExports.computeCycleAmounts === 'function'
    ? ccExports.computeCycleAmounts(20000000, 'PERCENT_WEEKLY', 1.4, 0, 14, 14)
    : { interestAmt: 0 };

  collector.recordCheck({
    tier: 2,
    checkName: 'Section 3 PERCENT_WEEKLY math (7/14-day cycle), 35-bank dropdown (#ccBankCode), searchable combobox DOM, and Vietnamese ASCII holder normalization',
    passed: Array.isArray(ccExports.VN_BANKS_CATALOG) &&
            ccExports.VN_BANKS_CATALOG.length === 35 &&
            Boolean(bankSelectDom && bankSelectDom.tagName === 'select') &&
            Boolean(dom.getElementById('ccBankCombobox') && dom.getElementById('ccBankSearchInput') && dom.getElementById('ccBankDropdownMenu')) &&
            bankOptCount === 35 &&
            Boolean(cycleDom && dom.contains('value="7"') && dom.contains('value="14"')) &&
            Boolean(intTypeDom && dom.contains('value="PERCENT_WEEKLY"')) &&
            asciiHolderSample === 'DANG THI BICH NGOC' &&
            weeklyCalc7.interestAmt === 280000 &&
            weeklyCalc14.interestAmt === 560000,
    expected: '35 banks in catalog + DOM select + searchable combobox, cycles 7 & 14, PERCENT_WEEKLY option, ASCII holder, 20M @ 1.4%/wk -> 280k (7d) & 560k (14d)',
    actual: `banks=${ccExports.VN_BANKS_CATALOG ? ccExports.VN_BANKS_CATALOG.length : 0}/${bankOptCount}, ascii="${asciiHolderSample}", 7d=${weeklyCalc7.interestAmt}, 14d=${weeklyCalc14.interestAmt}`
  });

  const rankedBidv = typeof ccExports.rankBanksByQuery === 'function' ? ccExports.rankBanksByQuery('bidv') : [];
  const rankedDauTu = typeof ccExports.rankBanksByQuery === 'function' ? ccExports.rankBanksByQuery('dau tu') : [];
  const rankedBin = typeof ccExports.rankBanksByQuery === 'function' ? ccExports.rankBanksByQuery('970418') : [];
  const rankedEmpty = typeof ccExports.rankBanksByQuery === 'function' ? ccExports.rankBanksByQuery('') : [];
  const rankedNone = typeof ccExports.rankBanksByQuery === 'function' ? ccExports.rankBanksByQuery('zzznobank999') : [];
  collector.recordCheck({
    tier: 2,
    checkName: 'Searchable bank ranking helper (rankBanksByQuery) prioritizes code/short/diacritic-free name/BIN hits cleanly',
    passed: rankedBidv.length > 0 && rankedBidv[0].bankCode === 'BIDV' &&
            rankedDauTu.length > 0 && rankedDauTu.some(b => b.bankCode === 'BIDV') &&
            rankedBin.length === 1 && rankedBin[0].bankCode === 'BIDV' &&
            rankedEmpty.length === 35 &&
            rankedNone.length === 0,
    expected: 'bidv->[0]=BIDV, dau tu matches BIDV, 970418->[BIDV], empty->35, zzznobank999->0',
    actual: `bidv0=${rankedBidv[0]?.bankCode}, dauTu=${rankedDauTu.length}, bin0=${rankedBin[0]?.bankCode}, empty=${rankedEmpty.length}, none=${rankedNone.length}`
  });

  // ===========================================================================
  // TIER 3: INTERACTIVE RUNTIME SIMULATION (CASCADING API, CCCD, DYNAMIC FORM, SUBMIT)
  // ===========================================================================
  console.log('► Tier 3: Interactive Runtime Simulation (Cascading Fetch, CCCD Hydration, Validation & Origination POST)...');

  const apiCalls = [];
  let postedOriginationBody = null;
  let postedCustomerBody = null;

  const mockFetch = async (url, options = {}) => {
    apiCalls.push({ url, method: options.method || 'GET' });

    if (url === '/api/v1/branches/provinces') {
      return {
        status: 200,
        ok: true,
        json: async () => ({
          success: true,
          data: [
            { code: '79', name: 'Hồ Chí Minh', fullName: 'Thành phố Hồ Chí Minh' },
            { code: '01', name: 'Hà Nội', fullName: 'Thành phố Hà Nội' }
          ]
        })
      };
    }
    if (url === '/api/v1/branches/provinces/79/districts') {
      return {
        status: 200,
        ok: true,
        json: async () => ({
          success: true,
          data: [
            { code: '760', name: '1', fullName: 'Quận 1', provinceCode: '79' },
            { code: '770', name: '3', fullName: 'Quận 3', provinceCode: '79' }
          ]
        })
      };
    }
    if (url === '/api/v1/branches/districts/760/wards') {
      return {
        status: 200,
        ok: true,
        json: async () => ({
          success: true,
          data: [
            { code: '26734', name: 'Tân Định', fullName: 'Phường Tân Định', districtCode: '760' },
            { code: '26740', name: 'Bến Nghé', fullName: 'Phường Bến Nghé', districtCode: '760' }
          ]
        })
      };
    }
    if (url === '/api/v1/branches/provinces/01/districts') {
      return {
        status: 200,
        ok: true,
        json: async () => ({
          success: true,
          data: [
            { code: '005', name: 'Cầu Giấy', fullName: 'Quận Cầu Giấy', provinceCode: '01' }
          ]
        })
      };
    }
    if (url === '/api/v1/branches/districts/005/wards') {
      return {
        status: 200,
        ok: true,
        json: async () => ({
          success: true,
          data: [
            { code: '00167', name: 'Dịch Vọng Hậu', fullName: 'Phường Dịch Vọng Hậu', districtCode: '005' }
          ]
        })
      };
    }
    if (url === '/api/v1/customers/identity/079095001234') {
      return {
        status: 200,
        ok: true,
        json: async () => ({
          success: true,
          data: {
            id: 12,
            customerCode: 'KH-2611-001234',
            fullName: 'Nguyễn Minh Quân',
            identityNumber: '079095001234',
            phoneNumber: '0909123456',
            provinceCode: '79',
            districtCode: '760',
            wardCode: '26740',
            permanentAddress: '15 Lê Lợi, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh',
            address: '15 Lê Lợi, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh',
            occupationGroup: 'OFFICE_WORKER',
            monthlyIncome: 25000000,
            verificationDocUrls: '/uploads/customers/079095001234/verification-docs',
            verificationDocUrlList: [
              '/uploads/customers/079095001234/verification-docs/01_hop_dong_ld.jpg',
              '/uploads/customers/079095001234/verification-docs/02_sao_ke_luong.png',
              '/uploads/customers/079095001234/verification-docs/03_vssid_bhxh.jpg'
            ],
            idCardFrontUrl: '/uploads/customers/079095001234/kyc/cccd_front.jpg',
            idCardBackUrl: '/uploads/customers/079095001234/kyc/cccd_back.jpg'
          }
        })
      };
    }
    if (url.startsWith('/api/v1/customers/identity/')) {
      return { status: 404, ok: false, json: async () => ({ success: false, code: 404 }) };
    }
    if (url === '/api/v1/files/customer-verification-docs' && options.method === 'POST') {
      const fd = options.body;
      const idNum = (fd && typeof fd.get === 'function' && fd.get('identityNumber')) || '079204888999';
      const keepUrls = (fd && typeof fd.getAll === 'function' && fd.getAll('keepUrls')) || [];
      const files = (fd && typeof fd.getAll === 'function' && fd.getAll('files')) || [];
      const folderUrl = `/uploads/customers/${idNum}/verification-docs`;
      const uploadedUrls = files.map((f, idx) => `${folderUrl}/${String(keepUrls.length + idx + 1).padStart(2, '0')}_${(f && f.name) || 'doc.jpg'}`);
      const imageUrls = [...keepUrls, ...uploadedUrls];
      return {
        status: 200,
        ok: true,
        json: async () => ({
          success: true,
          data: {
            folderUrl,
            imageUrls,
            totalFiles: imageUrls.length
          }
        })
      };
    }
    if (url === '/api/v1/files/upload' && options.method === 'POST') {
      const fd = options.body;
      const cat = String((fd && typeof fd.get === 'function' && fd.get('category')) || 'COLLATERAL').toUpperCase();
      const ownerKey = (fd && typeof fd.get === 'function' && fd.get('ownerKey')) || 'owner';
      const files = (fd && typeof fd.getAll === 'function' && fd.getAll('files')) || [];
      const prefix = cat === 'COLLATERAL'
        ? `/uploads/collaterals/${ownerKey}`
        : `/uploads/customers/${ownerKey}/kyc`;
      const imageUrls = files.map((f, idx) => `${prefix}/${cat.toLowerCase()}_${idx + 1}_${(f && f.name) || 'img.jpg'}`);
      return {
        status: 200,
        ok: true,
        json: async () => ({
          success: true,
          data: {
            category: cat,
            primaryUrl: imageUrls[0] || '',
            imageUrls,
            totalFiles: imageUrls.length
          }
        })
      };
    }
    if (url === '/api/v1/customers' && options.method === 'POST') {
      postedCustomerBody = JSON.parse(options.body);
      const savedFolderStr = Array.isArray(postedCustomerBody.verificationDocUrls)
        ? postedCustomerBody.verificationDocUrls.join(',')
        : (postedCustomerBody.verificationDocUrls || '');
      return {
        status: 200,
        ok: true,
        json: async () => ({
          success: true,
          data: Object.assign({ id: 88, customerCode: 'KH-2611-888999' }, postedCustomerBody, {
            verificationDocUrls: savedFolderStr,
            verificationDocUrlList: [
              `${savedFolderStr}/01_hop_dong_ld.jpg`,
              `${savedFolderStr}/02_sao_ke_luong.png`
            ]
          })
        })
      };
    }
    if (url.startsWith('/api/v1/customers/') && options.method === 'PUT') {
      const putBody = JSON.parse(options.body);
      apiCalls.lastPutCustomerBody = putBody;
      return {
        status: 200,
        ok: true,
        json: async () => ({
          success: true,
          data: Object.assign({ id: 12, customerCode: 'KH-2611-001234' }, putBody)
        })
      };
    }
    if (url === '/api/v1/contracts/originate' && options.method === 'POST') {
      postedOriginationBody = JSON.parse(options.body);
      return {
        status: 200,
        ok: true,
        json: async () => ({
          success: true,
          data: { id: 501, contractCode: 'HD-2611-B01-000501' }
        })
      };
    }
    return { status: 200, ok: true, json: async () => ({ success: true, data: {} }) };
  };

  const sim = createInteractiveDomSandbox(dom.raw, mockFetch);
  vm.runInNewContext(jsRaw, sim.sandbox);
  await new Promise(resolve => setTimeout(resolve, 30));

  const provEl = sim.elementsById.get('ccProvinceCode');
  const distEl = sim.elementsById.get('ccDistrictCode');
  const wardEl = sim.elementsById.get('ccWardCode');
  const streetEl = sim.elementsById.get('ccAddressStreet');
  const fullAddrEl = sim.elementsById.get('ccPermanentAddressFull');
  const sumAddrShortEl = sim.elementsById.get('sumCustomerAddressShort');

  // Simulate user changing Province -> Hà Nội ('01'), District -> Quận Cầu Giấy ('005'), Ward -> Phường Dịch Vọng Hậu ('00167')
  provEl.value = '01';
  await provEl.dispatchEvent({ type: 'change' });
  distEl.value = '005';
  await distEl.dispatchEvent({ type: 'change' });
  wardEl.value = '00167';
  await wardEl.dispatchEvent({ type: 'change' });
  streetEl.value = '120 Xuân Thủy';
  await streetEl.dispatchEvent({ type: 'input' });

  collector.recordCheck({
    tier: 3,
    checkName: 'Cascading Province -> District -> Ward change fetches /districts & /wards, renders fullName, and auto-composes #ccPermanentAddressFull + #sumCustomerAddressShort',
    passed: fullAddrEl.value === '120 Xuân Thủy, Phường Dịch Vọng Hậu, Quận Cầu Giấy, Thành phố Hà Nội' &&
            sumAddrShortEl.textContent === 'Phường Dịch Vọng Hậu, Quận Cầu Giấy, Thành phố Hà Nội' &&
            apiCalls.some(c => c.url === '/api/v1/branches/provinces/01/districts') &&
            apiCalls.some(c => c.url === '/api/v1/branches/districts/005/wards'),
    expected: '"120 Xuân Thủy, Phường Dịch Vọng Hậu, Quận Cầu Giấy, Thành phố Hà Nội"',
    actual: fullAddrEl.value
  });

  // Simulate inline New Customer creation via CCCD 079204888999
  const cccdEl = sim.elementsById.get('ccCustomerIdNumber');
  const lookupBtn = sim.elementsById.get('btnLookupCccd');
  const notFoundBanner = sim.elementsById.get('ccCustomerNotFoundBanner');
  const bannerCreateBtn = sim.elementsById.get('btnBannerCreateCustomer');
  const confirmCreateBtn = sim.elementsById.get('btnConfirmCreateCustomer');

  cccdEl.value = '079204888999';
  await lookupBtn.dispatchEvent({ type: 'click' });
  const showedNotFound = !notFoundBanner.classList.contains('d-none');

  await bannerCreateBtn.dispatchEvent({ type: 'click' });
  const creatorInitialProvinceEmpty = provEl.value === '';
  sim.elementsById.get('ccCustomerName').value = 'Lê Hoàng Nam';
  sim.elementsById.get('ccCustomerPhone').value = '0938112233';
  provEl.value = '01';
  await provEl.dispatchEvent({ type: 'change' });
  distEl.value = '005';
  await distEl.dispatchEvent({ type: 'change' });
  wardEl.value = '00167';
  await wardEl.dispatchEvent({ type: 'change' });
  streetEl.value = '25 Trần Thái Tông';
  await streetEl.dispatchEvent({ type: 'input' });

  // Verify saving WITHOUT selecting verification images marks #ccVerificationDocBox invalid first
  await confirmCreateBtn.dispatchEvent({ type: 'click' });
  const verDocBox = sim.elementsById.get('ccVerificationDocBox');
  const blockedWithoutVerificationImages = verDocBox.classList.contains('is-invalid') && postedCustomerBody === null;

  // Simulate selecting 2 verification images from computer folder + 1 CCCD front image
  const verFileInput = sim.elementsById.get('ccDocumentFileInput');
  const verGallery = sim.elementsById.get('ccVerificationDocGallery');
  verFileInput.files = [
    { name: 'hop_dong_ld.jpg', type: 'image/jpeg', size: 245000 },
    { name: 'sao_ke_luong.png', type: 'image/png', size: 312000 }
  ];
  await verFileInput.dispatchEvent({ type: 'change' });

  const stagedTwoNewDocsInGallery = verGallery.innerHTML.includes('hop_dong_ld.jpg') &&
    verGallery.innerHTML.includes('sao_ke_luong.png') &&
    verGallery.innerHTML.includes('Mới chọn') &&
    !verDocBox.classList.contains('is-invalid');

  // Click preview button on newly selected local file BEFORE saving -> opens #modalImagePreviewLightbox directly
  const preSavePreviewBtn = verGallery.children.find(b => b.getAttribute('data-media-action') === 'preview');
  if (preSavePreviewBtn) {
    await verGallery.dispatchEvent({ type: 'click', target: preSavePreviewBtn });
  }
  const preSaveImgSrc = sim.elementsById.get('lightboxMainImage').getAttribute('src') || '';
  const previewedStagedBeforeSave = sim.getShownModals().includes('modalImagePreviewLightbox') &&
    (preSaveImgSrc.startsWith('blob:') || preSaveImgSrc.startsWith('data:image/')) &&
    sim.elementsById.get('lightboxCounterBadge').textContent === 'Ảnh 1 / 2';

  const cccdFrontFileInput = sim.elementsById.get('ccIdCardFrontFileInput');
  cccdFrontFileInput.files = [{ name: 'cccd_front.jpg', type: 'image/jpeg', size: 180000 }];
  await cccdFrontFileInput.dispatchEvent({ type: 'change' });

  await confirmCreateBtn.dispatchEvent({ type: 'click' });

  collector.recordCheck({
    tier: 3,
    checkName: 'CCCD NOT_FOUND flow resets location to empty (no branch inheritance) and POSTs /api/v1/customers WITHOUT branchId (with provinceCode, districtCode, wardCode, addressDetail)',
    passed: showedNotFound &&
            creatorInitialProvinceEmpty &&
            postedCustomerBody !== null &&
            !('branchId' in postedCustomerBody) &&
            postedCustomerBody.identityNumber === '079204888999' &&
            postedCustomerBody.provinceCode === '01' &&
            postedCustomerBody.districtCode === '005' &&
            postedCustomerBody.wardCode === '00167' &&
            postedCustomerBody.addressDetail === '25 Trần Thái Tông' &&
            Array.isArray(postedCustomerBody.verificationDocUrls) &&
            postedCustomerBody.permanentAddress === '25 Trần Thái Tông, Phường Dịch Vọng Hậu, Quận Cầu Giấy, Thành phố Hà Nội',
    expected: 'POST /api/v1/customers WITHOUT branchId, provinceCode="01", districtCode="005", wardCode="00167", addressDetail="25 Trần Thái Tông"',
    actual: postedCustomerBody
      ? `hasBranchId=${'branchId' in postedCustomerBody}, prov=${postedCustomerBody.provinceCode}, dist=${postedCustomerBody.districtCode}, ward=${postedCustomerBody.wardCode} | ${postedCustomerBody.permanentAddress}`
      : 'null'
  });

  collector.recordCheck({
    tier: 3,
    checkName: 'New Customer multi-file folder picker stages 2 images, previews in Lightbox before save, uploads via FormData, and stores ONLY folder link "/uploads/customers/079204888999/verification-docs"',
    passed: blockedWithoutVerificationImages &&
            stagedTwoNewDocsInGallery &&
            previewedStagedBeforeSave &&
            apiCalls.some(c => c.url === '/api/v1/files/customer-verification-docs' && c.method === 'POST') &&
            postedCustomerBody !== null &&
            postedCustomerBody.verificationDocUrls.length === 1 &&
            postedCustomerBody.verificationDocUrls[0] === '/uploads/customers/079204888999/verification-docs' &&
            postedCustomerBody.idCardFrontUrl === '/uploads/customers/079204888999/kyc/cccd_front_1_cccd_front.jpg',
    expected: 'Blocked when empty, staged 2 local files, pre-save Lightbox preview ("1 / 2"), and verificationDocUrls=["/uploads/customers/079204888999/verification-docs"]',
    actual: postedCustomerBody
      ? `blockedEmpty=${blockedWithoutVerificationImages}, staged2=${stagedTwoNewDocsInGallery}, preSaveLightbox=${previewedStagedBeforeSave}, folder=${JSON.stringify(postedCustomerBody.verificationDocUrls)}`
      : 'null'
  });

  // Simulate switching collateral category to PHONE -> verify dynamic fields, untouched seed cleanup & zero false-alarm modal
  const catSelect = sim.elementsById.get('ccCollateralCategory');
  const colNameEl = sim.elementsById.get('ccCollateralName');
  catSelect.value = 'PHONE';
  await catSelect.dispatchEvent({ type: 'change' });

  const vehicleGroup = sim.elementsById.get('ccVehicleFieldsGroup');
  const techGroup = sim.elementsById.get('ccTechFieldsGroup');
  const plateInput = sim.elementsById.get('ccLicensePlate');
  const imeiInput = sim.elementsById.get('ccSerialOrImei');
  const noFalseAlarmModalOnSeed = !sim.getShownModals().includes('modalCollateralCategorySwitch');
  const cleanedUntouchedSeedName = colNameEl && colNameEl.value === '';
  const phonePlaceholderApplied = Boolean(colNameEl && (colNameEl.getAttribute('placeholder') || '').includes('iPhone'));

  if (colNameEl) {
    colNameEl.value = 'iPhone 16 Pro Max 256GB Titan Tự Nhiên';
    await colNameEl.dispatchEvent({ type: 'input' });
  }

  collector.recordCheck({
    tier: 3,
    checkName: 'Switching collateral category to PHONE reveals #ccTechFieldsGroup, hides #ccVehicleFieldsGroup, cleans untouched seed values without false-alarm modal, and strips required from hidden vehicle inputs',
    passed: vehicleGroup.classList.contains('d-none') &&
            !techGroup.classList.contains('d-none') &&
            !plateInput.hasAttribute('required') &&
            imeiInput.hasAttribute('required') &&
            noFalseAlarmModalOnSeed &&
            cleanedUntouchedSeedName &&
            phonePlaceholderApplied,
    expected: 'Vehicle hidden & unrequired; Tech visible & IMEI required; untouched seed cleaned without modal',
    actual: `vehicleHidden=${vehicleGroup.classList.contains('d-none')}, imeiRequired=${imeiInput.hasAttribute('required')}, noFalseAlarm=${noFalseAlarmModalOnSeed}, seedCleaned=${cleanedUntouchedSeedName}`
  });

  // Simulate scrolling deep down (scrollY = 480) BEFORE failed submit:
  // 1) #ccStickyWorkspaceHud becomes .is-stuck & #btnBackToTop becomes .is-visible
  // 2) Submitting invalid form reveals #ccFormErrorSummary in place WITHOUT yanking scrollY back to 0
  sim.windowMock.scrollY = 480;
  sim.windowMock.pageYOffset = 480;
  sim.windowMock.dispatchWindowEvent('scroll');

  const stickyHudEl = sim.elementsById.get('ccStickyWorkspaceHud');
  const backToTopBtn = sim.elementsById.get('btnBackToTop');
  const stuckWhenScrolled = stickyHudEl.classList.contains('is-stuck') &&
    backToTopBtn.classList.contains('is-visible') &&
    backToTopBtn.getAttribute('tabindex') === '0';

  // Simulate failed submit (missing IMEI & unchecked compliance checkboxes) -> WCAG Error Summary + inline aria-describedby + step error badges
  const formEl = sim.elementsById.get('formCreateContract');
  const errSummary = sim.elementsById.get('ccFormErrorSummary');
  const errList = sim.elementsById.get('ccFormErrorSummaryList');
  const errBadge = sim.elementsById.get('ccErrorSummaryCountBadge');
  const toggleErrBtn = sim.elementsById.get('btnToggleErrorSummary');
  const stepColPill = sim.elementsById.get('ccStepErrCollateral');
  const stepCompPill = sim.elementsById.get('ccStepErrCompliance');

  await formEl.dispatchEvent({ type: 'submit' });
  const preservedScrollOnInvalidSubmit = sim.windowMock.scrollY === 480;
  const initialErrCountText = errBadge.textContent;
  const hasInlineDescribedBy = (imeiInput.getAttribute('aria-describedby') || '').includes('ccSerialOrImei-error') &&
    sim.elementsById.has('ccSerialOrImei-error');
  const hasStepErrorBadges = !stepColPill.classList.contains('d-none') &&
    stepColPill.textContent === '1 lỗi' &&
    !stepCompPill.classList.contains('d-none') &&
    stepCompPill.textContent === '3 lỗi';

  // Test collapse & expand toggle on #ccFormErrorSummary
  await toggleErrBtn.dispatchEvent({ type: 'click' });
  const collapsedCorrectly = errSummary.classList.contains('is-collapsed') && toggleErrBtn.getAttribute('aria-expanded') === 'false';
  await toggleErrBtn.dispatchEvent({ type: 'click' });
  const expandedCorrectly = !errSummary.classList.contains('is-collapsed') && toggleErrBtn.getAttribute('aria-expanded') === 'true';

  collector.recordCheck({
    tier: 3,
    checkName: 'Submitting invalid form while scrolled down reveals sticky #ccFormErrorSummary, inline aria-describedby field errors, and per-step error pills',
    passed: stuckWhenScrolled &&
            preservedScrollOnInvalidSubmit &&
            !errSummary.classList.contains('d-none') &&
            errSummary.focused === true &&
            initialErrCountText === 'Còn 4 mục cần bổ sung' &&
            hasInlineDescribedBy &&
            hasStepErrorBadges &&
            collapsedCorrectly &&
            expandedCorrectly &&
            errList.innerHTML.includes('href="#ccSerialOrImei"') &&
            errList.innerHTML.includes('href="#chkVerifyOriginalCccd"'),
    expected: 'Sticky HUD stuck, scrollY=480 preserved, "Còn 4 mục cần bổ sung", aria-describedby="ccSerialOrImei-error", step badges ("1 lỗi", "3 lỗi"), collapse/expand working',
    actual: `stuck=${stuckWhenScrolled}, scrollY=${sim.windowMock.scrollY}, badge="${initialErrCountText}", inlineErr=${hasInlineDescribedBy}, stepBadges=${hasStepErrorBadges}`
  });

  // Complete remaining fields, verify live reactive error removal & auto-hide, test #btnBackToTop, save draft, preview print, and submit origination!
  imeiInput.value = '356998012345678';
  await imeiInput.dispatchEvent({ type: 'input' });
  const liveRemovedImeiError = !errList.innerHTML.includes('href="#ccSerialOrImei"') &&
    errBadge.textContent === 'Còn 3 mục cần bổ sung' &&
    !imeiInput.hasAttribute('aria-describedby') &&
    !sim.elementsById.has('ccSerialOrImei-error') &&
    stepColPill.classList.contains('d-none');

  sim.elementsById.get('ccCollateralName').value = 'iPhone 16 Pro Max 256GB Titan Tự Nhiên';
  sim.elementsById.get('ccAppraisedValue').value = '30000000';
  await sim.elementsById.get('ccAppraisedValue').dispatchEvent({ type: 'input' });
  sim.elementsById.get('ccPrincipalAmount').value = '20000000';
  await sim.elementsById.get('ccPrincipalAmount').dispatchEvent({ type: 'input' });

  const interestTypeEl = sim.elementsById.get('ccInterestType');
  interestTypeEl.value = 'MONTHLY_PERCENT';
  await interestTypeEl.dispatchEvent({ type: 'change' });

  // Draft save + restore check
  await sim.elementsById.get('btnSaveDraft').dispatchEvent({ type: 'click' });
  const savedDraftRaw = sim.mockLocalStorage.getItem('pm_contract_draft_v1');

  // Print preview check
  await sim.elementsById.get('btnPreviewContractPrint').dispatchEvent({ type: 'click' });
  await sim.elementsById.get('btnPrintDraftNow').dispatchEvent({ type: 'click' });
  const printBodyHtml = sim.elementsById.get('printContractBody').innerHTML;

  // Check compliance boxes -> verify #ccFormErrorSummary & step error badges auto-hide BEFORE clicking submit, then test #btnBackToTop!
  sim.elementsById.get('chkVerifyOriginalCccd').checked = true;
  await sim.elementsById.get('chkVerifyOriginalCccd').dispatchEvent({ type: 'change' });
  sim.elementsById.get('chkVerifyAssetSeal').checked = true;
  await sim.elementsById.get('chkVerifyAssetSeal').dispatchEvent({ type: 'change' });
  sim.elementsById.get('chkCustomerAgreedTerms').checked = true;
  await sim.elementsById.get('chkCustomerAgreedTerms').dispatchEvent({ type: 'change' });

  const autoHiddenWhenAllFixed = errSummary.classList.contains('d-none') && stepCompPill.classList.contains('d-none');

  await backToTopBtn.dispatchEvent({ type: 'click' });
  sim.windowMock.dispatchWindowEvent('scroll');
  const scrolledBackToTop = sim.windowMock.scrollY === 0 &&
    !backToTopBtn.classList.contains('is-visible') &&
    backToTopBtn.getAttribute('tabindex') === '-1' &&
    sim.elementsById.get('originationPageTitle').focused === true;

  collector.recordCheck({
    tier: 3,
    checkName: 'Live error summary auto-removes fixed items + aria-describedby + step badges ("Còn 3 mục cần bổ sung" -> auto-hidden at 0) and #btnBackToTop returns to top',
    passed: liveRemovedImeiError && autoHiddenWhenAllFixed && scrolledBackToTop,
    expected: 'IMEI inline error + step badge removed live on input, summary auto-hidden at 0, #btnBackToTop resets scrollY=0 & tabindex="-1"',
    actual: `liveRemovedImei=${liveRemovedImeiError}, autoHidden=${autoHiddenWhenAllFixed}, scrolledBackToTop=${scrolledBackToTop}`
  });

  // Simulate Smart Auto-Select on #ccIdentityIssueDate -> #ccIdentityIssuePlace + Free-Text "OTHER" option
  const simIssueDateEl = sim.elementsById.get('ccIdentityIssueDate');
  const simIssuePlaceEl = sim.elementsById.get('ccIdentityIssuePlace');
  const simIssuePlaceCustomWrap = sim.elementsById.get('ccIdentityIssuePlaceCustomWrap');
  const simIssuePlaceCustomInput = sim.elementsById.get('ccIdentityIssuePlaceCustom');

  simIssueDateEl.value = '2017-06-15';
  await simIssueDateEl.dispatchEvent({ type: 'change' });
  const autoPlace2017 = simIssuePlaceEl ? simIssuePlaceEl.value : '';

  simIssueDateEl.value = '2021-05-20';
  await simIssueDateEl.dispatchEvent({ type: 'change' });
  const autoPlace2021 = simIssuePlaceEl ? simIssuePlaceEl.value : '';

  simIssueDateEl.value = '2024-07-01';
  await simIssueDateEl.dispatchEvent({ type: 'change' });
  const autoPlace2024 = simIssuePlaceEl ? simIssuePlaceEl.value : '';
  const customHiddenOnPreset = Boolean(simIssuePlaceCustomWrap && simIssuePlaceCustomWrap.classList.contains('d-none'));

  if (simIssuePlaceEl) {
    simIssuePlaceEl.value = 'OTHER';
    await simIssuePlaceEl.dispatchEvent({ type: 'change' });
  }
  const customVisibleOnOther = Boolean(simIssuePlaceCustomWrap && !simIssuePlaceCustomWrap.classList.contains('d-none'));
  if (simIssuePlaceCustomInput) {
    simIssuePlaceCustomInput.value = 'Công an TP. Hà Nội';
    await simIssuePlaceCustomInput.dispatchEvent({ type: 'input' });
  }

  collector.recordCheck({
    tier: 3,
    checkName: 'Changing #ccIdentityIssueDate auto-selects #ccIdentityIssuePlace across all 3 statutory periods and selecting OTHER reveals free-text #ccIdentityIssuePlaceCustom',
    passed: autoPlace2017 === 'Cục Cảnh sát ĐKQL cư trú và DLQG về dân cư' &&
            autoPlace2021 === 'Cục Cảnh sát quản lý hành chính về trật tự xã hội' &&
            autoPlace2024 === 'Bộ Công an' &&
            customHiddenOnPreset &&
            customVisibleOnOther,
    expected: '2017->ĐKQL cư trú, 2021->QLHC về TTXH, 2024-07-01->Bộ Công an, OTHER reveals #ccIdentityIssuePlaceCustomWrap',
    actual: `2017="${autoPlace2017}", 2021="${autoPlace2021}", 2024="${autoPlace2024}", customHidden=${customHiddenOnPreset}, customVisible=${customVisibleOnOther}`
  });

  // Also select a local collateral seal photo before submitting origination
  const colFileInput = sim.elementsById.get('ccCollateralFileInput');
  colFileInput.files = [{ name: 'iphone16_seal.jpg', type: 'image/jpeg', size: 220000 }];
  await colFileInput.dispatchEvent({ type: 'change' });

  await formEl.dispatchEvent({ type: 'submit' });

  collector.recordCheck({
    tier: 3,
    checkName: 'LocalStorage draft save ("pm_contract_draft_v1") and A4 legal print preview ("HỢP ĐỒNG CẦM CỐ TÀI SẢN" + window.print) work end-to-end',
    passed: Boolean(savedDraftRaw && savedDraftRaw.includes('356998012345678')) &&
            printBodyHtml.includes('HỢP ĐỒNG CẦM CỐ TÀI SẢN') &&
            printBodyHtml.includes('25 Trần Thái Tông, Phường Dịch Vọng Hậu, Quận Cầu Giấy, Thành phố Hà Nội') &&
            sim.getPrintCount() === 1,
    expected: 'Draft persisted in localStorage; Print sheet rendered with permanent address & window.print() called',
    actual: `draftSaved=${Boolean(savedDraftRaw)}, printCount=${sim.getPrintCount()}`
  });

  collector.recordCheck({
    tier: 3,
    checkName: 'Valid #formCreateContract submit POSTs /api/v1/contracts/originate WITH branchId alongside customer provinceCode, districtCode, wardCode, addressDetail, folder verificationDocUrls & collateralImageUrls',
    passed: postedOriginationBody !== null &&
            typeof postedOriginationBody.branchId === 'number' &&
            postedOriginationBody.branchId > 0 &&
            postedOriginationBody.interestType === 'PERCENT_MONTHLY' &&
            postedOriginationBody.collateralCategory === 'PHONE' &&
            postedOriginationBody.serialOrFrameNumber === '356998012345678' &&
            postedOriginationBody.techImeiOrSerial === '356998012345678' &&
            postedOriginationBody.techPasscodeStatus === 'ICLOUD_OFF_UNLOCKED' &&
            postedOriginationBody.vehicleOdometerKm === null &&
            postedOriginationBody.goldPurity === null &&
            postedOriginationBody.provinceCode === '01' &&
            postedOriginationBody.districtCode === '005' &&
            postedOriginationBody.wardCode === '00167' &&
            postedOriginationBody.addressDetail === '25 Trần Thái Tông' &&
            postedOriginationBody.identityIssuePlace === 'Công an TP. Hà Nội' &&
            postedOriginationBody.verificationDocUrls === '/uploads/customers/079204888999/verification-docs' &&
            typeof postedOriginationBody.collateralImageUrls === 'string' &&
            postedOriginationBody.collateralImageUrls.includes('iphone16_seal.jpg') &&
            postedOriginationBody.permanentAddress === '25 Trần Thái Tông, Phường Dịch Vọng Hậu, Quận Cầu Giấy, Thành phố Hà Nội' &&
            postedOriginationBody.address === '25 Trần Thái Tông, Phường Dịch Vọng Hậu, Quận Cầu Giấy, Thành phố Hà Nội' &&
            sim.getShownModals().includes('modalOriginationSuccess') &&
            sim.elementsById.get('succContractCode').textContent === 'HD-2611-B01-000501',
    expected: 'POST /api/v1/contracts/originate WITH branchId + verificationDocUrls="/uploads/customers/079204888999/verification-docs" + collateralImageUrls',
    actual: postedOriginationBody
      ? `branchId=${postedOriginationBody.branchId}, verFolder=${postedOriginationBody.verificationDocUrls}, colImgs=${postedOriginationBody.collateralImageUrls}`
      : 'null'
  });

  // Verify GOLD_JEWELRY mandatory weight/purity validation + normalized payload fields & MOTORBIKE negative ODO gate + print sheet ODO
  const colCategorySelect = sim.elementsById.get('ccCollateralCategory');
  const confirmCatSwitchBtn = sim.elementsById.get('btnConfirmCategorySwitch');
  const goldPurityEl = sim.elementsById.get('ccGoldPurity');
  const goldWeightEl = sim.elementsById.get('ccGoldWeightChi');
  const gemCertEl = sim.elementsById.get('ccGemCertificateCode');
  const odometerEl = sim.elementsById.get('ccVehicleOdometer');
  const regNumEl = sim.elementsById.get('ccVehicleRegNumber');
  const plateInputEl = sim.elementsById.get('ccLicensePlate');
  const frameInputEl = sim.elementsById.get('ccFrameNumber');

  colCategorySelect.value = 'GOLD_JEWELRY';
  await colCategorySelect.dispatchEvent({ type: 'change' });
  if (sim.elementsById.get('modalCollateralCategorySwitch')?.classList.contains('show')) {
    await confirmCatSwitchBtn.dispatchEvent({ type: 'click' });
  }
  goldPurityEl.value = '9999';
  goldWeightEl.value = '0';
  postedOriginationBody = null;
  await formEl.dispatchEvent({ type: 'submit' });
  const goldZeroBlocked = postedOriginationBody === null && goldWeightEl.classList.contains('is-invalid');

  goldWeightEl.value = '5.250';
  await goldWeightEl.dispatchEvent({ type: 'input' });
  gemCertEl.value = 'SJC-2026-999123';
  await formEl.dispatchEvent({ type: 'submit' });
  const goldPayloadValid = postedOriginationBody !== null &&
    postedOriginationBody.collateralCategory === 'GOLD_JEWELRY' &&
    postedOriginationBody.goldPurity === '9999' &&
    postedOriginationBody.goldWeightChi === 5.25 &&
    postedOriginationBody.goldCertificateCode === 'SJC-2026-999123' &&
    postedOriginationBody.serialOrFrameNumber === 'SJC-2026-999123' &&
    postedOriginationBody.vehicleOdometerKm === null &&
    postedOriginationBody.techImeiOrSerial === null;

  colCategorySelect.value = 'MOTORBIKE';
  await colCategorySelect.dispatchEvent({ type: 'change' });
  if (sim.elementsById.get('modalCollateralCategorySwitch')?.classList.contains('show')) {
    await confirmCatSwitchBtn.dispatchEvent({ type: 'click' });
  }
  plateInputEl.value = '59-P1 988.22';
  frameInputEl.value = 'RLHJF8209PZ104821';
  regNumEl.value = 'DKX-79-0084512';
  odometerEl.value = '-10';
  postedOriginationBody = null;
  await formEl.dispatchEvent({ type: 'submit' });
  const negativeOdoBlocked = postedOriginationBody === null && odometerEl.classList.contains('is-invalid');

  odometerEl.value = '8450';
  await odometerEl.dispatchEvent({ type: 'input' });
  await sim.elementsById.get('btnPreviewContractPrint').dispatchEvent({ type: 'click' });
  const motoPrintHtml = sim.elementsById.get('printContractBody').innerHTML;
  await formEl.dispatchEvent({ type: 'submit' });
  const motoPayloadValid = postedOriginationBody !== null &&
    postedOriginationBody.collateralCategory === 'MOTORBIKE' &&
    postedOriginationBody.licensePlate === '59-P1 988.22' &&
    postedOriginationBody.vehicleFrameNumber === 'RLHJF8209PZ104821' &&
    postedOriginationBody.vehicleRegNumber === 'DKX-79-0084512' &&
    postedOriginationBody.vehicleOdometerKm === 8450 &&
    postedOriginationBody.goldPurity === null &&
    motoPrintHtml.includes('8,450 km');

  collector.recordCheck({
    tier: 3,
    checkName: 'Normalized collateral validation & payloads: GOLD_JEWELRY enforces weight > 0 + emits goldPurity/goldWeightChi; MOTORBIKE blocks negative ODO + emits ODO/RegNumber & renders ODO on print sheet',
    passed: goldZeroBlocked && goldPayloadValid && negativeOdoBlocked && motoPayloadValid,
    expected: 'goldZeroBlocked=true, goldPayloadValid=true, negativeOdoBlocked=true, motoPayloadValid=true (with ODO on print sheet)',
    actual: `goldBlocked=${goldZeroBlocked}, goldValid=${goldPayloadValid}, negOdoBlocked=${negativeOdoBlocked}, motoValid=${motoPayloadValid}`
  });

  // Simulate Section 3 PERCENT_WEEKLY (7-day cycle) + BANK_TRANSFER dropdown, STK sanitization, ASCII holder & mismatch warning, and clean notes
  const paymentCycleEl = sim.elementsById.get('ccPaymentCycle');
  const eqHintEl = sim.elementsById.get('ccInterestEquivalentHint');
  const disbMethodEl = sim.elementsById.get('ccDisbursementMethod');
  const bankGroupEl = sim.elementsById.get('ccBankAccountGroup');
  const bankCodeEl = sim.elementsById.get('ccBankCode');
  const bankAccEl = sim.elementsById.get('ccBankAccountNumber');
  const bankHolderEl = sim.elementsById.get('ccBankAccountHolder');
  const bankWarnEl = sim.elementsById.get('ccBankHolderMismatchWarning');
  const notesEl = sim.elementsById.get('ccContractNotes');

  interestTypeEl.value = 'PERCENT_WEEKLY';
  await interestTypeEl.dispatchEvent({ type: 'change' });
  paymentCycleEl.value = '7';
  await paymentCycleEl.dispatchEvent({ type: 'change' });
  const weeklyHintShown = Boolean(eqHintEl && eqHintEl.textContent.includes('%/tuần'));

  disbMethodEl.value = 'BANK_TRANSFER';
  await disbMethodEl.dispatchEvent({ type: 'change' });
  const bankGroupRevealed = Boolean(bankGroupEl && !bankGroupEl.classList.contains('d-none'));
  const holderAutoFilled = Boolean(bankHolderEl && bankHolderEl.value === ccExports.toBankAccountHolderAscii(sim.elementsById.get('ccCustomerName').value));

  bankCodeEl.value = '';
  bankAccEl.value = '123';
  await bankAccEl.dispatchEvent({ type: 'input' });
  postedOriginationBody = null;
  await formEl.dispatchEvent({ type: 'submit' });
  const invalidBankBlocked = postedOriginationBody === null &&
    bankCodeEl.classList.contains('is-invalid') &&
    bankAccEl.classList.contains('is-invalid');

  bankCodeEl.value = 'VCB';
  await bankCodeEl.dispatchEvent({ type: 'change' });
  bankAccEl.value = '0071 0099 8877';
  await bankAccEl.dispatchEvent({ type: 'input' });
  const stkSanitized = bankAccEl.value === '007100998877';

  // Verify Searchable Bank Combobox: type "bidv" -> auto-point BIDV -> Enter commits & focuses STK -> Clear resets
  const bankComboboxEl = sim.elementsById.get('ccBankCombobox');
  const bankSearchEl = sim.elementsById.get('ccBankSearchInput');
  const bankDropdownMenuEl = sim.elementsById.get('ccBankDropdownMenu');
  const bankListboxEl = sim.elementsById.get('ccBankDropdownListbox');
  const bankCodeBadgeEl = sim.elementsById.get('ccBankSelectedCodeBadge');
  const bankClearBtnEl = sim.elementsById.get('btnClearBankSelection');

  let bankSearchAutoPointed = false;
  let bankEnterCommittedBidv = false;
  let bankClearResetWorked = false;
  let bankArrowAndClickWorked = false;
  if (bankComboboxEl && bankSearchEl && bankDropdownMenuEl && bankListboxEl && bankClearBtnEl) {
    bankSearchEl.value = 'bidv';
    await bankSearchEl.dispatchEvent({ type: 'input' });
    const firstOpt = bankListboxEl.children[0];
    bankSearchAutoPointed = !bankDropdownMenuEl.classList.contains('d-none') &&
      Boolean(firstOpt && firstOpt.id === 'ccBankOption_BIDV' && firstOpt.classList.contains('is-active')) &&
      bankSearchEl.getAttribute('aria-activedescendant') === 'ccBankOption_BIDV';

    bankAccEl.focused = false;
    await bankSearchEl.dispatchEvent({ type: 'keydown', key: 'Enter' });
    bankEnterCommittedBidv = bankCodeEl.value === 'BIDV' &&
      bankDropdownMenuEl.classList.contains('d-none') &&
      Boolean(bankCodeBadgeEl && bankCodeBadgeEl.textContent === 'BIDV' && !bankCodeBadgeEl.classList.contains('d-none')) &&
      bankAccEl.focused === true;

    await bankClearBtnEl.dispatchEvent({ type: 'click' });
    bankClearResetWorked = bankCodeEl.value === '' &&
      bankSearchEl.value === '' &&
      !bankDropdownMenuEl.classList.contains('d-none');

    bankSearchEl.value = 'viet';
    await bankSearchEl.dispatchEvent({ type: 'input' });
    await bankSearchEl.dispatchEvent({ type: 'keydown', key: 'ArrowDown' });
    const secondActive = bankListboxEl.children[1];
    const arrowMoved = Boolean(secondActive && secondActive.classList.contains('is-active'));
    const firstOptionBtn = bankListboxEl.children.find(el => el.id === 'ccBankOption_VCB');
    if (firstOptionBtn) {
      await firstOptionBtn.dispatchEvent({ type: 'click' });
    }
    bankArrowAndClickWorked = arrowMoved && bankCodeEl.value === 'VCB' && bankDropdownMenuEl.classList.contains('d-none');
  }

  bankHolderEl.value = 'TRAN KHAC TEN';
  await bankHolderEl.dispatchEvent({ type: 'input' });
  const mismatchWarningShown = Boolean(bankWarnEl && !bankWarnEl.classList.contains('d-none'));

  if (notesEl) notesEl.value = 'Khách nhận CK nhanh';
  await formEl.dispatchEvent({ type: 'submit' });
  const weeklyBankPayloadValid = postedOriginationBody !== null &&
    postedOriginationBody.interestType === 'PERCENT_WEEKLY' &&
    postedOriginationBody.paymentCycleDays === 7 &&
    postedOriginationBody.disbursementMethod === 'BANK_TRANSFER' &&
    postedOriginationBody.disbursementBankCode === 'VCB' &&
    postedOriginationBody.disbursementBankName === 'Vietcombank' &&
    postedOriginationBody.disbursementAccountNumber === '007100998877' &&
    postedOriginationBody.disbursementAccountHolder === 'TRAN KHAC TEN' &&
    postedOriginationBody.notes === 'Khách nhận CK nhanh';

  // Reset disbursement mode & cycle cleanly for subsequent simulation steps
  disbMethodEl.value = 'CASH';
  await disbMethodEl.dispatchEvent({ type: 'change' });
  interestTypeEl.value = 'MONTHLY_PERCENT';
  await interestTypeEl.dispatchEvent({ type: 'change' });
  paymentCycleEl.value = '10';
  await paymentCycleEl.dispatchEvent({ type: 'change' });

  collector.recordCheck({
    tier: 3,
    checkName: 'Section 3 interactive flow: PERCENT_WEEKLY conversion, BANK_TRANSFER 35-bank searchable combobox (type bidv -> auto-point -> Enter/click/clear), STK sanitization, ASCII holder & mismatch warning, and structured payload',
    passed: weeklyHintShown && bankGroupRevealed && holderAutoFilled && invalidBankBlocked && stkSanitized &&
            bankSearchAutoPointed && bankEnterCommittedBidv && bankClearResetWorked && bankArrowAndClickWorked &&
            mismatchWarningShown && weeklyBankPayloadValid,
    expected: 'weeklyHint=true, bankRevealed=true, autoFilled=true, invalidBlocked=true, sanitized=true, autoPoint=true, enterBidv=true, clear=true, arrowClick=true, mismatchWarn=true, payload=true',
    actual: `hint=${weeklyHintShown}, revealed=${bankGroupRevealed}, autoFill=${holderAutoFilled}, blocked=${invalidBankBlocked}, sanitized=${stkSanitized}, autoPoint=${bankSearchAutoPointed}, enter=${bankEnterCommittedBidv}, clear=${bankClearResetWorked}, arrowClick=${bankArrowAndClickWorked}, warn=${mismatchWarningShown}, payload=${weeklyBankPayloadValid}`
  });

  // Simulate CCCD FOUND flow ("079095001234") -> auto-hydrates cascading TP.HCM / Quận 1 / Phường Bến Nghé ('26740'), strips suffixes from #ccAddressStreet, & locks fields + multi-image gallery
  cccdEl.value = '079095001234';
  await lookupBtn.dispatchEvent({ type: 'click' });
  const foundBanner = sim.elementsById.get('ccCustomerFoundBanner');

  collector.recordCheck({
    tier: 3,
    checkName: 'CCCD FOUND flow ("079095001234") hydrates cascading TP.HCM ("79") / Quận 1 ("760") / Phường Bến Nghé ("26740"), strips duplicate suffixes into #ccAddressStreet, and locks inputs',
    passed: !foundBanner.classList.contains('d-none') &&
            sim.elementsById.get('ccCustomerName').value === 'Nguyễn Minh Quân' &&
            sim.elementsById.get('ccCustomerName').hasAttribute('readonly') &&
            provEl.value === '79' &&
            distEl.value === '760' &&
            wardEl.value === '26740' &&
            streetEl.value === '15 Lê Lợi' &&
            fullAddrEl.value === '15 Lê Lợi, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh',
    expected: 'Hydrated "79"/"760"/"26740", streetEl="15 Lê Lợi", and fullAddr="15 Lê Lợi, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh"',
    actual: `${sim.elementsById.get('ccCustomerName').value} | street="${streetEl.value}" | ward=${wardEl.value} | ${fullAddrEl.value}`
  });

  // Verify Existing Customer ("079095001234") multi-image verification document list + direct in-system Lightbox preview (Zoom, Rotate, Next slide)
  const existingGalleryHtml = verGallery.innerHTML;
  const renderedAllThreeExistingDocs = existingGalleryHtml.includes('01_hop_dong_ld.jpg') &&
    existingGalleryHtml.includes('02_sao_ke_luong.png') &&
    existingGalleryHtml.includes('03_vssid_bhxh.jpg') &&
    existingGalleryHtml.includes('Đã lưu') &&
    verDocBox.classList.contains('is-locked');

  const existingPreviewBtn = verGallery.children.find(b => b.getAttribute('data-media-action') === 'preview');
  if (existingPreviewBtn) {
    await verGallery.dispatchEvent({ type: 'click', target: existingPreviewBtn });
  }
  const lbCounterInitial = sim.elementsById.get('lightboxCounterBadge').textContent;
  await sim.elementsById.get('btnLightboxZoomIn').dispatchEvent({ type: 'click' });
  await sim.elementsById.get('btnLightboxRotateRight').dispatchEvent({ type: 'click' });
  const lbZoomAfter = sim.elementsById.get('lightboxZoomIndicator').textContent;
  const lbTransformAfter = sim.elementsById.get('lightboxMainImage').style.transform || '';
  await sim.elementsById.get('btnLightboxNext').dispatchEvent({ type: 'click' });
  const lbCounterNext = sim.elementsById.get('lightboxCounterBadge').textContent;

  collector.recordCheck({
    tier: 3,
    checkName: 'Existing Customer ("khách hàng cũ") renders multi-image verification doc list (3 "Đã lưu" items) & clickable Lightbox viewer supports Zoom (125%), Rotate (+90°), and Next slide ("Ảnh 2 / 3")',
    passed: renderedAllThreeExistingDocs &&
            lbCounterInitial === 'Ảnh 1 / 3' &&
            lbZoomAfter === '125%' &&
            lbTransformAfter.includes('rotate(90deg)') &&
            lbCounterNext === 'Ảnh 2 / 3',
    expected: '3 existing verification docs rendered in #ccVerificationDocGallery (locked), Lightbox opens "Ảnh 1 / 3", Zoom="125%", Rotate="90deg", Next="Ảnh 2 / 3"',
    actual: `rendered3=${renderedAllThreeExistingDocs}, init="${lbCounterInitial}", zoom="${lbZoomAfter}", transform="${lbTransformAfter}", next="${lbCounterNext}"`
  });

  // Simulate Existing Customer ("Cập nhật TT") Option 1B Bottom Action Footer, Cancel/Revert, Bottom Save, and Auto-Save Guard on Origination
  const unlockEditBtn = sim.elementsById.get('btnUnlockCustomerEdit');
  const cancelEditTopBtn = sim.elementsById.get('btnCancelCustomerEditTop');
  const editFooterEl = sim.elementsById.get('ccEditCustomerFooter');
  const cancelEditBottomBtn = sim.elementsById.get('btnCancelEditCustomer');
  const confirmUpdateBottomBtn = sim.elementsById.get('btnConfirmUpdateCustomer');
  const phoneInputEl = sim.elementsById.get('ccCustomerPhone');
  const emailInputEl = sim.elementsById.get('ccCustomerEmail');
  const verDocFileInput = sim.elementsById.get('ccDocumentFileInput');

  // 1) Click "Cập nhật TT" -> unlocks fields & media, reveals #ccEditCustomerFooter at bottom of Section 1, shifts #ccCustomerFoundBanner to .is-editing
  await unlockEditBtn.dispatchEvent({ type: 'click' });
  const editUnlockedCorrectly = !editFooterEl.classList.contains('d-none') &&
    foundBanner.classList.contains('is-editing') &&
    !cancelEditTopBtn.classList.contains('d-none') &&
    !phoneInputEl.hasAttribute('readonly') &&
    !verDocBox.classList.contains('is-locked');

  // 2) Edit phone + stage a 4th verification photo, then click bottom "#btnCancelEditCustomer" ("Hủy thay đổi") -> reverts everything cleanly
  phoneInputEl.value = '0911000111';
  verDocFileInput.files = [{ name: '04_bang_luong_tam.jpg', type: 'image/jpeg', size: 180000 }];
  await verDocFileInput.dispatchEvent({ type: 'change' });
  await cancelEditBottomBtn.dispatchEvent({ type: 'click' });
  const cancelledAndReverted = editFooterEl.classList.contains('d-none') &&
    !foundBanner.classList.contains('is-editing') &&
    phoneInputEl.value === '0909123456' &&
    phoneInputEl.hasAttribute('readonly') &&
    verDocBox.classList.contains('is-locked') &&
    !verGallery.innerHTML.includes('04_bang_luong_tam.jpg') &&
    verGallery.innerHTML.includes('03_vssid_bhxh.jpg');

  // 3) Click "Cập nhật TT" again, update phone + add 4th verification photo, then click bottom "#btnConfirmUpdateCustomer" ("Lưu Cập Nhật & Khóa TT")
  await unlockEditBtn.dispatchEvent({ type: 'click' });
  phoneInputEl.value = '0909888999';
  verDocFileInput.files = [{ name: '04_bang_luong_moi.jpg', type: 'image/jpeg', size: 195000 }];
  await verDocFileInput.dispatchEvent({ type: 'change' });
  await confirmUpdateBottomBtn.dispatchEvent({ type: 'click' });
  const savedFromBottomFooter = editFooterEl.classList.contains('d-none') &&
    !foundBanner.classList.contains('is-editing') &&
    phoneInputEl.hasAttribute('readonly') &&
    verDocBox.classList.contains('is-locked') &&
    Boolean(apiCalls.lastPutCustomerBody && apiCalls.lastPutCustomerBody.phoneNumber === '0909888999') &&
    verGallery.innerHTML.includes('04_bang_luong_moi.jpg') &&
    verGallery.innerHTML.includes('01_hop_dong_ld.jpg');

  // 4) Auto-Save Guard: unlock "Cập nhật TT", edit email without clicking Save, then run pmEnsureCustomerReadyForOrigination()
  await unlockEditBtn.dispatchEvent({ type: 'click' });
  emailInputEl.value = 'minhquan.autosave@gmail.com';
  const autoSaveReady = await sim.sandbox.window.pmEnsureCustomerReadyForOrigination();
  const autoSavedOnSubmit = autoSaveReady === true &&
    editFooterEl.classList.contains('d-none') &&
    emailInputEl.hasAttribute('readonly') &&
    Boolean(apiCalls.lastPutCustomerBody && apiCalls.lastPutCustomerBody.email === 'minhquan.autosave@gmail.com');

  collector.recordCheck({
    tier: 3,
    checkName: 'Existing Customer "Cập nhật TT" reveals bottom #ccEditCustomerFooter (Option 1B), supports Cancel/Revert, saves via bottom CTA without scrolling up, and auto-saves on contract submit',
    passed: editUnlockedCorrectly && cancelledAndReverted && savedFromBottomFooter && autoSavedOnSubmit,
    expected: 'Unlock shows #ccEditCustomerFooter + .is-editing banner; Cancel reverts phone & staged photo; Bottom Save PUTs /api/v1/customers/12 & locks; Origination auto-saves unlocked edits',
    actual: `unlocked=${editUnlockedCorrectly}, reverted=${cancelledAndReverted}, bottomSaved=${savedFromBottomFooter}, autoSaved=${autoSavedOnSubmit}`
  });

  // Simulate LTV Ceiling Breach & Vault Cash Balance Gate Breach (branch cash default = 500,000,000 -> set principal 600,000,000 on 500,000,000 appraised)
  sim.elementsById.get('ccAppraisedValue').value = '500000000';
  await sim.elementsById.get('ccAppraisedValue').dispatchEvent({ type: 'input' });
  sim.elementsById.get('ccPrincipalAmount').value = '600000000';
  await sim.elementsById.get('ccPrincipalAmount').dispatchEvent({ type: 'input' });

  const ltvAlertEl = sim.elementsById.get('ccLtvWarningAlert');
  const cashAlertEl = sim.elementsById.get('ccCashBalanceAlert');
  const sumRiskLabelEl = sim.elementsById.get('sumLtvRiskLabel');
  const cashBadgeEl = sim.elementsById.get('sumCashSafetyBadge');

  collector.recordCheck({
    tier: 3,
    checkName: 'Exceeding LTV policy cap and branch cash balance reveals #ccLtvWarningAlert, #ccCashBalanceAlert, and danger ledger badges',
    passed: !ltvAlertEl.classList.contains('d-none') &&
            !cashAlertEl.classList.contains('d-none') &&
            sumRiskLabelEl.innerHTML.includes('Vượt trần LTV') &&
            cashBadgeEl.innerHTML.includes('Vượt tồn quỹ quầy'),
    expected: '#ccLtvWarningAlert & #ccCashBalanceAlert visible with danger badges',
    actual: `ltvAlertVisible=${!ltvAlertEl.classList.contains('d-none')}, cashAlertVisible=${!cashAlertEl.classList.contains('d-none')}`
  });

  // ===========================================================================
  // AUDIT ACTION PLAN CHECKS (STEPS 1, 2, 3, 4 & 5)
  // ===========================================================================

  // Step 1: Progressive Section 1 Accordion Sub-steps (1A-1E) & Sticky Stepper Sub-progress
  const auditCccdEl = sim.elementsById.get('ccCustomerIdNumber');
  const auditLookupBtn = sim.elementsById.get('btnLookupCccd');
  const auditBannerCreateBtn = sim.elementsById.get('btnBannerCreateCustomer');
  const auditPhoneInput = sim.elementsById.get('ccCustomerPhone');
  const auditEmailInput = sim.elementsById.get('ccCustomerEmail');
  const auditCatSelect = sim.elementsById.get('ccCollateralCategory');
  const auditTechGroup = sim.elementsById.get('ccTechFieldsGroup');
  const auditVehicleGroup = sim.elementsById.get('ccVehicleFieldsGroup');
  const auditImeiInput = sim.elementsById.get('ccSerialOrImei');

  const accCollapse1A = sim.elementsById.get('accCollapse1A');
  const accCollapse1B = sim.elementsById.get('accCollapse1B');
  const accCollapse1C = sim.elementsById.get('accCollapse1C');
  const accCollapse1D = sim.elementsById.get('accCollapse1D');
  const accCollapse1E = sim.elementsById.get('accCollapse1E');
  const stepSubprogressEl = sim.elementsById.get('ccStepCustomerSubprogress');
  const initialSubprogress = stepSubprogressEl ? stepSubprogressEl.textContent : '';

  // Enter new customer creation mode -> 1A & 1B open, 1C/1D/1E collapsed, then progressive auto-advance 1B -> 1C -> 1D -> 1E
  auditCccdEl.value = '079204777666';
  await auditLookupBtn.dispatchEvent({ type: 'click' });
  await auditBannerCreateBtn.dispatchEvent({ type: 'click' });
  const newModeInitialAccordionsOk = accCollapse1A.classList.contains('show') &&
    accCollapse1B.classList.contains('show') &&
    !accCollapse1C.classList.contains('show') &&
    !accCollapse1D.classList.contains('show') &&
    !accCollapse1E.classList.contains('show') &&
    stepSubprogressEl.textContent === '1A/E';

  sim.elementsById.get('ccCustomerName').value = 'Phạm Thu Hà';
  auditPhoneInput.value = '0912345678';
  await auditPhoneInput.dispatchEvent({ type: 'change' });
  const autoExpanded1C = accCollapse1C.classList.contains('show') && stepSubprogressEl.textContent === '1B/E';

  sim.elementsById.get('ccProvinceCode').value = '79';
  await sim.elementsById.get('ccProvinceCode').dispatchEvent({ type: 'change' });
  sim.elementsById.get('ccDistrictCode').value = '760';
  await sim.elementsById.get('ccDistrictCode').dispatchEvent({ type: 'change' });
  sim.elementsById.get('ccWardCode').value = '26740';
  sim.elementsById.get('ccAddressStreet').value = '12 Lê Lợi';
  await sim.elementsById.get('ccAddressStreet').dispatchEvent({ type: 'change' });
  const autoExpanded1D = accCollapse1D.classList.contains('show') && stepSubprogressEl.textContent === '1D/E';

  await sim.elementsById.get('ccOccupationGroup').dispatchEvent({ type: 'change' });
  const autoExpanded1E = accCollapse1E.classList.contains('show') && stepSubprogressEl.textContent === '1D/E';

  // Stage a verification doc in 1E -> subprogress reaches 1E/E!
  const auditVerFileInput = sim.elementsById.get('ccDocumentFileInput');
  auditVerFileInput.files = [{ name: 'bang_luong_ha.jpg', type: 'image/jpeg', size: 120000 }];
  await auditVerFileInput.dispatchEvent({ type: 'change' });
  const reachedSubprogress1E = stepSubprogressEl.textContent === '1E/E';

  collector.recordCheck({
    tier: 3,
    checkName: 'Step 1 Accordion Sub-steps (1A-1E): progressive disclosure auto-expands 1B -> 1C -> 1D -> 1E and updates #ccStepCustomerSubprogress (1A/E -> 1E/E)',
    passed: initialSubprogress === '1E/E' &&
            newModeInitialAccordionsOk &&
            autoExpanded1C &&
            autoExpanded1D &&
            autoExpanded1E &&
            reachedSubprogress1E,
    expected: 'FOUND=1E/E; New customer starts at 1A/E with 1A+1B open & 1C-1E collapsed, then auto-expands 1C -> 1D -> 1E and reaches 1E/E on KYC doc upload',
    actual: `initial=${initialSubprogress}, newInit=${newModeInitialAccordionsOk}, 1C=${autoExpanded1C}, 1D=${autoExpanded1D}, 1E=${autoExpanded1E}, final1E=${reachedSubprogress1E}`
  });

  // Step 3A: Real-Time Inline Field Validation on Blur / Change
  auditCccdEl.value = '12345';
  await auditCccdEl.dispatchEvent({ type: 'blur' });
  const cccdBlurInvalid = auditCccdEl.classList.contains('is-invalid') && sim.elementsById.has('ccCustomerIdNumber-error');
  auditCccdEl.value = '079204777666';
  await auditCccdEl.dispatchEvent({ type: 'blur' });
  const cccdBlurCleared = !auditCccdEl.classList.contains('is-invalid');

  auditPhoneInput.value = '12345';
  await auditPhoneInput.dispatchEvent({ type: 'blur' });
  const phoneBlurInvalid = auditPhoneInput.classList.contains('is-invalid') && sim.elementsById.has('ccCustomerPhone-error');
  auditPhoneInput.value = '0912345678';
  await auditPhoneInput.dispatchEvent({ type: 'blur' });

  auditEmailInput.value = 'bad-email-format';
  await auditEmailInput.dispatchEvent({ type: 'blur' });
  const emailBlurInvalid = auditEmailInput.classList.contains('is-invalid') && sim.elementsById.has('ccCustomerEmail-error');
  auditEmailInput.value = 'thuha@example.com';
  await auditEmailInput.dispatchEvent({ type: 'blur' });

  const dobEl = sim.elementsById.get('ccDateOfBirth');
  dobEl.value = '2099-12-31';
  await dobEl.dispatchEvent({ type: 'change' });
  const futureDobInvalid = dobEl.classList.contains('is-invalid') && sim.elementsById.has('ccDateOfBirth-error');
  dobEl.value = '1995-08-20';
  await dobEl.dispatchEvent({ type: 'change' });

  collector.recordCheck({
    tier: 3,
    checkName: 'Step 3A Inline Validation: blur/change validates CCCD (9/12 digits), 10-digit phone, email format, and blocks future DOB/issue dates',
    passed: cccdBlurInvalid && cccdBlurCleared && phoneBlurInvalid && emailBlurInvalid && futureDobInvalid && !dobEl.classList.contains('is-invalid'),
    expected: 'Invalid CCCD/phone/email/future-DOB marked .is-invalid with <id>-error on blur/change and cleared when fixed',
    actual: `cccd=${cccdBlurInvalid}/${cccdBlurCleared}, phone=${phoneBlurInvalid}, email=${emailBlurInvalid}, futureDob=${futureDobInvalid}`
  });

  // Step 3C: Vault Ledger Collateral Category Switch Modal (#modalCollateralCategorySwitch), Spec Group Stash, LTV Delta & Session Skip
  const modalCatSwitchEl = sim.elementsById.get('modalCollateralCategorySwitch');
  const btnCancelCatSwitch = sim.elementsById.get('btnCancelCategorySwitch');
  const btnConfirmCatSwitch = sim.elementsById.get('btnConfirmCategorySwitch');
  const chkSkipCatSwitch = sim.elementsById.get('chkSkipCategorySwitchConfirm');
  const auditGoldGroup = sim.elementsById.get('ccGoldFieldsGroup');

  // Reset first so we start from a clean untouched MOTORBIKE baseline, then switch to CAR -> GOLD_JEWELRY
  await sim.elementsById.get('btnResetForm').dispatchEvent({ type: 'click' });
  await sim.elementsById.get('btnConfirmReset').dispatchEvent({ type: 'click' });

  // 1) MOTORBIKE -> CAR shares VEHICLE spec group -> switches smoothly without modal
  auditCatSelect.value = 'CAR';
  await auditCatSelect.dispatchEvent({ type: 'change' });
  const auditPlateEl = sim.elementsById.get('ccLicensePlate');
  auditPlateEl.value = '51K-888.99';
  await auditPlateEl.dispatchEvent({ type: 'input' });

  // 2) CAR (with operator-entered plate 51K-888.99) -> GOLD_JEWELRY opens #modalCollateralCategorySwitch with 3-part summary
  auditCatSelect.value = 'GOLD_JEWELRY';
  await auditCatSelect.dispatchEvent({ type: 'change' });
  const catModalShown = modalCatSwitchEl && modalCatSwitchEl.classList.contains('show');
  const fromChipHtml = sim.elementsById.get('catSwitchFromChip')?.innerHTML || '';
  const toChipHtml = sim.elementsById.get('catSwitchToChip')?.innerHTML || '';
  const stashedListHtml = sim.elementsById.get('catSwitchStashedList')?.innerHTML || '';
  const ltvSummaryHtml = sim.elementsById.get('catSwitchLtvSummary')?.innerHTML || '';
  const modalRendered3Parts = fromChipHtml.includes('Ô tô') &&
    toChipHtml.includes('Vàng / Trang sức') &&
    stashedListHtml.includes('51K-888.99') &&
    ltvSummaryHtml.includes('→') &&
    ltvSummaryHtml.includes('%');

  // Cancel switch -> stays on CAR & keeps 51K-888.99
  await btnCancelCatSwitch.dispatchEvent({ type: 'click' });
  const abortedSwitchStayedOnPhone = auditCatSelect.value === 'CAR' &&
    !auditVehicleGroup.classList.contains('d-none') &&
    auditPlateEl.value === '51K-888.99';

  // Trigger CAR -> GOLD_JEWELRY again, check "Không hỏi lại trong phiên", and Confirm -> switches to GOLD_JEWELRY
  auditCatSelect.value = 'GOLD_JEWELRY';
  await auditCatSelect.dispatchEvent({ type: 'change' });
  if (chkSkipCatSwitch) chkSkipCatSwitch.checked = true;
  await btnConfirmCatSwitch.dispatchEvent({ type: 'click' });
  const confirmedSwitchToMotorbike = auditCatSelect.value === 'GOLD_JEWELRY' &&
    auditGoldGroup &&
    !auditGoldGroup.classList.contains('d-none');

  // Switch back GOLD_JEWELRY -> CAR -> restores 51K-888.99 from Session Spec Stash, and switching CAR -> PHONE skips modal because chkSkipCatSwitch was checked!
  auditCatSelect.value = 'CAR';
  await auditCatSelect.dispatchEvent({ type: 'change' });
  const restoredCarPlate = auditPlateEl.value === '51K-888.99';
  auditCatSelect.value = 'PHONE';
  await auditCatSelect.dispatchEvent({ type: 'change' });
  const skipInSessionRespected = auditCatSelect.value === 'PHONE' && !modalCatSwitchEl.classList.contains('show');
  const restoredPhoneImei = catModalShown && modalRendered3Parts && restoredCarPlate && skipInSessionRespected;

  // Step 3B: Reset Confirmation Modal (#btnResetForm -> #modalResetConfirmation -> #btnConfirmReset)
  await sim.elementsById.get('btnSaveDraft').dispatchEvent({ type: 'click' });
  const draftBeforeReset = Boolean(sim.mockLocalStorage.getItem('pm_contract_draft_v1'));
  await sim.elementsById.get('btnResetForm').dispatchEvent({ type: 'click' });
  const resetModalOpenedFirst = sim.getShownModals().includes('modalResetConfirmation') &&
    Boolean(sim.mockLocalStorage.getItem('pm_contract_draft_v1'));
  await sim.elementsById.get('btnConfirmReset').dispatchEvent({ type: 'click' });
  const resetConfirmedClearedDraft = !sim.mockLocalStorage.getItem('pm_contract_draft_v1') &&
    sim.elementsById.get('ccCollateralCategory').value === 'MOTORBIKE';

  // Step 3D: Descriptive alt text on single KYC slots & multi-image galleries + Step 5.4 Dynamic MAX LTV + Step 5.7 Filmstrip keyboard nav
  const frontSlotHtml = sim.elementsById.get('ccIdCardFrontSlot').innerHTML;
  const verGalleryAltHtml = sim.elementsById.get('ccVerificationDocGallery').innerHTML;
  const colGalleryAltHtml = sim.elementsById.get('ccCollateralImageGallery').innerHTML;
  const hasDescriptiveAlts = frontSlotHtml.includes('alt="Ảnh CCCD mặt trước - ') &&
    verGalleryAltHtml.includes(' - Ảnh 1 của ') &&
    colGalleryAltHtml.includes(' - Ảnh hiện trạng 1 của ');

  const maxPresetBtnEl = sim.elementsById.get('btnPresetMaxLtv');
  sim.elementsById.get('ccOccupationGroup').value = 'OFFICE_WORKER';
  sim.elementsById.get('ccCollateralCategory').value = 'GOLD_JEWELRY';
  sim.sandbox.window.recalculateLoanAndLedger({ triggerServerLtv: false });
  const dynamicMax92 = maxPresetBtnEl && maxPresetBtnEl.textContent === 'Tối đa (92%)';

  // Step 3E: Keyboard Shortcuts (Ctrl+S saves draft, Ctrl+P opens print preview, #btnKeyboardShortcuts opens #modalKeyboardShortcuts)
  await sim.documentMock.dispatchDocumentEvent({ type: 'keydown', key: 's', ctrlKey: true });
  const ctrlSSavedDraft = Boolean(sim.mockLocalStorage.getItem('pm_contract_draft_v1'));
  await sim.documentMock.dispatchDocumentEvent({ type: 'keydown', key: 'p', ctrlKey: true });
  const ctrlPOpenedPreview = sim.getShownModals().includes('modalContractPrintPreview');
  await sim.elementsById.get('btnKeyboardShortcuts').dispatchEvent({ type: 'click' });
  const shortcutsModalOpened = sim.getShownModals().includes('modalKeyboardShortcuts');

  // Step 5.1 Live clock timezone "(Giờ VN)" & Step 5.7 Filmstrip tabindex="0" + Enter key navigation
  const liveClockHasVnTz = (sim.elementsById.get('liveClock')?.textContent || '').includes('(Giờ VN)');
  sim.sandbox.window.PM_MEDIA_MANAGER.openLightbox(
    sim.sandbox.window.PM_MEDIA_MANAGER.state.verificationDocs,
    0,
    'Chứng từ xác minh'
  );
  const filmstripHtml = sim.elementsById.get('lightboxFilmstrip').innerHTML;
  const filmstripTabbable = filmstripHtml.includes('tabindex="0"');
  await sim.elementsById.get('lightboxFilmstrip').dispatchEvent({
    type: 'keydown',
    key: 'Enter',
    target: { closest: () => ({ getAttribute: () => '1' }) }
  });
  const filmstripEnterSwitched = sim.sandbox.window.PM_MEDIA_MANAGER.state.lightbox.activeIndex === 1;

  collector.recordCheck({
    tier: 3,
    checkName: 'Steps 3B/3C/3D/3E & Step 5: Reset confirmation modal, category switch warning + field preservation, descriptive alt text, dynamic MAX LTV (92%), keyboard shortcuts, (Giờ VN) clock, and filmstrip keyboard nav',
    passed: draftBeforeReset &&
            resetModalOpenedFirst &&
            resetConfirmedClearedDraft &&
            abortedSwitchStayedOnPhone &&
            confirmedSwitchToMotorbike &&
            restoredPhoneImei &&
            hasDescriptiveAlts &&
            dynamicMax92 &&
            ctrlSSavedDraft &&
            ctrlPOpenedPreview &&
            shortcutsModalOpened &&
            liveClockHasVnTz &&
            filmstripTabbable &&
            filmstripEnterSwitched &&
            sim.getInitializedTooltips().length >= 4,
    expected: 'All P1/P2/P3 interactive polish behaviors & 4+ contextual tooltips initialized',
    actual: `resetModal=${resetModalOpenedFirst}/${resetConfirmedClearedDraft}, catSwitch=${abortedSwitchStayedOnPhone}/${confirmedSwitchToMotorbike}/${restoredPhoneImei}, alts=${hasDescriptiveAlts}, max92=${dynamicMax92}, shortcuts=${ctrlSSavedDraft}/${ctrlPOpenedPreview}/${shortcutsModalOpened}, tz=${liveClockHasVnTz}, filmKey=${filmstripTabbable}/${filmstripEnterSwitched}, tooltips=${sim.getInitializedTooltips().length}`
  });

  // ===========================================================================
  // TIER 4: WCAG 2.2 AA ACCESSIBILITY & DESIGN.MD CRAFT FLOOR COMPLIANCE
  // ===========================================================================
  console.log('► Tier 4: WCAG 2.2 AA Labels/Contrast & DESIGN.md ("The Vault Ledger") Craft Floor...');

  // Verify every visible input/select/textarea in #formCreateContract has a matching <label for="...">
  const labelForIds = new Set(dom.getAttributes('for'));
  const controlTagRegex = /<(input|select|textarea)\b([^>]*)>/gi;
  const unlabeledControls = [];
  let cm;
  while ((cm = controlTagRegex.exec(dom.raw)) !== null) {
    const attrStr = cm[2] || '';
    if (/type=["']hidden["']/i.test(attrStr)) continue;
    const idMatch = attrStr.match(/\bid=["']([^"']+)["']/i);
    if (!idMatch) {
      unlabeledControls.push(`missing-id:${cm[1]}`);
    } else if (!labelForIds.has(idMatch[1])) {
      unlabeledControls.push(idMatch[1]);
    }
  }

  collector.recordCheck({
    tier: 4,
    checkName: '100% of visible form controls (<input>, <select>, <textarea>) are explicitly paired with <label for="...">',
    passed: unlabeledControls.length === 0,
    expected: '0 unlabeled visible form controls',
    actual: unlabeledControls.length === 0 ? 'All visible controls labeled' : `Unlabeled: ${unlabeledControls.join(', ')}`
  });

  const errSummaryElem = dom.getElementById('ccFormErrorSummary');
  const errTitleElem = dom.getElementById('ccFormErrorSummaryTitle');
  const errBadgeElem = dom.getElementById('ccErrorSummaryCountBadge');
  collector.recordCheck({
    tier: 4,
    checkName: 'ui-ux-pro-max Error Summary & Live Badge: role="alert" tabindex="-1", semantic <h2 id="ccFormErrorSummaryTitle">, and role="status" aria-atomic="true" count badge',
    passed: Boolean(errSummaryElem &&
            errSummaryElem.openTag.includes('role="alert"') &&
            errSummaryElem.openTag.includes('tabindex="-1"') &&
            errTitleElem &&
            errTitleElem.tagName === 'h2' &&
            errBadgeElem &&
            errBadgeElem.openTag.includes('role="status"') &&
            errBadgeElem.openTag.includes('aria-atomic="true"')),
    expected: 'role="alert" tabindex="-1", <h2 id="ccFormErrorSummaryTitle">, and role="status" aria-atomic="true" on #ccErrorSummaryCountBadge',
    actual: errTitleElem ? `<${errTitleElem.tagName}> + atomic status badge verified` : 'missing'
  });

  // WCAG AA Contrast verification using dashboard.css + contract-create.css tokens
  const primaryContrast = passesWcagAA(dashboardCss.getVariable('--pm-text-primary') || '#0f172a', '#ffffff', false);
  const secondaryContrast = passesWcagAA(dashboardCss.getVariable('--pm-text-secondary') || '#475569', '#f8fafc', false);
  const errorContrast = passesWcagAA('#991b1b', '#fef2f2', false);
  const primaryBtnContrast = passesWcagAA('#ffffff', '#1e40af', false);

  collector.recordCheck({
    tier: 4,
    checkName: 'All Vault Ledger text & semantic alert combinations exceed WCAG 2.2 AA contrast ratio (>= 4.5:1)',
    passed: primaryContrast.passes && secondaryContrast.passes && errorContrast.passes && primaryBtnContrast.passes,
    expected: 'All ratios >= 4.5:1',
    actual: `Ink=${primaryContrast.ratio}:1, Slate=${secondaryContrast.ratio}:1, Error=${errorContrast.ratio}:1, CobaltCTA=${primaryBtnContrast.ratio}:1`
  });

  // Forensic scan: Zero raw Unicode emojis, zero purple/neon gradients, zero nested .card .card
  const emojiRegex = /[\u{1F300}-\u{1FAFF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/u;
  const styleViolations = css.detectAntiPatterns();

  collector.recordCheck({
    tier: 4,
    checkName: 'Forensic scan: Zero raw Unicode emojis in contract-create.html and contract-create.js (pure Bootstrap Icons with aria-hidden="true")',
    passed: !emojiRegex.test(dom.raw) && !emojiRegex.test(jsRaw),
    expected: '0 raw emojis across HTML and JS',
    actual: (!emojiRegex.test(dom.raw) && !emojiRegex.test(jsRaw)) ? '0 raw emojis detected' : 'Emoji detected!'
  });

  collector.recordCheck({
    tier: 4,
    checkName: 'Forensic scan: Zero purple/neon linear gradients, zero neon shadows, and zero nested .card .card in HTML/CSS',
    passed: styleViolations.length === 0 &&
            dom.findNestedCards().length === 0 &&
            !/linear-gradient/i.test(css.raw) &&
            !/linear-gradient/i.test(dom.raw),
    expected: '0 style anti-patterns, 0 gradients, 0 nested cards',
    actual: `violations=${styleViolations.length}, nestedCards=${dom.findNestedCards().length}`
  });

  collector.recordCheck({
    tier: 4,
    checkName: 'Craft floor: >=40px control touch targets (44px category pills & #btnBackToTop), stretched sticky ledger runway, Single-Shell box-shadow:none, prefers-reduced-motion, and @media print A4 sheet',
    passed: css.raw.includes('min-height: 40px') &&
            css.raw.includes('min-height: 44px') &&
            css.raw.includes('.pm-sticky-workspace-hud') &&
            css.raw.includes('--pm-cc-ledger-top') &&
            css.raw.includes('.pm-ledger-footer') &&
            css.raw.includes('.pm-ledger-column') &&
            /\.pm-origination-grid\s*\{\s*align-items:\s*stretch/i.test(css.raw) &&
            !dom.contains('pm-origination-grid align-items-start') &&
            css.raw.includes('.pm-back-to-top') &&
            !/html\s*\{[^}]*overflow-x/i.test(css.raw) &&
            !/body\s*\{[^}]*overflow-x/i.test(css.raw) &&
            css.raw.includes(':focus-visible') &&
            css.raw.includes('box-shadow: none !important') &&
            css.raw.includes('prefers-reduced-motion: reduce') &&
            css.raw.includes('@media print') &&
            css.raw.includes('size: A4 portrait'),
    expected: 'All Vault Ledger craft-floor + stretched <aside.pm-ledger-column> sticky runway + zero html/body overflow-x sticky traps enforced',
    actual: 'Verified stretched .pm-origination-grid/.pm-ledger-column, sticky HUD/ledger/footer, 44px #btnBackToTop, zero html/body overflow-x, :focus-visible, Single-Shell, reduced-motion, and @media print A4'
  });

  // Verify Step 2 (3 Document Optgroups + 2 Bank Optgroups = 5 Optgroups), Step 4 (Responsive scroll-snap stepper + short titles), and Step 5 Minor CSS/HTML fixes
  const optgroupCount = (dom.raw.match(/<optgroup\s+label="/g) || []).length;
  const hasAll5Optgroups = dom.contains('label="Xác minh Thu nhập"') &&
    dom.contains('label="Xác minh Cư trú"') &&
    dom.contains('label="Xác minh Kinh doanh"') &&
    dom.contains('label="Ngân hàng Phổ biến &amp; Quốc doanh"') &&
    dom.contains('label="Ngân hàng Thương mại Cổ phần &amp; Nước ngoài"');
  const hasResponsiveSnapStepper = css.raw.includes('scroll-snap-type: x mandatory') &&
    css.raw.includes('scroll-snap-align: start') &&
    css.raw.includes('content: attr(data-short-title)') &&
    dom.contains('data-short-title="1. KH &amp; Địa chỉ"');
  const hasMinorPassPolish = dom.contains('id="modalOriginationSuccess" data-bs-backdrop="static" data-bs-keyboard="true"') &&
    dom.contains('Ghi chú thẩm định KHÁCH HÀNG:') &&
    dom.contains('Ghi chú ĐIỀU KHOẢN hợp đồng:') &&
    dom.contains('id="ccAddressCascadeError"') &&
    /\.pm-vietnamese-words-hint[\s\S]*?font-style:\s*normal/i.test(css.raw) &&
    /right:\s*1\.75rem/i.test(css.raw);

  collector.recordCheck({
    tier: 4,
    checkName: 'Step 2 Optgroups (3 Doc + 2 Bank = 5), Step 4 Responsive Scroll-Snap Stepper (data-short-title), and Step 5 Minor Polish (ESC modal, distinct placeholders, cascade error alert, normal Vietnamese words-hint, 1.75rem back-to-top)',
    passed: optgroupCount === 5 && hasAll5Optgroups && hasResponsiveSnapStepper && hasMinorPassPolish,
    expected: '5 semantic <optgroup>s (3 doc + 2 bank), scroll-snap stepper + data-short-title, data-bs-keyboard="true", distinct placeholders, font-style:normal Vietnamese hint, right:1.75rem',
    actual: `optgroups=${optgroupCount} (${hasAll5Optgroups}), snapStepper=${hasResponsiveSnapStepper}, minorPolish=${hasMinorPassPolish}`
  });

  // ===========================================================================
  // SUMMARY REPORT
  // ===========================================================================
  const summary = collector.getSummary();

  console.log('\n--------------------------------------------------------------------------------');
  console.log('  CONTRACT ORIGINATION WORKSPACE — TEST EXECUTION SUMMARY');
  console.log('--------------------------------------------------------------------------------');
  console.log(`  Tier 1 (XML & DOM/CSS/JS Parity): ${summary.tierCounts.tier1.passed} / ${summary.tierCounts.tier1.total} checks passed`);
  console.log(`  Tier 2 (Address, Words & Math):   ${summary.tierCounts.tier2.passed} / ${summary.tierCounts.tier2.total} checks passed`);
  console.log(`  Tier 3 (Interactive Simulation):  ${summary.tierCounts.tier3.passed} / ${summary.tierCounts.tier3.total} checks passed`);
  console.log(`  Tier 4 (WCAG & Craft Floor):      ${summary.tierCounts.tier4.passed} / ${summary.tierCounts.tier4.total} checks passed`);
  console.log('--------------------------------------------------------------------------------');
  console.log(`  TOTAL:                            ${summary.totalPassed} / ${summary.totalChecks} checks passed`);
  console.log('--------------------------------------------------------------------------------\n');

  if (summary.totalFailed > 0) {
    console.error('❌ CONTRACT CREATE VERIFICATION FAILURES:');
    collector.results.filter(r => !r.passed).forEach((f, idx) => {
      console.error(`  ${idx + 1}. [Tier ${f.tier}] ${f.checkName}`);
      console.error(`     Expected: ${f.expected}`);
      console.error(`     Actual:   ${f.actual}`);
    });
    process.exit(1);
  } else {
    console.log('✅ ALL CONTRACT ORIGINATION WORKSPACE TESTS PASSED (100% CONFORMANCE)!\n');
  }

  return summary;
}

if (require.main === module) {
  runContractCreatePageTests().catch(err => {
    console.error('Unhandled error in test_contract_create_page:', err);
    process.exit(1);
  });
}

module.exports = runContractCreatePageTests;
