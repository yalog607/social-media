(function () {
  'use strict';

  // Khi modal mở lên, fetch nội dung danh sách và chèn vào
  document.addEventListener('show.bs.modal', async function (event) {
    const modal = event.target;
    if (modal.id === 'friendsModal' || modal.id === 'followersModal' || modal.id === 'followingModal') {
      const listContainer = modal.querySelector('[data-list-container]');
      const url = modal.dataset.listUrl;
      if (!listContainer || !url) return;

      listContainer.innerHTML = '<div class="text-center p-4 text-soft">Đang tải...</div>';
      try {
        const response = await window.Aloute.api(url, { headers: { Accept: 'text/html' } });
        if (!response.ok) throw new Error('HTTP ' + response.status);
        listContainer.innerHTML = await response.text();
      } catch (error) {
        listContainer.innerHTML = '<div class="text-center p-4 text-danger">Chưa tải được danh sách, vui lòng thử lại sau.</div>';
      }
    }
  });

})();
