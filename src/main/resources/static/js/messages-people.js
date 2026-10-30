/* Direct-chat discovery. Group membership continues to use the friends picker. */
(() => {
    const search = document.getElementById('messagesPeopleSearch');
    if (!search) return;
    const input = document.getElementById('messagesPeopleQuery');
    const results = document.getElementById('messagesPeopleResults');
    const status = document.getElementById('messagesPeopleStatus');
    const template = document.getElementById('messagesPeopleForm');
    let timer;
    let controller;
    let revision = 0;

    document.getElementById('focusMessagesPeople').addEventListener('click', () => input.focus());

    input.addEventListener('input', () => {
        const current = ++revision;
        clearTimeout(timer);
        controller?.abort();
        results.replaceChildren();
        results.setAttribute('aria-busy', 'false');
        const query = input.value.trim().replace(/\s+/g, ' ');
        if (query.length < 2 || query.length > 100) {
            status.textContent = query.length > 100 ? 'Nhập tối đa 100 ký tự.' : 'Nhập ít nhất 2 ký tự để tìm người.';
            return;
        }
        status.textContent = 'Đang tìm người…';
        results.setAttribute('aria-busy', 'true');
        timer = setTimeout(async () => {
            controller = new AbortController();
            try {
                const url = new URL(search.dataset.searchUrl, window.location.origin);
                url.searchParams.set('q', query);
                const response = await fetch(url, {signal: controller.signal, headers: {'Accept': 'application/json'}});
                if (!response.ok) throw new Error('Search failed');
                const people = await response.json();
                if (current !== revision) return;
                const fragment = document.createDocumentFragment();
                people.forEach(person => {
                    const form = template.content.firstElementChild.cloneNode(true);
                    form.querySelector('[name="username"]').value = person.username;
                    form.querySelector('.search-user-name').textContent = person.displayName;
                    form.querySelector('[data-username]').textContent = '@' + person.username;
                    form.querySelector('button').setAttribute('aria-label', 'Nhắn tin với ' + person.displayName);
                    const avatar = form.querySelector('.avatar');
                    if (person.avatarUrl) {
                        const img = document.createElement('img');
                        img.src = person.avatarUrl;
                        img.alt = '';
                        img.loading = 'lazy';
                        avatar.append(img);
                    } else {
                        avatar.textContent = (person.displayName || person.username).slice(0, 1).toUpperCase();
                    }
                    fragment.append(form);
                });
                results.replaceChildren(fragment);
                status.textContent = people.length ? `Tìm thấy ${people.length} người (tối đa 20 kết quả).` : 'Không tìm thấy người phù hợp.';
            } catch (error) {
                if (current !== revision || error.name === 'AbortError') return;
                status.textContent = 'Chưa thể tìm người. Hãy thử nhập lại sau nhé.';
            } finally {
                if (current === revision) results.setAttribute('aria-busy', 'false');
            }
        }, 250);
    });
})();
