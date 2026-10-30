(function () {
  'use strict';
  function update() {
    document.querySelectorAll('[data-season-deadline]').forEach(function (el) {
      var remaining = Math.max(0, new Date(el.dataset.seasonDeadline).getTime() - Date.now());
      if (!Number.isFinite(remaining)) return;
      var minutes = Math.ceil(remaining / 60000);
      var days = Math.floor(minutes / 1440);
      var hours = Math.floor((minutes % 1440) / 60);
      var parts = [];
      if (days) parts.push(days + ' ngày');
      if (hours) parts.push(hours + ' giờ');
      if (minutes % 60) parts.push((minutes % 60) + ' phút');
      el.textContent = remaining === 0 ? 'Đang cập nhật trạng thái…' : el.dataset.seasonPrefix + ' ' + parts.join(' ');
    });
  }
  update();
  if (document.querySelector('[data-season-deadline]')) setInterval(update, 60000);
  if (document.querySelector('[data-season-card-standings]')) setInterval(async function () {
    if (document.hidden) return;
    try {
      var response = await fetch(window.location.href, { credentials: 'same-origin' });
      if (!response.ok) return;
      var page = new DOMParser().parseFromString(await response.text(), 'text/html');
      var updated = page.querySelector('[data-season-card-standings]');
      if (updated) document.querySelectorAll('[data-season-card-standings]').forEach(function (card) { card.replaceChildren.apply(card, Array.from(updated.cloneNode(true).childNodes)); });
    } catch (_) { }
  }, 30000);
  var standings = document.querySelector('[data-season-standings][data-season-live="true"]');
  if (standings) setInterval(async function () {
    if (document.hidden) return;
    try {
      var response = await fetch(window.location.href, { credentials: 'same-origin', headers: { 'Accept': 'text/html' } });
      if (!response.ok) return;
      var page = new DOMParser().parseFromString(await response.text(), 'text/html');
      var updated = page.querySelector('[data-season-standings]');
      if (updated) standings.replaceChildren.apply(standings, Array.from(updated.childNodes));
    } catch (_) { /* Lần cập nhật sau sẽ thử lại. */ }
  }, 30000);
})();
