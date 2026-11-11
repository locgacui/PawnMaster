/**
 * PawnMaster Login Terminal — Authentication Session Script
 * Adheres to "The Vault Ledger" (OPERATE Mode, Zero-Leakage):
 * - No hardcoded demo accounts or pre-populated credentials
 * - Strict authentication via /api/v1/auth/login
 * - Caps Lock detection, accessible password visibility toggle, and clean redirect to /dashboard
 */

function getRoleLabel(role) {
  switch (role) {
    case 'ADMIN':
      return 'Quản trị hệ thống (Admin)';
    case 'MANAGER':
      return 'Quản lý cửa hàng (Manager)';
    case 'ACCOUNTANT':
      return 'Kế toán sổ quỹ (Accountant)';
    case 'STAFF':
    default:
      return 'Giao dịch viên quầy (Staff)';
  }
}

function getInitials(fullName, role) {
  if (!fullName) return role === 'ADMIN' ? 'AD' : 'NV';
  const parts = fullName.trim().split(/\s+/);
  if (parts.length >= 2) {
    return (parts[parts.length - 2][0] + parts[parts.length - 1][0]).toUpperCase();
  }
  return fullName.slice(0, 2).toUpperCase();
}

function initLoginClock() {
  const clockEl = document.getElementById('loginLiveClock');
  if (!clockEl) return;

  function updateClock() {
    const now = new Date();
    const dd = String(now.getDate()).padStart(2, '0');
    const mm = String(now.getMonth() + 1).padStart(2, '0');
    const yyyy = now.getFullYear();
    const hh = String(now.getHours()).padStart(2, '0');
    const min = String(now.getMinutes()).padStart(2, '0');
    const ss = String(now.getSeconds()).padStart(2, '0');
    clockEl.textContent = `${dd}/${mm}/${yyyy} • ${hh}:${min}:${ss}`;
  }

  updateClock();
  setInterval(updateClock, 1000);
}

function showLoginAlert(message, type = 'error') {
  const banner = document.getElementById('loginAlertBanner');
  const msgEl = document.getElementById('loginAlertMessage');
  if (!banner || !msgEl) return;

  banner.classList.remove('d-none', 'pm-auth-alert-error', 'pm-auth-alert-success');
  banner.classList.add(type === 'success' ? 'pm-auth-alert-success' : 'pm-auth-alert-error');
  msgEl.textContent = message;
}

function hideLoginAlert() {
  const banner = document.getElementById('loginAlertBanner');
  if (banner) {
    banner.classList.add('d-none');
  }
}

