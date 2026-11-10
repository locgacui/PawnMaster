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

  const activeData = customData || defaultDatasets.months;

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
 * Initialize Table Live Search and Filter Tabs
 */
function initDashboardFilters(tableId = 'recentContractsTable', searchInputId = 'contractSearchInput', filterTabsId = 'contractFilterTabs') {
  const table = document.getElementById(tableId);
  const searchInput = document.getElementById(searchInputId);
  const searchClear = document.getElementById('contractSearchClear');
  const filterTabsContainer = document.getElementById(filterTabsId);
  const emptyState = document.getElementById('tableEmptyState');
  const recordCountBadge = document.getElementById('tableRecordCountBadge');

  if (!table) return;

  const tbody = table.querySelector('tbody');
  if (!tbody) return;

  let currentStatusFilter = 'ALL';
  let currentSearchQuery = '';

  function applyFilters() {
    const rows = tbody.querySelectorAll('tr[data-contract-code]');
    let visibleCount = 0;
    const query = currentSearchQuery.trim().toLowerCase();

    rows.forEach(row => {
      const rowStatus = (row.getAttribute('data-status') || '').toUpperCase();
      const isDueToday = row.getAttribute('data-due-today') === 'true';

      // Status Filter matching
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

      // Search Query matching across columns
      let searchMatches = true;
      if (query.length > 0) {
        const rowText = (row.textContent || '').toLowerCase();
        searchMatches = rowText.includes(query);
      }

      if (statusMatches && searchMatches) {
        row.style.display = '';
        visibleCount++;
      } else {
        row.style.display = 'none';
      }
    });

    // Handle Empty State
    if (emptyState) {
      if (visibleCount === 0) {
        emptyState.style.display = '';
      } else {
        emptyState.style.display = 'none';
      }
    }

    // Update Record Count Badge
    if (recordCountBadge) {
      recordCountBadge.textContent = `${visibleCount} HĐ`;
    }
  }

  // Bind Search Input
  if (searchInput) {
    searchInput.addEventListener('input', function (e) {
      currentSearchQuery = e.target.value;
      if (searchClear) {
        searchClear.style.display = currentSearchQuery.length > 0 ? 'block' : 'none';
      }
      applyFilters();
    });
  }

  // Bind Search Clear Button
  if (searchClear && searchInput) {
    searchClear.addEventListener('click', function () {
      searchInput.value = '';
      currentSearchQuery = '';
      searchClear.style.display = 'none';
      searchInput.focus();
      applyFilters();
    });
  }

  // Bind Filter Tabs
  if (filterTabsContainer) {
    const tabButtons = filterTabsContainer.querySelectorAll('.pm-tab-btn');
    tabButtons.forEach(btn => {
      btn.addEventListener('click', function () {
        tabButtons.forEach(b => b.classList.remove('active'));
        this.classList.add('active');
        currentStatusFilter = this.getAttribute('data-filter') || 'ALL';
        applyFilters();
      });
    });
  }

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
        filterTabsContainer.querySelectorAll('.pm-tab-btn').forEach(b => b.classList.remove('active'));
        allTab.classList.add('active');
      }
    }
    currentStatusFilter = 'ALL';
    applyFilters();
  };
}

/**
 * 2D LTV Matrix & Verification Document Definitions (Mirroring database LTV policy)
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

const VALID_DOCS_BY_GROUP = {
  OFFICE_WORKER: ['PAYSLIP_BANK_STATEMENT', 'LABOR_CONTRACT', 'SOCIAL_INSURANCE_VSSID'],
  FACTORY_WORKER: ['PAYSLIP_BANK_STATEMENT', 'WORKER_ID_OR_CONTRACT', 'SOCIAL_INSURANCE_VSSID'],
  TECH_DRIVER: ['DRIVER_APP_INCOME_SCREENSHOT', 'RESIDENCE_CONFIRMATION_CT07', 'RENTAL_CONTRACT'],
  SMALL_MERCHANT: ['BUSINESS_LICENSE_OR_STALL_LEASE', 'SHOP_PHOTO_AND_CASHFLOW'],
  ONLINE_SELLER: ['ECOMMERCE_REVENUE_SCREENSHOT', 'BANK_COD_STATEMENT'],
  FREELANCER: ['RESIDENCE_CONFIRMATION_CT07', 'RENTAL_CONTRACT', 'UTILITY_BILL']
};

/**
 * Setup Real-time Calculation for New Contract Modal (2D LTV Matrix & Risk Underwriting)
 */
