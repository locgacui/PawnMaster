/**
 * PawnMaster Dashboard — Production-grade Interaction & Visualization Script
 * Adheres to OPERATE mode: fast keyboard flow, instantaneous filtering,
 * accessible Chart.js initialization, and responsive state handling.
 */

// Global Chart Instances
let cashFlowChartInstance = null;
let collateralDonutChartInstance = null;

// Currency Formatter for vi-VN
const currencyFormatter = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0
});

/**
 * Format raw number to Vietnamese currency string
 * @param {number|string} amount 
 * @returns {string} e.g. "15.000.000 ₫"
 */
function formatVND(amount) {
  const num = Number(amount) || 0;
  return currencyFormatter.format(num);
}

/**
 * Parse currency formatted string back to number
 * @param {string} str 
 * @returns {number}
 */
function parseVND(str) {
  if (!str) return 0;
  const cleaned = str.toString().replace(/[^\d]/g, '');
  return parseInt(cleaned, 10) || 0;
}

/**
 * Resolve the user's active OS / Browser locale for date & time display.
 * Uses ECMAScript Intl default locale resolution first (matches Chromium/OS ICU locale
 * used by native <input type="date">), falling back to navigator.languages.
 * @param {string} [localeOverride]
 * @returns {string|undefined}
 */
function getSystemLocale(localeOverride) {
  if (localeOverride && typeof localeOverride === 'string') {
    return localeOverride;
  }
  try {
    const resolved = Intl.DateTimeFormat().resolvedOptions().locale;
    if (resolved) return resolved;
  } catch (e) {
    // Ignore Intl resolution restrictions
  }
  if (typeof navigator !== 'undefined') {
    if (Array.isArray(navigator.languages) && navigator.languages.length > 0 && navigator.languages[0]) {
      return navigator.languages[0];
    }
    if (navigator.language) return navigator.language;
  }
  return undefined;
}

/**
 * Derive human-readable short date pattern from the user's system locale
 * (e.g. "dd/mm/yyyy", "mm/dd/yyyy", "yyyy/mm/dd").
 * @param {string} [localeOverride]
 * @returns {string}
 */
function getSystemDateFormatPattern(localeOverride) {
  try {
    const parts = new Intl.DateTimeFormat(getSystemLocale(localeOverride), {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric'
    }).formatToParts(new Date(2026, 10, 21, 12, 0, 0));
    return parts.map(p => {
      if (p.type === 'day') return 'dd';
      if (p.type === 'month') return 'mm';
      if (p.type === 'year') return 'yyyy';
      return p.value;
    }).join('');
  } catch (e) {
    return 'dd/mm/yyyy';
  }
}

/**
 * Format ISO date (YYYY-MM-DD), SSR dd/MM/yyyy fallback string, or Date object
 * according to the user's OS / Browser locale while preserving 2-digit tabular width.
 * @param {Date|string} input
 * @param {string} [localeOverride]
 * @returns {string}
 */
function formatSystemDate(input, localeOverride) {
  if (!input) return '—';
  let dateObj = null;
  if (input instanceof Date) {
    dateObj = input;
  } else if (typeof input === 'string') {
    const trimmed = input.trim();
    if (!trimmed || trimmed === '—') return '—';
    const isoMatch = trimmed.slice(0, 10).match(/^(\d{4})-(\d{2})-(\d{2})$/);
    if (isoMatch) {
      dateObj = new Date(Number(isoMatch[1]), Number(isoMatch[2]) - 1, Number(isoMatch[3]), 12, 0, 0);
    } else {
      const dmyMatch = trimmed.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
      if (dmyMatch) {
        dateObj = new Date(Number(dmyMatch[3]), Number(dmyMatch[2]) - 1, Number(dmyMatch[1]), 12, 0, 0);
      } else {
        dateObj = new Date(trimmed);
      }
    }
  } else {
    dateObj = new Date(input);
  }
  if (!dateObj || Number.isNaN(dateObj.getTime())) return '—';
  try {
    return new Intl.DateTimeFormat(getSystemLocale(localeOverride), {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric'
    }).format(dateObj);
  } catch (e) {
    const d = String(dateObj.getDate()).padStart(2, '0');
    const m = String(dateObj.getMonth() + 1).padStart(2, '0');
    const y = dateObj.getFullYear();
    return `${d}/${m}/${y}`;
  }
}

/**
 * Normalize any embedded YYYY-MM-DD or SSR dd/MM/yyyy tokens inside a label string
 * (e.g. "Tháng này (01/11/2026 — 30/11/2026)") into the user's system locale.
 * @param {string} text
 * @param {string} [localeOverride]
 * @returns {string}
 */
function localizeDateTokensInText(text, localeOverride) {
  if (!text || typeof text !== 'string') return text;
  return text
    .replace(/\b(\d{4})-(\d{2})-(\d{2})\b/g, (_m, yyyy, mm, dd) =>
      formatSystemDate(`${yyyy}-${mm}-${dd}`, localeOverride))
    .replace(/\b(\d{2})\/(\d{2})\/(\d{4})\b/g, (_m, dd, mm, yyyy) =>
      formatSystemDate(`${yyyy}-${mm}-${dd}`, localeOverride));
}

/**
 * Parse a user-typed localized date string (matching the user's OS / Browser locale
 * order such as dd/mm/yyyy, mm/dd/yyyy, yyyy/mm/dd, 2-digit year yy, or 8-digit shorthand)
 * into strict ISO 8601 YYYY-MM-DD, returning '' if incomplete or invalid.
 * @param {string} rawText
 * @param {string} [localeOverride]
 * @returns {string}
 */
function parseSystemDateToIso(rawText, localeOverride) {
  if (!rawText || typeof rawText !== 'string') return '';
  const trimmed = rawText.trim();
  if (!trimmed) return '';

  const validateYmd = (y, m, d) => {
    if (!Number.isInteger(y) || !Number.isInteger(m) || !Number.isInteger(d)) return '';
    let year = y;
    if (year >= 0 && year < 100) {
      year = year <= 49 ? 2000 + year : 1900 + year;
    }
    if (year < 1900 || year > 2100 || m < 1 || m > 12 || d < 1 || d > 31) return '';
    const check = new Date(year, m - 1, d, 12, 0, 0);
    if (check.getFullYear() !== year || (check.getMonth() + 1) !== m || check.getDate() !== d) {
      return '';
    }
    return `${String(year).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
  };

  const isoDirect = trimmed.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
  if (isoDirect) {
    return validateYmd(Number(isoDirect[1]), Number(isoDirect[2]), Number(isoDirect[3]));
  }

  let partOrder = ['day', 'month', 'year'];
  try {
    const parts = new Intl.DateTimeFormat(getSystemLocale(localeOverride), {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric'
    }).formatToParts(new Date(2026, 10, 21, 12, 0, 0));
    const extracted = parts
      .filter(p => p.type === 'day' || p.type === 'month' || p.type === 'year')
      .map(p => p.type);
    if (extracted.length === 3) partOrder = extracted;
  } catch (e) {
    // Keep default order
  }

  if (/^\d{8}$/.test(trimmed)) {
    if (partOrder[0] === 'year') {
      return validateYmd(Number(trimmed.slice(0, 4)), Number(trimmed.slice(4, 6)), Number(trimmed.slice(6, 8)));
    }
    if (partOrder[0] === 'month') {
      return validateYmd(Number(trimmed.slice(4, 8)), Number(trimmed.slice(0, 2)), Number(trimmed.slice(2, 4)));
    }
    return validateYmd(Number(trimmed.slice(4, 8)), Number(trimmed.slice(2, 4)), Number(trimmed.slice(0, 2)));
  }

  const sepMatch = trimmed.match(/^(\d{1,4})[\/\.\-\s]+(\d{1,2})[\/\.\-\s]+(\d{1,4})$/);
  if (!sepMatch) return '';

  const nums = [Number(sepMatch[1]), Number(sepMatch[2]), Number(sepMatch[3])];
  const mapped = {};
  partOrder.forEach((type, idx) => {
    mapped[type] = nums[idx];
  });
  return validateYmd(mapped.year, mapped.month, mapped.day);
}

/**
 * Sync all <input type="date"> controls to the user's OS / Browser locale
 * with guaranteed visible formatting parity across Chromium, Firefox, and Safari
 * via .pm-locale-date-wrap while keeping HTMLInputElement.value strictly ISO YYYY-MM-DD.
 * @param {Document|HTMLElement} [rootEl]
 * @param {string} [localeOverride]
 */
function syncSystemDateInputsLocale(rootEl, localeOverride) {
  const scope = rootEl || (typeof document !== 'undefined' ? document : null);
  if (!scope || typeof scope.querySelectorAll !== 'function') return;
  const sysLocale = getSystemLocale(localeOverride);
  const pattern = getSystemDateFormatPattern(localeOverride);

  scope.querySelectorAll('input[type="date"]').forEach(dateInput => {
    if (sysLocale) {
      dateInput.setAttribute('lang', sysLocale);
    }
    dateInput.setAttribute('data-date-format', pattern);

    if (dateInput._pmSyncLocaleDate) {
      dateInput._pmSyncLocaleDate(localeOverride);
      return;
    }
    if (!dateInput.parentNode || typeof dateInput.parentNode.insertBefore !== 'function' ||
        typeof document === 'undefined' || typeof document.createElement !== 'function') {
      return;
    }

    let activeLocaleOverride = localeOverride;
    const protoDesc = (typeof HTMLInputElement !== 'undefined' && HTMLInputElement.prototype)
      ? Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')
      : null;

    const getRawIso = () => (protoDesc && protoDesc.get) ? (protoDesc.get.call(dateInput) || '') : (dateInput.value || '');
    const setRawIso = val => {
      const clean = val == null ? '' : String(val);
      if (protoDesc && protoDesc.set) protoDesc.set.call(dateInput, clean);
      else dateInput.setAttribute('value', clean);
    };

    const wrap = document.createElement('div');
    wrap.className = 'pm-locale-date-wrap';

    const displayInput = document.createElement('input');
    displayInput.type = 'text';
    displayInput.inputMode = 'numeric';
    displayInput.autocomplete = 'off';
    displayInput.spellcheck = false;
    displayInput.className = 'form-control pm-form-control font-tabular pm-locale-date-display';
    displayInput.placeholder = pattern;

    if (dateInput.id && typeof document.querySelector === 'function') {
      const assocLabel = document.querySelector(`label[for="${dateInput.id}"]`);
      if (assocLabel) {
        if (!assocLabel.id) assocLabel.id = `${dateInput.id}-label`;
        displayInput.setAttribute('aria-labelledby', assocLabel.id);
      }
    }

    const pickerBtn = document.createElement('button');
    pickerBtn.type = 'button';
    pickerBtn.className = 'pm-locale-date-picker-btn';
    pickerBtn.tabIndex = -1;
    pickerBtn.setAttribute('aria-label', 'Chọn ngày trên lịch');
    pickerBtn.innerHTML = '<i class="bi bi-calendar3" aria-hidden="true"></i>';

    dateInput.parentNode.insertBefore(wrap, dateInput);
    wrap.appendChild(dateInput);
    wrap.appendChild(displayInput);
    wrap.appendChild(pickerBtn);

    dateInput.classList.add('pm-native-date-proxy');
    dateInput.tabIndex = -1;
    dateInput.setAttribute('aria-hidden', 'true');

    let isInternalTyping = false;
    const syncTextFromNative = () => {
      const iso = getRawIso();
      displayInput.value = iso ? formatSystemDate(iso, activeLocaleOverride) : '';
      displayInput.placeholder = getSystemDateFormatPattern(activeLocaleOverride);
    };
    const syncStatesFromNative = () => {
      const isRo = dateInput.hasAttribute('readonly') || Boolean(dateInput.readOnly);
      const isDis = dateInput.hasAttribute('disabled') || Boolean(dateInput.disabled);
      displayInput.readOnly = isRo;
      displayInput.disabled = isDis;
      wrap.classList.toggle('is-readonly', isRo || isDis);
      pickerBtn.disabled = isRo || isDis;
    };

    try {
      Object.defineProperty(dateInput, 'value', {
        configurable: true,
        enumerable: true,
        get: () => getRawIso(),
        set: nextVal => {
          setRawIso(nextVal);
          if (!isInternalTyping) syncTextFromNative();
        }
      });
    } catch (e) {}

    dateInput.addEventListener('change', () => {
      if (!isInternalTyping) syncTextFromNative();
    });
    displayInput.addEventListener('blur', () => {
      if (displayInput.readOnly || displayInput.disabled) return;
      const raw = displayInput.value.trim();
      if (!raw) {
        setRawIso('');
        if (typeof Event === 'function') dateInput.dispatchEvent(new Event('change', { bubbles: true }));
        return;
      }
      const parsedIso = parseSystemDateToIso(raw, activeLocaleOverride);
      if (parsedIso) {
        setRawIso(parsedIso);
        displayInput.value = formatSystemDate(parsedIso, activeLocaleOverride);
        if (typeof Event === 'function') dateInput.dispatchEvent(new Event('change', { bubbles: true }));
      } else {
        syncTextFromNative();
      }
    });
    pickerBtn.addEventListener('click', e => {
      e.preventDefault();
      if (displayInput.readOnly || displayInput.disabled) return;
      if (typeof dateInput.showPicker === 'function') {
        try { dateInput.showPicker(); return; } catch (err) {}
      }
      displayInput.focus();
    });

    dateInput._pmSyncLocaleDate = nextOverride => {
      if (nextOverride !== undefined) activeLocaleOverride = nextOverride;
      syncTextFromNative();
      syncStatesFromNative();
    };
    syncTextFromNative();
    syncStatesFromNative();
  });
}

/**
 * Hydrate static / SSR dates in Dashboard tables, modals, and range pills
 * to match the user's OS / Browser locale idempotently via data-iso-date.
 * @param {Document|HTMLElement} [rootEl]
 * @param {string} [localeOverride]
 */
function hydrateStaticDatesToSystemLocale(rootEl, localeOverride) {
  const scope = rootEl || (typeof document !== 'undefined' ? document : null);
  if (!scope || typeof scope.querySelectorAll !== 'function') return;

  const extractIsoOnce = raw => {
    if (!raw) return null;
    const s = String(raw).trim();
    const iso = s.match(/^(\d{4})-(\d{2})-(\d{2})$/);
    if (iso) return `${iso[1]}-${iso[2]}-${iso[3]}`;
    const dmy = s.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
    if (dmy) return `${dmy[3]}-${dmy[2]}-${dmy[1]}`;
    return null;
  };

  scope.querySelectorAll('.pm-date').forEach(el => {
    let iso = el.getAttribute('data-iso-date');
    if (!iso) {
      iso = extractIsoOnce(el.textContent);
      if (iso) el.setAttribute('data-iso-date', iso);
    }
    if (iso) {
      el.textContent = formatSystemDate(iso, localeOverride);
    }
  });

  scope.querySelectorAll('[data-due-date]').forEach(el => {
    let iso = el.getAttribute('data-due-date-iso');
    if (!iso) {
      iso = extractIsoOnce(el.getAttribute('data-due-date'));
      if (iso) el.setAttribute('data-due-date-iso', iso);
    }
    if (iso) {
      el.setAttribute('data-due-date', formatSystemDate(iso, localeOverride));
    }
  });

  ['activeTimeframeRangeLabel', 'dtDateTimeline'].forEach(id => {
    const el = typeof scope.getElementById === 'function'
      ? scope.getElementById(id)
      : (typeof document !== 'undefined' ? document.getElementById(id) : null);
    if (el && !el.getAttribute('data-raw-date-template')) {
      el.setAttribute('data-raw-date-template', el.textContent || '');
    }
    if (el) {
      const tpl = el.getAttribute('data-raw-date-template') || el.textContent || '';
      el.textContent = localizeDateTokensInText(tpl, localeOverride);
    }
  });

  scope.querySelectorAll('#modalCollectInterest .pm-calc-row span.font-tabular').forEach(el => {
    if (!el.getAttribute('data-raw-date-template') && /\b\d{2}\/\d{2}\/\d{4}\b/.test(el.textContent || '')) {
      el.setAttribute('data-raw-date-template', el.textContent || '');
    }
    const tpl = el.getAttribute('data-raw-date-template');
    if (tpl) {
      el.textContent = localizeDateTokensInText(tpl, localeOverride);
    }
  });
}

/**
 * Initialize Cash Flow Spline Area Chart (Chart.js)
 * @param {string} canvasId 
 * @param {object} customData 
 */
function initCashFlowChart(canvasId = 'cashFlowChart', customData = null) {
  const canvas = document.getElementById(canvasId);
  if (!canvas || typeof Chart === 'undefined') return null;

  // Destroy existing instance if present
  if (cashFlowChartInstance) {
    cashFlowChartInstance.destroy();
  }

  const defaultDatasets = {
    LAST_7_DAYS: {
      labels: ['T2 (03/11)', 'T3 (04/11)', 'T4 (05/11)', 'T5 (06/11)', 'T6 (07/11)', 'T7 (08/11)', 'CN (09/11)'],
      disbursement: [85000000, 62000000, 110000000, 75000000, 95000000, 140000000, 80000000],
      collection: [72000000, 88000000, 94000000, 82000000, 115000000, 130000000, 98000000]
    },
    THIS_MONTH: {
      labels: ['Tuần 1', 'Tuần 2', 'Tuần 3', 'Tuần 4'],
      disbursement: [145000000, 180000000, 135000000, 150000000],
      collection: [130000000, 165000000, 148000000, 147000000]
    },
    LAST_MONTH: {
      labels: ['Tuần 1 (T10)', 'Tuần 2 (T10)', 'Tuần 3 (T10)', 'Tuần 4 (T10)'],
      disbursement: [110000000, 135000000, 120000000, 115000000],
      collection: [118000000, 128000000, 134000000, 130000000]
    },
    LAST_6_MONTHS: {
      labels: ['T6', 'T7', 'T8', 'T9', 'T10', 'T11'],
      disbursement: [320000000, 450000000, 380000000, 520000000, 480000000, 610000000],
      collection: [280000000, 390000000, 410000000, 470000000, 510000000, 590000000]
    },
    THIS_YEAR: {
      labels: ['Quý 1', 'Quý 2', 'Quý 3', 'Quý 4 (Dự kiến)'],
      disbursement: [1150000000, 1420000000, 1680000000, 1950000000],
      collection: [980000000, 1310000000, 1590000000, 1820000000]
    },
    months: {
      labels: ['T6', 'T7', 'T8', 'T9', 'T10', 'T11'],
      disbursement: [320000000, 450000000, 380000000, 520000000, 480000000, 610000000],
      collection: [280000000, 390000000, 410000000, 470000000, 510000000, 590000000]
    },
    days: {
      labels: ['T2 (03/11)', 'T3 (04/11)', 'T4 (05/11)', 'T5 (06/11)', 'T6 (07/11)', 'T7 (08/11)', 'CN (09/11)'],
      disbursement: [85000000, 62000000, 110000000, 75000000, 95000000, 140000000, 80000000],
      collection: [72000000, 88000000, 94000000, 82000000, 115000000, 130000000, 98000000]
    },
    year: {
      labels: ['Quý 1', 'Quý 2', 'Quý 3', 'Quý 4 (Dự kiến)'],
      disbursement: [1150000000, 1420000000, 1680000000, 1950000000],
      collection: [980000000, 1310000000, 1590000000, 1820000000]
    }
  };

  const activeData = customData || defaultDatasets.THIS_MONTH;

  const ctx = canvas.getContext('2d');

  // Disbursement Gradient (Blue)
  const blueGradient = ctx.createLinearGradient(0, 0, 0, 270);
  blueGradient.addColorStop(0, 'rgba(37, 99, 235, 0.18)');
  blueGradient.addColorStop(1, 'rgba(37, 99, 235, 0.01)');

  // Collection Gradient (Emerald)
  const emeraldGradient = ctx.createLinearGradient(0, 0, 0, 270);
  emeraldGradient.addColorStop(0, 'rgba(16, 185, 129, 0.18)');
  emeraldGradient.addColorStop(1, 'rgba(16, 185, 129, 0.01)');

  cashFlowChartInstance = new Chart(ctx, {
    type: 'line',
    data: {
      labels: activeData.labels,
      datasets: [
        {
          label: 'Chi giải ngân',
          data: activeData.disbursement,
          borderColor: '#1e40af',
          backgroundColor: blueGradient,
          fill: true,
          tension: 0.35,
          borderWidth: 2.5,
          pointRadius: 4,
          pointHoverRadius: 6,
          pointBackgroundColor: '#ffffff',
          pointBorderColor: '#1e40af',
          pointBorderWidth: 2
        },
        {
          label: 'Thu hồi vốn & lãi',
          data: activeData.collection,
          borderColor: '#10b981',
          backgroundColor: emeraldGradient,
          fill: true,
          tension: 0.35,
          borderWidth: 2.5,
          pointRadius: 4,
          pointHoverRadius: 6,
          pointBackgroundColor: '#ffffff',
          pointBorderColor: '#10b981',
          pointBorderWidth: 2
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false
      },
      plugins: {
        legend: {
          display: true,
          position: 'top',
          align: 'end',
          labels: {
            boxWidth: 12,
            boxHeight: 12,
            usePointStyle: true,
            pointStyle: 'circle',
            font: {
              family: "'Inter', sans-serif",
              size: 12,
              weight: '600'
            },
            color: '#475569'
          }
        },
        tooltip: {
          backgroundColor: '#0f172a',
          titleFont: { family: "'Inter', sans-serif", size: 12, weight: '700' },
          bodyFont: { family: "'Inter', sans-serif", size: 12 },
          padding: 10,
          cornerRadius: 6,
          callbacks: {
            label: function (context) {
              const label = context.dataset.label || '';
              const val = formatVND(context.parsed.y);
              return ` ${label}: ${val}`;
            }
          }
        }
      },
      scales: {
        x: {
          grid: { display: false },
          ticks: {
            font: { family: "'Inter', sans-serif", size: 11 },
            color: '#64748b'
          }
        },
        y: {
          grid: {
            color: '#f1f5f9',
            drawBorder: false
          },
          ticks: {
            font: { family: "'Inter', sans-serif", size: 11 },
            color: '#64748b',
            callback: function (value) {
              if (value >= 1000000000) return (value / 1000000000).toFixed(1) + ' Tỷ';
              if (value >= 1000000) return (value / 1000000).toFixed(0) + ' tr';
              return value;
            }
          }
        }
      }
    }
  });

  return cashFlowChartInstance;
}

/**
 * Initialize Collateral Distribution Donut Chart (Chart.js)
 * @param {string} canvasId 
 */
function initCollateralDonutChart(canvasId = 'collateralDonutChart') {
  const canvas = document.getElementById(canvasId);
  if (!canvas || typeof Chart === 'undefined') return null;

  if (collateralDonutChartInstance) {
    collateralDonutChartInstance.destroy();
  }

  const ctx = canvas.getContext('2d');

  collateralDonutChartInstance = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: ['Xe máy', 'Ô tô', 'Vàng / Trang sức', 'Điện thoại & Laptop', 'Khác'],
      datasets: [{
        data: [45, 25, 15, 10, 5],
        backgroundColor: [
          '#1e40af', // Blue 800 - Xe máy
          '#0284c7', // Sky 600 - Ô tô
          '#d97706', // Amber 600 - Vàng
          '#10b981', // Emerald 500 - Tech
          '#64748b'  // Slate 500 - Khác
        ],
        borderWidth: 2,
        borderColor: '#ffffff',
        hoverOffset: 4
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '70%',
      plugins: {
        legend: {
          display: false // Using custom accessible HTML legend below chart
        },
        tooltip: {
          backgroundColor: '#0f172a',
          titleFont: { family: "'Inter', sans-serif", size: 12, weight: '700' },
          bodyFont: { family: "'Inter', sans-serif", size: 12 },
          padding: 10,
          cornerRadius: 6,
          callbacks: {
            label: function (context) {
              const label = context.label || '';
              const percent = context.parsed || 0;
              return ` ${label}: ${percent}% tỷ trọng`;
            }
          }
        }
      }
    }
  });

  return collateralDonutChartInstance;
}

/**
 * Update pagination UI (buttons, visible range, record count, empty state) for server-side pagination
 */
function updateTablePaginationDom(totalElements, totalPages, currentPage0, pageSize = 10) {
  const paginationContainer = document.getElementById('tablePaginationControls');
  const visibleRangeEl = document.getElementById('tableVisibleRange');
  const recordCountBadge = document.getElementById('tableRecordCountBadge') || document.getElementById('contractRecordCount');
  const emptyState = document.getElementById('tableEmptyState');

  const safeTotal = Math.max(0, Number(totalElements) || 0);
  const safePages = Math.max(0, Number(totalPages) || 0);
  const safePage0 = Math.max(0, Number(currentPage0) || 0);
  const safeSize = Math.max(1, Number(pageSize) || 10);

  // 1. Update Record Count Badge
  if (recordCountBadge) {
    recordCountBadge.textContent = `${safeTotal} HĐ`;
  }

  // 2. Update Visible Range
  if (visibleRangeEl) {
    if (safeTotal === 0) {
      visibleRangeEl.textContent = '0 — 0';
    } else {
      const startIdx = safePage0 * safeSize + 1;
      const endIdx = Math.min((safePage0 + 1) * safeSize, safeTotal);
      visibleRangeEl.textContent = `${startIdx} — ${endIdx}`;
    }
  }

  // 3. Update Empty State
  if (emptyState) {
    emptyState.style.display = safeTotal === 0 ? '' : 'none';
  }

  // 4. Update Pagination Buttons
  if (!paginationContainer) return;

  if (safeTotal === 0 || safePages <= 1) {
    paginationContainer.innerHTML = `
      <li><button type="button" class="pm-page-link" disabled="disabled" aria-label="Trang trước"><i class="bi bi-chevron-left"></i></button></li>
      <li><button type="button" class="pm-page-link active" data-page="1">1</button></li>
      <li><button type="button" class="pm-page-link" disabled="disabled" aria-label="Trang sau"><i class="bi bi-chevron-right"></i></button></li>
    `;
    return;
  }

  const items = [];
  const hasPrev = safePage0 > 0;
  items.push(
    `<li><button type="button" class="pm-page-link" data-page="prev" ${hasPrev ? '' : 'disabled="disabled"'} aria-label="Trang trước"><i class="bi bi-chevron-left"></i></button></li>`
  );

  const maxButtons = 5;
  const currentDisplay = safePage0 + 1;
  let startPage = Math.max(1, currentDisplay - 2);
  let endPage = Math.min(safePages, startPage + maxButtons - 1);
  if (endPage - startPage < maxButtons - 1) {
    startPage = Math.max(1, endPage - maxButtons + 1);
  }

  for (let p = startPage; p <= endPage; p++) {
    items.push(
      `<li><button type="button" class="pm-page-link ${p === currentDisplay ? 'active' : ''}" data-page="${p}">${p}</button></li>`
    );
  }

  const hasNext = safePage0 + 1 < safePages;
  items.push(
    `<li><button type="button" class="pm-page-link" data-page="next" ${hasNext ? '' : 'disabled="disabled"'} aria-label="Trang sau"><i class="bi bi-chevron-right"></i></button></li>`
  );

  paginationContainer.innerHTML = items.join('');

  paginationContainer.querySelectorAll('button[data-page]').forEach(btn => {
    btn.addEventListener('click', function () {
      if (this.disabled) return;
      const target = this.getAttribute('data-page');
      let targetPage0 = safePage0;
      if (target === 'prev') {
        targetPage0 = Math.max(0, safePage0 - 1);
      } else if (target === 'next') {
        targetPage0 = Math.min(safePages - 1, safePage0 + 1);
      } else {
        const parsed = parseInt(target, 10);
        if (!isNaN(parsed)) targetPage0 = Math.max(0, parsed - 1);
      }

      if (window.location.protocol.startsWith('http')) {
        fetchDashboardContractPage(targetPage0);
      }
    });
  });
}

/**
 * Lazily fetch a page slice of contracts from GET /api/v1/dashboard/contracts
 * Without re-fetching or re-rendering Topbar, KPIs, Cash Flow chart, or Collateral Donut chart.
 */
async function fetchDashboardContractPage(pageIndex = 0) {
  if (!window.location.protocol.startsWith('http')) {
    return null;
  }

  const safePage0 = Math.max(0, parseInt(pageIndex, 10) || 0);
  const tf = PM_DASHBOARD_STATE.activeTimeframe || 'THIS_MONTH';
  const tab = PM_DASHBOARD_STATE.activeTabFilter || 'ALL';
  const keyword = (PM_DASHBOARD_STATE.activeSearchKeyword || '').trim();
  const branchId = PM_DASHBOARD_STATE.activeBranchId;
  const size = PM_DASHBOARD_STATE.tablePageSize || 10;

  const queryParams = [
    `page=${safePage0}`,
    `size=${size}`,
    `timeframe=${encodeURIComponent(tf)}`,
    `tab=${encodeURIComponent(tab)}`
  ];
  if (branchId != null && branchId !== '' && branchId !== 'ALL') {
    queryParams.push(`branchId=${encodeURIComponent(branchId)}`);
  }
  if (keyword.length > 0) {
    queryParams.push(`keyword=${encodeURIComponent(keyword)}`);
  }

  const url = `/api/v1/dashboard/contracts?${queryParams.join('&')}`;

  const loadingBar = document.getElementById('dashboardLoadingBar');
  const table = document.getElementById('recentContractsTable');
  if (loadingBar) loadingBar.classList.add('is-loading');
  if (table) table.classList.add('pm-table-loading');

  try {
    PM_DASHBOARD_STATE.isFetchingContractPage = true;
    const payload = await apiFetch(url, { method: 'GET' });
    if (payload && payload.data) {
      const pageData = payload.data;
      PM_DASHBOARD_STATE.isServerPaginated = true;
      PM_DASHBOARD_STATE.contractPage = pageData;
      PM_DASHBOARD_STATE.tablePage = pageData.page;
      PM_DASHBOARD_STATE.tableTotalPages = pageData.totalPages;
      PM_DASHBOARD_STATE.tableTotalElements = pageData.totalElements;
      PM_DASHBOARD_STATE.tablePageSize = pageData.size;
      PM_DASHBOARD_STATE.contracts = pageData.items || [];

      // Auto-clamping if current page became empty after a mutation:
      if (pageData.page > 0 && (!pageData.items || pageData.items.length === 0) && pageData.totalElements > 0) {
        return await fetchDashboardContractPage(Math.max(0, pageData.totalPages - 1));
      }

      renderContractsTableRows(PM_DASHBOARD_STATE.contracts);
      updateTablePaginationDom(
        pageData.totalElements,
        pageData.totalPages,
        pageData.page,
        pageData.size
      );
      return pageData;
    }
  } catch (err) {
    if (err && (err.isBranchAccessDenied || err.status === 403 || err.code === 4004)) {
      showBranchAccessDeniedModal(err.message, `Chi nhánh #${branchId || ''}`);
    } else {
      console.error('Lỗi khi tải trang hợp đồng:', err);
      showToast(err.message || 'Không thể tải danh sách hợp đồng', 'danger');
    }
  } finally {
    PM_DASHBOARD_STATE.isFetchingContractPage = false;
    if (loadingBar) loadingBar.classList.remove('is-loading');
    if (table) table.classList.remove('pm-table-loading');
  }
  return null;
}

