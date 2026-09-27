/* ALOUTE — tiện ích dùng chung. JS thuần, không phụ thuộc thư viện. */
(function () {
  'use strict';

  function csrfToken() {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : '';
  }

  /**
   * fetch có sẵn CSRF header. Nếu gặp 401 (access token hết hạn) thì gọi POST /auth/refresh
   * rồi thử lại đúng một lần; refresh thất bại thì đưa về trang đăng nhập. Nếu gặp 403 (cookie CSRF
   * vừa được cấp lại đúng lúc gọi, ví dụ do một request khác vừa refresh phiên) thì đọc lại cookie và
   * thử lại đúng một lần trước khi trả lỗi thật.
   */
  async function api(url, options) {
    const opts = Object.assign({ credentials: 'same-origin' }, options);
    opts.headers = Object.assign({ 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' }, opts.headers);
    const method = (opts.method || 'GET').toUpperCase();
    const isWrite = method !== 'GET' && method !== 'HEAD';
    if (isWrite) {
      opts.headers['X-XSRF-TOKEN'] = csrfToken();
    }

    let response = await fetch(url, opts);
    if (response.status === 401 && !url.startsWith('/auth/')) {
      const refreshed = await fetch('/auth/refresh', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'X-XSRF-TOKEN': csrfToken(), 'X-Requested-With': 'XMLHttpRequest' },
      });
      if (refreshed.ok) {
        opts.headers['X-XSRF-TOKEN'] = csrfToken();
        response = await fetch(url, opts);
      } else {
        window.location.href = '/login?next=' + encodeURIComponent(location.pathname + location.search);
      }
    } else if (response.status === 403 && isWrite) {
      const retryToken = csrfToken();
      if (retryToken && retryToken !== opts.headers['X-XSRF-TOKEN']) {
        opts.headers['X-XSRF-TOKEN'] = retryToken;
        response = await fetch(url, opts);
      }
    }
    return response;
  }

  /** Toast nhỏ ở góc dưới. kind: ok | error | warn */
  function toast(message, kind) {
    const stack = document.getElementById('toast-stack');
    if (!stack) return;
    const el = document.createElement('div');
    el.className = 'alert-al toast-al' + (kind === 'error' ? ' alert-al--error' : kind === 'warn' ? ' alert-al--warn' : ' alert-al--ok');
    el.setAttribute('role', kind === 'error' ? 'alert' : 'status');
    const p = document.createElement('p');
    p.textContent = message;
    el.appendChild(p);
    stack.appendChild(el);
    setTimeout(() => el.remove(), 4500);
  }

  // Nút Hiện/Ẩn mật khẩu
  document.addEventListener('click', function (event) {
    const button = event.target.closest('[data-pw-toggle]');
    if (!button) return;
    const input = document.getElementById(button.getAttribute('aria-controls'));
    if (!input) return;
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    button.textContent = show ? 'Ẩn' : 'Hiện';
    button.setAttribute('aria-pressed', String(show));
  });

  // Chống bấm gửi form hai lần
  document.addEventListener('submit', function (event) {
    const form = event.target;
    if (!(form instanceof HTMLFormElement) || form.dataset.noLock !== undefined) return;
    const submit = form.querySelector('button[type="submit"]');
    if (submit && !form.dataset.submitted) {
      form.dataset.submitted = '1';
      setTimeout(() => { submit.disabled = true; }, 0);
    }
  });

  window.Aloute = { api: api, csrfToken: csrfToken, toast: toast };
})();
