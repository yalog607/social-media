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

  // ---------- Hộp thoại sửa/xóa dùng chung: điền dữ liệu của bài vừa bấm ----------
  document.addEventListener('show.bs.modal', function (event) {
    const modal = event.target;
    const trigger = event.relatedTarget;
    if (!trigger || !trigger.dataset.postId) return;
    const form = modal.querySelector('form');
    if (modal.id === 'editPostModal') {
      form.action = '/posts/' + trigger.dataset.postId + '/edit';
      form.elements.content.value = trigger.dataset.content || '';
      form.elements.visibility.value = trigger.dataset.visibility || 'PUBLIC';
    } else if (modal.id === 'deletePostModal') {
      form.action = '/posts/' + trigger.dataset.postId + '/delete';
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