/**
 * Intelligently refresh Dashboard after a successful modal mutation (Originate, Approve, Reject, Collect, Redeem, Liquidate)
 * @param {boolean} resetToFirstPage When true (e.g. new contract), resets to tab ALL, page 0, keyword ''
 */
async function refreshDashboardAfterMutation(resetToFirstPage = false) {
  if (resetToFirstPage) {
    PM_DASHBOARD_STATE.activeTabFilter = 'ALL';
    PM_DASHBOARD_STATE.activeSearchKeyword = '';
    PM_DASHBOARD_STATE.tablePage = 0;

    const filterTabsContainer = document.getElementById('contractFilterTabs');
    if (filterTabsContainer) {
      filterTabsContainer.querySelectorAll('.pm-tab-btn, .status-filter-tab').forEach(b => b.classList.remove('active'));
      const allTab = filterTabsContainer.querySelector('[data-filter="ALL"]');
      if (allTab) allTab.classList.add('active');
    }
    const searchInput = document.getElementById('contractSearchInput');
    if (searchInput) searchInput.value = '';
    const searchClear = document.getElementById('contractSearchClear');
    if (searchClear) searchClear.style.display = 'none';

    await loadDashboardDataFromDb(PM_DASHBOARD_STATE.activeBranchId, false);
    return;
  }

  // Preserve active tab, keyword, and page
  const savedTab = PM_DASHBOARD_STATE.activeTabFilter || 'ALL';
  const savedKeyword = PM_DASHBOARD_STATE.activeSearchKeyword || '';
  const savedPage = PM_DASHBOARD_STATE.tablePage || 0;

  // 1. Reload summary to refresh KPIs, cash balance, tab counts, and charts
  await loadDashboardDataFromDb(PM_DASHBOARD_STATE.activeBranchId, false);

  // 2. Re-fetch active page slice so the user's active page and tab remain coherent
  PM_DASHBOARD_STATE.activeTabFilter = savedTab;
  PM_DASHBOARD_STATE.activeSearchKeyword = savedKeyword;
  if (PM_DASHBOARD_STATE.isServerPaginated) {
    await fetchDashboardContractPage(savedPage);
  }
}

/**
 * Initialize Table Live Search, Filter Tabs, and Client-Side Pagination
 */
function initDashboardFilters(tableId = 'recentContractsTable', searchInputId = 'contractSearchInput', filterTabsId = 'contractFilterTabs') {
  const table = document.getElementById(tableId);
  const searchInput = document.getElementById(searchInputId);
  const searchClear = document.getElementById('contractSearchClear');
  const filterTabsContainer = document.getElementById(filterTabsId);
  const emptyState = document.getElementById('tableEmptyState');
  const recordCountBadge = document.getElementById('tableRecordCountBadge');
  const paginationContainer = document.getElementById('tablePaginationControls');

  if (!table) return;

  const tbody = table.querySelector('tbody');
  if (!tbody) return;

  const PAGE_SIZE = 10;
  let currentStatusFilter = 'ALL';
  let currentSearchQuery = '';
  let currentPage = 1;

  function renderPagination(totalItems, totalPages) {
    if (!paginationContainer) return;
    if (totalItems === 0 || totalPages <= 1) {
      paginationContainer.innerHTML = `
        <li><button type="button" class="pm-page-link" disabled="disabled" aria-label="Trang trước"><i class="bi bi-chevron-left"></i></button></li>
        <li><button type="button" class="pm-page-link active" data-page="1">1</button></li>
        <li><button type="button" class="pm-page-link" disabled="disabled" aria-label="Trang sau"><i class="bi bi-chevron-right"></i></button></li>
      `;
      return;
    }

    const items = [];
    items.push(
      `<li><button type="button" class="pm-page-link" data-page="prev" ${currentPage <= 1 ? 'disabled="disabled"' : ''} aria-label="Trang trước"><i class="bi bi-chevron-left"></i></button></li>`
    );

    const maxButtons = 5;
    let startPage = Math.max(1, currentPage - 2);
    let endPage = Math.min(totalPages, startPage + maxButtons - 1);
    if (endPage - startPage < maxButtons - 1) {
      startPage = Math.max(1, endPage - maxButtons + 1);
    }

    for (let p = startPage; p <= endPage; p++) {
      items.push(
        `<li><button type="button" class="pm-page-link ${p === currentPage ? 'active' : ''}" data-page="${p}">${p}</button></li>`
      );
    }

    items.push(
      `<li><button type="button" class="pm-page-link" data-page="next" ${currentPage >= totalPages ? 'disabled="disabled"' : ''} aria-label="Trang sau"><i class="bi bi-chevron-right"></i></button></li>`
    );

    paginationContainer.innerHTML = items.join('');
    paginationContainer.querySelectorAll('button[data-page]').forEach(btn => {
      btn.addEventListener('click', function () {
        const target = this.getAttribute('data-page');
        if (target === 'prev' && currentPage > 1) {
          currentPage--;
        } else if (target === 'next' && currentPage < totalPages) {
          currentPage++;
        } else {
          const parsed = parseInt(target, 10);
          if (!isNaN(parsed)) currentPage = parsed;
        }
        applyFilters(false);
      });
    });
  }

  function applyFilters(resetPage = true) {
    if (resetPage) currentPage = 1;

    const rows = Array.from(tbody.querySelectorAll('tr[data-contract-code]'));
    const query = currentSearchQuery.trim().toLowerCase();
    const matchedRows = [];

    rows.forEach(row => {
      const rowStatus = (row.getAttribute('data-status') || '').toUpperCase();
      const isDueToday = row.getAttribute('data-due-today') === 'true';

      let statusMatches = false;
      if (currentStatusFilter === 'ALL') {
        statusMatches = true;
      } else if (currentStatusFilter === 'ACTIVE') {
        statusMatches = (rowStatus === 'ACTIVE' || rowStatus === 'EXTENDED');
      } else if (currentStatusFilter === 'DUE_TODAY') {
        statusMatches = isDueToday;
      } else if (currentStatusFilter === 'OVERDUE') {
        statusMatches = (rowStatus === 'OVERDUE');
      } else if (currentStatusFilter === 'WAITING_LIQUIDATION') {
        statusMatches = (rowStatus === 'WAITING_LIQUIDATION' || rowStatus === 'LIQUIDATING');
      } else if (currentStatusFilter === 'PENDING_APPROVAL') {
        statusMatches = (rowStatus === 'PENDING_APPROVAL');
      }

      let searchMatches = true;
      if (query.length > 0) {
        const rowText = (row.textContent || '').toLowerCase();
        searchMatches = rowText.includes(query);
      }

      if (statusMatches && searchMatches) {
        matchedRows.push(row);
      } else {
        row.style.display = 'none';
      }
    });

    const totalMatched = matchedRows.length;
    const totalPages = Math.max(1, Math.ceil(totalMatched / PAGE_SIZE));
    if (currentPage > totalPages) currentPage = totalPages;

    const startIdx = (currentPage - 1) * PAGE_SIZE;
    const endIdx = Math.min(startIdx + PAGE_SIZE, totalMatched);

    matchedRows.forEach((row, idx) => {
      row.style.display = (idx >= startIdx && idx < endIdx) ? '' : 'none';
    });

    if (emptyState) {
      emptyState.style.display = totalMatched === 0 ? '' : 'none';
    }

    if (recordCountBadge) {
      recordCountBadge.textContent = `${totalMatched} HĐ`;
    }
    const visibleRangeEl = document.getElementById('tableVisibleRange');
    if (visibleRangeEl) {
      visibleRangeEl.textContent = totalMatched > 0 ? `${startIdx + 1} — ${endIdx}` : '0 — 0';
    }

    renderPagination(totalMatched, totalPages);
  }

  // Bind Search Input
  if (searchInput) {
    searchInput.addEventListener('input', function (e) {
      const query = e.target.value;
      currentSearchQuery = query;
      if (searchClear) {
        searchClear.style.display = currentSearchQuery.length > 0 ? 'block' : 'none';
      }

      if (PM_DASHBOARD_STATE.isServerPaginated && window.location.protocol.startsWith('http')) {
        if (PM_DASHBOARD_STATE.searchDebounceTimer) {
          clearTimeout(PM_DASHBOARD_STATE.searchDebounceTimer);
        }
        PM_DASHBOARD_STATE.searchDebounceTimer = setTimeout(() => {
          PM_DASHBOARD_STATE.activeSearchKeyword = query;
          PM_DASHBOARD_STATE.tablePage = 0;
          fetchDashboardContractPage(0);
        }, 300);
      } else {
        applyFilters(true);
      }
    });
  }

  // Bind Search Clear Button
  if (searchClear && searchInput) {
    searchClear.addEventListener('click', function () {
      searchInput.value = '';
      currentSearchQuery = '';
      searchClear.style.display = 'none';
      searchInput.focus();

      if (PM_DASHBOARD_STATE.searchDebounceTimer) {
        clearTimeout(PM_DASHBOARD_STATE.searchDebounceTimer);
      }
      PM_DASHBOARD_STATE.activeSearchKeyword = '';

      if (PM_DASHBOARD_STATE.isServerPaginated && window.location.protocol.startsWith('http')) {
        PM_DASHBOARD_STATE.tablePage = 0;
        fetchDashboardContractPage(0);
      } else {
        applyFilters(true);
      }
    });
  }

  // Bind Filter Tabs
  if (filterTabsContainer) {
    const tabButtons = filterTabsContainer.querySelectorAll('.pm-tab-btn, .status-filter-tab');
    tabButtons.forEach(btn => {
      btn.addEventListener('click', function () {
        tabButtons.forEach(b => b.classList.remove('active'));
        this.classList.add('active');
        const filterVal = this.getAttribute('data-filter') || 'ALL';
        currentStatusFilter = filterVal;
        PM_DASHBOARD_STATE.activeTabFilter = filterVal;

        if (PM_DASHBOARD_STATE.isServerPaginated && window.location.protocol.startsWith('http')) {
          PM_DASHBOARD_STATE.tablePage = 0;
          fetchDashboardContractPage(0);
        } else {
          applyFilters(true);
        }
      });
    });
  }

  // Initial pagination sync for standalone SSR rows
  applyFilters(true);

  // Expose reset function for empty state button
  window.resetTableFilters = function () {
    if (searchInput) {
      searchInput.value = '';
      currentSearchQuery = '';
      if (searchClear) searchClear.style.display = 'none';
    }
    if (filterTabsContainer) {
      const allTab = filterTabsContainer.querySelector('[data-filter="ALL"]');
      if (allTab) {
        filterTabsContainer.querySelectorAll('.pm-tab-btn, .status-filter-tab').forEach(b => b.classList.remove('active'));
        allTab.classList.add('active');
      }
    }
    currentStatusFilter = 'ALL';
    PM_DASHBOARD_STATE.activeTabFilter = 'ALL';
    PM_DASHBOARD_STATE.activeSearchKeyword = '';
    if (PM_DASHBOARD_STATE.searchDebounceTimer) {
      clearTimeout(PM_DASHBOARD_STATE.searchDebounceTimer);
    }

    if (PM_DASHBOARD_STATE.isServerPaginated && window.location.protocol.startsWith('http')) {
      PM_DASHBOARD_STATE.tablePage = 0;
      fetchDashboardContractPage(0);
    } else {
      applyFilters(true);
    }
  };
}