function initPasswordToggle() {
  const toggleBtn = document.getElementById('togglePasswordBtn');
  const passwordInput = document.getElementById('loginPassword');
  const icon = document.getElementById('togglePasswordIcon');
  if (!toggleBtn || !passwordInput) return;

  toggleBtn.addEventListener('click', function () {
    const isPassword = passwordInput.getAttribute('type') === 'password';
    const nextType = isPassword ? 'text' : 'password';
    passwordInput.setAttribute('type', nextType);
    toggleBtn.setAttribute('aria-pressed', isPassword ? 'true' : 'false');
    toggleBtn.setAttribute('aria-label', isPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
    if (icon) {
      icon.className = isPassword ? 'bi bi-eye-slash' : 'bi bi-eye';
    }
    passwordInput.focus();
  });
}

function initCapsLockDetector() {
  const passwordInput = document.getElementById('loginPassword');
  const capsLockHint = document.getElementById('capsLockHint');
  if (!passwordInput || !capsLockHint) return;

  function checkCapsLock(event) {
    if (typeof event.getModifierState !== 'function') return;
    const isCapsOn = event.getModifierState('CapsLock');
    capsLockHint.classList.toggle('d-none', !isCapsOn);
  }

  passwordInput.addEventListener('keydown', checkCapsLock);
  passwordInput.addEventListener('keyup', checkCapsLock);
  passwordInput.addEventListener('blur', function () {
    capsLockHint.classList.add('d-none');
  });
}

function persistAuthSession(authData, rememberSession) {
  const token = authData.accessToken;
  const userSession = {
    userId: authData.userId,
    username: authData.username,
    fullName: authData.fullName,
    role: authData.role,
    roleLabel: getRoleLabel(authData.role),
    avatar: getInitials(authData.fullName, authData.role),
    branchId: authData.branchId || null,
    branchCode: authData.branchCode || '',
    branchName: authData.branchName || '',
    loginTime: new Date().toISOString()
  };

  try {
    sessionStorage.setItem('pm_access_token', token);
    sessionStorage.setItem('pm_current_user', JSON.stringify(userSession));
    if (rememberSession) {
      localStorage.setItem('pm_access_token', token);
      localStorage.setItem('pm_current_user', JSON.stringify(userSession));
    } else {
      localStorage.removeItem('pm_access_token');
      localStorage.removeItem('pm_current_user');
    }
  } catch (e) {
    // Ignore storage quota errors in restricted environments
  }

  try {
    const maxAgePart = rememberSession ? '; max-age=86400' : '';
    document.cookie = `pm_token=${encodeURIComponent(token)}; path=/${maxAgePart}; SameSite=Lax`;
  } catch (e) {
    // Ignore cookie errors in restricted environments
  }

  return userSession;
}

function navigateToDashboard(userSession) {
  const isAdmin = userSession && userSession.role === 'ADMIN';
  const branchParam = (!isAdmin && userSession && userSession.branchId)
    ? `?branchId=${encodeURIComponent(userSession.branchId)}`
    : '';
  window.location.href = `/dashboard${branchParam}`;
}

function setSubmittingState(isSubmitting) {
  const submitBtn = document.getElementById('loginSubmitBtn');
  const submitText = document.getElementById('loginSubmitText');
  const submitIcon = document.getElementById('loginSubmitIcon');

  if (submitBtn) submitBtn.disabled = isSubmitting;
  if (submitText) submitText.textContent = isSubmitting ? 'Đang xác thực...' : 'Đăng Nhập';
  if (submitIcon) submitIcon.className = isSubmitting ? 'bi bi-arrow-repeat' : 'bi bi-box-arrow-in-right';
}

function initLoginForm() {
  const form = document.getElementById('loginForm');
  const usernameInput = document.getElementById('loginUsername');
  const passwordInput = document.getElementById('loginPassword');
  const rememberCheck = document.getElementById('rememberShiftCheck');

  if (!form || !usernameInput || !passwordInput) return;

  usernameInput.addEventListener('input', hideLoginAlert);
  passwordInput.addEventListener('input', hideLoginAlert);

  form.addEventListener('submit', async function (e) {
    e.preventDefault();
    hideLoginAlert();

    const username = (usernameInput.value || '').trim();
    const password = passwordInput.value || '';
    const rememberSession = rememberCheck ? Boolean(rememberCheck.checked) : true;

    if (!username || !password) {
      showLoginAlert('Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.', 'error');
      if (!username) {
        usernameInput.focus();
      } else {
        passwordInput.focus();
      }
      return;
    }

    setSubmittingState(true);

    try {
      const response = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });

      const payload = await response.json().catch(() => null);

      if (response.ok && payload && payload.success && payload.data && payload.data.accessToken) {
        const session = persistAuthSession(payload.data, rememberSession);
        showLoginAlert('Xác thực thành công. Đang chuyển vào Bảng Điều Khiển...', 'success');
        setTimeout(() => navigateToDashboard(session), 200);
        return;
      }

      const errorMessage = (payload && payload.message)
        ? payload.message
        : 'Tên đăng nhập hoặc mật khẩu không chính xác.';
      showLoginAlert(errorMessage, 'error');
      passwordInput.value = '';
      passwordInput.focus();
      setSubmittingState(false);
    } catch (networkErr) {
      showLoginAlert('Không thể kết nối đến máy chủ xác thực. Vui lòng thử lại.', 'error');
      setSubmittingState(false);
    }
  });
}

document.addEventListener('DOMContentLoaded', function () {
  initLoginClock();
  initPasswordToggle();
  initCapsLockDetector();
  initLoginForm();
});