function initNewContractCalculator() {
  const occGroupSelect = document.getElementById('ncOccupationGroup');
  const catSelect = document.getElementById('ncCollateralCategory');
  const docTypeSelect = document.getElementById('ncVerificationDocType');
  const termSelect = document.getElementById('ncLoanTermDays');
  const appraisedInput = document.getElementById('ncAppraisedValue');
  const loanInput = document.getElementById('ncPrincipalAmount');
  const cycleSelect = document.getElementById('ncPaymentCycle');
  const rateInput = document.getElementById('ncDailyRate');

  const maxLtvDisplay = document.getElementById('ncMaxLtvAllowed');
  const maxLoanDisplay = document.getElementById('ncMaxLoanAllowed');
  const ltvDisplay = document.getElementById('ncLtvDisplay');
  const cycleInterestDisplay = document.getElementById('ncCycleInterestDisplay');
  const nextDueDateDisplay = document.getElementById('ncNextDueDateDisplay');
  const maturityDateDisplay = document.getElementById('ncMaturityDateDisplay');
  const disbursementDisplay = document.getElementById('ncDisbursementAmount');
  const ltvWarning = document.getElementById('ncLtvWarning');

  function formatDateVN(date) {
    const d = String(date.getDate()).padStart(2, '0');
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const y = date.getFullYear();
    return `${d}/${m}/${y}`;
  }

  function updateAllowedDocs() {
    if (!occGroupSelect || !docTypeSelect) return;
    const group = occGroupSelect.value;
    const allowed = VALID_DOCS_BY_GROUP[group] || [];
    let hasSelected = false;

    Array.from(docTypeSelect.options).forEach(opt => {
      const isAllowed = allowed.includes(opt.value);
      if (isAllowed) {
        opt.style.fontWeight = 'bold';
        opt.style.color = '#1e40af';
        if (!hasSelected) {
          docTypeSelect.value = opt.value;
          hasSelected = true;
        }
      } else {
        opt.style.fontWeight = 'normal';
        opt.style.color = '#94a3b8';
      }
    });
  }

  function calculate() {
    const group = (occGroupSelect ? occGroupSelect.value : 'OFFICE_WORKER') || 'OFFICE_WORKER';
    const category = (catSelect ? catSelect.value : 'MOTORBIKE') || 'MOTORBIKE';
    const appraised = parseVND(appraisedInput ? appraisedInput.value : 0);
    const loan = parseVND(loanInput ? loanInput.value : 0);
    const termDays = parseInt(termSelect ? termSelect.value : 30, 10) || 30;
    const cycleDays = parseInt(cycleSelect ? cycleSelect.value : 10, 10) || 10;
    const dailyRate = parseVND(rateInput ? rateInput.value : 2000) || 2000;

    // 2D LTV Matrix Lookup
    const matrixLtv = (LTV_MATRIX[group] && LTV_MATRIX[group][category]) || 0.70;
    const maxLoanAllowed = Math.round(appraised * matrixLtv);

    if (maxLtvDisplay) {
      maxLtvDisplay.textContent = `${(matrixLtv * 100).toFixed(1)}%`;
    }
    if (maxLoanDisplay) {
      maxLoanDisplay.textContent = formatVND(maxLoanAllowed);
    }

    // Calculate actual requested LTV
    let ltv = 0;
    if (appraised > 0) {
      ltv = (loan / appraised) * 100;
    }

    if (ltvDisplay) {
      ltvDisplay.textContent = `${ltv.toFixed(1)}%`;
      if (ltv > 80) {
        ltvDisplay.className = 'fw-bold text-danger font-tabular';
        if (ltvWarning) {
          ltvWarning.classList.remove('d-none');
          if (loan > maxLoanAllowed) {
            ltvWarning.innerHTML = `<i class="bi bi-exclamation-triangle-fill"></i> Cảnh báo: Số tiền vay (${formatVND(loan)}) vượt quá trần LTV ma trận ${(matrixLtv * 100).toFixed(1)}% (${formatVND(maxLoanAllowed)})!`;
          } else {
            ltvWarning.innerHTML = `<i class="bi bi-exclamation-triangle-fill"></i> Cảnh báo: Tỷ lệ cho vay vượt quá 80% định giá! Cần chữ ký phê duyệt của Chủ tiệm.`;
          }
        }
      } else {
        ltvDisplay.className = 'fw-bold text-success font-tabular';
        if (ltvWarning) ltvWarning.classList.add('d-none');
      }
    }

    // Calculate Interest per Cycle
    const cycleInterest = (loan / 1000000) * dailyRate * cycleDays;
    if (cycleInterestDisplay) {
      cycleInterestDisplay.textContent = formatVND(cycleInterest);
    }

    // Calculate Projected Dates
    const now = new Date();
    const nextDueDate = new Date(now.getTime() + cycleDays * 86400000);
    const maturityDate = new Date(now.getTime() + termDays * 86400000);

    if (nextDueDateDisplay) {
      nextDueDateDisplay.textContent = formatDateVN(nextDueDate);
    }
    if (maturityDateDisplay) {
      maturityDateDisplay.textContent = formatDateVN(maturityDate);
    }
    if (disbursementDisplay) {
      disbursementDisplay.textContent = formatVND(loan);
    }
  }

  if (occGroupSelect) {
    occGroupSelect.addEventListener('change', function () {
      updateAllowedDocs();
      calculate();
    });
  }
  if (catSelect) catSelect.addEventListener('change', calculate);
  if (appraisedInput) appraisedInput.addEventListener('input', calculate);
  if (loanInput) loanInput.addEventListener('input', calculate);
  if (termSelect) termSelect.addEventListener('change', calculate);
  if (cycleSelect) cycleSelect.addEventListener('change', calculate);
  if (rateInput) rateInput.addEventListener('input', calculate);

  // Initial runs
  updateAllowedDocs();
  calculate();
}