/**
 * Global Dashboard Runtime State (Synchronized with PawnMasterDB)
 */
const PM_DASHBOARD_STATE = {
  currentUser: null,
  userRole: null,
  activeBranchId: null,
  activeTimeframe: 'THIS_MONTH',
  userAssignedBranchId: null,
  userAssignedBranchCode: null,
  userAssignedBranchName: null,
  canSwitchBranch: false,
  branches: [],
  contracts: [],
  cashFlowCharts: null,
  collateralDistribution: null,
  pendingApproveRow: null,
  pendingRejectRow: null,
  pendingLiquidateRow: null,

  // Milestone 5: Server-side lazy pagination & filter state
  isServerPaginated: false,
  contractPage: null,
  tablePage: 0,
  tablePageSize: 10,
  tableTotalElements: 0,
  tableTotalPages: 0,
  activeTabFilter: 'ALL',
  activeSearchKeyword: '',
  searchDebounceTimer: null,
  isFetchingContractPage: false
};

/**
 * Resolve normalized RBAC role from server summary, body SSR attributes, or session storage.
 * Returns null only when opened standalone without any authenticated user context.
 */
function getEffectiveUserRole() {
  const fromState = PM_DASHBOARD_STATE.userRole ||
    (PM_DASHBOARD_STATE.currentUser && PM_DASHBOARD_STATE.currentUser.role);
  if (fromState) {
    return String(fromState).trim().toUpperCase();
  }
  if (typeof document !== 'undefined' && document.body) {
    const bodyRole = document.body.getAttribute('data-user-role');
    if (bodyRole && bodyRole.trim().length > 0) {
      return bodyRole.trim().toUpperCase();
    }
  }
  return null;
}

/**
 * Authoritative RBAC capability matrix (mirrors ContractController & ContractServiceImpl).
 * Unauthorized actions MUST be omitted/removed from the DOM — never hidden via CSS.
 */
function getRoleCapabilities() {
  const role = getEffectiveUserRole();
  return {
    role,
    canSwitchBranch: role
      ? role === 'ADMIN'
      : Boolean(PM_DASHBOARD_STATE.canSwitchBranch || !window.location.protocol.startsWith('http')),
    canApproveOrLiquidate: !role || role === 'ADMIN' || role === 'MANAGER',
    canOperateCounter: !role || role === 'ADMIN' || role === 'MANAGER' || role === 'STAFF',
    canViewDetail: true
  };
}

/**
 * Determine whether the current user is authorized to approve a specific contract.
 * Only ADMIN or same-branch MANAGER on a PENDING_APPROVAL contract may see the Approve trigger.
 */
function canCurrentUserApproveContract(status, contractBranchId = null) {
  if (String(status || '').toUpperCase() !== 'PENDING_APPROVAL') {
    return false;
  }
  const caps = getRoleCapabilities();
  if (!caps.canApproveOrLiquidate) {
    return false;
  }
  if (caps.role === 'MANAGER' &&
      PM_DASHBOARD_STATE.userAssignedBranchId != null &&
      contractBranchId != null &&
      String(contractBranchId) !== '') {
    return Number(PM_DASHBOARD_STATE.userAssignedBranchId) === Number(contractBranchId);
  }
  return true;
}

/**
 * Physically remove unauthorized action elements from the DOM (never hide via CSS).
 */
function pruneUnauthorizedDomActions() {
  if (typeof document === 'undefined') return;
  const caps = getRoleCapabilities();

  if (!caps.canApproveOrLiquidate) {
    document.querySelectorAll('.btn-action-approve, .btn-action-reject, .btn-action-liquidate, #btnDetailToApprove')
      .forEach(el => el.remove());
  }

  if (!caps.canOperateCounter) {
    document.querySelectorAll('.btn-action-collect-interest, .btn-action-redeem, #counterQuickActionsCol, .btn-counter-quick-action')
      .forEach(el => el.remove());
  }
}

/**
 * Format large capital/treasury amounts into compact Vietnamese "Tỷ" notation for branch metadata.
 */
function formatCompactBillionVND(amount) {
  const num = Number(amount) || 0;
  if (num >= 1000000000) {
    return `${(num / 1000000000).toFixed(1)} Tỷ`;
  }
  return formatVND(num);
}

/**
 * Return human-readable label of the currently active branch in #branchSelector.
 */
function getActiveBranchDisplayLabel() {
  const nameEl = document.getElementById('branchTriggerName');
  const codeEl = document.getElementById('branchTriggerCode');
  const nameText = (nameEl && nameEl.textContent ? nameEl.textContent.trim() : '');
  const codeText = (codeEl && codeEl.textContent ? codeEl.textContent.trim() : '');
  if (nameText) {
    return codeText && codeText !== 'ALL-CHAIN' ? `${nameText} (${codeText})` : nameText;
  }
  return 'Chi nhánh quản lý';
}

/**
 * Format human-readable disbursement summary from ContractResponse or plain descriptor.
 */
function formatContractDisbursementText(contractObj) {
  const method = String((contractObj && contractObj.disbursementMethod) || 'CASH').toUpperCase();
  if (method !== 'BANK_TRANSFER') {
    return 'Tiền mặt trực tiếp tại quầy';
  }
  const bankName = (contractObj && contractObj.disbursementBankName) || '';
  const bankCode = (contractObj && contractObj.disbursementBankCode) || '';
  const acc = (contractObj && contractObj.disbursementAccountNumber) || '';
  const holder = (contractObj && contractObj.disbursementAccountHolder) || '';
  const bankLabel = bankName || bankCode || 'Ngân hàng';
  return `Chuyển khoản (${bankLabel} • STK: ${acc || '—'}${holder ? ' • ' + holder : ''})`;
}

