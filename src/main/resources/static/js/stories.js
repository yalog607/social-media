/* ALOUTE — Quản lý Khoảnh Khắc 24H (Ephemeral Stories). */
(function () {
  'use strict';

  let currentUserId = null;
  let storiesList = [];
  let currentIndex = 0;
  let timerId = null;
  let isPaused = false;
  let startTime = 0;
  let elapsed = 0;
  let storyDuration = 5000;
  let currentVideoEl = null;

  const pastelColors = {
    lavender: '#E8DEFF',
    lemon: '#FFF3C4',
    rose: '#FFD6E0',
    sky: '#D0F0FD',
    mint: '#C8F7DC',
    peach: '#FFE3D8'
  };

  // ==================== KHỞI TẠO SỰ KIỆN GIAO DIỆN ====================
  document.addEventListener('DOMContentLoaded', function () {
    initTrayScroll();
    initTrayClicks();
    initCreateModal();
    initViewerControls();
    initChatStoryBadgeClicks();
    checkDirectStoryUrl();
  });

  function checkDirectStoryUrl() {
    const urlParams = new URLSearchParams(window.location.search);
    const targetStoryId = urlParams.get('viewStory');
    const authorId = urlParams.get('author');
    if (authorId) {
      openStoryViewer(authorId, '', targetStoryId);
    }
  }

  function initChatStoryBadgeClicks() {
    document.addEventListener('click', async function (e) {
      const badge = e.target.closest('.story-chat-badge');
      if (!badge) return;
      const storyId = badge.dataset.storyId;
      if (!storyId) return;

      const viewerModalEl = document.getElementById('storyViewerModal');
      if (viewerModalEl) {
        e.preventDefault();
        try {
          const resp = await window.Aloute.api('/stories/api/' + storyId);
          if (resp.ok) {
            const story = await resp.json();
            currentUserId = story.authorId;
            storiesList = [story];
            currentIndex = 0;
            const modal = bootstrap.Modal.getOrCreateInstance(viewerModalEl);
            modal.show();
            setupProgressBars();
            playStory(0);
          } else {
            window.location.href = badge.getAttribute('href') || '/';
          }
        } catch (err) {
          window.location.href = badge.getAttribute('href') || '/';
        }
      }
    });
  }

  // ==================== 1. THANH CUỘN STORY TRAY ====================
  function initTrayScroll() {
    const track = document.getElementById('storyTrack');
    const btnLeft = document.querySelector('.story-scroll-left');
    const btnRight = document.querySelector('.story-scroll-right');
    if (!track || !btnLeft || !btnRight) return;

    function updateArrows() {
      if (track.scrollWidth > track.clientWidth) {
        btnLeft.classList.toggle('d-none', track.scrollLeft <= 5);
        btnRight.classList.toggle('d-none', track.scrollLeft >= track.scrollWidth - track.clientWidth - 5);
      } else {
        btnLeft.classList.add('d-none');
        btnRight.classList.add('d-none');
      }
    }

    track.addEventListener('scroll', updateArrows);
    window.addEventListener('resize', updateArrows);
    setTimeout(updateArrows, 300);

    btnLeft.addEventListener('click', () => track.scrollBy({ left: -160, behavior: 'smooth' }));
    btnRight.addEventListener('click', () => track.scrollBy({ left: 160, behavior: 'smooth' }));
  }

  function initTrayClicks() {
    document.addEventListener('click', function (e) {
      const trigger = e.target.closest('.open-story-viewer-btn');
      if (!trigger) return;
      const userId = trigger.dataset.userId;
      const authorName = trigger.dataset.authorName || '';
      if (userId) {
        openStoryViewer(userId, authorName);
      }
    });
  }

  // ==================== 2. TRÌNH PHÁT STORY VIEWER ====================
  async function openStoryViewer(userId, authorName, targetStoryId) {
    currentUserId = userId;
    try {
      const resp = await window.Aloute.api('/stories/api/users/' + userId);
      if (!resp.ok) {
        window.Aloute.toast('Không thể tải khoảnh khắc.', 'error');
        return;
      }
      storiesList = await resp.json();
      if (!storiesList || storiesList.length === 0) {
        window.Aloute.toast('Người này hiện không có khoảnh khắc nào trong 24h qua.', 'warn');
        return;
      }

      // Luôn ưu tiên đưa story là VIBE lên vị trí đầu tiên (index 0)
      storiesList.sort((a, b) => {
        const aIsVibe = a.mediaType === 'VIBE' || a.isVibe || a.vibe || (a.emoji && a.emoji.trim() && a.mediaType === 'TEXT');
        const bIsVibe = b.mediaType === 'VIBE' || b.isVibe || b.vibe || (b.emoji && b.emoji.trim() && b.mediaType === 'TEXT');
        if (aIsVibe && !bIsVibe) return -1;
        if (!aIsVibe && bIsVibe) return 1;
        return new Date(a.createdAt) - new Date(b.createdAt);
      });

      currentIndex = 0;
      if (targetStoryId) {
        const foundIdx = storiesList.findIndex(s => s.id === targetStoryId);
        if (foundIdx !== -1) {
          currentIndex = foundIdx;
        }
      }
      setupProgressBars();

      const viewerModalEl = document.getElementById('storyViewerModal');
      const modal = bootstrap.Modal.getOrCreateInstance(viewerModalEl);
      modal.show();

      playStory(currentIndex);
    } catch (err) {
      console.error(err);
      window.Aloute.toast('Có lỗi xảy ra khi mở story.', 'error');
    }
  }

  function setupProgressBars() {
    const container = document.getElementById('storyProgressContainer');
    if (!container) return;
    container.innerHTML = '';
    for (let i = 0; i < storiesList.length; i++) {
      const seg = document.createElement('div');
      seg.className = 'story-progress-seg';
      seg.id = 'storySeg_' + i;
      const fill = document.createElement('div');
      fill.className = 'story-progress-seg-fill';
      fill.id = 'storyFill_' + i;
      seg.appendChild(fill);
      container.appendChild(seg);
    }
  }

  function playStory(index) {
    if (index < 0 || index >= storiesList.length) {
      closeViewer();
      return;
    }
    closeStoryViewersSheet();
    currentIndex = index;
    const story = storiesList[index];

    // Dọn dẹp timer cũ và video cũ
    cancelTimer();
    if (currentVideoEl) {
      currentVideoEl.pause();
      currentVideoEl = null;
    }

    // Cập nhật các thanh progress
    for (let i = 0; i < storiesList.length; i++) {
      const seg = document.getElementById('storySeg_' + i);
      const fill = document.getElementById('storyFill_' + i);
      if (!seg || !fill) continue;
      if (i < index) {
        seg.classList.add('done');
        fill.style.width = '100%';
      } else if (i > index) {
        seg.classList.remove('done');
        fill.style.width = '0%';
      } else {
        seg.classList.remove('done');
        fill.style.width = '0%';
      }
    }

    // Cập nhật Header
    const avatarEl = document.getElementById('viewerAuthorAvatar');
    const nameEl = document.getElementById('viewerAuthorName');
    const timeEl = document.getElementById('viewerTimeAgo');
    const deleteBtn = document.getElementById('storyDeleteBtn');

    if (avatarEl) {
      avatarEl.src = story.authorAvatarUrl || '/logo-proposals/aloute-sticker-icon.svg';
    }
    if (nameEl) {
      nameEl.textContent = story.authorDisplayName;
    }
    if (timeEl) {
      timeEl.textContent = story.timeAgo || 'vừa xong';
    }

    // Cập nhật thanh tương tác dưới cùng
    const replyBar = document.getElementById('storyReplyBar');
    const ownerBar = document.getElementById('storyOwnerBar');
    const replyInput = document.getElementById('storyReplyInput');
    const viewCountSpan = document.getElementById('storyViewCount');
    const viewerModalEl = document.getElementById('storyViewerModal');
    const currentMyId = viewerModalEl ? viewerModalEl.dataset.myId : '';

    const isOwner = Boolean(
      (story.authorId && currentMyId && String(story.authorId) === String(currentMyId)) ||
      story.isOwner === true ||
      story.isOwner === 'true' ||
      story.owner === true
    );

    if (isOwner) {
      if (replyBar) replyBar.classList.add('d-none');
      if (ownerBar) ownerBar.classList.remove('d-none');
      if (deleteBtn) {
        deleteBtn.classList.remove('d-none');
        deleteBtn.style.display = 'inline-flex';
      }
      if (viewCountSpan) viewCountSpan.textContent = story.viewCount || 0;
    } else {
      if (replyBar) replyBar.classList.remove('d-none');
      if (ownerBar) ownerBar.classList.add('d-none');
      if (deleteBtn) {
        deleteBtn.classList.add('d-none');
        deleteBtn.style.display = 'none';
      }
      if (replyInput) replyInput.placeholder = 'Gửi tin nhắn cho ' + story.authorDisplayName + '...';
    }

    // Hiển thị nội dung Media & Lớp Ambient Blur
    const ambientBg = document.getElementById('viewerAmbientBg');
    const imgEl = document.getElementById('viewerImage');
    const vidEl = document.getElementById('viewerVideo');
    const textCardEl = document.getElementById('viewerTextCard');
    const textEmojiEl = document.getElementById('viewerTextEmoji');
    const textContentEl = document.getElementById('viewerTextContent');
    const captionOverlay = document.getElementById('viewerCaptionOverlay');
    const captionText = document.getElementById('viewerCaptionText');

    imgEl.classList.add('d-none');
    vidEl.classList.add('d-none');
    textCardEl.classList.add('d-none');
    captionOverlay.classList.add('d-none');

    if (story.mediaType === 'IMAGE') {
      imgEl.src = story.mediaUrl;
      imgEl.classList.remove('d-none');
      if (ambientBg) {
        ambientBg.style.backgroundImage = 'url("' + story.mediaUrl + '")';
        ambientBg.style.opacity = '0.85';
      }
      storyDuration = 5000;
      if (story.caption && story.caption.trim()) {
        captionText.textContent = story.caption;
        captionOverlay.classList.remove('d-none');
      }
      startProgressTimer();
    } else if (story.mediaType === 'VIDEO') {
      vidEl.src = story.mediaUrl;
      vidEl.classList.remove('d-none');
      if (ambientBg) {
        ambientBg.style.backgroundImage = 'none';
        ambientBg.style.opacity = '0';
      }
      currentVideoEl = vidEl;
      vidEl.currentTime = 0;
      vidEl.muted = false;
      vidEl.play().catch(() => {
        vidEl.muted = true;
        vidEl.play().catch(() => { });
      });

      if (story.caption && story.caption.trim()) {
        captionText.textContent = story.caption;
        captionOverlay.classList.remove('d-none');
      }

      vidEl.onloadedmetadata = function () {
        storyDuration = Math.max(3000, Math.min(30000, (vidEl.duration || 5) * 1000));
        startProgressTimer();
      };
      vidEl.onended = function () {
        nextStory();
      };
    } else { // TEXT hoặc VIBE
      const isVibeStory = story.mediaType === 'VIBE' || story.isVibe || story.vibe || (story.emoji && story.emoji.trim() && story.mediaType === 'TEXT');
      const bgHex = pastelColors[story.backgroundColor] || '#E8DEFF';
      textCardEl.style.background = bgHex;
      textContentEl.style.color = '#16121F';

      const tapeEl = document.getElementById('viewerTextTape');
      const vibeCardBox = document.getElementById('viewerVibeCardBox');

      if (isVibeStory) {
        if (tapeEl) {
          tapeEl.textContent = '🌈 VIBE HÔM NAY';
          tapeEl.classList.add('story-note-tape--vibe');
        }
        if (vibeCardBox) {
          vibeCardBox.classList.add('story-vibe-card-mode');
        }
        textContentEl.textContent = '“ ' + story.caption + ' ”';
      } else {
        if (tapeEl) {
          tapeEl.textContent = '📌 GHI CHÚ';
          tapeEl.classList.remove('story-note-tape--vibe');
        }
        if (vibeCardBox) {
          vibeCardBox.classList.remove('story-vibe-card-mode');
        }
        textContentEl.textContent = story.caption;
      }

      if (textEmojiEl) {
        if (story.emoji && story.emoji.trim()) {
          textEmojiEl.textContent = story.emoji.trim();
          textEmojiEl.classList.remove('d-none');
        } else {
          textEmojiEl.classList.add('d-none');
        }
      }

      textCardEl.classList.remove('d-none');
      if (ambientBg) {
        ambientBg.style.backgroundImage = 'none';
        ambientBg.style.backgroundColor = bgHex;
        ambientBg.style.opacity = '0.35';
      }
      storyDuration = 5000;
      startProgressTimer();
    }

    // Ghi nhận lượt xem qua API
    window.Aloute.api('/stories/api/' + story.id + '/view', { method: 'POST' }).catch(() => { });

    // Kết nối WebSocket để người xem và người đăng thấy cảm xúc bay lên theo thời gian thực (Hình 1)
    initStoryWebSocket(story.id);

    // Nếu tin đã có người thả cảm xúc trước đó, hiện bay lên tất cả cảm xúc cho người xem / người đăng thấy (Hình 1)
    if (story.reactions && story.reactions.length > 0) {
      setTimeout(() => {
        story.reactions.forEach((r, idx) => {
          setTimeout(() => spawnReactionBurst(r), idx * 180);
        });
      }, 350);
    }
  }

  function startProgressTimer() {
    cancelTimer();
    isPaused = false;
    elapsed = 0;
    startTime = performance.now();
    updatePauseButtonUI();

    function step(now) {
      if (isPaused) {
        startTime = now - elapsed;
        timerId = requestAnimationFrame(step);
        return;
      }
      elapsed = now - startTime;
      const progress = Math.min(100, (elapsed / storyDuration) * 100);
      const fillEl = document.getElementById('storyFill_' + currentIndex);
      if (fillEl) {
        fillEl.style.width = progress + '%';
      }

      if (elapsed >= storyDuration) {
        nextStory();
      } else {
        timerId = requestAnimationFrame(step);
      }
    }

    timerId = requestAnimationFrame(step);
  }

  function cancelTimer() {
    if (timerId) {
      cancelAnimationFrame(timerId);
      timerId = null;
    }
  }

  function pauseStory() {
    isPaused = true;
    if (currentVideoEl && !currentVideoEl.paused) {
      currentVideoEl.pause();
    }
    updatePauseButtonUI();
  }

  function resumeStory() {
    isPaused = false;
    if (currentVideoEl && currentVideoEl.paused) {
      currentVideoEl.play().catch(() => { });
    }
    updatePauseButtonUI();
  }

  function togglePause() {
    if (isPaused) {
      resumeStory();
    } else {
      pauseStory();
    }
  }

  function updatePauseButtonUI() {
    const btn = document.getElementById('storyPauseBtn');
    if (btn) {
      if (isPaused) {
        btn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="6 4 20 12 6 20 6 4"/></svg>';
      } else {
        btn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/></svg>';
      }
    }
  }

  function nextStory() {
    if (currentIndex + 1 < storiesList.length) {
      playStory(currentIndex + 1);
    } else {
      closeViewer();
    }
  }

  function prevStory() {
    if (currentIndex > 0) {
      playStory(currentIndex - 1);
    } else {
      playStory(0);
    }
  }

  function closeViewer(e) {
    if (e) {
      if (typeof e.preventDefault === 'function') e.preventDefault();
      if (typeof e.stopPropagation === 'function') e.stopPropagation();
    }
    closeStoryViewersSheet();
    cancelTimer();
    isPaused = false;
    if (currentVideoEl) {
      try { currentVideoEl.pause(); } catch (_) { }
      currentVideoEl = null;
    }
    const viewerModalEl = document.getElementById('storyViewerModal');
    if (viewerModalEl) {
      try {
        const modal = bootstrap.Modal.getInstance(viewerModalEl) || bootstrap.Modal.getOrCreateInstance(viewerModalEl);
        if (modal) {
          modal.hide();
        }
      } catch (err) {
        console.warn('Bootstrap modal.hide error:', err);
      }

      viewerModalEl.classList.remove('show');
      viewerModalEl.setAttribute('aria-hidden', 'true');
      viewerModalEl.removeAttribute('aria-modal');
      viewerModalEl.removeAttribute('role');
      viewerModalEl.style.display = 'none';

      document.body.classList.remove('modal-open');
      document.body.style.removeProperty('overflow');
      document.body.style.removeProperty('padding-right');

      const backdrops = document.querySelectorAll('.modal-backdrop');
      backdrops.forEach(b => b.remove());
    }
  }

  // ==================== 3. ĐIỀU KHIỂN & TƯƠNG TÁC VIEWER ====================
  function initViewerControls() {
    const tapLeft = document.querySelector('.story-tap-left');
    const tapRight = document.querySelector('.story-tap-right');
    const pauseBtn = document.getElementById('storyPauseBtn');
    const deleteBtn = document.getElementById('storyDeleteBtn');
    const closeBtn = document.getElementById('storyCloseBtn');
    const viewport = document.getElementById('storyViewport');
    const viewerModalEl = document.getElementById('storyViewerModal');

    if (tapLeft) tapLeft.addEventListener('click', prevStory);
    if (tapRight) tapRight.addEventListener('click', nextStory);

    if (pauseBtn) {
      pauseBtn.addEventListener('mousedown', function (e) { e.stopPropagation(); });
      pauseBtn.addEventListener('click', function (e) {
        e.preventDefault();
        e.stopPropagation();
        togglePause();
      });
    }

    if (closeBtn) {
      closeBtn.addEventListener('mousedown', function (e) { e.stopPropagation(); });
      closeBtn.addEventListener('click', function (e) {
        e.preventDefault();
        e.stopPropagation();
        closeViewer(e);
      });
    }

    // Bắt sự kiện click vào nút đóng qua delegation (kể cả click vào svg/line bên trong)
    document.addEventListener('click', function (e) {
      const btn = e.target.closest('#storyCloseBtn');
      if (btn) {
        e.preventDefault();
        e.stopPropagation();
        closeViewer(e);
      }
    });

    // Giữ chuột / chạm màn hình để tạm dừng (chỉ trên viewport thực tế)
    if (viewport) {
      viewport.addEventListener('mousedown', function (e) {
        if (e.target.closest('.story-header') || e.target.closest('.story-ctrl-btn') || e.target.closest('.story-reaction-particles')) {
          return;
        }
        pauseStory();
      });
      viewport.addEventListener('mouseup', function (e) {
        if (e.target.closest('.story-header') || e.target.closest('.story-ctrl-btn')) return;
        resumeStory();
      });
      viewport.addEventListener('touchstart', function (e) {
        if (e.target.closest('.story-header') || e.target.closest('.story-ctrl-btn')) return;
        pauseStory();
      }, { passive: true });
      viewport.addEventListener('touchend', function (e) {
        if (e.target.closest('.story-header') || e.target.closest('.story-ctrl-btn')) return;
        resumeStory();
      }, { passive: true });
    }

    // Điều khiển bàn phím
    document.addEventListener('keydown', function (e) {
      if (!viewerModalEl || !viewerModalEl.classList.contains('show')) return;
      if (document.activeElement && document.activeElement.tagName === 'INPUT') return;

      if (e.key === 'ArrowRight') {
        nextStory();
      } else if (e.key === 'ArrowLeft') {
        prevStory();
      } else if (e.key === ' ' || e.code === 'Space') {
        e.preventDefault();
        togglePause();
      } else if (e.key === 'Escape') {
        closeViewer();
      }
    });

    if (viewerModalEl) {
      viewerModalEl.addEventListener('hidden.bs.modal', function () {
        cancelTimer();
        if (currentVideoEl) {
          currentVideoEl.pause();
          currentVideoEl = null;
        }
      });
    }

    // Xóa Story với Modal tùy chỉnh ALOUTE Neo-Brutalism (Hình 1)
    const deleteModalEl = document.getElementById('storyDeleteConfirmModal');
    const confirmDeleteBtn = document.getElementById('confirmDeleteStoryBtn');
    let deleteConfirmModal = null;
    if (deleteModalEl) {
      deleteConfirmModal = bootstrap.Modal.getOrCreateInstance(deleteModalEl);
      deleteModalEl.addEventListener('hidden.bs.modal', function () {
        resumeStory();
      });
    }

    if (deleteBtn) {
      deleteBtn.addEventListener('mousedown', function (e) { e.stopPropagation(); });
      deleteBtn.addEventListener('click', function (e) {
        e.preventDefault();
        e.stopPropagation();
        pauseStory();
        if (deleteConfirmModal) {
          deleteConfirmModal.show();
        }
      });
    }

    if (confirmDeleteBtn) {
      confirmDeleteBtn.addEventListener('click', async function () {
        const currentStory = storiesList[currentIndex];
        if (!currentStory) return;

        confirmDeleteBtn.disabled = true;
        confirmDeleteBtn.textContent = 'Đang xóa...';

        try {
          const resp = await window.Aloute.api('/stories/api/' + currentStory.id + '/delete', { method: 'POST' });
          if (deleteConfirmModal) {
            deleteConfirmModal.hide();
          }
          if (resp.ok) {
            window.Aloute.toast('Đã xóa khoảnh khắc thành công.', 'ok');
            const wasVibe = currentStory.mediaType === 'VIBE' || Boolean(currentStory.isVibe) || Boolean(currentStory.vibe) || (currentStory.emoji && currentStory.emoji.trim());
            if (wasVibe) {
              const myVibeBubbles = document.querySelectorAll('.story-vibe-bubble, .mood-emoji-badge, .profile-mood-pill');
              myVibeBubbles.forEach(b => b.remove());
            }

            storiesList.splice(currentIndex, 1);
            if (storiesList.length === 0 || wasVibe) {
              closeViewer();
              window.location.reload();
            } else {
              setupProgressBars();
              playStory(Math.min(currentIndex, storiesList.length - 1));
            }
          } else {
            window.Aloute.toast('Không thể xóa khoảnh khắc.', 'error');
            resumeStory();
          }
        } catch (err) {
          window.Aloute.toast('Lỗi kết nối khi xóa.', 'error');
          resumeStory();
        } finally {
          confirmDeleteBtn.disabled = false;
          confirmDeleteBtn.textContent = 'Xóa luôn';
        }
      });
    }

    // Gửi phản hồi qua Chat 1-1 (CHỈ gửi khi người dùng gõ tin nhắn và bấm nút Gửi - Hình 2)
    const replyForm = document.getElementById('storyReplyForm');
    const replyInput = document.getElementById('storyReplyInput');
    if (replyForm) {
      replyForm.addEventListener('submit', async function (e) {
        e.preventDefault();
        const text = replyInput.value ? replyInput.value.trim() : '';
        if (!text) return;

        const currentStory = storiesList[currentIndex];
        if (!currentStory) return;

        await sendStoryReply(currentStory.id, text, currentStory.authorDisplayName);
        replyInput.value = '';
      });
    }

    // Chạm emoji phản ứng nhanh: Bay trực tiếp lên tin, KHÔNG gửi tin nhắn chat (Hình 2)
    document.addEventListener('click', function (e) {
      const btn = e.target.closest('.story-emoji-btn');
      if (!btn) return;
      const emoji = btn.dataset.emoji;
      const currentStory = storiesList[currentIndex];
      if (!currentStory || !emoji) return;

      // 1. Hiệu ứng phóng to nút emoji
      btn.style.transform = 'scale(1.4)';
      setTimeout(() => btn.style.transform = '', 160);

      // 2. Bắn trực tiếp các hạt cảm xúc bay lên màn hình tin
      spawnReactionBurst(emoji);

      // 3. Ghi nhận cảm xúc lên backend cho story (không gửi tin nhắn chat)
      recordStoryReaction(currentStory.id, emoji);
    });

    // Mở Bottom Sheet danh sách người đã xem (hiện từ dưới lên)
    const openViewersBtn = document.getElementById('openViewersBtn');
    if (openViewersBtn) {
      openViewersBtn.addEventListener('click', async function () {
        const currentStory = storiesList[currentIndex];
        if (!currentStory) return;
        pauseStory();
        await loadStoryViewers(currentStory.id);
      });
    }

    // Đóng Bottom Sheet người xem
    const closeSheetBtn = document.getElementById('closeViewersSheetBtn');
    if (closeSheetBtn) {
      closeSheetBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        closeStoryViewersSheet();
      });
    }

    const closeSheetHandle = document.getElementById('closeViewersSheetHandle');
    if (closeSheetHandle) {
      closeSheetHandle.addEventListener('click', function (e) {
        e.stopPropagation();
        closeStoryViewersSheet();
      });
    }

    const sheetBackdrop = document.getElementById('storyViewersBackdrop');
    if (sheetBackdrop) {
      sheetBackdrop.addEventListener('click', function (e) {
        e.stopPropagation();
        closeStoryViewersSheet();
      });
    }
  }

  async function sendStoryReply(storyId, content, authorName) {
    try {
      const formData = new URLSearchParams();
      formData.append('content', content);

      const resp = await window.Aloute.api('/stories/api/' + storyId + '/reply', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: formData.toString()
      });

      if (resp.ok) {
        window.Aloute.toast('Đã gửi phản hồi đến ' + authorName + ' vào mục Tin nhắn!', 'ok');
      } else {
        const data = await resp.json().catch(() => ({}));
        window.Aloute.toast(data.error || 'Không thể gửi phản hồi.', 'error');
      }
    } catch (err) {
      window.Aloute.toast('Lỗi kết nối khi gửi phản hồi.', 'error');
    }
  }

  function spawnReactionBurst(emoji) {
    const container = document.getElementById('reactionParticles');
    if (!container) return;
    for (let i = 0; i < 6; i++) {
      setTimeout(() => {
        const p = document.createElement('span');
        p.className = 'story-reaction-particle';
        p.textContent = emoji;
        const left = 15 + Math.random() * 70; // rải đều 15% - 85%
        const rot = (Math.random() - 0.5) * 44; // xoay ngẫu nhiên
        const size = 1.8 + Math.random() * 1.4; // 1.8rem - 3.2rem
        p.style.left = left + '%';
        p.style.setProperty('--rot', rot + 'deg');
        p.style.fontSize = size + 'rem';
        container.appendChild(p);
        setTimeout(() => p.remove(), 1600);
      }, i * 65);
    }
  }

  function spawnReactionParticle(emoji) {
    spawnReactionBurst(emoji);
  }

  async function recordStoryReaction(storyId, emoji) {
    try {
      await window.Aloute.api('/stories/api/' + storyId + '/react?emoji=' + encodeURIComponent(emoji), {
        method: 'POST'
      });
    } catch (ignored) { }
  }

  // ---------- WebSocket Realtime cho Khoảnh Khắc (Cảm xúc bay trực tiếp cả bên người đăng và người xem - Hình 1) ----------
  let storyStompClient = null;
  let currentStorySub = null;

  function initStoryWebSocket(storyId) {
    if (!window.SockJS || !window.Stomp) return;
    if (currentStorySub) {
      try { currentStorySub.unsubscribe(); } catch (e) { }
      currentStorySub = null;
    }

    function doSubscribe() {
      if (!storyStompClient || !storyStompClient.connected) return;
      currentStorySub = storyStompClient.subscribe('/topic/stories/' + storyId + '/reactions', function (frame) {
        try {
          const data = JSON.parse(frame.body);
          if (data && data.emoji) {
            // Khi bất kỳ ai thả cảm xúc (kể cả người khác), bên người đăng đang xem lập tức bay lên hàng loạt cảm xúc (Hình 1)
            spawnReactionBurst(data.emoji);
            const currentStory = storiesList[currentIndex];
            if (currentStory && currentStory.id === storyId) {
              if (!currentStory.reactions) currentStory.reactions = [];
              currentStory.reactions.push(data.emoji);
            }
          }
        } catch (e) { }
      });
    }

    if (storyStompClient && storyStompClient.connected) {
      doSubscribe();
    } else {
      try {
        const socket = new SockJS('/ws');
        storyStompClient = Stomp.over(socket);
        storyStompClient.debug = null;
        storyStompClient.connect({}, function () {
          doSubscribe();
        }, function () {
          setTimeout(() => initStoryWebSocket(storyId), 5000);
        });
      } catch (err) { }
    }
  }

  // ---------- Quản lý Bottom Sheet Người xem (Hiện trượt từ dưới lên) ----------
  function openStoryViewersSheet() {
    const sheet = document.getElementById('storyViewersSheet');
    const backdrop = document.getElementById('storyViewersBackdrop');
    if (!sheet) return;
    pauseStory();
    if (backdrop) {
      backdrop.classList.remove('d-none');
      void backdrop.offsetWidth;
      backdrop.classList.add('show');
    }
    sheet.classList.add('show');
  }

  function closeStoryViewersSheet() {
    const sheet = document.getElementById('storyViewersSheet');
    const backdrop = document.getElementById('storyViewersBackdrop');
    if (!sheet || !sheet.classList.contains('show')) return;
    sheet.classList.remove('show');
    if (backdrop) {
      backdrop.classList.remove('show');
      setTimeout(() => {
        if (!sheet.classList.contains('show')) {
          backdrop.classList.add('d-none');
        }
      }, 250);
    }
    const viewerModalEl = document.getElementById('storyViewerModal');
    if (viewerModalEl && viewerModalEl.classList.contains('show')) {
      resumeStory();
    }
  }

  // ---------- Tải và hiển thị danh sách người đã xem & cảm xúc đã thả (Hiện từ dưới lên) ----------
  async function loadStoryViewers(storyId) {
    const listEl = document.getElementById('viewersList');
    const emptyNotice = document.getElementById('noViewersNotice');
    if (!listEl) return;

    listEl.innerHTML = '<div class="text-center py-3 text-soft small">Đang tải danh sách...</div>';
    emptyNotice.classList.add('d-none');

    // Mở Bottom Sheet trượt từ dưới lên
    openStoryViewersSheet();

    try {
      const resp = await window.Aloute.api('/stories/api/' + storyId + '/viewers');
      if (!resp.ok) {
        listEl.innerHTML = '<div class="text-danger small text-center py-2">Không thể tải danh sách.</div>';
        return;
      }
      const viewers = await resp.json();
      listEl.innerHTML = '';
      if (!viewers || viewers.length === 0) {
        emptyNotice.classList.remove('d-none');
        return;
      }

      viewers.forEach(v => {
        const item = document.createElement('div');
        item.className = 'd-flex align-items-center justify-content-between p-2 rounded-3 border border-2 border-dark';
        item.style.background = 'var(--white)';
        item.style.boxShadow = '2px 2px 0 var(--ink)';

        // Kế bên tên là các loại cảm xúc mà họ đã thả (chỉ hiện 1 icon tượng trưng cho mỗi loại)
        let reactionBadge = '';
        if (v.reaction && v.reaction.trim()) {
          const emojis = v.reaction.trim().split(/\s+/).filter(Boolean);
          const uniqueEmojis = [...new Set(emojis)];
          const emojiSpans = uniqueEmojis.map(em => `<span style="font-size: 1.25rem; line-height: 1;">${em}</span>`).join(' ');
          reactionBadge = `
            <span class="badge border border-2 border-dark rounded-pill px-2 py-1 ms-2 d-inline-flex align-items-center gap-1"
                  style="background: #FFF3C4; box-shadow: 1.5px 1.5px 0 var(--ink); color: var(--ink);" title="Đã thả: ${uniqueEmojis.join(' ')}">
              ${emojiSpans}
            </span>
          `;
        } else {
          reactionBadge = `<span class="badge border border-dark rounded-pill px-2 py-0_5 text-soft" style="font-size: .72rem; background: var(--paper-2);">Đã xem</span>`;
        }

        item.innerHTML = `
          <div class="d-flex align-items-center gap-2 flex-grow-1 minw-0" style="min-width: 0;">
            <img src="${v.avatarUrl || '/logo-proposals/aloute-sticker-icon.svg'}" alt=""
                 class="rounded-circle border border-2 border-dark flex-shrink-0"
                 style="width: 38px; height: 38px; object-fit: cover; box-shadow: 1px 1px 0 var(--ink); background: var(--white);">
            <div class="flex-grow-1 minw-0" style="min-width: 0;">
              <div class="fw-bold text-truncate d-flex align-items-center" style="font-size: .92rem; line-height: 1.2; font-family: var(--font-display);">
                <span>${v.displayName}</span>
                ${reactionBadge}
              </div>
              <div class="text-soft small" style="font-size: .72rem;">@${v.username}</div>
            </div>
          </div>
          <span class="text-soft small text-nowrap ms-2" style="font-size: .72rem;">${v.timeAgo}</span>
        `;
        listEl.appendChild(item);
      });
    } catch (err) {
      listEl.innerHTML = '<div class="text-danger small text-center py-2">Lỗi kết nối khi tải danh sách.</div>';
    }
  }

  // ==================== 4. MODAL TẠO KHOẢNH KHẮC MỚI ====================
  function initCreateModal() {
    const fileInput = document.getElementById('storyFileInput');
    const dropzone = document.getElementById('storyDropzone');
    const promptWrap = document.getElementById('dropzonePrompt');
    const previewWrap = document.getElementById('mediaPreviewWrap');
    const imgPreview = document.getElementById('imagePreview');
    const vidPreview = document.getElementById('videoPreview');
    const removeBtn = document.getElementById('removeMediaBtn');

    const captionInput = document.getElementById('storyCaption');
    const textPreviewContent = document.getElementById('textPreviewContent');
    const textPreviewCard = document.getElementById('textPreviewCard');
    const colorRadios = document.querySelectorAll('input[name="backgroundColor"]');

    if (fileInput && dropzone) {
      fileInput.addEventListener('change', function () {
        if (!fileInput.files || fileInput.files.length === 0) return;
        const file = fileInput.files[0];
        displayMediaPreview(file);
      });

      // Kéo thả file
      ['dragenter', 'dragover'].forEach(name => {
        dropzone.addEventListener(name, (e) => {
          e.preventDefault();
          dropzone.classList.add('bg-light');
        });
      });
      ['dragleave', 'drop'].forEach(name => {
        dropzone.addEventListener(name, (e) => {
          e.preventDefault();
          dropzone.classList.remove('bg-light');
        });
      });
      dropzone.addEventListener('drop', (e) => {
        if (e.dataTransfer && e.dataTransfer.files.length > 0) {
          fileInput.files = e.dataTransfer.files;
          displayMediaPreview(fileInput.files[0]);
        }
      });
    }

    if (removeBtn) {
      removeBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        fileInput.value = '';
        imgPreview.src = '';
        vidPreview.src = '';
        imgPreview.classList.add('d-none');
        vidPreview.classList.add('d-none');
        previewWrap.classList.add('d-none');
        promptWrap.classList.remove('d-none');
      });
    }

    function displayMediaPreview(file) {
      if (!file) return;
      const isVideo = file.type.startsWith('video/');
      const reader = new FileReader();
      reader.onload = function (e) {
        promptWrap.classList.add('d-none');
        previewWrap.classList.remove('d-none');
        if (isVideo) {
          imgPreview.classList.add('d-none');
          vidPreview.src = e.target.result;
          vidPreview.classList.remove('d-none');
        } else {
          vidPreview.classList.add('d-none');
          imgPreview.src = e.target.result;
          imgPreview.classList.remove('d-none');
        }
      };
      reader.readAsDataURL(file);
    }

    // Live update cho text preview
    if (captionInput && textPreviewContent) {
      captionInput.addEventListener('input', function () {
        const val = captionInput.value.trim();
        textPreviewContent.textContent = val || 'Nhập nội dung bên dưới để xem trước...';
      });
    }

    // Chọn emoji cảm xúc cho text story
    const emojiInput = document.getElementById('storyEmojiInput');
    const selectedEmojiText = document.getElementById('selectedEmojiText');
    const textPreviewEmoji = document.getElementById('textPreviewEmoji');
    const emojiButtons = document.querySelectorAll('.story-emoji-picker .story-picker-emoji');

    if (emojiButtons && emojiButtons.length) {
      emojiButtons.forEach(btn => {
        btn.addEventListener('click', function (e) {
          e.preventDefault();
          emojiButtons.forEach(b => b.classList.remove('is-active'));
          this.classList.add('is-active');

          const chosen = this.getAttribute('data-emoji') || '';
          if (emojiInput) emojiInput.value = chosen;

          if (selectedEmojiText) {
            selectedEmojiText.textContent = chosen ? 'Đang chọn: ' + chosen : 'Không dùng icon';
          }
          if (textPreviewEmoji) {
            if (chosen) {
              textPreviewEmoji.textContent = chosen;
              textPreviewEmoji.classList.remove('d-none');
            } else {
              textPreviewEmoji.classList.add('d-none');
            }
          }
        });
      });
    }

    if (colorRadios && textPreviewCard) {
      colorRadios.forEach(radio => {
        radio.addEventListener('change', function () {
          const color = radio.value;
          textPreviewCard.style.background = pastelColors[color] || '#E8DEFF';
        });
      });
    }

    // Tương tác Form Vibe (Hình 1 & Hình 2)
    const vibeDefaultTexts = {
      '🥰': 'Đang yêu đời',
      '😊': 'Vui vẻ tích cực',
      '🥳': 'Ăn mừng thôi',
      '😎': 'Cực kỳ ngầu',
      '🤩': 'Hào hứng phấn khởi',
      '🥺': 'Cần được an ủi',
      '😴': 'Buồn ngủ quá đi',
      '🫠': 'Tan chảy mệt mỏi',
      '😭': 'Khóc một dòng sông',
      '😤': 'Đầy quyết tâm',
      '🤯': 'Bùng nổ não bộ',
      '🤔': 'Đang suy tư'
    };

    const vibeEmojiButtons = document.querySelectorAll('.vibe-emoji-picker .story-picker-emoji');
    const vibeEmojiInput = document.getElementById('vibeEmojiInput');
    const selectedVibeEmojiText = document.getElementById('selectedVibeEmojiText');
    const vibePreviewEmoji = document.getElementById('vibePreviewEmoji');
    const vibeTextInput = document.getElementById('vibeTextInput');
    const vibePreviewText = document.getElementById('vibePreviewText');

    if (vibeEmojiButtons && vibeEmojiButtons.length) {
      vibeEmojiButtons.forEach(btn => {
        btn.addEventListener('click', function (e) {
          e.preventDefault();
          vibeEmojiButtons.forEach(b => b.classList.remove('is-active'));
          this.classList.add('is-active');
          const emo = this.getAttribute('data-emoji') || '🥰';
          if (vibeEmojiInput) vibeEmojiInput.value = emo;
          if (selectedVibeEmojiText) selectedVibeEmojiText.textContent = 'Đang chọn: ' + emo;
          if (vibePreviewEmoji) vibePreviewEmoji.textContent = emo;

          // Đổi icon vibe thì lời nhắn cũng tự động thay đổi theo (Yêu cầu 2)
          const autoMsg = vibeDefaultTexts[emo] || 'Đang yêu đời';
          if (vibeTextInput) {
            vibeTextInput.value = autoMsg;
          }
          if (vibePreviewText) {
            vibePreviewText.textContent = autoMsg;
          }
        });
      });
    }

    if (vibeTextInput && vibePreviewText) {
      vibeTextInput.addEventListener('input', function () {
        vibePreviewText.textContent = vibeTextInput.value.trim() || 'Đang yêu đời';
      });
    }

    const vibeColorRadios = document.querySelectorAll('#updateVibeForm input[name="colorTheme"]');
    const vibePreviewBubble = document.getElementById('vibePreviewBubble');
    const allNoteClasses = [
      'note--lemon', 'note--pink', 'note--mint', 'note--lavender', 'note--sky',
      'note--peach', 'note--rose', 'note--coral', 'note--sunshine'
    ];
    if (vibeColorRadios && vibePreviewBubble) {
      vibeColorRadios.forEach(r => {
        r.addEventListener('change', function () {
          allNoteClasses.forEach(c => vibePreviewBubble.classList.remove(c));
          vibePreviewBubble.classList.add('note--' + r.value);
        });
      });
    }
  }

  // Chuyển tab giữa MEDIA, TEXT và VIBE trong modal tạo (Hình 1)
  window.switchStoryTab = function (tab) {
    const tabMedia = document.getElementById('tabStoryMedia');
    const tabText = document.getElementById('tabStoryText');
    const tabVibe = document.getElementById('tabStoryVibe');
    const createForm = document.getElementById('createStoryForm');
    const vibeForm = document.getElementById('updateVibeForm');
    const mediaSection = document.getElementById('storyMediaSection');
    const textSection = document.getElementById('storyTextSection');
    const storyTypeInput = document.getElementById('storyTypeInput');
    const captionLabel = document.getElementById('captionLabel');

    [tabMedia, tabText, tabVibe].forEach(t => {
      if (t) {
        t.classList.remove('is-active');
        t.classList.add('btn-al--ghost');
      }
    });

    if (tab === 'VIBE') {
      if (tabVibe) {
        tabVibe.classList.add('is-active');
        tabVibe.classList.remove('btn-al--ghost');
      }
      if (createForm) createForm.classList.add('d-none');
      if (vibeForm) vibeForm.classList.remove('d-none');
      return;
    }

    if (createForm) createForm.classList.remove('d-none');
    if (vibeForm) vibeForm.classList.add('d-none');

    if (tab === 'MEDIA') {
      if (tabMedia) {
        tabMedia.classList.add('is-active');
        tabMedia.classList.remove('btn-al--ghost');
      }
      if (mediaSection) mediaSection.classList.remove('d-none');
      if (textSection) textSection.classList.add('d-none');
      if (storyTypeInput) storyTypeInput.value = 'MEDIA';
      if (captionLabel) captionLabel.textContent = 'Lời nhắn / Chú thích (tùy chọn):';
    } else { // TEXT
      if (tabText) {
        tabText.classList.add('is-active');
        tabText.classList.remove('btn-al--ghost');
      }
      if (mediaSection) mediaSection.classList.add('d-none');
      if (textSection) textSection.classList.remove('d-none');
      if (storyTypeInput) storyTypeInput.value = 'TEXT';
      if (captionLabel) captionLabel.textContent = 'Nội dung ghi chú (bắt buộc):';
    }
  };

})();
