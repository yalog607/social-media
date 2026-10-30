/* ALOUTE — trang một cuộc trò chuyện: nhận tin nhắn mới qua WebSocket (STOMP/SockJS), gửi tin qua fetch thường.
   Máy chủ đẩy MỌI tin nhắn mới (kể cả của chính mình) qua cùng một kênh, nên chỉ cần một chỗ để chèn bong bóng chat. */
(function () {
  'use strict';

  var main = document.getElementById('chat-main');
  if (!main) return;

  var conversationId = main.dataset.conversationId;
  var myId = main.dataset.userId;
  var stickerClipSequence = 0;
  var isGroup = main.dataset.group === 'true';
  var chatWindow = document.getElementById('chat-window');
  var composer = document.getElementById('chat-composer');
  var textarea = composer.querySelector('textarea[name="content"]');
  var attachInput = document.getElementById('chat-attach-input');
  var attachBtn = document.getElementById('chat-attach-btn');
  var attachPreview = document.getElementById('chat-attach-preview');
  var attachName = document.getElementById('chat-attach-name');
  var attachClear = document.getElementById('chat-attach-clear');

  function scrollToBottom() {
    chatWindow.scrollTop = chatWindow.scrollHeight;
  }
  function nearBottom() {
    return chatWindow.scrollHeight - chatWindow.scrollTop - chatWindow.clientHeight < 120;
  }
  scrollToBottom();
  // Ảnh/video tải sau lần cuộn đầu làm khung cao thêm: cuộn lại để vẫn dừng ở tin mới nhất, chừng nào người dùng chưa cuộn lên.
  var stickToBottom = true;
  chatWindow.addEventListener('scroll', function () { stickToBottom = nearBottom(); });
  chatWindow.addEventListener('load', function () { if (stickToBottom) scrollToBottom(); }, true);
  window.addEventListener('load', scrollToBottom);

  // ---------- Tải tin cũ khi cuộn lên đầu: chèn lên trên và giữ nguyên vị trí đang đọc ----------
  var loadingOlder = false;
  async function loadOlder() {
    var marker = chatWindow.querySelector('.chat-more');
    if (!marker || loadingOlder) return false;
    loadingOlder = true;
    marker.classList.add('is-loading');
    try {
      var response = await window.Aloute.api(marker.dataset.beforeUrl, { headers: { Accept: 'text/html' } });
      if (!response.ok) {
        var failure = await response.json().catch(function () { return {}; });
        throw new Error(failure.message || 'Chưa gửi được tin nhắn, thử lại nhé.');
      }
      var template = document.createElement('template');
      template.innerHTML = await response.text();
      // Bỏ tin đã có sẵn (vừa tới qua WebSocket trong lúc tải) để không bị lặp
      template.content.querySelectorAll('.msg[data-id]').forEach(function (el) {
        if (chatWindow.querySelector('.msg[data-id="' + el.dataset.id + '"]')) el.remove();
      });
      var previousHeight = chatWindow.scrollHeight;
      var previousTop = chatWindow.scrollTop;
      marker.replaceWith(template.content);
      chatWindow.scrollTop = previousTop + (chatWindow.scrollHeight - previousHeight);
      return true;
    } catch (error) {
      marker.classList.remove('is-loading');
      window.Aloute.toast('Chưa tải được tin cũ hơn, cuộn lên để thử lại.', 'error');
      return false;
    } finally {
      loadingOlder = false;
    }
  }
  chatWindow.addEventListener('scroll', function () {
    if (chatWindow.scrollTop < 80) loadOlder();
  });
  // Khung chưa đủ dài để cuộn thì không có sự kiện cuộn nào: tải tiếp cho tới khi đầy
  function fillIfShort() {
    if (chatWindow.querySelector('.chat-more') && chatWindow.scrollHeight <= chatWindow.clientHeight + 80) {
      loadOlder().then(function (ok) { if (ok) fillIfShort(); });
    }
  }
  window.addEventListener('load', fillIfShort);

  // ---------- Nhận tin nhắn realtime ----------
  function appendMessage(m) {
    var mine = String(m.sender.id) === String(myId);
    var msg = document.createElement('div');
    msg.className = 'msg' + (mine ? ' is-mine' : '');
    if (m.id) {
      if (chatWindow.querySelector('.msg[data-id="' + m.id + '"]')) return;
      msg.dataset.id = m.id;
      msg.id = 'msg-' + m.id;
    }

    if (m.isSystem) {
      msg.className = 'msg justify-content-center text-center my-3';
      var span = document.createElement('span');
      span.className = 'text-dark fw-medium';
      span.innerHTML = m.contentHtml || '';
      msg.appendChild(span);
      
      var empty = chatWindow.querySelector('p');
      if (empty) empty.remove();
      var follow = nearBottom();
      chatWindow.appendChild(msg);
      if (follow) scrollToBottom();
      return;
    }

    var avatar = document.createElement('span');
    avatar.className = 'avatar';
    if (m.sender.avatarFrame) avatar.classList.add('avatar-frame--' + m.sender.avatarFrame);
    if (m.sender.avatarUrl) {
      var img = document.createElement('img');
      img.src = m.sender.avatarUrl;
      img.loading = 'lazy';
      img.alt = '';
      avatar.appendChild(img);
    } else {
      avatar.textContent = (m.sender.displayName || '?').charAt(0).toUpperCase();
    }
    var avatarLink = document.createElement('a');
    avatarLink.href = '/u/' + encodeURIComponent(m.sender.username);
    avatarLink.appendChild(avatar);
    msg.appendChild(avatarLink);

    var body = document.createElement('div');
    body.className = 'msg-body';

    if (isGroup && !mine) {
      var senderRow = document.createElement('div');
      senderRow.className = 'd-flex align-items-center mb-1';
      var sender = document.createElement('span');
      sender.className = 'msg-sender mb-0';
      sender.textContent = m.sender.displayName;
      senderRow.appendChild(sender);

      if (m.sender.primaryRole === 'CREATOR') {
        var badge = document.createElement('div');
        badge.innerHTML = '<svg class="verified-badge" viewBox="0 0 24 24" role="img" aria-label="Creator đã xác minh" focusable="false"><title>Creator đã xác minh</title><polygon points="12.00,1.20 14.38,3.11 17.40,2.65 18.51,5.49 21.35,6.60 20.89,9.62 22.80,12.00 20.89,14.38 21.35,17.40 18.51,18.51 17.40,21.35 14.38,20.89 12.00,22.80 9.62,20.89 6.60,21.35 5.49,18.51 2.65,17.40 3.11,14.38 1.20,12.00 3.11,9.62 2.65,6.60 5.49,5.49 6.60,2.65 9.62,3.11" fill="#F5B800" stroke="#1B1B1F" stroke-width="1.4" stroke-linejoin="round"/><path d="M7.4 12.4l3.2 3.2 6-6.6" fill="none" stroke="#FFFFFF" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/></svg>';
        senderRow.appendChild(badge.firstChild);
      }
      body.appendChild(senderRow);
    }

    if (m.reply) {
      var replyBox = document.createElement('div');
      replyBox.className = 'msg-reply-container' + (mine ? ' is-mine' : '');
      replyBox.title = 'Nhấn để xem tin nhắn gốc';
      if (m.reply.messageId) replyBox.dataset.mid = m.reply.messageId;
      replyBox.dataset.snippet = m.reply.snippet;
      replyBox.onclick = function () {
        if (typeof scrollToRepliedMessage === 'function') {
          scrollToRepliedMessage(m.reply.messageId, m.reply.snippet);
        }
      };
      var indicator = document.createElement('div');
      indicator.className = 'msg-reply-indicator';
      var isRepliedToMe = !isGroup;
      var titleText = mine
        ? ('Bạn đã trả lời ' + m.reply.senderName)
        : (m.sender.displayName + ' đã trả lời ' + (isRepliedToMe ? 'bạn' : m.reply.senderName));
      indicator.innerHTML = '<svg class="icon" width="12" height="12" aria-hidden="true"><use href="#i-reply"/></svg><span>' + escapeText(titleText) + '</span>';
      
      var snippetEl = document.createElement('div');
      snippetEl.className = 'msg-reply-snippet-bubble';
      snippetEl.textContent = m.reply.snippet;

      replyBox.appendChild(indicator);
      replyBox.appendChild(snippetEl);
      body.appendChild(replyBox);
    }

    var row = document.createElement('div');
    row.className = 'msg-row d-flex align-items-center gap-2' + (mine ? ' flex-row-reverse' : '');

    var bubbleWrap = document.createElement('div');
    bubbleWrap.className = 'msg-bubble-wrap position-relative';

    var bubble = document.createElement('span');
    bubble.className = 'bubble ' + (mine ? 'bubble--out' : 'bubble--in');
    bubble.innerHTML = attachmentHtml(m.attachment) + (m.contentHtml || '');
    if (m.sticker && (m.sticker.renderType === 'IMAGE' || Number.isInteger(m.sticker.spriteIndex))) {
      bubble.className = 'msg-local-sticker';
      bubble.insertBefore(stickerArt(m.sticker, 'sticker-art--message'), bubble.firstChild);
    } else if (m.sticker || m.giphyId) {
      var hiddenSticker = document.createElement('span');
      hiddenSticker.className = 'text-soft small'; hiddenSticker.textContent = 'Nhãn dán cũ không còn hiển thị.';
      bubble.insertBefore(hiddenSticker, bubble.firstChild);
    }
    bubbleWrap.appendChild(bubble);

    var badge = document.createElement('div');
    badge.className = 'msg-reactions-badge d-none';
    badge.id = 'msg-reactions-' + m.id;
    badge.setAttribute('role', 'button');
    badge.setAttribute('tabindex', '0');
    badge.setAttribute('title', 'Xem ai đã thả cảm xúc');
    badge.setAttribute('data-bs-toggle', 'modal');
    badge.setAttribute('data-bs-target', '#reactorsModal');
    badge.dataset.mid = m.id;
    badge.dataset.cid = conversationId;

    var emojisSpan = document.createElement('span');
    emojisSpan.className = 'msg-reactions-emojis';
    var emojis = [
      { type: 'LOVE', emoji: '❤️', label: 'Yêu thích' },
      { type: 'HAHA', emoji: '😂', label: 'Haha' },
      { type: 'FIRE', emoji: '🔥', label: 'Cháy quá' },
      { type: 'WOW', emoji: '😮', label: 'Wow' },
      { type: 'SAD', emoji: '😢', label: 'Buồn ghê' }
    ];
    emojis.forEach(function (e) {
      var s = document.createElement('span');
      s.className = 'mr-emoji mr-emoji--' + e.type + ' d-none';
      s.textContent = e.emoji;
      emojisSpan.appendChild(s);
    });
    badge.appendChild(emojisSpan);

    var countSpan = document.createElement('span');
    countSpan.className = 'msg-reactions-count d-none';
    countSpan.textContent = '0';
    badge.appendChild(countSpan);
    bubbleWrap.appendChild(badge);

    // 3 tác nhân bên cạnh (cảm xúc, trả lời, khác)
    var actions = document.createElement('div');
    actions.className = 'msg-actions';

    // 1. Bày tỏ cảm xúc
    var reactContainer = document.createElement('div');
    reactContainer.className = 'msg-reaction-container';
    var reactBtn = document.createElement('button');
    reactBtn.type = 'button';
    reactBtn.className = 'msg-action-btn msg-action-btn--react';
    reactBtn.title = 'Bày tỏ cảm xúc';
    reactBtn.setAttribute('aria-label', 'Bày tỏ cảm xúc');
    reactBtn.innerHTML = '<svg class="icon" width="15" height="15" aria-hidden="true"><use href="#i-smile"/></svg>';

    var reactionBar = document.createElement('div');
    reactionBar.className = 'msg-reaction-bar';
    emojis.forEach(function (e) {
      var rBtn = document.createElement('button');
      rBtn.type = 'button';
      rBtn.className = 'msg-reaction-btn';
      rBtn.dataset.mid = m.id;
      rBtn.dataset.cid = conversationId;
      rBtn.dataset.type = e.type;
      rBtn.title = e.label;
      rBtn.innerHTML = '<span>' + e.emoji + '</span>';
      reactionBar.appendChild(rBtn);
    });
    reactContainer.appendChild(reactBtn);
    reactContainer.appendChild(reactionBar);
    actions.appendChild(reactContainer);

    // 2. Trả lời
    var replyBtn = document.createElement('button');
    replyBtn.type = 'button';
    replyBtn.className = 'msg-action-btn msg-action-btn--reply';
    replyBtn.title = 'Trả lời';
    replyBtn.setAttribute('aria-label', 'Trả lời');
    replyBtn.innerHTML = '<svg class="icon" width="14" height="14" aria-hidden="true"><use href="#i-reply"/></svg>';
    replyBtn.addEventListener('click', function () {
      if (typeof startReply === 'function') {
        startReply(m.id, m.sender.displayName);
      }
    });
    actions.appendChild(replyBtn);

    // 3. Khác (Ghim, Sửa, Xóa)
    var moreDropdown = document.createElement('div');
    moreDropdown.className = 'dropdown msg-more-dropdown';
    var moreBtn = document.createElement('button');
    moreBtn.type = 'button';
    moreBtn.className = 'msg-action-btn msg-action-btn--more';
    moreBtn.setAttribute('data-bs-toggle', 'dropdown');
    moreBtn.setAttribute('aria-expanded', 'false');
    moreBtn.title = 'Khác';
    moreBtn.setAttribute('aria-label', 'Khác');
    moreBtn.innerHTML = '<svg class="icon" width="14" height="14" aria-hidden="true"><use href="#i-more"/></svg>';

    var dropdownMenu = document.createElement('ul');
    dropdownMenu.className = 'dropdown-menu menu-al msg-dropdown-menu' + (mine ? ' dropdown-menu-end' : '');
    var menuHtml = '';
    if (mine && !m.giphyId && !m.sticker) {
      menuHtml += '<li><button class="dropdown-item" type="button" onclick="editMessage(\'' + m.id + '\')"><svg class="icon" width="15" height="15" aria-hidden="true"><use href="#i-pencil"/></svg><span>Sửa tin nhắn</span></button></li>';
    }
    menuHtml += '<li><button class="dropdown-item" type="button" onclick="copyMessageText(\'' + m.id + '\')"><svg class="icon" width="15" height="15" aria-hidden="true"><use href="#i-copy"/></svg><span>Sao chép văn bản</span></button></li>';
    if (mine) {
      menuHtml += '<li><hr class="dropdown-divider my-1"></li><li><button class="dropdown-item text-danger" type="button" onclick="confirmDeleteMessage(\'' + conversationId + '\', \'' + m.id + '\')"><svg class="icon" width="15" height="15" aria-hidden="true"><use href="#i-trash"/></svg><span>Xóa tin nhắn</span></button></li>';
    }
    dropdownMenu.innerHTML = menuHtml;
    moreDropdown.appendChild(moreBtn);
    moreDropdown.appendChild(dropdownMenu);
    actions.appendChild(moreDropdown);

    row.appendChild(bubbleWrap);
    row.appendChild(actions);
    body.appendChild(row);
    var time = document.createElement('span');
    time.className = 'msg-time';
    time.textContent = 'vừa xong';
    body.appendChild(time);

    msg.appendChild(body);
    var empty = chatWindow.querySelector('p');
    if (empty) empty.remove();
    var follow = mine || nearBottom();
    chatWindow.appendChild(msg);
    if (follow) scrollToBottom();
  }

  function escapeText(text) { var d = document.createElement("div"); d.textContent = text || ""; return d.innerHTML; }

  function attachmentHtml(a) {
    if (!a) return '';
    if (a.kind === 'IMAGE') {
      return '<a href="' + a.url + '" target="_blank" rel="noopener"><img class="msg-attachment-img" src="' + a.url + '" alt="Ảnh đính kèm"></a>';
    }
    if (a.kind === 'VIDEO') {
      return '<video class="msg-attachment-video" src="' + a.url + '" controls></video>';
    }
    var name = document.createElement('div');
    name.textContent = a.originalName;
    return '<a class="msg-attachment-file" href="' + a.url + '" target="_blank" rel="noopener">' + name.innerHTML + '</a>';
  }

  // ---------- Xử lý cảm xúc tin nhắn realtime qua WebSocket ----------
  function handleMessageReactionEvent(data) {
    var mid = data.messageId;
    var msg = document.getElementById('msg-' + mid) || chatWindow.querySelector('.msg[data-id="' + mid + '"]');
    if (!msg) return;

    var badge = document.getElementById('msg-reactions-' + mid) || msg.querySelector('.msg-reactions-badge');
    if (!badge) return;

    var total = data.total || 0;
    badge.classList.toggle('d-none', total <= 0);
    badge.classList.toggle('is-single', total === 1);

    var countEl = badge.querySelector('.msg-reactions-count');
    if (countEl) {
      countEl.textContent = String(total);
      countEl.classList.toggle('d-none', total <= 1);
    }

    badge.querySelectorAll('.mr-emoji').forEach(function (emojiSpan) {
      var t = emojiSpan.className.replace(/.*mr-emoji--([A-Z]+).*/, '$1');
      var count = (data.counts && data.counts[t]) || 0;
      emojiSpan.classList.toggle('d-none', count <= 0);
    });
  }

  // Bấm vào nút cảm xúc trên thanh nổi để thả/đổi/bỏ
  
  // Click vào nút mặt cười để bật / tắt thanh cảm xúc (Chỉ click mới hiện - Hình 2)
  document.addEventListener('click', function (event) {
    var reactBtn = event.target.closest('.msg-action-btn--react');
    if (reactBtn) {
      event.preventDefault();
      event.stopPropagation();
      var container = reactBtn.closest('.msg-reaction-container');
      if (!container) return;
      var bar = container.querySelector('.msg-reaction-bar');
      if (!bar) return;

      var wasOpen = bar.classList.contains('is-open');
      document.querySelectorAll('.msg-reaction-bar.is-open').forEach(function (el) {
        el.classList.remove('is-open');
      });
      if (!wasOpen) {
        bar.classList.add('is-open');
      }
      return;
    }

    // Click ra ngoài thì đóng thanh cảm xúc
    if (!event.target.closest('.msg-reaction-bar')) {
      document.querySelectorAll('.msg-reaction-bar.is-open').forEach(function (el) {
        el.classList.remove('is-open');
      });
    }
  });

  document.addEventListener('click', async function (event) {
    var btn = event.target.closest('.msg-reaction-btn');
    if (!btn || !btn.dataset.mid || !btn.dataset.type) return;

    var bar = btn.closest('.msg-reaction-bar');
    if (bar) bar.classList.remove('is-open');

    var mid = btn.dataset.mid;
    var cid = btn.dataset.cid || conversationId;
    var type = btn.dataset.type;

    try {
      var response = await window.Aloute.api('/api/conversations/' + cid + '/messages/' + mid + '/reaction?type=' + type, { method: 'POST' });
      if (!response.ok) throw new Error('HTTP ' + response.status);
    } catch (error) {
      window.Aloute.toast('Chưa thả được cảm xúc tin nhắn, thử lại nhé.', 'error');
    }
  });

  // Xem ai đã thả cảm xúc tin nhắn
  document.addEventListener('show.bs.modal', async function (event) {
    var modal = event.target;
    var trigger = event.relatedTarget;
    if (modal.id !== 'reactorsModal' || !trigger) return;

    var mid = trigger.dataset.mid;
    var cid = trigger.dataset.cid || conversationId;
    if (!mid) return;

    var box = modal.querySelector('[data-reactors-list]');
    if (!box) return;
    box.textContent = 'Đang tải…';
    try {
      var response = await window.Aloute.api('/api/conversations/' + cid + '/messages/' + mid + '/reactions', { headers: { Accept: 'text/html' } });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      box.innerHTML = await response.text();
    } catch (error) {
      box.textContent = 'Chưa tải được danh sách, thử lại nhé.';
    }
  });

  async function markCurrentConversationRead() {
    if (document.hidden) return;
    try {
      var response = await window.Aloute.api('/api/conversations/' + conversationId + '/read', { method: 'POST' });
      if (!response.ok) return;
      var data = await response.json();
      document.querySelectorAll('[data-unread-messages]').forEach(function (badge) {
        badge.textContent = String(data.unread);
        badge.hidden = data.unread === 0;
      });
    } catch (_) { /* Lần mở hoặc tin mới tiếp theo sẽ thử lại. */ }
  }
  document.addEventListener('visibilitychange', function () {
    if (!document.hidden) markCurrentConversationRead();
  });

  function connect() {
    var socket = new SockJS('/ws');
    var client = Stomp.over(socket);
    client.debug = null;
    client.connect({}, function () {
      client.subscribe('/topic/conversations/' + conversationId, function (frame) {
        var data = JSON.parse(frame.body);
        if (data.event === 'REACTION') {
          handleMessageReactionEvent(data);
        } else {
          appendMessage(data);
          markCurrentConversationRead();
        }
      });
    }, function () {
      setTimeout(connect, 3000); // mất kết nối thì thử lại, không báo lỗi ồn ào
    });
  }
  if (window.SockJS && window.Stomp) {
    connect();
  }

  // ---------- Đính kèm file (chọn từ máy hoặc dán bằng Ctrl+V) ----------
  var attachedFile = null;
  var attachThumb = document.getElementById('chat-attach-thumb');
  var maxAttachmentBytes = Number(main.dataset.maxAttachmentMb || 20) * 1024 * 1024;

  function clearAttachment() {
    attachedFile = null;
    attachInput.value = '';
    attachPreview.hidden = true;
    attachName.textContent = '';
    if (attachThumb.src) URL.revokeObjectURL(attachThumb.src);
    attachThumb.removeAttribute('src');
    attachThumb.hidden = true;
  }

  // Mỗi tin chỉ một file: file mới thay file đang chọn. Ảnh có bản xem trước nhỏ.
  function setAttachment(file) {
    clearAttachment();
    attachedFile = file;
    attachPreview.hidden = false;
    attachName.textContent = file.name;
    if (file.type.indexOf('image/') === 0) {
      attachThumb.src = URL.createObjectURL(file);
      attachThumb.hidden = false;
    }
  }

  attachBtn.addEventListener('click', function () { attachInput.click(); });
  attachInput.addEventListener('change', function () {
    var file = attachInput.files[0];
    if (!file) { clearAttachment(); return; }
    if (file.size > maxAttachmentBytes) {
      window.Aloute.toast('File tối đa ' + Math.round(maxAttachmentBytes / 1024 / 1024) + ' MB.', 'error');
      clearAttachment();
      return;
    }
    setAttachment(file);
  });
  attachClear.addEventListener('click', clearAttachment);

  // Ctrl+V: dán ảnh (ảnh chụp màn hình, ảnh sao chép từ web...) như khi chọn file. Dán chữ thì giữ nguyên hành vi mặc định.
  textarea.addEventListener('paste', function (event) {
    var items = event.clipboardData ? Array.prototype.slice.call(event.clipboardData.items) : [];
    var picture = null;
    items.forEach(function (item) {
      if (!picture && item.kind === 'file' && item.type.indexOf('image/') === 0) picture = item.getAsFile();
    });
    if (!picture) return;
    event.preventDefault();
    if (picture.size > maxAttachmentBytes) {
      window.Aloute.toast('Ảnh tối đa ' + Math.round(maxAttachmentBytes / 1024 / 1024) + ' MB.', 'error');
      return;
    }
    var extension = (picture.type.split('/')[1] || 'png').replace('jpeg', 'jpg');
    setAttachment(new File([picture], 'anh-dan-' + Date.now() + '.' + extension, { type: picture.type }));
  });

  // ---------- Gửi tin nhắn ----------
  composer.addEventListener('submit', async function (event) {
    event.preventDefault();
    var content = textarea.value.trim();
    if (!content && !attachedFile) return;

    if (window.chatReplyState) {
      content = '[reply:' + window.chatReplyState.messageId + ':@' + window.chatReplyState.senderName + ': "' + window.chatReplyState.snippet + '"]\n' + content;
      if (typeof cancelReply === 'function') cancelReply();
    }

    var body = new FormData();
    if (content) body.append('content', content);
    if (attachedFile) body.append('attachment', attachedFile);

    var submitBtn = composer.querySelector('.chat-send-btn');
    submitBtn.disabled = true;
    try {
      var response = await window.Aloute.api('/api/conversations/' + conversationId + '/messages', { method: 'POST', body: body });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      textarea.value = '';
      clearAttachment();
    } catch (error) {
      window.Aloute.toast(error.message || 'Chưa gửi được tin nhắn, thử lại nhé.', 'error');
    } finally {
      submitBtn.disabled = false;
    }
  });

  textarea.addEventListener('keydown', function (event) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      composer.requestSubmit();
    }
  });

  // Local character sticker packs. Favorites and recent sends are saved per account.
  var stickerBtn = document.getElementById('chat-sticker-btn');
  var stickerPicker = document.getElementById('chat-sticker-picker');
  var stickerGrid = document.getElementById('sticker-grid');
  var stickerSearch = document.getElementById('sticker-search');
  var stickerTabs = document.getElementById('sticker-pack-tabs');
  var stickerStatus = document.getElementById('sticker-status');
  var stickerRetry = document.getElementById('sticker-retry');
  var library = {packs: [], recent: [], favorites: []};
  var activeStickerTab = '', loadingLibrary = false, sendingSticker = false;

  function stickerArt(sticker, extraClass) {
    if (sticker.renderType === 'IMAGE') {
      var img = document.createElement('img'); img.className = 'sticker-art sticker-image ' + (extraClass || '');
      img.src = sticker.imageUrl; img.alt = sticker.name || 'Nhãn dán'; img.loading = 'lazy'; return img;
    }
    var svgNS = 'http://www.w3.org/2000/svg';
    var art = document.createElementNS(svgNS, 'svg'); art.setAttribute('class', 'sticker-art ' + (extraClass || ''));
    art.setAttribute('role', 'img'); art.setAttribute('aria-label', sticker.name || 'Nhãn dán');
    if (sticker.artwork) {
      art.setAttribute('viewBox', sticker.artwork.viewBox);
      var clipId = 'local-art-' + (++stickerClipSequence);
      var defs = document.createElementNS(svgNS, 'defs');
      var clip = document.createElementNS(svgNS, 'clipPath'); clip.id = clipId; clip.setAttribute('clipPathUnits', 'userSpaceOnUse');
      var polygon = document.createElementNS(svgNS, 'polygon'); polygon.setAttribute('points', sticker.artwork.clipPoints);
      clip.appendChild(polygon); defs.appendChild(clip); art.appendChild(defs);
      var image = document.createElementNS(svgNS, 'image'); image.setAttribute('href', '/stickers/original/characters.png');
      image.setAttribute('width', '1024'); image.setAttribute('height', '1536'); image.setAttribute('clip-path', 'url(#' + clipId + ')');
      art.appendChild(image);
    }
    return art;
  }

  function normalizeStickerText(value) {
    return (value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
  }

  async function loadStickerLibrary() {
    if (loadingLibrary) return;
    loadingLibrary = true; stickerStatus.textContent = 'Đang tải nhãn dán…'; stickerRetry.hidden = true;
    try {
      var response = await window.Aloute.api('/api/stickers/library');
      if (!response.ok) throw new Error('library');
      library = await response.json();
      if (!activeStickerTab) activeStickerTab = library.recent.length ? 'recent' : ((library.packs[0] || {}).id || 'recent');
      renderStickerTabs(); renderLocalStickers();
    } catch (error) { stickerStatus.textContent = 'Chưa tải được bộ nhãn dán. Bạn thử lại nhé.'; stickerRetry.hidden = false; }
    finally { loadingLibrary = false; }
  }

  function renderStickerTabs() {
    var restoreFocus = stickerTabs.contains(document.activeElement);
    stickerTabs.replaceChildren();
    var tabs = [{id: 'recent', name: 'Gần đây', icon: '◷'}, {id: 'favorites', name: 'Yêu thích', icon: '♡'}].concat(library.packs);
    tabs.forEach(function (tab) {
      var button = document.createElement('button'); button.type = 'button'; button.title = tab.name;
      button.setAttribute('aria-label', tab.name); button.setAttribute('aria-pressed', String(activeStickerTab === tab.id));
      if (tab.iconUrl && tab.iconUrl !== '/stickers/original/characters.png') button.appendChild(stickerArt({renderType: 'IMAGE', imageUrl: tab.iconUrl, name: tab.name}));
      else if (tab.stickers && tab.stickers[0]) button.appendChild(stickerArt(tab.stickers[0])); else button.textContent = tab.icon;
      if (tab.accessType === 'AWARD') {
        var gift = document.createElement('span'); gift.className = 'local-sticker-award'; gift.textContent = '🎁'; gift.setAttribute('aria-hidden', 'true'); button.appendChild(gift);
        button.setAttribute('aria-label', tab.name + ' · Phần thưởng'); button.title = tab.name + ' · Phần thưởng';
      }
      button.addEventListener('click', function () { activeStickerTab = tab.id; stickerSearch.value = ''; renderStickerTabs(); renderLocalStickers(); });
      stickerTabs.appendChild(button);
    });
    if (restoreFocus) { var selectedTab = stickerTabs.querySelector('[aria-pressed="true"]'); if (selectedTab) selectedTab.focus(); }
  }

  function renderLocalStickers() {
    stickerGrid.replaceChildren(); stickerRetry.hidden = true;
    var all = library.packs.flatMap(function (pack) { return pack.stickers; });
    var query = normalizeStickerText(stickerSearch.value.trim());
    var selected = library.packs.find(function (pack) { return pack.id === activeStickerTab; });
    var items;
    if (query) items = all.filter(function (sticker) { return normalizeStickerText(sticker.name + ' ' + (sticker.keywords || '')).includes(query); });
    else if (activeStickerTab === 'recent' || activeStickerTab === 'favorites') items = library[activeStickerTab].map(function (id) { return all.find(function (s) { return s.id === id; }); }).filter(Boolean);
    else items = selected ? selected.stickers : [];
    document.getElementById('sticker-section-title').textContent = query ? 'Kết quả tìm kiếm' : activeStickerTab === 'recent' ? 'Gần đây' : activeStickerTab === 'favorites' ? 'Yêu thích' : (selected || {}).name || 'Nhãn dán';
    if (!query && selected && selected.accessType === 'AWARD') document.getElementById('sticker-section-title').textContent += ' · 🎁 Phần thưởng';
    stickerStatus.textContent = items.length ? '' : query ? 'Không tìm thấy nhãn dán phù hợp.' : activeStickerTab === 'favorites' ? 'Nhấn ngôi sao trên nhãn dán để thêm vào yêu thích.' : 'Những nhãn dán bạn gửi sẽ xuất hiện ở đây.';
    items.forEach(function (sticker) {
      var cell = document.createElement('div'); cell.className = 'local-sticker-cell';
      cell.dataset.stickerId = sticker.id;
      var send = document.createElement('button'); send.type = 'button'; send.className = 'local-sticker-send'; send.title = sticker.name;
      send.setAttribute('aria-label', 'Gửi ' + sticker.name); send.appendChild(stickerArt(sticker));
      send.addEventListener('click', function () { sendLocalSticker(sticker.id); });
      var favorite = document.createElement('button'); favorite.type = 'button'; favorite.className = 'local-sticker-favorite';
      var saved = library.favorites.includes(sticker.id); favorite.textContent = saved ? '★' : '☆'; favorite.setAttribute('aria-pressed', String(saved));
      favorite.setAttribute('aria-label', (saved ? 'Bỏ yêu thích ' : 'Yêu thích ') + sticker.name);
      favorite.addEventListener('click', async function () {
        favorite.disabled = true;
        try {
          var response = await window.Aloute.api('/api/stickers/favorites/' + encodeURIComponent(sticker.id), {method: saved ? 'DELETE' : 'POST'});
          if (!response.ok) throw new Error('favorite');
          library.favorites = saved ? library.favorites.filter(function (id) { return id !== sticker.id; }) : [sticker.id].concat(library.favorites);
          renderLocalStickers();
          var matching = Array.from(stickerGrid.children).find(function (item) { return item.dataset.stickerId === sticker.id; });
          if (matching) matching.querySelector('.local-sticker-favorite').focus(); else stickerSearch.focus();
        } catch (error) { favorite.disabled = false; window.Aloute.toast('Chưa lưu được yêu thích, thử lại nhé.', 'error'); }
      });
      cell.append(send, favorite); stickerGrid.appendChild(cell);
    });
  }

  async function sendLocalSticker(id) {
    if (sendingSticker) return;
    sendingSticker = true;
    var reply = window.chatReplyState;
    var body = new FormData(); body.append('stickerId', id);
    if (reply) body.append('replyHeader', '[reply:' + reply.messageId + ':@' + reply.senderName + ': "' + reply.snippet + '"]');
    try {
      var response = await window.Aloute.api('/api/conversations/' + conversationId + '/stickers', {method: 'POST', body: body});
      if (!response.ok) {
        var failure = await response.json().catch(function () { return {}; });
        throw new Error(failure.message || 'Chưa gửi được nhãn dán, bạn thử lại nhé.');
      }
      library.recent = [id].concat(library.recent.filter(function (value) { return value !== id; })).slice(0, 24);
      if (reply && typeof cancelReply === 'function') cancelReply();
      closeLocalStickers(false);
      textarea.focus();
    } catch (error) { window.Aloute.toast(error.message || 'Chưa gửi được nhãn dán, bạn thử lại nhé.', 'error'); }
    finally { sendingSticker = false; }
  }

  function closeLocalStickers(focus) {
    stickerPicker.classList.add('d-none'); stickerBtn.setAttribute('aria-expanded', 'false'); if (focus) stickerBtn.focus();
  }
  if (stickerBtn && stickerPicker) {
    stickerPicker.addEventListener('click', function (event) { event.stopPropagation(); });
    stickerBtn.addEventListener('click', function () {
      var open = stickerPicker.classList.contains('d-none');
      stickerPicker.classList.toggle('d-none', !open); stickerBtn.setAttribute('aria-expanded', String(open));
      if (open) { stickerSearch.focus(); loadStickerLibrary(); }
    });
    document.getElementById('sticker-close').addEventListener('click', function () { closeLocalStickers(true); });
    stickerSearch.addEventListener('input', renderLocalStickers);
    stickerRetry.addEventListener('click', loadStickerLibrary);
    document.addEventListener('keydown', function (event) { if (event.key === 'Escape' && !stickerPicker.classList.contains('d-none')) closeLocalStickers(true); });
    document.addEventListener('click', function (event) { if (!stickerPicker.contains(event.target) && !stickerBtn.contains(event.target)) closeLocalStickers(false); });
  }
})();