function getDashboardTodayIso() {
  const bodyToday = typeof document !== 'undefined' && document.body
    ? (document.body.getAttribute('data-today-iso') || '').trim()
    : '';
  if (bodyToday && /^\d{4}-\d{2}-\d{2}$/.test(bodyToday)) {
    return bodyToday;
  }
  const now = new Date();
  const yyyy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, '0');
  const dd = String(now.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

function addDaysIso(isoStr, days) {
  const baseMatch = typeof isoStr === 'string'
    ? isoStr.trim().slice(0, 10).match(/^(\d{4})-(\d{2})-(\d{2})$/)
    : null;
  const baseDate = baseMatch
    ? new Date(Number(baseMatch[1]), Number(baseMatch[2]) - 1, Number(baseMatch[3]), 12, 0, 0)
    : new Date(new Date().getFullYear(), new Date().getMonth(), new Date().getDate(), 12, 0, 0);
  const shifted = new Date(baseDate.getTime() + (Number(days) || 0) * 86400000);
  const yyyy = shifted.getFullYear();
  const mm = String(shifted.getMonth() + 1).padStart(2, '0');
  const dd = String(shifted.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

/**
 * Populate #modalApproveContract fields from a unified contract descriptor.
 */
function populateApproveContractModal(contractData, sourceRow = null) {
  if (!contractData) return;
  PM_DASHBOARD_STATE.pendingApproveRow = sourceRow || null;

  const contractId = contractData.contractId || '';
  const contractCode = contractData.contractCode || (contractId ? `HD-2026-0${contractId}` : '');
  const stateContract = findContractInState(contractId, contractCode);
  const customerName = contractData.customerName || 'Khách hàng';
  const collateralName = contractData.collateralName || 'Tài sản niêm phong';
  const principalVal = Number(contractData.principalVal || 0);
  const appraisedVal = Number(contractData.appraisedVal || 0);
  const ltvVal = contractData.ltvVal ||
    (appraisedVal > 0 ? ((principalVal / appraisedVal) * 100).toFixed(1) : '75.0');

  const disbMethod = String(
    contractData.disbursementMethod || (stateContract && stateContract.disbursementMethod) || 'CASH'
  ).toUpperCase();
  const bankCode = contractData.disbursementBankCode || (stateContract && stateContract.disbursementBankCode) || '';
  const bankName = contractData.disbursementBankName || (stateContract && stateContract.disbursementBankName) || '';
  const bankAccount = contractData.disbursementAccountNumber || (stateContract && stateContract.disbursementAccountNumber) || '';
  const bankHolder = contractData.disbursementAccountHolder || (stateContract && stateContract.disbursementAccountHolder) || '';

  const rawStartDate = String(contractData.startDate || (stateContract && stateContract.startDate) || '').trim();
  const origStartIso = /^\d{4}-\d{2}-\d{2}/.test(rawStartDate)
    ? rawStartDate.slice(0, 10)
    : parseSystemDateToIso(rawStartDate);
  const loanTermDays = Number(contractData.loanTermDays || (stateContract && stateContract.loanTermDays) || 30) || 30;
  const paymentCycleDays = Number(contractData.paymentCycleDays || (stateContract && stateContract.paymentCycleDays) || 10) || 10;

  const todayIso = getDashboardTodayIso();
  const nextDueIso = addDaysIso(todayIso, paymentCycleDays);
  const maturityIso = addDaysIso(todayIso, loanTermDays);

  const idEl = document.getElementById('apContractId');
  const codeEl = document.getElementById('apContractCodeDisplay');
  const custEl = document.getElementById('apCustomerDisplay');
  const colEl = document.getElementById('apCollateralDisplay');
  const valLtvEl = document.getElementById('apValuationLtvDisplay');
  const disbMethodEl = document.getElementById('apDisbursementMethodDisplay');
  const effectiveStartEl = document.getElementById('apEffectiveStartDateDisplay');
  const effectiveSchedEl = document.getElementById('apEffectiveScheduleDisplay');
  const dateShiftNoticeEl = document.getElementById('apDateShiftNotice');
  const dateShiftNoticeTextEl = document.getElementById('apDateShiftNoticeText');
  const bankBoxEl = document.getElementById('apBankTransferBox');
  const bankNameEl = document.getElementById('apBankNameDisplay');
  const bankAccEl = document.getElementById('apBankAccountDisplay');
  const bankHolderEl = document.getElementById('apBankHolderDisplay');
  const cashbookBadgeEl = document.getElementById('apCashbookBadgeDisplay');
  const disbEl = document.getElementById('apDisbursementAmountDisplay');

  if (idEl) idEl.value = String(contractId);
  if (codeEl) codeEl.textContent = contractCode;
  if (custEl) custEl.textContent = customerName;
  if (colEl) colEl.textContent = collateralName;
  if (valLtvEl) valLtvEl.textContent = `${formatVND(appraisedVal)} (LTV: ${ltvVal}%)`;
  if (disbMethodEl) {
    disbMethodEl.textContent = disbMethod === 'BANK_TRANSFER' ? 'Chuyển khoản ngân hàng' : 'Tiền mặt tại quầy';
  }
  if (effectiveStartEl) {
    effectiveStartEl.textContent = `${formatSystemDate(todayIso)} (Hôm nay)`;
  }
  if (effectiveSchedEl) {
    effectiveSchedEl.textContent = `${formatSystemDate(nextDueIso)} • ${formatSystemDate(maturityIso)} (${loanTermDays} ngày)`;
  }
  if (dateShiftNoticeEl && dateShiftNoticeTextEl) {
    if (origStartIso && /^\d{4}-\d{2}-\d{2}$/.test(origStartIso) && origStartIso < todayIso) {
      dateShiftNoticeEl.classList.remove('d-none');
      dateShiftNoticeTextEl.innerHTML =
        `Hồ sơ lập ngày <strong class="font-tabular">${escapeHtml(formatSystemDate(origStartIso))}</strong>. ` +
        `Khi duyệt hôm nay (<strong class="font-tabular">${escapeHtml(formatSystemDate(todayIso))}</strong>), ` +
        `ngày mở hợp đồng &amp; giải ngân hiệu lực sẽ tự cập nhật sang <strong class="font-tabular">${escapeHtml(formatSystemDate(todayIso))}</strong>, ` +
        `kỳ lãi đầu <strong class="font-tabular">${escapeHtml(formatSystemDate(nextDueIso))}</strong> và đáo hạn <strong class="font-tabular">${escapeHtml(formatSystemDate(maturityIso))}</strong>.`;
    } else {
      dateShiftNoticeEl.classList.add('d-none');
      dateShiftNoticeTextEl.textContent = '';
    }
  }
  if (bankBoxEl) {
    bankBoxEl.classList.toggle('d-none', disbMethod !== 'BANK_TRANSFER');
  }
  if (bankNameEl) {
    bankNameEl.textContent = bankName ? (bankCode ? `${bankName} (${bankCode})` : bankName) : (bankCode || '—');
  }
  if (bankAccEl) bankAccEl.textContent = bankAccount || '—';
  if (bankHolderEl) bankHolderEl.textContent = bankHolder || '—';
  if (cashbookBadgeEl) {
    cashbookBadgeEl.textContent = disbMethod === 'BANK_TRANSFER'
      ? 'Phiếu Chi Giải Ngân (CASH_OUT • Chuyển khoản)'
      : 'Phiếu Chi Giải Ngân (CASH_OUT • Tiền mặt)';
  }
  if (disbEl) disbEl.textContent = formatVND(principalVal);
}

/**
 * Cleanly hand off from #modalContractDetail directly to #modalApproveContract
 * for the exact contract being reviewed, preventing accidental mis-approval.
 */
function transitionFromDetailToApproveModal(contractData, sourceRow = null) {
  populateApproveContractModal(contractData, sourceRow);

  const detailModalEl = document.getElementById('modalContractDetail');
  const approveModalEl = document.getElementById('modalApproveContract');
  if (!approveModalEl || typeof bootstrap === 'undefined') return;

  let handedOff = false;
  const openApproveModal = () => {
    if (handedOff) return;
    handedOff = true;
    const approveModal = bootstrap.Modal.getInstance(approveModalEl) || new bootstrap.Modal(approveModalEl);
    approveModal.show();
    setTimeout(() => {
      const notesEl = document.getElementById('apApprovalNotes');
      if (notesEl) notesEl.focus();
    }, 120);
  };

  if (detailModalEl && detailModalEl.classList.contains('show')) {
    detailModalEl.addEventListener('hidden.bs.modal', openApproveModal, { once: true });
    const detailModal = bootstrap.Modal.getInstance(detailModalEl) || new bootstrap.Modal(detailModalEl);
    detailModal.hide();
    setTimeout(openApproveModal, 320);
  } else {
    openApproveModal();
  }
}

const DASHBOARD_TIMEFRAME_CONFIG = {
  LAST_7_DAYS: {
    label: '7 ngày qua',
    kpiInterestTitle: 'LÃI DỰ THU 7 NGÀY QUA',
    legacyChartKey: 'days'
  },
  THIS_MONTH: {
    label: 'Tháng này',
    kpiInterestTitle: 'LÃI DỰ THU THÁNG NÀY',
    legacyChartKey: 'months'
  },
  LAST_MONTH: {
    label: 'Tháng trước',
    kpiInterestTitle: 'LÃI DỰ THU THÁNG TRƯỚC',
    legacyChartKey: 'months'
  },
  LAST_6_MONTHS: {
    label: '6 tháng trước',
    kpiInterestTitle: 'LÃI DỰ THU 6 THÁNG QUA',
    legacyChartKey: 'months'
  },
  THIS_YEAR: {
    label: 'Năm hiện tại',
    kpiInterestTitle: 'LÃI DỰ THU NĂM HIỆN TẠI',
    legacyChartKey: 'year'
  }
};

function computeTimeframeRangeText(timeframeKey) {
  const now = new Date();
  const fmt = d => formatSystemDate(d);

  const cfg = DASHBOARD_TIMEFRAME_CONFIG[timeframeKey] || DASHBOARD_TIMEFRAME_CONFIG.THIS_MONTH;
  let start = new Date(now.getFullYear(), now.getMonth(), 1);
  let end = new Date(now.getFullYear(), now.getMonth() + 1, 0);

  if (timeframeKey === 'LAST_7_DAYS') {
    start = new Date(now.getTime() - 6 * 86400000);
    end = now;
  } else if (timeframeKey === 'LAST_MONTH') {
    start = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    end = new Date(now.getFullYear(), now.getMonth(), 0);
  } else if (timeframeKey === 'LAST_6_MONTHS') {
    start = new Date(now.getFullYear(), now.getMonth() - 5, 1);
    end = new Date(now.getFullYear(), now.getMonth() + 1, 0);
  } else if (timeframeKey === 'THIS_YEAR') {
    start = new Date(now.getFullYear(), 0, 1);
    end = new Date(now.getFullYear(), 11, 31);
  }

  return `${cfg.label} (${fmt(start)} — ${fmt(end)})`;
}

function syncTimeframeBadgesToDom(timeframeKey, serverRangeLabel = null) {
  const normalized = DASHBOARD_TIMEFRAME_CONFIG[timeframeKey] ? timeframeKey : 'THIS_MONTH';
  const cfg = DASHBOARD_TIMEFRAME_CONFIG[normalized];

  const rangePill = document.getElementById('activeTimeframeRangeLabel');
  if (rangePill) {
    const resolvedRange = serverRangeLabel
      ? localizeDateTokensInText(serverRangeLabel)
      : computeTimeframeRangeText(normalized);
    rangePill.setAttribute('data-raw-date-template', serverRangeLabel || resolvedRange);
    rangePill.textContent = resolvedRange;
  }

  const chartPeriodBadge = document.getElementById('cashFlowPeriodBadge');
  if (chartPeriodBadge) {
    chartPeriodBadge.textContent = `Chu kỳ: ${cfg.label}`;
  }

  const kpiInterestTitle = document.getElementById('kpiExpectedInterestTitle');
  if (kpiInterestTitle) {
    kpiInterestTitle.textContent = cfg.kpiInterestTitle;
  }

  document.querySelectorAll('#dashboardTimeframeBar [data-timeframe]').forEach(btn => {
    btn.classList.toggle('active', btn.getAttribute('data-timeframe') === normalized);
  });
}

/**
 * Prevent accidental double-submits on financial mutation forms
 */
async function withSubmitLock(formEl, asyncTask) {
  const submitBtn = formEl ? formEl.querySelector('button[type="submit"]') : null;
  if (submitBtn && submitBtn.dataset.submitting === 'true') {
    return;
  }
  const originalHtml = submitBtn ? submitBtn.innerHTML : '';
  if (submitBtn) {
    submitBtn.dataset.submitting = 'true';
    submitBtn.disabled = true;
    submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span><span>Đang xử lý...</span>';
  }
  try {
    await asyncTask();
  } finally {
    if (submitBtn) {
      submitBtn.dataset.submitting = 'false';
      submitBtn.disabled = false;
      submitBtn.innerHTML = originalHtml;
    }
  }
}

/**
 * Authenticated API Fetch Helper with Automatic JWT Header & Branch Access Error Interception
 */
async function apiFetch(url, options = {}) {
  const headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
  try {
    const sessionToken = (typeof sessionStorage !== 'undefined') ? sessionStorage.getItem('pm_access_token') : null;
    const localToken = (typeof localStorage !== 'undefined') ? localStorage.getItem('pm_access_token') : null;
    const token = sessionToken || localToken;
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
  } catch (e) {
    // Ignore storage access errors
  }

  const response = await fetch(url, Object.assign({}, options, { headers }));
  let payload = null;
  try {
    payload = await response.json();
  } catch (e) {
    payload = null;
  }

  if (response.status === 401) {
    try {
      if (typeof localStorage !== 'undefined') {
        localStorage.removeItem('pm_access_token');
        localStorage.removeItem('pm_current_user');
      }
      if (typeof sessionStorage !== 'undefined') {
        sessionStorage.removeItem('pm_access_token');
        sessionStorage.removeItem('pm_current_user');
      }
    } catch (e) { }
    if (window.location.protocol.startsWith('http')) {
      window.location.href = '/login';
    }
    const authErr = new Error((payload && payload.message) || 'Phiên đăng nhập đã hết hạn');
    authErr.status = 401;
    throw authErr;
  }

  if (response.status === 403 || (payload && payload.code === 4004)) {
    const err = new Error(
      (payload && payload.message) ||
      'Bạn không có quyền truy cập hoặc thao tác trên chi nhánh khác (Mã lỗi: 4004).'
    );
    err.status = 403;
    err.code = (payload && payload.code) || 4004;
    err.isBranchAccessDenied = (payload && payload.code === 4004) ||
      (err.message && err.message.toLowerCase().includes('chi nhánh'));
    throw err;
  }

  if (!response.ok || (payload && payload.success === false)) {
    const err = new Error((payload && payload.message) || `Lỗi xử lý yêu cầu (HTTP ${response.status})`);
    err.status = response.status;
    err.code = payload ? payload.code : null;
    throw err;
  }

  return payload;
}

/**
 * Display the Vault Ledger Branch Access Denied Security Alert Modal (#modalBranchAccessDenied)
 */
function showBranchAccessDeniedModal(errorMessage, attemptedBranchLabel) {
  const branchPill = document.getElementById('topbarBranchPill');
  if (branchPill) {
    branchPill.classList.remove('pm-branch-pill-shake');
    void branchPill.offsetWidth;
    branchPill.classList.add('pm-branch-pill-shake');
    setTimeout(() => branchPill.classList.remove('pm-branch-pill-shake'), 800);
  }

  const user = PM_DASHBOARD_STATE.currentUser || {};
  const userLabel = (user.fullName || document.getElementById('topbarUserName')?.textContent || 'Nhân sự chi nhánh') +
    (user.role ? ` (${user.role})` : '');
  const allowedBranchLabel = PM_DASHBOARD_STATE.userAssignedBranchName
    ? `${PM_DASHBOARD_STATE.userAssignedBranchName}${PM_DASHBOARD_STATE.userAssignedBranchCode ? ' (' + PM_DASHBOARD_STATE.userAssignedBranchCode + ')' : ''}`
    : (user.branchName || 'Chi nhánh được phân công');

  const msgEl = document.getElementById('badErrorMessage');
  const userEl = document.getElementById('badUserInfo');
  const allowedEl = document.getElementById('badAllowedBranch');
  const attemptedEl = document.getElementById('badAttemptedBranch');
  const auditTimeEl = document.getElementById('badAuditTimestamp');

  if (msgEl && errorMessage) {
    msgEl.textContent = errorMessage;
  }
  if (userEl) {
    userEl.textContent = userLabel;
  }
  if (allowedEl) {
    allowedEl.innerHTML = `<i class="bi bi-check-circle-fill"></i><span>${escapeHtml(allowedBranchLabel)}</span>`;
  }
  if (attemptedEl) {
    attemptedEl.innerHTML = `<i class="bi bi-slash-circle-fill"></i><span>${escapeHtml(attemptedBranchLabel || 'Chi nhánh khác')} (Đã chặn)</span>`;
  }
  if (auditTimeEl) {
    const now = new Date();
    auditTimeEl.textContent = `${formatSystemDate(now)} ${now.toLocaleTimeString(getSystemLocale(), { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })} • HTTP 403`;
  }

  const modalEl = document.getElementById('modalBranchAccessDenied');
  if (modalEl && typeof bootstrap !== 'undefined') {
    const modal = bootstrap.Modal.getInstance(modalEl) || new bootstrap.Modal(modalEl);
    modal.show();
  }

  showToast(errorMessage || 'Bạn không có quyền chuyển sang chi nhánh khác!', 'danger');
}

/**
 * Lookup full Contract DTO by id or code if available in runtime state
 */
function findContractInState(contractId, contractCode) {
  if (!Array.isArray(PM_DASHBOARD_STATE.contracts)) return null;
  return PM_DASHBOARD_STATE.contracts.find(c =>
    (contractId && String(c.id) === String(contractId)) ||
    (contractCode && c.contractCode === contractCode)
  ) || null;
}

/**
 * Setup Quick Actions prefill from table rows (Collect Interest, Redeem, Liquidate, Contract Detail)
 */
function initRowActionTriggers() {
  // 1. Interest collection trigger -> #modalCollectInterest
  document.querySelectorAll('.btn-action-collect-interest').forEach(btn => {
    btn.addEventListener('click', async function (e) {
      e.preventDefault();
      const contractId = this.getAttribute('data-contract-id') || '';
      const code = this.getAttribute('data-contract-code') || '';
      const customer = this.getAttribute('data-customer-name') || '';
      let dueAmount = this.getAttribute('data-due-amount') || '0';

      const modalContractId = document.getElementById('ciContractId');
      const modalContractSelect = document.getElementById('ciContractCode');
      const modalCustomer = document.getElementById('ciCustomerName');
      const modalDueInterest = document.getElementById('ciDueInterest');
      const modalStorageFee = document.getElementById('ciStorageFee');
      const modalPartialPrincipal = document.getElementById('ciPartialPrincipal');
      const modalTotalDue = document.getElementById('ciTotalDue');

      if (modalContractId) modalContractId.value = contractId;
      if (modalContractSelect) modalContractSelect.value = code;
      if (modalCustomer) modalCustomer.value = customer;
      if (modalPartialPrincipal) modalPartialPrincipal.value = '';

      let storageFeeVal = 0;
      if (contractId && window.location.protocol.startsWith('http')) {
        try {
          const today = new Date().toISOString().slice(0, 10);
          const fromDate = new Date(Date.now() - 10 * 86400000).toISOString().slice(0, 10);
          const calcRes = await apiFetch(
            `/api/v1/contracts/${contractId}/calculate-interest?fromDate=${fromDate}&toDate=${today}`,
            { method: 'POST' }
          );
          if (calcRes && calcRes.data) {
            dueAmount = String(calcRes.data.interestAmount || dueAmount);
            storageFeeVal = Number(calcRes.data.storageFeeAmount || 0);
          }
        } catch (err) {
          if (err.isBranchAccessDenied) {
            showBranchAccessDeniedModal(err.message, 'Hợp đồng thuộc chi nhánh khác');
            return;
          }
        }
      }

      if (modalDueInterest) modalDueInterest.value = formatVND(dueAmount);
      if (modalStorageFee) modalStorageFee.value = formatVND(storageFeeVal);
      if (modalTotalDue) modalTotalDue.textContent = formatVND(parseVND(dueAmount) + storageFeeVal);

      if (modalPartialPrincipal && !modalPartialPrincipal.dataset.boundCalc) {
        modalPartialPrincipal.dataset.boundCalc = 'true';
        modalPartialPrincipal.addEventListener('input', function () {
          const curInterest = parseVND(modalDueInterest ? modalDueInterest.value : 0);
          const curStorage = parseVND(modalStorageFee ? modalStorageFee.value : 0);
          const curPartial = parseVND(this.value);
          if (modalTotalDue) {
            modalTotalDue.textContent = formatVND(curInterest + curStorage + curPartial);
          }
        });
      }

      const modalEl = document.getElementById('modalCollectInterest');
      if (modalEl && typeof bootstrap !== 'undefined') {
        const modal = bootstrap.Modal.getInstance(modalEl) || new bootstrap.Modal(modalEl);
        modal.show();
      }
    });
  });

  // 2. Redemption trigger -> #modalRedeemCollateral
  document.querySelectorAll('.btn-action-redeem').forEach(btn => {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      const contractId = this.getAttribute('data-contract-id') || '';
      const code = this.getAttribute('data-contract-code') || '';
      const customer = this.getAttribute('data-customer-name') || '';
      const principal = this.getAttribute('data-principal') || '0';
      const accrued = this.getAttribute('data-accrued-interest') || '0';

      const modalContractId = document.getElementById('rdContractId');
      const modalContract = document.getElementById('rdContractCode');
      const modalCustomer = document.getElementById('rdCustomerName');
      const modalPrincipal = document.getElementById('rdPrincipal');
      const modalAccrued = document.getElementById('rdAccruedInterest');
      const modalTotal = document.getElementById('rdTotalSettlement');

      const totalVal = parseVND(principal) + parseVND(accrued);

      if (modalContractId) modalContractId.value = contractId;
      if (modalContract) modalContract.value = code;
      if (modalCustomer) modalCustomer.value = customer;
      if (modalPrincipal) modalPrincipal.value = formatVND(principal);
      if (modalAccrued) modalAccrued.value = formatVND(accrued);
      if (modalTotal) modalTotal.textContent = formatVND(totalVal);

      const modalEl = document.getElementById('modalRedeemCollateral');
      if (modalEl && typeof bootstrap !== 'undefined') {
        const modal = bootstrap.Modal.getInstance(modalEl) || new bootstrap.Modal(modalEl);
        modal.show();
      }
    });
  });

  // 3. Dedicated Liquidation Modal trigger -> #modalLiquidateCollateral
  document.querySelectorAll('.btn-action-liquidate').forEach(btn => {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      const contractId = this.getAttribute('data-contract-id') || '';
      const code = this.getAttribute('data-contract-code') || '';
      const dbContract = findContractInState(contractId, code);

      const customerName = this.getAttribute('data-customer-name') || (dbContract && dbContract.customerName) || 'Khách hàng';
      const collateralName = this.getAttribute('data-collateral-name') || (dbContract && dbContract.collateralName) || 'Tài sản quá hạn';
      const principalVal = parseVND(this.getAttribute('data-principal') || (dbContract && (dbContract.remainingPrincipal || dbContract.principalAmount)) || 0);
      const accruedVal = parseVND(this.getAttribute('data-accrued-interest') || (dbContract && dbContract.cycleInterestAmount) || 0);
      const suggestedAmount = principalVal + accruedVal;

      PM_DASHBOARD_STATE.pendingLiquidateRow = this.closest('tr');

      const idEl = document.getElementById('lqContractId');
      const codeEl = document.getElementById('lqContractCodeDisplay');
      const assetCustomerEl = document.getElementById('lqAssetCustomerDisplay');
      const principalEl = document.getElementById('lqRemainingPrincipalDisplay');
      const accruedEl = document.getElementById('lqAccruedDebtDisplay');
      const amountInput = document.getElementById('lqLiquidationAmount');
      const recoveryEl = document.getElementById('lqRecoveryStatusDisplay');
      const netCashEl = document.getElementById('lqNetCashInDisplay');

      if (idEl) idEl.value = contractId;
      if (codeEl) codeEl.textContent = code;
      if (assetCustomerEl) assetCustomerEl.textContent = `${customerName} • ${collateralName}`;
      if (principalEl) principalEl.textContent = formatVND(principalVal);
      if (accruedEl) accruedEl.textContent = formatVND(accruedVal);
      if (amountInput) {
        amountInput.value = String(suggestedAmount > 0 ? suggestedAmount : principalVal);
        amountInput.dataset.debtTotal = String(suggestedAmount);
      }

      const updateLiquidationPreview = () => {
        const entered = parseVND(amountInput ? amountInput.value : 0);
        const totalDebt = Number(amountInput?.dataset.debtTotal || 0);
        const diff = entered - totalDebt;
        if (netCashEl) netCashEl.textContent = formatVND(entered);
        if (recoveryEl) {
          if (diff >= 0) {
            recoveryEl.className = 'font-tabular fw-bold text-success';
            recoveryEl.textContent = `Đủ thu hồi gốc & lãi (+${formatVND(diff)})`;
          } else {
            recoveryEl.className = 'font-tabular fw-bold text-danger';
            recoveryEl.textContent = `Thiếu hụt so với dư nợ (-${formatVND(Math.abs(diff))})`;
          }
        }
      };

      if (amountInput && !amountInput.dataset.boundLiqCalc) {
        amountInput.dataset.boundLiqCalc = 'true';
        amountInput.addEventListener('input', updateLiquidationPreview);
      }
      updateLiquidationPreview();

      const modalEl = document.getElementById('modalLiquidateCollateral');
      if (modalEl && typeof bootstrap !== 'undefined') {
        const modal = bootstrap.Modal.getInstance(modalEl) || new bootstrap.Modal(modalEl);
        modal.show();
      }
    });
  });

  // 4. Contract Dossier / Settlement Receipt Detail trigger -> #modalContractDetail
  document.querySelectorAll('.btn-action-detail').forEach(btn => {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      const contractId = this.getAttribute('data-contract-id') || '';
      const code = this.getAttribute('data-contract-code') || '';
      const dbContract = findContractInState(contractId, code);

      const status = (dbContract && (dbContract.collateralStatus === 'WAITING_LIQUIDATION' ? 'WAITING_LIQUIDATION' : dbContract.status)) ||
        this.getAttribute('data-status') || 'ACTIVE';

      const statusMetaMap = {
        PENDING_APPROVAL: { cls: 'pm-badge pm-badge-pending', text: 'Chờ duyệt giải ngân' },
        ACTIVE: { cls: 'pm-badge pm-badge-active', text: 'Đang cầm cố' },
        EXTENDED: { cls: 'pm-badge pm-badge-extended', text: 'Đã gia hạn' },
        OVERDUE: { cls: 'pm-badge pm-badge-overdue', text: 'Quá hạn đóng lãi' },
        WAITING_LIQUIDATION: { cls: 'pm-badge pm-badge-liquidation', text: 'Chờ thanh lý' },
        REDEEMED: { cls: 'pm-badge pm-badge-redeemed', text: 'Đã tất toán chuộc đồ' },
        LIQUIDATED: { cls: 'pm-badge pm-badge-liquidated', text: 'Đã thanh lý hoàn tất' },
        CANCELLED: { cls: 'pm-badge pm-badge-overdue', text: 'Đã từ chối / Hủy' }
      };
      const stMeta = statusMetaMap[status] || statusMetaMap.ACTIVE;

      const customerName = (dbContract && dbContract.customerName) || this.getAttribute('data-customer-name') || 'Khách hàng';
      const customerMeta = dbContract
        ? `${dbContract.customerPhone || '—'}${dbContract.customerIdentityNumber ? ' • CCCD: ' + dbContract.customerIdentityNumber : ''}`
        : (this.getAttribute('data-customer-phone') || 'Đã xác thực hồ sơ gốc');
      const branchLabel = dbContract && dbContract.branchName
        ? `${dbContract.branchName}${dbContract.branchCode ? ' (' + dbContract.branchCode + ')' : ''}`
        : getActiveBranchDisplayLabel();

      const collateralName = (dbContract && dbContract.collateralName) || this.getAttribute('data-collateral-name') || 'Tài sản niêm phong';
      const collateralMeta = dbContract
        ? `${dbContract.collateralLicensePlate ? 'BKS: ' + dbContract.collateralLicensePlate : (dbContract.collateralSerialOrFrame ? 'S/N: ' + dbContract.collateralSerialOrFrame : (dbContract.collateralCode || 'Đã niêm phong'))}${dbContract.collateralStorageLocation ? ' • ' + dbContract.collateralStorageLocation : ''}`
        : (this.getAttribute('data-collateral-sub') || 'Niêm phong tại kho két an toàn');
      const appraisedVal = Number((dbContract && dbContract.collateralAppraisedValue) || this.getAttribute('data-appraised-value') || 0);

      const origPrincipal = Number((dbContract && dbContract.principalAmount) || this.getAttribute('data-principal') || 0);
      const remPrincipal = dbContract && dbContract.remainingPrincipal != null
        ? Number(dbContract.remainingPrincipal)
        : (status === 'REDEEMED' || status === 'LIQUIDATED' ? 0 : origPrincipal);
      const cycleInt = Number((dbContract && dbContract.cycleInterestAmount) || this.getAttribute('data-accrued-interest') || 0);
      const paidInt = Number((dbContract && dbContract.totalInterestPaid) || 0);
      const ltvVal = this.getAttribute('data-ltv') ||
        (dbContract && dbContract.appliedLtvPercent != null
          ? Number(dbContract.appliedLtvPercent).toFixed(1)
          : (appraisedVal > 0 ? ((origPrincipal / appraisedVal) * 100).toFixed(1) : '75.0'));

      const startStr = dbContract && dbContract.startDate ? formatIsoDateToVN(dbContract.startDate) : 'Trong kỳ';
      const fallbackDueIso = this.getAttribute('data-due-date-iso') || this.getAttribute('data-due-date');
      const dueStr = dbContract && dbContract.nextInterestDueDate
        ? formatIsoDateToVN(dbContract.nextInterestDueDate)
        : (fallbackDueIso ? formatIsoDateToVN(fallbackDueIso) : '—');
      const matStr = dbContract && dbContract.maturityDate ? ` • Đáo hạn: ${formatIsoDateToVN(dbContract.maturityDate)}` : '';

      const elHeaderCode = document.getElementById('dtContractCodeHeader');
      const elBadge = document.getElementById('dtStatusBadge');
      const elCustName = document.getElementById('dtCustomerName');
      const elCustMeta = document.getElementById('dtCustomerMeta');
      const elBranch = document.getElementById('dtBranchName');
      const elColName = document.getElementById('dtCollateralName');
      const elColMeta = document.getElementById('dtCollateralMeta');
      const elColSpecRow = document.getElementById('dtCollateralSpecRow');
      const elColSpecDetail = document.getElementById('dtCollateralSpecDetail');
      const elAppraised = document.getElementById('dtAppraisedValue');
      const elPrinSum = document.getElementById('dtPrincipalSummary');
      const elDisbSum = document.getElementById('dtDisbursementSummary');
      const elIntSum = document.getElementById('dtInterestSummary');
      const elTimeline = document.getElementById('dtDateTimeline');
      const elNotes = document.getElementById('dtContractNotes');
      const elAuditHint = document.getElementById('dtFooterAuditHint');
      const elFooterActions = document.getElementById('dtFooterActions');

      if (elHeaderCode) elHeaderCode.textContent = code;
      if (elBadge) {
        elBadge.className = stMeta.cls;
        elBadge.innerHTML = `<span class="pm-badge-dot"></span><span>${escapeHtml(stMeta.text)}</span>`;
      }
      if (elCustName) elCustName.textContent = customerName;
      if (elCustMeta) elCustMeta.textContent = customerMeta;
      if (elBranch) elBranch.textContent = branchLabel;
      if (elColName) elColName.textContent = collateralName;
      if (elColMeta) elColMeta.textContent = collateralMeta;
      if (elColSpecRow && elColSpecDetail) {
        const specParts = [];
        if (dbContract) {
          const cloudLabels = {
            ICLOUD_OFF_UNLOCKED: 'Đã thoát iCloud/MDM',
            PASSCODE_SEALED: 'Mật khẩu niêm phong',
            MECHANICAL_CERTIFIED: 'Kiểm định máy cơ'
          };
          if (dbContract.collateralVehicleFrameNumber) {
            specParts.push(`Khung/Máy: ${dbContract.collateralVehicleFrameNumber}`);
          }
          if (dbContract.collateralVehicleRegNumber) {
            specParts.push(`ĐKX: ${dbContract.collateralVehicleRegNumber}`);
          }
          if (dbContract.collateralVehicleOdometerKm != null) {
            specParts.push(`ODO: ${Number(dbContract.collateralVehicleOdometerKm).toLocaleString('en-US')} km`);
          }
          if (dbContract.collateralTechImeiOrSerial) {
            specParts.push(`IMEI/SN: ${dbContract.collateralTechImeiOrSerial}`);
          }
          if (dbContract.collateralTechPasscodeStatus) {
            const cLabel = cloudLabels[dbContract.collateralTechPasscodeStatus] || dbContract.collateralTechPasscodeStatus;
            specParts.push(cLabel);
          }
          if (dbContract.collateralTechAccessories) {
            specParts.push(`Phụ kiện: ${dbContract.collateralTechAccessories}`);
          }
          if (dbContract.collateralGoldPurity) {
            specParts.push(`Hàm lượng: ${dbContract.collateralGoldPurity}`);
          }
          if (dbContract.collateralGoldWeightChi != null) {
            specParts.push(`Trọng lượng: ${Number(dbContract.collateralGoldWeightChi)} chỉ`);
          }
          if (dbContract.collateralGoldCertificateCode) {
            specParts.push(`Chứng thư: ${dbContract.collateralGoldCertificateCode}`);
          }
          if (dbContract.collateralOtherAssetSpec) {
            specParts.push(dbContract.collateralOtherAssetSpec);
          }
        }
        const specSummary = specParts.join(' • ');
        elColSpecDetail.textContent = specSummary || '—';
        elColSpecRow.classList.toggle('d-none', !specSummary);
      }
      if (elAppraised) elAppraised.textContent = formatVND(appraisedVal);
      if (elPrinSum) elPrinSum.textContent = `${formatVND(origPrincipal)} / Dư nợ hiện tại: ${formatVND(remPrincipal)}`;
      if (elDisbSum) elDisbSum.textContent = formatContractDisbursementText(dbContract || { disbursementMethod: 'CASH' });
      if (elIntSum) elIntSum.textContent = `Lãi kỳ: ${formatVND(cycleInt)} • Lũy kế đã thu: ${formatVND(paidInt)}`;
      if (elTimeline) elTimeline.textContent = `Ngày mở: ${startStr} — Hạn đóng lãi: ${dueStr}${matStr}`;
      if (elNotes) {
        elNotes.textContent = (dbContract && (dbContract.approvalNotes || dbContract.notes)) ||
          'Hồ sơ đã kiểm tra chứng từ & lưu trữ trên Sổ cái Vault Ledger';
      }
      if (elAuditHint) elAuditHint.textContent = `Mã hồ sơ: ${code} • Trạng thái: ${status}`;

      // Strictly mount/remove #btnDetailToApprove in DOM (never hide via CSS)
      const existingApproveBtn = document.getElementById('btnDetailToApprove');
      if (existingApproveBtn) {
        existingApproveBtn.remove();
      }

      const contractBranchId = (dbContract && dbContract.branchId) ||
        this.getAttribute('data-branch-id') ||
        PM_DASHBOARD_STATE.activeBranchId ||
        PM_DASHBOARD_STATE.userAssignedBranchId ||
        null;
      const sourceRow = this.closest('tr');

      if (elFooterActions && canCurrentUserApproveContract(status, contractBranchId)) {
        const approveBtn = document.createElement('button');
        approveBtn.type = 'button';
        approveBtn.id = 'btnDetailToApprove';
        approveBtn.className = 'pm-btn pm-btn-primary';
        approveBtn.setAttribute('aria-label', `Chuyển sang form duyệt hồ sơ hợp đồng ${code}`);
        approveBtn.innerHTML = '<i class="bi bi-patch-check-fill" aria-hidden="true"></i><span>Duyệt hồ sơ</span>';
        approveBtn.addEventListener('click', function () {
          transitionFromDetailToApproveModal({
            contractId: (dbContract && dbContract.id) || contractId,
            contractCode: code,
            customerName,
            collateralName,
            appraisedVal,
            principalVal: origPrincipal,
            ltvVal,
            disbursementMethod: dbContract ? dbContract.disbursementMethod : 'CASH',
            disbursementBankCode: dbContract ? dbContract.disbursementBankCode : '',
            disbursementBankName: dbContract ? dbContract.disbursementBankName : '',
            disbursementAccountNumber: dbContract ? dbContract.disbursementAccountNumber : '',
            disbursementAccountHolder: dbContract ? dbContract.disbursementAccountHolder : '',
            startDate: dbContract ? dbContract.startDate : '',
            loanTermDays: dbContract ? dbContract.loanTermDays : 30,
            paymentCycleDays: dbContract ? dbContract.paymentCycleDays : 10
          }, sourceRow);
        });
        elFooterActions.appendChild(approveBtn);
      }

      const modalEl = document.getElementById('modalContractDetail');
      if (modalEl) {
        if (!modalEl.dataset.boundDetailCleanup) {
          modalEl.dataset.boundDetailCleanup = 'true';
          modalEl.addEventListener('hidden.bs.modal', function () {
            const btn = document.getElementById('btnDetailToApprove');
            if (btn) btn.remove();
          });
        }
        if (typeof bootstrap !== 'undefined') {
          const modal = bootstrap.Modal.getInstance(modalEl) || new bootstrap.Modal(modalEl);
          modal.show();
        }
      }
    });
  });
}

