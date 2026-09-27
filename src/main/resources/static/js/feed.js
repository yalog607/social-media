/* ALOUTE: bảng tin. JS thuần: tải thêm bài, ô đăng bài (xem trước + kiểm tra trước khi tải lên), hộp thoại sửa/xóa. */
(function () {
  'use strict';

  const MB = 1024 * 1024;

  // ---------- Nút "Xem thêm": lấy mảnh HTML của trang kế và thay chỗ nút ----------
  document.addEventListener('click', async function (event) {
    const button = event.target.closest('[data-more-url]');
    if (!button) return;
    const label = button.textContent;
    button.disabled = true;
    button.textContent = 'Đang tải…';
    try {
      const response = await window.Aloute.api(button.dataset.moreUrl, { headers: { Accept: 'text/html' } });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const template = document.createElement('template');
      template.innerHTML = await response.text();
      button.closest('.feed-more').replaceWith(template.content);
    } catch (error) {
      button.disabled = false;
      button.textContent = label;
      window.Aloute.toast('Chưa tải thêm được, thử lại nhé.', 'error');
    }
  });

  // ---------- Hộp thoại sửa/xóa/chia sẻ dùng chung: điền dữ liệu của bài vừa bấm ----------
  document.addEventListener('show.bs.modal', function (event) {
    const modal = event.target;
    const trigger = event.relatedTarget;
    if (!trigger || !trigger.dataset.postId) return;
    const form = modal.querySelector('form');
    // Đọc lại cookie CSRF ngay lúc mở hộp thoại: trang có thể đã mở từ lâu, và giá trị in sẵn khi tải trang
    // đôi khi bị các request tài nguyên tĩnh chạy song song ghi đè ngay sau đó (xem CsrfCookieFilter).
    const csrfInput = form.querySelector('input[name="_csrf"]');
    if (csrfInput) {
      csrfInput.value = window.Aloute.csrfToken();
    }
    if (modal.id === 'editPostModal') {
      form.action = '/posts/' + trigger.dataset.postId + '/edit';
      form.elements.content.value = trigger.dataset.content || '';
      form.elements.visibility.value = trigger.dataset.visibility || 'PUBLIC';
    } else if (modal.id === 'deletePostModal') {
      form.action = '/posts/' + trigger.dataset.postId + '/delete';
    } else if (modal.id === 'sharePostModal') {
      form.action = '/posts/' + trigger.dataset.postId + '/share';
      form.elements.caption.value = '';
      const copyButton = modal.querySelector('[data-copy-link]');
      copyButton.dataset.url = window.location.origin + '/posts/' + trigger.dataset.postId;
    }
  });

  // ---------- Sao chép liên kết bài viết (hộp thoại chia sẻ) ----------
  document.addEventListener('click', function (event) {
    const button = event.target.closest('[data-copy-link]');
    if (!button || !button.dataset.url) return;
    navigator.clipboard.writeText(button.dataset.url)
      .then(function () { window.Aloute.toast('Đã sao chép liên kết!', 'ok'); })
      .catch(function () { window.Aloute.toast('Chưa sao chép được, thử lại nhé.', 'error'); });
  });

  // ---------- Cảm xúc: bấm để thả/đổi/bỏ, cập nhật số đếm ngay không cần tải lại trang ----------
  document.addEventListener('click', async function (event) {
    const chip = event.target.closest('.reaction-chip');
    if (!chip || chip.disabled) return;
    const actions = chip.closest('[data-post-id]');
    const postId = actions.dataset.postId;
    const type = chip.dataset.type;
    try {
      const response = await window.Aloute.api('/api/posts/' + postId + '/reaction?type=' + type, { method: 'POST' });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const data = await response.json();
      actions.querySelectorAll('.reaction-chip').forEach(function (item) {
        const count = data.counts[item.dataset.type] || 0;
        item.querySelector('.reaction-count').textContent = count > 0 ? String(count) : '';
        const active = data.mine === item.dataset.type;
        item.classList.toggle('is-active', active);
        item.setAttribute('aria-pressed', String(active));
      });
    } catch (error) {
      window.Aloute.toast('Chưa thả được cảm xúc, thử lại nhé.', 'error');
    }
  });

  // ---------- Bình luận: mở/đóng, tải danh sách, thêm, trả lời, xóa ----------
  function commentSectionOf(el) {
    return el.closest('.post').querySelector('.post-comments');
  }

  async function loadComments(section, url) {
    const list = section.querySelector('[data-comment-list]');
    try {
      const response = await window.Aloute.api(url, { headers: { Accept: 'text/html' } });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      list.innerHTML = await response.text();
    } catch (error) {
      window.Aloute.toast('Chưa tải được bình luận, thử lại nhé.', 'error');
    }
  }

  document.addEventListener('click', function (event) {
    const toggle = event.target.closest('[data-toggle-comments]');
    if (!toggle) return;
    const section = commentSectionOf(toggle);
    const wasHidden = section.hidden;
    section.hidden = !wasHidden;
    if (wasHidden && !section.dataset.loaded) {
      section.dataset.loaded = 'true';
      loadComments(section, toggle.dataset.commentsUrl);
    }
  });

  document.addEventListener('click', function (event) {
    const replyButton = event.target.closest('[data-reply-to]');
    if (!replyButton) return;
    const section = commentSectionOf(replyButton);
    const form = section.querySelector('.comment-form');
    if (!form) return;
    form.dataset.parentId = replyButton.dataset.parentId;
    const textarea = form.elements.content;
    textarea.placeholder = 'Trả lời ' + replyButton.dataset.name + '…';
    textarea.focus();
  });

  document.addEventListener('click', async function (event) {
    const deleteButton = event.target.closest('[data-delete-comment]');
    if (!deleteButton) return;
    const section = commentSectionOf(deleteButton);
    try {
      const response = await window.Aloute.api('/api/comments/' + deleteButton.dataset.commentId + '/delete', { method: 'POST' });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const toggle = section.closest('.post').querySelector('[data-toggle-comments]');
      await loadComments(section, toggle.dataset.commentsUrl);
    } catch (error) {
      window.Aloute.toast('Chưa xóa được bình luận, thử lại nhé.', 'error');
    }
  });

  document.addEventListener('submit', async function (event) {
    const form = event.target.closest('.comment-form');
    if (!form) return;
    event.preventDefault();
    const textarea = form.elements.content;
    const content = textarea.value.trim();
    if (!content) return;
    const section = form.closest('.post-comments');
    const body = new URLSearchParams({ content: content });
    if (form.dataset.parentId) {
      body.set('parentId', form.dataset.parentId);
    }
    try {
      const response = await window.Aloute.api(form.dataset.commentsUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body.toString(),
      });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      textarea.value = '';
      textarea.placeholder = 'Viết bình luận...';
      delete form.dataset.parentId;
      await loadComments(section, form.dataset.commentsUrl);
    } catch (error) {
      window.Aloute.toast('Chưa gửi được bình luận, thử lại nhé.', 'error');
    }
  });

  // ---------- Ô đăng bài ----------
  const composer = document.querySelector('[data-composer]');
  if (composer) initComposer(composer);

  function initComposer(form) {
    const text = form.querySelector('textarea[name="content"]');
    const counter = form.querySelector('[data-count]');
    const counterBox = counter.parentElement;
    const imageInput = form.querySelector('input[name="images"]');
    const videoInput = form.querySelector('input[name="video"]');
    const previews = form.querySelector('[data-previews]');
    const errorBox = form.querySelector('[data-composer-error]');
    const submit = form.querySelector('[data-composer-submit]');

    const maxChars = Number(form.dataset.maxChars);
    const maxImages = Number(form.dataset.maxImages);
    const maxImageBytes = Number(form.dataset.maxImageMb) * MB;
    const maxVideoBytes = Number(form.dataset.maxVideoMb) * MB;
    const imageTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
    const videoTypes = ['video/mp4', 'video/webm'];

    let images = [];
    let video = null;
    let objectUrls = [];

    function showError(message) {
      errorBox.textContent = message || '';
      errorBox.hidden = !message;
    }

    // input[type=file] chỉ ghi được qua DataTransfer nên phải dựng lại danh sách mỗi lần thêm/bớt
    function syncInputs() {
      const imageTransfer = new DataTransfer();
      images.forEach(function (file) { imageTransfer.items.add(file); });
      imageInput.files = imageTransfer.files;
      const videoTransfer = new DataTransfer();
      if (video) videoTransfer.items.add(video);
      videoInput.files = videoTransfer.files;
    }

    function refresh() {
      objectUrls.forEach(URL.revokeObjectURL);
      objectUrls = [];
      previews.textContent = '';
      images.forEach(function (file, index) { previews.appendChild(preview(file, false, function () { images.splice(index, 1); refresh(); })); });
      if (video) previews.appendChild(preview(video, true, function () { video = null; refresh(); }));
      syncInputs();
      const length = Array.from(text.value).length; // đếm ký tự (emoji tính là 1), khớp giới hạn của máy chủ
      counter.textContent = String(length);
      counterBox.classList.toggle('is-near', length > maxChars * 0.9);
      submit.disabled = length === 0 && images.length === 0 && !video;
    }

    function preview(file, isVideo, onRemove) {
      const box = document.createElement('div');
      box.className = 'preview';
      const media = document.createElement(isVideo ? 'video' : 'img');
      const url = URL.createObjectURL(file);
      objectUrls.push(url);
      media.src = url;
      if (isVideo) { media.muted = true; media.preload = 'metadata'; } else { media.alt = 'Ảnh sẽ đăng'; }
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'preview-remove';
      remove.setAttribute('aria-label', 'Bỏ ' + (isVideo ? 'video' : 'ảnh') + ' này');
      remove.textContent = '×';
      remove.addEventListener('click', onRemove);
      box.append(media, remove);
      return box;
    }

    imageInput.addEventListener('change', function () {
      showError('');
      const chosen = Array.from(imageInput.files);
      if (video && chosen.length) {
        showError('Mỗi bài chỉ đăng ảnh hoặc video. Hãy bỏ video trước nếu muốn đăng ảnh.');
      } else {
        for (const file of chosen) {
          if (images.length >= maxImages) { showError('Mỗi bài tối đa ' + maxImages + ' ảnh.'); break; }
          if (!imageTypes.includes(file.type)) { showError('Chỉ nhận ảnh JPG, PNG, GIF hoặc WEBP.'); continue; }
          if (file.size > maxImageBytes) { showError('Mỗi ảnh tối đa ' + (maxImageBytes / MB) + ' MB.'); continue; }
          images.push(file);
        }
      }
      refresh();
    });

    videoInput.addEventListener('change', function () {
      showError('');
      const file = videoInput.files[0];
      if (!file) { video = null; refresh(); return; }
      if (images.length) {
        showError('Mỗi bài chỉ đăng ảnh hoặc video. Hãy bỏ ảnh trước nếu muốn đăng video.');
      } else if (!videoTypes.includes(file.type)) {
        showError('Chỉ nhận video MP4 hoặc WEBM.');
      } else if (file.size > maxVideoBytes) {
        showError('Video tối đa ' + (maxVideoBytes / MB) + ' MB.');
      } else {
        video = file;
      }
      refresh();
    });

    text.addEventListener('input', refresh);
    refresh();
  }
})();
