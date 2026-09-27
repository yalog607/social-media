/* ALOUTE — phóng to ảnh. Đặt data-lightbox="url ảnh gốc" lên bất kỳ phần tử nào (kèm role="button" tabindex="0"
   cho bàn phím) là bấm/Enter/Space vào nó sẽ mở ảnh cỡ lớn, có nút đóng, bấm ra ngoài hoặc nhấn Esc cũng đóng. */
(function () {
  'use strict';

  let overlay = null;

  function buildOverlay() {
    overlay = document.createElement('div');
    overlay.className = 'lightbox';
    overlay.hidden = true;
    overlay.innerHTML =
      '<button type="button" class="lightbox-close" aria-label="Đóng ảnh">×</button>' +
      '<img class="lightbox-img" alt="Ảnh cỡ lớn">';
    overlay.addEventListener('click', function (event) {
      if (event.target === overlay || event.target.classList.contains('lightbox-close')) closeLightbox();
    });
    document.body.appendChild(overlay);
  }

  function openLightbox(url) {
    if (!overlay) buildOverlay();
    overlay.querySelector('.lightbox-img').src = url;
    overlay.hidden = false;
    document.body.classList.add('lightbox-open');
    overlay.querySelector('.lightbox-close').focus();
  }

  function closeLightbox() {
    if (!overlay || overlay.hidden) return;
    overlay.hidden = true;
    overlay.querySelector('.lightbox-img').src = '';
    document.body.classList.remove('lightbox-open');
  }

  document.addEventListener('click', function (event) {
    const trigger = event.target.closest('[data-lightbox]');
    if (!trigger) return;
    openLightbox(trigger.dataset.lightbox);
  });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') {
      closeLightbox();
      return;
    }
    if (event.key !== 'Enter' && event.key !== ' ') return;
    const trigger = event.target.closest('[data-lightbox]');
    if (!trigger) return;
    event.preventDefault();
    openLightbox(trigger.dataset.lightbox);
  });
})();
