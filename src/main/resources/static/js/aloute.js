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

  /** Kết quả thao tác, mới nhất trên cùng. kind: ok | success | error | warn */
  function toast(message, kind) {
    const stack = document.getElementById('toast-stack');
    if (!stack) return;
    const el = document.createElement('div');
    el.className = 'alert-al toast-al' + (kind === 'error' ? ' alert-al--error' : kind === 'warn' ? ' alert-al--warn' : ' alert-al--ok');
    el.setAttribute('role', kind === 'error' ? 'alert' : 'status');
    el.setAttribute('aria-atomic', 'true');
    const p = document.createElement('p');
    p.textContent = message;
    el.appendChild(p);
    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'toast-close';
    close.setAttribute('aria-label', 'Đóng thông báo');
    close.textContent = '×';
    el.appendChild(close);

    const previousFocus = document.activeElement;
    let remaining = 4500;
    let timer = null;
    let startedAt = 0;
    let hovered = false;
    let focused = false;
    let dismissed = false;

    function pause() {
      if (timer === null) return;
      clearTimeout(timer);
      timer = null;
      remaining = Math.max(0, remaining - (performance.now() - startedAt));
    }
    function resume() {
      if (dismissed || hovered || focused || timer !== null) return;
      startedAt = performance.now();
      timer = setTimeout(dismiss, remaining);
    }
    function dismiss() {
      if (dismissed) return;
      dismissed = true;
      pause();
      function remove() {
        if (!el.isConnected) return;
        const restoreFocus = el.contains(document.activeElement);
        el.remove();
        if (restoreFocus && previousFocus && previousFocus.isConnected) previousFocus.focus({ preventScroll: true });
      }
      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) { remove(); return; }
      el.classList.add('is-dismissing');
      el.addEventListener('animationend', function (event) { if (event.target === el) remove(); });
      setTimeout(remove, 220);
    }
    close.addEventListener('click', dismiss);
    el.addEventListener('pointerenter', function (event) { if (event.pointerType !== 'touch') { hovered = true; pause(); } });
    el.addEventListener('pointerleave', function (event) { if (event.pointerType !== 'touch') { hovered = false; resume(); } });
    el.addEventListener('focusin', function () { focused = true; pause(); });
    el.addEventListener('focusout', function (event) { focused = el.contains(event.relatedTarget); if (!focused) resume(); });

    let gesture = null;
    el.addEventListener('touchstart', function (event) {
      if (event.touches.length !== 1) { gesture = null; return; }
      // Theo dõi cả đoạn nội dung đang cuộn và nút đóng; chạm thường vẫn dùng ×.
      gesture = { x: event.touches[0].clientX, y: event.touches[0].clientY };
    }, { passive: true });
    el.addEventListener('touchmove', function (event) {
      if (!gesture || event.touches.length !== 1) return;
      const dx = event.touches[0].clientX - gesture.x;
      const dy = event.touches[0].clientY - gesture.y;
      if (dy < -8 && Math.abs(dy) > Math.abs(dx) * 1.25 && event.cancelable) event.preventDefault();
    }, { passive: false });
    el.addEventListener('touchend', function (event) {
      if (!gesture || !event.changedTouches.length) return;
      const dx = event.changedTouches[0].clientX - gesture.x;
      const dy = event.changedTouches[0].clientY - gesture.y;
      gesture = null;
      if (dy < -40 && Math.abs(dy) > Math.abs(dx) * 1.25) dismiss();
    });
    el.addEventListener('touchcancel', function () { gesture = null; });
    stack.prepend(el);
    resume();
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

  // Chỉ chuyển flash của layout, không lấy field/global form errors.
  document.querySelectorAll('[data-flash-toast]').forEach(function (flash) {
    toast(flash.textContent.trim(), flash.dataset.flashToast);
    flash.remove();
  });
})();
