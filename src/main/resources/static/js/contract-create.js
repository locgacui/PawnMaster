/**
 * PawnMaster — Dedicated Contract Origination Workspace Controller
 * Target View: src/main/resources/templates/contract-create.html (/contracts/new)
 * Creative North Star: "The Vault Ledger" (Operate Mode)
 */

const CC_DRAFT_STORAGE_KEY = 'pm_contract_draft_v1';

const currencyFormatter = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0
});

/**
 * Format numeric amount to VND string (e.g. "45.000.000 ₫")
 * @param {number|string} amount
 * @returns {string}
 */
function formatVND(amount) {
  const num = Math.round(Number(amount) || 0);
  return currencyFormatter.format(num);
}

/**
 * Format numeric amount with Vietnamese dot separators without currency symbol (e.g. "60.000.000")
 * @param {number|string} amount
 * @returns {string}
 */
function formatVndDigits(amount) {
  const num = Math.max(0, Math.round(Number(amount) || 0));
  if (num === 0) return '0';
  return String(num).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

/**
 * Parse formatted VND string to integer
 * @param {string|number} raw
 * @returns {number}
 */
function parseVND(raw) {
  if (raw === null || raw === undefined) return 0;
  if (typeof raw === 'number') return Math.max(0, Math.round(raw));
  const cleaned = String(raw).replace(/[^\d]/g, '');
  return parseInt(cleaned, 10) || 0;
}

/**
 * Escape HTML special characters for XSS-safe DOM rendering
 * @param {string} str
 * @returns {string}
 */
function escapeHtml(str) {
  return String(str == null ? '' : str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * Normalize Vietnamese string for fuzzy administrative unit matching
 * @param {string} text
 * @returns {string}
 */
function normalizeVnSearchKey(text) {
  return String(text || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[đĐ]/g, 'd')
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
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
 * Format ISO date (YYYY-MM-DD), legacy dd/MM/yyyy string, or Date object
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
 * Backward-compatible alias delegating to formatSystemDate so all schedule,
 * ledger, and draft dates follow the user's OS / Browser locale.
 * @param {Date|string} input
 * @param {string} [localeOverride]
 * @returns {string}
 */
function formatDateVN(input, localeOverride) {
  return formatSystemDate(input, localeOverride);
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
 * Progressively enhance a single <input type="date"> so its visible formatted text
 * ALWAYS matches getSystemLocale() / formatSystemDate() (even in Chromium where native
 * <input type="date"> ignores lang="..."), while preserving canonical ISO YYYY-MM-DD
 * in input.value, native calendar showPicker(), readonly locking, and validation states.
 * @param {HTMLInputElement} dateInput
 * @param {string} [localeOverride]
 */
function enhanceSingleLocaleDateInput(dateInput, localeOverride) {
  if (!dateInput) return;
  const sysLocale = getSystemLocale(localeOverride);
  const pattern = getSystemDateFormatPattern(localeOverride);
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

  const getRawIso = () => {
    if (protoDesc && typeof protoDesc.get === 'function') {
      return protoDesc.get.call(dateInput) || '';
    }
    return dateInput.value || dateInput.getAttribute('value') || '';
  };

  const setRawIso = val => {
    const clean = val == null ? '' : String(val);
    if (protoDesc && typeof protoDesc.set === 'function') {
      protoDesc.set.call(dateInput, clean);
    } else {
      dateInput.setAttribute('value', clean);
    }
  };

  const wrap = document.createElement('div');
  wrap.className = 'pm-locale-date-wrap';

  const displayInput = document.createElement('input');
  displayInput.type = 'text';
  displayInput.inputMode = 'numeric';
  displayInput.autocomplete = 'off';
  displayInput.spellcheck = false;
  displayInput.className = 'form-control pm-form-control font-tabular pm-locale-date-display';
  if (dateInput.classList && dateInput.classList.contains('pm-autofill-target')) {
    displayInput.classList.add('pm-autofill-target');
  }
  displayInput.placeholder = pattern;

  if (dateInput.id && typeof document.querySelector === 'function') {
    const assocLabel = document.querySelector(`label[for="${dateInput.id}"]`);
    if (assocLabel) {
      if (!assocLabel.id) {
        assocLabel.id = `${dateInput.id}-label`;
      }
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

  const origSetAttr = dateInput.setAttribute.bind(dateInput);
  const origRemoveAttr = dateInput.removeAttribute.bind(dateInput);

  const syncStatesFromNative = () => {
    const isRo = dateInput.hasAttribute('readonly') || Boolean(dateInput.readOnly);
    const isDis = dateInput.hasAttribute('disabled') || Boolean(dateInput.disabled);
    displayInput.readOnly = isRo;
    displayInput.disabled = isDis;
    if (isRo) {
      displayInput.setAttribute('readonly', 'readonly');
    } else {
      displayInput.removeAttribute('readonly');
    }
    wrap.classList.toggle('is-readonly', isRo || isDis);
    pickerBtn.disabled = isRo || isDis;

    const isInvalid = (dateInput.classList && dateInput.classList.contains('is-invalid')) ||
      dateInput.getAttribute('aria-invalid') === 'true';
    displayInput.classList.toggle('is-invalid', isInvalid);
    if (isInvalid) {
      displayInput.setAttribute('aria-invalid', 'true');
    } else {
      displayInput.removeAttribute('aria-invalid');
    }

    const descBy = dateInput.getAttribute('aria-describedby');
    if (descBy) {
      displayInput.setAttribute('aria-describedby', descBy);
    } else {
      displayInput.removeAttribute('aria-describedby');
    }
  };

  try {
    Object.defineProperty(dateInput, 'value', {
      configurable: true,
      enumerable: true,
      get() {
        return getRawIso();
      },
      set(nextVal) {
        setRawIso(nextVal);
        if (!isInternalTyping) {
          syncTextFromNative();
        }
      }
    });
  } catch (e) {
    // Ignore if environment forbids redefining value
  }

  dateInput.setAttribute = function (name, value) {
    origSetAttr(name, value);
    const lower = String(name).toLowerCase();
    if (lower === 'value' && !isInternalTyping) {
      syncTextFromNative();
    } else if (lower === 'readonly' || lower === 'disabled' || lower === 'aria-invalid' || lower === 'aria-describedby' || lower === 'class') {
      syncStatesFromNative();
    }
  };

  dateInput.removeAttribute = function (name) {
    origRemoveAttr(name);
    const lower = String(name).toLowerCase();
    if (lower === 'readonly' || lower === 'disabled' || lower === 'aria-invalid' || lower === 'aria-describedby' || lower === 'class') {
      syncStatesFromNative();
    }
  };

  if (typeof MutationObserver !== 'undefined') {
    const observer = new MutationObserver(() => syncStatesFromNative());
    observer.observe(dateInput, {
      attributes: true,
      attributeFilter: ['readonly', 'disabled', 'class', 'aria-invalid', 'aria-describedby']
    });
  }

  dateInput.focus = function (opts) {
    displayInput.focus(opts);
  };
  dateInput.addEventListener('focus', () => {
    displayInput.focus();
  });

  const dispatchNativeEvents = includeChange => {
    if (typeof Event === 'function') {
      dateInput.dispatchEvent(new Event('input', { bubbles: true }));
      if (includeChange) {
        dateInput.dispatchEvent(new Event('change', { bubbles: true }));
      }
    }
  };

  dateInput.addEventListener('input', () => {
    if (!isInternalTyping) syncTextFromNative();
  });
  dateInput.addEventListener('change', () => {
    if (!isInternalTyping) syncTextFromNative();
  });

  displayInput.addEventListener('input', () => {
    if (displayInput.readOnly || displayInput.disabled) return;
    const raw = displayInput.value.trim();
    isInternalTyping = true;
    try {
      if (!raw) {
        setRawIso('');
        dispatchNativeEvents(false);
      } else {
        const parsedIso = parseSystemDateToIso(raw, activeLocaleOverride);
        if (parsedIso) {
          setRawIso(parsedIso);
          dispatchNativeEvents(true);
        }
      }
    } finally {
      isInternalTyping = false;
    }
  });

  displayInput.addEventListener('blur', () => {
    if (displayInput.readOnly || displayInput.disabled) return;
    const raw = displayInput.value.trim();
    if (!raw) {
      setRawIso('');
      displayInput.value = '';
      dispatchNativeEvents(true);
      return;
    }
    const parsedIso = parseSystemDateToIso(raw, activeLocaleOverride);
    if (parsedIso) {
      setRawIso(parsedIso);
      displayInput.value = formatSystemDate(parsedIso, activeLocaleOverride);
      dispatchNativeEvents(true);
    } else {
      syncTextFromNative();
    }
  });

  pickerBtn.addEventListener('click', e => {
    e.preventDefault();
    if (displayInput.readOnly || displayInput.disabled) return;
    if (typeof dateInput.showPicker === 'function') {
      try {
        dateInput.showPicker();
        return;
      } catch (err) {
        // Fallback when showPicker requires non-readonly native state
      }
    }
    displayInput.focus();
  });

  if (dateInput.form && typeof dateInput.form.addEventListener === 'function') {
    dateInput.form.addEventListener('reset', () => {
      setTimeout(() => {
        syncTextFromNative();
        syncStatesFromNative();
      }, 0);
    });
  }

  dateInput._pmDisplayInput = displayInput;
  dateInput._pmSyncLocaleDate = nextOverride => {
    if (nextOverride !== undefined) activeLocaleOverride = nextOverride;
    const loc = getSystemLocale(activeLocaleOverride);
    if (loc) origSetAttr('lang', loc);
    origSetAttr('data-date-format', getSystemDateFormatPattern(activeLocaleOverride));
    syncTextFromNative();
    syncStatesFromNative();
  };

  syncTextFromNative();
  syncStatesFromNative();
}

/**
 * Sync all <input type="date"> controls to the user's OS / Browser locale
 * with guaranteed visible formatting parity across Chromium, Firefox, and Safari.
 * Note: HTMLInputElement.value remains ISO YYYY-MM-DD for API safety.
 * @param {Document|HTMLElement} [rootEl]
 * @param {string} [localeOverride]
 */
function syncSystemDateInputsLocale(rootEl, localeOverride) {
  const scope = rootEl || (typeof document !== 'undefined' ? document : null);
  if (!scope || typeof scope.querySelectorAll !== 'function') return;
  scope.querySelectorAll('input[type="date"]').forEach(input => {
    enhanceSingleLocaleDateInput(input, localeOverride);
  });
}

/**
 * Standard CCCD / CMND Issuing Authorities & Statutory Auto-Selection Rules
 */
const CCCD_ISSUE_PLACE_OPTIONS = {
  BO_CONG_AN: 'Bộ Công an',
  CUC_CS_QLHC_TTXH: 'Cục Cảnh sát quản lý hành chính về trật tự xã hội',
  CUC_CS_DKQL_CU_TRU: 'Cục Cảnh sát ĐKQL cư trú và DLQG về dân cư',
  OTHER: 'OTHER'
};

/**
 * Suggest the statutory CCCD issuing authority from the issue date:
 * - >= 01/07/2024: "Bộ Công an"
 * - 10/10/2018 .. 30/06/2024: "Cục Cảnh sát quản lý hành chính về trật tự xã hội"
 * - 01/01/2016 .. 09/10/2018: "Cục Cảnh sát ĐKQL cư trú và DLQG về dân cư"
 * - < 01/01/2016: "OTHER" ("Khác / Công an Tỉnh, Thành phố")
 * @param {string} issueDateRaw ISO YYYY-MM-DD or localized date string
 * @returns {string|null}
 */
function suggestIdentityIssuePlaceByDate(issueDateRaw) {
  if (!issueDateRaw || typeof issueDateRaw !== 'string') return null;
  const iso = parseSystemDateToIso(issueDateRaw.trim().slice(0, 10));
  if (!iso) return null;

  if (iso >= '2024-07-01') {
    return CCCD_ISSUE_PLACE_OPTIONS.BO_CONG_AN;
  }
  if (iso >= '2018-10-10' && iso <= '2024-06-30') {
    return CCCD_ISSUE_PLACE_OPTIONS.CUC_CS_QLHC_TTXH;
  }
  if (iso >= '2016-01-01' && iso <= '2018-10-09') {
    return CCCD_ISSUE_PLACE_OPTIONS.CUC_CS_DKQL_CU_TRU;
  }
  return CCCD_ISSUE_PLACE_OPTIONS.OTHER;
}

/**
 * Normalize a saved customer's identityIssuePlace into dropdown value + optional custom text.
 * Supports legacy abbreviations ("Cục Cảnh sát QLHC về TTXH") seamlessly.
 * @param {string|null|undefined} rawPlace
 * @param {string|null|undefined} [fallbackIssueDateIso]
 * @returns {{ selectValue: string, customValue: string }}
 */
function normalizeSavedIssuePlace(rawPlace, fallbackIssueDateIso) {
  const trimmed = (rawPlace == null ? '' : String(rawPlace)).trim();
  if (!trimmed) {
    const suggested = suggestIdentityIssuePlaceByDate(fallbackIssueDateIso || '') ||
      CCCD_ISSUE_PLACE_OPTIONS.CUC_CS_QLHC_TTXH;
    return { selectValue: suggested, customValue: '' };
  }

  const lower = trimmed.toLowerCase();
  if (lower === 'bộ công an') {
    return { selectValue: CCCD_ISSUE_PLACE_OPTIONS.BO_CONG_AN, customValue: '' };
  }
  if (
    lower === 'cục cảnh sát quản lý hành chính về trật tự xã hội' ||
    lower === 'cục cảnh sát qlhc về ttxh'
  ) {
    return { selectValue: CCCD_ISSUE_PLACE_OPTIONS.CUC_CS_QLHC_TTXH, customValue: '' };
  }
  if (
    lower === 'cục cảnh sát đkql cư trú và dlqg về dân cư' ||
    lower === 'cục cảnh sát đkql cư trú và dlqg về dc'
  ) {
    return { selectValue: CCCD_ISSUE_PLACE_OPTIONS.CUC_CS_DKQL_CU_TRU, customValue: '' };
  }

  return {
    selectValue: CCCD_ISSUE_PLACE_OPTIONS.OTHER,
    customValue: trimmed
  };
}

/**
 * Sync visibility of #ccIdentityIssuePlaceCustomWrap based on #ccIdentityIssuePlace selection
 * @param {{ focusCustom?: boolean }} [options]
 */
function syncIdentityIssuePlaceCustomUI(options = {}) {
  if (typeof document === 'undefined') return;
  const selectEl = document.getElementById('ccIdentityIssuePlace');
  const customWrap = document.getElementById('ccIdentityIssuePlaceCustomWrap');
  const customInput = document.getElementById('ccIdentityIssuePlaceCustom');
  if (!selectEl || !customWrap) return;

  const isOther = selectEl.value === CCCD_ISSUE_PLACE_OPTIONS.OTHER;
  customWrap.classList.toggle('d-none', !isOther);
  if (!isOther && customInput && typeof clearFieldValidationError === 'function') {
    clearFieldValidationError(customInput);
  }
  if (isOther && options.focusCustom && customInput && !customInput.hasAttribute('readonly')) {
    customInput.focus();
  }
}

/**
 * Resolve the effective Nơi cấp CCCD string from dropdown or custom input
 * @returns {string}
 */
function getResolvedIdentityIssuePlace() {
  if (typeof document === 'undefined') return '';
  const selectEl = document.getElementById('ccIdentityIssuePlace');
  if (!selectEl) return '';
  if (selectEl.value === CCCD_ISSUE_PLACE_OPTIONS.OTHER) {
    const customInput = document.getElementById('ccIdentityIssuePlaceCustom');
    return (customInput ? customInput.value : '').trim();
  }
  return (selectEl.value || '').trim();
}

/**
 * Parse YYYY-MM-DD safely into local Date at noon to avoid DST/timezone drift
 * @param {string} isoStr
 * @returns {Date}
 */
function parseIsoDateLocal(isoStr) {
  if (isoStr && /^\d{4}-\d{2}-\d{2}$/.test(isoStr.trim())) {
    const [y, m, d] = isoStr.trim().split('-').map(Number);
    return new Date(y, m - 1, d, 12, 0, 0);
  }
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate(), 12, 0, 0);
}

/**
 * Convert Date to YYYY-MM-DD
 * @param {Date} dateObj
 * @returns {string}
 */
function toIsoDateString(dateObj) {
  const d = dateObj instanceof Date && !Number.isNaN(dateObj.getTime()) ? dateObj : new Date();
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

/**
 * Read a 3-digit Vietnamese group (0..999)
 */
function readThreeDigitsVN(groupValue, isLeadingGroup) {
  const digits = ['không', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín'];
  const hundreds = Math.floor(groupValue / 100);
  const tens = Math.floor((groupValue % 100) / 10);
  const ones = groupValue % 10;
  const parts = [];

  if (!isLeadingGroup || hundreds > 0) {
    parts.push(`${digits[hundreds]} trăm`);
  }

  if (tens === 0) {
    if (ones > 0) {
      if (!isLeadingGroup || hundreds > 0) {
        parts.push(`linh ${digits[ones]}`);
      } else {
        parts.push(digits[ones]);
      }
    }
  } else if (tens === 1) {
    if (ones === 0) {
      parts.push('mười');
    } else if (ones === 5) {
      parts.push('mười lăm');
    } else {
      parts.push(`mười ${digits[ones]}`);
    }
  } else {
    let tensPhrase = `${digits[tens]} mươi`;
    if (ones === 1) {
      tensPhrase += ' mốt';
    } else if (ones === 4) {
      tensPhrase += ' tư';
    } else if (ones === 5) {
      tensPhrase += ' lăm';
    } else if (ones > 0) {
      tensPhrase += ` ${digits[ones]}`;
    }
    parts.push(tensPhrase);
  }

  return parts.join(' ');
}

/**
 * Convert numeric VND amount into accurate Vietnamese legal currency words
 * Example: 60000000 -> "Sáu mươi triệu đồng chẵn"
 * @param {number|string} amount
 * @returns {string}
 */
function readVndInWords(amount) {
  const numeric = typeof amount === 'number' ? Math.round(amount) : parseVND(amount);
  if (!numeric || numeric <= 0) {
    return 'Không đồng';
  }

  const scaleUnits = ['', 'nghìn', 'triệu', 'tỷ', 'nghìn tỷ', 'triệu tỷ'];
  const groups = [];
  let remaining = Math.floor(Math.abs(numeric));
  while (remaining > 0) {
    groups.push(remaining % 1000);
    remaining = Math.floor(remaining / 1000);
  }

  const phrases = [];
  let hasStarted = false;
  for (let i = groups.length - 1; i >= 0; i--) {
    const val = groups[i];
    if (val === 0) continue;
    const chunkText = readThreeDigitsVN(val, !hasStarted);
    const unit = scaleUnits[i] || '';
    phrases.push(unit ? `${chunkText} ${unit}` : chunkText);
    hasStarted = true;
  }

  const joined = phrases.join(' ').replace(/\s+/g, ' ').trim();
  if (!joined) return 'Không đồng';
  const capitalized = joined.charAt(0).toUpperCase() + joined.slice(1);
  const suffix = numeric % 1000 === 0 ? 'đồng chẵn' : 'đồng';
  return `${capitalized} ${suffix}`;
}

/**
 * 2D LTV Policy Matrix (Mirrors LtvPolicyDataInitializer & LtvPolicyServiceImpl)
 */
const LTV_MATRIX = {
  OFFICE_WORKER: {
    GOLD_JEWELRY: 0.92, MOTORBIKE: 0.90, CAR: 0.85, PHONE: 0.80, LAPTOP: 0.80, WATCH: 0.75, REAL_ESTATE: 0.75, OTHER: 0.70
  },
  FACTORY_WORKER: {
    GOLD_JEWELRY: 0.90, MOTORBIKE: 0.80, CAR: 0.75, PHONE: 0.75, LAPTOP: 0.75, WATCH: 0.70, REAL_ESTATE: 0.70, OTHER: 0.65
  },
  TECH_DRIVER: {
    GOLD_JEWELRY: 0.88, MOTORBIKE: 0.70, CAR: 0.70, PHONE: 0.70, LAPTOP: 0.70, WATCH: 0.65, REAL_ESTATE: 0.65, OTHER: 0.60
  },
  SMALL_MERCHANT: {
    GOLD_JEWELRY: 0.90, MOTORBIKE: 0.80, CAR: 0.80, PHONE: 0.75, LAPTOP: 0.75, WATCH: 0.75, REAL_ESTATE: 0.70, OTHER: 0.65
  },
  ONLINE_SELLER: {
    GOLD_JEWELRY: 0.88, MOTORBIKE: 0.75, CAR: 0.75, PHONE: 0.70, LAPTOP: 0.70, WATCH: 0.68, REAL_ESTATE: 0.65, OTHER: 0.60
  },
  FREELANCER: {
    GOLD_JEWELRY: 0.85, MOTORBIKE: 0.65, CAR: 0.65, PHONE: 0.60, LAPTOP: 0.60, WATCH: 0.55, REAL_ESTATE: 0.55, OTHER: 0.50
  }
};

/**
 * Allowed KYC Verification Documents by Occupation Group (Mirrors VerificationDocumentPolicy)
 */
const VALID_DOCS_BY_GROUP = {
  OFFICE_WORKER: ['PAYSLIP_BANK_STATEMENT', 'LABOR_CONTRACT', 'SOCIAL_INSURANCE_VSSID'],
  FACTORY_WORKER: ['PAYSLIP_BANK_STATEMENT', 'WORKER_ID_OR_CONTRACT', 'SOCIAL_INSURANCE_VSSID'],
  TECH_DRIVER: ['DRIVER_APP_INCOME_SCREENSHOT', 'RESIDENCE_CONFIRMATION_CT07', 'RENTAL_CONTRACT'],
  SMALL_MERCHANT: ['BUSINESS_LICENSE_OR_STALL_LEASE', 'SHOP_PHOTO_AND_CASHFLOW'],
  ONLINE_SELLER: ['ECOMMERCE_REVENUE_SCREENSHOT', 'BANK_COD_STATEMENT'],
  FREELANCER: ['RESIDENCE_CONFIRMATION_CT07', 'RENTAL_CONTRACT', 'UTILITY_BILL']
};

const COLLATERAL_CATEGORY_LABELS = {
  MOTORBIKE: 'Xe máy',
  CAR: 'Ô tô',
  GOLD_JEWELRY: 'Vàng / Trang sức',
  PHONE: 'Điện thoại',
  LAPTOP: 'Laptop',
  WATCH: 'Đồng hồ',
  REAL_ESTATE: 'Bất động sản',
  OTHER: 'Tài sản khác'
};

const OCCUPATION_GROUP_LABELS = {
  OFFICE_WORKER: 'Viên chức - Văn phòng',
  FACTORY_WORKER: 'Công nhân nhà máy',
  TECH_DRIVER: 'Tài xế công nghệ',
  SMALL_MERCHANT: 'Tiểu thương chợ / cửa hàng',
  ONLINE_SELLER: 'Kinh doanh online',
  FREELANCER: 'Lao động tự do'
};

/**
 * Built-in Offline GSO 3-Tier Administrative Catalog (Fallback for standalone preview / offline resiliency)
 */
const OFFLINE_ADMIN_CATALOG = {
  provinces: [
    { code: '79', name: 'Hồ Chí Minh', fullName: 'Thành phố Hồ Chí Minh' },
    { code: '01', name: 'Hà Nội', fullName: 'Thành phố Hà Nội' },
    { code: '48', name: 'Đà Nẵng', fullName: 'Thành phố Đà Nẵng' },
    { code: '92', name: 'Cần Thơ', fullName: 'Thành phố Cần Thơ' },
    { code: '74', name: 'Bình Dương', fullName: 'Tỉnh Bình Dương' },
    { code: '75', name: 'Đồng Nai', fullName: 'Tỉnh Đồng Nai' }
  ],
  districts: {
    '79': [
      { code: '760', name: '1', fullName: 'Quận 1', provinceCode: '79' },
      { code: '770', name: '3', fullName: 'Quận 3', provinceCode: '79' },
      { code: '765', name: 'Bình Thạnh', fullName: 'Quận Bình Thạnh', provinceCode: '79' },
      { code: '769', name: 'Thủ Đức', fullName: 'Thành phố Thủ Đức', provinceCode: '79' },
      { code: '778', name: '7', fullName: 'Quận 7', provinceCode: '79' }
    ],
    '01': [
      { code: '001', name: 'Ba Đình', fullName: 'Quận Ba Đình', provinceCode: '01' },
      { code: '002', name: 'Hoàn Kiếm', fullName: 'Quận Hoàn Kiếm', provinceCode: '01' },
      { code: '005', name: 'Cầu Giấy', fullName: 'Quận Cầu Giấy', provinceCode: '01' }
    ],
    '48': [
      { code: '492', name: 'Hải Châu', fullName: 'Quận Hải Châu', provinceCode: '48' },
      { code: '491', name: 'Thanh Khê', fullName: 'Quận Thanh Khê', provinceCode: '48' }
    ],
    '92': [
      { code: '916', name: 'Ninh Kiều', fullName: 'Quận Ninh Kiều', provinceCode: '92' },
      { code: '919', name: 'Cái Răng', fullName: 'Quận Cái Răng', provinceCode: '92' }
    ],
    '74': [
      { code: '718', name: 'Thủ Dầu Một', fullName: 'Thành phố Thủ Dầu Một', provinceCode: '74' },
      { code: '724', name: 'Dĩ An', fullName: 'Thành phố Dĩ An', provinceCode: '74' },
      { code: '725', name: 'Thuận An', fullName: 'Thành phố Thuận An', provinceCode: '74' }
    ],
    '75': [
      { code: '731', name: 'Biên Hòa', fullName: 'Thành phố Biên Hòa', provinceCode: '75' },
      { code: '732', name: 'Long Khánh', fullName: 'Thành phố Long Khánh', provinceCode: '75' }
    ]
  },
  wards: {
    '760': [
      { code: '26740', name: 'Bến Nghé', fullName: 'Phường Bến Nghé', districtCode: '760' },
      { code: '26743', name: 'Bến Thành', fullName: 'Phường Bến Thành', districtCode: '760' },
      { code: '26737', name: 'Đa Kao', fullName: 'Phường Đa Kao', districtCode: '760' },
      { code: '26734', name: 'Tân Định', fullName: 'Phường Tân Định', districtCode: '760' },
      { code: '26746', name: 'Nguyễn Thái Bình', fullName: 'Phường Nguyễn Thái Bình', districtCode: '760' },
      { code: '26749', name: 'Phạm Ngũ Lão', fullName: 'Phường Phạm Ngũ Lão', districtCode: '760' },
      { code: '26752', name: 'Cầu Ông Lãnh', fullName: 'Phường Cầu Ông Lãnh', districtCode: '760' },
      { code: '26755', name: 'Cô Giang', fullName: 'Phường Cô Giang', districtCode: '760' },
      { code: '26758', name: 'Nguyễn Cư Trinh', fullName: 'Phường Nguyễn Cư Trinh', districtCode: '760' },
      { code: '26761', name: 'Cầu Kho', fullName: 'Phường Cầu Kho', districtCode: '760' }
    ],
    '770': [
      { code: '27139', name: 'Võ Thị Sáu', fullName: 'Phường Võ Thị Sáu', districtCode: '770' },
      { code: '27160', name: '01', fullName: 'Phường 01', districtCode: '770' },
      { code: '27157', name: '02', fullName: 'Phường 02', districtCode: '770' },
      { code: '27151', name: '05', fullName: 'Phường 05', districtCode: '770' },
      { code: '27142', name: '09', fullName: 'Phường 09', districtCode: '770' },
      { code: '27127', name: '14', fullName: 'Phường 14', districtCode: '770' }
    ],
    '765': [
      { code: '26905', name: '13', fullName: 'Phường 13', districtCode: '765' },
      { code: '26908', name: '11', fullName: 'Phường 11', districtCode: '765' },
      { code: '26917', name: '12', fullName: 'Phường 12', districtCode: '765' },
      { code: '26914', name: '26', fullName: 'Phường 26', districtCode: '765' },
      { code: '26911', name: '27', fullName: 'Phường 27', districtCode: '765' }
    ],
    '769': [
      { code: '26794', name: 'Linh Xuân', fullName: 'Phường Linh Xuân', districtCode: '769' },
      { code: '26797', name: 'Bình Chiểu', fullName: 'Phường Bình Chiểu', districtCode: '769' },
      { code: '26800', name: 'Linh Trung', fullName: 'Phường Linh Trung', districtCode: '769' },
      { code: '26803', name: 'Tam Bình', fullName: 'Phường Tam Bình', districtCode: '769' },
      { code: '26806', name: 'Tam Phú', fullName: 'Phường Tam Phú', districtCode: '769' }
    ],
    '778': [
      { code: '27466', name: 'Tân Thuận Đông', fullName: 'Phường Tân Thuận Đông', districtCode: '778' },
      { code: '27469', name: 'Tân Thuận Tây', fullName: 'Phường Tân Thuận Tây', districtCode: '778' },
      { code: '27475', name: 'Tân Hưng', fullName: 'Phường Tân Hưng', districtCode: '778' },
      { code: '27490', name: 'Tân Phong', fullName: 'Phường Tân Phong', districtCode: '778' },
      { code: '27487', name: 'Tân Phú', fullName: 'Phường Tân Phú', districtCode: '778' }
    ],
    '001': [
      { code: '00001', name: 'Phúc Xá', fullName: 'Phường Phúc Xá', districtCode: '001' },
      { code: '00004', name: 'Trúc Bạch', fullName: 'Phường Trúc Bạch', districtCode: '001' },
      { code: '00007', name: 'Cống Vị', fullName: 'Phường Cống Vị', districtCode: '001' },
      { code: '00019', name: 'Điện Biên', fullName: 'Phường Điện Biên', districtCode: '001' },
      { code: '00028', name: 'Kim Mã', fullName: 'Phường Kim Mã', districtCode: '001' }
    ],
    '002': [
      { code: '00037', name: 'Phúc Tân', fullName: 'Phường Phúc Tân', districtCode: '002' },
      { code: '00049', name: 'Hàng Đào', fullName: 'Phường Hàng Đào', districtCode: '002' },
      { code: '00061', name: 'Hàng Bạc', fullName: 'Phường Hàng Bạc', districtCode: '002' },
      { code: '00079', name: 'Tràng Tiền', fullName: 'Phường Tràng Tiền', districtCode: '002' },
      { code: '00085', name: 'Phan Chu Trinh', fullName: 'Phường Phan Chu Trinh', districtCode: '002' }
    ],
    '005': [
      { code: '00157', name: 'Nghĩa Đô', fullName: 'Phường Nghĩa Đô', districtCode: '005' },
      { code: '00163', name: 'Mai Dịch', fullName: 'Phường Mai Dịch', districtCode: '005' },
      { code: '00166', name: 'Dịch Vọng', fullName: 'Phường Dịch Vọng', districtCode: '005' },
      { code: '00167', name: 'Dịch Vọng Hậu', fullName: 'Phường Dịch Vọng Hậu', districtCode: '005' },
      { code: '00175', name: 'Trung Hoà', fullName: 'Phường Trung Hoà', districtCode: '005' }
    ],
    '492': [
      { code: '20227', name: 'Thanh Bình', fullName: 'Phường Thanh Bình', districtCode: '492' },
      { code: '20233', name: 'Thạch Thang', fullName: 'Phường Thạch Thang', districtCode: '492' },
      { code: '20236', name: 'Hải Châu I', fullName: 'Phường Hải Châu I', districtCode: '492' },
      { code: '20239', name: 'Hải Châu II', fullName: 'Phường Hải Châu II', districtCode: '492' },
      { code: '20242', name: 'Phước Ninh', fullName: 'Phường Phước Ninh', districtCode: '492' }
    ],
    '491': [
      { code: '20209', name: 'Xuân Hà', fullName: 'Phường Xuân Hà', districtCode: '491' },
      { code: '20212', name: 'Tân Chính', fullName: 'Phường Tân Chính', districtCode: '491' },
      { code: '20218', name: 'Vĩnh Trung', fullName: 'Phường Vĩnh Trung', districtCode: '491' },
      { code: '20221', name: 'Thạc Gián', fullName: 'Phường Thạc Gián', districtCode: '491' }
    ],
    '916': [
      { code: '31117', name: 'Cái Khế', fullName: 'Phường Cái Khế', districtCode: '916' },
      { code: '31135', name: 'Tân An', fullName: 'Phường Tân An', districtCode: '916' },
      { code: '31144', name: 'Xuân Khánh', fullName: 'Phường Xuân Khánh', districtCode: '916' },
      { code: '31147', name: 'Hưng Lợi', fullName: 'Phường Hưng Lợi', districtCode: '916' }
    ],
    '919': [
      { code: '31186', name: 'Lê Bình', fullName: 'Phường Lê Bình', districtCode: '919' },
      { code: '31189', name: 'Hưng Phú', fullName: 'Phường Hưng Phú', districtCode: '919' },
      { code: '31192', name: 'Hưng Thạnh', fullName: 'Phường Hưng Thạnh', districtCode: '919' }
    ],
    '718': [
      { code: '25744', name: 'Phú Lợi', fullName: 'Phường Phú Lợi', districtCode: '718' },
      { code: '25747', name: 'Phú Cường', fullName: 'Phường Phú Cường', districtCode: '718' },
      { code: '25750', name: 'Phú Hòa', fullName: 'Phường Phú Hòa', districtCode: '718' }
    ],
    '724': [
      { code: '25942', name: 'Dĩ An', fullName: 'Phường Dĩ An', districtCode: '724' },
      { code: '25957', name: 'Đông Hòa', fullName: 'Phường Đông Hòa', districtCode: '724' }
    ],
    '725': [
      { code: '25966', name: 'Lái Thiêu', fullName: 'Phường Lái Thiêu', districtCode: '725' },
      { code: '25987', name: 'Bình Hòa', fullName: 'Phường Bình Hòa', districtCode: '725' }
    ],
    '731': [
      { code: '26008', name: 'Tân Hiệp', fullName: 'Phường Tân Hiệp', districtCode: '731' },
      { code: '26029', name: 'Thống Nhất', fullName: 'Phường Thống Nhất', districtCode: '731' },
      { code: '26032', name: 'Trung Dũng', fullName: 'Phường Trung Dũng', districtCode: '731' }
    ],
    '732': [
      { code: '26077', name: 'Xuân Bình', fullName: 'Phường Xuân Bình', districtCode: '732' },
      { code: '26080', name: 'Xuân An', fullName: 'Phường Xuân An', districtCode: '732' }
    ]
  }
};

/**
 * Rich Demo Seed Customers for Offline Preview & Instant Counter Testing
 * Note: '079204888999' is intentionally excluded to trigger NOT_FOUND inline customer creation.
 */
const OFFLINE_CUSTOMER_PROFILES = {
  '079201004821': {
    id: 2,
    customerCode: 'KH-2610-6W702P',
    fullName: 'Nguyễn Văn An',
    identityNumber: '079201004821',
    identityIssueDate: '2022-08-19',
    identityIssuePlace: 'Cục Cảnh sát quản lý hành chính về trật tự xã hội',
    phoneNumber: '0908 123 456',
    email: 'nguyenvanan.q1@gmail.com',
    dateOfBirth: '1992-05-14',
    gender: 'NAM',
    provinceCode: '79',
    districtCode: '760',
    wardCode: '26740',
    addressDetail: '88 Nguyễn Huệ',
    street: '88 Nguyễn Huệ',
    permanentAddress: '88 Nguyễn Huệ, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh',
    address: '88 Nguyễn Huệ, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh',
    occupationGroup: 'OFFICE_WORKER',
    monthlyIncome: 18000000,
    hasSocialInsurance: true,
    verificationDocType: 'LABOR_CONTRACT',
    verificationDocUrls: '/uploads/customers/079201004821/verification-docs',
    verificationDocUrlList: [
      '/uploads/docs/kh_002_contract.jpg',
      '/uploads/docs/kh_002_payslip.jpg'
    ],
    verificationDocUrl: '/uploads/docs/kh_002_contract.jpg',
    idCardFrontUrl: '/uploads/cccd/079201004821_front.jpg',
    idCardBackUrl: '/uploads/cccd/079201004821_back.jpg',
    portraitUrl: '/uploads/portraits/079201004821.jpg',
    notes: 'Khách hàng truyền thống thanh toán lãi đúng hạn.',
    status: 'ACTIVE',
    statusLabel: 'Đang hoạt động'
  },
  '079198005432': {
    id: 3,
    customerCode: 'KH-2610-D9K03R',
    fullName: 'Trần Thị Mai',
    identityNumber: '079198005432',
    identityIssueDate: '2021-11-10',
    identityIssuePlace: 'Cục Cảnh sát quản lý hành chính về trật tự xã hội',
    phoneNumber: '0913 456 789',
    email: 'maitran.merchant@gmail.com',
    dateOfBirth: '1989-11-03',
    gender: 'NU',
    provinceCode: '79',
    districtCode: '770',
    wardCode: '27139',
    addressDetail: '154 Nguyễn Đình Chiểu',
    street: '154 Nguyễn Đình Chiểu',
    permanentAddress: '154 Nguyễn Đình Chiểu, Phường Võ Thị Sáu, Quận 3, Thành phố Hồ Chí Minh',
    address: '154 Nguyễn Đình Chiểu, Phường Võ Thị Sáu, Quận 3, Thành phố Hồ Chí Minh',
    occupationGroup: 'SMALL_MERCHANT',
    monthlyIncome: 32000000,
    hasSocialInsurance: false,
    verificationDocType: 'BUSINESS_LICENSE_OR_STALL_LEASE',
    verificationDocUrls: '/uploads/customers/079198005432/verification-docs',
    verificationDocUrlList: [
      '/uploads/docs/kh_003_stall_lease.jpg'
    ],
    verificationDocUrl: '/uploads/docs/kh_003_stall_lease.jpg',
    idCardFrontUrl: '/uploads/cccd/079198005432_front.jpg',
    idCardBackUrl: '/uploads/cccd/079198005432_back.jpg',
    portraitUrl: '/uploads/portraits/079198005432.jpg',
    notes: 'Tiểu thương kinh doanh sạp vải Quận 3, dòng tiền ổn định.',
    status: 'ACTIVE',
    statusLabel: 'Đang hoạt động'
  }
};
const DEMO_CUSTOMERS_BY_CCCD = OFFLINE_CUSTOMER_PROFILES;

const VN_BANKS_CATALOG = Object.freeze([
  { bankCode: 'VCB', bin: '970436', shortName: 'Vietcombank', fullName: 'Ngân hàng TMCP Ngoại thương Việt Nam', bankGroup: 'STATE_POPULAR' },
  { bankCode: 'CTG', bin: '970415', shortName: 'VietinBank', fullName: 'Ngân hàng TMCP Công thương Việt Nam', bankGroup: 'STATE_POPULAR' },
  { bankCode: 'BIDV', bin: '970418', shortName: 'BIDV', fullName: 'Ngân hàng TMCP Đầu tư và Phát triển Việt Nam', bankGroup: 'STATE_POPULAR' },
  { bankCode: 'VBA', bin: '970405', shortName: 'Agribank', fullName: 'Ngân hàng Nông nghiệp và Phát triển Nông thôn Việt Nam', bankGroup: 'STATE_POPULAR' },
  { bankCode: 'TCB', bin: '970407', shortName: 'Techcombank', fullName: 'Ngân hàng TMCP Kỹ thương Việt Nam', bankGroup: 'STATE_POPULAR' },
  { bankCode: 'MB', bin: '970422', shortName: 'MB Bank', fullName: 'Ngân hàng TMCP Quân đội', bankGroup: 'STATE_POPULAR' },
  { bankCode: 'ACB', bin: '970416', shortName: 'ACB', fullName: 'Ngân hàng TMCP Á Châu', bankGroup: 'STATE_POPULAR' },
  { bankCode: 'VPB', bin: '970432', shortName: 'VPBank', fullName: 'Ngân hàng TMCP Việt Nam Thịnh Vượng', bankGroup: 'STATE_POPULAR' },
  { bankCode: 'STB', bin: '970403', shortName: 'Sacombank', fullName: 'Ngân hàng TMCP Sài Gòn Thương Tín', bankGroup: 'STATE_POPULAR' },
  { bankCode: 'TPB', bin: '970423', shortName: 'TPBank', fullName: 'Ngân hàng TMCP Tiên Phong', bankGroup: 'STATE_POPULAR' },
  { bankCode: 'HDB', bin: '970437', shortName: 'HDBank', fullName: 'Ngân hàng TMCP Phát triển Thành phố Hồ Chí Minh', bankGroup: 'STATE_POPULAR' },
  { bankCode: 'VIB', bin: '970441', shortName: 'VIB', fullName: 'Ngân hàng TMCP Quốc tế Việt Nam', bankGroup: 'STATE_POPULAR' },
  { bankCode: 'SHB', bin: '970443', shortName: 'SHB', fullName: 'Ngân hàng TMCP Sài Gòn - Hà Nội', bankGroup: 'COMMERCIAL' },
  { bankCode: 'MSB', bin: '970426', shortName: 'MSB', fullName: 'Ngân hàng TMCP Hàng Hải Việt Nam', bankGroup: 'COMMERCIAL' },
  { bankCode: 'OCB', bin: '970448', shortName: 'OCB', fullName: 'Ngân hàng TMCP Phương Đông', bankGroup: 'COMMERCIAL' },
  { bankCode: 'LPB', bin: '970449', shortName: 'LPBank', fullName: 'Ngân hàng TMCP Lộc Phát Việt Nam', bankGroup: 'COMMERCIAL' },
  { bankCode: 'SEAB', bin: '970440', shortName: 'SeABank', fullName: 'Ngân hàng TMCP Đông Nam Á', bankGroup: 'COMMERCIAL' },
  { bankCode: 'EIB', bin: '970431', shortName: 'Eximbank', fullName: 'Ngân hàng TMCP Xuất Nhập Khẩu Việt Nam', bankGroup: 'COMMERCIAL' },
  { bankCode: 'NAB', bin: '970428', shortName: 'Nam A Bank', fullName: 'Ngân hàng TMCP Nam Á', bankGroup: 'COMMERCIAL' },
  { bankCode: 'PVCB', bin: '970412', shortName: 'PVcomBank', fullName: 'Ngân hàng TMCP Đại Chúng Việt Nam', bankGroup: 'COMMERCIAL' },
  { bankCode: 'BAB', bin: '970409', shortName: 'Bac A Bank', fullName: 'Ngân hàng TMCP Bắc Á', bankGroup: 'COMMERCIAL' },
  { bankCode: 'ABB', bin: '970425', shortName: 'ABBANK', fullName: 'Ngân hàng TMCP An Bình', bankGroup: 'COMMERCIAL' },
  { bankCode: 'VIETBANK', bin: '970433', shortName: 'VietBank', fullName: 'Ngân hàng TMCP Việt Nam Thương Tín', bankGroup: 'COMMERCIAL' },
  { bankCode: 'BVB', bin: '970454', shortName: 'BVBank', fullName: 'Ngân hàng TMCP Bản Việt', bankGroup: 'COMMERCIAL' },
  { bankCode: 'KLB', bin: '970452', shortName: 'KienlongBank', fullName: 'Ngân hàng TMCP Kiên Long', bankGroup: 'COMMERCIAL' },
  { bankCode: 'NCB', bin: '970419', shortName: 'NCB', fullName: 'Ngân hàng TMCP Quốc Dân', bankGroup: 'COMMERCIAL' },
  { bankCode: 'SGB', bin: '970400', shortName: 'SAIGONBANK', fullName: 'Ngân hàng TMCP Sài Gòn Công Thương', bankGroup: 'COMMERCIAL' },
  { bankCode: 'VAB', bin: '970427', shortName: 'VietABank', fullName: 'Ngân hàng TMCP Việt Á', bankGroup: 'COMMERCIAL' },
  { bankCode: 'PGB', bin: '970430', shortName: 'PGBank', fullName: 'Ngân hàng TMCP Thịnh Vượng và Phát triển', bankGroup: 'COMMERCIAL' },
  { bankCode: 'BAOVIET', bin: '970438', shortName: 'BaoVietBank', fullName: 'Ngân hàng TMCP Bảo Việt', bankGroup: 'COMMERCIAL' },
  { bankCode: 'SHBVN', bin: '970424', shortName: 'Shinhan Bank', fullName: 'Ngân hàng TNHH MTV Shinhan Việt Nam', bankGroup: 'COMMERCIAL' },
  { bankCode: 'WVN', bin: '970457', shortName: 'Woori Bank', fullName: 'Ngân hàng TNHH MTV Woori Việt Nam', bankGroup: 'COMMERCIAL' },
  { bankCode: 'UOB', bin: '970458', shortName: 'UOB', fullName: 'Ngân hàng TNHH MTV United Overseas Bank Việt Nam', bankGroup: 'COMMERCIAL' },
  { bankCode: 'HSBC', bin: '458761', shortName: 'HSBC', fullName: 'Ngân hàng TNHH MTV HSBC Việt Nam', bankGroup: 'COMMERCIAL' },
  { bankCode: 'SCVN', bin: '970410', shortName: 'Standard Chartered', fullName: 'Ngân hàng TNHH MTV Standard Chartered Việt Nam', bankGroup: 'COMMERCIAL' }
]);

const VN_BANKS_BY_CODE = VN_BANKS_CATALOG.reduce((acc, b) => {
  acc[b.bankCode] = b;
  return acc;
}, Object.create(null));

function toBankAccountHolderAscii(rawName) {
  if (!rawName) return '';
  return String(rawName)
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toUpperCase()
    .replace(/[^A-Z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 150);
}

function resolveBankShortName(bankCode) {
  const code = String(bankCode || '').trim().toUpperCase();
  if (!code) return '';
  return VN_BANKS_BY_CODE[code] ? VN_BANKS_BY_CODE[code].shortName : code;
}

function normalizeBankSearchText(raw) {
  if (!raw) return '';
  return String(raw)
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

function rankBanksByQuery(rawQuery) {
  const query = normalizeBankSearchText(rawQuery);
  if (!query) {
    return VN_BANKS_CATALOG.slice();
  }
  const tokens = query.split(' ').filter(Boolean);
  const scored = [];

  VN_BANKS_CATALOG.forEach((bank, idx) => {
    const code = normalizeBankSearchText(bank.bankCode);
    const shortName = normalizeBankSearchText(bank.shortName);
    const fullName = normalizeBankSearchText(bank.fullName);
    const bin = normalizeBankSearchText(bank.bin);

    let score = 0;
    if (code === query || shortName === query || bin === query) {
      score = 1000;
    } else if (code.startsWith(query) || shortName.startsWith(query)) {
      score = 850;
    } else {
      const shortWords = shortName.split(/[\s\-\/]+/);
      const fullWords = fullName.split(/[\s\-\/]+/);
      if (bin.startsWith(query) || shortWords.some(w => w.startsWith(query)) || fullWords.some(w => w.startsWith(query))) {
        score = 680;
      } else if (code.includes(query) || shortName.includes(query)) {
        score = 500;
      } else if (fullName.includes(query) || bin.includes(query)) {
        score = 320;
      } else if (tokens.length > 1) {
        const haystack = `${code} ${shortName} ${fullName} ${bin}`;
        if (tokens.every(t => haystack.includes(t))) {
          score = 200;
        }
      }
    }

    if (score > 0) {
      const popularBonus = bank.bankGroup === 'POPULAR' ? 10 : 0;
      scored.push({ bank, score: score + popularBonus, idx });
    }
  });

  scored.sort((a, b) => b.score - a.score || a.idx - b.idx);
  return scored.map(entry => entry.bank);
}

function findBankMatchRange(originalText, rawQuery) {
  const query = normalizeBankSearchText(rawQuery);
  const source = String(originalText || '');
  if (!query || !source) return null;

  let normStr = '';
  const normToOrigStart = [];
  const normToOrigEnd = [];
  for (let i = 0; i < source.length; i++) {
    const mapped = source[i]
      .replace(/đ/g, 'd')
      .replace(/Đ/g, 'D')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase();
    for (let j = 0; j < mapped.length; j++) {
      normStr += mapped[j];
      normToOrigStart.push(i);
      normToOrigEnd.push(i + 1);
    }
  }

  const matchIdx = normStr.indexOf(query);
  if (matchIdx === -1) return null;
  const startOrig = normToOrigStart[matchIdx];
  const endOrig = normToOrigEnd[matchIdx + query.length - 1];
  if (startOrig === undefined || endOrig === undefined) return null;
  return { start: startOrig, end: endOrig };
}

function appendHighlightedBankText(parentEl, text, rawQuery) {
  if (!parentEl || typeof document === 'undefined') return;
  const source = String(text || '');
  const range = findBankMatchRange(source, rawQuery);
  if (!range) {
    parentEl.textContent = source;
    return;
  }
  parentEl.textContent = '';
  if (range.start > 0) {
    parentEl.appendChild(document.createTextNode(source.slice(0, range.start)));
  }
  const markEl = document.createElement('mark');
  markEl.className = 'pm-bank-match';
  markEl.textContent = source.slice(range.start, range.end);
  parentEl.appendChild(markEl);
  if (range.end < source.length) {
    parentEl.appendChild(document.createTextNode(source.slice(range.end)));
  }
}

function syncBankComboboxFromSelect() {
  if (typeof document === 'undefined') return;
  const bankSelect = document.getElementById('ccBankCode');
  const searchInput = document.getElementById('ccBankSearchInput');
  const codeBadge = document.getElementById('ccBankSelectedCodeBadge');
  const clearBtn = document.getElementById('btnClearBankSelection');
  if (!bankSelect || !searchInput) return;

  const selectedCode = String(bankSelect.value || '').trim().toUpperCase();
  const bank = selectedCode ? VN_BANKS_BY_CODE[selectedCode] : null;
  if (bank) {
    searchInput.value = bank.shortName;
    if (codeBadge) {
      codeBadge.textContent = bank.bankCode;
      codeBadge.classList.remove('d-none');
    }
    if (clearBtn) clearBtn.classList.remove('d-none');
  } else {
    searchInput.value = '';
    if (codeBadge) {
      codeBadge.textContent = '';
      codeBadge.classList.add('d-none');
    }
    if (clearBtn) clearBtn.classList.add('d-none');
  }
}

function initSearchableBankCombobox() {
  if (typeof document === 'undefined') return;
  const combobox = document.getElementById('ccBankCombobox');
  const searchInput = document.getElementById('ccBankSearchInput');
  const bankSelect = document.getElementById('ccBankCode');
  const dropdownMenu = document.getElementById('ccBankDropdownMenu');
  const listbox = document.getElementById('ccBankDropdownListbox');
  const emptyEl = document.getElementById('ccBankDropdownEmpty');
  const countTextEl = document.getElementById('ccBankResultCountText');
  const codeBadge = document.getElementById('ccBankSelectedCodeBadge');
  const clearBtn = document.getElementById('btnClearBankSelection');
  const toggleBtn = document.getElementById('btnToggleBankDropdown');
  if (!combobox || !searchInput || !bankSelect || !dropdownMenu || !listbox) return;

  let isOpen = false;
  let activeIndex = -1;
  let renderedItems = [];

  function setActiveIndex(nextIdx, options = {}) {
    if (!renderedItems.length) {
      activeIndex = -1;
      searchInput.removeAttribute('aria-activedescendant');
      return;
    }
    const clamped = ((nextIdx % renderedItems.length) + renderedItems.length) % renderedItems.length;
    activeIndex = clamped;
    renderedItems.forEach((item, idx) => {
      const isAct = idx === activeIndex;
      item.el.classList.toggle('is-active', isAct);
    });
    const activeItem = renderedItems[activeIndex];
    if (activeItem) {
      searchInput.setAttribute('aria-activedescendant', activeItem.optionId);
      if (options.scrollIntoView !== false && typeof activeItem.el.scrollIntoView === 'function') {
        activeItem.el.scrollIntoView({ block: 'nearest' });
      }
    }
  }

  function appendOptionRow(bank, rawQuery, selectedCode) {
    const idx = renderedItems.length;
    const optionId = `ccBankOption_${bank.bankCode}`;
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.id = optionId;
    btn.className = 'pm-bank-option';
    btn.setAttribute('role', 'option');
    const isSelected = Boolean(selectedCode && bank.bankCode === selectedCode);
    btn.setAttribute('aria-selected', isSelected ? 'true' : 'false');
    if (isSelected) btn.classList.add('is-selected');

    const codeSpan = document.createElement('span');
    codeSpan.className = 'pm-bank-opt-code';
    appendHighlightedBankText(codeSpan, bank.bankCode, rawQuery);

    const bodyDiv = document.createElement('div');
    bodyDiv.className = 'pm-bank-opt-body';

    const shortSpan = document.createElement('span');
    shortSpan.className = 'pm-bank-opt-short';
    appendHighlightedBankText(shortSpan, bank.shortName, rawQuery);

    const fullSpan = document.createElement('span');
    fullSpan.className = 'pm-bank-opt-full';
    const fullWithBin = `${bank.fullName} · BIN ${bank.bin}`;
    appendHighlightedBankText(fullSpan, fullWithBin, rawQuery);

    const checkIcon = document.createElement('i');
    checkIcon.className = 'bi bi-check2 pm-bank-opt-check';
    checkIcon.setAttribute('aria-hidden', 'true');

    bodyDiv.appendChild(shortSpan);
    bodyDiv.appendChild(fullSpan);
    btn.appendChild(codeSpan);
    btn.appendChild(bodyDiv);
    btn.appendChild(checkIcon);

    btn.addEventListener('mousedown', function (e) {
      if (e && typeof e.preventDefault === 'function') e.preventDefault();
    });
    btn.addEventListener('click', function () {
      commitBankSelection(bank, { focusAccountInput: true });
    });

    listbox.appendChild(btn);
    renderedItems.push({ bank, el: btn, optionId });
    return idx;
  }

  function renderBankList(rawQuery) {
    const query = String(rawQuery || '').trim();
    const matches = rankBanksByQuery(query);
    const selectedCode = String(bankSelect.value || '').trim().toUpperCase();

    if (typeof listbox.replaceChildren === 'function') {
      listbox.replaceChildren();
    } else {
      listbox.innerHTML = '';
      if (Array.isArray(listbox.children)) listbox.children.length = 0;
    }
    renderedItems = [];

    if (countTextEl) {
      countTextEl.textContent = query
        ? `Tìm thấy ${matches.length} ngân hàng phù hợp`
        : `${matches.length} ngân hàng hỗ trợ`;
    }

    if (matches.length === 0) {
      listbox.classList.add('d-none');
      if (emptyEl) emptyEl.classList.remove('d-none');
      activeIndex = -1;
      searchInput.removeAttribute('aria-activedescendant');
      return;
    }

    listbox.classList.remove('d-none');
    if (emptyEl) emptyEl.classList.add('d-none');

    if (!query) {
      const popular = matches.filter(b => b.bankGroup === 'POPULAR');
      const commercial = matches.filter(b => b.bankGroup !== 'POPULAR');
      if (popular.length) {
        const popHeader = document.createElement('div');
        popHeader.className = 'pm-bank-group-label';
        popHeader.textContent = 'Ngân hàng phổ biến';
        listbox.appendChild(popHeader);
        popular.forEach(bank => appendOptionRow(bank, '', selectedCode));
      }
      if (commercial.length) {
        const comHeader = document.createElement('div');
        comHeader.className = 'pm-bank-group-label';
        comHeader.textContent = 'Ngân hàng TMCP khác & Quốc tế';
        listbox.appendChild(comHeader);
        commercial.forEach(bank => appendOptionRow(bank, '', selectedCode));
      }
      const selIdx = renderedItems.findIndex(item => item.bank.bankCode === selectedCode);
      setActiveIndex(selIdx >= 0 ? selIdx : 0, { scrollIntoView: selIdx >= 0 });
    } else {
      matches.forEach(bank => appendOptionRow(bank, query, selectedCode));
      // Auto-point to top search hit so pressing Enter immediately picks it
      setActiveIndex(0, { scrollIntoView: false });
    }
  }

  function openDropdown(queryOverride) {
    const query = queryOverride !== undefined ? queryOverride : '';
    renderBankList(query);
    isOpen = true;
    combobox.classList.add('is-open');
    dropdownMenu.classList.remove('d-none');
    searchInput.setAttribute('aria-expanded', 'true');
    if (toggleBtn) toggleBtn.setAttribute('aria-expanded', 'true');
  }

  function closeDropdown(options = {}) {
    if (!isOpen && dropdownMenu.classList.contains('d-none')) return;
    isOpen = false;
    combobox.classList.remove('is-open');
    dropdownMenu.classList.add('d-none');
    searchInput.setAttribute('aria-expanded', 'false');
    searchInput.removeAttribute('aria-activedescendant');
    if (toggleBtn) toggleBtn.setAttribute('aria-expanded', 'false');
    if (options.syncDisplay !== false) {
      syncBankComboboxFromSelect();
    }
  }

  function dispatchBankSelectChange() {
    if (typeof window !== 'undefined' && typeof window.Event === 'function') {
      bankSelect.dispatchEvent(new window.Event('change', { bubbles: true }));
    } else if (typeof bankSelect.dispatchEvent === 'function') {
      bankSelect.dispatchEvent({ type: 'change', bubbles: true });
    }
  }

  function commitBankSelection(bank, options = {}) {
    if (!bank) return;
    const prevValue = bankSelect.value;
    bankSelect.value = bank.bankCode;
    syncBankComboboxFromSelect();
    clearFieldValidationError(bankSelect);
    if (prevValue !== bank.bankCode) {
      dispatchBankSelectChange();
      markDraftUnsaved();
    }
    closeDropdown({ syncDisplay: false });
    if (options.focusAccountInput) {
      document.getElementById('ccBankAccountNumber')?.focus();
    }
  }

  searchInput.addEventListener('focus', function () {
    if (!isOpen) {
      openDropdown('');
      if (typeof searchInput.select === 'function' && searchInput.value) {
        searchInput.select();
      }
    }
  });

  searchInput.addEventListener('click', function () {
    if (!isOpen) {
      openDropdown('');
    }
  });

  searchInput.addEventListener('input', function () {
    const rawVal = searchInput.value || '';
    const selectedCode = String(bankSelect.value || '').trim().toUpperCase();
    const currentBank = selectedCode ? VN_BANKS_BY_CODE[selectedCode] : null;
    if (codeBadge) {
      const sameSelectedDisplay = Boolean(
        currentBank && normalizeBankSearchText(rawVal) === normalizeBankSearchText(currentBank.shortName)
      );
      if (sameSelectedDisplay) {
        codeBadge.textContent = currentBank.bankCode;
        codeBadge.classList.remove('d-none');
      } else {
        codeBadge.classList.add('d-none');
      }
    }
    if (clearBtn) {
      clearBtn.classList.toggle('d-none', !rawVal.trim() && !selectedCode);
    }
    openDropdown(rawVal);
  });

  searchInput.addEventListener('keydown', function (e) {
    if (!e) return;
    const key = e.key;
    if (key === 'ArrowDown') {
      if (typeof e.preventDefault === 'function') e.preventDefault();
      if (!isOpen) {
        openDropdown(searchInput.value && !VN_BANKS_BY_CODE[bankSelect.value] ? searchInput.value : '');
      } else if (renderedItems.length) {
        setActiveIndex(activeIndex + 1);
      }
      return;
    }
    if (key === 'ArrowUp') {
      if (typeof e.preventDefault === 'function') e.preventDefault();
      if (!isOpen) {
        openDropdown('');
      } else if (renderedItems.length) {
        setActiveIndex(activeIndex - 1);
      }
      return;
    }
    if (key === 'Home' && isOpen && renderedItems.length) {
      if (typeof e.preventDefault === 'function') e.preventDefault();
      setActiveIndex(0);
      return;
    }
    if (key === 'End' && isOpen && renderedItems.length) {
      if (typeof e.preventDefault === 'function') e.preventDefault();
      setActiveIndex(renderedItems.length - 1);
      return;
    }
    if (key === 'Enter') {
      if (isOpen && activeIndex >= 0 && renderedItems[activeIndex]) {
        if (typeof e.preventDefault === 'function') e.preventDefault();
        commitBankSelection(renderedItems[activeIndex].bank, { focusAccountInput: true });
      }
      return;
    }
    if (key === 'Escape') {
      if (isOpen) {
        if (typeof e.preventDefault === 'function') e.preventDefault();
        closeDropdown({ syncDisplay: true });
      }
      return;
    }
    if (key === 'Tab') {
      closeDropdown({ syncDisplay: true });
    }
  });

  searchInput.addEventListener('blur', function () {
    closeDropdown({ syncDisplay: true });
  });

  if (toggleBtn) {
    toggleBtn.addEventListener('mousedown', function (e) {
      if (e && typeof e.preventDefault === 'function') e.preventDefault();
    });
    toggleBtn.addEventListener('click', function () {
      if (isOpen) {
        closeDropdown({ syncDisplay: true });
      } else {
        searchInput.focus();
        openDropdown('');
      }
    });
  }

  if (clearBtn) {
    clearBtn.addEventListener('mousedown', function (e) {
      if (e && typeof e.preventDefault === 'function') e.preventDefault();
    });
    clearBtn.addEventListener('click', function () {
      bankSelect.value = '';
      syncBankComboboxFromSelect();
      clearFieldValidationError(bankSelect);
      dispatchBankSelectChange();
      markDraftUnsaved();
      searchInput.focus();
      openDropdown('');
    });
  }

  document.addEventListener('mousedown', function (e) {
    if (!isOpen) return;
    const target = e ? e.target : null;
    const isInside = target && typeof combobox.contains === 'function'
      ? combobox.contains(target)
      : target === combobox || target === searchInput || target === toggleBtn || target === clearBtn;
    if (!isInside) {
      closeDropdown({ syncDisplay: true });
    }
  });

  syncBankComboboxFromSelect();
}

/**
 * Runtime Controller State & In-Memory Caches
 */
const PM_CONTRACT_CREATE_STATE = {
  currentUser: null,
  userRole: null,
  canSwitchBranch: true,
  userAssignedBranchId: null,
  activeBranchId: 1,
  branchCashBalance: 520000000,
  branchNameLabel: 'Chi nhánh Quận 1 (CN-HCM-01)',
  provincesCache: null,
  districtsCache: new Map(),
  wardsCache: new Map(),
  serverEvaluatedMaxLtvRatio: null,
  ltvDebounceTimer: null,
  activeScheduleCycles: [],
  errorSummaryActive: false,
  lastErrorSignature: '',
  bankHolderEditedManually: false,
  lastInterestType: 'DAILY_PER_MILLION'
};

function syncBankAccountHolderFromCustomer(force) {
  if (typeof document === 'undefined') return;
  const holderEl = document.getElementById('ccBankAccountHolder');
  const custNameEl = document.getElementById('ccCustomerName');
  if (!holderEl || !custNameEl) return;
  const asciiName = toBankAccountHolderAscii(custNameEl.value || '');
  if (force || !PM_CONTRACT_CREATE_STATE.bankHolderEditedManually || !holderEl.value.trim()) {
    holderEl.value = asciiName;
    if (force) {
      PM_CONTRACT_CREATE_STATE.bankHolderEditedManually = false;
    }
  }
  evaluateBankHolderMismatchWarning();
}

function evaluateBankHolderMismatchWarning() {
  if (typeof document === 'undefined') return;
  const warnBox = document.getElementById('ccBankHolderMismatchWarning');
  const warnText = document.getElementById('ccBankHolderMismatchText');
  if (!warnBox) return;

  const isBank = document.getElementById('ccDisbursementMethod')?.value === 'BANK_TRANSFER';
  const expectedHolder = toBankAccountHolderAscii(document.getElementById('ccCustomerName')?.value || '');
  const enteredHolder = toBankAccountHolderAscii(document.getElementById('ccBankAccountHolder')?.value || '');

  const mismatch = Boolean(isBank && expectedHolder && enteredHolder && enteredHolder !== expectedHolder);
  warnBox.classList.toggle('d-none', !mismatch);
  if (mismatch && warnText) {
    warnText.textContent = `Tên chủ tài khoản (${enteredHolder}) khác với tên khách hàng trên CCCD (${expectedHolder}). Vui lòng xác minh chính chủ trước khi giải ngân.`;
  }
}

function getAuthToken() {
  try {
    const sessionToken = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('pm_access_token') : null;
    const localToken = typeof localStorage !== 'undefined' ? localStorage.getItem('pm_access_token') : null;
    return sessionToken || localToken || null;
  } catch (e) {
    return null;
  }
}

function isHttpProtocol() {
  return Boolean(typeof window !== 'undefined' && window.location && window.location.protocol && window.location.protocol.startsWith('http'));
}

function isStaticPreviewMode() {
  if (typeof window === 'undefined' || !window.location) return true;
  const proto = String(window.location.protocol || '').toLowerCase();
  const pathname = String(window.location.pathname || '').toLowerCase();
  const href = String(window.location.href || '').toLowerCase();
  const cleanHref = href.split('?')[0].split('#')[0];
  return proto === 'file:' || pathname.endsWith('.html') || cleanHref.endsWith('.html');
}

function isBackendServerMode() {
  return isHttpProtocol() && !isStaticPreviewMode();
}

function resolvePageRoute(springRoute, staticFile, queryString = '') {
  const base = isStaticPreviewMode() ? staticFile : springRoute;
  return queryString ? `${base}?${queryString}` : base;
}

function setLoadingBarActive(isActive) {
  const bar = document.getElementById('contractLoadingBar');
  if (bar) {
    bar.classList.toggle('is-loading', Boolean(isActive));
  }
}

/**
 * Authenticated API Fetch Helper (Consistent with dashboard.js JWT + BaseResponse contract)
 */
async function apiFetch(url, options = {}) {
  const isFormDataBody = typeof FormData !== 'undefined' && options.body instanceof FormData;
  const baseHeaders = isFormDataBody
    ? { 'Accept': 'application/json' }
    : { 'Content-Type': 'application/json', 'Accept': 'application/json' };
  const headers = Object.assign(baseHeaders, options.headers || {});
  if (isFormDataBody) {
    delete headers['Content-Type'];
  }
  const token = getAuthToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
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
    } catch (e) {
      // Ignore storage cleanup error
    }
    if (typeof window !== 'undefined' && window.location) {
      window.location.href = resolvePageRoute('/login', 'login.html');
    }
    const authErr = new Error((payload && payload.message) || 'Phiên đăng nhập đã hết hạn');
    authErr.status = 401;
    throw authErr;
  }

  if (response.status === 403 || (payload && payload.code === 4004)) {
    const err = new Error((payload && payload.message) || 'Tài khoản không có quyền thao tác trên chi nhánh này (Mã lỗi: 4004).');
    err.status = 403;
    err.code = (payload && payload.code) || 4004;
    err.isBranchAccessDenied = true;
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
 * XSS-Safe Toast Notification Helper (#pmToastContainer)
 */
function showToast(message, type = 'success') {
  let toastContainer = document.getElementById('pmToastContainer');
  if (!toastContainer && typeof document !== 'undefined' && document.body) {
    toastContainer = document.createElement('div');
    toastContainer.id = 'pmToastContainer';
    toastContainer.className = 'toast-container position-fixed bottom-0 end-0 p-3';
    toastContainer.style.zIndex = '1090';
    document.body.appendChild(toastContainer);
  }
  if (!toastContainer) return;

  const toastId = 'cc_toast_' + Date.now() + '_' + Math.floor(Math.random() * 1000);
  const iconClass = type === 'success'
    ? 'bi-check-circle-fill text-success'
    : (type === 'danger'
      ? 'bi-shield-exclamation text-danger'
      : (type === 'warning' ? 'bi-exclamation-triangle-fill text-warning' : 'bi-info-circle-fill text-primary'));

  const toastHtml = `
    <div id="${toastId}" class="toast align-items-center border shadow-sm bg-white" role="alert" aria-live="assertive" aria-atomic="true">
      <div class="d-flex">
        <div class="toast-body d-flex align-items-center gap-2 text-dark fw-medium">
          <i class="bi ${iconClass}" aria-hidden="true"></i>
          <span>${escapeHtml(message)}</span>
        </div>
        <button type="button" class="btn-close me-2 m-auto" data-bs-dismiss="toast" aria-label="Đóng thông báo"></button>
      </div>
    </div>
  `;

  toastContainer.insertAdjacentHTML('beforeend', toastHtml);
  const toastEl = document.getElementById(toastId);
  if (toastEl && typeof bootstrap !== 'undefined' && bootstrap.Toast) {
    const toast = new bootstrap.Toast(toastEl, { delay: 4200 });
    toast.show();
    toastEl.addEventListener('hidden.bs.toast', () => toastEl.remove());
  }
}

/**
 * Prevent double-submission on financial forms
 */
async function withSubmitLock(btnEl, asyncTask) {
  if (btnEl && btnEl.dataset.submitting === 'true') {
    return;
  }
  const originalHtml = btnEl ? btnEl.innerHTML : '';
  if (btnEl) {
    btnEl.dataset.submitting = 'true';
    btnEl.disabled = true;
    btnEl.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span><span>Đang xử lý hồ sơ...</span>';
  }
  setLoadingBarActive(true);
  try {
    await asyncTask();
  } finally {
    setLoadingBarActive(false);
    if (btnEl) {
      btnEl.dataset.submitting = 'false';
      btnEl.disabled = false;
      btnEl.innerHTML = originalHtml;
    }
  }
}

/**
 * Initialize Topbar Live Clock, Auth Session Sync & Logout
 */
function initTopbarAndAuth() {
  const clockEl = document.getElementById('liveClock');
  if (clockEl) {
    const updateClock = () => {
      const now = new Date();
      const days = ['Chủ Nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];
      const dateStr = formatSystemDate(now);
      const timeStr = now.toLocaleTimeString(getSystemLocale(), { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
      clockEl.textContent = `${days[now.getDay()]}, ${dateStr} - ${timeStr} (Giờ VN)`;
    };
    updateClock();
    setInterval(updateClock, 1000);
  }

  if (typeof document !== 'undefined' && document.body) {
    const ssrRole = (document.body.getAttribute('data-user-role') || '').trim();
    const ssrBranchId = (document.body.getAttribute('data-user-branch-id') || '').trim();
    const ssrCanSwitch = document.body.getAttribute('data-can-switch-branch');
    if (ssrRole) {
      PM_CONTRACT_CREATE_STATE.userRole = ssrRole.toUpperCase();
      PM_CONTRACT_CREATE_STATE.canSwitchBranch = ssrRole.toUpperCase() === 'ADMIN';
    } else if (ssrCanSwitch != null && ssrCanSwitch !== '') {
      PM_CONTRACT_CREATE_STATE.canSwitchBranch = ssrCanSwitch === 'true';
    }
    if (ssrBranchId) {
      PM_CONTRACT_CREATE_STATE.userAssignedBranchId = Number(ssrBranchId);
    }
  }

  try {
    const sessionRaw = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('pm_current_user') : null;
    const localRaw = typeof localStorage !== 'undefined'
      ? (localStorage.getItem('pm_current_user') || localStorage.getItem('pm_user'))
      : null;
    const rawUser = sessionRaw || localRaw;
    if (rawUser) {
      const user = JSON.parse(rawUser);
      PM_CONTRACT_CREATE_STATE.currentUser = user;
      if (user.role) {
        PM_CONTRACT_CREATE_STATE.userRole = String(user.role).trim().toUpperCase();
        PM_CONTRACT_CREATE_STATE.canSwitchBranch = PM_CONTRACT_CREATE_STATE.userRole === 'ADMIN';
      }
      if (user.branchId != null) {
        PM_CONTRACT_CREATE_STATE.userAssignedBranchId = Number(user.branchId);
      }

      const nameEl = document.getElementById('topbarUserName');
      const roleEl = document.getElementById('topbarUserRole');
      const avatarEl = document.getElementById('topbarUserAvatar');
      if (nameEl && user.fullName) nameEl.textContent = user.fullName;
      if (roleEl && (user.roleLabel || user.role)) {
        roleEl.textContent = user.roleLabel || `Vai trò: ${user.role}`;
      }
      if (avatarEl && (user.avatar || user.fullName)) {
        const initials = user.avatar || String(user.fullName)
          .trim()
          .split(/\s+/)
          .slice(-2)
          .map(w => w.charAt(0).toUpperCase())
          .join('');
        if (initials) avatarEl.textContent = initials;
      }

      if (!PM_CONTRACT_CREATE_STATE.canSwitchBranch && PM_CONTRACT_CREATE_STATE.userAssignedBranchId != null) {
        const branchSel = document.getElementById('ccBranchId');
        if (branchSel) {
          Array.from(branchSel.options).forEach(opt => {
            if (String(opt.value) !== String(PM_CONTRACT_CREATE_STATE.userAssignedBranchId)) {
              opt.remove();
            }
          });
          branchSel.disabled = true;
          branchSel.setAttribute('title', 'Chi nhánh được cố định theo tài khoản nhân viên. Liên hệ Quản lý nếu cần chuyển đổi.');
        }
      }
    }
  } catch (e) {
    // Ignore storage read error
  }

  const branchTipEl = document.getElementById('tipBranchLock');
  if (branchTipEl) {
    const lockMsg = PM_CONTRACT_CREATE_STATE.canSwitchBranch
      ? 'Tài khoản Quản trị / Quản lý được phép chuyển đổi chi nhánh lập hợp đồng.'
      : 'Chi nhánh được cố định theo tài khoản nhân viên (STAFF). Liên hệ Quản lý nếu cần chuyển đổi.';
    branchTipEl.setAttribute('title', lockMsg);
    branchTipEl.setAttribute('data-bs-original-title', lockMsg);
  }

  const btnLogout = document.getElementById('btnLogout');
  if (btnLogout) {
    btnLogout.addEventListener('click', async function (e) {
      e.preventDefault();
      try {
        if (typeof fetch !== 'undefined' && isBackendServerMode()) {
          await fetch('/api/v1/auth/logout', { method: 'POST' });
        }
      } catch (err) {
        // Ignore network error during logout
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
        // Ignore cleanup error
      }
      if (typeof window !== 'undefined' && window.location) {
        window.location.href = resolvePageRoute('/login', 'login.html');
      }
    });
  }
}

/**
 * Sync Branch Selection (#ccBranchId) with Topbar Cash, Inline Hint, Sticky Ledger & Cash Gate
 */
function syncBranchTelemetryFromSelector() {
  const branchSelect = document.getElementById('ccBranchId');
  const branchHint = document.getElementById('ccBranchCashHint');
  const topbarCash = document.getElementById('topbarCashBalance');
  const sumBranchName = document.getElementById('sumBranchName');
  const sumBranchCash = document.getElementById('sumBranchCashAvailable');

  if (!branchSelect || branchSelect.options.length === 0) return;
  const selectedOpt = branchSelect.options[branchSelect.selectedIndex] || branchSelect.options[0];
  if (!selectedOpt) return;

  const branchId = Number(selectedOpt.value || 1);
  const attrCash = selectedOpt.getAttribute('data-cash');
  const cashVal = attrCash != null && attrCash !== ''
    ? Number(attrCash)
    : parseVND(topbarCash ? topbarCash.textContent : 520000000);
  const optName = selectedOpt.getAttribute('data-name') || '';
  const optCode = selectedOpt.getAttribute('data-code') || '';
  const displayLabel = optName && optCode
    ? `${optName} (${optCode})`
    : (selectedOpt.textContent || 'Chi nhánh Quận 1 (CN-HCM-01)').trim();

  PM_CONTRACT_CREATE_STATE.activeBranchId = branchId;
  PM_CONTRACT_CREATE_STATE.branchCashBalance = cashVal;
  PM_CONTRACT_CREATE_STATE.branchNameLabel = displayLabel;

  if (branchHint) {
    branchHint.innerHTML = `<i class="bi bi-safe2 text-success" aria-hidden="true"></i><span>Quỹ khả dụng tại quầy: <strong class="font-tabular">${formatVND(cashVal)}</strong></span>`;
  }
  if (topbarCash) {
    topbarCash.textContent = formatVND(cashVal);
  }
  if (sumBranchName) {
    sumBranchName.textContent = displayLabel;
  }
  if (sumBranchCash) {
    sumBranchCash.textContent = formatVND(cashVal);
  }

  const backLink = document.getElementById('btnBackToDashboard');
  if (backLink) {
    const qs = branchId ? `branchId=${encodeURIComponent(branchId)}` : '';
    backLink.setAttribute('href', resolvePageRoute('/dashboard', 'dashboard.html', qs));
  }
}

/**
 * ============================================================================
 * 1. DB-DRIVEN 3-TIER CASCADING ADMINISTRATIVE DROPDOWNS
 *    (/api/v1/branches/provinces -> /districts -> /wards)
 * ============================================================================
 */
const PM_ADDRESS_CASCADE = {
  setCascadeError(message) {
    const errBox = document.getElementById('ccAddressCascadeError');
    const errText = document.getElementById('ccAddressCascadeErrorText');
    if (!errBox) return;
    if (message) {
      if (errText) errText.textContent = message;
      errBox.classList.remove('d-none');
    } else {
      errBox.classList.add('d-none');
    }
  },

  clearCascadeError() {
    this.setCascadeError('');
  },

  getSelectedOptionLabel(selectEl) {
    if (!selectEl || !selectEl.value) return '';
    const opt = selectEl.options[selectEl.selectedIndex];
    return opt ? (opt.textContent || '').trim() : '';
  },

  stripAdminSuffixesFromStreet(rawStreetText, adminLabels = []) {
    const raw = String(rawStreetText || '').trim();
    if (!raw) return '';
    const segments = raw.split(',').map(s => s.trim()).filter(Boolean);
    if (segments.length <= 1) return raw;

    const knownAdminKeys = new Set([
      'tp hcm', 'tphcm', 'tp ho chi minh', 'ho chi minh', 'sai gon',
      'tp hn', 'tp ha noi', 'ha noi', 'da nang', 'tp da nang',
      'can tho', 'tp can tho', 'binh duong', 'dong nai'
    ]);

    adminLabels.filter(Boolean).forEach(label => {
      const fullNorm = normalizeVnSearchKey(label);
      if (fullNorm) {
        knownAdminKeys.add(fullNorm);
        const shortNorm = fullNorm
          .replace(/^(thanh pho|tinh|quan|huyen|thi xa|phuong|xa|thi tran|tp|tt|q|p)\s+/, '')
          .trim();
        if (shortNorm && shortNorm.length >= 2) {
          knownAdminKeys.add(shortNorm);
        }
      }
    });

    while (segments.length > 1) {
      const tailNorm = normalizeVnSearchKey(segments[segments.length - 1]);
      const tailShort = tailNorm
        .replace(/^(thanh pho|tinh|quan|huyen|thi xa|phuong|xa|thi tran|tp|tt|q|p)\s+/, '')
        .trim();
      const hasAdminPrefix = /^(thanh pho|tinh|quan|huyen|thi xa|phuong|xa|thi tran|tp)\s+/.test(tailNorm);
      if (knownAdminKeys.has(tailNorm) || (tailShort && knownAdminKeys.has(tailShort)) || hasAdminPrefix) {
        segments.pop();
      } else {
        break;
      }
    }

    return segments.join(', ');
  },

  composePermanentAddress() {
    const streetEl = document.getElementById('ccAddressStreet');
    const wardEl = document.getElementById('ccWardCode');
    const districtEl = document.getElementById('ccDistrictCode');
    const provinceEl = document.getElementById('ccProvinceCode');
    const fullEl = document.getElementById('ccPermanentAddressFull');
    const sumAddrShort = document.getElementById('sumCustomerAddressShort');

    const wardLabel = this.getSelectedOptionLabel(wardEl);
    const districtLabel = this.getSelectedOptionLabel(districtEl);
    const provinceLabel = this.getSelectedOptionLabel(provinceEl);
    const rawStreet = streetEl ? streetEl.value.trim() : '';
    const street = this.stripAdminSuffixesFromStreet(rawStreet, [wardLabel, districtLabel, provinceLabel]);

    const parts = [street, wardLabel, districtLabel, provinceLabel].filter(Boolean);
    const composedFull = parts.join(', ');
    if (fullEl) {
      fullEl.value = composedFull;
    }

    const adminParts = [wardLabel, districtLabel, provinceLabel].filter(Boolean);
    if (sumAddrShort) {
      sumAddrShort.textContent = adminParts.length > 0
        ? adminParts.join(', ')
        : (composedFull || 'Chưa chọn địa chỉ thường trú');
    }

    return composedFull;
  },

  async fetchProvinces() {
    if (PM_CONTRACT_CREATE_STATE.provincesCache) {
      return PM_CONTRACT_CREATE_STATE.provincesCache;
    }
    if (isBackendServerMode()) {
      try {
        const res = await apiFetch('/api/v1/branches/provinces', { method: 'GET' });
        if (res && Array.isArray(res.data) && res.data.length > 0) {
          PM_CONTRACT_CREATE_STATE.provincesCache = res.data;
          return res.data;
        }
      } catch (e) {
        // Graceful fallback to SSR options or offline catalog
      }
    }
    PM_CONTRACT_CREATE_STATE.provincesCache = OFFLINE_ADMIN_CATALOG.provinces;
    return OFFLINE_ADMIN_CATALOG.provinces;
  },

  async fetchDistricts(provinceCode) {
    const code = String(provinceCode || '').trim();
    if (!code) return [];
    if (PM_CONTRACT_CREATE_STATE.districtsCache.has(code)) {
      return PM_CONTRACT_CREATE_STATE.districtsCache.get(code);
    }
    if (isBackendServerMode()) {
      try {
        const res = await apiFetch(`/api/v1/branches/provinces/${encodeURIComponent(code)}/districts`, { method: 'GET' });
        if (res && Array.isArray(res.data) && res.data.length > 0) {
          this.clearCascadeError();
          PM_CONTRACT_CREATE_STATE.districtsCache.set(code, res.data);
          return res.data;
        }
      } catch (e) {
        const fallbackOnErr = OFFLINE_ADMIN_CATALOG.districts[code] || [];
        this.setCascadeError(
          fallbackOnErr.length > 0
            ? 'Không thể kết nối máy chủ danh mục Quận/Huyện. Đang hiển thị danh mục dự phòng nội bộ.'
            : 'Không thể tải danh sách Quận/Huyện. Vui lòng thử lại hoặc kiểm tra kết nối mạng.'
        );
        PM_CONTRACT_CREATE_STATE.districtsCache.set(code, fallbackOnErr);
        return fallbackOnErr;
      }
    }
    this.clearCascadeError();
    const fallback = OFFLINE_ADMIN_CATALOG.districts[code] || [];
    PM_CONTRACT_CREATE_STATE.districtsCache.set(code, fallback);
    return fallback;
  },

  async fetchWards(districtCode) {
    const code = String(districtCode || '').trim();
    if (!code) return [];
    if (PM_CONTRACT_CREATE_STATE.wardsCache.has(code)) {
      return PM_CONTRACT_CREATE_STATE.wardsCache.get(code);
    }
    if (isBackendServerMode()) {
      try {
        const res = await apiFetch(`/api/v1/branches/districts/${encodeURIComponent(code)}/wards`, { method: 'GET' });
        if (res && Array.isArray(res.data) && res.data.length > 0) {
          this.clearCascadeError();
          PM_CONTRACT_CREATE_STATE.wardsCache.set(code, res.data);
          return res.data;
        }
      } catch (e) {
        const fallbackOnErr = OFFLINE_ADMIN_CATALOG.wards[code] || [];
        this.setCascadeError(
          fallbackOnErr.length > 0
            ? 'Không thể kết nối máy chủ danh mục Phường/Xã. Đang hiển thị danh mục dự phòng nội bộ.'
            : 'Không thể tải danh sách Phường/Xã. Vui lòng thử lại hoặc kiểm tra kết nối mạng.'
        );
        PM_CONTRACT_CREATE_STATE.wardsCache.set(code, fallbackOnErr);
        return fallbackOnErr;
      }
    }
    this.clearCascadeError();
    const fallback = OFFLINE_ADMIN_CATALOG.wards[code] || [];
    PM_CONTRACT_CREATE_STATE.wardsCache.set(code, fallback);
    return fallback;
  },

  async initProvincesDropdown() {
    const provinceEl = document.getElementById('ccProvinceCode');
    if (!provinceEl) return;
    const currentSelected = provinceEl.value || '79';

    const existingRealOptions = Array.from(provinceEl.options).filter(o => o.value !== '');
    const provinces = await this.fetchProvinces();

    if (provinces.length > existingRealOptions.length || existingRealOptions.length === 0) {
      const optionsHtml = ['<option value="">-- Chọn Tỉnh / Thành phố --</option>'];
      provinces.forEach(p => {
        const label = p.fullName || p.name || p.code;
        const selectedAttr = String(p.code) === String(currentSelected) ? ' selected="selected"' : '';
        optionsHtml.push(`<option value="${escapeHtml(p.code)}"${selectedAttr}>${escapeHtml(label)}</option>`);
      });
      provinceEl.innerHTML = optionsHtml.join('');
    } else if (currentSelected) {
      provinceEl.value = currentSelected;
    }
  },

  async loadDistrictOptions(provinceCode, preferredDistrictCode = '') {
    const districtEl = document.getElementById('ccDistrictCode');
    const wardEl = document.getElementById('ccWardCode');
    const loadingEl = document.getElementById('ccDistrictLoading');
    if (!districtEl) return [];

    if (!provinceCode) {
      districtEl.innerHTML = '<option value="">-- Chọn Quận / Huyện --</option>';
      if (wardEl) {
        wardEl.innerHTML = '<option value="">-- Chọn Phường / Xã --</option>';
      }
      this.composePermanentAddress();
      return [];
    }

    if (loadingEl) loadingEl.classList.remove('d-none');
    try {
      const districts = await this.fetchDistricts(provinceCode);
      const html = ['<option value="">-- Chọn Quận / Huyện --</option>'];
      districts.forEach(d => {
        const displayLabel = d.fullName || d.name || d.code;
        const isSel = preferredDistrictCode && String(d.code) === String(preferredDistrictCode);
        html.push(`<option value="${escapeHtml(d.code)}"${isSel ? ' selected="selected"' : ''}>${escapeHtml(displayLabel)}</option>`);
      });
      districtEl.innerHTML = html.join('');
      if (preferredDistrictCode) {
        districtEl.value = String(preferredDistrictCode);
      }

      if (wardEl && !preferredDistrictCode) {
        wardEl.innerHTML = '<option value="">-- Chọn Phường / Xã --</option>';
      }
      this.composePermanentAddress();
      return districts;
    } finally {
      if (loadingEl) loadingEl.classList.add('d-none');
    }
  },

  async loadWardOptions(districtCode, preferredWardCode = '') {
    const wardEl = document.getElementById('ccWardCode');
    const loadingEl = document.getElementById('ccWardLoading');
    if (!wardEl) return [];

    if (!districtCode) {
      wardEl.innerHTML = '<option value="">-- Chọn Phường / Xã --</option>';
      this.composePermanentAddress();
      return [];
    }

    if (loadingEl) loadingEl.classList.remove('d-none');
    try {
      const wards = await this.fetchWards(districtCode);
      const html = ['<option value="">-- Chọn Phường / Xã --</option>'];
      wards.forEach(w => {
        const displayLabel = w.fullName || w.name || w.code;
        const isSel = preferredWardCode && String(w.code) === String(preferredWardCode);
        html.push(`<option value="${escapeHtml(w.code)}"${isSel ? ' selected="selected"' : ''}>${escapeHtml(displayLabel)}</option>`);
      });
      wardEl.innerHTML = html.join('');
      if (preferredWardCode) {
        wardEl.value = String(preferredWardCode);
      }
      this.composePermanentAddress();
      return wards;
    } finally {
      if (loadingEl) loadingEl.classList.add('d-none');
    }
  },

  matchOptionByText(selectEl, addressText) {
    if (!selectEl || !addressText) return '';
    const normalizedAddr = normalizeVnSearchKey(addressText);
    if (!normalizedAddr) return '';

    const aliases = [
      { keys: ['tp hcm', 'tphcm', 'ho chi minh', 'sai gon'], code: '79' },
      { keys: ['ha noi', 'tp hn'], code: '01' },
      { keys: ['da nang'], code: '48' },
      { keys: ['can tho'], code: '92' },
      { keys: ['binh duong'], code: '74' },
      { keys: ['dong nai'], code: '75' }
    ];

    for (const opt of Array.from(selectEl.options)) {
      if (!opt.value) continue;
      const optNorm = normalizeVnSearchKey(opt.textContent);
      if (optNorm && normalizedAddr.includes(optNorm)) {
        return opt.value;
      }
    }

    if (selectEl.id === 'ccProvinceCode') {
      for (const alias of aliases) {
        if (alias.keys.some(k => normalizedAddr.includes(k))) {
          const hasCode = Array.from(selectEl.options).some(o => o.value === alias.code);
          if (hasCode) return alias.code;
        }
      }
    }

    for (const opt of Array.from(selectEl.options)) {
      if (!opt.value) continue;
      const shortNorm = normalizeVnSearchKey(opt.textContent)
        .replace(/^(thanh pho|tinh|quan|huyen|thi xa|phuong|xa|thi tran)\s+/, '')
        .trim();
      if (shortNorm && shortNorm.length >= 2 && normalizedAddr.includes(shortNorm)) {
        return opt.value;
      }
    }

    return '';
  },

  /**
   * Reverse-hydrate 3-tier cascading dropdowns + street from Customer DTO
   */
  async hydrateCustomerAddress(customer) {
    const provinceEl = document.getElementById('ccProvinceCode');
    const districtEl = document.getElementById('ccDistrictCode');
    const wardEl = document.getElementById('ccWardCode');
    const streetEl = document.getElementById('ccAddressStreet');
    const sameCheckbox = document.getElementById('ccSameAsPermanent');
    const currentGroup = document.getElementById('ccCurrentAddressGroup');
    const currentInput = document.getElementById('ccCurrentAddressInput');

    if (!provinceEl || !districtEl || !wardEl) return;

    if (provinceEl.options.length <= 1) {
      await this.initProvincesDropdown();
    }

    const rawPermanent = (customer && (customer.permanentAddress || customer.fullAddress || customer.address)) || '';
    const rawCurrent = (customer && customer.address) || '';

    const directProvCode = (customer && customer.provinceCode) ? String(customer.provinceCode).trim() : '';
    const directDistCode = (customer && customer.districtCode) ? String(customer.districtCode).trim() : '';
    const directWardCode = (customer && customer.wardCode) ? String(customer.wardCode).trim() : '';

    let provCode = directProvCode;
    if (!provCode && rawPermanent) {
      provCode = this.matchOptionByText(provinceEl, rawPermanent);
    }

    provinceEl.value = provCode;
    await this.loadDistrictOptions(provCode, directDistCode);

    let distCode = directDistCode;
    if (!distCode && rawPermanent) {
      distCode = this.matchOptionByText(districtEl, rawPermanent);
    }
    if (distCode) {
      districtEl.value = distCode;
    }

    await this.loadWardOptions(distCode, directWardCode);

    let wardCode = directWardCode;
    if (!wardCode && rawPermanent) {
      wardCode = this.matchOptionByText(wardEl, rawPermanent);
    }
    if (wardCode) {
      wardEl.value = wardCode;
    }

    const wardLabel = this.getSelectedOptionLabel(wardEl);
    const districtLabel = this.getSelectedOptionLabel(districtEl);
    const provinceLabel = this.getSelectedOptionLabel(provinceEl);

    if (streetEl) {
      const explicitDetail = (customer && (customer.addressDetail || customer.street))
        ? String(customer.addressDetail || customer.street).trim()
        : '';
      const candidateStreet = explicitDetail || rawPermanent;
      streetEl.value = this.stripAdminSuffixesFromStreet(candidateStreet, [
        wardLabel,
        districtLabel,
        provinceLabel,
        customer && customer.wardName,
        customer && customer.districtName,
        customer && customer.provinceName
      ]);
    }

    const composed = this.composePermanentAddress();

    if (sameCheckbox && currentGroup && currentInput) {
      const hasDistinctCurrent = Boolean(
        rawCurrent &&
        composed &&
        normalizeVnSearchKey(rawCurrent) !== normalizeVnSearchKey(composed) &&
        (!rawPermanent || normalizeVnSearchKey(rawCurrent) !== normalizeVnSearchKey(rawPermanent))
      );
      sameCheckbox.checked = !hasDistinctCurrent;
      currentGroup.classList.toggle('d-none', !hasDistinctCurrent);
      currentInput.value = hasDistinctCurrent ? rawCurrent : '';
    }
  }
};

function initCascadingAddressListeners() {
  const provinceEl = document.getElementById('ccProvinceCode');
  const districtEl = document.getElementById('ccDistrictCode');
  const wardEl = document.getElementById('ccWardCode');
  const streetEl = document.getElementById('ccAddressStreet');
  const sameCheckbox = document.getElementById('ccSameAsPermanent');
  const currentGroup = document.getElementById('ccCurrentAddressGroup');

  if (provinceEl) {
    provinceEl.addEventListener('change', async function () {
      clearFieldValidationError(provinceEl);
      await PM_ADDRESS_CASCADE.loadDistrictOptions(provinceEl.value, '');
      markDraftUnsaved();
    });
  }

  if (districtEl) {
    districtEl.addEventListener('change', async function () {
      clearFieldValidationError(districtEl);
      await PM_ADDRESS_CASCADE.loadWardOptions(districtEl.value, '');
      markDraftUnsaved();
    });
  }

  if (wardEl) {
    wardEl.addEventListener('change', function () {
      clearFieldValidationError(wardEl);
      PM_ADDRESS_CASCADE.composePermanentAddress();
      markDraftUnsaved();
    });
  }

  if (streetEl) {
    streetEl.addEventListener('input', function () {
      clearFieldValidationError(streetEl);
      PM_ADDRESS_CASCADE.composePermanentAddress();
      markDraftUnsaved();
    });
  }

  if (sameCheckbox && currentGroup) {
    sameCheckbox.addEventListener('change', function () {
      currentGroup.classList.toggle('d-none', sameCheckbox.checked);
      if (!sameCheckbox.checked) {
        const curInput = document.getElementById('ccCurrentAddressInput');
        if (curInput) curInput.focus();
      }
      markDraftUnsaved();
    });
  }

  // Hydrate initial province/district/ward options preserving default selection ('79' -> '760' -> '26740' Phường Bến Nghé)
  (async () => {
    const initialProv = provinceEl ? (provinceEl.value || '79') : '79';
    const initialDist = districtEl ? (districtEl.value || '760') : '760';
    const initialWardLabel = wardEl && wardEl.options && wardEl.options[wardEl.selectedIndex]
      ? (wardEl.options[wardEl.selectedIndex].textContent || '')
      : '';
    let initialWard = wardEl ? (wardEl.value || '26740') : '26740';
    if (initialWard === '26734' && initialWardLabel.includes('Bến Nghé')) {
      initialWard = '26740';
    }
    await PM_ADDRESS_CASCADE.initProvincesDropdown();
    await PM_ADDRESS_CASCADE.loadDistrictOptions(initialProv, initialDist);
    await PM_ADDRESS_CASCADE.loadWardOptions(initialDist, initialWard);
    PM_ADDRESS_CASCADE.composePermanentAddress();
  })();
}

/**
 * ============================================================================
 * 2. SMART CCCD LOOKUP & INLINE CUSTOMER CREATE / UPDATE
 * ============================================================================
 */
function normalizeCccdDigits(raw) {
  return String(raw || '').replace(/\D+/g, '').slice(0, 12);
}

function isValidCccdLength(cccd) {
  return cccd.length === 9 || cccd.length === 12;
}

function updateAllowedDocsForOccupation(preserveExistingValid = true) {
  const occSelect = document.getElementById('ccOccupationGroup');
  const docSelect = document.getElementById('ccVerificationDocType');
  if (!occSelect || !docSelect) return;

  const group = occSelect.value || 'OFFICE_WORKER';
  const allowed = VALID_DOCS_BY_GROUP[group] || VALID_DOCS_BY_GROUP.OFFICE_WORKER;
  const currentVal = docSelect.value;
  const isCurrentAllowed = preserveExistingValid && allowed.includes(currentVal);
  let firstAllowedValue = '';

  Array.from(docSelect.options).forEach(opt => {
    const ok = allowed.includes(opt.value);
    if (ok) {
      opt.style.fontWeight = '600';
      opt.style.color = '#1e40af';
      if (!firstAllowedValue) firstAllowedValue = opt.value;
    } else {
      opt.style.fontWeight = '400';
      opt.style.color = '#64748b';
    }
  });

  if (!isCurrentAllowed && firstAllowedValue) {
    docSelect.value = firstAllowedValue;
  }
}

/**
 * ============================================================================
 * LOCAL FOLDER IMAGE PICKER, MULTI-IMAGE VERIFICATION GALLERY & LIGHTBOX VIEWER
 * ============================================================================
 */
const PM_MEDIA_MANAGER = (function () {
  const MAX_FILE_BYTES = 10 * 1024 * 1024; // 10 MB
  const MAX_BATCH_FILES = 15;
  const ALLOWED_EXT_REGEX = /\.(jpe?g|png|webp|gif|svg)$/i;

  let mediaSeqCounter = 1;

  const state = {
    verificationDocs: [],
    verificationFolderUrl: '',
    verificationDirty: false,
    customerLocked: true,
    singleSlots: {
      cccdFront: {
        key: 'cccdFront',
        category: 'CCCD_FRONT',
        label: 'Ảnh CCCD Mặt Trước',
        emptyIcon: 'bi-person-vcard',
        urlInputId: 'ccIdCardFrontUrl',
        fileInputId: 'ccIdCardFrontFileInput',
        slotId: 'ccIdCardFrontSlot',
        item: null
      },
      cccdBack: {
        key: 'cccdBack',
        category: 'CCCD_BACK',
        label: 'Ảnh CCCD Mặt Sau',
        emptyIcon: 'bi-credit-card-2-back',
        urlInputId: 'ccIdCardBackUrl',
        fileInputId: 'ccIdCardBackFileInput',
        slotId: 'ccIdCardBackSlot',
        item: null
      },
      portrait: {
        key: 'portrait',
        category: 'PORTRAIT',
        label: 'Ảnh Chân Dung Tại Quầy',
        emptyIcon: 'bi-person-bounding-box',
        urlInputId: 'ccPortraitUrl',
        fileInputId: 'ccPortraitFileInput',
        slotId: 'ccPortraitSlot',
        item: null
      }
    },
    collateralDocs: [],
    collateralDirty: false,
    lightbox: {
      items: [],
      activeIndex: 0,
      zoom: 100,
      rotation: 0,
      categoryLabel: 'Hình ảnh hồ sơ'
    }
  };

  function buildFallbackSvgDataUri(fileName, categoryText) {
    const safeTitle = String(fileName || 'hinh-anh-ho-so.jpg').replace(/[<>&"']/g, '_');
    const safeCat = String(categoryText || 'Hồ sơ lưu trữ PawnMaster').replace(/[<>&"']/g, '_');
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="880" height="580" viewBox="0 0 880 580">` +
      `<rect width="880" height="580" fill="#f8fafc"/>` +
      `<rect x="28" y="28" width="824" height="524" rx="14" fill="#ffffff" stroke="#cbd5e1" stroke-width="2"/>` +
      `<rect x="28" y="28" width="824" height="64" rx="14" fill="#eff6ff"/>` +
      `<line x1="28" y1="92" x2="852" y2="92" stroke="#bfdbfe" stroke-width="1.5"/>` +
      `<text x="58" y="68" font-family="Inter,Segoe UI,Arial,sans-serif" font-size="19" font-weight="700" fill="#1e40af">${safeCat}</text>` +
      `<rect x="58" y="124" width="764" height="332" rx="10" fill="#f8fafc" stroke="#e2e8f0" stroke-width="1.5" stroke-dasharray="7 5"/>` +
      `<circle cx="440" cy="252" r="38" fill="#eff6ff" stroke="#bfdbfe" stroke-width="2"/>` +
      `<path d="M426 258 l10 -12 l9 10 l12 -16 l15 18 z" fill="#1e40af"/>` +
      `<text x="440" y="328" text-anchor="middle" font-family="Inter,Segoe UI,Arial,sans-serif" font-size="18" font-weight="700" fill="#0f172a">${safeTitle}</text>` +
      `<text x="440" y="360" text-anchor="middle" font-family="Inter,Segoe UI,Arial,sans-serif" font-size="14" fill="#475569">Xem trực tiếp bản lưu hình ảnh trên hệ thống PawnMaster</text>` +
      `</svg>`;
    return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
  }

  function extractFileNameFromUrl(url) {
    if (!url) return 'hinh-anh.jpg';
    const clean = String(url).split('?')[0].split('#')[0].replace(/\\/g, '/');
    const parts = clean.split('/').filter(Boolean);
    return parts.length > 0 ? decodeURIComponent(parts[parts.length - 1]) : 'hinh-anh.jpg';
  }

  function looksLikeFolderPath(url) {
    if (!url || typeof url !== 'string') return false;
    const trimmed = url.trim().split('?')[0].replace(/\/+$/, '');
    if (!trimmed.startsWith('/uploads/')) return false;
    return !ALLOWED_EXT_REGEX.test(trimmed);
  }

  function resolvePreviewSrcForStoredUrl(storedUrl, categoryLabel) {
    const trimmed = String(storedUrl || '').trim();
    if (!trimmed) return buildFallbackSvgDataUri('hinh-anh.jpg', categoryLabel);
    if (trimmed.startsWith('data:') || trimmed.startsWith('blob:')) return trimmed;
    if (isBackendServerMode() && trimmed.startsWith('/uploads/')) {
      return trimmed;
    }
    if (!isBackendServerMode()) {
      return buildFallbackSvgDataUri(extractFileNameFromUrl(trimmed), categoryLabel);
    }
    return trimmed;
  }

  function createObjectUrlSafe(file, categoryLabel) {
    if (file && typeof file.previewUrl === 'string' && file.previewUrl) {
      return { url: file.previewUrl, revocable: false };
    }
    if (typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function' && typeof Blob !== 'undefined' && file instanceof Blob) {
      return { url: URL.createObjectURL(file), revocable: true };
    }
    return {
      url: buildFallbackSvgDataUri((file && file.name) || 'anh-moi-chon.jpg', categoryLabel),
      revocable: false
    };
  }

  function revokeItemObjectUrl(item) {
    if (item && item.revocable && item.previewUrl && typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') {
      try {
        URL.revokeObjectURL(item.previewUrl);
      } catch (e) {
        // Ignore revoke error
      }
    }
  }

  function formatFileSizeShort(bytes) {
    const num = Number(bytes || 0);
    if (!num || num <= 0) return '';
    if (num < 1024) return `${num} B`;
    if (num < 1024 * 1024) return `${(num / 1024).toFixed(1)} KB`;
    return `${(num / (1024 * 1024)).toFixed(2)} MB`;
  }

  function validatePickedImageFile(file) {
    if (!file) return 'Tệp không hợp lệ.';
    const name = String(file.name || '');
    const mime = String(file.type || '').toLowerCase();
    const validType = (mime && mime.startsWith('image/')) || ALLOWED_EXT_REGEX.test(name);
    if (!validType) {
      return `Tệp "${name || 'không rõ tên'}" không phải định dạng hình ảnh hợp lệ (JPG, PNG, WEBP, GIF).`;
    }
    if (Number(file.size || 0) > MAX_FILE_BYTES) {
      return `Tệp "${name}" vượt quá giới hạn dung lượng 10MB.`;
    }
    return null;
  }

  function syncVerificationCanonicalHiddenInput() {
    const docUrlInput = document.getElementById('ccDocumentUrl');
    const folderBadge = document.getElementById('ccVerificationFolderBadge');
    const countBadge = document.getElementById('ccVerificationDocCountBadge');
    const cccdRaw = normalizeCccdDigits(document.getElementById('ccCustomerIdNumber')?.value || '');
    const count = state.verificationDocs.length;

    if (countBadge) {
      countBadge.textContent = count > 0
        ? `${String(count).padStart(2, '0')} ảnh chứng từ`
        : 'Chưa có ảnh';
    }

    const computedFolder = state.verificationFolderUrl ||
      (cccdRaw.length >= 9 ? `/uploads/customers/${cccdRaw}/verification-docs` : '/uploads/customers/pending/verification-docs');

    if (folderBadge) {
      if (count > 0) {
        folderBadge.textContent = computedFolder;
        folderBadge.classList.remove('d-none');
      } else {
        folderBadge.classList.add('d-none');
      }
    }

    if (docUrlInput) {
      if (count === 0) {
        docUrlInput.value = '';
      } else if (state.verificationFolderUrl) {
        docUrlInput.value = state.verificationFolderUrl;
      } else {
        const allSaved = state.verificationDocs.every(it => it.isSaved && it.storedUrl);
        docUrlInput.value = allSaved
          ? state.verificationDocs.map(it => it.storedUrl).join(',')
          : computedFolder;
      }
    }

    const boxEl = document.getElementById('ccVerificationDocBox');
    if (count > 0 && boxEl) {
      clearFieldValidationError(boxEl);
    }
    refreshLiveFormErrorSummary();
    if (typeof syncCustomerAccordionSubsteps === 'function') {
      syncCustomerAccordionSubsteps();
    }
  }

  function syncCollateralCanonicalHiddenInput() {
    const colInput = document.getElementById('ccCollateralImageUrls');
    const countBadge = document.getElementById('ccCollateralImageCountBadge');
    const count = state.collateralDocs.length;
    if (countBadge) {
      countBadge.textContent = count > 0
        ? `${String(count).padStart(2, '0')} ảnh tài sản`
        : 'Chưa có ảnh';
    }
    if (colInput) {
      colInput.value = state.collateralDocs
        .map(it => it.storedUrl || `staged://${it.name}`)
        .join(',');
    }
  }

  function buildDescriptiveGalleryAlt(groupKey, idx, total) {
    if (groupKey === 'verification') {
      const docSelect = document.getElementById('ccVerificationDocType');
      const selectedOpt = docSelect && docSelect.options ? docSelect.options[docSelect.selectedIndex] : null;
      const docLabel = (selectedOpt && selectedOpt.textContent ? selectedOpt.textContent.trim() : '') || 'Chứng từ xác minh';
      return `${docLabel} - Ảnh ${idx + 1} của ${total}`;
    }
    if (groupKey === 'collateral') {
      const colName = (document.getElementById('ccCollateralName')?.value || '').trim() || 'Tài sản cầm cố';
      return `${colName} - Ảnh hiện trạng ${idx + 1} của ${total}`;
    }
    return `Hình ảnh đối chiếu ${idx + 1} của ${total}`;
  }

  function buildDescriptiveSlotAlt(slotKey, fallbackLabel) {
    const custName = (document.getElementById('ccCustomerName')?.value || '').trim() || 'Chưa nhập tên';
    const labelMap = {
      cccdFront: `Ảnh CCCD mặt trước - ${custName}`,
      cccdBack: `Ảnh CCCD mặt sau - ${custName}`,
      portrait: `Ảnh chân dung tại quầy - ${custName}`
    };
    return labelMap[slotKey] || `${fallbackLabel || 'Ảnh định danh'} - ${custName}`;
  }

  function renderMultiGalleryList(containerId, emptyId, clearBtnId, items, groupKey, allowRemove) {
    const galleryEl = document.getElementById(containerId);
    const emptyEl = document.getElementById(emptyId);
    const clearBtn = document.getElementById(clearBtnId);

    if (clearBtn) {
      clearBtn.classList.toggle('d-none', !allowRemove || items.length === 0);
    }
    if (emptyEl) {
      emptyEl.classList.toggle('d-none', items.length > 0);
    }
    if (!galleryEl) return;

    if (items.length === 0) {
      galleryEl.innerHTML = '';
      return;
    }

    const htmlParts = items.map((item, idx) => {
      const numLabel = `#${String(idx + 1).padStart(2, '0')}`;
      const statusClass = item.isSaved ? 'pm-doc-status-saved' : 'pm-doc-status-staged';
      const statusText = item.isSaved ? 'Đã lưu' : 'Mới chọn';
      const sizeStr = formatFileSizeShort(item.sizeBytes);
      const subText = sizeStr ? `${sizeStr} • Xem trực tiếp` : 'Nhấn để xem trực tiếp';
      const safeName = escapeHtml(item.name || `hinh-anh-${idx + 1}.jpg`);
      const safeAlt = escapeHtml(buildDescriptiveGalleryAlt(groupKey, idx, items.length));
      const safeSrc = escapeHtml(item.previewUrl || buildFallbackSvgDataUri(item.name, 'Hình ảnh đối chiếu'));

      const removeBtnHtml = allowRemove
        ? `<button type="button" class="pm-evidence-remove-btn" data-media-action="remove" data-media-group="${groupKey}" data-media-index="${idx}" aria-label="Gỡ ảnh ${safeName}" title="Gỡ ảnh này">` +
          `<i class="bi bi-x-lg" aria-hidden="true"></i>` +
          `</button>`
        : '';

      return `<div class="pm-evidence-tile" data-media-group="${groupKey}" data-media-index="${idx}">` +
        `<button type="button" class="pm-evidence-thumb-btn" data-media-action="preview" data-media-group="${groupKey}" data-media-index="${idx}" aria-label="Xem trực tiếp ${safeAlt}">` +
          `<span class="pm-evidence-index font-tabular">${numLabel}</span>` +
          `<span class="pm-doc-status-pill ${statusClass} pm-evidence-status-float">${statusText}</span>` +
          `<img class="pm-evidence-thumb-img" src="${safeSrc}" alt="${safeAlt}" loading="lazy" />` +
          `<span class="pm-doc-thumb-zoom" aria-hidden="true"><i class="bi bi-arrows-angle-expand" aria-hidden="true"></i><span>Xem trực tiếp</span></span>` +
        `</button>` +
        removeBtnHtml +
        `<div class="pm-evidence-meta">` +
          `<button type="button" class="pm-doc-filename-btn" data-media-action="preview" data-media-group="${groupKey}" data-media-index="${idx}" title="${safeName}">${safeName}</button>` +
          `<span class="pm-doc-subtext font-tabular">${escapeHtml(subText)}</span>` +
        `</div>` +
      `</div>`;
    });

    if (allowRemove && items.length < MAX_BATCH_FILES) {
      htmlParts.push(
        `<button type="button" class="pm-evidence-add-tile" data-media-action="add-more" data-media-group="${groupKey}" aria-label="Chọn thêm hình ảnh từ máy tính">` +
          `<i class="bi bi-plus-circle-dotted" aria-hidden="true"></i>` +
          `<span class="pm-evidence-add-label">Thêm ảnh khác</span>` +
          `<span class="pm-evidence-add-hint font-tabular">${String(items.length).padStart(2, '0')} / ${MAX_BATCH_FILES} ảnh</span>` +
        `</button>`
      );
    }

    galleryEl.innerHTML = htmlParts.join('');
  }

  function renderVerificationGallery() {
    const verBoxEl = document.getElementById('ccVerificationDocBox');
    const toolbarEl = document.getElementById('ccVerificationDocToolbar');
    const lockedHintEl = document.getElementById('ccVerificationLockedHint');
    const canEdit = !state.customerLocked;

    if (verBoxEl) verBoxEl.classList.toggle('is-locked', !canEdit);
    if (toolbarEl) toolbarEl.classList.toggle('d-none', !canEdit);
    if (lockedHintEl) lockedHintEl.classList.toggle('d-none', canEdit);

    renderMultiGalleryList(
      'ccVerificationDocGallery',
      'ccVerificationDocEmpty',
      'btnClearVerificationDocs',
      state.verificationDocs,
      'verification',
      canEdit
    );
    syncVerificationCanonicalHiddenInput();
  }

  function renderCollateralGallery() {
    renderMultiGalleryList(
      'ccCollateralImageGallery',
      'ccCollateralImageEmpty',
      'btnClearCollateralImages',
      state.collateralDocs,
      'collateral',
      true
    );
    syncCollateralCanonicalHiddenInput();
  }

  function renderSingleKycSlot(slotKey) {
    const cfg = state.singleSlots[slotKey];
    if (!cfg) return;
    const slotEl = document.getElementById(cfg.slotId);
    const urlInput = document.getElementById(cfg.urlInputId);
    const canEdit = !state.customerLocked;
    const item = cfg.item;

    if (urlInput) {
      urlInput.value = item ? (item.storedUrl || `staged://${item.name}`) : '';
    }
    if (!slotEl) return;

    slotEl.classList.toggle('has-image', Boolean(item));
    slotEl.classList.toggle('is-locked', !canEdit);

    if (!item) {
      if (canEdit) {
        slotEl.innerHTML =
          `<button type="button" class="pm-kyc-empty-trigger" data-media-action="pick-single" data-slot-key="${slotKey}" aria-label="Chọn ${escapeHtml(cfg.label)} từ máy tính">` +
            `<span class="pm-kyc-empty-emblem" aria-hidden="true"><i class="bi ${cfg.emptyIcon}" aria-hidden="true"></i></span>` +
            `<span class="pm-kyc-empty-cta">Chọn ảnh từ máy</span>` +
            `<span class="pm-kyc-empty-sub">Kéo thả • JPG, PNG, WEBP</span>` +
          `</button>`;
      } else {
        slotEl.innerHTML =
          `<div class="pm-kyc-empty-locked">` +
            `<span class="pm-kyc-empty-emblem" aria-hidden="true"><i class="bi ${cfg.emptyIcon}" aria-hidden="true"></i></span>` +
            `<span class="pm-kyc-empty-sub">Chưa có ảnh lưu trữ</span>` +
          `</div>`;
      }
      return;
    }

    const safeName = escapeHtml(item.name || 'hinh-anh-kyc.jpg');
    const safeAlt = escapeHtml(buildDescriptiveSlotAlt(slotKey, cfg.label));
    const safeSrc = escapeHtml(item.previewUrl || buildFallbackSvgDataUri(item.name, cfg.label));
    const statusClass = item.isSaved ? 'pm-doc-status-saved' : 'pm-doc-status-staged';
    const statusText = item.isSaved ? 'Đã lưu' : 'Mới chọn';

    const editBtnsHtml = canEdit
      ? `<button type="button" class="pm-kyc-icon-btn" data-media-action="pick-single" data-slot-key="${slotKey}" aria-label="Đổi ${escapeHtml(cfg.label)}" title="Đổi ảnh khác">` +
          `<i class="bi bi-arrow-repeat" aria-hidden="true"></i>` +
        `</button>` +
        `<button type="button" class="pm-kyc-icon-btn pm-kyc-icon-btn-danger" data-media-action="clear-single" data-slot-key="${slotKey}" aria-label="Xóa ${escapeHtml(cfg.label)}" title="Xóa ảnh">` +
          `<i class="bi bi-trash3" aria-hidden="true"></i>` +
        `</button>`
      : '';

    slotEl.innerHTML = `<div class="pm-kyc-frame-card">` +
      `<button type="button" class="pm-kyc-stage-btn" data-media-action="preview-single" data-slot-key="${slotKey}" aria-label="Xem trực tiếp ${safeAlt}">` +
        `<span class="pm-doc-status-pill ${statusClass} pm-kyc-status-float">${statusText}</span>` +
        `<img class="pm-single-photo-img" src="${safeSrc}" alt="${safeAlt}" loading="lazy" />` +
        `<span class="pm-doc-thumb-zoom" aria-hidden="true"><i class="bi bi-arrows-angle-expand" aria-hidden="true"></i><span>Xem trực tiếp</span></span>` +
      `</button>` +
      `<div class="pm-kyc-card-footer">` +
        `<button type="button" class="pm-doc-filename-btn" data-media-action="preview-single" data-slot-key="${slotKey}" title="${safeName}">${safeName}</button>` +
        `<div class="pm-kyc-icon-actions">` +
          `<button type="button" class="pm-kyc-icon-btn" data-media-action="preview-single" data-slot-key="${slotKey}" aria-label="Phóng to ${escapeHtml(cfg.label)}" title="Xem trực tiếp">` +
            `<i class="bi bi-eye-fill" aria-hidden="true"></i>` +
          `</button>` +
          editBtnsHtml +
        `</div>` +
      `</div>` +
    `</div>`;
  }

  function renderAllSingleKycSlots() {
    Object.keys(state.singleSlots).forEach(renderSingleKycSlot);
  }

  function setCustomerMediaLocked(isLocked) {
    state.customerLocked = Boolean(isLocked);
    renderVerificationGallery();
    renderAllSingleKycSlots();
  }

  function appendFilesToMultiList(targetArray, rawFiles, categoryLabel) {
    const incoming = Array.from(rawFiles || []);
    if (incoming.length === 0) return 0;

    let addedCount = 0;
    for (const file of incoming) {
      if (targetArray.length >= MAX_BATCH_FILES) {
        showToast(`Tối đa ${MAX_BATCH_FILES} hình ảnh cho danh mục này.`, 'warning');
        break;
      }
      const errMsg = validatePickedImageFile(file);
      if (errMsg) {
        showToast(errMsg, 'warning');
        continue;
      }
      const duplicate = targetArray.some(existing =>
        !existing.isSaved &&
        existing.file &&
        existing.name === file.name &&
        existing.sizeBytes === Number(file.size || 0)
      );
      if (duplicate) continue;

      const created = createObjectUrlSafe(file, categoryLabel);
      targetArray.push({
        id: `media_${mediaSeqCounter++}`,
        name: file.name || `anh_${mediaSeqCounter}.jpg`,
        previewUrl: created.url,
        revocable: created.revocable,
        storedUrl: '',
        file,
        sizeBytes: Number(file.size || 0),
        isSaved: false
      });
      addedCount++;
    }
    return addedCount;
  }

  function addVerificationFiles(fileList) {
    const added = appendFilesToMultiList(state.verificationDocs, fileList, 'Hình Ảnh Chứng Từ Xác Minh');
    if (added > 0) {
      state.verificationDirty = true;
      renderVerificationGallery();
      markDraftUnsaved();
    }
    return added;
  }

  function addCollateralFiles(fileList) {
    const added = appendFilesToMultiList(state.collateralDocs, fileList, 'Hình Ảnh Tài Sản Cầm Cố');
    if (added > 0) {
      state.collateralDirty = true;
      renderCollateralGallery();
      markDraftUnsaved();
    }
    return added;
  }

  function setSingleSlotFile(slotKey, file) {
    const cfg = state.singleSlots[slotKey];
    if (!cfg || !file) return false;
    const errMsg = validatePickedImageFile(file);
    if (errMsg) {
      showToast(errMsg, 'warning');
      return false;
    }
    if (cfg.item) {
      revokeItemObjectUrl(cfg.item);
    }
    const created = createObjectUrlSafe(file, cfg.label);
    cfg.item = {
      id: `single_${slotKey}_${mediaSeqCounter++}`,
      name: file.name || `${slotKey}.jpg`,
      previewUrl: created.url,
      revocable: created.revocable,
      storedUrl: '',
      file,
      sizeBytes: Number(file.size || 0),
      isSaved: false
    };
    renderSingleKycSlot(slotKey);
    markDraftUnsaved();
    return true;
  }

  function clearSingleSlot(slotKey) {
    const cfg = state.singleSlots[slotKey];
    if (!cfg) return;
    if (cfg.item) {
      revokeItemObjectUrl(cfg.item);
      cfg.item = null;
    }
    const fileInput = document.getElementById(cfg.fileInputId);
    if (fileInput) fileInput.value = '';
    renderSingleKycSlot(slotKey);
    markDraftUnsaved();
  }

  async function hydrateCustomerMedia(customer, options = {}) {
    state.verificationDocs.forEach(revokeItemObjectUrl);
    state.verificationDocs = [];
    state.verificationDirty = false;

    const cccd = normalizeCccdDigits((customer && customer.identityNumber) || '');
    const rawCandidate = (customer && (customer.verificationDocUrls || customer.verificationDocUrl)) || '';
    const rawStored = Array.isArray(rawCandidate) ? rawCandidate.filter(Boolean).join(',') : String(rawCandidate || '');
    state.verificationFolderUrl = looksLikeFolderPath(rawStored)
      ? rawStored.trim()
      : (cccd ? `/uploads/customers/${cccd}/verification-docs` : '');

    let resolvedUrls = [];
    if (customer && Array.isArray(customer.verificationDocUrlList) && customer.verificationDocUrlList.length > 0) {
      resolvedUrls = customer.verificationDocUrlList.filter(Boolean);
    } else if (rawStored) {
      if (looksLikeFolderPath(rawStored)) {
        if (isBackendServerMode()) {
          try {
            const folderRes = await apiFetch(`/api/v1/files/folder-images?folder=${encodeURIComponent(rawStored.trim())}`, {
              method: 'GET'
            });
            if (folderRes && Array.isArray(folderRes.data)) {
              resolvedUrls = folderRes.data;
            }
          } catch (e) {
            resolvedUrls = [];
          }
        }
      } else {
        resolvedUrls = String(rawStored)
          .split(',')
          .map(s => s.trim())
          .filter(Boolean);
      }
    }

    resolvedUrls.forEach((url, idx) => {
      const fileName = extractFileNameFromUrl(url) || `chung-tu-${idx + 1}.jpg`;
      const localMatch = Array.isArray(customer && customer._localVerificationItems)
        ? customer._localVerificationItems[idx]
        : null;
      state.verificationDocs.push({
        id: `saved_ver_${mediaSeqCounter++}`,
        name: (localMatch && localMatch.name) || fileName,
        previewUrl: (localMatch && localMatch.previewUrl) || resolvePreviewSrcForStoredUrl(url, 'Chứng Từ Xác Minh Lưu Trữ'),
        revocable: false,
        storedUrl: url,
        file: null,
        sizeBytes: (localMatch && localMatch.sizeBytes) || 0,
        isSaved: true
      });
    });

    const slotMapping = [
      { key: 'cccdFront', val: customer && customer.idCardFrontUrl },
      { key: 'cccdBack', val: customer && customer.idCardBackUrl },
      { key: 'portrait', val: customer && customer.portraitUrl }
    ];
    slotMapping.forEach(({ key, val }) => {
      const cfg = state.singleSlots[key];
      if (cfg.item) revokeItemObjectUrl(cfg.item);
      const trimmed = String(val || '').trim();
      if (trimmed) {
        const localPreview = customer && customer._localSinglePreviews && customer._localSinglePreviews[key];
        cfg.item = {
          id: `saved_${key}_${mediaSeqCounter++}`,
          name: extractFileNameFromUrl(trimmed),
          previewUrl: localPreview || resolvePreviewSrcForStoredUrl(trimmed, cfg.label),
          revocable: false,
          storedUrl: trimmed,
          file: null,
          sizeBytes: 0,
          isSaved: true
        };
      } else {
        cfg.item = null;
      }
    });

    state.customerLocked = options.locked !== false;
    renderVerificationGallery();
    renderAllSingleKycSlots();
  }

  function resetCustomerMediaForNewCustomer() {
    state.verificationDocs.forEach(revokeItemObjectUrl);
    state.verificationDocs = [];
    state.verificationFolderUrl = '';
    state.verificationDirty = false;

    Object.keys(state.singleSlots).forEach(key => {
      const cfg = state.singleSlots[key];
      if (cfg.item) revokeItemObjectUrl(cfg.item);
      cfg.item = null;
      const fi = document.getElementById(cfg.fileInputId);
      if (fi) fi.value = '';
    });

    const verInput = document.getElementById('ccDocumentFileInput');
    if (verInput) verInput.value = '';

    state.customerLocked = false;
    renderVerificationGallery();
    renderAllSingleKycSlots();
  }

  function hydrateCollateralFromHiddenInput() {
    state.collateralDocs.forEach(revokeItemObjectUrl);
    state.collateralDocs = [];
    state.collateralDirty = false;

    const raw = (document.getElementById('ccCollateralImageUrls')?.value || '').trim();
    if (raw) {
      raw.split(',').map(s => s.trim()).filter(Boolean).forEach((url, idx) => {
        if (url.startsWith('staged://')) return;
        state.collateralDocs.push({
          id: `col_${mediaSeqCounter++}`,
          name: extractFileNameFromUrl(url) || `tai-san-${idx + 1}.jpg`,
          previewUrl: resolvePreviewSrcForStoredUrl(url, 'Hình Ảnh Tài Sản Cầm Cố'),
          revocable: false,
          storedUrl: url,
          file: null,
          sizeBytes: 0,
          isSaved: true
        });
      });
    }
    renderCollateralGallery();
  }

  async function uploadPendingCustomerMedia(cccd) {
    const cleanCccd = normalizeCccdDigits(cccd);
    if (!cleanCccd) return;

    const stagedVerFiles = state.verificationDocs.filter(it => !it.isSaved && it.file);
    const keptSavedUrls = state.verificationDocs.filter(it => it.isSaved && it.storedUrl).map(it => it.storedUrl);
    const targetFolderUrl = `/uploads/customers/${cleanCccd}/verification-docs`;

    if (stagedVerFiles.length > 0 || state.verificationDirty) {
      let uploadedServerResult = null;
      if (isBackendServerMode() && typeof FormData !== 'undefined') {
        try {
          const fd = new FormData();
          fd.append('identityNumber', cleanCccd);
          fd.append('syncExistingFolder', 'true');
          keptSavedUrls.forEach(u => fd.append('keepUrls', u));
          stagedVerFiles.forEach(it => fd.append('files', it.file, it.name));

          const res = await apiFetch('/api/v1/files/customer-verification-docs', {
            method: 'POST',
            body: fd
          });
          if (res && res.data && res.data.folderUrl) {
            uploadedServerResult = res.data;
          }
        } catch (err) {
          if (err && (err.status === 400 || err.status === 413 || err.status === 422)) {
            throw err;
          }
          // Offline / mock fallback below
        }
      }

      if (uploadedServerResult) {
        state.verificationFolderUrl = uploadedServerResult.folderUrl;
        const serverUrls = Array.isArray(uploadedServerResult.imageUrls) ? uploadedServerResult.imageUrls : [];
        state.verificationDocs.forEach(revokeItemObjectUrl);
        state.verificationDocs = serverUrls.map((u, idx) => ({
          id: `ver_srv_${mediaSeqCounter++}`,
          name: extractFileNameFromUrl(u) || `chung-tu-${idx + 1}.jpg`,
          previewUrl: resolvePreviewSrcForStoredUrl(u, 'Chứng Từ Xác Minh Lưu Trữ'),
          revocable: false,
          storedUrl: u,
          file: null,
          sizeBytes: 0,
          isSaved: true
        }));
      } else {
        state.verificationFolderUrl = targetFolderUrl;
        state.verificationDocs = state.verificationDocs.map((it, idx) => ({
          id: it.id,
          name: it.name,
          previewUrl: it.previewUrl,
          revocable: it.revocable,
          storedUrl: it.storedUrl || `${targetFolderUrl}/${String(idx + 1).padStart(2, '0')}_${it.name}`,
          file: null,
          sizeBytes: it.sizeBytes,
          isSaved: true
        }));
      }
      state.verificationDirty = false;
      renderVerificationGallery();
    }

    // Upload single KYC photo slots if staged
    for (const slotKey of Object.keys(state.singleSlots)) {
      const cfg = state.singleSlots[slotKey];
      if (!cfg.item || cfg.item.isSaved || !cfg.item.file) continue;

      let storedPath = null;
      if (isBackendServerMode() && typeof FormData !== 'undefined') {
        try {
          const fd = new FormData();
          fd.append('category', cfg.category);
          fd.append('ownerKey', cleanCccd);
          fd.append('files', cfg.item.file, cfg.item.name);
          const res = await apiFetch('/api/v1/files/upload', {
            method: 'POST',
            body: fd
          });
          if (res && res.data && res.data.primaryUrl) {
            storedPath = res.data.primaryUrl;
          }
        } catch (err) {
          if (err && (err.status === 400 || err.status === 413 || err.status === 422)) {
            throw err;
          }
        }
      }

      if (storedPath) {
        revokeItemObjectUrl(cfg.item);
        cfg.item.storedUrl = storedPath;
        cfg.item.previewUrl = resolvePreviewSrcForStoredUrl(storedPath, cfg.label);
        cfg.item.revocable = false;
      } else {
        cfg.item.storedUrl = `/uploads/customers/${cleanCccd}/kyc/${cfg.category.toLowerCase()}_${cfg.item.name}`;
      }
      cfg.item.file = null;
      cfg.item.isSaved = true;
      renderSingleKycSlot(slotKey);
    }
  }

  async function uploadPendingCollateralMedia(cccd) {
    const staged = state.collateralDocs.filter(it => !it.isSaved && it.file);
    if (staged.length === 0) {
      syncCollateralCanonicalHiddenInput();
      return;
    }

    const cleanKey = normalizeCccdDigits(cccd) || 'collateral';
    let serverUrls = null;

    if (isBackendServerMode() && typeof FormData !== 'undefined') {
      try {
        const fd = new FormData();
        fd.append('category', 'COLLATERAL');
        fd.append('ownerKey', cleanKey);
        staged.forEach(it => fd.append('files', it.file, it.name));
        const res = await apiFetch('/api/v1/files/upload', {
          method: 'POST',
          body: fd
        });
        if (res && res.data && Array.isArray(res.data.imageUrls)) {
          serverUrls = res.data.imageUrls;
        }
      } catch (err) {
        if (err && (err.status === 400 || err.status === 413 || err.status === 422)) {
          throw err;
        }
      }
    }

    let sIdx = 0;
    state.collateralDocs = state.collateralDocs.map((it, idx) => {
      if (it.isSaved) return it;
      const uploadedUrl = serverUrls && serverUrls[sIdx] ? serverUrls[sIdx++] : null;
      if (uploadedUrl) {
        revokeItemObjectUrl(it);
        return {
          id: it.id,
          name: extractFileNameFromUrl(uploadedUrl),
          previewUrl: resolvePreviewSrcForStoredUrl(uploadedUrl, 'Hình Ảnh Tài Sản Cầm Cố'),
          revocable: false,
          storedUrl: uploadedUrl,
          file: null,
          sizeBytes: it.sizeBytes,
          isSaved: true
        };
      }
      return {
        id: it.id,
        name: it.name,
        previewUrl: it.previewUrl,
        revocable: it.revocable,
        storedUrl: `/uploads/collaterals/${cleanKey}/${String(idx + 1).padStart(2, '0')}_${it.name}`,
        file: null,
        sizeBytes: it.sizeBytes,
        isSaved: true
      };
    });
    state.collateralDirty = false;
    renderCollateralGallery();
  }

  // Lightbox Direct In-System Viewer
  function applyLightboxTransform() {
    const imgEl = document.getElementById('lightboxMainImage');
    const zoomEl = document.getElementById('lightboxZoomIndicator');
    if (zoomEl) zoomEl.textContent = `${state.lightbox.zoom}%`;
    if (imgEl) {
      const scale = (state.lightbox.zoom / 100).toFixed(2);
      imgEl.style.transform = `scale(${scale}) rotate(${state.lightbox.rotation}deg)`;
    }
  }

  function renderLightboxSlide() {
    const items = state.lightbox.items;
    if (!items || items.length === 0) return;

    const idx = Math.max(0, Math.min(state.lightbox.activeIndex, items.length - 1));
    state.lightbox.activeIndex = idx;
    const current = items[idx];

    const catBadge = document.getElementById('lightboxCategoryBadge');
    const titleEl = document.getElementById('modalImagePreviewLightboxTitle');
    const counterEl = document.getElementById('lightboxCounterBadge');
    const imgEl = document.getElementById('lightboxMainImage');
    const prevBtn = document.getElementById('btnLightboxPrev');
    const nextBtn = document.getElementById('btnLightboxNext');
    const stripWrap = document.getElementById('lightboxFilmstripWrap');
    const stripEl = document.getElementById('lightboxFilmstrip');

    if (catBadge) catBadge.textContent = state.lightbox.categoryLabel || 'Hình ảnh hồ sơ';
    if (titleEl) titleEl.textContent = current.name || `Hình ảnh ${idx + 1}`;
    if (counterEl) counterEl.textContent = `Ảnh ${idx + 1} / ${items.length}`;

    if (imgEl) {
      imgEl.setAttribute('src', current.previewUrl || buildFallbackSvgDataUri(current.name, state.lightbox.categoryLabel));
      imgEl.setAttribute('alt', current.name || 'Hình ảnh hồ sơ đối chiếu trực tiếp');
    }

    const hasMultiple = items.length > 1;
    if (prevBtn) prevBtn.classList.toggle('d-none', !hasMultiple);
    if (nextBtn) nextBtn.classList.toggle('d-none', !hasMultiple);
    if (stripWrap) stripWrap.classList.toggle('d-none', !hasMultiple);

    if (stripEl) {
      if (!hasMultiple) {
        stripEl.innerHTML = '';
      } else {
        stripEl.innerHTML = items.map((it, i) => {
          const activeCls = i === idx ? ' active' : '';
          const safeSrc = escapeHtml(it.previewUrl || buildFallbackSvgDataUri(it.name, state.lightbox.categoryLabel));
          const safeName = escapeHtml(it.name || `Ảnh ${i + 1}`);
          return `<button type="button" tabindex="0" class="pm-lightbox-film-item${activeCls}" data-lightbox-index="${i}" aria-label="Chuyển tới ${safeName}">` +
            `<img src="${safeSrc}" alt="${safeName} (${i + 1}/${items.length})" />` +
          `</button>`;
        }).join('');
      }
    }

    applyLightboxTransform();
  }

  function openLightbox(items, startIndex, categoryLabel) {
    if (!Array.isArray(items) || items.length === 0) return;
    state.lightbox.items = items.slice();
    state.lightbox.activeIndex = Math.max(0, Math.min(Number(startIndex) || 0, items.length - 1));
    state.lightbox.zoom = 100;
    state.lightbox.rotation = 0;
    state.lightbox.categoryLabel = categoryLabel || 'Hình ảnh hồ sơ';

    renderLightboxSlide();

    const modalEl = document.getElementById('modalImagePreviewLightbox');
    if (modalEl && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
      const modal = bootstrap.Modal.getInstance(modalEl) || new bootstrap.Modal(modalEl);
      modal.show();
    }
  }

  function stepLightboxSlide(delta) {
    const len = state.lightbox.items.length;
    if (len <= 1) return;
    state.lightbox.activeIndex = (state.lightbox.activeIndex + delta + len) % len;
    state.lightbox.zoom = 100;
    state.lightbox.rotation = 0;
    renderLightboxSlide();
  }

  function adjustLightboxZoom(delta) {
    state.lightbox.zoom = Math.max(50, Math.min(300, state.lightbox.zoom + delta));
    applyLightboxTransform();
  }

  function rotateLightbox(degDelta) {
    state.lightbox.rotation = (state.lightbox.rotation + degDelta) % 360;
    applyLightboxTransform();
  }

  function bindDropzoneEvents(panelEl, onFilesDropped, canDropFn) {
    if (!panelEl) return;
    panelEl.addEventListener('dragover', function (e) {
      if (canDropFn && !canDropFn()) return;
      e.preventDefault();
      panelEl.classList.add('is-dragover');
    });
    panelEl.addEventListener('dragleave', function () {
      panelEl.classList.remove('is-dragover');
    });
    panelEl.addEventListener('drop', function (e) {
      panelEl.classList.remove('is-dragover');
      if (canDropFn && !canDropFn()) return;
      e.preventDefault();
      const dtFiles = e.dataTransfer && e.dataTransfer.files;
      if (dtFiles && dtFiles.length > 0) {
        onFilesDropped(dtFiles);
      }
    });
  }

  function initMediaControls() {
    const verFileInput = document.getElementById('ccDocumentFileInput');
    const pickVerBtn = document.getElementById('btnPickVerificationDocs');
    const clearVerBtn = document.getElementById('btnClearVerificationDocs');
    const verBox = document.getElementById('ccVerificationDocBox');
    const verEmpty = document.getElementById('ccVerificationDocEmpty');
    const verGallery = document.getElementById('ccVerificationDocGallery');

    if (pickVerBtn && verFileInput) {
      pickVerBtn.addEventListener('click', function () {
        if (state.customerLocked) return;
        verFileInput.click();
      });
    }

    if (verEmpty && verFileInput) {
      verEmpty.addEventListener('click', function () {
        if (state.customerLocked) return;
        verFileInput.click();
      });
      verEmpty.addEventListener('keydown', function (e) {
        if (state.customerLocked) return;
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          verFileInput.click();
        }
      });
    }

    if (verFileInput) {
      verFileInput.addEventListener('change', function () {
        if (state.customerLocked) return;
        addVerificationFiles(verFileInput.files);
        verFileInput.value = '';
      });
    }

    if (clearVerBtn) {
      clearVerBtn.addEventListener('click', function () {
        if (state.customerLocked) return;
        state.verificationDocs.forEach(revokeItemObjectUrl);
        state.verificationDocs = [];
        state.verificationDirty = true;
        renderVerificationGallery();
        markDraftUnsaved();
      });
    }

    bindDropzoneEvents(verBox, addVerificationFiles, () => !state.customerLocked);

    if (verGallery) {
      verGallery.addEventListener('click', function (e) {
        const actionBtn = e.target && typeof e.target.closest === 'function'
          ? e.target.closest('[data-media-action]')
          : null;
        if (!actionBtn) return;
        const action = actionBtn.getAttribute('data-media-action');
        const idx = parseInt(actionBtn.getAttribute('data-media-index') || '0', 10);
        if (action === 'preview') {
          openLightbox(state.verificationDocs, idx, 'Hình Ảnh Chứng Từ Xác Minh');
        } else if (action === 'add-more' && !state.customerLocked && verFileInput) {
          verFileInput.click();
        } else if (action === 'remove' && !state.customerLocked) {
          const removed = state.verificationDocs.splice(idx, 1)[0];
          revokeItemObjectUrl(removed);
          state.verificationDirty = true;
          renderVerificationGallery();
          markDraftUnsaved();
        }
      });
    }

    // Single KYC Slots
    Object.keys(state.singleSlots).forEach(slotKey => {
      const cfg = state.singleSlots[slotKey];
      const fileInput = document.getElementById(cfg.fileInputId);
      const slotEl = document.getElementById(cfg.slotId);

      if (fileInput) {
        fileInput.addEventListener('change', function () {
          if (state.customerLocked) return;
          const f = fileInput.files && fileInput.files[0];
          if (f) setSingleSlotFile(slotKey, f);
          fileInput.value = '';
        });
      }

      if (slotEl) {
        bindDropzoneEvents(
          slotEl,
          files => { if (files && files[0]) setSingleSlotFile(slotKey, files[0]); },
          () => !state.customerLocked
        );

        slotEl.addEventListener('click', function (e) {
          const btn = e.target && typeof e.target.closest === 'function'
            ? e.target.closest('[data-media-action]')
            : null;
          if (!btn) return;
          const act = btn.getAttribute('data-media-action');
          if (act === 'pick-single' && !state.customerLocked && fileInput) {
            fileInput.click();
          } else if (act === 'clear-single' && !state.customerLocked) {
            clearSingleSlot(slotKey);
          } else if (act === 'preview-single' && cfg.item) {
            openLightbox([cfg.item], 0, cfg.label);
          }
        });
      }
    });

    // Collateral Multi-Image Controls
    const colFileInput = document.getElementById('ccCollateralFileInput');
    const pickColBtn = document.getElementById('btnPickCollateralImages');
    const clearColBtn = document.getElementById('btnClearCollateralImages');
    const colBox = document.getElementById('ccCollateralImageBox');
    const colEmpty = document.getElementById('ccCollateralImageEmpty');
    const colGallery = document.getElementById('ccCollateralImageGallery');

    if (pickColBtn && colFileInput) {
      pickColBtn.addEventListener('click', function () {
        colFileInput.click();
      });
    }

    if (colEmpty && colFileInput) {
      colEmpty.addEventListener('click', function () {
        colFileInput.click();
      });
      colEmpty.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          colFileInput.click();
        }
      });
    }

    if (colFileInput) {
      colFileInput.addEventListener('change', function () {
        addCollateralFiles(colFileInput.files);
        colFileInput.value = '';
      });
    }

    if (clearColBtn) {
      clearColBtn.addEventListener('click', function () {
        state.collateralDocs.forEach(revokeItemObjectUrl);
        state.collateralDocs = [];
        state.collateralDirty = true;
        renderCollateralGallery();
        markDraftUnsaved();
      });
    }

    bindDropzoneEvents(colBox, addCollateralFiles, () => true);

    if (colGallery) {
      colGallery.addEventListener('click', function (e) {
        const actionBtn = e.target && typeof e.target.closest === 'function'
          ? e.target.closest('[data-media-action]')
          : null;
        if (!actionBtn) return;
        const action = actionBtn.getAttribute('data-media-action');
        const idx = parseInt(actionBtn.getAttribute('data-media-index') || '0', 10);
        if (action === 'preview') {
          openLightbox(state.collateralDocs, idx, 'Hình Ảnh Hiện Trạng Tài Sản & Tem Niêm Phong');
        } else if (action === 'add-more' && colFileInput) {
          colFileInput.click();
        } else if (action === 'remove') {
          const removed = state.collateralDocs.splice(idx, 1)[0];
          revokeItemObjectUrl(removed);
          state.collateralDirty = true;
          renderCollateralGallery();
          markDraftUnsaved();
        }
      });
    }

    // Lightbox Toolbar & Keyboard Shortcuts
    const zoomInBtn = document.getElementById('btnLightboxZoomIn');
    const zoomOutBtn = document.getElementById('btnLightboxZoomOut');
    const resetBtn = document.getElementById('btnLightboxReset');
    const rotLeftBtn = document.getElementById('btnLightboxRotateLeft');
    const rotRightBtn = document.getElementById('btnLightboxRotateRight');
    const prevBtn = document.getElementById('btnLightboxPrev');
    const nextBtn = document.getElementById('btnLightboxNext');
    const filmstripEl = document.getElementById('lightboxFilmstrip');
    const stageEl = document.getElementById('lightboxStage');
    const lightboxModalEl = document.getElementById('modalImagePreviewLightbox');

    if (zoomInBtn) zoomInBtn.addEventListener('click', () => adjustLightboxZoom(25));
    if (zoomOutBtn) zoomOutBtn.addEventListener('click', () => adjustLightboxZoom(-25));
    if (resetBtn) {
      resetBtn.addEventListener('click', () => {
        state.lightbox.zoom = 100;
        state.lightbox.rotation = 0;
        applyLightboxTransform();
      });
    }
    if (rotLeftBtn) rotLeftBtn.addEventListener('click', () => rotateLightbox(-90));
    if (rotRightBtn) rotRightBtn.addEventListener('click', () => rotateLightbox(90));
    if (prevBtn) prevBtn.addEventListener('click', () => stepLightboxSlide(-1));
    if (nextBtn) nextBtn.addEventListener('click', () => stepLightboxSlide(1));

    if (filmstripEl) {
      const activateFilmstripTarget = e => {
        const thumbBtn = e.target && typeof e.target.closest === 'function'
          ? e.target.closest('[data-lightbox-index]')
          : null;
        if (!thumbBtn) return;
        const targetIdx = parseInt(thumbBtn.getAttribute('data-lightbox-index') || '0', 10);
        state.lightbox.activeIndex = targetIdx;
        state.lightbox.zoom = 100;
        state.lightbox.rotation = 0;
        renderLightboxSlide();
      };
      filmstripEl.addEventListener('click', activateFilmstripTarget);
      filmstripEl.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          activateFilmstripTarget(e);
        }
      });
    }

    if (stageEl) {
      stageEl.addEventListener('wheel', function (e) {
        if (!e.deltaY) return;
        e.preventDefault();
        adjustLightboxZoom(e.deltaY < 0 ? 20 : -20);
      }, { passive: false });
    }

    if (typeof document !== 'undefined') {
      document.addEventListener('keydown', function (e) {
        if (!lightboxModalEl || !lightboxModalEl.classList.contains('show')) return;
        if (e.key === 'ArrowLeft') {
          e.preventDefault();
          stepLightboxSlide(-1);
        } else if (e.key === 'ArrowRight') {
          e.preventDefault();
          stepLightboxSlide(1);
        } else if (e.key === '+' || e.key === '=') {
          e.preventDefault();
          adjustLightboxZoom(25);
        } else if (e.key === '-') {
          e.preventDefault();
          adjustLightboxZoom(-25);
        } else if (e.key === '0') {
          e.preventDefault();
          state.lightbox.zoom = 100;
          state.lightbox.rotation = 0;
          applyLightboxTransform();
        }
      });
    }

    hydrateCollateralFromHiddenInput();
  }

  function hasVerificationDocuments() {
    return state.verificationDocs.length > 0 ||
      Boolean((document.getElementById('ccDocumentUrl')?.value || '').trim());
  }

  function snapshotCustomerLocalMedia() {
    const singlePreviews = {};
    Object.keys(state.singleSlots).forEach(k => {
      const it = state.singleSlots[k].item;
      if (it && it.previewUrl) {
        singlePreviews[k] = it.previewUrl;
      }
    });
    return {
      verificationDocUrls: state.verificationFolderUrl || (document.getElementById('ccDocumentUrl')?.value || '').trim(),
      verificationDocUrlList: state.verificationDocs.map(it => it.storedUrl || it.previewUrl).filter(Boolean),
      _localVerificationItems: state.verificationDocs.map(it => ({
        name: it.name,
        previewUrl: it.previewUrl,
        sizeBytes: it.sizeBytes
      })),
      _localSinglePreviews: singlePreviews
    };
  }

  function refreshDescriptiveAltTexts() {
    renderAllSingleKycSlots();
    renderVerificationGallery();
    renderCollateralGallery();
  }

  return {
    state,
    initMediaControls,
    addVerificationFiles,
    addCollateralFiles,
    setSingleSlotFile,
    clearSingleSlot,
    setCustomerMediaLocked,
    hydrateCustomerMedia,
    resetCustomerMediaForNewCustomer,
    hydrateCollateralFromHiddenInput,
    uploadPendingCustomerMedia,
    uploadPendingCollateralMedia,
    openLightbox,
    stepLightboxSlide,
    adjustLightboxZoom,
    rotateLightbox,
    hasVerificationDocuments,
    snapshotCustomerLocalMedia,
    refreshDescriptiveAltTexts
  };
})();

/**
 * Progressive Section 1 Accordion Sub-steps (1A -> 1E) & Sticky Stepper Sub-progress
 */
const CUSTOMER_SUBSTEP_PANELS = [
  { key: '1A', itemId: 'accItem1A', btnId: 'accBtn1A', collapseId: 'accCollapse1A', statusId: 'accStatus1A' },
  { key: '1B', itemId: 'accItem1B', btnId: 'accBtn1B', collapseId: 'accCollapse1B', statusId: 'accStatus1B' },
  { key: '1C', itemId: 'accItem1C', btnId: 'accBtn1C', collapseId: 'accCollapse1C', statusId: 'accStatus1C' },
  { key: '1D', itemId: 'accItem1D', btnId: 'accBtn1D', collapseId: 'accCollapse1D', statusId: 'accStatus1D' },
  { key: '1E', itemId: 'accItem1E', btnId: 'accBtn1E', collapseId: 'accCollapse1E', statusId: 'accStatus1E' }
];

function setAccordionPanelOpen(substepKey, isOpen) {
  const meta = CUSTOMER_SUBSTEP_PANELS.find(p => p.key === substepKey);
  if (!meta) return;
  const btnEl = document.getElementById(meta.btnId);
  const collapseEl = document.getElementById(meta.collapseId);
  if (!collapseEl) return;

  const shouldOpen = Boolean(isOpen);
  if (typeof bootstrap !== 'undefined' && bootstrap.Collapse && typeof bootstrap.Collapse.getOrCreateInstance === 'function') {
    const inst = bootstrap.Collapse.getOrCreateInstance(collapseEl, { toggle: false });
    if (shouldOpen) {
      inst.show();
    } else {
      inst.hide();
    }
  }

  collapseEl.classList.toggle('show', shouldOpen);
  if (btnEl) {
    btnEl.classList.toggle('collapsed', !shouldOpen);
    btnEl.setAttribute('aria-expanded', shouldOpen ? 'true' : 'false');
  }
}

function expandParentAccordionIfCollapsed(targetEl) {
  if (!targetEl || typeof document === 'undefined') return;
  for (const meta of CUSTOMER_SUBSTEP_PANELS) {
    const collapseEl = document.getElementById(meta.collapseId);
    if (!collapseEl) continue;
    const containsTarget = (typeof collapseEl.contains === 'function' && collapseEl.contains(targetEl)) ||
      (targetEl.id && collapseEl.innerHTML && collapseEl.innerHTML.includes(`id="${targetEl.id}"`));
    if (containsTarget && !collapseEl.classList.contains('show')) {
      setAccordionPanelOpen(meta.key, true);
    }
  }
}

function syncCustomerAccordionSubsteps(options = {}) {
  const lookupState = document.getElementById('ccCustomerLookupState')?.value || 'FOUND';
  const cccd = normalizeCccdDigits(document.getElementById('ccCustomerIdNumber')?.value || '');
  const custName = (document.getElementById('ccCustomerName')?.value || '').trim();
  const phoneDigits = (document.getElementById('ccCustomerPhone')?.value || '').replace(/\D+/g, '');
  const provCode = document.getElementById('ccProvinceCode')?.value || '';
  const distCode = document.getElementById('ccDistrictCode')?.value || '';
  const wardCode = document.getElementById('ccWardCode')?.value || '';
  const street = (document.getElementById('ccAddressStreet')?.value || '').trim();
  const occGroup = document.getElementById('ccOccupationGroup')?.value || '';
  const docType = document.getElementById('ccVerificationDocType')?.value || '';
  const hasDocs = PM_MEDIA_MANAGER.hasVerificationDocuments();

  const done1A = isValidCccdLength(cccd) && (lookupState === 'FOUND' || lookupState === 'CREATING_NEW');
  const done1B = done1A && custName.length >= 2 && phoneDigits.length >= 9 && phoneDigits.length <= 12;
  const done1C = done1A && Boolean(provCode && distCode && wardCode && street.length >= 2);
  const done1D = done1A && Boolean(occGroup && docType);
  const done1E = done1A && hasDocs;

  const statusConfig = [
    { key: '1A', statusId: 'accStatus1A', done: done1A, doneText: lookupState === 'CREATING_NEW' ? 'Tạo mới' : 'Đã đối chiếu', pendingText: 'Cần tra cứu' },
    { key: '1B', statusId: 'accStatus1B', done: done1B, doneText: 'Hoàn tất', pendingText: 'Đang nhập' },
    { key: '1C', statusId: 'accStatus1C', done: done1C, doneText: 'Hoàn tất', pendingText: 'Chưa đủ' },
    { key: '1D', statusId: 'accStatus1D', done: done1D, doneText: 'Hoàn tất', pendingText: 'Chưa chọn' },
    { key: '1E', statusId: 'accStatus1E', done: done1E, doneText: 'Đủ chứng từ', pendingText: 'Thiếu ảnh' }
  ];

  statusConfig.forEach(cfg => {
    const pill = document.getElementById(cfg.statusId);
    if (!pill) return;
    pill.classList.remove('is-done', 'is-pending', 'is-warning');
    pill.classList.add(cfg.done ? 'is-done' : 'is-pending');
    const iconCls = cfg.done ? 'bi-check-circle-fill' : 'bi-clock-history';
    pill.innerHTML = `<i class="bi ${iconCls}" aria-hidden="true"></i><span>${escapeHtml(cfg.done ? cfg.doneText : cfg.pendingText)}</span>`;
  });

  // Update sticky stepper sub-step progress badge (1A/E .. 1E/E)
  const subBadge = document.getElementById('ccStepCustomerSubprogress');
  if (subBadge) {
    let stageCode = '1A/E';
    if (done1A && done1B && done1C && done1D && done1E) {
      stageCode = '1E/E';
    } else if (done1A && done1B && done1C && done1D) {
      stageCode = '1D/E';
    } else if (done1A && done1B && done1C) {
      stageCode = '1C/E';
    } else if (done1A && done1B) {
      stageCode = '1B/E';
    }
    subBadge.textContent = stageCode;
  }

  // Progressive auto-expand next sub-step when operator finishes current sub-step
  if (options.autoAdvanceFrom === '1B' && done1B) {
    setAccordionPanelOpen('1C', true);
  } else if (options.autoAdvanceFrom === '1C' && done1C) {
    setAccordionPanelOpen('1D', true);
  } else if (options.autoAdvanceFrom === '1D' && done1D) {
    setAccordionPanelOpen('1E', true);
  }
}

function initCustomerAccordionSubsteps() {
  CUSTOMER_SUBSTEP_PANELS.forEach(meta => {
    const btnEl = document.getElementById(meta.btnId);
    const collapseEl = document.getElementById(meta.collapseId);
    if (!btnEl || !collapseEl) return;

    // Fallback toggle when Bootstrap JS Collapse data-api is unavailable (e.g., offline / test harness)
    btnEl.addEventListener('click', function () {
      if (typeof bootstrap === 'undefined' || !bootstrap.Collapse) {
        const nextOpen = !collapseEl.classList.contains('show');
        setAccordionPanelOpen(meta.key, nextOpen);
      }
    });

    collapseEl.addEventListener('shown.bs.collapse', function () {
      btnEl.classList.remove('collapsed');
      btnEl.setAttribute('aria-expanded', 'true');
    });
    collapseEl.addEventListener('hidden.bs.collapse', function () {
      btnEl.classList.add('collapsed');
      btnEl.setAttribute('aria-expanded', 'false');
    });
  });

  // Auto-advance progressive disclosure listeners on key sub-step completion fields
  ['ccCustomerName', 'ccCustomerPhone'].forEach(id => {
    const el = document.getElementById(id);
    if (el) {
      el.addEventListener('change', () => syncCustomerAccordionSubsteps({ autoAdvanceFrom: '1B' }));
    }
  });
  ['ccWardCode', 'ccAddressStreet'].forEach(id => {
    const el = document.getElementById(id);
    if (el) {
      el.addEventListener('change', () => syncCustomerAccordionSubsteps({ autoAdvanceFrom: '1C' }));
    }
  });
  ['ccOccupationGroup', 'ccVerificationDocType'].forEach(id => {
    const el = document.getElementById(id);
    if (el) {
      el.addEventListener('change', () => syncCustomerAccordionSubsteps({ autoAdvanceFrom: '1D' }));
    }
  });

  syncCustomerAccordionSubsteps();
}

function initCustomerLookupAndInlineCreate() {
  const cccdInput = document.getElementById('ccCustomerIdNumber');
  const clearBtn = document.getElementById('btnClearCccd');
  const lookupBtn = document.getElementById('btnLookupCccd');
  const plusBtn = document.getElementById('btnQuickCreateCustomerPlus');
  const bannerCreateBtn = document.getElementById('btnBannerCreateCustomer');
  const unlockEditBtn = document.getElementById('btnUnlockCustomerEdit');
  const cancelEditTopBtn = document.getElementById('btnCancelCustomerEditTop');
  const cancelCreateBtn = document.getElementById('btnCancelCreateCustomer');
  const confirmCreateBtn = document.getElementById('btnConfirmCreateCustomer');
  const cancelEditBottomBtn = document.getElementById('btnCancelEditCustomer');
  const confirmUpdateBottomBtn = document.getElementById('btnConfirmUpdateCustomer');

  const selectedCustomerIdInput = document.getElementById('ccSelectedCustomerId');
  const lookupStateInput = document.getElementById('ccCustomerLookupState');

  const awaitingBanner = document.getElementById('ccCustomerAwaitingBanner');
  const foundBanner = document.getElementById('ccCustomerFoundBanner');
  const foundBannerIconGlyph = document.getElementById('ccFoundBannerIconGlyph');
  const notFoundBanner = document.getElementById('ccCustomerNotFoundBanner');
  const fieldsWrapper = document.getElementById('ccCustomerFieldsWrapper');
  const newCustomerHeader = document.getElementById('ccNewCustomerHeader');
  const newCustomerFooter = document.getElementById('ccNewCustomerFooter');
  const editCustomerFooter = document.getElementById('ccEditCustomerFooter');
  const editFooterCodeEl = document.getElementById('ccEditFooterCustomerCode');

  const foundHeading = document.getElementById('ccFoundBannerHeading');
  const foundCodeBadge = document.getElementById('ccFoundCustomerCode');
  const foundStatusBadge = document.getElementById('ccFoundCustomerStatus');
  const foundSubtext = document.getElementById('ccFoundBannerSubtext');
  const notFoundCccdDisplay = document.getElementById('ccNotFoundCccdDisplay');
  const creatingCccdBadge = document.getElementById('ccCreatingCccdBadge');

  const nameInput = document.getElementById('ccCustomerName');
  const phoneInput = document.getElementById('ccCustomerPhone');
  const emailInput = document.getElementById('ccCustomerEmail');
  const dobInput = document.getElementById('ccDateOfBirth');
  const genderSelect = document.getElementById('ccGender');
  const issueDateInput = document.getElementById('ccIdentityIssueDate');
  const issuePlaceInput = document.getElementById('ccIdentityIssuePlace');
  const issuePlaceCustomInput = document.getElementById('ccIdentityIssuePlaceCustom');
  const streetInput = document.getElementById('ccAddressStreet');
  const provinceSelect = document.getElementById('ccProvinceCode');
  const districtSelect = document.getElementById('ccDistrictCode');
  const wardSelect = document.getElementById('ccWardCode');
  const sameAsPermCheckbox = document.getElementById('ccSameAsPermanent');
  const currentAddrInput = document.getElementById('ccCurrentAddressInput');

  const occSelect = document.getElementById('ccOccupationGroup');
  const monthlyIncomeInput = document.getElementById('ccMonthlyIncome');
  const socialInsCheckbox = document.getElementById('ccHasSocialInsurance');
  const docTypeSelect = document.getElementById('ccVerificationDocType');
  const docUrlInput = document.getElementById('ccDocumentUrl');
  const frontUrlInput = document.getElementById('ccIdCardFrontUrl');
  const backUrlInput = document.getElementById('ccIdCardBackUrl');
  const portraitUrlInput = document.getElementById('ccPortraitUrl');
  const customerNotesInput = document.getElementById('ccCustomerNotes');

  const sampleChips = document.querySelectorAll('.pm-cccd-sample-chip[data-sample-cccd]');
  if (!cccdInput || !lookupBtn || !fieldsWrapper) return;

  let activeMatchedCccd = normalizeCccdDigits(cccdInput.value);
  let activeMatchedCustomerRecord = null;
  let isManualEditUnlocked = false;

  function renderFoundBannerDefaultSubtext(cccd) {
    if (!foundSubtext) return;
    foundSubtext.innerHTML = '';
    foundSubtext.appendChild(document.createTextNode('Đối chiếu khớp CCCD '));
    const cccdSpan = document.createElement('span');
    cccdSpan.className = 'font-tabular font-mono fw-semibold';
    cccdSpan.textContent = cccd;
    foundSubtext.appendChild(cccdSpan);
    foundSubtext.appendChild(document.createTextNode(' • Trần LTV đồng bộ theo nhóm nghề nghiệp.'));
  }

  function syncExistingCustomerEditChrome(isEditing, options = {}) {
    fieldsWrapper.classList.toggle('is-locked-autofill', !isEditing);
    if (editCustomerFooter) {
      editCustomerFooter.classList.toggle('d-none', !isEditing);
    }
    if (editFooterCodeEl && foundCodeBadge) {
      editFooterCodeEl.textContent = foundCodeBadge.textContent || 'KH';
    }
    if (foundBanner) {
      foundBanner.classList.toggle('is-editing', isEditing);
    }
    if (foundBannerIconGlyph) {
      foundBannerIconGlyph.className = isEditing ? 'bi bi-pencil-square' : 'bi bi-patch-check-fill';
    }
    if (cancelEditTopBtn) {
      cancelEditTopBtn.classList.toggle('d-none', !isEditing);
    }
    if (unlockEditBtn) {
      unlockEditBtn.classList.toggle('is-editing-save', isEditing);
      unlockEditBtn.innerHTML = isEditing
        ? '<i class="bi bi-check2-square" aria-hidden="true"></i><span>Lưu &amp; Khóa TT</span>'
        : '<i class="bi bi-pencil-square" aria-hidden="true"></i><span>Cập nhật TT</span>';
    }

    if (isEditing) {
      if (foundHeading) {
        foundHeading.textContent = 'Đang mở chỉnh sửa hồ sơ khách hàng lưu trữ';
      }
      if (foundStatusBadge) {
        foundStatusBadge.textContent = 'Đang chỉnh sửa • Chưa lưu';
      }
      if (foundSubtext) {
        foundSubtext.textContent = 'Sau khi cập nhật thông tin hoặc hình ảnh bên dưới, nhấn "Lưu Cập Nhật & Khóa TT" ở cuối mục này.';
      }
    } else if (options.wasUpdated) {
      const cccd = options.cccd || normalizeCccdDigits(cccdInput.value);
      if (foundHeading) {
        foundHeading.textContent = 'Đã cập nhật & khóa hồ sơ khách hàng lưu trữ';
      }
      if (foundStatusBadge) {
        const rec = activeMatchedCustomerRecord;
        foundStatusBadge.textContent = (rec && rec.statusLabel)
          ? rec.statusLabel
          : ((rec && rec.status === 'VIP') ? 'Khách VIP' : 'Đang hoạt động');
      }
      renderFoundBannerDefaultSubtext(cccd);
    }
  }

  function syncClearBtnVisibility() {
    if (!clearBtn) return;
    const hasVal = (cccdInput.value || '').trim().length > 0;
    clearBtn.classList.toggle('is-empty', !hasVal);
  }

  function syncActiveSampleChip(cccd) {
    sampleChips.forEach(chip => {
      chip.classList.toggle('active', chip.getAttribute('data-sample-cccd') === cccd);
    });
  }

  function setCustomerInputsLocked(isLocked) {
    const textControls = [
      nameInput, phoneInput, emailInput, dobInput,
      issueDateInput, issuePlaceCustomInput, streetInput, monthlyIncomeInput
    ];
    textControls.forEach(el => {
      if (!el) return;
      if (isLocked) {
        el.setAttribute('readonly', 'readonly');
      } else {
        el.removeAttribute('readonly');
      }
    });
  }

  function triggerAutofillFlash() {
    fieldsWrapper.querySelectorAll('.pm-autofill-target').forEach(el => {
      el.classList.remove('pm-field-autofilled-flash');
      void el.offsetWidth;
      el.classList.add('pm-field-autofilled-flash');
    });
  }

  function updateCustomerLedgerSummary() {
    const sumEl = document.getElementById('sumCustomerSummary');
    if (!sumEl) return;
    const nameVal = (nameInput ? nameInput.value : '').trim() || 'Chưa xác định';
    const cccdVal = normalizeCccdDigits(cccdInput.value) || '—';
    sumEl.textContent = `${nameVal} • ${cccdVal}`;
  }

  function enterAwaitingLookupState() {
    activeMatchedCccd = '';
    activeMatchedCustomerRecord = null;
    isManualEditUnlocked = false;
    if (lookupStateInput) lookupStateInput.value = 'AWAITING_LOOKUP';
    if (selectedCustomerIdInput) selectedCustomerIdInput.value = '';

    if (awaitingBanner) awaitingBanner.classList.remove('d-none');
    if (foundBanner) {
      foundBanner.classList.add('d-none');
      foundBanner.classList.remove('is-editing');
    }
    if (notFoundBanner) notFoundBanner.classList.add('d-none');
    if (plusBtn) {
      plusBtn.classList.add('d-none');
      plusBtn.classList.remove('is-attention');
    }

    fieldsWrapper.classList.add('d-none');
    fieldsWrapper.classList.remove('is-creating-new');
    if (newCustomerHeader) newCustomerHeader.classList.add('d-none');
    if (newCustomerFooter) newCustomerFooter.classList.add('d-none');
    if (editCustomerFooter) editCustomerFooter.classList.add('d-none');
    updateCustomerLedgerSummary();
    updateStepperCompletionStates();
  }

  async function applyFoundCustomer(customer, options = {}) {
    const cccd = normalizeCccdDigits(customer.identityNumber || cccdInput.value);
    activeMatchedCccd = cccd;
    isManualEditUnlocked = false;

    if (lookupStateInput) lookupStateInput.value = 'FOUND';
    if (selectedCustomerIdInput) {
      selectedCustomerIdInput.value = customer.id != null ? String(customer.id) : '';
    }

    if (awaitingBanner) awaitingBanner.classList.add('d-none');
    if (notFoundBanner) notFoundBanner.classList.add('d-none');
    if (foundBanner) {
      foundBanner.classList.remove('d-none', 'is-editing');
    }
    if (foundBannerIconGlyph) {
      foundBannerIconGlyph.className = 'bi bi-patch-check-fill';
    }
    if (cancelEditTopBtn) {
      cancelEditTopBtn.classList.add('d-none');
    }
    if (plusBtn) {
      plusBtn.classList.add('d-none');
      plusBtn.classList.remove('is-attention');
    }

    if (foundHeading) {
      foundHeading.textContent = options.isNewlyCreated
        ? 'Đã tạo & tự động áp dụng hồ sơ khách hàng mới'
        : 'Đã tự động điền hồ sơ khách hàng lưu trữ';
    }
    if (foundCodeBadge) {
      foundCodeBadge.textContent = customer.customerCode || 'KH-MỚI';
    }
    if (editFooterCodeEl) {
      editFooterCodeEl.textContent = customer.customerCode || 'KH-MỚI';
    }
    if (foundStatusBadge) {
      foundStatusBadge.textContent = options.isNewlyCreated
        ? 'Hồ sơ mới tạo'
        : (customer.statusLabel || (customer.status === 'VIP' ? 'Khách VIP' : 'Đang hoạt động'));
    }
    renderFoundBannerDefaultSubtext(cccd);
    if (unlockEditBtn) {
      unlockEditBtn.classList.remove('is-editing-save');
      unlockEditBtn.innerHTML = '<i class="bi bi-pencil-square" aria-hidden="true"></i><span>Cập nhật TT</span>';
    }

    fieldsWrapper.classList.remove('d-none', 'is-creating-new');
    fieldsWrapper.classList.add('is-locked-autofill');
    if (newCustomerHeader) newCustomerHeader.classList.add('d-none');
    if (newCustomerFooter) newCustomerFooter.classList.add('d-none');
    if (editCustomerFooter) editCustomerFooter.classList.add('d-none');

    if (nameInput) nameInput.value = customer.fullName || '';
    if (phoneInput) phoneInput.value = customer.phoneNumber || '';
    if (emailInput) emailInput.value = customer.email || '';
    if (dobInput) dobInput.value = customer.dateOfBirth ? String(customer.dateOfBirth).slice(0, 10) : '';
    if (genderSelect && customer.gender) genderSelect.value = customer.gender;
    if (issueDateInput) {
      issueDateInput.value = customer.identityIssueDate ? String(customer.identityIssueDate).slice(0, 10) : '';
    }
    if (issuePlaceInput) {
      const normalizedPlace = normalizeSavedIssuePlace(customer.identityIssuePlace, customer.identityIssueDate);
      issuePlaceInput.value = normalizedPlace.selectValue;
      if (issuePlaceCustomInput) {
        issuePlaceCustomInput.value = normalizedPlace.customValue;
      }
      syncIdentityIssuePlaceCustomUI({ focusCustom: false });
    }

    const incomeVal = parseVND(customer.monthlyIncome != null ? customer.monthlyIncome : 0);
    if (monthlyIncomeInput) {
      monthlyIncomeInput.value = incomeVal > 0 ? formatVndDigits(incomeVal) : '';
    }
    if (socialInsCheckbox) {
      socialInsCheckbox.checked = Boolean(customer.hasSocialInsurance);
    }

    if (occSelect) {
      occSelect.value = customer.occupationGroup || 'OFFICE_WORKER';
    }
    updateAllowedDocsForOccupation(false);

    if (docTypeSelect && customer.verificationDocType) {
      const hasDocOpt = Array.from(docTypeSelect.options).some(o => o.value === customer.verificationDocType);
      if (hasDocOpt) docTypeSelect.value = customer.verificationDocType;
    }

    if (customerNotesInput) customerNotesInput.value = customer.notes || '';
    await PM_MEDIA_MANAGER.hydrateCustomerMedia(customer, { locked: true });
    activeMatchedCustomerRecord = Object.assign({}, customer, PM_MEDIA_MANAGER.snapshotCustomerLocalMedia());

    await PM_ADDRESS_CASCADE.hydrateCustomerAddress(customer);

    setCustomerInputsLocked(true);
    syncActiveSampleChip(cccd);
    updateCustomerLedgerSummary();
    syncBankAccountHolderFromCustomer(true);
    recalculateLoanAndLedger({ triggerServerLtv: true });
    updateStepperCompletionStates();

    if (!options.silent) {
      triggerAutofillFlash();
    }
    if (options.focusCollateral) {
      const colNameEl = document.getElementById('ccCollateralName');
      if (colNameEl) colNameEl.focus();
    }
  }

  function applyNotFoundState(cccd) {
    activeMatchedCccd = '';
    activeMatchedCustomerRecord = null;
    isManualEditUnlocked = false;

    if (lookupStateInput) lookupStateInput.value = 'NOT_FOUND';
    if (selectedCustomerIdInput) selectedCustomerIdInput.value = '';

    if (awaitingBanner) awaitingBanner.classList.add('d-none');
    if (foundBanner) {
      foundBanner.classList.add('d-none');
      foundBanner.classList.remove('is-editing');
    }
    if (notFoundBanner) notFoundBanner.classList.remove('d-none');
    if (notFoundCccdDisplay) notFoundCccdDisplay.textContent = cccd;

    if (plusBtn) {
      plusBtn.classList.remove('d-none');
      plusBtn.classList.add('is-attention');
    }

    fieldsWrapper.classList.add('d-none');
    fieldsWrapper.classList.remove('is-creating-new', 'is-locked-autofill');
    if (newCustomerHeader) newCustomerHeader.classList.add('d-none');
    if (newCustomerFooter) newCustomerFooter.classList.add('d-none');
    if (editCustomerFooter) editCustomerFooter.classList.add('d-none');

    syncActiveSampleChip(cccd);
    updateCustomerLedgerSummary();
    updateStepperCompletionStates();
  }

  async function openInlineCustomerCreator() {
    const cccd = normalizeCccdDigits(cccdInput.value);
    if (!isValidCccdLength(cccd)) {
      showToast('Vui lòng nhập đúng 9 số CMND hoặc 12 số CCCD trước khi tạo khách hàng mới.', 'warning');
      cccdInput.focus();
      return;
    }

    cccdInput.value = cccd;
    syncClearBtnVisibility();
    activeMatchedCustomerRecord = null;
    isManualEditUnlocked = false;

    if (lookupStateInput) lookupStateInput.value = 'CREATING_NEW';
    if (selectedCustomerIdInput) selectedCustomerIdInput.value = '';

    if (awaitingBanner) awaitingBanner.classList.add('d-none');
    if (foundBanner) {
      foundBanner.classList.add('d-none');
      foundBanner.classList.remove('is-editing');
    }
    if (notFoundBanner) notFoundBanner.classList.add('d-none');
    if (plusBtn) {
      plusBtn.classList.add('d-none');
      plusBtn.classList.remove('is-attention');
    }

    if (creatingCccdBadge) {
      creatingCccdBadge.textContent = `CCCD: ${cccd}`;
    }

    fieldsWrapper.classList.remove('d-none', 'is-locked-autofill');
    fieldsWrapper.classList.add('is-creating-new');
    if (newCustomerHeader) newCustomerHeader.classList.remove('d-none');
    if (newCustomerFooter) newCustomerFooter.classList.remove('d-none');
    if (editCustomerFooter) editCustomerFooter.classList.add('d-none');

    setCustomerInputsLocked(false);
    if (nameInput) nameInput.value = '';
    if (phoneInput) phoneInput.value = '';
    if (emailInput) emailInput.value = '';
    if (dobInput) dobInput.value = '';
    if (genderSelect) genderSelect.value = 'NAM';
    if (issueDateInput) issueDateInput.value = '';
    if (issuePlaceInput) {
      issuePlaceInput.value = CCCD_ISSUE_PLACE_OPTIONS.BO_CONG_AN;
      if (issuePlaceCustomInput) issuePlaceCustomInput.value = '';
      syncIdentityIssuePlaceCustomUI({ focusCustom: false });
    }
    if (streetInput) streetInput.value = '';
    if (sameAsPermCheckbox) sameAsPermCheckbox.checked = true;
    const curGroup = document.getElementById('ccCurrentAddressGroup');
    if (curGroup) curGroup.classList.add('d-none');
    if (currentAddrInput) currentAddrInput.value = '';

    if (occSelect) occSelect.value = 'OFFICE_WORKER';
    updateAllowedDocsForOccupation(false);
    if (monthlyIncomeInput) monthlyIncomeInput.value = '';
    if (socialInsCheckbox) socialInsCheckbox.checked = false;
    if (customerNotesInput) customerNotesInput.value = '';
    PM_MEDIA_MANAGER.resetCustomerMediaForNewCustomer();

    if (provinceSelect) {
      await PM_ADDRESS_CASCADE.initProvincesDropdown();
      provinceSelect.value = '';
      await PM_ADDRESS_CASCADE.loadDistrictOptions('', '');
    }
    PM_ADDRESS_CASCADE.composePermanentAddress();
    updateCustomerLedgerSummary();
    recalculateLoanAndLedger({ triggerServerLtv: false });
    setAccordionPanelOpen('1A', true);
    setAccordionPanelOpen('1B', true);
    setAccordionPanelOpen('1C', false);
    setAccordionPanelOpen('1D', false);
    setAccordionPanelOpen('1E', false);
    syncCustomerAccordionSubsteps();

    if (nameInput) nameInput.focus();
  }

  function buildCustomerRequestPayload(cccd) {
    const wardLabel = PM_ADDRESS_CASCADE.getSelectedOptionLabel(wardSelect);
    const districtLabel = PM_ADDRESS_CASCADE.getSelectedOptionLabel(districtSelect);
    const provinceLabel = PM_ADDRESS_CASCADE.getSelectedOptionLabel(provinceSelect);
    const rawStreet = streetInput ? streetInput.value.trim() : '';
    const addressDetail = PM_ADDRESS_CASCADE.stripAdminSuffixesFromStreet(rawStreet, [
      wardLabel,
      districtLabel,
      provinceLabel
    ]);
    const permanentFull = PM_ADDRESS_CASCADE.composePermanentAddress();
    const samePerm = sameAsPermCheckbox ? sameAsPermCheckbox.checked : true;
    const currentContact = (!samePerm && currentAddrInput && currentAddrInput.value.trim())
      ? currentAddrInput.value.trim()
      : permanentFull;
    const docUrl = (docUrlInput ? docUrlInput.value : '').trim();
    const incomeNum = parseVND(monthlyIncomeInput ? monthlyIncomeInput.value : 0);

    return {
      fullName: (nameInput ? nameInput.value : '').trim(),
      identityNumber: cccd,
      identityIssueDate: (issueDateInput && issueDateInput.value) ? issueDateInput.value : null,
      identityIssuePlace: getResolvedIdentityIssuePlace() || null,
      phoneNumber: (phoneInput ? phoneInput.value : '').trim(),
      email: (emailInput && emailInput.value.trim()) ? emailInput.value.trim() : null,
      dateOfBirth: (dobInput && dobInput.value) ? dobInput.value : null,
      gender: genderSelect ? genderSelect.value : 'NAM',
      provinceCode: provinceSelect ? (provinceSelect.value || null) : null,
      districtCode: districtSelect ? (districtSelect.value || null) : null,
      wardCode: wardSelect ? (wardSelect.value || null) : null,
      addressDetail: addressDetail || null,
      address: currentContact,
      permanentAddress: permanentFull,
      occupationGroup: occSelect ? occSelect.value : 'OFFICE_WORKER',
      monthlyIncome: incomeNum > 0 ? incomeNum : 0,
      hasSocialInsurance: Boolean(socialInsCheckbox && socialInsCheckbox.checked),
      verificationDocType: docTypeSelect ? docTypeSelect.value : 'PAYSLIP_BANK_STATEMENT',
      verificationDocUrls: docUrl ? [docUrl] : [],
      idCardFrontUrl: (frontUrlInput && frontUrlInput.value.trim()) ? frontUrlInput.value.trim() : null,
      idCardBackUrl: (backUrlInput && backUrlInput.value.trim()) ? backUrlInput.value.trim() : null,
      portraitUrl: (portraitUrlInput && portraitUrlInput.value.trim()) ? portraitUrlInput.value.trim() : null,
      notes: (customerNotesInput && customerNotesInput.value.trim()) ? customerNotesInput.value.trim() : null
    };
  }

  async function saveInlineNewCustomer(options = {}) {
    const cccd = normalizeCccdDigits(cccdInput.value);
    const fullName = (nameInput ? nameInput.value : '').trim();
    const phone = (phoneInput ? phoneInput.value : '').trim();
    const phoneDigits = phone.replace(/\D+/g, '');
    const provCode = provinceSelect ? provinceSelect.value : '';
    const distCode = districtSelect ? districtSelect.value : '';
    const wardCode = wardSelect ? wardSelect.value : '';
    const street = (streetInput ? streetInput.value : '').trim();

    if (!isValidCccdLength(cccd)) {
      showToast('Số CCCD không hợp lệ (cần đúng 9 hoặc 12 chữ số).', 'warning');
      cccdInput.focus();
      return false;
    }
    if (fullName.length < 2) {
      expandParentAccordionIfCollapsed(nameInput);
      markFieldInvalid(nameInput);
      showToast('Vui lòng nhập Họ và Tên đầy đủ của khách hàng mới.', 'warning');
      if (nameInput) nameInput.focus();
      return false;
    }
    if (phoneDigits.length < 9 || phoneDigits.length > 12) {
      expandParentAccordionIfCollapsed(phoneInput);
      markFieldInvalid(phoneInput);
      showToast('Vui lòng nhập Số điện thoại hợp lệ (9 - 11 chữ số).', 'warning');
      if (phoneInput) phoneInput.focus();
      return false;
    }
    if (!provCode) {
      expandParentAccordionIfCollapsed(provinceSelect);
      markFieldInvalid(provinceSelect);
      showToast('Vui lòng chọn Tỉnh / Thành phố thường trú.', 'warning');
      provinceSelect.focus();
      return false;
    }
    if (!distCode) {
      expandParentAccordionIfCollapsed(districtSelect);
      markFieldInvalid(districtSelect);
      showToast('Vui lòng chọn Quận / Huyện thường trú.', 'warning');
      districtSelect.focus();
      return false;
    }
    if (!wardCode) {
      expandParentAccordionIfCollapsed(wardSelect);
      markFieldInvalid(wardSelect);
      showToast('Vui lòng chọn Phường / Xã thường trú.', 'warning');
      wardSelect.focus();
      return false;
    }
    if (street.length < 2) {
      expandParentAccordionIfCollapsed(streetInput);
      markFieldInvalid(streetInput);
      showToast('Vui lòng nhập Số nhà, tên đường thường trú.', 'warning');
      if (streetInput) streetInput.focus();
      return false;
    }
    if (!PM_MEDIA_MANAGER.hasVerificationDocuments()) {
      const verBox = document.getElementById('ccVerificationDocBox');
      expandParentAccordionIfCollapsed(verBox);
      markFieldInvalid(verBox, 'Vui lòng chọn ít nhất 01 hình ảnh chứng từ xác minh KYC từ máy tính.');
      showToast('Vui lòng chọn ít nhất 01 hình ảnh chứng từ xác minh KYC từ máy tính.', 'warning');
      const pickBtn = document.getElementById('btnPickVerificationDocs');
      if (pickBtn) pickBtn.focus();
      return false;
    }

    try {
      await PM_MEDIA_MANAGER.uploadPendingCustomerMedia(cccd);
    } catch (uploadErr) {
      showToast((uploadErr && uploadErr.message) || 'Không thể tải lên hình ảnh chứng từ xác minh.', 'danger');
      return false;
    }

    const docUrl = (docUrlInput ? docUrlInput.value : '').trim();
    const requestBody = buildCustomerRequestPayload(cccd);
    const mediaSnapshot = PM_MEDIA_MANAGER.snapshotCustomerLocalMedia();
    let savedRecord = null;

    if (isBackendServerMode()) {
      try {
        const apiRes = await apiFetch('/api/v1/customers', {
          method: 'POST',
          body: JSON.stringify(requestBody)
        });
        if (apiRes && apiRes.data) {
          savedRecord = Object.assign({}, apiRes.data, {
            wardCode: apiRes.data.wardCode || wardCode,
            addressDetail: apiRes.data.addressDetail || street,
            street,
            verificationDocUrl: docUrl,
            verificationDocUrls: apiRes.data.verificationDocUrls || mediaSnapshot.verificationDocUrls || docUrl,
            verificationDocUrlList: (Array.isArray(apiRes.data.verificationDocUrlList) && apiRes.data.verificationDocUrlList.length > 0)
              ? apiRes.data.verificationDocUrlList
              : mediaSnapshot.verificationDocUrlList,
            _localVerificationItems: mediaSnapshot._localVerificationItems,
            _localSinglePreviews: mediaSnapshot._localSinglePreviews,
            statusLabel: 'Hồ sơ mới tạo'
          });
        }
      } catch (err) {
        if (err && (err.status === 400 || err.status === 409 || err.isBranchAccessDenied)) {
          showToast(err.message || 'Không thể lưu hồ sơ khách hàng mới.', 'danger');
          return false;
        }
        // If backend is unreachable in static/offline mode, fall back cleanly below
      }
    }

    if (!savedRecord) {
      const now = new Date();
      const yy = String(now.getFullYear()).slice(-2);
      const mm = String(now.getMonth() + 1).padStart(2, '0');
      savedRecord = Object.assign({}, requestBody, mediaSnapshot, {
        id: Date.now(),
        customerCode: `KH-${yy}${mm}-${cccd.slice(-6)}`,
        wardCode,
        addressDetail: street,
        street,
        verificationDocUrl: docUrl,
        status: 'ACTIVE',
        statusLabel: 'Hồ sơ mới tạo'
      });
    }

    DEMO_CUSTOMERS_BY_CCCD[cccd] = savedRecord;
    await applyFoundCustomer(savedRecord, {
      isNewlyCreated: true,
      focusCollateral: !options.fromContractSubmit
    });

    if (!options.fromContractSubmit) {
      showToast(`Đã lưu hồ sơ khách hàng ${savedRecord.customerCode} (${savedRecord.fullName}) thành công!`, 'success');
    }
    return true;
  }

  async function lookupCustomerRecord(cccd) {
    if (isBackendServerMode()) {
      try {
        const res = await apiFetch(`/api/v1/customers/identity/${encodeURIComponent(cccd)}`, {
          method: 'GET'
        });
        if (res && res.data) {
          return res.data;
        }
      } catch (err) {
        if (err && err.status === 404) {
          return DEMO_CUSTOMERS_BY_CCCD[cccd] || null;
        }
        // Graceful fallback to offline seed catalog
      }
    }
    return DEMO_CUSTOMERS_BY_CCCD[cccd] || null;
  }

  async function performCccdLookup(options = {}) {
    const cccd = normalizeCccdDigits(cccdInput.value);
    cccdInput.value = cccd;
    syncClearBtnVisibility();
    clearFieldValidationError(cccdInput);

    if (!isValidCccdLength(cccd)) {
      if (!options.silentInvalid) {
        showToast('Vui lòng nhập đúng 9 số CMND hoặc 12 số CCCD để tra cứu.', 'warning');
        cccdInput.focus();
      }
      return;
    }

    lookupBtn.disabled = true;
    setLoadingBarActive(true);
    try {
      const matched = await lookupCustomerRecord(cccd);
      if (matched) {
        await applyFoundCustomer(matched, { silent: Boolean(options.silent) });
        if (!options.silent) {
          showToast(`Đã nạp hồ sơ khách hàng: ${matched.fullName} (${matched.customerCode || 'KH'})`, 'success');
        }
      } else {
        applyNotFoundState(cccd);
        if (!options.silent) {
          showToast(`Chưa có hồ sơ cho CCCD ${cccd}. Nhấn "Tạo khách hàng mới" để đăng ký KYC.`, 'info');
        }
      }
    } finally {
      lookupBtn.disabled = false;
      setLoadingBarActive(false);
    }
  }

  // Event bindings
  cccdInput.addEventListener('input', function () {
    const normalized = normalizeCccdDigits(cccdInput.value);
    if (cccdInput.value !== normalized) {
      cccdInput.value = normalized;
    }
    syncClearBtnVisibility();
    syncActiveSampleChip(normalized);
    if (normalized !== activeMatchedCccd) {
      enterAwaitingLookupState();
    }
    markDraftUnsaved();
  });

  cccdInput.addEventListener('keydown', function (e) {
    if (e.key === 'Enter') {
      e.preventDefault();
      performCccdLookup();
    }
  });

  if (clearBtn) {
    clearBtn.addEventListener('click', function () {
      cccdInput.value = '';
      syncClearBtnVisibility();
      syncActiveSampleChip('');
      enterAwaitingLookupState();
      cccdInput.focus();
    });
  }

  lookupBtn.addEventListener('click', function () {
    performCccdLookup();
  });

  if (plusBtn) {
    plusBtn.addEventListener('click', function () {
      openInlineCustomerCreator();
    });
  }

  if (bannerCreateBtn) {
    bannerCreateBtn.addEventListener('click', function () {
      openInlineCustomerCreator();
    });
  }

  if (cancelCreateBtn) {
    cancelCreateBtn.addEventListener('click', function () {
      const cccd = normalizeCccdDigits(cccdInput.value);
      if (isValidCccdLength(cccd)) {
        applyNotFoundState(cccd);
      } else {
        enterAwaitingLookupState();
      }
    });
  }

  if (confirmCreateBtn) {
    confirmCreateBtn.addEventListener('click', function () {
      withSubmitLock(confirmCreateBtn, async () => {
        await saveInlineNewCustomer({ fromContractSubmit: false });
      });
    });
  }

  function unlockExistingCustomerEdit() {
    const cccd = normalizeCccdDigits(cccdInput.value);
    if (!activeMatchedCustomerRecord) {
      const seed = DEMO_CUSTOMERS_BY_CCCD[cccd] || {};
      activeMatchedCustomerRecord = Object.assign(
        {},
        seed,
        buildCustomerRequestPayload(cccd),
        PM_MEDIA_MANAGER.snapshotCustomerLocalMedia(),
        {
          id: selectedCustomerIdInput ? selectedCustomerIdInput.value : (seed.id || ''),
          customerCode: foundCodeBadge ? foundCodeBadge.textContent : (seed.customerCode || 'KH')
        }
      );
    }

    isManualEditUnlocked = true;
    setCustomerInputsLocked(false);
    PM_MEDIA_MANAGER.setCustomerMediaLocked(false);
    syncExistingCustomerEditChrome(true);
    setAccordionPanelOpen('1B', true);
    setAccordionPanelOpen('1E', true);
    syncCustomerAccordionSubsteps();
    if (phoneInput) phoneInput.focus();
  }

  async function cancelExistingCustomerEdits() {
    const verBox = document.getElementById('ccVerificationDocBox');
    [nameInput, phoneInput, provinceSelect, districtSelect, wardSelect, streetInput, verBox].forEach(el => {
      if (el) clearFieldValidationError(el);
    });

    if (activeMatchedCustomerRecord) {
      await applyFoundCustomer(activeMatchedCustomerRecord, { silent: true });
    } else {
      isManualEditUnlocked = false;
      setCustomerInputsLocked(true);
      PM_MEDIA_MANAGER.setCustomerMediaLocked(true);
      syncExistingCustomerEditChrome(false);
    }
    showToast('Đã hủy chỉnh sửa và khôi phục thông tin gốc của khách hàng.', 'info');
  }

  async function saveExistingCustomerEdits(options = {}) {
    const cccd = normalizeCccdDigits(cccdInput.value);
    const fullName = (nameInput ? nameInput.value : '').trim();
    const phone = (phoneInput ? phoneInput.value : '').trim();
    const phoneDigits = phone.replace(/\D+/g, '');
    const provCode = provinceSelect ? provinceSelect.value : '';
    const distCode = districtSelect ? districtSelect.value : '';
    const wardCode = wardSelect ? wardSelect.value : '';
    const street = (streetInput ? streetInput.value : '').trim();

    if (fullName.length < 2) {
      markFieldInvalid(nameInput);
      showToast('Vui lòng nhập Họ và Tên đầy đủ của khách hàng.', 'warning');
      if (nameInput) nameInput.focus();
      return false;
    }
    if (phoneDigits.length < 9 || phoneDigits.length > 12) {
      markFieldInvalid(phoneInput);
      showToast('Vui lòng nhập Số điện thoại hợp lệ (9 - 11 chữ số).', 'warning');
      if (phoneInput) phoneInput.focus();
      return false;
    }
    if (!provCode) {
      markFieldInvalid(provinceSelect);
      showToast('Vui lòng chọn Tỉnh / Thành phố thường trú.', 'warning');
      provinceSelect.focus();
      return false;
    }
    if (!distCode) {
      markFieldInvalid(districtSelect);
      showToast('Vui lòng chọn Quận / Huyện thường trú.', 'warning');
      districtSelect.focus();
      return false;
    }
    if (!wardCode) {
      markFieldInvalid(wardSelect);
      showToast('Vui lòng chọn Phường / Xã thường trú.', 'warning');
      wardSelect.focus();
      return false;
    }
    if (street.length < 2) {
      markFieldInvalid(streetInput);
      showToast('Vui lòng nhập Số nhà, tên đường thường trú.', 'warning');
      if (streetInput) streetInput.focus();
      return false;
    }
    if (!PM_MEDIA_MANAGER.hasVerificationDocuments()) {
      const verBox = document.getElementById('ccVerificationDocBox');
      markFieldInvalid(verBox, 'Vui lòng giữ lại hoặc chọn ít nhất 01 hình ảnh chứng từ xác minh KYC.');
      showToast('Vui lòng giữ lại hoặc chọn ít nhất 01 hình ảnh chứng từ xác minh KYC.', 'warning');
      const pickBtn = document.getElementById('btnPickVerificationDocs');
      if (pickBtn) pickBtn.focus();
      return false;
    }

    try {
      await PM_MEDIA_MANAGER.uploadPendingCustomerMedia(cccd);
    } catch (uploadErr) {
      showToast((uploadErr && uploadErr.message) || 'Không thể tải lên hình ảnh chứng từ xác minh.', 'danger');
      return false;
    }

    const custId = selectedCustomerIdInput ? selectedCustomerIdInput.value.trim() : '';
    const updateBody = buildCustomerRequestPayload(cccd);
    const mediaSnapshot = PM_MEDIA_MANAGER.snapshotCustomerLocalMedia();
    let updatedApiData = null;

    if (custId && isBackendServerMode()) {
      try {
        const putRes = await apiFetch(`/api/v1/customers/${encodeURIComponent(custId)}`, {
          method: 'PUT',
          body: JSON.stringify(updateBody)
        });
        if (putRes && putRes.data) {
          updatedApiData = putRes.data;
        }
      } catch (err) {
        if (err && (err.status === 400 || err.status === 409 || err.isBranchAccessDenied)) {
          showToast(err.message || 'Không thể lưu cập nhật thông tin khách hàng.', 'danger');
          return false;
        }
        // Keep updated values locally in static/offline fallback mode
      }
    }

    const mergedRecord = Object.assign(
      {},
      activeMatchedCustomerRecord || {},
      updatedApiData || {},
      updateBody,
      mediaSnapshot,
      {
        id: custId || (activeMatchedCustomerRecord && activeMatchedCustomerRecord.id) || '',
        customerCode: (updatedApiData && updatedApiData.customerCode)
          || (activeMatchedCustomerRecord && activeMatchedCustomerRecord.customerCode)
          || (foundCodeBadge ? foundCodeBadge.textContent : 'KH'),
        wardCode,
        addressDetail: updateBody.addressDetail || street,
        street: updateBody.addressDetail || street
      }
    );

    activeMatchedCustomerRecord = mergedRecord;
    if (DEMO_CUSTOMERS_BY_CCCD[cccd]) {
      Object.assign(DEMO_CUSTOMERS_BY_CCCD[cccd], mergedRecord);
    } else {
      DEMO_CUSTOMERS_BY_CCCD[cccd] = mergedRecord;
    }

    isManualEditUnlocked = false;
    setCustomerInputsLocked(true);
    PM_MEDIA_MANAGER.setCustomerMediaLocked(true);
    syncExistingCustomerEditChrome(false, { wasUpdated: true, cccd });
    updateCustomerLedgerSummary();
    recalculateLoanAndLedger({ triggerServerLtv: true });
    updateStepperCompletionStates();

    if (!options.fromContractSubmit) {
      showToast('Đã lưu cập nhật thông tin & hình ảnh KYC khách hàng thành công.', 'success');
    }
    return true;
  }

  if (unlockEditBtn) {
    unlockEditBtn.addEventListener('click', function () {
      if (!isManualEditUnlocked) {
        unlockExistingCustomerEdit();
      } else {
        withSubmitLock(unlockEditBtn, async () => {
          await saveExistingCustomerEdits({ fromContractSubmit: false });
        });
      }
    });
  }

  if (confirmUpdateBottomBtn) {
    confirmUpdateBottomBtn.addEventListener('click', function () {
      withSubmitLock(confirmUpdateBottomBtn, async () => {
        await saveExistingCustomerEdits({ fromContractSubmit: false });
      });
    });
  }

  if (cancelEditTopBtn) {
    cancelEditTopBtn.addEventListener('click', function () {
      cancelExistingCustomerEdits();
    });
  }

  if (cancelEditBottomBtn) {
    cancelEditBottomBtn.addEventListener('click', function () {
      cancelExistingCustomerEdits();
    });
  }

  sampleChips.forEach(chip => {
    chip.addEventListener('click', function () {
      const sampleCccd = chip.getAttribute('data-sample-cccd') || '';
      cccdInput.value = sampleCccd;
      syncClearBtnVisibility();
      performCccdLookup();
    });
  });

  if (nameInput) {
    nameInput.addEventListener('input', function () {
      clearFieldValidationError(nameInput);
      updateCustomerLedgerSummary();
      PM_MEDIA_MANAGER.refreshDescriptiveAltTexts();
      syncCustomerAccordionSubsteps();
      markDraftUnsaved();
    });
  }

  const handleIssueDateSmartSuggest = () => {
    if (!issueDateInput || !issuePlaceInput) return;
    const rawIso = (issueDateInput.value || '').trim();
    if (!rawIso) return;
    const suggestedPlace = suggestIdentityIssuePlaceByDate(rawIso);
    if (!suggestedPlace) return;
    issuePlaceInput.value = suggestedPlace;
    if (suggestedPlace === CCCD_ISSUE_PLACE_OPTIONS.OTHER && issuePlaceCustomInput) {
      issuePlaceCustomInput.removeAttribute('readonly');
    }
    syncIdentityIssuePlaceCustomUI({ focusCustom: false });
    issuePlaceInput.classList.remove('pm-field-autofilled-flash');
    void issuePlaceInput.offsetWidth;
    issuePlaceInput.classList.add('pm-field-autofilled-flash');
    markDraftUnsaved();
  };

  if (issueDateInput) {
    issueDateInput.addEventListener('change', handleIssueDateSmartSuggest);
  }

  if (issuePlaceInput) {
    issuePlaceInput.addEventListener('change', function () {
      if (issuePlaceInput.value === CCCD_ISSUE_PLACE_OPTIONS.OTHER && issuePlaceCustomInput) {
        issuePlaceCustomInput.removeAttribute('readonly');
      }
      syncIdentityIssuePlaceCustomUI({ focusCustom: true });
      markDraftUnsaved();
    });
  }

  if (issuePlaceCustomInput) {
    issuePlaceCustomInput.addEventListener('input', function () {
      clearFieldValidationError(issuePlaceCustomInput);
      markDraftUnsaved();
    });
  }

  if (occSelect) {
    occSelect.addEventListener('change', function () {
      updateAllowedDocsForOccupation(false);
      PM_MEDIA_MANAGER.refreshDescriptiveAltTexts();
      recalculateLoanAndLedger({ triggerServerLtv: true });
      syncCustomerAccordionSubsteps({ autoAdvanceFrom: '1D' });
      markDraftUnsaved();
    });
  }

  if (docTypeSelect) {
    docTypeSelect.addEventListener('change', function () {
      PM_MEDIA_MANAGER.refreshDescriptiveAltTexts();
      recalculateLoanAndLedger({ triggerServerLtv: true });
      syncCustomerAccordionSubsteps({ autoAdvanceFrom: '1D' });
      markDraftUnsaved();
    });
  }

  // Expose async customer-state validator & auto-save guard for #formCreateContract submission
  window.pmEnsureCustomerReadyForOrigination = async function () {
    const state = lookupStateInput ? lookupStateInput.value : 'FOUND';
    if (state === 'CREATING_NEW') {
      return await saveInlineNewCustomer({ fromContractSubmit: true });
    }
    if (state === 'FOUND' && isManualEditUnlocked) {
      return await saveExistingCustomerEdits({ fromContractSubmit: true });
    }
    return state === 'FOUND';
  };

  window.pmPerformCccdLookup = performCccdLookup;

  // Initial URL ?cccd=... or body[data-prefill-cccd] hydration
  syncClearBtnVisibility();
  updateAllowedDocsForOccupation(true);

  let prefillCccd = '';
  if (typeof window !== 'undefined' && window.location && window.location.search) {
    const params = new URLSearchParams(window.location.search);
    prefillCccd = normalizeCccdDigits(params.get('cccd') || '');
  }
  if (!prefillCccd && typeof document !== 'undefined' && document.body) {
    prefillCccd = normalizeCccdDigits(document.body.getAttribute('data-prefill-cccd') || '');
  }

  if (prefillCccd && isValidCccdLength(prefillCccd)) {
    cccdInput.value = prefillCccd;
    syncClearBtnVisibility();
    performCccdLookup({ silent: false });
  } else if (activeMatchedCccd && isValidCccdLength(activeMatchedCccd)) {
    performCccdLookup({ silent: true });
  }
}

/**
 * ============================================================================
 * 3. DYNAMIC COLLATERAL CATEGORY & VALUATION ASSISTANCE
 * ============================================================================
 */
function getActiveCollateralCategory() {
  const catSelect = document.getElementById('ccCollateralCategory');
  return (catSelect && catSelect.value) ? catSelect.value : 'MOTORBIKE';
}

function toggleDynamicCollateralGroups(category) {
  const vehicleGroup = document.getElementById('ccVehicleFieldsGroup');
  const techGroup = document.getElementById('ccTechFieldsGroup');
  const goldGroup = document.getElementById('ccGoldFieldsGroup');
  const otherGroup = document.getElementById('ccOtherFieldsGroup');

  const isVehicle = category === 'MOTORBIKE' || category === 'CAR';
  const isTech = category === 'PHONE' || category === 'LAPTOP' || category === 'WATCH';
  const isGold = category === 'GOLD_JEWELRY';
  const isOther = !isVehicle && !isTech && !isGold;

  const groupEntries = [
    { el: vehicleGroup, visible: isVehicle, requiredIds: ['ccLicensePlate', 'ccFrameNumber'] },
    { el: techGroup, visible: isTech, requiredIds: ['ccSerialOrImei'] },
    { el: goldGroup, visible: isGold, requiredIds: ['ccGoldPurity', 'ccGoldWeightChi'] },
    { el: otherGroup, visible: isOther, requiredIds: ['ccOtherAssetSpec'] }
  ];

  groupEntries.forEach(({ el, visible, requiredIds }) => {
    if (!el) return;
    el.classList.toggle('d-none', !visible);
    el.querySelectorAll('input, select, textarea').forEach(ctrl => {
      if (!visible) {
        ctrl.removeAttribute('required');
        clearFieldValidationError(ctrl);
      } else if (requiredIds.includes(ctrl.id)) {
        ctrl.setAttribute('required', 'required');
      }
    });
  });

  document.querySelectorAll('#ccCategoryPills .pm-category-pill[data-category]').forEach(pill => {
    const matches = pill.getAttribute('data-category') === category;
    pill.classList.toggle('active', matches);
    pill.setAttribute('aria-pressed', matches ? 'true' : 'false');
  });

  syncUnifiedCollateralIdentifier();
  updateCollateralLedgerSummary();
}

function syncUnifiedCollateralIdentifier() {
  const category = getActiveCollateralCategory();
  const unifiedEl = document.getElementById('ccCollateralIdentifier');
  if (!unifiedEl) return;

  let identifier = '';
  if (category === 'MOTORBIKE' || category === 'CAR') {
    const plate = (document.getElementById('ccLicensePlate')?.value || '').trim();
    const frame = (document.getElementById('ccFrameNumber')?.value || '').trim();
    identifier = frame || plate;
  } else if (category === 'PHONE' || category === 'LAPTOP' || category === 'WATCH') {
    identifier = (document.getElementById('ccSerialOrImei')?.value || '').trim();
  } else if (category === 'GOLD_JEWELRY') {
    const cert = (document.getElementById('ccGemCertificateCode')?.value || '').trim();
    const purity = (document.getElementById('ccGoldPurity')?.value || '').trim();
    const weight = (document.getElementById('ccGoldWeightChi')?.value || '').trim();
    identifier = cert || (purity ? `Vàng ${purity}${weight ? ' - ' + weight + ' chỉ' : ''}` : '');
  } else {
    identifier = (document.getElementById('ccOtherAssetSpec')?.value || '').trim();
  }

  unifiedEl.value = identifier;
}

function updateCollateralLedgerSummary() {
  const sumCol = document.getElementById('sumCollateralSummary');
  if (!sumCol) return;
  const cat = getActiveCollateralCategory();
  const catLabel = COLLATERAL_CATEGORY_LABELS[cat] || 'Tài sản';
  const colName = (document.getElementById('ccCollateralName')?.value || '').trim() || 'Chưa nhập tên tài sản';
  sumCol.textContent = `${catLabel} • ${colName}`;
}

function bindLiveVndInputFormatting(inputEl, wordsEl, includePrefix = true) {
  if (!inputEl) return;

  const refreshDisplay = () => {
    const amount = parseVND(inputEl.value);
    if (inputEl.type === 'text') {
      inputEl.value = amount > 0 ? formatVndDigits(amount) : (inputEl.value.trim() === '0' ? '0' : '');
    }
    if (wordsEl) {
      const words = readVndInWords(amount);
      wordsEl.textContent = includePrefix ? `Bằng chữ: ${words}` : words;
    }
  };

  inputEl.addEventListener('input', function () {
    clearFieldValidationError(inputEl);
    refreshDisplay();
    recalculateLoanAndLedger({ triggerServerLtv: true });
    markDraftUnsaved();
  });

  refreshDisplay();
}

const COLLATERAL_CATEGORY_FIELD_MAP = {
  MOTORBIKE: ['ccLicensePlate', 'ccFrameNumber', 'ccVehicleRegNumber', 'ccVehicleOdometer'],
  CAR: ['ccLicensePlate', 'ccFrameNumber', 'ccVehicleRegNumber', 'ccVehicleOdometer'],
  PHONE: ['ccSerialOrImei', 'ccTechPasscodeStatus', 'ccTechAccessories'],
  LAPTOP: ['ccSerialOrImei', 'ccTechPasscodeStatus', 'ccTechAccessories'],
  WATCH: ['ccSerialOrImei', 'ccTechPasscodeStatus', 'ccTechAccessories'],
  GOLD_JEWELRY: ['ccGoldPurity', 'ccGoldWeightChi', 'ccGemCertificateCode'],
  OTHER: ['ccOtherAssetSpec']
};

const COLLATERAL_SPEC_GROUP_MAP = {
  MOTORBIKE: 'VEHICLE',
  CAR: 'VEHICLE',
  PHONE: 'TECH',
  LAPTOP: 'TECH',
  WATCH: 'TECH',
  GOLD_JEWELRY: 'GOLD',
  OTHER: 'OTHER'
};

const COLLATERAL_CATEGORY_ICONS = {
  MOTORBIKE: 'bi-scooter',
  CAR: 'bi-car-front-fill',
  GOLD_JEWELRY: 'bi-gem',
  PHONE: 'bi-phone-fill',
  LAPTOP: 'bi-laptop-fill',
  WATCH: 'bi-smartwatch',
  REAL_ESTATE: 'bi-building-fill',
  OTHER: 'bi-box-seam-fill'
};

const COLLATERAL_SPEC_FIELD_LABELS = {
  ccLicensePlate: 'Biển kiểm soát',
  ccFrameNumber: 'Số khung / Số máy',
  ccVehicleRegNumber: 'Số đăng ký xe (Cà vẹt)',
  ccVehicleOdometer: 'Số ODO (km)',
  ccSerialOrImei: 'Số IMEI / Serial / Ref',
  ccTechPasscodeStatus: 'Tài khoản Cloud & mật khẩu',
  ccTechAccessories: 'Phụ kiện bàn giao',
  ccGoldPurity: 'Tuổi vàng / Hàm lượng',
  ccGoldWeightChi: 'Trọng lượng tịnh',
  ccGemCertificateCode: 'Mã giấy kiểm định',
  ccOtherAssetSpec: 'Mã định danh / Đặc điểm kỹ thuật'
};

const COLLATERAL_CATEGORY_PLACEHOLDERS = {
  MOTORBIKE: {
    ccCollateralName: 'Ví dụ: Honda SH 150i ABS (2023) Màu Trắng Đen',
    ccCollateralBrand: 'Honda, Yamaha...',
    ccCollateralModel: 'SH 150i ABS 2023',
    ccStorageLocation: 'Kho xe K1 - Ô số 12',
    ccConditionDescription: 'Mô tả vết trầy xước dàn áo, số chìa khóa, cà vẹt bản gốc và mã tem niêm phong...',
    ccLicensePlate: '59-P1 988.22'
  },
  CAR: {
    ccCollateralName: 'Ví dụ: Mazda CX-5 2.0 Luxury (2022) Màu Đỏ',
    ccCollateralBrand: 'Toyota, Mazda, VinFast...',
    ccCollateralModel: 'CX-5 2.0 Luxury',
    ccStorageLocation: 'Kho ô tô B1 - Mái che A03',
    ccConditionDescription: 'Mô tả ngoại thất, nội thất, đăng kiểm, bảo hiểm, số chìa khóa và tem niêm phong...',
    ccLicensePlate: '51K-888.99'
  },
  GOLD_JEWELRY: {
    ccCollateralName: 'Ví dụ: Vàng miếng SJC 999.9 (05 lượng) / Nhẫn trơn 24K',
    ccCollateralBrand: 'SJC, PNJ, DOJI...',
    ccCollateralModel: 'Vàng miếng 999.9 / Nhẫn trơn',
    ccStorageLocation: 'Két sắt V1 - Ngăn kim loại quý 04',
    ccConditionDescription: 'Mô tả tình trạng vỉ ép SJC, dấu hiệu móp méo, kết quả thử phổ và mã túi niêm phong...'
  },
  PHONE: {
    ccCollateralName: 'Ví dụ: iPhone 16 Pro Max 256GB Titan Tự Nhiên',
    ccCollateralBrand: 'Apple, Samsung...',
    ccCollateralModel: 'iPhone 16 Pro Max 256GB',
    ccStorageLocation: 'Tủ chống ẩm T2 - Hộc thiết bị số 09',
    ccConditionDescription: 'Mô tả ngoại hình viền/màn hình, tình trạng pin, thoát iCloud và mã phong bì niêm phong...'
  },
  LAPTOP: {
    ccCollateralName: 'Ví dụ: MacBook Pro 14 M3 Pro 18GB/512GB Space Black',
    ccCollateralBrand: 'Apple, Dell, ThinkPad...',
    ccCollateralModel: 'MacBook Pro 14 M3 Pro',
    ccStorageLocation: 'Tủ chống ẩm T2 - Hộc thiết bị số 09',
    ccConditionDescription: 'Mô tả ngoại hình vỏ/màn hình, chu kỳ sạc pin, bộ sạc kèm theo và tem niêm phong ốc...'
  },
  WATCH: {
    ccCollateralName: 'Ví dụ: Rolex Datejust 41 126334 Mặt Xanh Fluted',
    ccCollateralBrand: 'Rolex, Omega, Apple...',
    ccCollateralModel: 'Datejust 41 Ref. 126334',
    ccStorageLocation: 'Két sắt V1 - Ngăn đồng hồ 02',
    ccConditionDescription: 'Mô tả tình trạng kính sapphire, dây đeo, hộp sổ thẻ bảo hành gốc và tem niêm phong...'
  },
  OTHER: {
    ccCollateralName: 'Nhập tên chi tiết tài sản cầm cố...',
    ccCollateralBrand: 'Tên hãng / Nhà sản xuất',
    ccCollateralModel: 'Mã dòng / Năm sản xuất',
    ccStorageLocation: 'Kho lưu trữ chuyên dụng chi nhánh',
    ccConditionDescription: 'Mô tả chi tiết hiện trạng ngoại quan, giấy tờ sở hữu và quy cách niêm phong...'
  }
};

function initCollateralSection() {
  const catSelect = document.getElementById('ccCollateralCategory');
  const pills = document.querySelectorAll('#ccCategoryPills .pm-category-pill[data-category]');
  const storageInput = document.getElementById('ccStorageLocation');
  const storageChips = document.querySelectorAll('.pm-storage-chip[data-storage]');
  const conditionTextarea = document.getElementById('ccConditionDescription');
  const conditionChips = document.querySelectorAll('.pm-condition-chip[data-condition]');

  const modalSwitchEl = document.getElementById('modalCollateralCategorySwitch');
  const btnConfirmSwitch = document.getElementById('btnConfirmCategorySwitch');
  const btnCancelSwitch = document.getElementById('btnCancelCategorySwitch');
  const chkSkipConfirm = document.getElementById('chkSkipCategorySwitchConfirm');

  const COMMON_COLLATERAL_FIELD_IDS = [
    'ccCollateralName',
    'ccCollateralBrand',
    'ccCollateralModel',
    'ccStorageLocation',
    'ccConditionDescription'
  ];
  const ALL_SPEC_FIELD_IDS = [
    'ccLicensePlate', 'ccFrameNumber', 'ccVehicleRegNumber', 'ccVehicleOdometer',
    'ccSerialOrImei', 'ccTechPasscodeStatus', 'ccTechAccessories',
    'ccGoldPurity', 'ccGoldWeightChi', 'ccGemCertificateCode',
    'ccOtherAssetSpec'
  ];

  const initialSeedValues = {};
  [...COMMON_COLLATERAL_FIELD_IDS, ...ALL_SPEC_FIELD_IDS].forEach(id => {
    const el = document.getElementById(id);
    initialSeedValues[id] = el ? String(el.value || '') : '';
  });

  let currentCollateralCategory = getActiveCollateralCategory();
  let pendingTargetCategory = null;
  let skipCategorySwitchConfirmInSession = false;
  const specGroupStash = {};
  const operatorDirtyFields = new Set();

  function getSpecGroup(cat) {
    return COLLATERAL_SPEC_GROUP_MAP[cat] || 'OTHER';
  }

  function applyCategoryPlaceholders(cat) {
    const map = COLLATERAL_CATEGORY_PLACEHOLDERS[cat] || COLLATERAL_CATEGORY_PLACEHOLDERS.OTHER;
    Object.keys(map).forEach(id => {
      const el = document.getElementById(id);
      if (el) {
        el.setAttribute('placeholder', map[id]);
      }
    });
  }

  function cleanUntouchedSeedFieldsWhenLeavingDefault(targetCat) {
    if (targetCat === 'MOTORBIKE') return;
    COMMON_COLLATERAL_FIELD_IDS.forEach(id => {
      const el = document.getElementById(id);
      if (!el || operatorDirtyFields.has(id)) return;
      const curVal = String(el.value || '').trim();
      const seedVal = String(initialSeedValues[id] || '').trim();
      if (curVal && curVal === seedVal) {
        el.value = '';
        if (id === 'ccStorageLocation') {
          storageChips.forEach(c => c.classList.remove('active'));
        }
        if (id === 'ccConditionDescription') {
          conditionChips.forEach(c => c.classList.remove('active'));
        }
      }
    });

    ['ccLicensePlate', 'ccFrameNumber', 'ccVehicleRegNumber', 'ccVehicleOdometer'].forEach(id => {
      const el = document.getElementById(id);
      if (!el || operatorDirtyFields.has(id)) return;
      const curVal = String(el.value || '').trim();
      const seedVal = String(initialSeedValues[id] || '').trim();
      if (curVal && curVal === seedVal) {
        el.value = '';
      }
    });
  }

  function getOperatorEnteredSpecEntries(cat) {
    const ids = COLLATERAL_CATEGORY_FIELD_MAP[cat] || [];
    const entries = [];

    ids.forEach(id => {
      const el = document.getElementById(id);
      if (!el) return;
      const rawVal = String(el.value || '').trim();
      if (!rawVal) return;

      const seedVal = String(initialSeedValues[id] || '').trim();
      const isSelect = el.tagName && el.tagName.toLowerCase() === 'select';
      const isOperatorEntered = isSelect
        ? (operatorDirtyFields.has(id) && rawVal !== seedVal)
        : (operatorDirtyFields.has(id) || rawVal !== seedVal);

      if (!isOperatorEntered) return;

      let displayValue = rawVal;
      if (isSelect && el.options && el.selectedIndex >= 0) {
        const optText = (el.options[el.selectedIndex]?.textContent || '').trim();
        if (optText) displayValue = optText;
      } else if (id === 'ccGoldWeightChi') {
        displayValue = `${rawVal} chỉ`;
      } else if (id === 'ccVehicleOdometer') {
        displayValue = `${rawVal} km`;
      }

      entries.push({
        id,
        label: COLLATERAL_SPEC_FIELD_LABELS[id] || id,
        displayValue
      });
    });

    return entries;
  }

  function snapshotSpecGroupFields(cat) {
    const groupKey = getSpecGroup(cat);
    const ids = COLLATERAL_CATEGORY_FIELD_MAP[cat] || [];
    const snap = {};
    let hasOperatorValue = false;

    ids.forEach(id => {
      const el = document.getElementById(id);
      if (!el) return;
      const val = String(el.value || '');
      const seedVal = String(initialSeedValues[id] || '');
      const isSelect = el.tagName && el.tagName.toLowerCase() === 'select';
      const keepVal = isSelect
        ? (operatorDirtyFields.has(id) && val !== seedVal)
        : (operatorDirtyFields.has(id) || val.trim() !== seedVal.trim());

      if (keepVal) {
        snap[id] = val;
        if (val.trim()) hasOperatorValue = true;
      } else if (!isSelect) {
        snap[id] = '';
      }
    });

    if (hasOperatorValue) {
      specGroupStash[groupKey] = snap;
    } else {
      delete specGroupStash[groupKey];
    }
  }

  function restoreSpecGroupFields(cat) {
    const groupKey = getSpecGroup(cat);
    const saved = specGroupStash[groupKey];
    if (!saved) return;
    Object.keys(saved).forEach(id => {
      const el = document.getElementById(id);
      if (el) {
        el.value = saved[id];
      }
    });
  }

  function formatPctClean(pct) {
    const rounded = Math.round(pct * 10) / 10;
    return Number.isInteger(rounded) ? `${rounded}%` : `${rounded.toFixed(1)}%`;
  }

  function closeCategorySwitchModal() {
    if (!modalSwitchEl) return;
    if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
      const inst = bootstrap.Modal.getInstance(modalSwitchEl);
      if (inst) inst.hide();
    }
    modalSwitchEl.classList.remove('show');
    modalSwitchEl.setAttribute('aria-hidden', 'true');
  }

  function openCategorySwitchModal(fromCat, toCat, enteredEntries) {
    const fromLabel = COLLATERAL_CATEGORY_LABELS[fromCat] || fromCat;
    const toLabel = COLLATERAL_CATEGORY_LABELS[toCat] || toCat;
    const fromIcon = COLLATERAL_CATEGORY_ICONS[fromCat] || 'bi-box-seam-fill';
    const toIcon = COLLATERAL_CATEGORY_ICONS[toCat] || 'bi-box-seam-fill';

    const fromChipEl = document.getElementById('catSwitchFromChip');
    const toChipEl = document.getElementById('catSwitchToChip');
    const countPillEl = document.getElementById('catSwitchStashedCount');
    const stashedListEl = document.getElementById('catSwitchStashedList');
    const ltvSummaryEl = document.getElementById('catSwitchLtvSummary');
    const capNoticeEl = document.getElementById('catSwitchLoanCapNotice');
    const lblCancelEl = document.getElementById('lblCancelCategorySwitch');
    const lblConfirmEl = document.getElementById('lblConfirmCategorySwitch');

    if (fromChipEl) {
      fromChipEl.innerHTML = `<i class="bi ${escapeHtml(fromIcon)}" aria-hidden="true"></i><span>${escapeHtml(fromLabel)}</span>`;
    }
    if (toChipEl) {
      toChipEl.innerHTML = `<i class="bi ${escapeHtml(toIcon)}" aria-hidden="true"></i><span>${escapeHtml(toLabel)}</span>`;
    }
    if (countPillEl) {
      countPillEl.textContent = `${String(enteredEntries.length).padStart(2, '0')} thông số`;
    }
    if (stashedListEl) {
      stashedListEl.innerHTML = enteredEntries.map(item => (
        `<div class="pm-cat-switch-spec-row">` +
          `<span class="pm-cat-switch-spec-label">${escapeHtml(item.label)}</span>` +
          `<span class="pm-cat-switch-spec-val font-tabular font-mono">${escapeHtml(item.displayValue)}</span>` +
        `</div>`
      )).join('');
    }

    const occGroup = document.getElementById('ccOccupationGroup')?.value || 'OFFICE_WORKER';
    const fromRatio = getLocalMatrixMaxLtvRatio(occGroup, fromCat);
    const toRatio = getLocalMatrixMaxLtvRatio(occGroup, toCat);
    const fromPct = fromRatio * 100;
    const toPct = toRatio * 100;
    const diffPct = Math.round((toPct - fromPct) * 10) / 10;

    if (ltvSummaryEl) {
      let deltaBadge = '<span class="pm-ltv-delta-pill pm-ltv-delta-same">Giữ nguyên</span>';
      if (diffPct > 0) {
        deltaBadge = `<span class="pm-ltv-delta-pill pm-ltv-delta-up"><i class="bi bi-arrow-up-right" aria-hidden="true"></i>+${formatPctClean(diffPct)}</span>`;
      } else if (diffPct < 0) {
        deltaBadge = `<span class="pm-ltv-delta-pill pm-ltv-delta-down"><i class="bi bi-arrow-down-right" aria-hidden="true"></i>${formatPctClean(diffPct)}</span>`;
      }
      ltvSummaryEl.innerHTML = `<span>${formatPctClean(fromPct)} → ${formatPctClean(toPct)}</span>${deltaBadge}`;
    }

    if (capNoticeEl) {
      const appraisedValue = parseVND(document.getElementById('ccAppraisedValue')?.value || 0);
      const principalAmount = parseVND(document.getElementById('ccPrincipalAmount')?.value || 0);
      const newMaxLoan = Math.round(appraisedValue * toRatio);
      if (appraisedValue > 0 && principalAmount > newMaxLoan) {
        capNoticeEl.classList.remove('d-none');
        capNoticeEl.innerHTML =
          `<i class="bi bi-exclamation-triangle-fill me-1" aria-hidden="true"></i>` +
          `Số tiền vay hiện tại (<strong class="font-tabular">${escapeHtml(formatVND(principalAmount))}</strong>) sẽ vượt hạn mức tối đa mới (<strong class="font-tabular">${escapeHtml(formatVND(newMaxLoan))}</strong>) — cần điều chỉnh lại ở Mục 3.`;
      } else {
        capNoticeEl.classList.add('d-none');
        capNoticeEl.innerHTML = '';
      }
    }

    if (lblCancelEl) {
      lblCancelEl.textContent = `Giữ nguyên ${fromLabel}`;
    }
    if (lblConfirmEl) {
      lblConfirmEl.textContent = `Chuyển sang ${toLabel}`;
    }

    if (modalSwitchEl) {
      if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
        const modal = bootstrap.Modal.getInstance(modalSwitchEl) || new bootstrap.Modal(modalSwitchEl);
        modal.show();
      }
      modalSwitchEl.classList.add('show');
      modalSwitchEl.removeAttribute('aria-hidden');
      if (btnConfirmSwitch && typeof btnConfirmSwitch.focus === 'function') {
        btnConfirmSwitch.focus({ preventScroll: true });
      }
    }
  }

  function applyCollateralCategorySwitch(targetCat) {
    const prevGroup = getSpecGroup(currentCollateralCategory);
    const nextGroup = getSpecGroup(targetCat);

    if (prevGroup !== nextGroup) {
      snapshotSpecGroupFields(currentCollateralCategory);
    }

    cleanUntouchedSeedFieldsWhenLeavingDefault(targetCat);
    currentCollateralCategory = targetCat;

    if (catSelect) {
      catSelect.value = targetCat;
    }

    applyCategoryPlaceholders(targetCat);

    if (prevGroup !== nextGroup) {
      restoreSpecGroupFields(targetCat);
    }

    toggleDynamicCollateralGroups(targetCat);
    syncUnifiedCollateralIdentifier();
    updateCollateralLedgerSummary();
    PM_MEDIA_MANAGER.refreshDescriptiveAltTexts();
    updateStepperCompletionStates();
    recalculateLoanAndLedger({ triggerServerLtv: true });
    markDraftUnsaved();
  }

  function requestCollateralCategorySwitch(nextCategory) {
    const targetCat = nextCategory || 'MOTORBIKE';
    if (targetCat === currentCollateralCategory) {
      toggleDynamicCollateralGroups(targetCat);
      return true;
    }

    const switchesSpecGroup = getSpecGroup(currentCollateralCategory) !== getSpecGroup(targetCat);
    const enteredEntries = switchesSpecGroup
      ? getOperatorEnteredSpecEntries(currentCollateralCategory)
      : [];

    if (switchesSpecGroup && enteredEntries.length > 0 && !skipCategorySwitchConfirmInSession && modalSwitchEl) {
      pendingTargetCategory = targetCat;
      if (catSelect) {
        catSelect.value = currentCollateralCategory;
      }
      toggleDynamicCollateralGroups(currentCollateralCategory);
      openCategorySwitchModal(currentCollateralCategory, targetCat, enteredEntries);
      return false;
    }

    applyCollateralCategorySwitch(targetCat);
    return true;
  }

  if (btnConfirmSwitch) {
    btnConfirmSwitch.addEventListener('click', function () {
      if (chkSkipConfirm && chkSkipConfirm.checked) {
        skipCategorySwitchConfirmInSession = true;
      }
      const confirmedTarget = pendingTargetCategory;
      pendingTargetCategory = null;
      closeCategorySwitchModal();
      if (confirmedTarget && confirmedTarget !== currentCollateralCategory) {
        applyCollateralCategorySwitch(confirmedTarget);
      }
    });
  }

  if (btnCancelSwitch) {
    btnCancelSwitch.addEventListener('click', function () {
      pendingTargetCategory = null;
      if (catSelect) {
        catSelect.value = currentCollateralCategory;
      }
      toggleDynamicCollateralGroups(currentCollateralCategory);
      closeCategorySwitchModal();
    });
  }

  if (modalSwitchEl) {
    modalSwitchEl.addEventListener('hidden.bs.modal', function () {
      pendingTargetCategory = null;
      if (catSelect) {
        catSelect.value = currentCollateralCategory;
      }
      toggleDynamicCollateralGroups(currentCollateralCategory);
    });
  }

  pills.forEach(pill => {
    pill.addEventListener('click', function () {
      const targetCat = pill.getAttribute('data-category') || 'MOTORBIKE';
      requestCollateralCategorySwitch(targetCat);
    });
  });

  if (catSelect) {
    catSelect.addEventListener('change', function () {
      requestCollateralCategorySwitch(catSelect.value || 'MOTORBIKE');
    });
  }

  [...COMMON_COLLATERAL_FIELD_IDS, ...ALL_SPEC_FIELD_IDS].forEach(id => {
    const el = document.getElementById(id);
    if (!el) return;
    const evtName = (el.tagName && el.tagName.toLowerCase() === 'select') ? 'change' : 'input';
    el.addEventListener(evtName, function () {
      operatorDirtyFields.add(id);
      clearFieldValidationError(el);
      syncUnifiedCollateralIdentifier();
      updateCollateralLedgerSummary();
      if (id === 'ccCollateralName') {
        PM_MEDIA_MANAGER.refreshDescriptiveAltTexts();
      }
      updateStepperCompletionStates();
      markDraftUnsaved();
    });
  });

  storageChips.forEach(chip => {
    chip.addEventListener('click', function () {
      const val = chip.getAttribute('data-storage') || '';
      if (storageInput) {
        storageInput.value = val;
        operatorDirtyFields.add('ccStorageLocation');
      }
      storageChips.forEach(c => c.classList.toggle('active', c === chip));
      markDraftUnsaved();
    });
  });

  if (storageInput) {
    storageInput.addEventListener('input', function () {
      operatorDirtyFields.add('ccStorageLocation');
      const cur = storageInput.value.trim();
      storageChips.forEach(c => c.classList.toggle('active', c.getAttribute('data-storage') === cur));
      markDraftUnsaved();
    });
  }

  conditionChips.forEach(chip => {
    chip.addEventListener('click', function () {
      if (!conditionTextarea) return;
      const phrase = (chip.getAttribute('data-condition') || '').trim();
      if (!phrase) return;

      const currentText = conditionTextarea.value.trim();
      const lowerCurrent = currentText.toLowerCase();
      const lowerPhrase = phrase.toLowerCase();

      if (lowerCurrent.includes(lowerPhrase)) {
        const parts = currentText
          .split(',')
          .map(s => s.trim())
          .filter(s => s && s.toLowerCase() !== lowerPhrase);
        conditionTextarea.value = parts.join(', ');
        chip.classList.remove('active');
      } else {
        const cleanedBase = currentText.replace(/[.,;\s]+$/, '');
        conditionTextarea.value = cleanedBase ? `${cleanedBase}, ${phrase}` : phrase;
        chip.classList.add('active');
      }
      operatorDirtyFields.add('ccConditionDescription');
      markDraftUnsaved();
    });
  });

  window.pmMarkCollateralDraftRestored = function (restoredFields) {
    currentCollateralCategory = getActiveCollateralCategory();
    applyCategoryPlaceholders(currentCollateralCategory);
    if (restoredFields && typeof restoredFields === 'object') {
      [...COMMON_COLLATERAL_FIELD_IDS, ...ALL_SPEC_FIELD_IDS].forEach(id => {
        if (id in restoredFields && String(restoredFields[id] || '').trim() !== String(initialSeedValues[id] || '').trim()) {
          operatorDirtyFields.add(id);
        }
      });
    }
  };

  window.pmResetCollateralSwitchState = function () {
    pendingTargetCategory = null;
    skipCategorySwitchConfirmInSession = false;
    if (chkSkipConfirm) chkSkipConfirm.checked = false;
    operatorDirtyFields.clear();
    Object.keys(specGroupStash).forEach(k => delete specGroupStash[k]);
    [...COMMON_COLLATERAL_FIELD_IDS, ...ALL_SPEC_FIELD_IDS].forEach(id => {
      const el = document.getElementById(id);
      if (el && id in initialSeedValues) {
        el.value = initialSeedValues[id];
      }
    });
    currentCollateralCategory = 'MOTORBIKE';
    applyCategoryPlaceholders('MOTORBIKE');
    syncUnifiedCollateralIdentifier();
    updateCollateralLedgerSummary();
  };

  bindLiveVndInputFormatting(
    document.getElementById('ccAppraisedValue'),
    document.getElementById('ccAppraisedValueWords'),
    true
  );
  bindLiveVndInputFormatting(
    document.getElementById('ccPrincipalAmount'),
    document.getElementById('ccPrincipalAmountWords'),
    true
  );
  bindLiveVndInputFormatting(
    document.getElementById('ccMonthlyIncome'),
    null,
    false
  );

  toggleDynamicCollateralGroups(getActiveCollateralCategory());
}

/**
 * ============================================================================
 * 4. REAL-TIME DUAL-LAYER LTV CALCULATOR, CASH GATE & INTEREST SCHEDULE
 * ============================================================================
 */
function getLocalMatrixMaxLtvRatio(occupationGroup, collateralCategory) {
  const grp = occupationGroup || 'OFFICE_WORKER';
  const cat = collateralCategory || 'MOTORBIKE';
  return (LTV_MATRIX[grp] && LTV_MATRIX[grp][cat]) || 0.70;
}

function scheduleServerLtvEvaluation(occupationGroup, category, appraisedValue, principalAmount) {
  if (!isBackendServerMode() || appraisedValue <= 0) return;
  if (PM_CONTRACT_CREATE_STATE.ltvDebounceTimer) {
    clearTimeout(PM_CONTRACT_CREATE_STATE.ltvDebounceTimer);
  }

  PM_CONTRACT_CREATE_STATE.ltvDebounceTimer = setTimeout(async () => {
    try {
      const docType = document.getElementById('ccVerificationDocType')?.value || 'PAYSLIP_BANK_STATEMENT';
      const docUrl = (document.getElementById('ccDocumentUrl')?.value || '').trim();
      const res = await apiFetch('/api/v1/collaterals/evaluate-ltv', {
        method: 'POST',
        body: JSON.stringify({
          occupationGroup,
          category,
          appraisedValue,
          requestedLoanAmount: principalAmount > 0 ? principalAmount : null,
          docType,
          docUrls: docUrl ? [docUrl] : []
        })
      });
      if (res && res.data && res.data.maxLtvPercent != null) {
        let serverVal = Number(res.data.maxLtvPercent);
        if (serverVal > 1) {
          serverVal = serverVal / 100;
        }
        if (serverVal > 0 && serverVal <= 1) {
          PM_CONTRACT_CREATE_STATE.serverEvaluatedMaxLtvRatio = serverVal;
          recalculateLoanAndLedger({ triggerServerLtv: false });
        }
      }
    } catch (e) {
      // Keep deterministic local LTV matrix when offline or unauthenticated
    }
  }, 260);
}

function computeCycleAmounts(principal, interestType, interestRate, storageInput, daysInPeriod, standardCycleDays) {
  if (principal <= 0 || daysInPeriod <= 0) {
    return { interestAmt: 0, storageAmt: 0, totalAmt: 0 };
  }

  const isMonthlyPct = interestType === 'MONTHLY_PERCENT' || interestType === 'PERCENT_MONTHLY';
  const isWeeklyPct = interestType === 'PERCENT_WEEKLY';
  let interestAmt = 0;
  if (isMonthlyPct) {
    interestAmt = Math.round(principal * (interestRate / 100) * (daysInPeriod / 30));
  } else if (isWeeklyPct) {
    interestAmt = Math.round(principal * (interestRate / 100) * (daysInPeriod / 7));
  } else {
    interestAmt = Math.round((principal / 1000000) * interestRate * daysInPeriod);
  }

  let storageAmt = 0;
  if (storageInput > 0) {
    if (storageInput >= 1000) {
      storageAmt = Math.round(storageInput * (daysInPeriod / Math.max(1, standardCycleDays)));
    } else if (isMonthlyPct) {
      storageAmt = Math.round(principal * (storageInput / 100) * (daysInPeriod / 30));
    } else if (isWeeklyPct) {
      storageAmt = Math.round(principal * (storageInput / 100) * (daysInPeriod / 7));
    } else {
      storageAmt = Math.round((principal / 1000000) * storageInput * daysInPeriod);
    }
  }

  return {
    interestAmt,
    storageAmt,
    totalAmt: interestAmt + storageAmt
  };
}

function buildPaymentScheduleCycles(startDateIso, loanTermDays, paymentCycleDays, principal, interestType, interestRate, storageInput) {
  const start = parseIsoDateLocal(startDateIso);
  const safeTerm = Math.max(1, Number(loanTermDays) || 30);
  const safeCycle = Math.max(1, Number(paymentCycleDays) || 10);

  const cycles = [];
  let elapsed = 0;
  let periodIndex = 1;

  while (elapsed < safeTerm) {
    const daysInThisPeriod = Math.min(safeCycle, safeTerm - elapsed);
    const fromDate = new Date(start.getTime() + elapsed * 86400000);
    const dueDate = new Date(start.getTime() + (elapsed + daysInThisPeriod) * 86400000);
    const isFinal = (elapsed + daysInThisPeriod) >= safeTerm;

    const { interestAmt, storageAmt, totalAmt } = computeCycleAmounts(
      principal,
      interestType,
      interestRate,
      storageInput,
      daysInThisPeriod,
      safeCycle
    );

    cycles.push({
      index: periodIndex,
      label: isFinal ? `Kỳ ${periodIndex} (Đáo hạn)` : `Kỳ ${periodIndex}`,
      fromDateStr: formatDateVN(fromDate),
      dueDateStr: formatDateVN(dueDate),
      days: daysInThisPeriod,
      interestAmt,
      storageAmt,
      totalAmt,
      isFinal
    });

    elapsed += daysInThisPeriod;
    periodIndex += 1;
  }

  return cycles;
}

function renderSchedulePreviewTable(cycles) {
  const tbody = document.getElementById('ccPaymentScheduleBody');
  if (!tbody) return;

  if (!cycles || cycles.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" class="text-center text-muted py-3">Nhập số tiền vay và kỳ hạn để xem lịch thu lãi dự kiến.</td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = cycles.map(c => `
    <tr>
      <td class="fw-semibold font-tabular">${escapeHtml(c.label)}</td>
      <td class="font-tabular pm-date">${escapeHtml(c.fromDateStr)} → ${escapeHtml(c.dueDateStr)}</td>
      <td class="font-tabular pm-date fw-semibold">${escapeHtml(c.dueDateStr)}</td>
      <td class="text-end font-tabular">${c.days} ngày</td>
      <td class="text-end font-tabular pm-currency">${formatVND(c.interestAmt)}</td>
      <td class="text-end font-tabular pm-currency">${formatVND(c.storageAmt)}</td>
      <td class="text-end font-tabular pm-currency fw-bold text-primary">${formatVND(c.totalAmt)}</td>
    </tr>
  `).join('');
}

function recalculateLoanAndLedger(options = {}) {
  const occGroup = document.getElementById('ccOccupationGroup')?.value || 'OFFICE_WORKER';
  const category = getActiveCollateralCategory();
  const appraisedValue = parseVND(document.getElementById('ccAppraisedValue')?.value || 0);
  const principalAmount = parseVND(document.getElementById('ccPrincipalAmount')?.value || 0);

  const startDateIso = document.getElementById('ccStartDate')?.value || toIsoDateString(new Date());
  const loanTermDays = parseInt(document.getElementById('ccLoanTermDays')?.value || '30', 10) || 30;
  const paymentCycleDays = parseInt(document.getElementById('ccPaymentCycle')?.value || '10', 10) || 10;
  const interestType = document.getElementById('ccInterestType')?.value || 'DAILY_PER_MILLION';
  const interestRate = Math.max(0, parseFloat(document.getElementById('ccInterestRate')?.value || '0') || 0);
  const storageInput = Math.max(0, parseFloat(document.getElementById('ccStorageFeeRate')?.value || '0') || 0);

  if (options.triggerServerLtv) {
    PM_CONTRACT_CREATE_STATE.serverEvaluatedMaxLtvRatio = null;
    scheduleServerLtvEvaluation(occGroup, category, appraisedValue, principalAmount);
  }

  const maxLtvRatio = PM_CONTRACT_CREATE_STATE.serverEvaluatedMaxLtvRatio || getLocalMatrixMaxLtvRatio(occGroup, category);
  const maxLtvPct = maxLtvRatio * 100;
  const maxLoanAllowed = Math.round(appraisedValue * maxLtvRatio);
  const appliedLtvPct = appraisedValue > 0 ? (principalAmount / appraisedValue) * 100 : 0;

  // Equivalent interest rate hint
  const eqHintEl = document.getElementById('ccInterestEquivalentHint');
  if (eqHintEl) {
    const isMonthlyPct = interestType === 'MONTHLY_PERCENT' || interestType === 'PERCENT_MONTHLY';
    const isWeeklyPct = interestType === 'PERCENT_WEEKLY';
    if (isMonthlyPct) {
      const equivDailyPerMil = Math.round(((interestRate / 100) / 30) * 1000000);
      const equivWeeklyPct = (interestRate / 30) * 7;
      eqHintEl.textContent = `${interestRate.toFixed(2).replace('.', ',')}%/tháng (~${formatVndDigits(equivDailyPerMil)} ₫/triệu/ngày • ~${equivWeeklyPct.toFixed(2).replace('.', ',')}%/tuần)`;
    } else if (isWeeklyPct) {
      const equivDailyPerMil = Math.round(((interestRate / 100) / 7) * 1000000);
      const equivMonthlyPct = (interestRate / 7) * 30;
      eqHintEl.textContent = `${interestRate.toFixed(2).replace('.', ',')}%/tuần (~${formatVndDigits(equivDailyPerMil)} ₫/triệu/ngày • ~${equivMonthlyPct.toFixed(2).replace('.', ',')}%/tháng)`;
    } else {
      const equivWeeklyPct = ((interestRate * 7) / 1000000) * 100;
      const equivMonthly = ((interestRate * 30) / 1000000) * 100;
      eqHintEl.textContent = `${formatVndDigits(interestRate)} ₫/triệu/ngày (~${equivWeeklyPct.toFixed(2).replace('.', ',')}%/tuần • ~${equivMonthly.toFixed(2).replace('.', ',')}%/tháng)`;
    }
  }

  // Words display sync
  const appraisedWordsEl = document.getElementById('ccAppraisedValueWords');
  if (appraisedWordsEl) {
    appraisedWordsEl.textContent = `Bằng chữ: ${readVndInWords(appraisedValue)}`;
  }
  const principalWordsEl = document.getElementById('ccPrincipalAmountWords');
  if (principalWordsEl) {
    principalWordsEl.textContent = `Bằng chữ: ${readVndInWords(principalAmount)}`;
  }
  const netWordsEl = document.getElementById('calcNetDisbursementWords');
  if (netWordsEl) {
    netWordsEl.textContent = readVndInWords(principalAmount);
  }

  // Build projected payment schedule
  const cycles = buildPaymentScheduleCycles(
    startDateIso,
    loanTermDays,
    paymentCycleDays,
    principalAmount,
    interestType,
    interestRate,
    storageInput
  );
  PM_CONTRACT_CREATE_STATE.activeScheduleCycles = cycles;
  renderSchedulePreviewTable(cycles);

  const firstCycle = cycles[0] || { interestAmt: 0, storageAmt: 0, dueDateStr: '—' };
  const maturityDateObj = new Date(parseIsoDateLocal(startDateIso).getTime() + loanTermDays * 86400000);
  const maturityDateStr = formatDateVN(maturityDateObj);

  // Sticky Ledger LTV Risk Meter
  const sumCurLtv = document.getElementById('sumLtvCurrentPct');
  const sumMaxLtv = document.getElementById('sumLtvMaxPct');
  const sumBar = document.getElementById('sumLtvProgressBar');
  const sumCapMarker = document.getElementById('sumLtvCapMarker');
  const sumRiskLabel = document.getElementById('sumLtvRiskLabel');

  if (sumCurLtv) sumCurLtv.textContent = `${appliedLtvPct.toFixed(1)}%`;
  if (sumMaxLtv) sumMaxLtv.textContent = `${maxLtvPct.toFixed(1)}%`;
  if (sumCapMarker) {
    sumCapMarker.style.left = `${Math.min(100, Math.max(0, maxLtvPct))}%`;
  }

  const exceedsPolicyCap = appraisedValue > 0 && principalAmount > maxLoanAllowed;
  const isNearCap = !exceedsPolicyCap && appliedLtvPct > 75;

  if (sumBar) {
    sumBar.style.width = `${Math.min(100, Math.max(0, appliedLtvPct)).toFixed(1)}%`;
    sumBar.classList.remove('is-safe', 'is-warning', 'is-danger');
    if (exceedsPolicyCap || appliedLtvPct > 80) {
      sumBar.classList.add('is-danger');
    } else if (isNearCap) {
      sumBar.classList.add('is-warning');
    } else {
      sumBar.classList.add('is-safe');
    }
  }

  if (sumRiskLabel) {
    if (exceedsPolicyCap) {
      sumRiskLabel.className = 'pm-badge pm-badge-overdue';
      sumRiskLabel.innerHTML = '<span class="pm-badge-dot" aria-hidden="true"></span><span>Vượt trần LTV</span>';
    } else if (appliedLtvPct > 75) {
      sumRiskLabel.className = 'pm-badge pm-badge-liquidation';
      sumRiskLabel.innerHTML = '<span class="pm-badge-dot" aria-hidden="true"></span><span>Sát trần LTV</span>';
    } else {
      sumRiskLabel.className = 'pm-badge pm-badge-active';
      sumRiskLabel.innerHTML = '<span class="pm-badge-dot" aria-hidden="true"></span><span>An toàn</span>';
    }
  }

  // Live Calculation Box (#ccLiveCalculationBox)
  const elMaxLtv = document.getElementById('calcMaxLtvAllowed');
  const elMaxLoan = document.getElementById('calcMaxLoanAllowed');
  const elAppliedLtv = document.getElementById('calcAppliedLtv');
  const elCycleInt = document.getElementById('calcCycleInterest');
  const elStorage = document.getElementById('calcStorageFee');
  const elFirstDue = document.getElementById('calcFirstDueDate');
  const elMaturity = document.getElementById('calcMaturityDate');
  const elNetDisb = document.getElementById('calcNetDisbursement');

  if (elMaxLtv) elMaxLtv.textContent = `${maxLtvPct.toFixed(1)}%`;
  if (elMaxLoan) elMaxLoan.textContent = formatVND(maxLoanAllowed);
  if (elAppliedLtv) {
    elAppliedLtv.textContent = `${appliedLtvPct.toFixed(1)}%`;
    elAppliedLtv.className = exceedsPolicyCap
      ? 'font-tabular fw-bold text-danger'
      : (appliedLtvPct > 75 ? 'font-tabular fw-bold text-warning' : 'font-tabular fw-bold text-success');
  }
  if (elCycleInt) elCycleInt.textContent = formatVND(firstCycle.interestAmt);
  if (elStorage) elStorage.textContent = formatVND(firstCycle.storageAmt);
  if (elFirstDue) elFirstDue.textContent = firstCycle.dueDateStr;
  if (elMaturity) elMaturity.textContent = maturityDateStr;
  if (elNetDisb) elNetDisb.textContent = formatVND(principalAmount);

  // Sync LTV Preset Button active state & dynamic MAX LTV percentage label
  const maxPresetBtn = document.getElementById('btnPresetMaxLtv');
  if (maxPresetBtn) {
    const formattedMaxPct = Number.isInteger(maxLtvPct)
      ? String(maxLtvPct)
      : maxLtvPct.toFixed(1).replace(/\.0$/, '');
    maxPresetBtn.textContent = `Tối đa (${formattedMaxPct}%)`;
  }

  document.querySelectorAll('.pm-ltv-preset-btn[data-ltv-ratio]').forEach(btn => {
    const ratioAttr = btn.getAttribute('data-ltv-ratio');
    const targetRatio = ratioAttr === 'MAX' ? maxLtvRatio : (Number(ratioAttr) / 100);
    const targetAmt = Math.round(appraisedValue * targetRatio);
    btn.classList.toggle('active', appraisedValue > 0 && Math.abs(principalAmount - targetAmt) <= 1000);
  });

  // Gate 1: LTV Ceiling Warning (#ccLtvWarningAlert)
  const ltvAlert = document.getElementById('ccLtvWarningAlert');
  if (ltvAlert) {
    if (exceedsPolicyCap) {
      ltvAlert.classList.remove('d-none');
      ltvAlert.innerHTML = `
        <i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>
        <div>
          <strong>Cảnh báo vượt trần LTV (${maxLtvPct.toFixed(1)}%):</strong> Số tiền vay (${formatVND(principalAmount)}) vượt hạn mức cho phép ${formatVND(maxLoanAllowed)} đối với nhóm nghề nghiệp và tài sản này.
        </div>
      `;
    } else if (appliedLtvPct > 80) {
      ltvAlert.classList.remove('d-none');
      ltvAlert.innerHTML = `
        <i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>
        <div>
          <strong>Lưu ý thẩm định LTV cao (${appliedLtvPct.toFixed(1)}%):</strong> Hồ sơ nằm trên ngưỡng 80% giá trị định giá, yêu cầu Quản lý chi nhánh kiểm tra kỹ chứng từ trước khi giải ngân.
        </div>
      `;
    } else {
      ltvAlert.classList.add('d-none');
    }
  }

  // Gate 2: Branch Cash Balance Gate (#ccCashBalanceAlert & #sumCashSafetyBadge)
  const branchCash = PM_CONTRACT_CREATE_STATE.branchCashBalance || 0;
  const exceedsBranchCash = branchCash > 0 && principalAmount > branchCash;
  const cashAlert = document.getElementById('ccCashBalanceAlert');
  const cashSafetyBadge = document.getElementById('sumCashSafetyBadge');

  if (cashAlert) {
    if (exceedsBranchCash) {
      cashAlert.classList.remove('d-none');
      cashAlert.innerHTML = `
        <i class="bi bi-wallet-fill" aria-hidden="true"></i>
        <div>
          <strong>Cảnh báo tồn quỹ tiền mặt:</strong> Số tiền giải ngân (${formatVND(principalAmount)}) vượt quá quỹ khả dụng tại quầy (${formatVND(branchCash)}). Cần tiếp quỹ trước khi Quản lý duyệt chi.
        </div>
      `;
    } else {
      cashAlert.classList.add('d-none');
    }
  }

  if (cashSafetyBadge) {
    if (exceedsBranchCash) {
      cashSafetyBadge.className = 'pm-badge pm-badge-overdue';
      cashSafetyBadge.innerHTML = '<span class="pm-badge-dot" aria-hidden="true"></span><span>Vượt tồn quỹ quầy</span>';
    } else if (branchCash > 0 && principalAmount > branchCash * 0.8) {
      cashSafetyBadge.className = 'pm-badge pm-badge-liquidation';
      cashSafetyBadge.innerHTML = '<span class="pm-badge-dot" aria-hidden="true"></span><span>Sát tồn quỹ quầy</span>';
    } else {
      cashSafetyBadge.className = 'pm-badge pm-badge-active';
      cashSafetyBadge.innerHTML = '<span class="pm-badge-dot" aria-hidden="true"></span><span>Đủ quỹ giải ngân</span>';
    }
  }

  if (PM_CONTRACT_CREATE_STATE.errorSummaryActive) {
    refreshLiveFormErrorSummary();
  }
}

function initLoanTermsSection() {
  const startDateInput = document.getElementById('ccStartDate');
  if (startDateInput) {
    const bodyToday = typeof document !== 'undefined' && document.body
      ? (document.body.getAttribute('data-today-iso') || '').trim()
      : '';
    if (bodyToday && /^\d{4}-\d{2}-\d{2}$/.test(bodyToday)) {
      startDateInput.value = bodyToday;
    } else if (!startDateInput.value) {
      startDateInput.value = toIsoDateString(new Date());
    }
  }

  document.querySelectorAll('.pm-ltv-preset-btn[data-ltv-ratio]').forEach(btn => {
    btn.addEventListener('click', function () {
      const appraised = parseVND(document.getElementById('ccAppraisedValue')?.value || 0);
      if (appraised <= 0) {
        showToast('Vui lòng nhập Giá trị thẩm định tài sản trước khi chọn nhanh tỷ lệ LTV.', 'warning');
        document.getElementById('ccAppraisedValue')?.focus();
        return;
      }

      const occGroup = document.getElementById('ccOccupationGroup')?.value || 'OFFICE_WORKER';
      const category = getActiveCollateralCategory();
      const maxRatio = PM_CONTRACT_CREATE_STATE.serverEvaluatedMaxLtvRatio || getLocalMatrixMaxLtvRatio(occGroup, category);
      const ratioAttr = btn.getAttribute('data-ltv-ratio');
      const targetRatio = ratioAttr === 'MAX' ? maxRatio : (Number(ratioAttr) / 100);
      const computedPrincipal = Math.round(appraised * targetRatio);

      const principalEl = document.getElementById('ccPrincipalAmount');
      if (principalEl) {
        principalEl.value = formatVndDigits(computedPrincipal);
        clearFieldValidationError(principalEl);
      }
      recalculateLoanAndLedger({ triggerServerLtv: true });
      markDraftUnsaved();
    });
  });

  const interestTypeSelect = document.getElementById('ccInterestType');
  const interestRateInput = document.getElementById('ccInterestRate');
  if (interestTypeSelect && interestRateInput) {
    PM_CONTRACT_CREATE_STATE.lastInterestType = interestTypeSelect.value || 'DAILY_PER_MILLION';
    interestTypeSelect.addEventListener('change', function () {
      const prevType = PM_CONTRACT_CREATE_STATE.lastInterestType || 'DAILY_PER_MILLION';
      const nextType = interestTypeSelect.value || 'DAILY_PER_MILLION';
      const curRate = parseFloat(interestRateInput.value || '0') || 0;

      if (curRate > 0 && prevType !== nextType) {
        let dailyPerMil = curRate;
        const wasMonthly = prevType === 'MONTHLY_PERCENT' || prevType === 'PERCENT_MONTHLY';
        if (wasMonthly && curRate < 100) {
          dailyPerMil = ((curRate / 100) / 30) * 1000000;
        } else if (prevType === 'PERCENT_WEEKLY' && curRate < 100) {
          dailyPerMil = ((curRate / 100) / 7) * 1000000;
        }

        if (nextType === 'MONTHLY_PERCENT' || nextType === 'PERCENT_MONTHLY') {
          const convertedMonthly = Number((((dailyPerMil * 30) / 1000000) * 100).toFixed(2));
          interestRateInput.value = String(convertedMonthly > 0 ? convertedMonthly : 3);
        } else if (nextType === 'PERCENT_WEEKLY') {
          const convertedWeekly = Number((((dailyPerMil * 7) / 1000000) * 100).toFixed(2));
          interestRateInput.value = String(convertedWeekly > 0 ? convertedWeekly : 1.4);
        } else {
          const convertedDaily = Math.round(dailyPerMil);
          interestRateInput.value = String(convertedDaily > 0 ? convertedDaily : 2000);
        }
      }

      PM_CONTRACT_CREATE_STATE.lastInterestType = nextType;
      recalculateLoanAndLedger({ triggerServerLtv: false });
      markDraftUnsaved();
    });
  }

  ['ccStartDate', 'ccLoanTermDays', 'ccPaymentCycle', 'ccInterestRate', 'ccStorageFeeRate'].forEach(id => {
    const el = document.getElementById(id);
    if (!el) return;
    const evtName = el.tagName === 'SELECT' ? 'change' : 'input';
    el.addEventListener(evtName, function () {
      clearFieldValidationError(el);
      recalculateLoanAndLedger({ triggerServerLtv: false });
      markDraftUnsaved();
    });
  });

  const disbMethodSelect = document.getElementById('ccDisbursementMethod');
  const bankGroup = document.getElementById('ccBankAccountGroup');
  if (disbMethodSelect && bankGroup) {
    disbMethodSelect.addEventListener('change', function () {
      const isBank = disbMethodSelect.value === 'BANK_TRANSFER';
      bankGroup.classList.toggle('d-none', !isBank);
      if (isBank) {
        syncBankAccountHolderFromCustomer(false);
        evaluateBankHolderMismatchWarning();
        document.getElementById('ccBankSearchInput')?.focus();
      } else {
        evaluateBankHolderMismatchWarning();
      }
      markDraftUnsaved();
    });
  }

  initSearchableBankCombobox();

  const bankCodeSelect = document.getElementById('ccBankCode');
  if (bankCodeSelect) {
    bankCodeSelect.addEventListener('change', function () {
      syncBankComboboxFromSelect();
      clearFieldValidationError(bankCodeSelect);
      markDraftUnsaved();
    });
  }

  const bankAccInput = document.getElementById('ccBankAccountNumber');
  if (bankAccInput) {
    bankAccInput.addEventListener('input', function () {
      const cleaned = bankAccInput.value.replace(/\s+/g, '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 25);
      if (bankAccInput.value !== cleaned) {
        bankAccInput.value = cleaned;
      }
      clearFieldValidationError(bankAccInput);
      markDraftUnsaved();
    });
  }

  const bankHolderInput = document.getElementById('ccBankAccountHolder');
  if (bankHolderInput) {
    bankHolderInput.addEventListener('input', function () {
      PM_CONTRACT_CREATE_STATE.bankHolderEditedManually = Boolean(bankHolderInput.value.trim());
      evaluateBankHolderMismatchWarning();
      markDraftUnsaved();
    });
    bankHolderInput.addEventListener('blur', function () {
      const normalized = toBankAccountHolderAscii(bankHolderInput.value || '');
      if (normalized) {
        bankHolderInput.value = normalized;
      }
      evaluateBankHolderMismatchWarning();
    });
  }

  const customerNameInput = document.getElementById('ccCustomerName');
  if (customerNameInput) {
    customerNameInput.addEventListener('input', function () {
      syncBankAccountHolderFromCustomer(false);
    });
  }

  const branchSelect = document.getElementById('ccBranchId');
  if (branchSelect) {
    branchSelect.addEventListener('change', function () {
      syncBranchTelemetryFromSelector();
      recalculateLoanAndLedger({ triggerServerLtv: false });
      markDraftUnsaved();
    });
  }
}

/**
 * ============================================================================
 * 5. STEPPER SCROLL SPY, LOCALSTORAGE DRAFTS, PRINT PREVIEW & ORIGINATION SUBMIT
 * ============================================================================
 */
function updateStepperCompletionStates() {
  const stepButtons = document.querySelectorAll('#ccStepperNav .pm-step-item[data-step-target]');
  if (!stepButtons.length) return;

  const lookupState = document.getElementById('ccCustomerLookupState')?.value || '';
  const custDone = lookupState === 'FOUND' &&
    Boolean(document.getElementById('ccCustomerName')?.value.trim()) &&
    Boolean(document.getElementById('ccProvinceCode')?.value) &&
    Boolean(document.getElementById('ccDistrictCode')?.value) &&
    Boolean(document.getElementById('ccWardCode')?.value);

  const colDone = Boolean(document.getElementById('ccCollateralName')?.value.trim()) &&
    parseVND(document.getElementById('ccAppraisedValue')?.value || 0) >= 100000;

  const loanDone = parseVND(document.getElementById('ccPrincipalAmount')?.value || 0) >= 100000 &&
    (parseFloat(document.getElementById('ccInterestRate')?.value || '0') || 0) >= 0;

  const compDone = Boolean(document.getElementById('chkVerifyOriginalCccd')?.checked) &&
    Boolean(document.getElementById('chkVerifyAssetSeal')?.checked) &&
    Boolean(document.getElementById('chkCustomerAgreedTerms')?.checked);

  const completionMap = {
    '#sectionCustomer': custDone,
    '#sectionCollateral': colDone,
    '#sectionLoanTerms': loanDone,
    '#sectionCompliance': compDone
  };

  stepButtons.forEach(btn => {
    const targetSel = btn.getAttribute('data-step-target');
    const isCompleted = Boolean(completionMap[targetSel]);
    if (!btn.classList.contains('is-active') && !btn.classList.contains('has-step-error')) {
      btn.classList.toggle('is-completed', isCompleted);
    } else {
      btn.classList.remove('is-completed');
    }
  });

  syncCustomerAccordionSubsteps();
}

function syncStepErrorBadges(errors) {
  const counts = {
    sectionCustomer: 0,
    sectionCollateral: 0,
    sectionLoanTerms: 0,
    sectionCompliance: 0
  };
  (errors || []).forEach(err => {
    if (err && err.sectionId && Object.prototype.hasOwnProperty.call(counts, err.sectionId)) {
      counts[err.sectionId]++;
    }
  });

  const stepMeta = [
    { sec: 'sectionCustomer', btn: document.getElementById('ccStepBtnCustomer'), pill: document.getElementById('ccStepErrCustomer') },
    { sec: 'sectionCollateral', btn: document.getElementById('ccStepBtnCollateral'), pill: document.getElementById('ccStepErrCollateral') },
    { sec: 'sectionLoanTerms', btn: document.getElementById('ccStepBtnLoanTerms'), pill: document.getElementById('ccStepErrLoanTerms') },
    { sec: 'sectionCompliance', btn: document.getElementById('ccStepBtnCompliance'), pill: document.getElementById('ccStepErrCompliance') }
  ];

  stepMeta.forEach(({ sec, btn, pill }) => {
    const cnt = counts[sec] || 0;
    if (btn) {
      btn.classList.toggle('has-step-error', cnt > 0);
    }
    if (pill) {
      pill.textContent = `${cnt} lỗi`;
      pill.classList.toggle('d-none', cnt === 0);
    }
  });

  updateStepperCompletionStates();
}

/**
 * Synchronize sticky offsets (--pm-cc-topbar-h, --pm-cc-hud-h, --pm-cc-ledger-top, --pm-cc-scroll-margin),
 * compact HUD state (.is-stuck), and floating back-to-top visibility (.is-visible).
 */
function syncStickyWorkspaceOffsets() {
  const topbarEl = typeof document !== 'undefined' ? document.querySelector('.pm-topbar') : null;
  const hudEl = typeof document !== 'undefined' ? document.getElementById('ccStickyWorkspaceHud') : null;
  const backToTopBtn = typeof document !== 'undefined' ? document.getElementById('btnBackToTop') : null;

  const scrollY = (typeof window !== 'undefined' && typeof window.scrollY === 'number')
    ? window.scrollY
    : ((typeof window !== 'undefined' && typeof window.pageYOffset === 'number') ? window.pageYOffset : 0);

  if (hudEl) {
    hudEl.classList.toggle('is-stuck', scrollY > 88);
  }
  if (backToTopBtn) {
    const showBackToTop = scrollY > 260;
    backToTopBtn.classList.toggle('is-visible', showBackToTop);
    backToTopBtn.setAttribute('tabindex', showBackToTop ? '0' : '-1');
  }

  const topbarH = (topbarEl && Number(topbarEl.offsetHeight) > 0) ? Math.round(topbarEl.offsetHeight) : 64;
  const hudH = (hudEl && Number(hudEl.offsetHeight) > 0) ? Math.round(hudEl.offsetHeight) : 68;
  const ledgerTop = topbarH + hudH + 12;
  const scrollMargin = topbarH + hudH + 20;

  if (typeof document !== 'undefined' &&
      document.documentElement &&
      document.documentElement.style &&
      typeof document.documentElement.style.setProperty === 'function') {
    document.documentElement.style.setProperty('--pm-cc-topbar-h', `${topbarH}px`);
    document.documentElement.style.setProperty('--pm-cc-hud-h', `${hudH}px`);
    document.documentElement.style.setProperty('--pm-cc-ledger-top', `${ledgerTop}px`);
    document.documentElement.style.setProperty('--pm-cc-scroll-margin', `${scrollMargin}px`);
  }

  return { topbarH, hudH, ledgerTop, scrollMargin, scrollY };
}

function initBackToTopButton() {
  const backToTopBtn = document.getElementById('btnBackToTop');
  if (!backToTopBtn) return;

  backToTopBtn.addEventListener('click', function () {
    const prefersReducedMotion = Boolean(
      typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    );

    if (typeof window !== 'undefined' && typeof window.scrollTo === 'function') {
      window.scrollTo({ top: 0, behavior: prefersReducedMotion ? 'auto' : 'smooth' });
    }

    const pageTitleEl = document.getElementById('originationPageTitle');
    if (pageTitleEl) {
      if (!pageTitleEl.hasAttribute('tabindex')) {
        pageTitleEl.setAttribute('tabindex', '-1');
      }
      pageTitleEl.focus({ preventScroll: true });
    }
  });
}

function initWorkflowStepperScrollSpy() {
  const stepButtons = Array.from(document.querySelectorAll('#ccStepperNav .pm-step-item[data-step-target]'));
  if (!stepButtons.length) return;

  const sectionEntries = stepButtons
    .map(btn => {
      const targetSel = btn.getAttribute('data-step-target');
      const secEl = targetSel ? document.getElementById(targetSel.replace('#', '')) : null;
      return secEl ? { btn, secEl } : null;
    })
    .filter(Boolean);

  function setActiveStep(activeBtn) {
    stepButtons.forEach(b => {
      const active = b === activeBtn;
      b.classList.toggle('is-active', active);
      if (active) {
        b.setAttribute('aria-current', 'step');
      } else {
        b.removeAttribute('aria-current');
      }
    });
    updateStepperCompletionStates();
  }

  sectionEntries.forEach(({ btn, secEl }) => {
    btn.addEventListener('click', function () {
      syncStickyWorkspaceOffsets();
      setActiveStep(btn);
      secEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  });

  ['chkVerifyOriginalCccd', 'chkVerifyAssetSeal', 'chkCustomerAgreedTerms'].forEach(id => {
    const chk = document.getElementById(id);
    if (chk) {
      chk.addEventListener('change', function () {
        clearFieldValidationError(chk);
        updateStepperCompletionStates();
      });
    }
  });

  function evaluateScrollSpyAndStickyHud() {
    const offsets = syncStickyWorkspaceOffsets();
    const threshold = (offsets && offsets.scrollMargin ? offsets.scrollMargin : 150) + 16;
    let currentEntry = sectionEntries[0];
    for (const entry of sectionEntries) {
      const rect = entry.secEl.getBoundingClientRect();
      if (rect.top <= threshold) {
        currentEntry = entry;
      }
    }
    if (currentEntry) {
      setActiveStep(currentEntry.btn);
    }
  }

  syncStickyWorkspaceOffsets();

  if (typeof ResizeObserver !== 'undefined') {
    const ro = new ResizeObserver(() => {
      syncStickyWorkspaceOffsets();
    });
    const topbarEl = document.querySelector('.pm-topbar');
    const hudEl = document.getElementById('ccStickyWorkspaceHud');
    if (topbarEl) ro.observe(topbarEl);
    if (hudEl) ro.observe(hudEl);
  }

  let ticking = false;
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', function () {
      syncStickyWorkspaceOffsets();
    }, { passive: true });

    window.addEventListener('scroll', function () {
      syncStickyWorkspaceOffsets();
      if (ticking) return;
      ticking = true;
      const scheduleFrame = typeof requestAnimationFrame === 'function'
        ? requestAnimationFrame
        : (cb => setTimeout(cb, 0));
      scheduleFrame(() => {
        ticking = false;
        evaluateScrollSpyAndStickyHud();
      });
    }, { passive: true });
  }
}

/**
 * LocalStorage Draft Persistence (#btnSaveDraft, #btnRestoreDraft, #btnResetForm)
 */
const DRAFT_FIELD_IDS = [
  'ccBranchId', 'ccSelectedCustomerId', 'ccCustomerLookupState', 'ccCustomerIdNumber',
  'ccCustomerName', 'ccCustomerPhone', 'ccGender', 'ccDateOfBirth',
  'ccIdentityIssueDate', 'ccIdentityIssuePlace', 'ccIdentityIssuePlaceCustom', 'ccCustomerEmail',
  'ccProvinceCode', 'ccDistrictCode', 'ccWardCode', 'ccAddressStreet',
  'ccSameAsPermanent', 'ccCurrentAddressInput',
  'ccOccupationGroup', 'ccMonthlyIncome', 'ccHasSocialInsurance',
  'ccVerificationDocType', 'ccDocumentUrl', 'ccIdCardFrontUrl', 'ccIdCardBackUrl', 'ccPortraitUrl', 'ccCustomerNotes',
  'ccCollateralCategory', 'ccCollateralName', 'ccCollateralBrand', 'ccCollateralModel',
  'ccLicensePlate', 'ccFrameNumber', 'ccVehicleRegNumber', 'ccVehicleOdometer',
  'ccSerialOrImei', 'ccTechPasscodeStatus', 'ccTechAccessories',
  'ccGoldPurity', 'ccGoldWeightChi', 'ccGemCertificateCode', 'ccOtherAssetSpec',
  'ccAppraisedValue', 'ccStorageLocation', 'ccConditionDescription', 'ccCollateralImageUrls',
  'ccPrincipalAmount', 'ccStartDate', 'ccLoanTermDays', 'ccPaymentCycle',
  'ccInterestType', 'ccInterestRate', 'ccStorageFeeRate',
  'ccDisbursementMethod', 'ccBankCode', 'ccBankAccountNumber', 'ccBankAccountHolder', 'ccContractNotes'
];

function setDraftBadgeState(mode, labelText) {
  const badge = document.getElementById('ccDraftStatusBadge');
  if (!badge) return;
  badge.classList.remove('is-saved', 'is-unsaved', 'is-restored');
  if (mode) badge.classList.add(mode);
  badge.innerHTML = `<span class="pm-badge-dot" aria-hidden="true"></span><span>${escapeHtml(labelText)}</span>`;
}

function markDraftUnsaved() {
  setDraftBadgeState('is-unsaved', 'Chưa lưu bản nháp');
}

function checkExistingDraftVisibility() {
  const restoreBtn = document.getElementById('btnRestoreDraft');
  if (!restoreBtn) return;
  try {
    const raw = typeof localStorage !== 'undefined' ? localStorage.getItem(CC_DRAFT_STORAGE_KEY) : null;
    restoreBtn.classList.toggle('d-none', !raw);
  } catch (e) {
    restoreBtn.classList.add('d-none');
  }
}

function saveContractDraftToStorage() {
  try {
    if (typeof localStorage === 'undefined') return;
    const fields = {};
    DRAFT_FIELD_IDS.forEach(id => {
      const el = document.getElementById(id);
      if (!el) return;
      fields[id] = el.type === 'checkbox' ? el.checked : el.value;
    });

    const draftObj = {
      savedAt: new Date().toISOString(),
      fields
    };
    localStorage.setItem(CC_DRAFT_STORAGE_KEY, JSON.stringify(draftObj));

    const timeStr = new Date().toLocaleTimeString(getSystemLocale(), { hour: '2-digit', minute: '2-digit', hour12: false });
    setDraftBadgeState('is-saved', `Đã lưu nháp lúc ${timeStr}`);
    checkExistingDraftVisibility();
    showToast('Đã lưu bản nháp hồ sơ cầm cố vào bộ nhớ trình duyệt.', 'success');
  } catch (e) {
    showToast('Không thể lưu bản nháp trên trình duyệt hiện tại.', 'warning');
  }
}

async function restoreContractDraftFromStorage() {
  try {
    const raw = typeof localStorage !== 'undefined' ? localStorage.getItem(CC_DRAFT_STORAGE_KEY) : null;
    if (!raw) {
      showToast('Không tìm thấy bản nháp đã lưu.', 'info');
      return;
    }
    const parsed = JSON.parse(raw);
    const fields = (parsed && parsed.fields) || {};

    const provCode = fields.ccProvinceCode || '79';
    const distCode = fields.ccDistrictCode || '';
    const wardCode = fields.ccWardCode || '';

    await PM_ADDRESS_CASCADE.initProvincesDropdown();
    const provEl = document.getElementById('ccProvinceCode');
    if (provEl) provEl.value = provCode;
    await PM_ADDRESS_CASCADE.loadDistrictOptions(provCode, distCode);
    await PM_ADDRESS_CASCADE.loadWardOptions(distCode, wardCode);

    DRAFT_FIELD_IDS.forEach(id => {
      if (id === 'ccProvinceCode' || id === 'ccDistrictCode' || id === 'ccWardCode') return;
      const el = document.getElementById(id);
      if (!el || !(id in fields)) return;
      if (el.type === 'checkbox') {
        el.checked = Boolean(fields[id]);
      } else {
        el.value = fields[id];
      }
    });

    const samePerm = document.getElementById('ccSameAsPermanent');
    const curGroup = document.getElementById('ccCurrentAddressGroup');
    if (samePerm && curGroup) {
      curGroup.classList.toggle('d-none', samePerm.checked);
    }

    syncIdentityIssuePlaceCustomUI({ focusCustom: false });

    const disbMethod = document.getElementById('ccDisbursementMethod');
    const bankGroup = document.getElementById('ccBankAccountGroup');
    if (disbMethod && bankGroup) {
      bankGroup.classList.toggle('d-none', disbMethod.value !== 'BANK_TRANSFER');
    }
    syncBankComboboxFromSelect();
    const restoredInterestType = document.getElementById('ccInterestType')?.value;
    if (restoredInterestType) {
      PM_CONTRACT_CREATE_STATE.lastInterestType = restoredInterestType;
    }
    const restoredHolder = (document.getElementById('ccBankAccountHolder')?.value || '').trim();
    const expectedHolder = toBankAccountHolderAscii(document.getElementById('ccCustomerName')?.value || '');
    PM_CONTRACT_CREATE_STATE.bankHolderEditedManually = Boolean(restoredHolder && restoredHolder !== expectedHolder);
    evaluateBankHolderMismatchWarning();

    syncBranchTelemetryFromSelector();
    PM_ADDRESS_CASCADE.composePermanentAddress();
    toggleDynamicCollateralGroups(getActiveCollateralCategory());
    if (typeof window.pmMarkCollateralDraftRestored === 'function') {
      window.pmMarkCollateralDraftRestored(fields);
    }
    await PM_MEDIA_MANAGER.hydrateCustomerMedia({
      identityNumber: document.getElementById('ccCustomerIdNumber')?.value || '',
      verificationDocUrls: document.getElementById('ccDocumentUrl')?.value || '',
      idCardFrontUrl: document.getElementById('ccIdCardFrontUrl')?.value || '',
      idCardBackUrl: document.getElementById('ccIdCardBackUrl')?.value || '',
      portraitUrl: document.getElementById('ccPortraitUrl')?.value || ''
    }, { locked: (document.getElementById('ccCustomerLookupState')?.value || 'FOUND') === 'FOUND' });
    PM_MEDIA_MANAGER.hydrateCollateralFromHiddenInput();
    recalculateLoanAndLedger({ triggerServerLtv: true });
    hideFormErrorSummary();
    setDraftBadgeState('is-restored', 'Đã khôi phục bản nháp');
    showToast('Đã khôi phục đầy đủ thông tin bản nháp hợp đồng!', 'success');
  } catch (e) {
    showToast('Bản nháp không hợp lệ hoặc đã cũ.', 'warning');
  }
}

function clearSavedContractDraft() {
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(CC_DRAFT_STORAGE_KEY);
    }
  } catch (e) {
    // Ignore storage error
  }
  checkExistingDraftVisibility();
}

async function resetOriginationFormToDefaults() {
  const form = document.getElementById('formCreateContract');
  if (form && typeof form.reset === 'function') form.reset();

  hideFormErrorSummary();
  clearSavedContractDraft();

  const cccdInput = document.getElementById('ccCustomerIdNumber');
  if (cccdInput) cccdInput.value = '079201004821';
  if (typeof window.pmPerformCccdLookup === 'function') {
    await window.pmPerformCccdLookup({ silent: true });
  }

  const catSelect = document.getElementById('ccCollateralCategory');
  if (catSelect) catSelect.value = 'MOTORBIKE';
  toggleDynamicCollateralGroups('MOTORBIKE');
  if (typeof window.pmResetCollateralSwitchState === 'function') {
    window.pmResetCollateralSwitchState();
  }

  const colImgsHidden = document.getElementById('ccCollateralImageUrls');
  if (colImgsHidden) colImgsHidden.value = '/uploads/collaterals/ts_0128_1.jpg';
  PM_MEDIA_MANAGER.hydrateCollateralFromHiddenInput();

  const appraisedEl = document.getElementById('ccAppraisedValue');
  if (appraisedEl) appraisedEl.value = formatVndDigits(60000000);
  const principalEl = document.getElementById('ccPrincipalAmount');
  if (principalEl) principalEl.value = formatVndDigits(45000000);

  const bankGroup = document.getElementById('ccBankAccountGroup');
  if (bankGroup) bankGroup.classList.add('d-none');
  const bankSelectEl = document.getElementById('ccBankCode');
  if (bankSelectEl) bankSelectEl.value = '';
  syncBankComboboxFromSelect();
  PM_CONTRACT_CREATE_STATE.bankHolderEditedManually = false;
  PM_CONTRACT_CREATE_STATE.lastInterestType = document.getElementById('ccInterestType')?.value || 'DAILY_PER_MILLION';
  syncBankAccountHolderFromCustomer(true);

  syncBranchTelemetryFromSelector();
  recalculateLoanAndLedger({ triggerServerLtv: false });
  setDraftBadgeState('', 'Hồ sơ trực tiếp tại quầy');
  showToast('Đã làm mới biểu mẫu lập hợp đồng về trạng thái mặc định.', 'info');
}

function initDraftToolbarButtons() {
  checkExistingDraftVisibility();

  const btnSave = document.getElementById('btnSaveDraft');
  const btnRestore = document.getElementById('btnRestoreDraft');
  const btnReset = document.getElementById('btnResetForm');
  const modalResetEl = document.getElementById('modalResetConfirmation');
  const btnConfirmReset = document.getElementById('btnConfirmReset');
  const btnCancelReset = document.getElementById('btnCancelReset');

  if (btnSave) {
    btnSave.addEventListener('click', saveContractDraftToStorage);
  }
  if (btnRestore) {
    btnRestore.addEventListener('click', function () {
      restoreContractDraftFromStorage();
    });
  }
  if (btnReset) {
    btnReset.addEventListener('click', function () {
      if (modalResetEl) {
        if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
          const modal = bootstrap.Modal.getInstance(modalResetEl) || new bootstrap.Modal(modalResetEl);
          modal.show();
        }
        modalResetEl.classList.add('show');
        modalResetEl.removeAttribute('aria-hidden');
      } else {
        resetOriginationFormToDefaults();
      }
    });
  }
  if (btnCancelReset && modalResetEl) {
    btnCancelReset.addEventListener('click', function () {
      if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
        const modal = bootstrap.Modal.getInstance(modalResetEl);
        if (modal) modal.hide();
      }
      modalResetEl.classList.remove('show');
      modalResetEl.setAttribute('aria-hidden', 'true');
    });
  }
  if (btnConfirmReset) {
    btnConfirmReset.addEventListener('click', async function () {
      await resetOriginationFormToDefaults();
      if (modalResetEl) {
        if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
          const modal = bootstrap.Modal.getInstance(modalResetEl);
          if (modal) modal.hide();
        }
        modalResetEl.classList.remove('show');
        modalResetEl.setAttribute('aria-hidden', 'true');
      }
      const secCustomer = document.getElementById('sectionCustomer');
      if (secCustomer && typeof secCustomer.scrollIntoView === 'function') {
        secCustomer.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    });
  }
}

/**
 * Printable A4 Vietnamese Pawn Contract Draft (#modalContractPrintPreview & #printContractBody)
 */
function renderPrintableContractSheet() {
  const printBody = document.getElementById('printContractBody');
  if (!printBody) return;

  const branchLabel = PM_CONTRACT_CREATE_STATE.branchNameLabel || 'Chi nhánh Quận 1 (CN-HCM-01)';
  const customerName = (document.getElementById('ccCustomerName')?.value || '').trim() || '........................................';
  const cccd = normalizeCccdDigits(document.getElementById('ccCustomerIdNumber')?.value || '') || '....................';
  const issueDate = formatDateVN(document.getElementById('ccIdentityIssueDate')?.value || '');
  const issuePlace = getResolvedIdentityIssuePlace() || CCCD_ISSUE_PLACE_OPTIONS.CUC_CS_QLHC_TTXH;
  const phone = (document.getElementById('ccCustomerPhone')?.value || '').trim() || '....................';
  const dob = formatDateVN(document.getElementById('ccDateOfBirth')?.value || '');
  const permanentAddr = PM_ADDRESS_CASCADE.composePermanentAddress() || '............................................................';
  const samePerm = document.getElementById('ccSameAsPermanent')?.checked !== false;
  const currentAddr = (!samePerm && document.getElementById('ccCurrentAddressInput')?.value.trim())
    ? document.getElementById('ccCurrentAddressInput').value.trim()
    : permanentAddr;
  const occCode = document.getElementById('ccOccupationGroup')?.value || 'OFFICE_WORKER';
  const occLabel = OCCUPATION_GROUP_LABELS[occCode] || occCode;

  const category = getActiveCollateralCategory();
  const categoryLabel = COLLATERAL_CATEGORY_LABELS[category] || category;
  const collateralName = (document.getElementById('ccCollateralName')?.value || '').trim() || '........................................';
  const brand = (document.getElementById('ccCollateralBrand')?.value || '').trim();
  const model = (document.getElementById('ccCollateralModel')?.value || '').trim();
  const licensePlate = (document.getElementById('ccLicensePlate')?.value || '').trim();
  const frameNumber = (document.getElementById('ccFrameNumber')?.value || '').trim();
  const regNumber = (document.getElementById('ccVehicleRegNumber')?.value || '').trim();
  const odometerRaw = (document.getElementById('ccVehicleOdometer')?.value || '').trim();
  const serialImei = (document.getElementById('ccSerialOrImei')?.value || '').trim();
  const passcodeStatus = (document.getElementById('ccTechPasscodeStatus')?.value || '').trim();
  const techAccessories = (document.getElementById('ccTechAccessories')?.value || '').trim();
  const goldPurity = (document.getElementById('ccGoldPurity')?.value || '').trim();
  const goldWeight = (document.getElementById('ccGoldWeightChi')?.value || '').trim();
  const gemCert = (document.getElementById('ccGemCertificateCode')?.value || '').trim();
  const otherSpec = (document.getElementById('ccOtherAssetSpec')?.value || '').trim();
  const conditionDesc = (document.getElementById('ccConditionDescription')?.value || '').trim() || 'Tài sản nguyên trạng, đã dán tem niêm phong.';
  const storageLoc = (document.getElementById('ccStorageLocation')?.value || '').trim() || 'Kho lưu trữ an toàn chi nhánh';

  const passcodeLabels = {
    ICLOUD_OFF_UNLOCKED: 'Đã thoát hoàn toàn iCloud / Google / MDM',
    PASSCODE_SEALED: 'Đã bàn giao mật khẩu kèm phong bì niêm phong',
    MECHANICAL_CERTIFIED: 'Đồng hồ cơ — Đã kiểm định máy & thẻ bảo hành'
  };

  let technicalDetails = '';
  if (category === 'MOTORBIKE' || category === 'CAR') {
    const odoPart = odometerRaw !== '' && Number.isFinite(Number(odometerRaw))
      ? ` • Số ODO: ${Number(odometerRaw).toLocaleString('en-US')} km`
      : '';
    technicalDetails = `Biển kiểm soát: ${licensePlate || '—'} • Số khung/Số máy: ${frameNumber || '—'}${regNumber ? ' • Số ĐKX: ' + regNumber : ''}${odoPart}`;
  } else if (category === 'PHONE' || category === 'LAPTOP' || category === 'WATCH') {
    const cloudPart = passcodeStatus ? ` • Bảo mật: ${passcodeLabels[passcodeStatus] || passcodeStatus}` : '';
    const accPart = techAccessories ? ` • Phụ kiện: ${techAccessories}` : '';
    technicalDetails = `Số IMEI / Serial: ${serialImei || '—'}${cloudPart}${accPart}`;
  } else if (category === 'GOLD_JEWELRY') {
    technicalDetails = `Hàm lượng vàng: ${goldPurity || '—'} • Trọng lượng: ${goldWeight || '—'} chỉ${gemCert ? ' • Mã kiểm định: ' + gemCert : ''}`;
  } else {
    technicalDetails = `Đặc điểm định danh: ${otherSpec || '—'}`;
  }

  const appraisedVal = parseVND(document.getElementById('ccAppraisedValue')?.value || 0);
  const principalVal = parseVND(document.getElementById('ccPrincipalAmount')?.value || 0);
  const startDateIso = document.getElementById('ccStartDate')?.value || toIsoDateString(new Date());
  const loanTermDays = parseInt(document.getElementById('ccLoanTermDays')?.value || '30', 10) || 30;
  const paymentCycleDays = parseInt(document.getElementById('ccPaymentCycle')?.value || '10', 10) || 10;
  const interestType = document.getElementById('ccInterestType')?.value || 'DAILY_PER_MILLION';
  const interestRate = parseFloat(document.getElementById('ccInterestRate')?.value || '0') || 0;
  const storageFee = parseFloat(document.getElementById('ccStorageFeeRate')?.value || '0') || 0;
  const printBankCode = (document.getElementById('ccBankCode')?.value || '').trim();
  const printBankName = resolveBankShortName(printBankCode) || printBankCode || 'Chưa chọn NH';
  const printBankAcc = (document.getElementById('ccBankAccountNumber')?.value || '').trim();
  const printBankHolder = toBankAccountHolderAscii(document.getElementById('ccBankAccountHolder')?.value || '') || toBankAccountHolderAscii(customerName);
  const disbMethod = document.getElementById('ccDisbursementMethod')?.value === 'BANK_TRANSFER'
    ? `Chuyển khoản (${printBankName} - STK: ${printBankAcc}${printBankHolder ? ' - ' + printBankHolder : ''})`
    : 'Tiền mặt trực tiếp tại quầy';

  const maturityDateStr = formatDateVN(new Date(parseIsoDateLocal(startDateIso).getTime() + loanTermDays * 86400000));
  let rateText = `${formatVndDigits(interestRate)} VNĐ / 1.000.000 VNĐ / ngày`;
  if (interestType === 'MONTHLY_PERCENT' || interestType === 'PERCENT_MONTHLY') {
    rateText = `${interestRate}% / tháng`;
  } else if (interestType === 'PERCENT_WEEKLY') {
    rateText = `${interestRate}% / tuần (7 ngày)`;
  }

  const cycles = PM_CONTRACT_CREATE_STATE.activeScheduleCycles || [];
  const scheduleRowsHtml = cycles.map(c => `
    <tr>
      <td>${escapeHtml(c.label)}</td>
      <td>${escapeHtml(c.fromDateStr)} - ${escapeHtml(c.dueDateStr)}</td>
      <td>${escapeHtml(c.dueDateStr)}</td>
      <td class="text-end">${c.days} ngày</td>
      <td class="text-end">${formatVND(c.interestAmt)}</td>
      <td class="text-end">${formatVND(c.storageAmt)}</td>
      <td class="text-end"><strong>${formatVND(c.totalAmt)}</strong></td>
    </tr>
  `).join('');

  printBody.innerHTML = `
    <div class="text-center mb-3">
      <div class="fw-bold text-uppercase">CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM</div>
      <div class="fw-semibold">Độc lập - Tự do - Hạnh phúc</div>
      <hr class="w-25 mx-auto my-2" />
      <h3 class="fw-bold text-uppercase mt-2 mb-1">HỢP ĐỒNG CẦM CỐ TÀI SẢN</h3>
      <div class="small font-tabular">Mã dự thảo: <strong>DRAFT-${toIsoDateString(new Date()).replace(/-/g, '')}</strong> • Ngày lập: <strong>${formatDateVN(startDateIso)}</strong></div>
    </div>

    <div class="mb-3">
      <div class="fw-bold text-uppercase mb-1">I. BÊN NHẬN CẦM CỐ (BÊN A — CƠ SỞ KINH DOANH DỊCH VỤ CẦM ĐỒ)</div>
      <div>Đơn vị giao dịch: <strong>Hệ thống Cầm đồ PawnMaster — ${escapeHtml(branchLabel)}</strong></div>
      <div>Đại diện giao dịch tại quầy: <strong>${escapeHtml(document.getElementById('topbarUserName')?.textContent || 'Giao dịch viên')}</strong></div>
    </div>

    <div class="mb-3">
      <div class="fw-bold text-uppercase mb-1">II. BÊN CẦM CỐ TÀI SẢN (BÊN B — KHÁCH HÀNG)</div>
      <div>Họ và tên khách hàng: <strong>${escapeHtml(customerName)}</strong> • Ngày sinh: <span class="font-tabular">${escapeHtml(dob)}</span></div>
      <div>Số CCCD/Định danh: <strong class="font-tabular font-mono">${escapeHtml(cccd)}</strong> • Ngày cấp: <span class="font-tabular">${escapeHtml(issueDate)}</span> • Nơi cấp: ${escapeHtml(issuePlace)}</div>
      <div>Số điện thoại liên hệ: <strong class="font-tabular">${escapeHtml(phone)}</strong> • Nhóm nghề nghiệp: ${escapeHtml(occLabel)}</div>
      <div>Địa chỉ thường trú (theo CCCD): <strong>${escapeHtml(permanentAddr)}</strong></div>
      <div>Địa chỉ cư trú / liên hệ hiện tại: ${escapeHtml(currentAddr)}</div>
    </div>

    <div class="mb-3">
      <div class="fw-bold text-uppercase mb-1">III. THÔNG TIN TÀI SẢN CẦM CỐ &amp; NIÊM PHONG</div>
      <table class="pm-print-table">
        <thead>
          <tr>
            <th>Danh mục</th>
            <th>Tên tài sản &amp; Nhãn hiệu</th>
            <th>Thông số định danh pháp lý</th>
            <th class="text-end">Giá trị thẩm định</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>${escapeHtml(categoryLabel)}</td>
            <td><strong>${escapeHtml(collateralName)}</strong>${brand || model ? `<br /><small>${escapeHtml([brand, model].filter(Boolean).join(' • '))}</small>` : ''}</td>
            <td class="font-tabular">${escapeHtml(technicalDetails)}</td>
            <td class="text-end font-tabular"><strong>${formatVND(appraisedVal)}</strong></td>
          </tr>
        </tbody>
      </table>
      <div><strong>Hiện trạng ngoại quan &amp; niêm phong:</strong> ${escapeHtml(conditionDesc)}</div>
      <div><strong>Vị trí lưu kho bảo quản:</strong> ${escapeHtml(storageLoc)}</div>
    </div>

    <div class="mb-3">
      <div class="fw-bold text-uppercase mb-1">IV. ĐIỀU KHOẢN KHOẢN VAY, LÃI SUẤT &amp; LỊCH THANH TOÁN ĐỊNH KỲ</div>
      <div>Số tiền cho vay gốc: <strong class="font-tabular">${formatVND(principalVal)}</strong> (<em>Bằng chữ: ${escapeHtml(readVndInWords(principalVal))}</em>)</div>
      <div>Phương thức giải ngân: <strong>${escapeHtml(disbMethod)}</strong></div>
      <div>Thời hạn cầm cố: <strong class="font-tabular">${loanTermDays} ngày</strong> (Từ ngày <span class="font-tabular">${escapeHtml(formatDateVN(startDateIso))}</span> đến ngày đáo hạn <span class="font-tabular">${escapeHtml(maturityDateStr)}</span>)</div>
      <div>Chu kỳ đóng lãi: <strong class="font-tabular">${paymentCycleDays} ngày / kỳ</strong> • Mức lãi thỏa thuận: <strong class="font-tabular">${escapeHtml(rateText)}</strong> • Phí bảo quản: <span class="font-tabular">${storageFee > 0 ? formatVND(storageFee) + '/kỳ' : '0 ₫'}</span></div>

      <table class="pm-print-table mt-2">
        <thead>
          <tr>
            <th>Kỳ thu</th>
            <th>Khoảng ngày tính lãi</th>
            <th>Hạn đóng lãi</th>
            <th class="text-end">Số ngày</th>
            <th class="text-end">Tiền lãi kỳ</th>
            <th class="text-end">Phí lưu kho</th>
            <th class="text-end">Tổng cộng kỳ</th>
          </tr>
        </thead>
        <tbody>
          ${scheduleRowsHtml}
        </tbody>
      </table>
    </div>

    <div class="mb-2 small">
      <strong>V. CAM KẾT PHÁP LÝ:</strong> Bên B cam đoan tài sản cầm cố nêu trên thuộc quyền sở hữu hợp pháp của Bên B, không có tranh chấp, không do hành vi vi phạm pháp luật mà có. Hai bên đã đọc kỹ, hiểu rõ quyền và nghĩa vụ theo Bộ luật Dân sự và Nghị định 96/2016/NĐ-CP.
    </div>

    <div class="pm-print-signatures">
      <div>
        <div class="fw-bold text-uppercase">BÊN CẦM CỐ TÀI SẢN (BÊN B)</div>
        <div class="small text-muted mb-5">(Ký, ghi rõ họ tên &amp; điểm chỉ)</div>
        <div class="fw-semibold mt-4">${escapeHtml(customerName)}</div>
      </div>
      <div>
        <div class="fw-bold text-uppercase">ĐẠI DIỆN BÊN NHẬN CẦM CỐ (BÊN A)</div>
        <div class="small text-muted mb-5">(Ký, đóng dấu xác nhận niêm phong)</div>
        <div class="fw-semibold mt-4">${escapeHtml(document.getElementById('topbarUserName')?.textContent || 'Giao dịch viên')}</div>
      </div>
    </div>
  `;
}

function initPrintPreviewModal() {
  const previewBtn = document.getElementById('btnPreviewContractPrint');
  const previewModalEl = document.getElementById('modalContractPrintPreview');
  const printNowBtn = document.getElementById('btnPrintDraftNow');

  if (previewBtn) {
    previewBtn.addEventListener('click', function () {
      renderPrintableContractSheet();
    });
  }
  if (previewModalEl) {
    previewModalEl.addEventListener('show.bs.modal', function () {
      renderPrintableContractSheet();
    });
  }
  if (printNowBtn) {
    printNowBtn.addEventListener('click', function () {
      renderPrintableContractSheet();
      if (typeof window !== 'undefined' && typeof window.print === 'function') {
        window.print();
      }
    });
  }

  // Pre-populate print preview sheet with live workspace data immediately
  renderPrintableContractSheet();
}

/**
 * WCAG Inline Validation & Linked Error Summary (#ccFormErrorSummary)
 * Follows ui-ux-pro-max Error Placement & Focusable Error Summary guidelines:
 * 1) Semantic <h2> error summary at top of form with role="alert" tabindex="-1"
 * 2) Contextual atomic live count badge (role="status" aria-atomic="true")
 * 3) Specific inline <p id="<fieldId>-error" class="pm-field-error"> linked via aria-describedby
 */
function markFieldInvalid(fieldEl, message) {
  if (!fieldEl) return;
  fieldEl.classList.add('is-invalid');
  fieldEl.setAttribute('aria-invalid', 'true');

  if (!fieldEl.id || !message || typeof document === 'undefined') return;
  const errId = `${fieldEl.id}-error`;

  const existingDesc = (fieldEl.getAttribute('aria-describedby') || '')
    .split(/\s+/)
    .filter(Boolean);
  if (!existingDesc.includes(errId)) {
    existingDesc.push(errId);
    fieldEl.setAttribute('aria-describedby', existingDesc.join(' '));
  }

  let anchorTargetEl = fieldEl;
  if (fieldEl.id === 'ccBankCode') {
    const comboboxEl = document.getElementById('ccBankCombobox');
    const bankSearchEl = document.getElementById('ccBankSearchInput');
    if (comboboxEl) {
      comboboxEl.classList.add('pm-bank-combobox-invalid');
      anchorTargetEl = comboboxEl;
    }
    if (bankSearchEl) {
      bankSearchEl.setAttribute('aria-invalid', 'true');
      const searchDesc = (bankSearchEl.getAttribute('aria-describedby') || '')
        .split(/\s+/)
        .filter(Boolean);
      if (!searchDesc.includes(errId)) {
        searchDesc.push(errId);
        bankSearchEl.setAttribute('aria-describedby', searchDesc.join(' '));
      }
    }
  }

  let errEl = document.getElementById(errId);
  if (errEl) {
    if (errEl.textContent !== message) {
      errEl.textContent = message;
    }
    errEl.classList.remove('d-none');
    return;
  }

  const anchorEl = anchorTargetEl !== fieldEl
    ? anchorTargetEl
    : (typeof fieldEl.closest === 'function' && fieldEl.closest('.input-group, .pm-cccd-lookup-bar, .pm-compliance-item')) || fieldEl;
  if (anchorEl && typeof anchorEl.insertAdjacentHTML === 'function') {
    anchorEl.insertAdjacentHTML(
      'afterend',
      `<p id="${escapeHtml(errId)}" class="pm-field-error mb-0">${escapeHtml(message)}</p>`
    );
  }
}

function clearFieldValidationError(fieldEl) {
  if (!fieldEl) return;
  fieldEl.classList.remove('is-invalid');
  fieldEl.removeAttribute('aria-invalid');

  if (!fieldEl.id || typeof document === 'undefined') return;
  const errId = `${fieldEl.id}-error`;

  if (fieldEl.hasAttribute('aria-describedby')) {
    const remainingDesc = (fieldEl.getAttribute('aria-describedby') || '')
      .split(/\s+/)
      .filter(token => Boolean(token) && token !== errId);
    if (remainingDesc.length > 0) {
      fieldEl.setAttribute('aria-describedby', remainingDesc.join(' '));
    } else {
      fieldEl.removeAttribute('aria-describedby');
    }
  }

  if (fieldEl.id === 'ccBankCode') {
    const comboboxEl = document.getElementById('ccBankCombobox');
    const bankSearchEl = document.getElementById('ccBankSearchInput');
    if (comboboxEl) comboboxEl.classList.remove('pm-bank-combobox-invalid');
    if (bankSearchEl) {
      bankSearchEl.removeAttribute('aria-invalid');
      if (bankSearchEl.hasAttribute('aria-describedby')) {
        const searchRemaining = (bankSearchEl.getAttribute('aria-describedby') || '')
          .split(/\s+/)
          .filter(token => Boolean(token) && token !== errId);
        if (searchRemaining.length > 0) {
          bankSearchEl.setAttribute('aria-describedby', searchRemaining.join(' '));
        } else {
          bankSearchEl.removeAttribute('aria-describedby');
        }
      }
    }
  }

  const errEl = document.getElementById(errId);
  if (errEl) {
    if (typeof errEl.remove === 'function') {
      errEl.remove();
    } else {
      errEl.classList.add('d-none');
    }
  }
}

function setErrorSummaryCollapsed(collapsed) {
  const summaryBox = document.getElementById('ccFormErrorSummary');
  const toggleBtn = document.getElementById('btnToggleErrorSummary');
  const toggleIcon = document.getElementById('ccErrorSummaryToggleIcon');
  const toggleText = document.getElementById('ccErrorSummaryToggleText');
  const isCollapsed = Boolean(collapsed);

  if (summaryBox) {
    summaryBox.classList.toggle('is-collapsed', isCollapsed);
  }
  if (toggleBtn) {
    toggleBtn.setAttribute('aria-expanded', isCollapsed ? 'false' : 'true');
  }
  if (toggleIcon) {
    toggleIcon.setAttribute('class', isCollapsed ? 'bi bi-chevron-down' : 'bi bi-chevron-up');
  }
  if (toggleText) {
    toggleText.textContent = isCollapsed ? 'Mở danh sách' : 'Thu gọn';
  }
  syncStickyWorkspaceOffsets();
}

function hideFormErrorSummary() {
  const summaryBox = document.getElementById('ccFormErrorSummary');
  const summaryList = document.getElementById('ccFormErrorSummaryList');
  const countBadge = document.getElementById('ccErrorSummaryCountBadge');
  PM_CONTRACT_CREATE_STATE.errorSummaryActive = false;
  PM_CONTRACT_CREATE_STATE.lastErrorSignature = '';
  if (summaryList) summaryList.innerHTML = '';
  if (countBadge) countBadge.textContent = 'Còn 0 mục cần bổ sung';
  if (summaryBox) {
    summaryBox.classList.remove('is-collapsed');
    summaryBox.classList.add('d-none');
  }
  syncStepErrorBadges([]);
  setErrorSummaryCollapsed(false);
}

function showFormErrorSummary(errors, options = {}) {
  const summaryBox = document.getElementById('ccFormErrorSummary');
  const summaryList = document.getElementById('ccFormErrorSummaryList');
  const countBadge = document.getElementById('ccErrorSummaryCountBadge');
  if (!summaryBox || !summaryList) return;

  PM_CONTRACT_CREATE_STATE.errorSummaryActive = errors.length > 0;
  syncStepErrorBadges(errors);

  const nextSignature = errors.map(err => `${err.targetId}:${err.message}`).join('|');
  const signatureChanged = nextSignature !== PM_CONTRACT_CREATE_STATE.lastErrorSignature;

  if (options.forceRender === true || signatureChanged) {
    PM_CONTRACT_CREATE_STATE.lastErrorSignature = nextSignature;

    if (countBadge) {
      countBadge.textContent = `Còn ${errors.length} mục cần bổ sung`;
    }

    summaryList.innerHTML = errors.map(err => `
      <li>
        <a href="#${escapeHtml(err.targetId)}" data-error-target="${escapeHtml(err.targetId)}">${escapeHtml(err.message)}</a>
      </li>
    `).join('');

    summaryList.querySelectorAll('a[data-error-target]').forEach(link => {
      link.addEventListener('click', function (e) {
        e.preventDefault();
        syncStickyWorkspaceOffsets();
        const id = link.getAttribute('data-error-target');
        const resolvedId = id === 'ccBankCode' ? 'ccBankSearchInput' : id;
        const targetEl = resolvedId ? document.getElementById(resolvedId) : (id ? document.getElementById(id) : null);
        if (targetEl) {
          expandParentAccordionIfCollapsed(targetEl);
          const scrollTarget = id === 'ccBankCode'
            ? (document.getElementById('ccBankCombobox') || targetEl)
            : ((typeof targetEl.closest === 'function' && targetEl.closest('.pm-compliance-item')) || targetEl);
          scrollTarget.scrollIntoView({ behavior: 'smooth', block: 'start' });
          targetEl.focus({ preventScroll: true });
        }
      });
    });
  }

  if (options.expandOnShow !== false) {
    setErrorSummaryCollapsed(false);
  }
  summaryBox.classList.remove('d-none');
  syncStickyWorkspaceOffsets();

  if (options.focusSummary !== false) {
    summaryBox.focus({ preventScroll: true });
  }
}

function refreshLiveFormErrorSummary() {
  if (!PM_CONTRACT_CREATE_STATE.errorSummaryActive) return;
  const remainingErrors = validateFullContractForm();
  if (remainingErrors.length === 0) {
    hideFormErrorSummary();
  } else {
    showFormErrorSummary(remainingErrors, { focusSummary: false, expandOnShow: false, forceRender: false });
  }
}

function initErrorSummaryControls() {
  const toggleBtn = document.getElementById('btnToggleErrorSummary');
  const summaryBox = document.getElementById('ccFormErrorSummary');
  const form = document.getElementById('formCreateContract');

  if (toggleBtn && summaryBox) {
    toggleBtn.addEventListener('click', function () {
      const nextCollapsed = !summaryBox.classList.contains('is-collapsed');
      setErrorSummaryCollapsed(nextCollapsed);
    });
  }

  if (form) {
    form.addEventListener('input', refreshLiveFormErrorSummary);
    form.addEventListener('change', refreshLiveFormErrorSummary);
  }

  const watchedIds = Array.from(new Set([
    ...DRAFT_FIELD_IDS,
    'chkVerifyOriginalCccd',
    'chkVerifyAssetSeal',
    'chkCustomerAgreedTerms'
  ]));
  watchedIds.forEach(fieldId => {
    const ctrl = document.getElementById(fieldId);
    if (!ctrl) return;
    ctrl.addEventListener('input', refreshLiveFormErrorSummary);
    ctrl.addEventListener('change', refreshLiveFormErrorSummary);
  });
}

function validateFullContractForm() {
  const errors = [];
  const invalidFieldMessages = new Map();

  function recordError(sectionId, targetId, message, fieldIdOverride) {
    const fieldId = fieldIdOverride || targetId;
    errors.push({ sectionId, targetId, fieldId, message });
    if (fieldId && !invalidFieldMessages.has(fieldId)) {
      invalidFieldMessages.set(fieldId, message);
    }
  }

  const lookupState = document.getElementById('ccCustomerLookupState')?.value || 'FOUND';
  const cccd = normalizeCccdDigits(document.getElementById('ccCustomerIdNumber')?.value || '');
  if (!isValidCccdLength(cccd)) {
    recordError('sectionCustomer', 'ccCustomerIdNumber', 'Số CCCD/CMND khách hàng phải gồm đúng 9 hoặc 12 chữ số.');
  } else if (lookupState === 'AWAITING_LOOKUP') {
    recordError('sectionCustomer', 'btnLookupCccd', 'Vui lòng nhấn "Tra cứu" số CCCD để đối soát hồ sơ khách hàng trước khi trình duyệt.', 'ccCustomerIdNumber');
  } else if (lookupState === 'NOT_FOUND') {
    recordError('sectionCustomer', 'btnBannerCreateCustomer', 'Số CCCD chưa có hồ sơ. Vui lòng nhấn "Tạo khách hàng mới" để nhập thông tin KYC.', 'ccCustomerIdNumber');
  }

  if (lookupState === 'FOUND' || lookupState === 'CREATING_NEW') {
    const custName = (document.getElementById('ccCustomerName')?.value || '').trim();
    const custPhone = (document.getElementById('ccCustomerPhone')?.value || '').trim();
    const phoneDigits = custPhone.replace(/\D+/g, '');
    const provCode = document.getElementById('ccProvinceCode')?.value || '';
    const distCode = document.getElementById('ccDistrictCode')?.value || '';
    const wardCode = document.getElementById('ccWardCode')?.value || '';
    const street = (document.getElementById('ccAddressStreet')?.value || '').trim();
    const occGroup = document.getElementById('ccOccupationGroup')?.value || 'OFFICE_WORKER';
    const docType = document.getElementById('ccVerificationDocType')?.value || '';
    const docUrl = (document.getElementById('ccDocumentUrl')?.value || '').trim();

    if (custName.length < 2) {
      recordError('sectionCustomer', 'ccCustomerName', 'Họ và tên khách hàng không được để trống.');
    }
    if (phoneDigits.length < 9 || phoneDigits.length > 12) {
      recordError('sectionCustomer', 'ccCustomerPhone', 'Số điện thoại chính chủ phải gồm từ 9 đến 11 chữ số.');
    }
    if (!provCode) {
      recordError('sectionCustomer', 'ccProvinceCode', 'Vui lòng chọn Tỉnh / Thành phố thường trú.');
    }
    if (!distCode) {
      recordError('sectionCustomer', 'ccDistrictCode', 'Vui lòng chọn Quận / Huyện thường trú.');
    }
    if (!wardCode) {
      recordError('sectionCustomer', 'ccWardCode', 'Vui lòng chọn Phường / Xã thường trú.');
    }
    if (street.length < 2) {
      recordError('sectionCustomer', 'ccAddressStreet', 'Vui lòng nhập Số nhà, tên đường địa chỉ thường trú.');
    }
    if (document.getElementById('ccIdentityIssuePlace')?.value === CCCD_ISSUE_PLACE_OPTIONS.OTHER) {
      const customPlaceVal = (document.getElementById('ccIdentityIssuePlaceCustom')?.value || '').trim();
      if (customPlaceVal.length < 2) {
        recordError('sectionCustomer', 'ccIdentityIssuePlaceCustom', 'Vui lòng nhập cụ thể Nơi cấp CCCD / Công an Tỉnh, Thành phố.');
      }
    }

    const allowedDocs = VALID_DOCS_BY_GROUP[occGroup] || [];
    if (!docType || (allowedDocs.length > 0 && !allowedDocs.includes(docType))) {
      recordError('sectionCustomer', 'ccVerificationDocType', 'Loại chứng từ xác minh chưa phù hợp với nhóm nghề nghiệp đã chọn.');
    }
    if (!PM_MEDIA_MANAGER.hasVerificationDocuments()) {
      recordError('sectionCustomer', 'btnPickVerificationDocs', 'Bắt buộc đính kèm hình ảnh chứng từ xác minh thu nhập/cư trú.', 'ccVerificationDocBox');
    }
  }

  // Section 2: Collateral checks
  const category = getActiveCollateralCategory();
  const colName = (document.getElementById('ccCollateralName')?.value || '').trim();
  const appraised = parseVND(document.getElementById('ccAppraisedValue')?.value || 0);

  if (colName.length < 2) {
    recordError('sectionCollateral', 'ccCollateralName', 'Vui lòng nhập Tên chi tiết tài sản cầm cố.');
  }

  if (category === 'MOTORBIKE' || category === 'CAR') {
    const plate = (document.getElementById('ccLicensePlate')?.value || '').trim();
    const frame = (document.getElementById('ccFrameNumber')?.value || '').trim();
    const odoRaw = (document.getElementById('ccVehicleOdometer')?.value || '').trim();
    if (!plate) {
      recordError('sectionCollateral', 'ccLicensePlate', 'Phương tiện giao thông bắt buộc nhập Biển kiểm soát.');
    }
    if (!frame) {
      recordError('sectionCollateral', 'ccFrameNumber', 'Phương tiện giao thông bắt buộc nhập Số khung / Số máy.');
    }
    if (odoRaw !== '') {
      const odoNum = Number(odoRaw);
      if (!Number.isInteger(odoNum) || odoNum < 0) {
        recordError('sectionCollateral', 'ccVehicleOdometer', 'Số ODO (km) phải là số nguyên không âm (>= 0).');
      }
    }
  } else if (category === 'PHONE' || category === 'LAPTOP' || category === 'WATCH') {
    const imei = (document.getElementById('ccSerialOrImei')?.value || '').trim();
    if (!imei) {
      recordError('sectionCollateral', 'ccSerialOrImei', 'Thiết bị công nghệ / Đồng hồ bắt buộc nhập Số IMEI hoặc Serial Number.');
    }
  } else if (category === 'GOLD_JEWELRY') {
    const purity = (document.getElementById('ccGoldPurity')?.value || '').trim();
    const weightRaw = (document.getElementById('ccGoldWeightChi')?.value || '').trim();
    const weightNum = Number(weightRaw);
    if (!purity) {
      recordError('sectionCollateral', 'ccGoldPurity', 'Tài sản Vàng / Trang sức bắt buộc chọn Tuổi vàng / Hàm lượng.');
    }
    if (!weightRaw || !Number.isFinite(weightNum) || weightNum <= 0) {
      recordError('sectionCollateral', 'ccGoldWeightChi', 'Vui lòng nhập Trọng lượng tịnh (chỉ vàng) lớn hơn 0.');
    }
  } else {
    const spec = (document.getElementById('ccOtherAssetSpec')?.value || '').trim();
    if (!spec) {
      recordError('sectionCollateral', 'ccOtherAssetSpec', 'Vui lòng nhập Mã định danh / Số seri hoặc đặc điểm tài sản.');
    }
  }

  if (appraised < 100000) {
    recordError('sectionCollateral', 'ccAppraisedValue', 'Giá trị thẩm định thực tế phải từ 100.000 VNĐ trở lên.');
  }

  // Section 3: Loan Terms & LTV Ceiling Gate
  const principal = parseVND(document.getElementById('ccPrincipalAmount')?.value || 0);
  const occGroup = document.getElementById('ccOccupationGroup')?.value || 'OFFICE_WORKER';
  const maxRatio = PM_CONTRACT_CREATE_STATE.serverEvaluatedMaxLtvRatio || getLocalMatrixMaxLtvRatio(occGroup, category);
  const maxAllowedLoan = Math.round(appraised * maxRatio);

  if (principal < 100000) {
    recordError('sectionLoanTerms', 'ccPrincipalAmount', 'Số tiền cho vay giải ngân phải từ 100.000 VNĐ trở lên.');
  } else if (appraised >= 100000 && principal > maxAllowedLoan) {
    recordError(
      'sectionLoanTerms',
      'ccPrincipalAmount',
      `Số tiền vay (${formatVND(principal)}) vượt quá trần LTV tối đa ${(maxRatio * 100).toFixed(1)}% (${formatVND(maxAllowedLoan)}).`
    );
  }

  const rateVal = parseFloat(document.getElementById('ccInterestRate')?.value || '');
  if (Number.isNaN(rateVal) || rateVal < 0) {
    recordError('sectionLoanTerms', 'ccInterestRate', 'Mức lãi suất áp dụng không hợp lệ.');
  }

  if (document.getElementById('ccDisbursementMethod')?.value === 'BANK_TRANSFER') {
    const bankCode = (document.getElementById('ccBankCode')?.value || '').trim();
    const rawBankAcc = (document.getElementById('ccBankAccountNumber')?.value || '').replace(/\s+/g, '').toUpperCase();
    const bankAccEl = document.getElementById('ccBankAccountNumber');
    if (bankAccEl && bankAccEl.value !== rawBankAcc) {
      bankAccEl.value = rawBankAcc;
    }
    if (!bankCode) {
      recordError('sectionLoanTerms', 'ccBankCode', 'Vui lòng chọn Ngân hàng thụ hưởng khi giải ngân chuyển khoản.');
    }
    if (!rawBankAcc) {
      recordError('sectionLoanTerms', 'ccBankAccountNumber', 'Vui lòng nhập Số tài khoản ngân hàng thụ hưởng.');
    } else if (!/^[A-Z0-9]{6,25}$/.test(rawBankAcc)) {
      recordError('sectionLoanTerms', 'ccBankAccountNumber', 'Số tài khoản ngân hàng không hợp lệ (6–25 ký tự chữ/số).');
    }
  }

  // Section 4: Legal Compliance Checkboxes
  const complianceChecks = [
    { id: 'chkVerifyOriginalCccd', msg: 'Bắt buộc xác nhận đã đối chiếu bản gốc CCCD gắn chip chính chủ.' },
    { id: 'chkVerifyAssetSeal', msg: 'Bắt buộc xác nhận đã kiểm tra nguồn gốc tài sản và dán tem niêm phong.' },
    { id: 'chkCustomerAgreedTerms', msg: 'Bắt buộc xác nhận khách hàng đã đồng ý lãi suất và điều khoản hợp đồng.' }
  ];
  complianceChecks.forEach(({ id, msg }) => {
    const chk = document.getElementById(id);
    if (chk && !chk.checked) {
      recordError('sectionCompliance', id, msg);
    }
  });

  // Reconcile inline field errors: clear fixed fields, mark/update invalid fields without DOM thrash
  const form = document.getElementById('formCreateContract');
  if (form) {
    form.querySelectorAll('.is-invalid').forEach(el => {
      if (!el.id || !invalidFieldMessages.has(el.id)) {
        clearFieldValidationError(el);
      }
    });
  }
  invalidFieldMessages.forEach((msg, fieldId) => {
    markFieldInvalid(document.getElementById(fieldId), msg);
  });

  return errors;
}

function buildOriginationPayload() {
  const branchId = Number(document.getElementById('ccBranchId')?.value || PM_CONTRACT_CREATE_STATE.activeBranchId || 1);
  const cccd = normalizeCccdDigits(document.getElementById('ccCustomerIdNumber')?.value || '');
  const fullName = (document.getElementById('ccCustomerName')?.value || '').trim();
  const phoneNumber = (document.getElementById('ccCustomerPhone')?.value || '').trim();

  const provinceEl = document.getElementById('ccProvinceCode');
  const districtEl = document.getElementById('ccDistrictCode');
  const wardEl = document.getElementById('ccWardCode');
  const provinceCode = provinceEl?.value || null;
  const districtCode = districtEl?.value || null;
  const wardCode = wardEl?.value || null;
  const rawStreet = (document.getElementById('ccAddressStreet')?.value || '').trim();
  const addressDetail = PM_ADDRESS_CASCADE.stripAdminSuffixesFromStreet(rawStreet, [
    PM_ADDRESS_CASCADE.getSelectedOptionLabel(wardEl),
    PM_ADDRESS_CASCADE.getSelectedOptionLabel(districtEl),
    PM_ADDRESS_CASCADE.getSelectedOptionLabel(provinceEl)
  ]) || null;
  const permanentAddressFull = PM_ADDRESS_CASCADE.composePermanentAddress();
  const sameAsPermanent = document.getElementById('ccSameAsPermanent')?.checked !== false;
  const currentAddrRaw = (document.getElementById('ccCurrentAddressInput')?.value || '').trim();
  const contactAddress = (!sameAsPermanent && currentAddrRaw) ? currentAddrRaw : permanentAddressFull;

  const identityIssueDate = document.getElementById('ccIdentityIssueDate')?.value || null;
  const identityIssuePlace = getResolvedIdentityIssuePlace() || null;
  const dateOfBirth = document.getElementById('ccDateOfBirth')?.value || null;
  const gender = document.getElementById('ccGender')?.value || null;
  const email = (document.getElementById('ccCustomerEmail')?.value || '').trim() || null;
  const idCardFrontUrl = (document.getElementById('ccIdCardFrontUrl')?.value || '').trim() || null;
  const idCardBackUrl = (document.getElementById('ccIdCardBackUrl')?.value || '').trim() || null;
  const portraitUrl = (document.getElementById('ccPortraitUrl')?.value || '').trim() || null;

  const occGroup = document.getElementById('ccOccupationGroup')?.value || 'OFFICE_WORKER';
  const monthlyIncome = parseVND(document.getElementById('ccMonthlyIncome')?.value || 0);
  const hasSocialInsurance = Boolean(document.getElementById('ccHasSocialInsurance')?.checked);
  const verificationDocType = document.getElementById('ccVerificationDocType')?.value || 'PAYSLIP_BANK_STATEMENT';
  const verificationDocUrls = (document.getElementById('ccDocumentUrl')?.value || '').trim();

  const category = getActiveCollateralCategory();
  const baseColName = (document.getElementById('ccCollateralName')?.value || '').trim();
  const brand = (document.getElementById('ccCollateralBrand')?.value || '').trim();
  const model = (document.getElementById('ccCollateralModel')?.value || '').trim();

  let serialOrFrameNumber = null;
  let licensePlate = null;
  let vehicleFrameNumber = null;
  let vehicleRegNumber = null;
  let vehicleOdometerKm = null;
  let techImeiOrSerial = null;
  let techPasscodeStatus = null;
  let techAccessories = null;
  let goldPurity = null;
  let goldWeightChi = null;
  let goldCertificateCode = null;
  let otherAssetSpec = null;

  if (category === 'MOTORBIKE' || category === 'CAR') {
    licensePlate = (document.getElementById('ccLicensePlate')?.value || '').trim() || null;
    vehicleFrameNumber = (document.getElementById('ccFrameNumber')?.value || '').trim() || null;
    vehicleRegNumber = (document.getElementById('ccVehicleRegNumber')?.value || '').trim() || null;
    const odoRaw = (document.getElementById('ccVehicleOdometer')?.value || '').trim();
    if (odoRaw !== '' && Number.isInteger(Number(odoRaw)) && Number(odoRaw) >= 0) {
      vehicleOdometerKm = Number(odoRaw);
    }
    serialOrFrameNumber = vehicleFrameNumber || licensePlate;
  } else if (category === 'PHONE' || category === 'LAPTOP' || category === 'WATCH') {
    techImeiOrSerial = (document.getElementById('ccSerialOrImei')?.value || '').trim() || null;
    techPasscodeStatus = (document.getElementById('ccTechPasscodeStatus')?.value || '').trim() || null;
    techAccessories = (document.getElementById('ccTechAccessories')?.value || '').trim() || null;
    serialOrFrameNumber = techImeiOrSerial;
  } else if (category === 'GOLD_JEWELRY') {
    goldPurity = (document.getElementById('ccGoldPurity')?.value || '').trim() || null;
    const weightRaw = (document.getElementById('ccGoldWeightChi')?.value || '').trim();
    if (weightRaw !== '' && Number.isFinite(Number(weightRaw)) && Number(weightRaw) > 0) {
      goldWeightChi = Number(weightRaw);
    }
    goldCertificateCode = (document.getElementById('ccGemCertificateCode')?.value || '').trim() || null;
    serialOrFrameNumber = goldCertificateCode || [
      goldPurity ? `Vàng ${goldPurity}` : '',
      goldWeightChi != null ? `${goldWeightChi} chỉ` : ''
    ].filter(Boolean).join(' - ') || null;
  } else {
    otherAssetSpec = (document.getElementById('ccOtherAssetSpec')?.value || '').trim() || null;
    serialOrFrameNumber = otherAssetSpec;
  }

  const storageLocation = (document.getElementById('ccStorageLocation')?.value || '').trim() || null;
  const baseCondition = (document.getElementById('ccConditionDescription')?.value || '').trim();
  const conditionDescription = baseCondition || null;

  const appraisedValue = parseVND(document.getElementById('ccAppraisedValue')?.value || 0);
  const principalAmount = parseVND(document.getElementById('ccPrincipalAmount')?.value || 0);
  const rawInterestType = document.getElementById('ccInterestType')?.value || 'DAILY_PER_MILLION';
  const backendInterestType = rawInterestType === 'MONTHLY_PERCENT' ? 'PERCENT_MONTHLY' : rawInterestType;
  const interestRate = parseFloat(document.getElementById('ccInterestRate')?.value || '2000') || 0;
  const loanTermDays = parseInt(document.getElementById('ccLoanTermDays')?.value || '30', 10) || 30;
  const paymentCycleDays = parseInt(document.getElementById('ccPaymentCycle')?.value || '10', 10) || 10;
  const startDate = document.getElementById('ccStartDate')?.value || toIsoDateString(new Date());

  const storageInput = parseFloat(document.getElementById('ccStorageFeeRate')?.value || '0') || 0;
  let backendStorageFeeRate = 0;
  if (storageInput > 0 && principalAmount > 0 && paymentCycleDays > 0) {
    if (storageInput >= 1000) {
      if (backendInterestType === 'PERCENT_MONTHLY') {
        backendStorageFeeRate = Number((((storageInput / paymentCycleDays) * 30 / principalAmount) * 100).toFixed(4));
      } else if (backendInterestType === 'PERCENT_WEEKLY') {
        backendStorageFeeRate = Number((((storageInput / paymentCycleDays) * 7 / principalAmount) * 100).toFixed(4));
      } else {
        backendStorageFeeRate = Number(((storageInput / paymentCycleDays) / (principalAmount / 1000000)).toFixed(2));
      }
    } else {
      backendStorageFeeRate = storageInput;
    }
  }

  const disbMethod = document.getElementById('ccDisbursementMethod')?.value || 'CASH';
  const bankCode = disbMethod === 'BANK_TRANSFER'
    ? (document.getElementById('ccBankCode')?.value || '').trim().toUpperCase() || null
    : null;
  const bankName = bankCode ? resolveBankShortName(bankCode) : null;
  const bankAcc = disbMethod === 'BANK_TRANSFER'
    ? (document.getElementById('ccBankAccountNumber')?.value || '').replace(/\s+/g, '').toUpperCase() || null
    : null;
  const rawBankHolder = (document.getElementById('ccBankAccountHolder')?.value || '').trim();
  const bankHolder = disbMethod === 'BANK_TRANSFER'
    ? (toBankAccountHolderAscii(rawBankHolder) || toBankAccountHolderAscii(fullName) || null)
    : null;
  const rawNotes = (document.getElementById('ccContractNotes')?.value || '').trim();
  const notes = rawNotes || null;

  return {
    branchId,
    customerFullName: fullName,
    identityNumber: cccd,
    identityIssueDate,
    identityIssuePlace,
    phoneNumber,
    email,
    dateOfBirth,
    gender,
    provinceCode,
    districtCode,
    wardCode,
    addressDetail,
    address: contactAddress,
    permanentAddress: permanentAddressFull,
    occupationGroup: occGroup,
    monthlyIncome: monthlyIncome > 0 ? monthlyIncome : null,
    hasSocialInsurance,
    verificationDocType,
    verificationDocUrls,
    idCardFrontUrl,
    idCardBackUrl,
    portraitUrl,
    collateralCategory: category,
    collateralName: baseColName,
    brand: brand || null,
    model: model || null,
    serialOrFrameNumber,
    licensePlate,
    vehicleFrameNumber,
    vehicleRegNumber,
    vehicleOdometerKm,
    techImeiOrSerial,
    techPasscodeStatus,
    techAccessories,
    goldPurity,
    goldWeightChi,
    goldCertificateCode,
    otherAssetSpec,
    storageLocation,
    conditionDescription,
    collateralImageUrls: (document.getElementById('ccCollateralImageUrls')?.value || '').trim() || null,
    appraisedValue,
    principalAmount,
    interestType: backendInterestType,
    interestRate,
    storageFeeRate: backendStorageFeeRate,
    loanTermDays,
    paymentCycleDays,
    startDate,
    disbursementMethod: disbMethod,
    disbursementBankCode: bankCode,
    disbursementBankName: bankName,
    disbursementAccountNumber: bankAcc,
    disbursementAccountHolder: bankHolder,
    notes
  };
}

function openOriginationSuccessModal(contractCode, customerName, principalAmount, branchId) {
  const codeEl = document.getElementById('succContractCode');
  const custEl = document.getElementById('succCustomerName');
  const prinEl = document.getElementById('succPrincipalAmount');
  const goDashboardLink = document.getElementById('btnGoToDashboardAfterSuccess');

  if (codeEl) codeEl.textContent = contractCode || 'HD-MỚI';
  if (custEl) custEl.textContent = customerName || 'Khách hàng';
  if (prinEl) prinEl.textContent = formatVND(principalAmount);
  if (goDashboardLink) {
    const qs = branchId ? `branchId=${encodeURIComponent(branchId)}` : '';
    goDashboardLink.setAttribute('href', resolvePageRoute('/dashboard', 'dashboard.html', qs));
  }

  const modalEl = document.getElementById('modalOriginationSuccess');
  if (modalEl && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
    const modal = bootstrap.Modal.getInstance(modalEl) || new bootstrap.Modal(modalEl);
    modal.show();
  }
}

function initOriginationFormSubmit() {
  const form = document.getElementById('formCreateContract');
  const submitBtn = document.getElementById('btnSubmitOrigination');
  const createAnotherBtn = document.getElementById('btnCreateAnotherContract');

  if (createAnotherBtn) {
    createAnotherBtn.addEventListener('click', function () {
      resetOriginationFormToDefaults();
      if (typeof window !== 'undefined') {
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }
    });
  }

  if (!form) return;

  form.addEventListener('submit', function (e) {
    e.preventDefault();

    const validationErrors = validateFullContractForm();
    if (validationErrors.length > 0) {
      showFormErrorSummary(validationErrors, { focusSummary: true, expandOnShow: true, forceRender: true });
      showToast(`Biểu mẫu còn ${validationErrors.length} mục cần kiểm tra trước khi trình duyệt.`, 'danger');
      return;
    }

    hideFormErrorSummary();

    withSubmitLock(submitBtn, async () => {
      if (typeof window.pmEnsureCustomerReadyForOrigination === 'function') {
        const customerReady = await window.pmEnsureCustomerReadyForOrigination();
        if (!customerReady) {
          return;
        }
      }

      const cccdForMedia = normalizeCccdDigits(document.getElementById('ccCustomerIdNumber')?.value || '');
      try {
        await PM_MEDIA_MANAGER.uploadPendingCustomerMedia(cccdForMedia);
        await PM_MEDIA_MANAGER.uploadPendingCollateralMedia(cccdForMedia);
      } catch (mediaErr) {
        showToast((mediaErr && mediaErr.message) || 'Không thể tải lên hình ảnh hồ sơ. Vui lòng kiểm tra định dạng/dung lượng tệp.', 'danger');
        return;
      }

      const payload = buildOriginationPayload();

      if (isBackendServerMode()) {
        try {
          const result = await apiFetch('/api/v1/contracts/originate', {
            method: 'POST',
            body: JSON.stringify(payload)
          });

          clearSavedContractDraft();
          setDraftBadgeState('is-saved', 'Đã gửi trình duyệt giải ngân');

          const createdCode = (result && result.data && result.data.contractCode)
            ? result.data.contractCode
            : `HD-${toIsoDateString(new Date()).slice(0, 7).replace('-', '')}-${String(Math.floor(1000 + Math.random() * 9000))}`;

          showToast(`Khởi tạo hồ sơ hợp đồng ${createdCode} thành công!`, 'success');
          openOriginationSuccessModal(createdCode, payload.customerFullName, payload.principalAmount, payload.branchId);
          return;
        } catch (err) {
          showToast(err.message || 'Không thể khởi tạo hợp đồng. Vui lòng kiểm tra hạn mức LTV và chứng từ.', 'danger');
          return;
        }
      }

      // Standalone / Offline Preview Fallback
      clearSavedContractDraft();
      setDraftBadgeState('is-saved', 'Đã lập hồ sơ (Chế độ xem trước)');
      const demoCode = `HD-${toIsoDateString(new Date()).slice(0, 7).replace('-', '')}-0899`;
      showToast(`Đã tạo hồ sơ hợp đồng mẫu ${demoCode} thành công!`, 'success');
      openOriginationSuccessModal(demoCode, payload.customerFullName, payload.principalAmount, payload.branchId);
    });
  });
}

/**
 * Contextual Bootstrap Tooltips (Step 2B & Step 5.2)
 */
function initContextualTooltips() {
  if (typeof document === 'undefined') return;
  const triggers = document.querySelectorAll('[data-bs-toggle="tooltip"]');
  triggers.forEach(el => {
    // Prevent tooltip trigger inside <label> (e.g., #tipAssetSeal) from toggling parent checkbox on click/Space
    el.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
    });
    el.addEventListener('keydown', function (e) {
      if (e.key === ' ' || e.key === 'Enter') {
        e.preventDefault();
        e.stopPropagation();
      }
    });
    if (typeof bootstrap !== 'undefined' && bootstrap.Tooltip) {
      new bootstrap.Tooltip(el, {
        trigger: 'hover focus',
        html: false
      });
    }
  });
}

/**
 * Real-Time Inline Field Validation on Blur / Change (Step 3A)
 */
function getWorkspaceTodayIso() {
  const bodyToday = typeof document !== 'undefined' && document.body
    ? (document.body.getAttribute('data-today-iso') || '').trim()
    : '';
  if (bodyToday && /^\d{4}-\d{2}-\d{2}$/.test(bodyToday)) {
    return bodyToday;
  }
  return toIsoDateString(new Date());
}

function initInlineValidation() {
  const cccdEl = document.getElementById('ccCustomerIdNumber');
  const phoneEl = document.getElementById('ccCustomerPhone');
  const emailEl = document.getElementById('ccCustomerEmail');
  const dobEl = document.getElementById('ccDateOfBirth');
  const issueDateEl = document.getElementById('ccIdentityIssueDate');

  if (cccdEl) {
    cccdEl.addEventListener('blur', function () {
      const val = normalizeCccdDigits(cccdEl.value || '');
      if (!val) return;
      if (!/^(\d{9}|\d{12})$/.test(val)) {
        markFieldInvalid(cccdEl, 'CCCD phải là 9 hoặc 12 chữ số');
      } else {
        clearFieldValidationError(cccdEl);
      }
    });
  }

  if (phoneEl) {
    phoneEl.addEventListener('input', function () {
      clearFieldValidationError(phoneEl);
    });
    phoneEl.addEventListener('blur', function () {
      if (phoneEl.hasAttribute('readonly')) return;
      const raw = (phoneEl.value || '').trim();
      if (!raw) return;
      const digits = raw.replace(/\D+/g, '');
      if (!/^0\d{9}$/.test(digits)) {
        markFieldInvalid(phoneEl, 'Số điện thoại phải là 10 chữ số (bắt đầu bằng số 0)');
      } else {
        clearFieldValidationError(phoneEl);
      }
    });
  }

  if (emailEl) {
    emailEl.addEventListener('input', function () {
      clearFieldValidationError(emailEl);
    });
    emailEl.addEventListener('blur', function () {
      if (emailEl.hasAttribute('readonly')) return;
      const raw = (emailEl.value || '').trim();
      if (!raw) {
        clearFieldValidationError(emailEl);
        return;
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(raw)) {
        markFieldInvalid(emailEl, 'Định dạng email không hợp lệ');
      } else {
        clearFieldValidationError(emailEl);
      }
    });
  }

  const bindNotFutureDateCheck = (dateEl, futureMsg) => {
    if (!dateEl) return;
    const validateDate = () => {
      if (dateEl.hasAttribute('readonly')) return;
      const isoVal = (dateEl.value || '').trim();
      if (!isoVal) {
        clearFieldValidationError(dateEl);
        return;
      }
      if (/^\d{4}-\d{2}-\d{2}$/.test(isoVal) && isoVal > getWorkspaceTodayIso()) {
        markFieldInvalid(dateEl, futureMsg);
      } else {
        clearFieldValidationError(dateEl);
      }
    };
    dateEl.addEventListener('change', validateDate);
    dateEl.addEventListener('blur', validateDate);
  };

  bindNotFutureDateCheck(dobEl, 'Ngày sinh không thể là ngày trong tương lai');
  bindNotFutureDateCheck(issueDateEl, 'Ngày cấp CCCD không thể là ngày trong tương lai');

  const odometerEl = document.getElementById('ccVehicleOdometer');
  if (odometerEl) {
    odometerEl.addEventListener('input', () => clearFieldValidationError(odometerEl));
    odometerEl.addEventListener('blur', function () {
      const raw = (odometerEl.value || '').trim();
      if (!raw) {
        clearFieldValidationError(odometerEl);
        return;
      }
      const num = Number(raw);
      if (!Number.isInteger(num) || num < 0) {
        markFieldInvalid(odometerEl, 'Số ODO (km) phải là số nguyên không âm (>= 0)');
      } else {
        clearFieldValidationError(odometerEl);
      }
    });
  }

  const goldWeightEl = document.getElementById('ccGoldWeightChi');
  if (goldWeightEl) {
    goldWeightEl.addEventListener('input', () => clearFieldValidationError(goldWeightEl));
    goldWeightEl.addEventListener('blur', function () {
      const cat = (document.getElementById('ccCollateralCategory')?.value || '').trim();
      const raw = (goldWeightEl.value || '').trim();
      if (cat !== 'GOLD_JEWELRY' && !raw) {
        clearFieldValidationError(goldWeightEl);
        return;
      }
      const num = Number(raw);
      if (raw !== '' && (!Number.isFinite(num) || num <= 0)) {
        markFieldInvalid(goldWeightEl, 'Trọng lượng tịnh (chỉ vàng) phải lớn hơn 0');
      } else {
        clearFieldValidationError(goldWeightEl);
      }
    });
  }
}

/**
 * Operate-Mode Counter Keyboard Shortcuts & Help Modal (Step 3E)
 */
function initKeyboardShortcuts() {
  const shortcutsBtn = document.getElementById('btnKeyboardShortcuts');
  const shortcutsModalEl = document.getElementById('modalKeyboardShortcuts');

  if (shortcutsBtn && shortcutsModalEl) {
    shortcutsBtn.addEventListener('click', function () {
      if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
        const modal = bootstrap.Modal.getInstance(shortcutsModalEl) || new bootstrap.Modal(shortcutsModalEl);
        modal.show();
      }
      shortcutsModalEl.classList.add('show');
      shortcutsModalEl.removeAttribute('aria-hidden');
    });
  }

  if (typeof document === 'undefined') return;

  document.addEventListener('keydown', function (e) {
    if (!e) return;
    const key = String(e.key || '');
    const lowerKey = key.toLowerCase();

    // Ctrl/Cmd + S -> Save Draft
    if ((e.ctrlKey || e.metaKey) && !e.altKey && lowerKey === 's') {
      e.preventDefault();
      const saveBtn = document.getElementById('btnSaveDraft');
      if (saveBtn) saveBtn.click();
      return;
    }

    // Ctrl/Cmd + P -> Open Print Preview Modal
    if ((e.ctrlKey || e.metaKey) && !e.altKey && lowerKey === 'p') {
      e.preventDefault();
      renderPrintableContractSheet();
      const previewBtn = document.getElementById('btnPreviewContractPrint');
      if (previewBtn) previewBtn.click();
      const previewModalEl = document.getElementById('modalContractPrintPreview');
      if (previewModalEl && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
        const modal = bootstrap.Modal.getInstance(previewModalEl) || new bootstrap.Modal(previewModalEl);
        modal.show();
      }
      return;
    }

    // Ctrl/Cmd + Enter -> Submit Contract Origination
    if ((e.ctrlKey || e.metaKey) && !e.altKey && key === 'Enter') {
      e.preventDefault();
      const submitBtn = document.getElementById('btnSubmitOrigination');
      const formEl = document.getElementById('formCreateContract');
      if (submitBtn && !submitBtn.disabled) {
        if (formEl && typeof formEl.requestSubmit === 'function') {
          formEl.requestSubmit(submitBtn);
        } else {
          submitBtn.click();
        }
      }
      return;
    }

    // Alt + 1 / 2 / 3 / 4 -> Jump to Section 1..4
    if (e.altKey && !e.ctrlKey && !e.metaKey && ['1', '2', '3', '4'].includes(key)) {
      e.preventDefault();
      const sectionIds = ['sectionCustomer', 'sectionCollateral', 'sectionLoanTerms', 'sectionCompliance'];
      const stepBtnIds = ['ccStepBtnCustomer', 'ccStepBtnCollateral', 'ccStepBtnLoanTerms', 'ccStepBtnCompliance'];
      const idx = parseInt(key, 10) - 1;
      const stepBtn = document.getElementById(stepBtnIds[idx]);
      const targetSec = document.getElementById(sectionIds[idx]);
      if (stepBtn) {
        stepBtn.click();
      } else if (targetSec && typeof targetSec.scrollIntoView === 'function') {
        targetSec.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }
  });
}

/**
 * Bootstrap Full Contract Origination Workspace on DOM Ready
 */
function initContractCreateWorkspace() {
  syncSystemDateInputsLocale();
  initTopbarAndAuth();
  syncBranchTelemetryFromSelector();
  initCascadingAddressListeners();
  initCollateralSection();
  initLoanTermsSection();
  PM_MEDIA_MANAGER.initMediaControls();
  initCustomerAccordionSubsteps();
  initCustomerLookupAndInlineCreate();
  initInlineValidation();
  initContextualTooltips();
  initBackToTopButton();
  initWorkflowStepperScrollSpy();
  initErrorSummaryControls();
  initDraftToolbarButtons();
  initPrintPreviewModal();
  initKeyboardShortcuts();
  initOriginationFormSubmit();
  recalculateLoanAndLedger({ triggerServerLtv: false });
}

if (typeof window !== 'undefined') {
  window.formatVND = formatVND;
  window.parseVND = parseVND;
  window.readVndInWords = readVndInWords;
  window.getSystemLocale = getSystemLocale;
  window.getSystemDateFormatPattern = getSystemDateFormatPattern;
  window.formatSystemDate = formatSystemDate;
  window.parseSystemDateToIso = parseSystemDateToIso;
  window.syncSystemDateInputsLocale = syncSystemDateInputsLocale;
  window.OFFLINE_CUSTOMER_PROFILES = OFFLINE_CUSTOMER_PROFILES;
  window.PM_ADDRESS_CASCADE = PM_ADDRESS_CASCADE;
  window.PM_MEDIA_MANAGER = PM_MEDIA_MANAGER;
  window.PM_CONTRACT_CREATE_STATE = PM_CONTRACT_CREATE_STATE;
  window.recalculateLoanAndLedger = recalculateLoanAndLedger;
}

if (typeof document !== 'undefined') {
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initContractCreateWorkspace);
  } else {
    initContractCreateWorkspace();
  }
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    formatVND,
    formatVndDigits,
    parseVND,
    readVndInWords,
    getSystemLocale,
    getSystemDateFormatPattern,
    formatSystemDate,
    formatDateVN,
    parseSystemDateToIso,
    enhanceSingleLocaleDateInput,
    syncSystemDateInputsLocale,
    CCCD_ISSUE_PLACE_OPTIONS,
    suggestIdentityIssuePlaceByDate,
    getResolvedIdentityIssuePlace,
    isStaticPreviewMode,
    resolvePageRoute,
    PM_ADDRESS_CASCADE,
    PM_MEDIA_MANAGER,
    LTV_MATRIX,
    VALID_DOCS_BY_GROUP,
    OFFLINE_ADMIN_CATALOG,
    OFFLINE_CUSTOMER_PROFILES,
    DEMO_CUSTOMERS_BY_CCCD,
    VN_BANKS_CATALOG,
    toBankAccountHolderAscii,
    normalizeBankSearchText,
    rankBanksByQuery,
    computeCycleAmounts,
    buildPaymentScheduleCycles
  };
}