/**
 * Live Clock for Topbar
 */
function initLiveClock() {
  const clockEl = document.getElementById('liveClock');
  if (!clockEl) return;

  function update() {
    const now = new Date();
    const days = ['Chủ Nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];
    const dayName = days[now.getDay()];
    const dateStr = formatSystemDate(now);
    const timeStr = now.toLocaleTimeString(getSystemLocale(), { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
    clockEl.textContent = `${dayName}, ${dateStr} - ${timeStr}`;
  }

  update();
  setInterval(update, 1000);
}

/**
 * Render Cash Flow Chart for a given global timeframe key
 */
function updateCashFlowChartForTimeframe(timeframeKey) {
  const normalized = DASHBOARD_TIMEFRAME_CONFIG[timeframeKey] ? timeframeKey : 'THIS_MONTH';
  const legacyKey = DASHBOARD_TIMEFRAME_CONFIG[normalized].legacyChartKey;

  const fallbackDatasets = {
    LAST_7_DAYS: {
      labels: ['T2 (03/11)', 'T3 (04/11)', 'T4 (05/11)', 'T5 (06/11)', 'T6 (07/11)', 'T7 (08/11)', 'CN (09/11)'],
      disbursement: [85000000, 62000000, 110000000, 75000000, 95000000, 140000000, 80000000],
      collection: [72000000, 88000000, 94000000, 82000000, 115000000, 130000000, 98000000]
    },
    THIS_MONTH: {
      labels: ['Tuần 1', 'Tuần 2', 'Tuần 3', 'Tuần 4'],
      disbursement: [145000000, 180000000, 135000000, 150000000],
      collection: [130000000, 165000000, 148000000, 147000000]
    },
    LAST_MONTH: {
      labels: ['Tuần 1 (T10)', 'Tuần 2 (T10)', 'Tuần 3 (T10)', 'Tuần 4 (T10)'],
      disbursement: [110000000, 135000000, 120000000, 115000000],
      collection: [118000000, 128000000, 134000000, 130000000]
    },
    LAST_6_MONTHS: {
      labels: ['T6', 'T7', 'T8', 'T9', 'T10', 'T11'],
      disbursement: [320000000, 450000000, 380000000, 520000000, 480000000, 610000000],
      collection: [280000000, 390000000, 410000000, 470000000, 510000000, 590000000]
    },
    THIS_YEAR: {
      labels: ['Quý 1', 'Quý 2', 'Quý 3', 'Quý 4 (Dự kiến)'],
      disbursement: [1150000000, 1420000000, 1680000000, 1950000000],
      collection: [980000000, 1310000000, 1590000000, 1820000000]
    }
  };

  if (PM_DASHBOARD_STATE.cashFlowCharts) {
    const dbSeries = PM_DASHBOARD_STATE.cashFlowCharts[normalized] || PM_DASHBOARD_STATE.cashFlowCharts[legacyKey];
    if (dbSeries && Array.isArray(dbSeries.labels) && dbSeries.labels.length > 0) {
      initCashFlowChart('cashFlowChart', {
        labels: dbSeries.labels,
        disbursement: (dbSeries.disburseMillions || []).map(v => Number(v) * 1000000),
        collection: (dbSeries.collectMillions || []).map(v => Number(v) * 1000000)
      });
      return;
    }
  }

  initCashFlowChart('cashFlowChart', fallbackDatasets[normalized] || fallbackDatasets.THIS_MONTH);
}

/**
 * Handle Global Dashboard Timeframe Selector (#dashboardTimeframeBar)
 */
function initChartTimeframeTabs() {
  const tabs = document.querySelectorAll('.pm-chart-tab[data-timeframe]');
  syncTimeframeBadgesToDom(PM_DASHBOARD_STATE.activeTimeframe || 'THIS_MONTH');

  tabs.forEach(tab => {
    tab.addEventListener('click', async function () {
      const selectedTimeframe = this.getAttribute('data-timeframe') || 'THIS_MONTH';
      PM_DASHBOARD_STATE.activeTimeframe = selectedTimeframe;
      PM_DASHBOARD_STATE.tablePage = 0;
      syncTimeframeBadgesToDom(selectedTimeframe);

      if (window.location.protocol.startsWith('http')) {
        try {
          await loadDashboardDataFromDb(PM_DASHBOARD_STATE.activeBranchId, false, selectedTimeframe);
          if (PM_DASHBOARD_STATE.isServerPaginated && ((PM_DASHBOARD_STATE.activeTabFilter && PM_DASHBOARD_STATE.activeTabFilter !== 'ALL') || (PM_DASHBOARD_STATE.activeSearchKeyword && PM_DASHBOARD_STATE.activeSearchKeyword.trim().length > 0))) {
            await fetchDashboardContractPage(0);
          }
          if (window.history && typeof window.history.replaceState === 'function') {
            const q = [];
            if (PM_DASHBOARD_STATE.activeBranchId != null) {
              q.push(`branchId=${encodeURIComponent(PM_DASHBOARD_STATE.activeBranchId)}`);
            }
            q.push(`timeframe=${encodeURIComponent(selectedTimeframe)}`);
            window.history.replaceState({}, '', `/dashboard?${q.join('&')}`);
          }
          const cfg = DASHBOARD_TIMEFRAME_CONFIG[selectedTimeframe] || DASHBOARD_TIMEFRAME_CONFIG.THIS_MONTH;
          showToast(`Đã lọc dữ liệu Dashboard theo khung: ${cfg.label}`, 'info');
          return;
        } catch (err) {
          showToast(err.message || 'Không thể tải dữ liệu cho khung thời gian đã chọn', 'danger');
        }
      }

      updateCashFlowChartForTimeframe(selectedTimeframe);
    });
  });
}

/**
 * Toast Notification Helper (XSS-Safe)
 * @param {string} message 
 * @param {string} type 'success' | 'danger' | 'info'
 */
function showToast(message, type = 'success') {
  let toastContainer = document.getElementById('pmToastContainer');
  if (!toastContainer) {
    toastContainer = document.createElement('div');
    toastContainer.id = 'pmToastContainer';
    toastContainer.className = 'toast-container position-fixed bottom-0 end-0 p-3';
    toastContainer.style.zIndex = '1090';
    document.body.appendChild(toastContainer);
  }

  const toastId = 'toast_' + Date.now();
  const iconClass = type === 'success'
    ? 'bi-check-circle-fill text-success'
    : (type === 'danger' ? 'bi-shield-exclamation text-danger' : 'bi-info-circle-fill text-primary');

  const toastHtml = `
    <div id="${toastId}" class="toast align-items-center border shadow-sm bg-white" role="alert" aria-live="assertive" aria-atomic="true">
      <div class="d-flex">
        <div class="toast-body d-flex align-items-center gap-2 text-dark fw-medium">
          <i class="bi ${iconClass}"></i>
          <span>${escapeHtml(message)}</span>
        </div>
        <button type="button" class="btn-close me-2 m-auto" data-bs-dismiss="toast" aria-label="Đóng"></button>
      </div>
    </div>
  `;

  toastContainer.insertAdjacentHTML('beforeend', toastHtml);
  const toastEl = document.getElementById(toastId);
  if (toastEl && typeof bootstrap !== 'undefined') {
    const toast = new bootstrap.Toast(toastEl, { delay: 4000 });
    toast.show();
    toastEl.addEventListener('hidden.bs.toast', () => toastEl.remove());
  }
}

/**
 * Modal Form Submit Handlers (Connected to PawnMasterDB REST APIs with Double-Submit Protection)
 */
function initModalForms() {
  // Quick Preset Reason Chips inside #modalRejectContract
  const presetGroup = document.getElementById('rejectReasonPresetGroup');
  if (presetGroup) {
    presetGroup.querySelectorAll('.pm-reason-chip').forEach(chip => {
      chip.addEventListener('click', function () {
        presetGroup.querySelectorAll('.pm-reason-chip').forEach(c => c.classList.remove('active'));
        this.classList.add('active');
        const reasonInput = document.getElementById('rjRejectionReason');
        if (reasonInput) {
          reasonInput.value = this.getAttribute('data-reason') || '';
          reasonInput.focus();
        }
      });
    });
  }

  // 1. Collect Interest Form -> POST /api/v1/contracts/{id}/collect-interest
  const formCollectInterest = document.getElementById('formCollectInterest');
  if (formCollectInterest) {
    formCollectInterest.addEventListener('submit', function (e) {
      e.preventDefault();
      withSubmitLock(formCollectInterest, async () => {
        let contractId = document.getElementById('ciContractId')?.value || '';
        const contractCode = (document.getElementById('ciContractCode')?.value || '').trim();

        if (!contractId && contractCode && Array.isArray(PM_DASHBOARD_STATE.contracts)) {
          const matched = PM_DASHBOARD_STATE.contracts.find(c => c.contractCode === contractCode);
          if (matched) contractId = String(matched.id);
        }

        const interestAmount = parseVND(document.getElementById('ciDueInterest')?.value || 0);
        const storageFeeAmount = parseVND(document.getElementById('ciStorageFee')?.value || 0);
        const principalPaidAmount = parseVND(document.getElementById('ciPartialPrincipal')?.value || 0);
        const paymentMethod = document.getElementById('ciPaymentMethod')?.value || 'CASH';

        if (contractId && window.location.protocol.startsWith('http')) {
          try {
            await apiFetch(`/api/v1/contracts/${contractId}/collect-interest`, {
              method: 'POST',
              body: JSON.stringify({
                interestAmount: interestAmount,
                storageFeeAmount: storageFeeAmount,
                penaltyAmount: 0,
                principalPaidAmount: principalPaidAmount,
                paymentMethod: paymentMethod,
                notes: `Thu lãi định kỳ hợp đồng ${contractCode} tại quầy`
              })
            });

            const modalEl = document.getElementById('modalCollectInterest');
            if (modalEl && typeof bootstrap !== 'undefined') {
              const modal = bootstrap.Modal.getInstance(modalEl);
              if (modal) modal.hide();
            }
            showToast(`Đã ghi nhận phiếu thu lãi hợp đồng ${contractCode} và cộng quỹ tiền mặt!`, 'success');
            await refreshDashboardAfterMutation(false);
            return;
          } catch (err) {
            if (err.isBranchAccessDenied) {
              showBranchAccessDeniedModal(err.message, `Hợp đồng ${contractCode}`);
              return;
            }
            showToast(err.message || 'Lỗi khi ghi nhận thu lãi', 'danger');
            return;
          }
        }

        const modalEl = document.getElementById('modalCollectInterest');
        if (modalEl && typeof bootstrap !== 'undefined') {
          const modal = bootstrap.Modal.getInstance(modalEl);
          if (modal) modal.hide();
        }
        showToast('Đã ghi nhận phiếu thu lãi và gia hạn kỳ tiếp theo thành công!', 'success');
      });
    });
  }

  // 3. Redeem Collateral Form -> POST /api/v1/contracts/{id}/redeem
  const formRedeem = document.getElementById('formRedeemCollateral');
  if (formRedeem) {
    formRedeem.addEventListener('submit', function (e) {
      e.preventDefault();
      withSubmitLock(formRedeem, async () => {
        let contractId = document.getElementById('rdContractId')?.value || '';
        const contractCode = (document.getElementById('rdContractCode')?.value || '').trim();

        if (!contractId && contractCode && Array.isArray(PM_DASHBOARD_STATE.contracts)) {
          const matched = PM_DASHBOARD_STATE.contracts.find(c => c.contractCode === contractCode);
          if (matched) contractId = String(matched.id);
        }

        if (contractId && window.location.protocol.startsWith('http')) {
          try {
            await apiFetch(`/api/v1/contracts/${contractId}/redeem`, {
              method: 'POST',
              body: JSON.stringify({
                notes: `Khách tất toán chuộc tài sản hợp đồng ${contractCode} tại quầy`
              })
            });

            const modalEl = document.getElementById('modalRedeemCollateral');
            if (modalEl && typeof bootstrap !== 'undefined') {
              const modal = bootstrap.Modal.getInstance(modalEl);
              if (modal) modal.hide();
            }
            showToast(`Tất toán hợp đồng ${contractCode} và xuất kho bàn giao tài sản thành công!`, 'success');
            await refreshDashboardAfterMutation(false);
            return;
          } catch (err) {
            if (err.isBranchAccessDenied) {
              showBranchAccessDeniedModal(err.message, `Hợp đồng ${contractCode}`);
              return;
            }
            showToast(err.message || 'Lỗi khi tất toán hợp đồng', 'danger');
            return;
          }
        }

        const modalEl = document.getElementById('modalRedeemCollateral');
        if (modalEl && typeof bootstrap !== 'undefined') {
          const modal = bootstrap.Modal.getInstance(modalEl);
          if (modal) modal.hide();
        }
        showToast('Tất toán hợp đồng và xuất kho bàn giao tài sản thành công!', 'success');
      });
    });
  }

  // 4. Approve Contract Form -> POST /api/v1/contracts/{id}/approve
  const formApprove = document.getElementById('formApproveContract');
  if (formApprove) {
    formApprove.addEventListener('submit', function (e) {
      e.preventDefault();
      withSubmitLock(formApprove, async () => {
        const contractId = document.getElementById('apContractId')?.value || '';
        const contractCode = (document.getElementById('apContractCodeDisplay')?.textContent || '').trim();
        const approvalNotes = (document.getElementById('apApprovalNotes')?.value || '').trim() ||
          'Quản lý phê duyệt giải ngân trên Bảng Điều Khiển';

        if (window.location.protocol.startsWith('http') && contractId) {
          try {
            await apiFetch(`/api/v1/contracts/${contractId}/approve`, {
              method: 'POST',
              body: JSON.stringify({ approvalNotes })
            });

            const modalEl = document.getElementById('modalApproveContract');
            if (modalEl && typeof bootstrap !== 'undefined') {
              const modal = bootstrap.Modal.getInstance(modalEl);
              if (modal) modal.hide();
            }
            showToast(`Đã phê duyệt và xuất quỹ giải ngân thành công cho hợp đồng ${contractCode}!`, 'success');
            await refreshDashboardAfterMutation(false);
            return;
          } catch (err) {
            if (err.isBranchAccessDenied) {
              showBranchAccessDeniedModal(err.message, `Duyệt hợp đồng ${contractCode}`);
              return;
            }
            showToast(err.message || 'Không thể phê duyệt hợp đồng', 'danger');
            return;
          }
        }

        const row = PM_DASHBOARD_STATE.pendingApproveRow;
        if (row) {
          row.setAttribute('data-status', 'ACTIVE');
          const badge = row.querySelector('.pm-badge');
          if (badge) {
            badge.className = 'pm-badge pm-badge-active';
            badge.innerHTML = '<span class="pm-badge-dot"></span><span>Đang vay</span>';
          }
        }
        const modalEl = document.getElementById('modalApproveContract');
        if (modalEl && typeof bootstrap !== 'undefined') {
          const modal = bootstrap.Modal.getInstance(modalEl);
          if (modal) modal.hide();
        }
        showToast(`Đã phê duyệt và xuất quỹ giải ngân thành công cho hợp đồng ${contractCode}!`, 'success');
      });
    });
  }

  // 5. Reject Contract Form -> POST /api/v1/contracts/{id}/reject
  const formReject = document.getElementById('formRejectContract');
  if (formReject) {
    formReject.addEventListener('submit', function (e) {
      e.preventDefault();
      withSubmitLock(formReject, async () => {
        const contractId = document.getElementById('rjContractId')?.value || '';
        const contractCode = (document.getElementById('rjContractCodeDisplay')?.textContent || '').trim();
        const rejectionReason = (document.getElementById('rjRejectionReason')?.value || '').trim();

        if (!rejectionReason) {
          showToast('Vui lòng nhập lý do từ chối hồ sơ', 'danger');
          return;
        }

        if (window.location.protocol.startsWith('http') && contractId) {
          try {
            await apiFetch(`/api/v1/contracts/${contractId}/reject`, {
              method: 'POST',
              body: JSON.stringify({ rejectionReason })
            });

            const modalEl = document.getElementById('modalRejectContract');
            if (modalEl && typeof bootstrap !== 'undefined') {
              const modal = bootstrap.Modal.getInstance(modalEl);
              if (modal) modal.hide();
            }
            showToast(`Đã từ chối hồ sơ hợp đồng ${contractCode}`, 'info');
            await refreshDashboardAfterMutation(false);
            return;
          } catch (err) {
            if (err.isBranchAccessDenied) {
              showBranchAccessDeniedModal(err.message, `Từ chối hợp đồng ${contractCode}`);
              return;
            }
            showToast(err.message || 'Không thể từ chối hợp đồng', 'danger');
            return;
          }
        }

        const row = PM_DASHBOARD_STATE.pendingRejectRow;
        if (row) {
          row.setAttribute('data-status', 'CANCELLED');
          const badge = row.querySelector('.pm-badge');
          if (badge) {
            badge.className = 'pm-badge pm-badge-overdue';
            badge.innerHTML = '<span class="pm-badge-dot"></span><span>Đã từ chối</span>';
          }
        }
        const modalEl = document.getElementById('modalRejectContract');
        if (modalEl && typeof bootstrap !== 'undefined') {
          const modal = bootstrap.Modal.getInstance(modalEl);
          if (modal) modal.hide();
        }
        showToast(`Đã từ chối hồ sơ hợp đồng ${contractCode}`, 'info');
      });
    });
  }

  // 6. Liquidate Collateral Form -> POST /api/v1/contracts/{id}/liquidate
  const formLiquidate = document.getElementById('formLiquidateCollateral');
  if (formLiquidate) {
    formLiquidate.addEventListener('submit', function (e) {
      e.preventDefault();
      withSubmitLock(formLiquidate, async () => {
        const contractId = document.getElementById('lqContractId')?.value || '';
        const contractCode = (document.getElementById('lqContractCodeDisplay')?.textContent || '').trim();
        const liqAmount = parseVND(document.getElementById('lqLiquidationAmount')?.value || 0);
        const paymentMethod = document.getElementById('lqPaymentMethod')?.value || 'CASH';
        const buyerName = (document.getElementById('lqBuyerName')?.value || '').trim();
        const notes = (document.getElementById('lqNotes')?.value || '').trim() ||
          `Thanh lý tài sản quá hạn hợp đồng ${contractCode}`;

        if (!liqAmount || liqAmount <= 0) {
          showToast('Giá trị thu thanh lý phải lớn hơn 0 ₫', 'danger');
          return;
        }

        if (window.location.protocol.startsWith('http') && contractId) {
          try {
            await apiFetch(`/api/v1/contracts/${contractId}/liquidate`, {
              method: 'POST',
              body: JSON.stringify({
                liquidationAmount: liqAmount,
                buyerName: buyerName || null,
                paymentMethod: paymentMethod,
                notes: notes
              })
            });

            const modalEl = document.getElementById('modalLiquidateCollateral');
            if (modalEl && typeof bootstrap !== 'undefined') {
              const modal = bootstrap.Modal.getInstance(modalEl);
              if (modal) modal.hide();
            }
            showToast(`Đã thanh lý tài sản hợp đồng ${contractCode} (+${formatVND(liqAmount)} vào quỹ)!`, 'success');
            await refreshDashboardAfterMutation(false);
            return;
          } catch (err) {
            if (err.isBranchAccessDenied) {
              showBranchAccessDeniedModal(err.message, `Thanh lý hợp đồng ${contractCode}`);
              return;
            }
            showToast(err.message || 'Không thể thanh lý hợp đồng', 'danger');
            return;
          }
        }

        const row = PM_DASHBOARD_STATE.pendingLiquidateRow;
        if (row) {
          row.setAttribute('data-status', 'LIQUIDATED');
          const badge = row.querySelector('.pm-badge');
          if (badge) {
            badge.className = 'pm-badge pm-badge-liquidated';
            badge.innerHTML = '<span class="pm-badge-dot"></span><span>Đã thanh lý</span>';
          }
        }
        const modalEl = document.getElementById('modalLiquidateCollateral');
        if (modalEl && typeof bootstrap !== 'undefined') {
          const modal = bootstrap.Modal.getInstance(modalEl);
          if (modal) modal.hide();
        }
        showToast(`Đã ghi nhận biên bản thanh lý hợp đồng ${contractCode}!`, 'success');
      });
    });
  }
}

/**
 * Synchronize the topbar custom branch trigger button & option selection state.
 */
function syncBranchTriggerDom(selection) {
  const trigger = document.getElementById('branchSelector');
  const nameEl = document.getElementById('branchTriggerName');
  const codeEl = document.getElementById('branchTriggerCode');
  const iconEl = document.getElementById('branchTriggerIcon');
  const listEl = document.getElementById('branchDropdownList');
  if (!trigger || !selection) return;

  const valStr = selection.value != null && selection.value !== '' ? String(selection.value) : 'ALL';
  const isSystem = valStr === 'ALL';

  trigger.setAttribute('data-selected-value', valStr);
  if (nameEl) {
    nameEl.textContent = selection.name || (isSystem ? 'Toàn hệ thống' : `Chi nhánh #${valStr}`);
  }
  if (codeEl) {
    codeEl.textContent = selection.code || (isSystem ? 'ALL-CHAIN' : `CN-${valStr}`);
  }
  if (iconEl) {
    iconEl.className = isSystem ? 'bi bi-buildings-fill' : 'bi bi-geo-alt-fill';
  }

  if (listEl) {
    listEl.querySelectorAll('.pm-branch-option').forEach(opt => {
      const matches = String(opt.getAttribute('data-branch-value') || '') === valStr;
      opt.classList.toggle('is-selected', matches);
      opt.setAttribute('aria-selected', matches ? 'true' : 'false');
    });
  }

  // Sync primary Contract Origination workspace CTA (#btnOpenFullContractPage) with active branch context
  const openFullBtn = document.getElementById('btnOpenFullContractPage');
  if (openFullBtn) {
    const isHttp = Boolean(
      typeof window !== 'undefined' &&
      window.location &&
      window.location.protocol &&
      window.location.protocol.startsWith('http')
    );
    const baseHref = isHttp ? '/contracts/new' : 'contract-create.html';
    openFullBtn.setAttribute('href', isSystem ? baseHref : `${baseHref}?branchId=${encodeURIComponent(valStr)}`);
  }
}

/**
 * Render authorized branch options into #branchDropdownList.
 * Single-branch users strictly receive ONLY their 1 assigned branch in the DOM (no [Khóa] items, no CSS hiding).
 */
function renderBranchDropdownOptions(branches, selectedBranchId, canSwitchBranch, userAssignedBranchId) {
  const listEl = document.getElementById('branchDropdownList');
  const scopeBadgeEl = document.getElementById('branchDropdownScopeBadge');
  const footerTextEl = document.getElementById('branchDropdownFooterText');

  const safeBranches = Array.isArray(branches) ? branches : [];
  let allowedBranches = safeBranches;
  if (!canSwitchBranch) {
    const targetId = userAssignedBranchId != null ? Number(userAssignedBranchId)
      : (selectedBranchId != null ? Number(selectedBranchId) : null);
    if (targetId != null) {
      const matched = safeBranches.filter(b => b && Number(b.id) === targetId);
      allowedBranches = matched.length > 0 ? [matched[0]] : (safeBranches.length === 1 ? [safeBranches[0]] : []);
    } else if (safeBranches.length > 0) {
      allowedBranches = [safeBranches[0]];
    }
  }

  if (scopeBadgeEl) {
    scopeBadgeEl.textContent = canSwitchBranch ? 'Toàn chuỗi' : 'Chi nhánh cố định';
  }
  if (footerTextEl) {
    footerTextEl.textContent = canSwitchBranch
      ? 'Quyền Chủ hệ thống: Cho phép giám sát liên chi nhánh'
      : 'Phân quyền cố định: Chỉ hiển thị chi nhánh trực thuộc ca trực';
  }

  if (listEl && allowedBranches.length > 0) {
    const itemsHtml = [];
    if (canSwitchBranch) {
      const totalSystemCash = allowedBranches.reduce((sum, b) => sum + Number(b.currentCashBalance || 0), 0);
      const totalSystemCapital = allowedBranches.reduce((sum, b) => sum + Number(b.capitalLimit || 0), 0);
      const allSelected = selectedBranchId == null;
      itemsHtml.push(`
        <button type="button"
                role="option"
                class="pm-branch-option pm-branch-option-system${allSelected ? ' is-selected' : ''}"
                aria-selected="${allSelected ? 'true' : 'false'}"
                data-branch-value="ALL"
                data-cash="${totalSystemCash}"
                data-capital="${totalSystemCapital}"
                data-code="ALL-CHAIN"
                data-name="Toàn hệ thống">
          <span class="pm-branch-opt-icon pm-branch-opt-icon-system" aria-hidden="true">
            <i class="bi bi-buildings-fill"></i>
          </span>
          <span class="pm-branch-opt-body">
            <span class="pm-branch-opt-row1">
              <span class="pm-branch-opt-name">Toàn hệ thống</span>
              <span class="pm-branch-opt-code font-tabular font-mono">ALL-CHAIN</span>
            </span>
            <span class="pm-branch-opt-meta font-tabular">
              <span class="pm-branch-opt-cash">Tổng quỹ: ${formatVND(totalSystemCash)}</span>
              ${totalSystemCapital > 0 ? `<span class="pm-branch-opt-sep" aria-hidden="true">•</span><span>Vốn chuỗi: ${formatCompactBillionVND(totalSystemCapital)}</span>` : ''}
            </span>
          </span>
          <i class="bi bi-check-circle-fill pm-branch-opt-check" aria-hidden="true"></i>
        </button>
      `);
    }

    allowedBranches.forEach(b => {
      const isSelected = !canSwitchBranch ||
        (selectedBranchId != null && Number(selectedBranchId) === Number(b.id));
      const cashVal = Number(b.currentCashBalance || 0);
      const capVal = Number(b.capitalLimit || 0);
      itemsHtml.push(`
        <button type="button"
                role="option"
                class="pm-branch-option${isSelected ? ' is-selected' : ''}"
                aria-selected="${isSelected ? 'true' : 'false'}"
                data-branch-value="${b.id}"
                data-cash="${cashVal}"
                data-capital="${capVal}"
                data-code="${escapeHtml(b.branchCode || '')}"
                data-name="${escapeHtml(b.branchName || '')}">
          <span class="pm-branch-opt-icon" aria-hidden="true">
            <i class="bi bi-geo-alt-fill"></i>
          </span>
          <span class="pm-branch-opt-body">
            <span class="pm-branch-opt-row1">
              <span class="pm-branch-opt-name">${escapeHtml(b.branchName || '')}</span>
              <span class="pm-branch-opt-code font-tabular font-mono">${escapeHtml(b.branchCode || '')}</span>
            </span>
            <span class="pm-branch-opt-meta font-tabular">
              <span class="pm-branch-opt-cash">Quỹ: ${formatVND(cashVal)}</span>
              ${capVal > 0 ? `<span class="pm-branch-opt-sep" aria-hidden="true">•</span><span>Hạn mức: ${formatCompactBillionVND(capVal)}</span>` : ''}
            </span>
          </span>
          <i class="bi bi-check-circle-fill pm-branch-opt-check" aria-hidden="true"></i>
        </button>
      `);
    });

    listEl.innerHTML = itemsHtml.join('');
  }

  // Sync Trigger Display
  if (canSwitchBranch && selectedBranchId == null) {
    syncBranchTriggerDom({ value: 'ALL', name: 'Toàn hệ thống', code: 'ALL-CHAIN' });
  } else {
    const activeBranchObj = allowedBranches.find(b => selectedBranchId != null && Number(b.id) === Number(selectedBranchId)) ||
      allowedBranches[0];
    if (activeBranchObj) {
      syncBranchTriggerDom({
        value: activeBranchObj.id,
        name: activeBranchObj.branchName,
        code: activeBranchObj.branchCode
      });
    }
  }
}

/**
 * Branch Context Custom Popover Selector Handler with Keyboard Accessibility & Strict Backend Authorization Check
 */
function initBranchSelector() {
  const container = document.getElementById('topbarBranchPill');
  const trigger = document.getElementById('branchSelector');
  const menu = document.getElementById('branchDropdownMenu');
  const list = document.getElementById('branchDropdownList');
  if (!container || !trigger || !menu || !list) return;

  const getOptionButtons = () => Array.from(list.querySelectorAll('.pm-branch-option'));

  const closeMenu = (restoreFocus = false) => {
    if (menu.hidden) return;
    menu.hidden = true;
    trigger.setAttribute('aria-expanded', 'false');
    if (restoreFocus) {
      trigger.focus();
    }
  };

  const openMenu = () => {
    menu.hidden = false;
    trigger.setAttribute('aria-expanded', 'true');
    const selectedOpt = list.querySelector('.pm-branch-option.is-selected') || list.querySelector('.pm-branch-option');
    if (selectedOpt) {
      requestAnimationFrame(() => selectedOpt.focus());
    }
  };

  trigger.addEventListener('click', function (e) {
    e.preventDefault();
    e.stopPropagation();
    if (menu.hidden) {
      openMenu();
    } else {
      closeMenu(false);
    }
  });

  trigger.addEventListener('keydown', function (e) {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      openMenu();
    } else if (e.key === 'Escape') {
      closeMenu(false);
    }
  });

  menu.addEventListener('keydown', function (e) {
    const opts = getOptionButtons();
    if (opts.length === 0) return;
    const currentIdx = opts.indexOf(document.activeElement);

    if (e.key === 'Escape') {
      e.preventDefault();
      closeMenu(true);
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      const nextIdx = currentIdx >= 0 ? (currentIdx + 1) % opts.length : 0;
      opts[nextIdx].focus();
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      const prevIdx = currentIdx > 0 ? currentIdx - 1 : opts.length - 1;
      opts[prevIdx].focus();
    } else if (e.key === 'Home') {
      e.preventDefault();
      opts[0].focus();
    } else if (e.key === 'End') {
      e.preventDefault();
      opts[opts.length - 1].focus();
    } else if (e.key === 'Tab') {
      closeMenu(false);
    }
  });

  document.addEventListener('click', function (e) {
    if (!menu.hidden && !container.contains(e.target)) {
      closeMenu(false);
    }
  });

  list.addEventListener('click', async function (e) {
    const optBtn = e.target.closest('.pm-branch-option');
    if (!optBtn) return;
    e.preventDefault();

    const rawValue = optBtn.getAttribute('data-branch-value') || '';
    const optName = optBtn.getAttribute('data-name') || '';
    const optCode = optBtn.getAttribute('data-code') || '';
    const currentSelectedValue = trigger.getAttribute('data-selected-value') || '';

    closeMenu(true);

    const isUnchangedAuthorizedSelection =
      rawValue === currentSelectedValue &&
      (rawValue === 'ALL'
        ? getRoleCapabilities().canSwitchBranch
        : Number(rawValue) === Number(PM_DASHBOARD_STATE.activeBranchId || PM_DASHBOARD_STATE.userAssignedBranchId || rawValue));

    if (isUnchangedAuthorizedSelection) {
      return;
    }

    const targetBranchId = rawValue === 'ALL' || rawValue === '' ? null : Number(rawValue);
    PM_DASHBOARD_STATE.tablePage = 0;

    const attemptedLabel = optName
      ? (optCode && optCode !== 'ALL-CHAIN' ? `${optName} (${optCode})` : optName)
      : (rawValue === 'ALL' ? 'Toàn hệ thống' : `Chi nhánh #${rawValue}`);

    const previousSelection = {
      value: PM_DASHBOARD_STATE.activeBranchId != null
        ? String(PM_DASHBOARD_STATE.activeBranchId)
        : (PM_DASHBOARD_STATE.canSwitchBranch
          ? 'ALL'
          : (PM_DASHBOARD_STATE.userAssignedBranchId != null ? String(PM_DASHBOARD_STATE.userAssignedBranchId) : currentSelectedValue || '1')),
      name: PM_DASHBOARD_STATE.userAssignedBranchName || document.getElementById('branchTriggerName')?.textContent || 'Chi nhánh hiện tại',
      code: PM_DASHBOARD_STATE.userAssignedBranchCode || document.getElementById('branchTriggerCode')?.textContent || 'CN'
    };

    if (window.location.protocol.startsWith('http')) {
      try {
        await loadDashboardDataFromDb(targetBranchId, true, PM_DASHBOARD_STATE.activeTimeframe);
        if (PM_DASHBOARD_STATE.isServerPaginated &&
            ((PM_DASHBOARD_STATE.activeTabFilter && PM_DASHBOARD_STATE.activeTabFilter !== 'ALL') ||
             (PM_DASHBOARD_STATE.activeSearchKeyword && PM_DASHBOARD_STATE.activeSearchKeyword.trim().length > 0))) {
          await fetchDashboardContractPage(0);
        }
        if (window.history && typeof window.history.replaceState === 'function') {
          const q = [];
          if (targetBranchId != null) q.push(`branchId=${encodeURIComponent(targetBranchId)}`);
          if (PM_DASHBOARD_STATE.activeTimeframe) q.push(`timeframe=${encodeURIComponent(PM_DASHBOARD_STATE.activeTimeframe)}`);
          const nextUrl = q.length > 0 ? `/dashboard?${q.join('&')}` : '/dashboard';
          window.history.replaceState({}, '', nextUrl);
        }
        showToast(`Đã tải dữ liệu: ${attemptedLabel}`, 'info');
      } catch (err) {
        syncBranchTriggerDom(previousSelection);
        if (err.isBranchAccessDenied || err.status === 403 || err.code === 4004) {
          showBranchAccessDeniedModal(err.message, attemptedLabel);
        } else {
          showToast(err.message || 'Không thể chuyển chi nhánh', 'danger');
        }
      }
    } else {
      syncBranchTriggerDom({
        value: rawValue || '1',
        name: optName || 'Chi nhánh mới',
        code: optCode || 'CN'
      });
      const cash = optBtn.getAttribute('data-cash');
      const topbarCash = document.getElementById('topbarCashBalance');
      if (cash && topbarCash) {
        topbarCash.textContent = formatVND(cash);
      }
      showToast(`Đã chuyển sang: ${attemptedLabel}`, 'info');
    }
  });
}

/**
 * Manager Maker-Checker Triggers (Opens Dedicated Vault Ledger Modals #modalApproveContract & #modalRejectContract)
 */
function initManagerActions() {
  document.querySelectorAll('.btn-action-approve').forEach(btn => {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      const contractId = this.getAttribute('data-contract-id') || '';
      const contractCode = this.getAttribute('data-contract-code') || ('HD-2026-0' + contractId);
      const dbContract = findContractInState(contractId, contractCode);

      const customerName = this.getAttribute('data-customer-name') || (dbContract && dbContract.customerName) || 'Khách hàng';
      const collateralName = this.getAttribute('data-collateral-name') || (dbContract && dbContract.collateralName) || 'Tài sản niêm phong';
      const principalVal = Number(this.getAttribute('data-principal') || (dbContract && dbContract.principalAmount) || 0);
      const appraisedVal = Number(this.getAttribute('data-appraised-value') || (dbContract && dbContract.collateralAppraisedValue) || 0);
      const ltvVal = this.getAttribute('data-ltv') ||
        (dbContract && dbContract.appliedLtvPercent != null
          ? Number(dbContract.appliedLtvPercent).toFixed(1)
          : (appraisedVal > 0 ? ((principalVal / appraisedVal) * 100).toFixed(1) : '75.0'));
      const disbursementMethod = this.getAttribute('data-disbursement-method') || (dbContract && dbContract.disbursementMethod) || 'CASH';
      const disbursementBankCode = this.getAttribute('data-bank-code') || (dbContract && dbContract.disbursementBankCode) || '';
      const disbursementBankName = this.getAttribute('data-bank-name') || (dbContract && dbContract.disbursementBankName) || '';
      const disbursementAccountNumber = this.getAttribute('data-bank-account') || (dbContract && dbContract.disbursementAccountNumber) || '';
      const disbursementAccountHolder = this.getAttribute('data-bank-holder') || (dbContract && dbContract.disbursementAccountHolder) || '';
      const startDate = this.getAttribute('data-start-date') || (dbContract && dbContract.startDate) || '';
      const loanTermDays = Number(this.getAttribute('data-loan-term-days') || (dbContract && dbContract.loanTermDays) || 30) || 30;
      const paymentCycleDays = Number(this.getAttribute('data-payment-cycle-days') || (dbContract && dbContract.paymentCycleDays) || 10) || 10;

      populateApproveContractModal({
        contractId,
        contractCode,
        customerName,
        collateralName,
        principalVal,
        appraisedVal,
        ltvVal,
        disbursementMethod,
        disbursementBankCode,
        disbursementBankName,
        disbursementAccountNumber,
        disbursementAccountHolder,
        startDate,
        loanTermDays,
        paymentCycleDays
      }, this.closest('tr'));

      const modalEl = document.getElementById('modalApproveContract');
      if (modalEl && typeof bootstrap !== 'undefined') {
        const modal = bootstrap.Modal.getInstance(modalEl) || new bootstrap.Modal(modalEl);
        modal.show();
      }
    });
  });

  document.querySelectorAll('.btn-action-reject').forEach(btn => {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      const contractId = this.getAttribute('data-contract-id') || '';
      const contractCode = this.getAttribute('data-contract-code') || ('HD-2026-0' + contractId);
      const dbContract = findContractInState(contractId, contractCode);

      const customerName = this.getAttribute('data-customer-name') || (dbContract && dbContract.customerName) || 'Khách hàng';
      const collateralName = this.getAttribute('data-collateral-name') || (dbContract && dbContract.collateralName) || 'Tài sản cầm cố';
      const principalVal = Number(this.getAttribute('data-principal') || (dbContract && dbContract.principalAmount) || 0);

      PM_DASHBOARD_STATE.pendingRejectRow = this.closest('tr');

      const idEl = document.getElementById('rjContractId');
      const codeEl = document.getElementById('rjContractCodeDisplay');
      const custEl = document.getElementById('rjCustomerDisplay');
      const sumEl = document.getElementById('rjLoanSummaryDisplay');

      if (idEl) idEl.value = contractId;
      if (codeEl) codeEl.textContent = contractCode;
      if (custEl) custEl.textContent = customerName;
      if (sumEl) sumEl.textContent = `${formatVND(principalVal)} • ${collateralName}`;

      const modalEl = document.getElementById('modalRejectContract');
      if (modalEl && typeof bootstrap !== 'undefined') {
        const modal = bootstrap.Modal.getInstance(modalEl) || new bootstrap.Modal(modalEl);
        modal.show();
      }
    });
  });
}

