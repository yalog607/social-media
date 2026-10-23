(function () {
  'use strict';

  // Khi modal mở lên, xử lý nội dung hoặc điền form
  document.addEventListener('show.bs.modal', async function (event) {
    const modal = event.target;
    if (modal.id === 'deleteGuestbookModal') {
      const trigger = event.relatedTarget;
      if (!trigger) return;
      const form = modal.querySelector('form');
      const url = trigger.getAttribute('data-delete-url') || trigger.dataset?.deleteUrl;
      if (form && url) {
        form.action = url;
      }
      const csrfInput = form ? form.querySelector('input[name="_csrf"]') : null;
      if (csrfInput && window.Aloute && typeof window.Aloute.csrfToken === 'function') {
        csrfInput.value = window.Aloute.csrfToken();
      }
      return;
    }
    if (modal.id === 'friendsModal' || modal.id === 'followersModal' || modal.id === 'followingModal' || modal.id === 'postDetailModal') {
      const listContainer = modal.querySelector('[data-list-container]');
      // Cho postDetailModal, URL được đặt trên nút kích hoạt (event.relatedTarget) thay vì trên modal
      const url = modal.id === 'postDetailModal' ? event.relatedTarget?.dataset?.postUrl : modal.dataset.listUrl;
      if (!listContainer || !url) return;

      listContainer.innerHTML = '<div class="text-center p-4 text-soft">Đang tải...</div>';
      try {
        const response = await window.Aloute.api(url, { headers: { Accept: 'text/html' } });
        if (!response.ok) throw new Error('HTTP ' + response.status);
        listContainer.innerHTML = await response.text();
        
        // Kích hoạt load bình luận (giống như trong feed.js) nếu có
        const commentsSec = listContainer.querySelector('[data-comments]');
        if (commentsSec && window.Aloute && window.Aloute.loadComments) {
            // Wait, Aloute might not expose loadComments directly, let's just emit a custom event or let it be handled.
            // feed.js listens to click on `data-toggle-comments`, so users can click it.
        }
      } catch (error) {
        listContainer.innerHTML = '<div class="text-center p-4 text-danger">Chưa tải được dữ liệu, vui lòng thử lại sau.</div>';
      }
    }
  });

})();