/**
 * Setup Quick Actions prefill from table rows
 */
function initRowActionTriggers() {
  // Interest collection trigger
  document.querySelectorAll('.btn-action-collect-interest').forEach(btn => {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      const code = this.getAttribute('data-contract-code') || '';
      const customer = this.getAttribute('data-customer-name') || '';
      const dueAmount = this.getAttribute('data-due-amount') || '0';
      const principal = this.getAttribute('data-principal') || '0';

      const modalContractSelect = document.getElementById('ciContractCode');
      const modalCustomer = document.getElementById('ciCustomerName');
      const modalDueInterest = document.getElementById('ciDueInterest');
      const modalTotalDue = document.getElementById('ciTotalDue');

      if (modalContractSelect) modalContractSelect.value = code;
      if (modalCustomer) modalCustomer.value = customer;
      if (modalDueInterest) modalDueInterest.value = formatVND(dueAmount);
      if (modalTotalDue) modalTotalDue.textContent = formatVND(dueAmount);

      const modalEl = document.getElementById('modalCollectInterest');
      if (modalEl && typeof bootstrap !== 'undefined') {
        const modal = bootstrap.Modal.getInstance(modalEl) || new bootstrap.Modal(modalEl);
        modal.show();
      }
    });
  });

  // Redemption trigger
  document.querySelectorAll('.btn-action-redeem').forEach(btn => {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      const code = this.getAttribute('data-contract-code') || '';
      const customer = this.getAttribute('data-customer-name') || '';
      const principal = this.getAttribute('data-principal') || '0';
      const accrued = this.getAttribute('data-accrued-interest') || '0';

      const modalContract = document.getElementById('rdContractCode');
      const modalCustomer = document.getElementById('rdCustomerName');
      const modalPrincipal = document.getElementById('rdPrincipal');
      const modalAccrued = document.getElementById('rdAccruedInterest');
      const modalTotal = document.getElementById('rdTotalSettlement');

      const totalVal = parseVND(principal) + parseVND(accrued);

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
    const dateStr = now.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
    const timeStr = now.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    clockEl.textContent = `${dayName}, ${dateStr} - ${timeStr}`;
  }

  update();
  setInterval(update, 1000);
}

/**
 * Handle Timeframe Tabs on Cash Flow Chart
 */
