/* Đăng nhập Google/Facebook qua Firebase: lấy ID token rồi đổi lấy phiên (cookie) của ALOUTE. */
import { initializeApp } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js';
import {
  getAuth, signInWithPopup, signOut, GoogleAuthProvider, FacebookAuthProvider,
} from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-auth.js';

const configEl = document.getElementById('firebase-config');
const errorEl = document.getElementById('social-error');

function showError(message) {
  errorEl.textContent = message;
  errorEl.hidden = false;
}

if (configEl) {
  const app = initializeApp({
    apiKey: configEl.dataset.apiKey,
    authDomain: configEl.dataset.authDomain,
    projectId: configEl.dataset.projectId,
  });
  const auth = getAuth(app);
  const providers = { google: () => new GoogleAuthProvider(), facebook: () => new FacebookAuthProvider() };

  document.querySelectorAll('[data-provider]').forEach((button) => {
    button.addEventListener('click', async () => {
      errorEl.hidden = true;
      button.disabled = true;
      try {
        const result = await signInWithPopup(auth, providers[button.dataset.provider]());
        const idToken = await result.user.getIdToken();
        const response = await window.Aloute.api('/auth/firebase', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ idToken }),
        });
        // Firebase chỉ dùng để xác minh danh tính; phiên thật do ALOUTE quản lý
        await signOut(auth);
        if (response.ok) {
          const next = document.querySelector('input[name="next"]');
          window.location.href = next && next.value ? next.value : '/';
          return;
        }
        const body = await response.json().catch(() => ({}));
        showError(body.message || 'Không đăng nhập được, thử lại nhé.');
      } catch (error) {
        if (error.code !== 'auth/popup-closed-by-user' && error.code !== 'auth/cancelled-popup-request') {
          showError('Không mở được cửa sổ đăng nhập. Kiểm tra trình duyệt có chặn popup không nhé.');
        }
      } finally {
        button.disabled = false;
      }
    });
  });
}
