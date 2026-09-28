/* ALOUTE — thay danh sách xổ xuống mặc định của trình duyệt (không thể tùy biến hình thức) bằng một bảng
   chọn tự vẽ theo đúng phong cách của app. <select> gốc vẫn nằm trong DOM (ẩn hình, không ẩn hẳn) để form
   nộp lên bình thường và bàn phím/trình đọc màn hình vẫn dùng được select thật khi cần.
   Dùng: thêm data-custom-select vào bất kỳ <select class="input-al"> nào muốn thay giao diện. */
(function () {
  'use strict';

  function enhance(select) {
    if (select.dataset.customSelectReady) return;
    select.dataset.customSelectReady = '1';

    const wrapper = document.createElement('div');
    wrapper.className = 'csel';
    select.parentNode.insertBefore(wrapper, select);
    wrapper.appendChild(select);
    select.classList.add('csel-native');
    select.tabIndex = -1;

    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'input-al csel-btn';
    wrapper.appendChild(button);

    const panel = document.createElement('div');
    panel.className = 'csel-panel';
    panel.hidden = true;
    wrapper.appendChild(panel);

    function label() {
      const opt = select.options[select.selectedIndex];
      return opt ? opt.textContent : '';
    }

    function renderOptions() {
      panel.innerHTML = '';
      Array.from(select.options).forEach(function (opt) {
        if (opt.disabled) return; // placeholder "Chọn..." không cần chọn lại được
        const item = document.createElement('button');
        item.type = 'button';
        item.className = 'csel-option';
        item.textContent = opt.textContent;
        if (opt.selected) item.classList.add('is-selected');
        item.addEventListener('click', function () {
          select.value = opt.value;
          select.dispatchEvent(new Event('change', { bubbles: true }));
          button.textContent = label();
          close();
        });
        panel.appendChild(item);
      });
    }

    function open() {
      renderOptions();
      panel.hidden = false;
      wrapper.classList.add('is-open');
    }

    function close() {
      panel.hidden = true;
      wrapper.classList.remove('is-open');
    }

    button.addEventListener('click', function () {
      if (panel.hidden) open(); else close();
    });
    document.addEventListener('click', function (event) {
      if (!wrapper.contains(event.target)) close();
    });
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') close();
    });
    select.addEventListener('change', function () { button.textContent = label(); });

    button.textContent = label();
  }

  document.querySelectorAll('select[data-custom-select]').forEach(enhance);
})();