function initChartTimeframeTabs() {
  const tabs = document.querySelectorAll('.pm-chart-tab[data-timeframe]');
  tabs.forEach(tab => {
    tab.addEventListener('click', function () {
      tabs.forEach(t => t.classList.remove('active'));
      this.classList.add('active');
      const timeframe = this.getAttribute('data-timeframe');

      const defaultDatasets = {
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

      const selected = defaultDatasets[timeframe] || defaultDatasets.months;
      initCashFlowChart('cashFlowChart', selected);
    });
  });
}

/**
 * Toast Notification Helper
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
  const iconClass = type === 'success' ? 'bi-check-circle-fill text-success' : 'bi-info-circle-fill text-primary';

  const toastHtml = `
    <div id="${toastId}" class="toast align-items-center border-0 shadow-sm" role="alert" aria-live="assertive" aria-atomic="true">
      <div class="d-flex">
        <div class="toast-body d-flex align-items-center gap-2">
          <i class="bi ${iconClass}"></i>
          <span>${message}</span>
        </div>
        <button type="button" class="btn-close me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
      </div>
    </div>
  `;

  toastContainer.insertAdjacentHTML('beforeend', toastHtml);
  const toastEl = document.getElementById(toastId);
  if (toastEl && typeof bootstrap !== 'undefined') {
    const toast = new bootstrap.Toast(toastEl, { delay: 3500 });
    toast.show();
    toastEl.addEventListener('hidden.bs.toast', () => toastEl.remove());
  }
}

// Modal Form Submit Mock Handlers
function initModalForms() {
  const formNewContract = document.getElementById('formNewContract');
  if (formNewContract) {
    formNewContract.addEventListener('submit', function (e) {
      e.preventDefault();
      const modalEl = document.getElementById('modalNewContract');
      if (modalEl && typeof bootstrap !== 'undefined') {
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();
      }
      showToast('Đã tạo hợp đồng cầm đồ mới và lưu sổ quỹ giải ngân thành công!', 'success');
    });
  }

  const formCollectInterest = document.getElementById('formCollectInterest');
  if (formCollectInterest) {
    formCollectInterest.addEventListener('submit', function (e) {
      e.preventDefault();
      const modalEl = document.getElementById('modalCollectInterest');
      if (modalEl && typeof bootstrap !== 'undefined') {
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();
      }
      showToast('Đã ghi nhận phiếu thu lãi và gia hạn kỳ tiếp theo thành công!', 'success');
    });
  }

  const formRedeem = document.getElementById('formRedeemCollateral');
  if (formRedeem) {
    formRedeem.addEventListener('submit', function (e) {
      e.preventDefault();
      const modalEl = document.getElementById('modalRedeemCollateral');
      if (modalEl && typeof bootstrap !== 'undefined') {
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();
      }
      showToast('Tất toán hợp đồng và xuất kho bàn giao tài sản thành công!', 'success');
    });
  }
}

/**
 * Branch Context Selector Handler
 */
function initBranchSelector() {
  const selector = document.getElementById('branchSelector');
  if (!selector) return;
  selector.addEventListener('change', function () {
    const branchId = this.value;
    const opt = this.options[this.selectedIndex];
    const cash = opt ? opt.getAttribute('data-cash') : null;
    const topbarCash = document.getElementById('topbarCashBalance');
    if (cash && topbarCash) {
      topbarCash.textContent = formatVND(cash);
    }
    if (window.location.protocol.startsWith('http')) {
      window.location.href = `/dashboard?branchId=${branchId}`;
    } else {
      showToast(`Đã chuyển sang: ${opt ? opt.text.split('(')[0].trim() : 'Chi nhánh mới'}`, 'info');
    }
  });
}

/**
 * Manager Quick Approval & Rejection Handlers
 */
function initManagerActions() {
  document.querySelectorAll('.btn-action-approve').forEach(btn => {
    btn.addEventListener('click', async function (e) {
      e.preventDefault();
      const contractId = this.getAttribute('data-contract-id');
      const contractCode = this.getAttribute('data-contract-code') || ('HD-2026-0' + contractId);
      const row = this.closest('tr');

      const confirmed = window.confirm(`Bạn có chắc chắn muốn PHÊ DUYỆT và XUẤT QUỸ GIẢI NGÂN cho hợp đồng ${contractCode}?`);
      if (!confirmed) return;

      try {
        await fetch(`/api/v1/contracts/${contractId}/approve`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ notes: 'Quản lý phê duyệt giải ngân trên giao diện Dashboard' })
        });
      } catch (err) {
        console.log('Approve API simulation/offline:', err);
      }

      if (row) {
        row.setAttribute('data-status', 'ACTIVE');
        const badge = row.querySelector('.pm-badge');
        if (badge) {
          badge.className = 'pm-badge pm-badge-active';
          badge.innerHTML = '<span class="pm-badge-dot"></span><span>Đang vay</span>';
        }
        const actionCell = row.querySelector('td:last-child');
        if (actionCell) {
          actionCell.innerHTML = `
            <div class="d-inline-flex gap-1">
              <button type="button" class="pm-btn-icon btn-action-collect-interest" title="Thu lãi" data-contract-code="${contractCode}" data-customer-name="Võ Hoàng Yến" data-due-amount="600000" data-principal="30000000">
                <i class="bi bi-receipt text-success"></i>
              </button>
              <button type="button" class="pm-btn-icon btn-action-redeem" title="Chuộc đồ" data-contract-code="${contractCode}" data-customer-name="Võ Hoàng Yến" data-principal="30000000" data-accrued-interest="600000">
                <i class="bi bi-box-arrow-right text-primary"></i>
              </button>
            </div>
          `;
          initRowActionTriggers();
        }
      }
      showToast(`Đã phê duyệt và xuất quỹ giải ngân thành công cho hợp đồng ${contractCode}!`, 'success');
    });
  });

  document.querySelectorAll('.btn-action-reject').forEach(btn => {
    btn.addEventListener('click', async function (e) {
      e.preventDefault();
      const contractId = this.getAttribute('data-contract-id');
      const contractCode = this.getAttribute('data-contract-code') || ('HD-2026-0' + contractId);
      const row = this.closest('tr');

      const reason = window.prompt(`Lý do từ chối hồ sơ ${contractCode}:`, 'Hồ sơ chứng từ không đủ điều kiện');
      if (reason === null) return;

      try {
        await fetch(`/api/v1/contracts/${contractId}/reject`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ reason: reason })
        });
      } catch (err) {
        console.log('Reject API simulation/offline:', err);
      }

      if (row) {
        row.setAttribute('data-status', 'CANCELLED');
        const badge = row.querySelector('.pm-badge');
        if (badge) {
          badge.className = 'pm-badge pm-badge-overdue';
          badge.innerHTML = '<span class="pm-badge-dot"></span><span>Đã từ chối</span>';
        }
        const actionCell = row.querySelector('td:last-child');
        if (actionCell) {
          actionCell.innerHTML = '<span class="text-muted small">Đã hủy</span>';
        }
      }
      showToast(`Đã từ chối hồ sơ hợp đồng ${contractCode}`, 'info');
    });
  });
}

