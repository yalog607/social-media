/* ALOUTE: gợi ý khi gõ @ để nhắc tên. Gắn vào mọi <textarea data-mention-url="..."> (URL trả JSON [{username, displayName, nickname?}]).
   Gõ "@" rồi vài chữ -> hiện danh sách; ↑/↓ chọn, Enter/Tab hoặc bấm để chèn "@username ", Esc để đóng. */
(function () {
  'use strict';

  const cache = new Map(); // url -> Promise<người[]>
  let menu = null;
  let active = null; // { textarea, start, items, index }

  function load(url) {
    if (!cache.has(url)) {
      cache.set(url, window.Aloute.api(url, { headers: { Accept: 'application/json' } })
        .then(function (response) { if (!response.ok) throw new Error('HTTP ' + response.status); return response.json(); })
        .catch(function (error) { cache.delete(url); return []; }));
    }
    return cache.get(url);
  }

  function ensureMenu() {
    if (menu) return menu;
    menu = document.createElement('ul');
    menu.className = 'mention-menu';
    menu.setAttribute('role', 'listbox');
    menu.hidden = true;
    document.body.appendChild(menu);
    // mousedown (không phải click) để không làm textarea mất focus trước khi chọn
    menu.addEventListener('mousedown', function (event) {
      const item = event.target.closest('[data-index]');
      if (!item || !active) return;
      event.preventDefault();
      choose(Number(item.dataset.index));
    });
    return menu;
  }

  function close() {
    if (menu) menu.hidden = true;
    active = null;
  }

  // Tìm "@từ" ngay trước con trỏ; trả về {start, query} hoặc null
  function tokenBeforeCaret(textarea) {
    const caret = textarea.selectionStart;
    const before = textarea.value.slice(0, caret);
    const match = /(^|[^\p{L}\p{N}_.@&])@([A-Za-z0-9_.]{0,24})$/u.exec(before);
    if (!match) return null;
    return { start: caret - match[2].length - 1, query: match[2].toLowerCase() };
  }

  function render() {
    const el = ensureMenu();
    el.textContent = '';
    active.items.forEach(function (person, index) {
      const li = document.createElement('li');
      li.dataset.index = String(index);
      li.setAttribute('role', 'option');
      li.className = 'mention-item' + (index === active.index ? ' is-active' : '');
      const name = document.createElement('strong');
      name.textContent = person.nickname || person.displayName;
      const handle = document.createElement('span');
      handle.textContent = '@' + person.username;
      li.appendChild(name);
      li.appendChild(handle);
      el.appendChild(li);
    });
    const rect = active.textarea.getBoundingClientRect();
    const height = Math.min(active.items.length, 5) * 40 + 8;
    const below = window.innerHeight - rect.bottom > height + 8;
    el.style.left = Math.max(8, rect.left) + 'px';
    el.style.width = Math.min(rect.width, 320) + 'px';
    el.style.top = (below ? rect.bottom + 4 : rect.top - height - 4) + 'px';
    el.hidden = false;
  }

  function choose(index) {
    const person = active.items[index];
    const textarea = active.textarea;
    const caret = textarea.selectionStart;
    const insert = '@' + person.username + ' ';
    textarea.value = textarea.value.slice(0, active.start) + insert + textarea.value.slice(caret);
    const position = active.start + insert.length;
    textarea.setSelectionRange(position, position);
    textarea.dispatchEvent(new Event('input', { bubbles: true }));
    close();
    textarea.focus();
  }

  async function update(textarea) {
    const token = tokenBeforeCaret(textarea);
    if (!token) return close();
    const people = await load(textarea.dataset.mentionUrl);
    const again = tokenBeforeCaret(textarea); // người dùng có thể đã gõ tiếp trong lúc chờ tải
    if (!again) return close();
    const query = again.query;
    const items = people.filter(function (person) {
      return person.username.toLowerCase().indexOf(query) === 0
        || (person.displayName || '').toLowerCase().indexOf(query) >= 0
        || (person.nickname || '').toLowerCase().indexOf(query) >= 0;
    }).slice(0, 8);
    if (!items.length) return close();
    active = { textarea: textarea, start: again.start, items: items, index: 0 };
    render();
  }

  document.addEventListener('input', function (event) {
    const textarea = event.target;
    if (textarea.matches && textarea.matches('textarea[data-mention-url]')) update(textarea);
  });

  // Pha capture để chạy trước phím Enter gửi tin của chat.js
  document.addEventListener('keydown', function (event) {
    if (!active || !menu || menu.hidden || event.target !== active.textarea) return;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const step = event.key === 'ArrowDown' ? 1 : -1;
      active.index = (active.index + step + active.items.length) % active.items.length;
      render();
    } else if (event.key === 'Enter' || event.key === 'Tab') {
      event.preventDefault();
      event.stopImmediatePropagation();
      choose(active.index);
    } else if (event.key === 'Escape') {
      event.preventDefault();
      close();
    }
  }, true);

  document.addEventListener('focusout', function (event) {
    if (event.target.matches && event.target.matches('textarea[data-mention-url]')) setTimeout(close, 120);
  });
  window.addEventListener('scroll', close, true);
  window.addEventListener('resize', close);
})();
