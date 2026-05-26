var inspectorStatusBadge  = document.getElementById('inspectorStatusBadge');
var inspectorStartBtn     = document.getElementById('inspectorStartBtn');
var inspectorStopBtn      = document.getElementById('inspectorStopBtn');
var inspectorClearBtn     = document.getElementById('inspectorClearBtn');
var inspectorPickedList   = document.getElementById('inspectorPickedList');
var inspectorEmpty        = document.getElementById('inspectorEmpty');
var inspectorPickedCount  = document.getElementById('inspectorPickedCount');

var modalUrlInput      = document.getElementById('modalUrlInput');
var modalBrowserSelect = document.getElementById('modalBrowserSelect');
var modalTimeoutInput  = document.getElementById('modalTimeoutInput');
var modalHeadlessInput = document.getElementById('modalHeadlessInput');

inspectorStartBtn.addEventListener('click', async () => {
    const url     = modalUrlInput.value.trim();
    const browser = modalBrowserSelect.value;
    const timeout = Number(modalTimeoutInput.value) || 30000;
    if (!url) { alert('URL을 먼저 입력하세요.'); return; }

    inspectorStartBtn.disabled = true;
    inspectorStatusBadge.textContent = '브라우저 실행 중...';
    inspectorStatusBadge.className   = 'inspector-status on';

    try {
        const res  = await fetch('/api/browser/inspector/start', {
            method:'POST', headers:{'Content-Type':'application/json'},
            body: JSON.stringify({
                url, browser, timeout,
                viewport: (typeof buildViewportFromModal === 'function') ? buildViewportFromModal() : null
            })
        });
        const data = await res.json();

        if (!data.running) {
            const msg = data.errorMessage || data.error || '브라우저 실행 실패';
            throw new Error(msg);
        }
        inspectorSessionId = data.sessionId;
        inspectorStatusBadge.textContent = '활성 - 요소를 클릭하세요';
        inspectorStopBtn.disabled = false;
        inspectorPollTimer = setInterval(pollInspector, 500);
    } catch (e) {
        inspectorStatusBadge.textContent = '비활성';
        inspectorStatusBadge.className   = 'inspector-status off';
        inspectorStartBtn.disabled = false;
        alert('인스펙터 시작 실패: ' + e.message);
    }
});

inspectorStopBtn.addEventListener('click', async () => {
    await stopInspector();
});

async function stopInspector() {
    clearInterval(inspectorPollTimer);
    inspectorPollTimer = null;
    if (inspectorSessionId) {
        try { await fetch('/api/browser/inspector/stop/' + inspectorSessionId, { method:'POST' }); } catch(_) {}
        inspectorSessionId = null;
    }
    inspectorStatusBadge.textContent = '비활성';
    inspectorStatusBadge.className   = 'inspector-status off';
    inspectorStartBtn.disabled  = false;
    inspectorStopBtn.disabled   = true;
}

async function pollInspector() {
    if (!inspectorSessionId) return;
    try {
        const res  = await fetch('/api/browser/inspector/elements/' + inspectorSessionId);
        const data = await res.json();
        const newItems = data.elements || [];
        if (newItems.length > inspectorPicked.length) {
            const added = newItems.slice(inspectorPicked.length);
            added.forEach(el => inspectorPicked.push({ ...el, fillText: '' }));
            renderInspectorList();
        }
        if (!data.running) {
            const reason = data.errorMessage ? ' (' + data.errorMessage + ')' : ' (브라우저 종료됨)';
            inspectorStatusBadge.textContent = '비활성' + reason;
            inspectorStatusBadge.className   = 'inspector-status off';
            inspectorStartBtn.disabled = false;
            inspectorStopBtn.disabled  = true;
            inspectorSessionId = null;
            clearInterval(inspectorPollTimer);
            inspectorPollTimer = null;
        }
    } catch (e) {
        console.warn('[Inspector] poll error:', e);
    }
}

