var screenshotStats      = document.getElementById('screenshotStats');
var screenshotRefreshBtn = document.getElementById('screenshotRefreshBtn');
var screenshotDeleteAllBtn = document.getElementById('screenshotDeleteAllBtn');

var screenshotFiles = [];
var lightboxIndex   = 0;

async function loadScreenshotStats() {
    document.getElementById('screenshotCountNum').textContent = '...';
    document.getElementById('screenshotSizeMb').textContent = '...';
    try {
        const res  = await fetch('/api/browser/screenshots');
        const data = await res.json();
        const mb   = (data.totalBytes / 1024 / 1024).toFixed(2);
        document.getElementById('screenshotCountNum').textContent = data.count;
        document.getElementById('screenshotSizeMb').textContent = mb;
        screenshotFiles = data.files || [];
        renderGallery();
    } catch(e) {
        document.getElementById('screenshotCountNum').textContent = '?';
        document.getElementById('screenshotSizeMb').textContent = '?';
    }
}

function renderGallery() {
    const gallery = document.getElementById('screenshotGallery');
    const empty   = document.getElementById('screenshotGalleryEmpty');
    const filterEl = document.getElementById('screenshotScenarioFilter');
    const filterCountEl = document.getElementById('screenshotFilterCount');
    const selectedScenario = filterEl ? filterEl.value : '';

    if (filterEl) {
        const names = [...new Set(screenshotFiles.filter(f => f.scenarioName).map(f => f.scenarioName))];
        const currentVal = filterEl.value;
        filterEl.innerHTML = '<option value="">전체</option>' +
            names.map(n => `<option value="${escapeHtml(n)}" ${n === currentVal ? 'selected' : ''}>${escapeHtml(n)}</option>`).join('');
    }

    const filtered = selectedScenario
        ? screenshotFiles.filter(f => f.scenarioName === selectedScenario)
        : screenshotFiles;

    if (filterCountEl) filterCountEl.textContent = filtered.length + '개';

    gallery.innerHTML = '';
    if (filtered.length === 0) {
        gallery.style.display = 'none';
        empty.style.display   = '';
        return;
    }
    gallery.style.display = '';
    empty.style.display   = 'none';
    filtered.forEach((f) => {
        const realIdx = screenshotFiles.indexOf(f);
        const url  = '/api/browser/screenshots/file/' + f.name;
        const date = new Date(f.createdAt).toLocaleString('ko-KR');
        const kb   = (f.size / 1024).toFixed(1);
        const card = document.createElement('div');
        card.className = 'screenshot-thumb-card';
        const stepStatus = f.stepStatus === 'success' ? 'success' : 'error';
        const scenarioTag = f.scenarioName
            ? `<div class="screenshot-scenario-tag">${escapeHtml(f.scenarioName)} #${f.stepOrder ?? '?'} <span class="badge ${stepStatus === 'success' ? 'badge-success' : 'badge-error'}" style="font-size:10px;">${stepStatus}</span></div>`
            : '';
        card.innerHTML = `
            <img class="screenshot-thumb-img" src="${escapeHtml(url)}" alt="${escapeHtml(f.name)}" loading="lazy" />
            <div class="screenshot-thumb-info">
                ${scenarioTag}
                <div class="screenshot-thumb-date">${escapeHtml(date)}</div>
                <div class="screenshot-thumb-size">${kb} KB</div>
                <button class="ss-del-btn" data-name="${escapeHtml(f.name)}" type="button">삭제</button>
            </div>
        `;
        card.querySelector('img').addEventListener('click', () => openLightbox(realIdx));
        card.querySelector('.ss-del-btn').addEventListener('click', async (e) => {
            e.stopPropagation();
            const name = e.currentTarget.dataset.name;
            if (!confirm('"' + name + '" 파일을 삭제할까요?')) return;
            const res  = await fetch('/api/browser/screenshots/' + encodeURIComponent(name), { method: 'DELETE' });
            const data = await res.json();
            if (data.ok) await loadScreenshotStats();
            else alert('삭제 실패: ' + (data.error || ''));
        });
        gallery.appendChild(card);
    });
}

var lightboxOverlay  = document.getElementById('lightboxOverlay');
var lightboxImg      = document.getElementById('lightboxImg');
var lightboxFilename = document.getElementById('lightboxFilename');

function openLightbox(idx) {
    lightboxIndex = idx;
    showLightboxAt(idx);
    lightboxOverlay.classList.add('open');
}
function showLightboxAt(idx) {
    const f = screenshotFiles[idx];
    if (!f) return;
    lightboxImg.src = '/api/browser/screenshots/file/' + f.name;
    lightboxFilename.textContent = f.name + '  (' + (idx+1) + ' / ' + screenshotFiles.length + ')';
}
document.getElementById('lightboxClose').addEventListener('click', () => lightboxOverlay.classList.remove('open'));
lightboxOverlay.addEventListener('click', e => { if (e.target === lightboxOverlay) lightboxOverlay.classList.remove('open'); });
document.getElementById('lightboxPrev').addEventListener('click', () => {
    if (lightboxIndex > 0) { lightboxIndex--; showLightboxAt(lightboxIndex); }
});
document.getElementById('lightboxNext').addEventListener('click', () => {
    if (lightboxIndex < screenshotFiles.length - 1) { lightboxIndex++; showLightboxAt(lightboxIndex); }
});
document.addEventListener('keydown', e => {
    if (!lightboxOverlay.classList.contains('open')) return;
    if (e.key === 'Escape') lightboxOverlay.classList.remove('open');
    if (e.key === 'ArrowLeft' && lightboxIndex > 0) { lightboxIndex--; showLightboxAt(lightboxIndex); }
    if (e.key === 'ArrowRight' && lightboxIndex < screenshotFiles.length - 1) { lightboxIndex++; showLightboxAt(lightboxIndex); }
});

screenshotRefreshBtn.addEventListener('click', loadScreenshotStats);
document.getElementById('screenshotScenarioFilter').addEventListener('change', renderGallery);

screenshotDeleteAllBtn.addEventListener('click', async () => {
    if (!confirm('모든 스크린샷을 삭제할까요?')) return;
    const res  = await fetch('/api/browser/screenshots', { method: 'DELETE' });
    const data = await res.json();
    alert(data.deleted + '개 삭제 완료');
    loadScreenshotStats();
});