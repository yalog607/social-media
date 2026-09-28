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
  scrollToBottom();

  // ---------- Nhận tin nhắn realtime ----------
  function appendMessage(m) {
    var mine = String(m.sender.id) === String(myId);
    var msg = document.createElement('div');
    msg.className = 'msg' + (mine ? ' is-mine' : '');

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
      var sender = document.createElement('span');
      sender.className = 'msg-sender';
      sender.textContent = m.sender.displayName;
      body.appendChild(sender);
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
    chatWindow.appendChild(msg);
    scrollToBottom();
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

  // ---------- Đính kèm file ----------
  var attachedFile = null;
  attachBtn.addEventListener('click', function () { attachInput.click(); });
  attachInput.addEventListener('change', function () {
    attachedFile = attachInput.files[0] || null;
    attachPreview.hidden = !attachedFile;
    attachName.textContent = attachedFile ? attachedFile.name : '';
  });
  attachClear.addEventListener('click', function () {
    attachedFile = null;
    attachInput.value = '';
    attachPreview.hidden = true;
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
      attachedFile = null;
      attachInput.value = '';
      attachPreview.hidden = true;
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
