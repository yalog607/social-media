/* ALOUTE — xem trước và cắt ảnh đại diện/ảnh bìa trước khi lưu (chỉ dùng ở trang Cài đặt).
   Chọn ảnh -> mở hộp thoại kéo/phóng -> "Dùng ảnh này" vẽ đúng vùng đang thấy ra canvas, đóng gói lại thành
   File và thay vào input gốc (giống cách composer thay input[type=file] bằng DataTransfer), nên form vẫn gửi
   multipart bình thường, chỉ khác là ảnh đã được cắt sẵn đúng khung thay vì gửi nguyên file to. */
(function () {
  'use strict';

  const modalEl = document.getElementById('cropModal');
  const avatarInput = document.getElementById('avatar');
  const coverInput = document.getElementById('cover');
  if (!modalEl || (!avatarInput && !coverInput)) return;

  const CONFIGS = [
    { input: avatarInput, title: 'Chỉnh ảnh đại diện', circle: true,
      frameW: 220, frameH: 220, outW: 480, outH: 480, previewSelector: '#avatarPreview' },
    { input: coverInput, title: 'Chỉnh ảnh bìa', circle: false,
      frameW: 420, frameH: 140, outW: 1500, outH: 500, previewSelector: '#coverPreview' },
  ];

  const frame = modalEl.querySelector('[data-crop-frame]');
  const img = modalEl.querySelector('[data-crop-image]');
  const zoom = modalEl.querySelector('[data-crop-zoom]');
  const title = modalEl.querySelector('[data-crop-title]');
  const confirmButton = modalEl.querySelector('[data-crop-confirm]');
  img.draggable = false;

  let modalInstance = null;
  function modal() {
    // Khởi tạo trễ (chỉ khi thật sự cần) để không phụ thuộc thứ tự nạp so với bootstrap.bundle.min.js,
    // vì thẻ script của trang này nằm TRƯỚC thẻ script Bootstrap trong layout/base.html.
    if (!modalInstance) modalInstance = new window.bootstrap.Modal(modalEl);
    return modalInstance;
  }

  let active = null;
  let objectUrl = null;
  let natural = { w: 0, h: 0 };
  let offset = { x: 0, y: 0 };
  let drag = null;
  let confirmed = false;

  function applyFrameSize(config) {
    frame.style.width = config.frameW + 'px';
    frame.style.height = config.frameH + 'px';
    frame.classList.toggle('is-circle', config.circle);
  }

  function clampOffset() {
    const scale = Number(zoom.value);
    const w = natural.w * scale;
    const h = natural.h * scale;
    const minX = active.frameW - w;
    const minY = active.frameH - h;
    offset.x = Math.min(0, Math.max(minX, offset.x));
    offset.y = Math.min(0, Math.max(minY, offset.y));
  }

  function render() {
    const scale = Number(zoom.value);
    img.style.width = (natural.w * scale) + 'px';
    img.style.height = (natural.h * scale) + 'px';
    img.style.transform = 'translate(' + offset.x + 'px,' + offset.y + 'px)';
  }

  function openCropper(config, file) {
    active = config;
    confirmed = false;
    applyFrameSize(config);
    title.textContent = config.title;
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    objectUrl = URL.createObjectURL(file);
    img.onload = function () {
      natural = { w: img.naturalWidth, h: img.naturalHeight };
      // Nhỏ nhất để ảnh luôn phủ kín khung (không để lộ nền trống) — khớp "ảnh quá to thì cắt xén"
      const minScale = Math.max(config.frameW / natural.w, config.frameH / natural.h);
      zoom.min = String(minScale);
      zoom.max = String(minScale * 3);
      zoom.step = String((minScale * 3 - minScale) / 100 || 0.001);
      zoom.value = String(minScale);
      offset.x = (config.frameW - natural.w * minScale) / 2;
      offset.y = (config.frameH - natural.h * minScale) / 2;
      render();
      modal().show();
    };
    img.src = objectUrl;
  }

  CONFIGS.forEach(function (config) {
    if (!config.input) return;
    config.input.addEventListener('change', function () {
      const file = config.input.files && config.input.files[0];
      if (!file || file.type.indexOf('image/') !== 0) return;
      openCropper(config, file);
    });
  });

  frame.addEventListener('pointerdown', function (event) {
    drag = { x: event.clientX - offset.x, y: event.clientY - offset.y };
    frame.setPointerCapture(event.pointerId);
    event.preventDefault();
  });
  frame.addEventListener('pointermove', function (event) {
    if (!drag) return;
    offset.x = event.clientX - drag.x;
    offset.y = event.clientY - drag.y;
    clampOffset();
    render();
  });
  ['pointerup', 'pointercancel'].forEach(function (type) {
    frame.addEventListener(type, function () { drag = null; });
  });

  zoom.addEventListener('input', function () {
    clampOffset();
    render();
  });

  // Đóng hộp thoại mà không bấm "Dùng ảnh này" (bấm Hủy, hoặc Esc): bỏ file vừa chọn, tránh gửi nhầm ảnh gốc chưa cắt
  modalEl.addEventListener('hidden.bs.modal', function () {
    if (active && !confirmed) {
      active.input.value = '';
    }
  });

  confirmButton.addEventListener('click', function () {
    const config = active;
    const scale = Number(zoom.value);
    const ratio = config.outW / config.frameW; // = outH/frameH, cùng tỉ lệ với khung hiển thị

    const canvas = document.createElement('canvas');
    canvas.width = config.outW;
    canvas.height = config.outH;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(img, 0, 0, natural.w, natural.h,
        offset.x * ratio, offset.y * ratio, natural.w * scale * ratio, natural.h * scale * ratio);

    canvas.toBlob(function (blob) {
      if (!blob) return;
      const name = (config.input.id === 'avatar' ? 'avatar' : 'cover') + '.jpg';
      const file = new File([blob], name, { type: 'image/jpeg' });
      const transfer = new DataTransfer();
      transfer.items.add(file);
      config.input.files = transfer.files;
      updateLivePreview(config, blob);
      confirmed = true;
      modal().hide();
    }, 'image/jpeg', 0.9);
  });

  function updateLivePreview(config, blob) {
    const el = document.querySelector(config.previewSelector);
    if (!el) return;
    const url = URL.createObjectURL(blob);
    if (config.circle) {
      // #avatarPreview chỉ là khung bọc (display:contents) quanh <span class="avatar">; phải sửa nội dung
      // của chính khung avatar đó, không phải của khung bọc, nếu không mất luôn kiểu tròn/kích thước.
      const bubble = el.querySelector('.avatar') || el;
      bubble.innerHTML = '<img src="' + url + '" alt="">';
    } else {
      el.style.backgroundImage = 'url(' + url + ')';
    }
  }
})();