function renderInspectorList() {
    inspectorPickedCount.innerHTML = '선택한 요소: <strong>' + inspectorPicked.length + '</strong>개';
    inspectorEmpty.style.display = inspectorPicked.length ? 'none' : '';
    while (inspectorPickedList.children.length > 1) inspectorPickedList.removeChild(inspectorPickedList.lastChild);
    inspectorPicked.forEach((el, idx) => {
        const item = document.createElement('div');

        // ── 다이얼로그 캡처 (alert / confirm / prompt) ──────────────────
        const isDialog = ['alert', 'confirm', 'prompt'].includes(el.type);
        if (isDialog) {
            item.className = 'inspector-item is-dialog';
            const icons   = { alert: '⚠️', confirm: '❓', prompt: '✏️' };
            const labels  = { alert: '알림', confirm: '확인요청', prompt: '입력요청' };
            const icon    = icons[el.type]  || '💬';
            const dlgLabel = labels[el.type] || el.type;
            const msgPreview = escapeHtml((el.message || '').substring(0, 60));
            const resultInfo = el.type === 'confirm'
                ? `<span style="font-size:11px;color:#888;flex-shrink:0;">(${el.result ? '확인 클릭' : '취소 클릭'})</span>`
                : el.type === 'prompt' && el.input != null
                ? `<span style="font-size:11px;color:#888;flex-shrink:0;">입력: ${escapeHtml(String(el.input).substring(0, 20))}</span>`
                : '';
            item.innerHTML = `
                <span class="inspector-item-order">${idx + 1}</span>
                <span class="badge badge-dialog">${icon} ${dlgLabel}</span>
                <span class="inspector-item-label" title="${escapeHtml(el.message || '')}">${msgPreview}</span>
                ${resultInfo}
                <span class="inspector-item-del" title="삭제">x</span>
            `;
            item.querySelector('.inspector-item-del').addEventListener('click', () => {
                inspectorPicked.splice(idx, 1);
                renderInspectorList();
            });
            inspectorPickedList.appendChild(item);
            inspectorPickedList.scrollTop = inspectorPickedList.scrollHeight;
            return;
        }

        // ── 일반 요소 ────────────────────────────────────────────────────
        item.className = 'inspector-item';
        const label = (el.text || el.placeholder || el.selector || '').substring(0, 40);
        const isFill   = el.interactionType === 'fill';
        const isSelect = el.interactionType === 'select';
        const selectDisplay = isSelect
            ? (el.selectedText ? `${escapeHtml(el.selectedText)} (${escapeHtml(el.selectedValue||'')})` : '<span style="color:#e74c3c;font-size:11px;">값 미선택</span>')
            : '';
        item.innerHTML = `
            <span class="inspector-item-order">${idx + 1}</span>
            <span class="inspector-item-tag">${escapeHtml(el.tag||'')}</span>
            <span class="badge ${badgeCls[el.interactionType]||''}" style="font-size:11px;">${escapeHtml(el.interactionType||'')}</span>
            <span class="inspector-item-label" title="${escapeHtml(el.selector||'')}">${escapeHtml(label)}</span>
            <span class="inspector-item-sel">${escapeHtml((el.selector||'').substring(0,30))}</span>
            ${isFill ? `<input type="text" class="inspector-fill-input" placeholder="입력값" value="${escapeHtml(el.fillText||'')}" />` : ''}
            ${isSelect ? `<span class="inspector-select-value" style="font-size:12px;color:#1a5fd1;padding:0 6px;">${selectDisplay}</span>` : ''}
            <span class="inspector-item-del" title="삭제">x</span>
        `;
        if (isFill) {
            item.querySelector('.inspector-fill-input').addEventListener('input', e => {
                inspectorPicked[idx].fillText = e.target.value;
            });
        }
        item.querySelector('.inspector-item-del').addEventListener('click', () => {
            inspectorPicked.splice(idx, 1);
            renderInspectorList();
        });
        inspectorPickedList.appendChild(item);
        // 스크롤을 맨 아래로 이동
        inspectorPickedList.scrollTop = inspectorPickedList.scrollHeight;
    });
}

inspectorClearBtn.addEventListener('click', () => {
    inspectorPicked = [];
    renderInspectorList();
});

/*fillSampleButton.addEventListener('click', () => {
    urlInput.value = 'https://landsoft.co.kr/';
    setStatus('샘플 URL을 입력했습니다.', 'success');
});*/