/* ALOUTE — trang một cuộc trò chuyện: nhận tin nhắn mới qua WebSocket (STOMP/SockJS), gửi tin qua fetch thường.
   Máy chủ đẩy MỌI tin nhắn mới (kể cả của chính mình) qua cùng một kênh, nên chỉ cần một chỗ để chèn bong bóng chat. */
(function () {
  'use strict';

  var main = document.getElementById('chat-main');
  if (!main) return;

  var conversationId = main.dataset.conversationId;
  var myId = main.dataset.userId;
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
      if (!response.ok) throw new Error('HTTP ' + response.status);
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
    if (m.sender.avatarUrl) {
      var img = document.createElement('img');
      img.src = m.sender.avatarUrl;
      img.loading = 'lazy';
      img.alt = '';
      avatar.appendChild(img);
    } else {
      avatar.textContent = (m.sender.displayName || '?').charAt(0).toUpperCase();
    }
    msg.appendChild(avatar);

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
      indicator.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><path d="M10 9V5l-7 7 7 7v-4.1c5 0 8.5 1.6 11 5.1-1-5-4-10-11-11z"/></svg><span>' + escapeText(titleText) + '</span>';
      
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
    reactBtn.innerHTML = '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><path d="M8 14s1.5 2 4 2 4-2 4-2"></path><line x1="9" y1="9" x2="9.01" y2="9"></line><line x1="15" y1="15.01" y2="9"></line></svg>';

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
    replyBtn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M10 9V5l-7 7 7 7v-4.1c5 0 8.5 1.6 11 5.1-1-5-4-10-11-11z"/></svg>';
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
    moreBtn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="5" r="2.2"/><circle cx="12" cy="12" r="2.2"/><circle cx="12" cy="19" r="2.2"/></svg>';

    var dropdownMenu = document.createElement('ul');
    dropdownMenu.className = 'dropdown-menu menu-al msg-dropdown-menu' + (mine ? ' dropdown-menu-end' : '');
    var menuHtml = '';
    if (mine) {
      menuHtml += '<li><button class="dropdown-item" type="button" onclick="editMessage(\'' + m.id + '\')"><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"></path><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path></svg><span>Sửa tin nhắn</span></button></li>';
    }
    menuHtml += '<li><button class="dropdown-item" type="button" onclick="copyMessageText(\'' + m.id + '\')"><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg><span>Sao chép văn bản</span></button></li>';
    if (mine) {
      menuHtml += '<li><hr class="dropdown-divider my-1"></li><li><button class="dropdown-item text-danger" type="button" onclick="confirmDeleteMessage(\'' + conversationId + '\', \'' + m.id + '\')"><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg><span>Xóa tin nhắn</span></button></li>';
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
      window.Aloute.toast('Chưa gửi được tin nhắn, thử lại nhé.', 'error');
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
})();
