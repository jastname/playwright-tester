function renderScenarioList() {
    scenarioSection.style.display = scenarios.length ? '' : 'none';
    scenarioList.innerHTML = '';

    scenarios.forEach(sc => {
        const card = document.createElement('div');
        card.className = 'scenario-card';
        card.dataset.id = sc.id;
        const stepBadges = sc.steps.map(s =>
            `<span class="badge ${badgeCls[s.interactionType] || ''}" style="font-size:11px;">${s.order}. ${escapeHtml(s.labelText || s.idName)}</span>`
        ).join(' ');
        card.innerHTML = `
            <div class="scenario-card-body">
                <div class="scenario-card-title">${escapeHtml(sc.name)}</div>
                <div class="scenario-card-meta">
                    <span>URL ${escapeHtml(sc.url)}</span>
                    <span>브라우저 ${escapeHtml(sc.browser)}</span>
                    <span>${sc.timeout}ms</span>
                    <span>${sc.steps.length}단계</span>
                </div>
                <div style="margin-top:8px; display:flex; gap:6px; flex-wrap:wrap;">${stepBadges}</div>
                <div class="scenario-result-area" id="result-${sc.id}"></div>
            </div>
            <div class="scenario-card-actions">
                <a href="/scenario.html?id=${sc.id}" class="btn-detail-link">상세</a>
                <button type="button" class="btn-accent run-btn" data-id="${sc.id}">실행</button>
                <button type="button" class="btn-danger delete-btn" data-id="${sc.id}" style="font-size:12px;padding:5px 10px;">삭제</button>
            </div>
        `;
        scenarioList.appendChild(card);
    });

    scenarioList.querySelectorAll('.run-btn').forEach(btn =>
        btn.addEventListener('click', () => runScenario(Number(btn.dataset.id))));
    scenarioList.querySelectorAll('.delete-btn').forEach(btn =>
        btn.addEventListener('click', () => {
            scenarios = scenarios.filter(s => s.id !== Number(btn.dataset.id));
            renderScenarioList();
            persistScenarios();
        }));
}

async function runScenario(id) {
    const sc = scenarios.find(s => s.id === id);
    if (!sc) return;

    const runBtn = scenarioList.querySelector(`.run-btn[data-id="${id}"]`);
    const resultArea = document.getElementById(`result-${id}`);

    runBtn.disabled = true;
    runBtn.textContent = '실행 중...';
    setStatus(`"${sc.name}" 시나리오 실행 중...`, '');

    resultArea.innerHTML = sc.steps.map(s => `
        <div class="step-result-row" id="sr-${id}-${s.order}">
            <span style="min-width:24px;font-size:12px;color:#888;">${s.order}.</span>
            <span style="font-size:12px;color:#555;min-width:150px;">${escapeHtml(s.labelText || s.idName)}</span>
            <span class="badge ${badgeCls[s.interactionType] || ''}" style="font-size:11px;">${escapeHtml(s.interactionType)}</span>
            <span style="color:#aaa;font-size:12px;">대기 중</span>
        </div>`).join('');

    try {
        const apiSteps = sc.steps.map(({ selector, interactionType, fillText, waitMs }) =>
            ({ selector, interactionType, fillText, waitMs }));

        const res = await fetch('/api/browser/test-scenario', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                url: sc.url,
                browser: sc.browser,
                headless: sc.headless,
                timeout: sc.timeout,
                steps: apiSteps,
                scenarioId: sc.id,
                scenarioName: sc.name
            })
        });
        const data = await res.json();
        const stepResults = data.steps || [];

        sc.steps.forEach((s, i) => {
            const sr = stepResults[i];
            const row = document.getElementById(`sr-${id}-${s.order}`);
            if (!row || !sr) return;

            const statusBadge = sr.status === 'success'
                ? '<span class="badge badge-success">성공</span>'
                : '<span class="badge badge-error">실패</span>';
            const thumb = sr.screenshotUrl
                ? `<a class="thumb-sm" href="${escapeHtml(sr.screenshotUrl)}" target="_blank"><img src="${escapeHtml(sr.screenshotUrl)}" alt="screenshot"/></a>`
                : '';
            const errSpan = sr.errorMessage
                ? `<span class="error-msg" title="클릭 시 펼치기" onclick="this.classList.toggle('expanded')">${escapeHtml(sr.errorMessage)}</span>`
                : '';

            row.innerHTML = `
                <span style="min-width:24px;font-size:12px;color:#888;">${s.order}.</span>
                <span style="font-size:12px;color:#555;min-width:150px;">${escapeHtml(s.labelText || s.idName)}</span>
                <span class="badge ${badgeCls[s.interactionType] || ''}" style="font-size:11px;">${escapeHtml(s.interactionType)}</span>
                ${statusBadge}${thumb}${errSpan}
            `;
        });

        const suc = data.successCount ?? 0;
        const fail = data.failCount ?? 0;
        setStatus(`"${sc.name}" 완료 - 성공: ${suc}단계, 실패: ${fail}단계`, fail === 0 ? 'success' : '');
    } catch(e) {
        setStatus('시나리오 실행 중 오류: ' + e.message, 'error');
    } finally {
        runBtn.disabled = false;
        runBtn.textContent = '실행';
    }
}