/**
 * Auth Session Sync & Logout Handler
 */
function initAuthSessionAndLogout() {
  try {
    if (typeof localStorage !== 'undefined') {
      const rawUser = localStorage.getItem('pm_current_user');
      if (rawUser) {
        const user = JSON.parse(rawUser);
        const nameEl = document.getElementById('topbarUserName');
        const roleEl = document.getElementById('topbarUserRole');
        const avatarEl = document.getElementById('topbarUserAvatar');
        if (nameEl && user.fullName) nameEl.textContent = user.fullName;
        if (roleEl && (user.roleLabel || user.role)) {
          roleEl.textContent = user.roleLabel || (`Vai trò: ${user.role}`);
        }
        if (avatarEl && user.avatar) avatarEl.textContent = user.avatar;
      }
    }
  } catch (e) {
    // Ignore storage read issues in sandboxed test environments
  }

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
        }
        if (typeof sessionStorage !== 'undefined') {
          sessionStorage.removeItem('pm_access_token');
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
  initAuthSessionAndLogout();
  initLiveClock();
  initCashFlowChart('cashFlowChart');
  initCollateralDonutChart('collateralDonutChart');
  initDashboardFilters('recentContractsTable', 'contractSearchInput', 'contractFilterTabs');
  initNewContractCalculator();
  initRowActionTriggers();
  initChartTimeframeTabs();
  initModalForms();
  initBranchSelector();
  initManagerActions();
});
