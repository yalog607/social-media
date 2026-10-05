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

    var bubble = document.createElement('span');
    bubble.className = 'bubble ' + (mine ? 'bubble--out' : 'bubble--in');
    bubble.innerHTML = attachmentHtml(m.attachment) + (m.contentHtml || '');
    body.appendChild(bubble);

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

  function connect() {
    var socket = new SockJS('/ws');
    var client = Stomp.over(socket);
    client.debug = null;
    client.connect({}, function () {
      client.subscribe('/topic/conversations/' + conversationId, function (frame) {
        appendMessage(JSON.parse(frame.body));
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