/**
 * Render Live Database Summary into Topbar, 4 KPI Cards, 2 Charts, Filter Badges & 7-Column Table
 */
function applyDashboardSummaryToDom(summary) {
  if (!summary) return;

  PM_DASHBOARD_STATE.activeBranchId = summary.selectedBranchId;
  if (summary.selectedTimeframe && DASHBOARD_TIMEFRAME_CONFIG[summary.selectedTimeframe]) {
    PM_DASHBOARD_STATE.activeTimeframe = summary.selectedTimeframe;
  }
  if (summary.userRole) {
    PM_DASHBOARD_STATE.userRole = String(summary.userRole).trim().toUpperCase();
  }
  PM_DASHBOARD_STATE.userAssignedBranchId = summary.userAssignedBranchId;
  PM_DASHBOARD_STATE.userAssignedBranchCode = summary.userAssignedBranchCode;
  PM_DASHBOARD_STATE.userAssignedBranchName = summary.userAssignedBranchName;
  PM_DASHBOARD_STATE.canSwitchBranch = Boolean(summary.canSwitchBranch);
  PM_DASHBOARD_STATE.branches = summary.branches || [];
  PM_DASHBOARD_STATE.contracts = summary.contracts || [];
  PM_DASHBOARD_STATE.cashFlowCharts = summary.cashFlowCharts || null;
  PM_DASHBOARD_STATE.collateralDistribution = summary.collateralDistribution || null;

  // Enforce strict DOM removal for unauthorized controls
  pruneUnauthorizedDomActions();

  // Sync Global Timeframe Strip & Period Badges
  const activeTf = PM_DASHBOARD_STATE.activeTimeframe || 'THIS_MONTH';
  syncTimeframeBadgesToDom(activeTf, summary.timeframeRangeLabel || null);

  // 1. Update Topbar Cash Balance
  const topbarCash = document.getElementById('topbarCashBalance');
  if (topbarCash && summary.currentCashBalance != null) {
    topbarCash.textContent = formatVND(summary.currentCashBalance);
  }

  // 2. Populate custom Vault Ledger branch popover (1 branch only for single-branch users)
  if (Array.isArray(summary.branches) && summary.branches.length > 0) {
    renderBranchDropdownOptions(
      summary.branches,
      summary.selectedBranchId,
      Boolean(summary.canSwitchBranch),
      summary.userAssignedBranchId
    );
  }

  // 3. Update 4 KPI Cards
  if (summary.kpi) {
    const kpi = summary.kpi;
    const elActiveVal = document.getElementById('kpiActiveContractsValue');
    const elActiveSub = document.getElementById('kpiActivePrincipalSubtext');
    const elActiveTrend = document.getElementById('kpiActiveTrendText');
    if (elActiveVal) elActiveVal.textContent = String(kpi.activeContractsCount || 0);
    if (elActiveSub) {
      elActiveSub.innerHTML = `Dư nợ gốc: <strong class="font-tabular">${formatVND(kpi.disbursedPrincipalAmount || 0)}</strong>`;
    }
    if (elActiveTrend) {
      elActiveTrend.textContent = kpi.pendingApprovalCount > 0
        ? `${kpi.pendingApprovalCount} HĐ đang chờ duyệt giải ngân`
        : 'Tất cả hồ sơ đã được xử lý';
    }

    const elDisbursedVal = document.getElementById('kpiTotalDisbursedValue');
    const elCapUtilSub = document.getElementById('kpiCapitalUtilizationSubtext');
    const elDisbursedTrend = document.getElementById('kpiDisbursedTrendText');
    const capBillionStr = kpi.capitalLimitAmount
      ? (Number(kpi.capitalLimitAmount) / 1000000000).toFixed(1) + ' Tỷ'
      : '2.5 Tỷ';
    if (elDisbursedVal) elDisbursedVal.textContent = formatVND(kpi.disbursedPrincipalAmount || 0);
    if (elCapUtilSub) {
      elCapUtilSub.innerHTML = `Hạn mức vốn sử dụng: <strong class="font-tabular">${kpi.capitalUtilizationPercent || 0}% / ${capBillionStr}</strong>`;
    }
    if (elDisbursedTrend) {
      elDisbursedTrend.textContent = `Quỹ khả dụng: ${formatVND(kpi.currentCashBalance || 0)}`;
    }

    const elInterestVal = document.getElementById('kpiAccruedInterestValue');
    const elInterestSub = document.getElementById('kpiCollectedInterestSubtext');
    const elInterestTrend = document.getElementById('kpiInterestProgressText');
    if (elInterestVal) elInterestVal.textContent = formatVND(kpi.expectedInterestAmount || 0);
    if (elInterestSub) {
      elInterestSub.innerHTML = `Đã thu: <strong class="font-tabular">${formatVND(kpi.collectedInterestAmount || 0)}</strong> (${kpi.interestCollectionPercent || 0}%)`;
    }
    if (elInterestTrend) {
      elInterestTrend.textContent = `Tiến độ thu ${kpi.interestCollectionPercent || 0}%`;
    }

    const elOverdueVal = document.getElementById('kpiOverdueContractsValue');
    const elOverdueSub = document.getElementById('kpiAtRiskPrincipalSubtext');
    const elOverdueTrend = document.getElementById('kpiOverdueBreakdownText');
    if (elOverdueVal) elOverdueVal.textContent = `${kpi.overdueContractsCount || 0} HĐ`;
    if (elOverdueSub) {
      elOverdueSub.innerHTML = `Dư nợ rủi ro: <strong class="font-tabular text-danger">${formatVND(kpi.atRiskPrincipalAmount || 0)}</strong>`;
    }
    if (elOverdueTrend) {
      elOverdueTrend.textContent = `${kpi.overdueOnlyCount || 0} trễ lãi • ${kpi.waitingLiquidationCount || 0} chờ thanh lý`;
    }
  }

  // 4. Update Cash Flow Chart for active global timeframe
  updateCashFlowChartForTimeframe(activeTf);

  // 5. Update Collateral Donut Chart & Accessible Legend
  if (Array.isArray(summary.collateralDistribution) && summary.collateralDistribution.length > 0) {
    const totalActive = summary.collateralDistribution.reduce((acc, item) => acc + Number(item.contractCount || 0), 0);
    const donutBadge = document.getElementById('donutTotalContractsBadge');
    if (donutBadge) donutBadge.textContent = `${totalActive} HĐ`;

    const shortLabels = {
      MOTORBIKE: 'Xe máy',
      CAR: 'Ô tô',
      GOLD_JEWELRY: 'Vàng/Trang sức',
      PHONE_LAPTOP: 'Điện thoại/Laptop',
      WATCH_OTHER: 'Khác'
    };

    summary.collateralDistribution.forEach(item => {
      const rowEl = document.querySelector(`#collateralDonutLegend [data-category-group="${item.groupKey}"]`);
      if (rowEl) {
        const textEl = rowEl.querySelector('.pm-legend-text');
        const valEl = rowEl.querySelector('.pm-legend-val');
        const baseName = shortLabels[item.groupKey] || item.label;
        if (textEl) textEl.textContent = `${baseName} (${item.percentage || 0}%)`;
        if (valEl) valEl.textContent = `${item.contractCount || 0} HĐ`;
      }
    });

    if (collateralDonutChartInstance) {
      collateralDonutChartInstance.data.datasets[0].data = summary.collateralDistribution.map(i => Number(i.percentage || 0));
      collateralDonutChartInstance.update();
    }
  }

  // 6. Update Filter Tab Counters
  if (summary.tabCounts) {
    Object.keys(summary.tabCounts).forEach(key => {
      const badge = document.querySelector(`#contractFilterTabs [data-badge-for="${key}"]`);
      if (badge) badge.textContent = String(summary.tabCounts[key] || 0);
    });
  }

  // 7. Render Period Contracts into #recentContractsTable (including empty array [] to clear stale rows)
  if (summary.contractPage) {
    PM_DASHBOARD_STATE.isServerPaginated = true;
    PM_DASHBOARD_STATE.contractPage = summary.contractPage;
    PM_DASHBOARD_STATE.tablePage = summary.contractPage.page;
    PM_DASHBOARD_STATE.tableTotalPages = summary.contractPage.totalPages;
    PM_DASHBOARD_STATE.tableTotalElements = summary.contractPage.totalElements;
    PM_DASHBOARD_STATE.tablePageSize = summary.contractPage.size;
    PM_DASHBOARD_STATE.contracts = summary.contractPage.items || summary.contracts || [];

    renderContractsTableRows(PM_DASHBOARD_STATE.contracts);
    updateTablePaginationDom(
      summary.contractPage.totalElements,
      summary.contractPage.totalPages,
      summary.contractPage.page,
      summary.contractPage.size
    );
  } else {
    PM_DASHBOARD_STATE.isServerPaginated = false;
    if (Array.isArray(summary.contracts)) {
      renderContractsTableRows(summary.contracts);
    }
  }
}

