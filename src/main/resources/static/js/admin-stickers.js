(function () {
  'use strict';
  var main = document.getElementById('sticker-admin');
  if (!main) return;
  var confirmElement = document.getElementById('stickerConfirmModal');
  var pendingForm = null;
  if (confirmElement && window.bootstrap) {
    var confirmModal = bootstrap.Modal.getOrCreateInstance(confirmElement);
    main.addEventListener('submit', function (event) {
      var target = event.target.closest('form[data-sticker-confirm]');
      if (!target) return;
      event.preventDefault(); pendingForm = target;
      document.getElementById('stickerConfirmTitle').textContent = target.dataset.confirmTitle;
      document.getElementById('stickerConfirmMessage').textContent = target.dataset.confirmMessage;
      document.getElementById('stickerConfirmAction').textContent = target.dataset.confirmAction;
      confirmModal.show();
    });
    document.getElementById('stickerConfirmAction').addEventListener('click', function () {
      if (!pendingForm) return;
      var form = pendingForm; pendingForm = null;
      this.disabled = true; form.submit();
    });
    confirmElement.addEventListener('hidden.bs.modal', function () {
      pendingForm = null; document.getElementById('stickerConfirmAction').disabled = false;
    });
  } else {
    main.addEventListener('submit', function (event) {
      var target = event.target.closest('form[data-sticker-confirm]');
      if (target && !window.confirm(target.dataset.confirmMessage)) event.preventDefault();
    });
  }
  var files = document.getElementById('sticker-files');
  var preview = document.getElementById('sticker-upload-preview');
  var error = document.getElementById('sticker-upload-error');
  var imageUrls = [];
  files.addEventListener('change', function () {
    imageUrls.forEach(URL.revokeObjectURL); imageUrls = []; preview.replaceChildren(); error.textContent = '';
    var chosen = Array.from(files.files);
    if (chosen.length > 10 || chosen.length + Number(main.dataset.stickerCount) > 50) {
      error.textContent = 'Tối đa 10 ảnh mỗi lần tải và 50 ảnh mỗi bộ.'; files.value = ''; return;
    }
    if (chosen.some(function (file) { return file.size > 10 * 1024 * 1024; })) {
      error.textContent = 'Mỗi ảnh tối đa 10 MB.'; files.value = ''; return;
    }
    chosen.forEach(function (file, index) {
      var card = document.createElement('div');
      var img = document.createElement('img'); img.src = URL.createObjectURL(file); imageUrls.push(img.src); img.alt = 'Ảnh sẽ tải lên ' + (index + 1);
      var label = document.createElement('label'); label.textContent = 'Tên nhãn dán ' + (index + 1); label.htmlFor = 'upload-name-' + index;
      var name = document.createElement('input'); name.type = 'text'; name.name = 'names'; name.id = label.htmlFor;
      name.className = 'input-al'; name.maxLength = 100; name.required = true; name.value = file.name.replace(/\.[^.]+$/, '').slice(0, 100);
      card.append(img, label, name); preview.appendChild(card);
    });
  });
  window.addEventListener('pagehide', function () { imageUrls.forEach(URL.revokeObjectURL); });

  var form = document.getElementById('sticker-grant-form');
  if (!form) return;
  var search = document.getElementById('sticker-recipient-search');
  var results = document.getElementById('sticker-recipient-results');
  var selectedBox = document.getElementById('sticker-selected-recipients');
  var status = document.getElementById('sticker-grant-status');
  var submit = document.getElementById('sticker-grant-button');
  var selected = new Map(), timer, generation = 0;
  function renderSelection() {
    selectedBox.replaceChildren(); submit.disabled = selected.size === 0;
    if (!selected.size) { selectedBox.textContent = 'Chưa chọn người nhận.'; return; }
    selected.forEach(function (user, id) {
      var chip = document.createElement('button'); chip.type = 'button'; chip.className = 'btn-al btn-al--sm btn-al--white';
      chip.textContent = user.displayName + ' (@' + user.username + ') ×'; chip.setAttribute('aria-label', 'Bỏ chọn ' + user.username);
      chip.addEventListener('click', function () { selected.delete(id); renderSelection(); }); selectedBox.appendChild(chip);
    });
  }
  search.addEventListener('input', function () {
    clearTimeout(timer); var request = ++generation; var q = search.value.trim();
    if (q.length < 2) { results.replaceChildren(); return; }
    timer = setTimeout(async function () {
      results.textContent = 'Đang tìm…';
      try {
        var response = await window.Aloute.api('/admin/stickers/recipients?q=' + encodeURIComponent(q));
        if (!response.ok) throw new Error('search');
        var users = await response.json(); if (request !== generation) return;
        results.replaceChildren(); if (!users.length) results.textContent = 'Không tìm thấy tài khoản hoạt động.';
        users.forEach(function (user) {
          var button = document.createElement('button'); button.type = 'button'; button.className = 'sticker-recipient-option';
          button.textContent = user.displayName + ' · @' + user.username;
          button.addEventListener('click', function () {
            if (selected.size >= 100 && !selected.has(user.id)) { status.textContent = 'Mỗi lần cấp tối đa 100 người.'; return; }
            selected.set(user.id, user); renderSelection();
          }); results.appendChild(button);
        });
      } catch (e) { if (request === generation) results.textContent = 'Chưa tìm được người dùng. Thử nhập lại nhé.'; }
    }, 300);
  });
  form.addEventListener('submit', async function (event) {
    event.preventDefault(); if (!selected.size || !form.reportValidity()) return;
    submit.disabled = true; status.textContent = 'Đang cấp quà…';
    var body = new FormData(form); selected.forEach(function (user, id) { body.append('recipientIds', id); });
    try {
      var response = await window.Aloute.api(form.action, { method: 'POST', body: body });
      var result = await response.json();
      if (!response.ok) throw new Error(result.message || 'Chưa cấp được quà. Danh sách đã chọn được giữ lại.');
      window.location.assign(result.redirectUrl);
    } catch (e) { status.textContent = e.message || 'Chưa cấp được quà. Hãy thử lại.'; submit.disabled = false; }
  });
  renderSelection();
})();
