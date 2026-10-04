/* ALOUTE: bảng tin. JS thuần: tải thêm bài, ô đăng bài (xem trước + kiểm tra trước khi tải lên), hộp thoại sửa/xóa. */
(function () {
  'use strict';

  const MB = 1024 * 1024;

  // ---------- Tải thêm bài: tự động khi cuộn gần tới cuối, nút "Xem thêm" là phương án dự phòng ----------
  // Mảnh HTML của trang kế thay chỗ khối .feed-more và mang theo khối .feed-more mới (nếu còn bài).
  const autoLoad = 'IntersectionObserver' in window ? new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (entry.isIntersecting) {
        autoLoad.unobserve(entry.target);
        loadMore(entry.target.querySelector('[data-more-url]'));
      }
    });
  }, { rootMargin: '400px 0px' }) : null;

  function watch(root) {
    if (!autoLoad) return;
    root.querySelectorAll('.feed-more').forEach(function (block) { autoLoad.observe(block); });
  }

  async function loadMore(button) {
    if (!button || button.disabled) return;
    const block = button.closest('.feed-more');
    const label = button.textContent;
    button.disabled = true;
    button.textContent = 'Đang tải…';
    try {
      const response = await window.Aloute.api(button.dataset.moreUrl, { headers: { Accept: 'text/html' } });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const template = document.createElement('template');
      template.innerHTML = await response.text();
      watch(template.content);
      block.replaceWith(template.content);
    } catch (error) {
      // Không quan sát lại: nút vẫn bấm được để thử lại, tránh vòng lặp lỗi khi khối còn nằm trong tầm nhìn
      button.disabled = false;
      button.textContent = label;
      window.Aloute.toast('Chưa tải thêm được, thử lại nhé.', 'error');
    }
  }

  document.addEventListener('click', function (event) {
    loadMore(event.target.closest('[data-more-url]'));
  });
  watch(document);

  // ---------- Lượt xem: báo cho máy chủ khi một bài hiện đủ lâu trên màn hình (mỗi bài một lần mỗi lần mở trang) ----------
  if ('IntersectionObserver' in window && document.body.dataset.auth === 'true') {
    const seen = new Set();
    const timers = new Map();
    const viewObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        const card = entry.target;
        const id = card.id.replace('post-', '');
        if (entry.isIntersecting && !seen.has(id)) {
          // Phải ở trong tầm nhìn ~1 giây mới tính, để cuộn lướt qua không bị đếm
          timers.set(card, setTimeout(function () {
            seen.add(id);
            viewObserver.unobserve(card);
            window.Aloute.api('/api/posts/' + id + '/view', { method: 'POST' }).catch(function () { /* số liệu phụ, bỏ qua lỗi */ });
          }, 1000));
        } else if (timers.has(card)) {
          clearTimeout(timers.get(card));
          timers.delete(card);
        }
      });
    }, { threshold: 0.5 });
    const observeCards = function (root) {
      root.querySelectorAll('article.post[id^="post-"]').forEach(function (card) { viewObserver.observe(card); });
    };
    observeCards(document);
    // Bài tải thêm khi cuộn cũng được theo dõi
    new MutationObserver(function (mutations) {
      mutations.forEach(function (m) {
        m.addedNodes.forEach(function (node) {
          if (node.nodeType === 1) {
            if (node.matches && node.matches('article.post[id^="post-"]')) viewObserver.observe(node);
            observeCards(node);
          }
        });
      });
    }).observe(document.body, { childList: true, subtree: true });
  }

  // ---------- Hộp thoại báo cáo dùng chung: điền loại/ID đối tượng của nút vừa bấm, gửi bằng fetch ----------
  document.addEventListener('show.bs.modal', function (event) {
    const modal = event.target;
    const trigger = event.relatedTarget;
    if (modal.id !== 'reportModal' || !trigger || !trigger.dataset.reportId) return;
    const form = modal.querySelector('[data-report-form]');
    form.elements.targetType.value = trigger.dataset.reportType;
    form.elements.targetId.value = trigger.dataset.reportId;
    form.elements.detail.value = '';
    modal.querySelector('[data-report-label]').textContent = trigger.dataset.reportLabel || 'nội dung này';
    const error = form.querySelector('[data-report-error]');
    error.hidden = true;
  });

  document.addEventListener('submit', async function (event) {
    const form = event.target.closest('[data-report-form]');
    if (!form) return;
    event.preventDefault();
    const error = form.querySelector('[data-report-error]');
    const submit = form.querySelector('[type="submit"]');
    submit.disabled = true;
    try {
      const response = await window.Aloute.api('/api/reports', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(new FormData(form)).toString(),
      });
      if (response.status === 400 || response.status === 429) {
        let message = 'Chưa gửi được báo cáo, thử lại nhé.';
        if (response.status === 429) message = 'Bạn gửi báo cáo hơi nhiều, đợi ít phút rồi thử lại nhé.';
        else try { message = (await response.json()).error || message; } catch (ignored) { /* giữ thông báo chung */ }
        error.textContent = message;
        error.hidden = false;
        return;
      }
      if (!response.ok) throw new Error('HTTP ' + response.status);
      bootstrap.Modal.getInstance(form.closest('.modal')).hide();
      window.Aloute.toast('Đã gửi báo cáo, cảm ơn bạn.', 'success');
    } catch (failure) {
      error.textContent = 'Chưa gửi được báo cáo, thử lại nhé.';
      error.hidden = false;
    } finally {
      submit.disabled = false;
    }
  });

  // ---------- Tặng Xu cho Creator và mở khóa bài trả phí ----------
  document.addEventListener('show.bs.modal', function (event) {
    const modal = event.target;
    const trigger = event.relatedTarget;
    if (modal.id !== 'donateModal' || !trigger || !trigger.dataset.donateUser) return;
    const form = modal.querySelector('[data-donate-form]');
    form.elements.toUserId.value = trigger.dataset.donateUser;
    modal.querySelector('[data-donate-name]').textContent = trigger.dataset.donateName || 'Creator';
    form.querySelector('[data-donate-error]').hidden = true;
  });

  // Gửi một form/nút tới API Xu; trả {ok, data} và hiển thị thông báo lỗi tiếng Việt do máy chủ trả về
  async function postCoins(url, bodyText) {
    const response = await window.Aloute.api(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: bodyText,
    });
    let data = {};
    try { data = await response.json(); } catch (ignored) { /* không có nội dung JSON */ }
    return { ok: response.ok, data: data };
  }

  document.addEventListener('submit', async function (event) {
    const form = event.target.closest('[data-donate-form]');
    if (!form) return;
    event.preventDefault();
    const error = form.querySelector('[data-donate-error]');
    const submit = form.querySelector('[type="submit"]');
    submit.disabled = true;
    try {
      const result = await postCoins('/api/donations', new URLSearchParams(new FormData(form)).toString());
      if (!result.ok) {
        error.textContent = result.data.error || 'Chưa tặng được Xu, thử lại nhé.';
        error.hidden = false;
        return;
      }
      bootstrap.Modal.getInstance(form.closest('.modal')).hide();
      window.Aloute.toast('Đã tặng Xu, cảm ơn bạn!', 'success');
    } catch (failure) {
      error.textContent = 'Chưa tặng được Xu, thử lại nhé.';
      error.hidden = false;
    } finally {
      submit.disabled = false;
    }
  });

  document.addEventListener('click', async function (event) {
    const button = event.target.closest('[data-unlock-post]');
    if (!button || button.disabled) return;
    button.disabled = true;
    try {
      const result = await postCoins('/api/posts/' + button.dataset.unlockPost + '/unlock', '');
      if (!result.ok) {
        window.Aloute.toast(result.data.error || 'Chưa mở khóa được, thử lại nhé.', 'error');
        button.disabled = false;
        return;
      }
      window.location.reload();
    } catch (failure) {
      window.Aloute.toast('Chưa mở khóa được, thử lại nhé.', 'error');
      button.disabled = false;
    }
  });

  // ---------- Hộp thoại sửa bài: danh mục và thẻ bạn bè ----------
  let friendsPromise = null;
  function loadFriends() {
    if (!friendsPromise) {
      friendsPromise = window.Aloute.api('/api/friends', { headers: { Accept: 'application/json' } })
        .then(function (response) { if (!response.ok) throw new Error('HTTP ' + response.status); return response.json(); })
        .catch(function (error) { friendsPromise = null; throw error; });
    }
    return friendsPromise;
  }

  function fillEditCategory(form, trigger) {
    const select = form.elements.categoryId;
    if (!select) return;
    const id = trigger.dataset.category || '';
    // Danh mục đã ngưng dùng không có trong danh sách: thêm tạm để sửa bài không làm mất nhãn của nó
    if (id && !Array.from(select.options).some(function (option) { return option.value === id; })) {
      const option = document.createElement('option');
      option.value = id;
      option.textContent = trigger.dataset.categoryName || 'Danh mục hiện tại';
      select.appendChild(option);
    }
    select.value = id;
  }

  async function fillEditTags(form, trigger) {
    const box = form.querySelector('[data-edit-tags]');
    const loaded = form.querySelector('[data-tags-loaded]');
    if (!box || !loaded) return;
    loaded.value = 'false'; // chưa nạp xong thì máy chủ giữ nguyên thẻ cũ
    box.textContent = 'Đang tải danh sách bạn bè…';
    try {
      const friends = await loadFriends();
      const picked = (trigger.dataset.tagged || '').split(',').filter(Boolean);
      box.textContent = '';
      if (!friends.length) {
        box.textContent = 'Bạn chưa có bạn bè nào để gắn thẻ.';
      }
      friends.forEach(function (friend) {
        const label = document.createElement('label');
        const input = document.createElement('input');
        input.type = 'checkbox';
        input.name = 'taggedUserIds';
        input.value = friend.id;
        input.checked = picked.indexOf(friend.id) >= 0;
        const span = document.createElement('span');
        span.textContent = friend.displayName;
        label.appendChild(input);
        label.appendChild(span);
        box.appendChild(label);
      });
      loaded.value = 'true';
    } catch (error) {
      box.textContent = 'Chưa tải được danh sách bạn bè, thẻ hiện tại sẽ được giữ nguyên.';
    }
  }

  // ---------- Hộp thoại sửa/xóa/chia sẻ dùng chung: điền dữ liệu của bài vừa bấm ----------
  document.addEventListener('show.bs.modal', function (event) {
    const modal = event.target;
    const trigger = event.relatedTarget;
    if (!trigger || !trigger.dataset.postId) return;
    const form = modal.querySelector('form');
    if (!form) return; // hộp thoại không có biểu mẫu (ví dụ danh sách người thả cảm xúc) có handler riêng
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
      fillEditCategory(form, trigger);
      fillEditTags(form, trigger);
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

  // Cập nhật dòng "N cảm xúc" (và ẩn/hiện cả dòng thống kê) sau khi thả hoặc bỏ cảm xúc
  function updateReactionStats(post, total) {
    if (!post) return;
    const stats = post.querySelector('.post-stats');
    const button = post.querySelector('[data-show-reactors]');
    if (button) {
      button.querySelector('[data-reactions-total]').textContent = String(total);
      button.hidden = total === 0;
    }
    if (stats) {
      const others = Array.from(stats.children).some(function (child) { return child !== button && !child.hidden; });
      stats.hidden = total === 0 && !others;
    }
  }

  // ---------- Xem ai đã thả cảm xúc ----------
  document.addEventListener('show.bs.modal', async function (event) {
    const modal = event.target;
    const trigger = event.relatedTarget;
    if (modal.id !== 'reactorsModal' || !trigger || !trigger.dataset.postId) return;
    const box = modal.querySelector('[data-reactors-list]');
    box.textContent = 'Đang tải…';
    try {
      const response = await window.Aloute.api('/api/posts/' + trigger.dataset.postId + '/reactions', { headers: { Accept: 'text/html' } });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      box.innerHTML = await response.text();
    } catch (error) {
      box.textContent = 'Chưa tải được danh sách, thử lại nhé.';
    }
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
      updateReactionStats(actions.closest('.post'), data.total);
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

  // Ô "Đang trả lời X": nhắc rõ đang trả lời ai (kiểu Facebook), kèm @Tên chèn sẵn vào nội dung.
  function showReplyContext(form, parentId, name) {
    form.dataset.parentId = parentId;
    const chip = form.querySelector('[data-replying-to]');
    chip.querySelector('[data-reply-name]').textContent = name;
    chip.hidden = false;
    const textarea = form.elements.content;
    textarea.value = '@' + name + ' ';
    textarea.focus();
    textarea.setSelectionRange(textarea.value.length, textarea.value.length);
  }

  function clearReplyContext(form) {
    delete form.dataset.parentId;
    form.querySelector('[data-replying-to]').hidden = true;
    form.elements.content.value = '';
  }

  document.addEventListener('click', function (event) {
    const replyButton = event.target.closest('[data-reply-to]');
    if (!replyButton) return;
    const section = commentSectionOf(replyButton);
    const form = section.querySelector('.comment-form');
    if (!form) return;
    showReplyContext(form, replyButton.dataset.parentId, replyButton.dataset.name);
  });

  document.addEventListener('click', function (event) {
    const cancelButton = event.target.closest('[data-cancel-reply]');
    if (!cancelButton) return;
    clearReplyContext(cancelButton.closest('.comment-form'));
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

  // ---------- Ảnh trong bình luận: dán Ctrl+V (hoặc chọn file), xem trước, gửi kèm bình luận ----------
  const COMMENT_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
  const COMMENT_IMAGE_MAX_BYTES = 8 * MB; // khớp MediaLimits.MAX_IMAGE_BYTES; máy chủ vẫn kiểm lại
  const commentImages = new WeakMap(); // form -> File đang chờ gửi

  function setCommentImage(form, file) {
    clearCommentImage(form);
    commentImages.set(form, file);
    const box = form.querySelector('[data-comment-attach]');
    const thumb = form.querySelector('[data-comment-thumb]');
    thumb.src = URL.createObjectURL(file);
    box.hidden = false;
  }

  function clearCommentImage(form) {
    commentImages.delete(form);
    const box = form.querySelector('[data-comment-attach]');
    const thumb = form.querySelector('[data-comment-thumb]');
    if (!box || !thumb) return;
    if (thumb.src) URL.revokeObjectURL(thumb.src);
    thumb.removeAttribute('src');
    box.hidden = true;
  }

  document.addEventListener('paste', function (event) {
    const textarea = event.target;
    const form = textarea.closest && textarea.closest('.comment-form');
    if (!form || textarea.name !== 'content') return;
    const items = event.clipboardData ? Array.from(event.clipboardData.items) : [];
    const item = items.find(function (candidate) { return candidate.kind === 'file' && candidate.type.indexOf('image/') === 0; });
    const picture = item ? item.getAsFile() : null;
    if (!picture) return; // dán chữ: giữ hành vi mặc định
    event.preventDefault();
    if (COMMENT_IMAGE_TYPES.indexOf(picture.type) < 0) { window.Aloute.toast('Chỉ nhận ảnh JPG, PNG, GIF hoặc WEBP.', 'error'); return; }
    if (picture.size > COMMENT_IMAGE_MAX_BYTES) { window.Aloute.toast('Mỗi ảnh tối đa ' + (COMMENT_IMAGE_MAX_BYTES / MB) + ' MB.', 'error'); return; }
    const extension = (picture.type.split('/')[1] || 'png').replace('jpeg', 'jpg');
    setCommentImage(form, new File([picture], 'anh-dan-' + Date.now() + '.' + extension, { type: picture.type }));
  });

  document.addEventListener('click', function (event) {
    const clear = event.target.closest('[data-comment-attach-clear]');
    if (clear) clearCommentImage(clear.closest('.comment-form'));
  });

  document.addEventListener('submit', async function (event) {
    const form = event.target.closest('.comment-form');
    if (!form) return;
    event.preventDefault();
    const textarea = form.elements.content;
    const content = textarea.value.trim();
    const image = commentImages.get(form);
    if (!content && !image) return;
    const section = form.closest('.post-comments');
    const body = new FormData();
    if (content) body.append('content', content);
    if (image) body.append('image', image);
    if (form.dataset.parentId) {
      body.append('parentId', form.dataset.parentId);
    }
    const submit = form.querySelector('[type="submit"]');
    submit.disabled = true;
    try {
      const response = await window.Aloute.api(form.dataset.commentsUrl, { method: 'POST', body: body });
      if (response.status === 400) {
        let message = 'Chưa gửi được bình luận, thử lại nhé.';
        try { message = (await response.json()).error || message; } catch (ignored) { /* giữ thông báo chung */ }
        window.Aloute.toast(message, 'error');
        return;
      }
      if (!response.ok) throw new Error('HTTP ' + response.status);
      clearReplyContext(form);
      clearCommentImage(form);
      await loadComments(section, form.dataset.commentsUrl);
    } catch (error) {
      window.Aloute.toast('Chưa gửi được bình luận, thử lại nhé.', 'error');
    } finally {
      submit.disabled = false;
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

    const tzField = form.querySelector('[data-tz-offset]');
    if (tzField) tzField.value = String(new Date().getTimezoneOffset()); // để máy chủ hiểu đúng giờ hẹn theo múi giờ của bạn

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

    // Chỉ dựng lại khi danh sách ảnh/video thật sự đổi (không phải mỗi lần gõ chữ), nếu không ảnh sẽ nháy liên tục:
    // mỗi lần gọi revoke URL cũ và tạo <img> mới, trong khi ảnh cũ vừa được trình duyệt giải phóng.
    function refreshPreviews() {
      objectUrls.forEach(URL.revokeObjectURL);
      objectUrls = [];
      previews.textContent = '';
      images.forEach(function (file, index) { previews.appendChild(preview(file, false, function () { images.splice(index, 1); refreshPreviews(); refreshCounters(); })); });
      if (video) previews.appendChild(preview(video, true, function () { video = null; refreshPreviews(); refreshCounters(); }));
      syncInputs();
    }

    function refreshCounters() {
      const length = Array.from(text.value).length; // đếm ký tự (emoji tính là 1), khớp giới hạn của máy chủ
      counter.textContent = String(length);
      counterBox.classList.toggle('is-near', length > maxChars * 0.9);
      submit.disabled = length === 0 && images.length === 0 && !video;
    }

    function refresh() {
      refreshPreviews();
      refreshCounters();
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

    // Thêm ảnh vào bài (dùng chung cho chọn file và dán Ctrl+V), có kiểm tra như nhau
    function addImages(chosen) {
      showError('');
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
    }

    imageInput.addEventListener('change', function () {
      addImages(Array.from(imageInput.files));
    });

    // Ctrl+V: dán ảnh từ bộ nhớ tạm (ảnh chụp màn hình, ảnh sao chép từ web...) vào ô đăng bài như khi chọn file.
    // Chỉ chặn hành vi mặc định khi trong clipboard thật sự có ảnh; dán chữ vẫn bình thường.
    form.addEventListener('paste', function (event) {
      const items = event.clipboardData ? Array.from(event.clipboardData.items) : [];
      const pictures = items.filter(function (item) { return item.kind === 'file' && item.type.indexOf('image/') === 0; })
        .map(function (item) { return item.getAsFile(); })
        .filter(Boolean)
        .map(function (file, index) {
          // Ảnh dán thường tên chung chung ("image.png"); đặt tên riêng để dễ phân biệt khi dán nhiều ảnh
          const extension = (file.type.split('/')[1] || 'png').replace('jpeg', 'jpg');
          return new File([file], 'anh-dan-' + Date.now() + '-' + index + '.' + extension, { type: file.type });
        });
      if (!pictures.length) return;
      event.preventDefault();
      addImages(pictures);
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

    text.addEventListener('input', refreshCounters);
    refresh();
  }
})();