/**
 * Render 7-column contract rows from Database into #recentContractsTable
 */
function renderContractsTableRows(contracts) {
  const table = document.getElementById('recentContractsTable');
  if (!table) return;
  const tbody = table.querySelector('tbody');
  if (!tbody) return;

  const emptyStateRow = document.getElementById('tableEmptyState');
  const emptyStateHtml = emptyStateRow ? emptyStateRow.outerHTML : '';

  const categoryIconMap = {
    MOTORBIKE: 'bi-scooter',
    CAR: 'bi-car-front-fill',
    GOLD_JEWELRY: 'bi-gem',
    PHONE: 'bi-phone',
    LAPTOP: 'bi-laptop',
    WATCH: 'bi-smartwatch',
    REAL_ESTATE: 'bi-house-door-fill',
    OTHER: 'bi-box-seam'
  };

  const rowsHtml = contracts.map(c => {
    const isWaitingLiq = c.collateralStatus === 'WAITING_LIQUIDATION';
    const effectiveFilterStatus = isWaitingLiq ? 'WAITING_LIQUIDATION' : (c.status || 'ACTIVE');
    const dueTodayAttr = c.dueToday ? 'true' : 'false';
    const iconClass = categoryIconMap[c.collateralCategory] || 'bi-box-seam';

    const subIdentifier = c.collateralLicensePlate
      ? `BKS: ${c.collateralLicensePlate}`
      : (c.collateralSerialOrFrame ? `S/N: ${c.collateralSerialOrFrame}` : (c.collateralCode || 'Đã niêm phong'));
    const vaultLoc = c.collateralStorageLocation ? ` • ${c.collateralStorageLocation}` : '';

    const principalVal = c.remainingPrincipal != null && Number(c.remainingPrincipal) > 0
      ? Number(c.remainingPrincipal)
      : Number(c.principalAmount || 0);
    const cycleInterestVal = Number(c.cycleInterestAmount || 0);
    const appraisedVal = Number(c.collateralAppraisedValue || 0);

    // Status badge
    let badgeClass = 'pm-badge-active';
    let badgeLabel = 'Đang cầm';
    if (c.status === 'PENDING_APPROVAL') {
      badgeClass = 'pm-badge-pending';
      badgeLabel = 'Chờ duyệt';
    } else if (isWaitingLiq) {
      badgeClass = 'pm-badge-liquidation';
      badgeLabel = 'Chờ thanh lý';
    } else if (c.status === 'OVERDUE') {
      badgeClass = 'pm-badge-overdue';
      badgeLabel = 'Quá hạn';
    } else if (c.status === 'EXTENDED') {
      badgeClass = 'pm-badge-extended';
      badgeLabel = 'Đã gia hạn';
    } else if (c.status === 'REDEEMED') {
      badgeClass = 'pm-badge-redeemed';
      badgeLabel = 'Đã chuộc';
    } else if (c.status === 'LIQUIDATED') {
      badgeClass = 'pm-badge-liquidated';
      badgeLabel = 'Đã thanh lý';
    } else if (c.status === 'CANCELLED') {
      badgeClass = 'pm-badge-overdue';
      badgeLabel = 'Đã từ chối';
    }

    // Due date & relative badge
    const dueDateStr = c.nextInterestDueDate ? formatIsoDateToVN(c.nextInterestDueDate) : '—';
    let dueSubBadge = '';
    if (c.status === 'REDEEMED' || c.status === 'LIQUIDATED' || c.status === 'CANCELLED') {
      dueSubBadge = `<span class="pm-due-badge pm-due-future">Đã đóng hồ sơ</span>`;
    } else if (c.dueToday) {
      dueSubBadge = `<span class="pm-due-badge pm-due-today"><i class="bi bi-alarm"></i> Hôm nay</span>`;
    } else if (c.daysUntilDue != null && c.daysUntilDue < 0) {
      dueSubBadge = `<span class="pm-due-badge pm-due-overdue">Trễ ${Math.abs(c.daysUntilDue)} ngày</span>`;
    } else if (c.daysUntilDue != null) {
      dueSubBadge = `<span class="pm-due-badge pm-due-future">Còn ${c.daysUntilDue} ngày</span>`;
    }

    const branchIdAttr = c.branchId != null ? String(c.branchId) : '';
    const detailBtn = `<button type="button" class="pm-btn-icon btn-action-detail" title="Xem hồ sơ chi tiết" aria-label="Xem chi tiết hợp đồng ${escapeHtml(c.contractCode)}" data-contract-id="${c.id}" data-contract-code="${escapeHtml(c.contractCode)}" data-branch-id="${escapeHtml(branchIdAttr)}" data-status="${effectiveFilterStatus}">
      <i class="bi bi-eye text-secondary"></i>
    </button>`;

    // Action buttons by status & strict RBAC (Unauthorized buttons are NEVER rendered into DOM)
    const caps = getRoleCapabilities();
    const canApproveThisRow = canCurrentUserApproveContract(c.status, c.branchId);
    let actionsHtml = '';
    if (c.status === 'PENDING_APPROVAL') {
      const approveRejectBtns = canApproveThisRow
        ? `<button type="button" class="pm-btn pm-btn-sm pm-btn-success-tonal btn-action-approve" title="Phê duyệt &amp; Giải ngân" aria-label="Phê duyệt hợp đồng ${escapeHtml(c.contractCode)}" data-contract-id="${c.id}" data-contract-code="${escapeHtml(c.contractCode)}" data-customer-name="${escapeHtml(c.customerName || '')}" data-collateral-name="${escapeHtml(c.collateralName || '')}" data-appraised-value="${appraisedVal}" data-principal="${principalVal}" data-disbursement-method="${escapeHtml(c.disbursementMethod || 'CASH')}" data-bank-code="${escapeHtml(c.disbursementBankCode || '')}" data-bank-name="${escapeHtml(c.disbursementBankName || '')}" data-bank-account="${escapeHtml(c.disbursementAccountNumber || '')}" data-bank-holder="${escapeHtml(c.disbursementAccountHolder || '')}" data-start-date="${escapeHtml(c.startDate || '')}" data-loan-term-days="${c.loanTermDays != null ? c.loanTermDays : 30}" data-payment-cycle-days="${c.paymentCycleDays != null ? c.paymentCycleDays : 10}">
            <i class="bi bi-check-lg"></i>
            <span>Duyệt</span>
          </button>
          <button type="button" class="pm-btn pm-btn-sm pm-btn-danger-tonal btn-action-reject" title="Từ chối hồ sơ" aria-label="Từ chối hợp đồng ${escapeHtml(c.contractCode)}" data-contract-id="${c.id}" data-contract-code="${escapeHtml(c.contractCode)}" data-customer-name="${escapeHtml(c.customerName || '')}" data-collateral-name="${escapeHtml(c.collateralName || '')}" data-principal="${principalVal}">
            <i class="bi bi-x-lg"></i>
            <span>Từ chối</span>
          </button>`
        : '';
      actionsHtml = `
        <div class="d-inline-flex gap-1 align-items-center">
          ${approveRejectBtns}
          ${detailBtn}
        </div>
      `;
    } else if (c.status === 'ACTIVE' || c.status === 'EXTENDED' || c.status === 'OVERDUE') {
      const counterBtns = caps.canOperateCounter
        ? `<button type="button" class="pm-btn-icon btn-action-collect-interest" title="Thu lãi định kỳ" aria-label="Thu lãi hợp đồng ${escapeHtml(c.contractCode)}" data-contract-id="${c.id}" data-contract-code="${escapeHtml(c.contractCode)}" data-customer-name="${escapeHtml(c.customerName || '')}" data-due-amount="${cycleInterestVal}" data-principal="${principalVal}">
            <i class="bi bi-receipt text-success"></i>
          </button>
          <button type="button" class="pm-btn-icon btn-action-redeem" title="Chuộc đồ tất toán" aria-label="Chuộc đồ hợp đồng ${escapeHtml(c.contractCode)}" data-contract-id="${c.id}" data-contract-code="${escapeHtml(c.contractCode)}" data-customer-name="${escapeHtml(c.customerName || '')}" data-principal="${principalVal}" data-accrued-interest="${cycleInterestVal}">
            <i class="bi bi-box-arrow-right text-primary"></i>
          </button>`
        : '';
      const liqBtn = (isWaitingLiq && caps.canApproveOrLiquidate)
        ? `<button type="button" class="pm-btn-icon btn-action-liquidate" title="Lập biên bản thanh lý tài sản" aria-label="Thanh lý hợp đồng ${escapeHtml(c.contractCode)}" data-contract-id="${c.id}" data-contract-code="${escapeHtml(c.contractCode)}" data-customer-name="${escapeHtml(c.customerName || '')}" data-collateral-name="${escapeHtml(c.collateralName || '')}" data-appraised-value="${appraisedVal}" data-principal="${principalVal}" data-accrued-interest="${cycleInterestVal}">
             <i class="bi bi-hammer text-warning"></i>
           </button>`
        : '';
      actionsHtml = `
        <div class="d-inline-flex gap-1">
          ${counterBtns}
          ${liqBtn}
          ${detailBtn}
        </div>
      `;
    } else {
      actionsHtml = `
        <div class="d-inline-flex gap-1">
          <button type="button" class="pm-btn-icon btn-action-detail" title="Xem biên bản đóng hồ sơ" aria-label="Xem biên bản hợp đồng ${escapeHtml(c.contractCode)}" data-contract-id="${c.id}" data-contract-code="${escapeHtml(c.contractCode)}" data-branch-id="${escapeHtml(branchIdAttr)}" data-status="${effectiveFilterStatus}">
            <i class="bi bi-file-earmark-check text-success"></i>
          </button>
        </div>
      `;
    }

    return `
      <tr data-contract-id="${c.id}" data-contract-code="${escapeHtml(c.contractCode)}" data-branch-id="${escapeHtml(branchIdAttr)}" data-status="${effectiveFilterStatus}" data-due-today="${dueTodayAttr}">
        <td>
          <span class="pm-contract-code font-tabular font-mono fw-semibold text-primary">${escapeHtml(c.contractCode)}</span>
          <div class="pm-customer-subtext font-tabular">${escapeHtml(c.branchCode || '')}</div>
        </td>
        <td>
          <div class="pm-customer-name text-truncate">${escapeHtml(c.customerName || 'Khách hàng')}</div>
          <div class="pm-customer-subtext font-tabular">${escapeHtml(c.customerPhone || '')}${c.customerIdentityNumber ? ' • CCCD: ' + escapeHtml(c.customerIdentityNumber) : ''}</div>
        </td>
        <td>
          <div class="pm-collateral-cell">
            <div class="pm-collateral-icon"><i class="bi ${iconClass}"></i></div>
            <div>
              <div class="fw-semibold">${escapeHtml(c.collateralName || 'Tài sản cầm cố')}</div>
              <div class="pm-customer-subtext font-tabular">${escapeHtml(subIdentifier)}${escapeHtml(vaultLoc)}</div>
            </div>
          </div>
        </td>
        <td class="text-end">
          <div class="pm-currency font-tabular fw-bold text-dark">${formatVND(principalVal)}</div>
          <div class="pm-customer-subtext font-tabular">Lãi kỳ: ${formatVND(cycleInterestVal)}</div>
        </td>
        <td class="text-center">
          <span class="pm-badge ${badgeClass}">
            <span class="pm-badge-dot"></span>
            <span>${badgeLabel}</span>
          </span>
        </td>
        <td>
          <div class="pm-date font-tabular fw-semibold"${c.nextInterestDueDate ? ` data-iso-date="${escapeHtml(String(c.nextInterestDueDate).slice(0, 10))}"` : ''}>${dueDateStr}</div>
          ${dueSubBadge}
        </td>
        <td class="text-end">
          ${actionsHtml}
        </td>
      </tr>
    `;
  }).join('');

  tbody.innerHTML = rowsHtml + emptyStateHtml;

  const badgeCount = document.getElementById('tableRecordCountBadge') || document.getElementById('contractRecordCount');
  if (badgeCount && !PM_DASHBOARD_STATE.isServerPaginated) {
    badgeCount.textContent = `${contracts.length} HĐ`;
  }

  // Re-bind row action buttons & manager approve/reject handlers
  initRowActionTriggers();
  initManagerActions();

  // Trigger active filter tab to refresh visible rows & pagination only when client-side paginated
  if (!PM_DASHBOARD_STATE.isServerPaginated) {
    const activeTab = document.querySelector('#contractFilterTabs .pm-tab-btn.active');
    if (activeTab) {
      activeTab.click();
    }
  }
}

function formatIsoDateToVN(isoStr, localeOverride) {
  return formatSystemDate(isoStr, localeOverride);
}

function escapeHtml(str) {
  return String(str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/**
 * Fetch and hydrate full Dashboard data from PawnMasterDB (scoped by branchId & timeframe)
 */
async function loadDashboardDataFromDb(requestedBranchId, isSwitchAttempt = false, requestedTimeframe = null) {
  if (!window.location.protocol.startsWith('http')) {
    return null;
  }

  const loadingBar = document.getElementById('dashboardLoadingBar');
  if (loadingBar) loadingBar.classList.add('is-loading');

  try {
    const tf = requestedTimeframe || PM_DASHBOARD_STATE.activeTimeframe || 'THIS_MONTH';
    const queryParams = [];
    if (requestedBranchId != null && requestedBranchId !== '' && requestedBranchId !== 'ALL') {
      queryParams.push(`branchId=${encodeURIComponent(requestedBranchId)}`);
    }
    if (isSwitchAttempt) {
      queryParams.push('switchAttempt=true');
    }
    if (tf) {
      queryParams.push(`timeframe=${encodeURIComponent(tf)}`);
    }
    const url = '/api/v1/dashboard/summary' + (queryParams.length > 0 ? `?${queryParams.join('&')}` : '');

    const payload = await apiFetch(url, { method: 'GET' });
    if (payload && payload.data) {
      applyDashboardSummaryToDom(payload.data);
      return payload.data;
    }
    return null;
  } finally {
    if (loadingBar) loadingBar.classList.remove('is-loading');
  }
}

/**
 * Auth Session Sync & Logout Handler
 */
function initAuthSessionAndLogout() {
  // 1. Read SSR body security attributes first (Spring Security cookie session)
  if (typeof document !== 'undefined' && document.body) {
    const ssrRole = (document.body.getAttribute('data-user-role') || '').trim();
    const ssrBranchId = (document.body.getAttribute('data-user-branch-id') || '').trim();
    const ssrBranchCode = (document.body.getAttribute('data-user-branch-code') || '').trim();
    const ssrBranchName = (document.body.getAttribute('data-user-branch-name') || '').trim();
    const ssrCanSwitch = document.body.getAttribute('data-can-switch-branch');

    if (ssrRole) {
      PM_DASHBOARD_STATE.userRole = ssrRole.toUpperCase();
      PM_DASHBOARD_STATE.canSwitchBranch = ssrRole.toUpperCase() === 'ADMIN';
    } else if (ssrCanSwitch != null) {
      PM_DASHBOARD_STATE.canSwitchBranch = ssrCanSwitch === 'true';
    }
    if (ssrBranchId) {
      PM_DASHBOARD_STATE.userAssignedBranchId = Number(ssrBranchId);
      if (PM_DASHBOARD_STATE.userRole && PM_DASHBOARD_STATE.userRole !== 'ADMIN') {
        PM_DASHBOARD_STATE.activeBranchId = Number(ssrBranchId);
      }
    }
    if (ssrBranchCode) PM_DASHBOARD_STATE.userAssignedBranchCode = ssrBranchCode;
    if (ssrBranchName) PM_DASHBOARD_STATE.userAssignedBranchName = ssrBranchName;
  }

  // 2. Sync client sessionStorage / localStorage user profile if present
  try {
    const localRaw = (typeof localStorage !== 'undefined')
      ? (localStorage.getItem('pm_current_user') || localStorage.getItem('pm_user'))
      : null;
    const sessionRaw = (typeof sessionStorage !== 'undefined')
      ? sessionStorage.getItem('pm_current_user')
      : null;
    const rawUser = sessionRaw || localRaw;
    if (rawUser) {
      const user = JSON.parse(rawUser);
      PM_DASHBOARD_STATE.currentUser = user;
      if (user.role) {
        PM_DASHBOARD_STATE.userRole = String(user.role).trim().toUpperCase();
      }
      PM_DASHBOARD_STATE.canSwitchBranch = PM_DASHBOARD_STATE.userRole === 'ADMIN';
      if (user.branchId != null) {
        PM_DASHBOARD_STATE.userAssignedBranchId = Number(user.branchId);
        if (PM_DASHBOARD_STATE.userRole !== 'ADMIN') {
          PM_DASHBOARD_STATE.activeBranchId = Number(user.branchId);
        }
      }
      if (user.branchName) {
        PM_DASHBOARD_STATE.userAssignedBranchName = user.branchName;
      }
      if (user.branchCode) {
        PM_DASHBOARD_STATE.userAssignedBranchCode = user.branchCode;
      }

      const nameEl = document.getElementById('topbarUserName');
      const roleEl = document.getElementById('topbarUserRole');
      const avatarEl = document.getElementById('topbarUserAvatar');
      if (nameEl && user.fullName) nameEl.textContent = user.fullName;
      if (roleEl && (user.roleLabel || user.role)) {
        roleEl.textContent = user.roleLabel || (`Vai trò: ${user.role}`);
      }
      if (avatarEl && user.avatar) avatarEl.textContent = user.avatar;

      // If single-branch user is active before API hydration, immediately prune other branch options from DOM
      if (!PM_DASHBOARD_STATE.canSwitchBranch && PM_DASHBOARD_STATE.userAssignedBranchId != null) {
        const listEl = document.getElementById('branchDropdownList');
        if (listEl) {
          listEl.querySelectorAll('.pm-branch-option').forEach(opt => {
            const val = opt.getAttribute('data-branch-value');
            if (String(val) !== String(PM_DASHBOARD_STATE.userAssignedBranchId)) {
              opt.remove();
            }
          });
        }
        if (PM_DASHBOARD_STATE.userAssignedBranchName) {
          syncBranchTriggerDom({
            value: PM_DASHBOARD_STATE.userAssignedBranchId,
            name: PM_DASHBOARD_STATE.userAssignedBranchName,
            code: PM_DASHBOARD_STATE.userAssignedBranchCode || `CN-${PM_DASHBOARD_STATE.userAssignedBranchId}`
          });
        }
      }
    }
  } catch (e) {
    // Ignore storage read issues in sandboxed test environments
  }

  // 3. Immediately strip unauthorized buttons from DOM at t = 0ms (never hide via CSS)
  pruneUnauthorizedDomActions();

  const btnLogout = document.getElementById('btnLogout');
  if (btnLogout) {
    btnLogout.addEventListener('click', async function (e) {
      e.preventDefault();
      try {
        if (typeof fetch !== 'undefined') {
          await fetch('/api/v1/auth/logout', { method: 'POST' });
        }
      } catch (err) {
        // Ignore network errors if offline
      }

      try {
        if (typeof localStorage !== 'undefined') {
          localStorage.removeItem('pm_access_token');
          localStorage.removeItem('pm_current_user');
          localStorage.removeItem('pm_user');
        }
        if (typeof sessionStorage !== 'undefined') {
          sessionStorage.removeItem('pm_access_token');
          sessionStorage.removeItem('pm_current_user');
        }
        if (typeof document !== 'undefined' && document.cookie !== undefined) {
          document.cookie = 'pm_token=; path=/; max-age=0; SameSite=Lax';
        }
      } catch (err) {
        // Ignore storage cleanup errors
      }

      if (window && window.location && window.location.protocol && window.location.protocol.startsWith('http')) {
        window.location.href = '/login';
      } else if (window && window.location) {
        window.location.href = 'login.html';
      }
    });
  }
}

// DOM Ready Initialization
document.addEventListener('DOMContentLoaded', function () {
  if (window.location && window.location.search) {
    const initParams = new URLSearchParams(window.location.search);
    const paramTf = initParams.get('timeframe');
    if (paramTf && DASHBOARD_TIMEFRAME_CONFIG[paramTf]) {
      PM_DASHBOARD_STATE.activeTimeframe = paramTf;
    }
  }

  syncSystemDateInputsLocale();
  hydrateStaticDatesToSystemLocale();
  initAuthSessionAndLogout();
  initLiveClock();
  initCashFlowChart('cashFlowChart');
  initCollateralDonutChart('collateralDonutChart');
  initDashboardFilters('recentContractsTable', 'contractSearchInput', 'contractFilterTabs');
  initRowActionTriggers();
  initChartTimeframeTabs();
  initModalForms();
  initBranchSelector();
  initManagerActions();

  // Automatically hydrate Dashboard from PawnMasterDB after login
  if (window.location.protocol.startsWith('http')) {
    const urlParams = new URLSearchParams(window.location.search);
    const paramBranchId = urlParams.get('branchId');
    const isUnauthorizedAllAttempt = paramBranchId === 'ALL' && !PM_DASHBOARD_STATE.canSwitchBranch;
    const initialBranchId = (paramBranchId && paramBranchId !== 'ALL')
      ? Number(paramBranchId)
      : (isUnauthorizedAllAttempt
        ? null
        : (PM_DASHBOARD_STATE.canSwitchBranch ? null : (PM_DASHBOARD_STATE.userAssignedBranchId || null)));

    loadDashboardDataFromDb(initialBranchId, isUnauthorizedAllAttempt, PM_DASHBOARD_STATE.activeTimeframe).catch(err => {
      if (err && (err.isBranchAccessDenied || err.status === 403 || err.code === 4004)) {
        const attemptedText = isUnauthorizedAllAttempt
          ? 'Toàn hệ thống (Tất cả chi nhánh)'
          : `Chi nhánh #${initialBranchId}`;
        showBranchAccessDeniedModal(err.message, attemptedText);
        if (PM_DASHBOARD_STATE.userAssignedBranchId) {
          loadDashboardDataFromDb(PM_DASHBOARD_STATE.userAssignedBranchId, false, PM_DASHBOARD_STATE.activeTimeframe).catch(() => { });
        }
      } else {
        console.error('PawnMaster Dashboard hydration error:', err);
        showToast((err && err.message) || 'Không thể tải dữ liệu Dashboard từ máy chủ', 'danger');
      }
    });
  }
});
