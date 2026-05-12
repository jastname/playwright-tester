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
            <div class="scenario-card-header">
                <div class="scenario-card-title">${escapeHtml(sc.name)}</div>
                <div class="scenario-card-actions">
                    <a href="/scenario.html?id=${sc.id}" class="btn-detail-link">상세</a>
                    <button type="button" class="btn-accent run-btn" data-id="${sc.id}">실행</button>
                    <button type="button" class="btn-danger delete-btn" data-id="${sc.id}">삭제</button>
                </div>
            </div>
            <div class="scenario-card-meta">
                <span>시나리오 URL : ${escapeHtml(sc.url)}</span>
                <span>브라우저 : ${escapeHtml(sc.browser)}</span>
                <span>타임아웃 :${sc.timeout}ms</span>
                <span>${sc.steps.length}단계</span>
            </div>

            <div class="scenario-result-area" id="result-${sc.id}"></div>
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

    // 진행 바 초기 렌더링
    resultArea.innerHTML = `
        <div class="scenario-progress-bar-wrap" id="prog-wrap-${id}">
            <div class="scenario-progress-bar" id="prog-bar-${id}" style="width:0%"></div>
        </div>
        <div class="scenario-progress-label" id="prog-label-${id}">준비 중…</div>

    `;
        /*${sc.steps.map(s => `
        <div class="step-result-row" id="sr-${id}-${s.order}">
            <span class="step-order-num">${s.order}.</span>
            <span class="step-label">${escapeHtml(s.labelText || s.idName)}</span>
            <span class="badge ${badgeCls[s.interactionType] || ''}">${escapeHtml(s.interactionType)}</span>
            <span class="step-status-pending">⏳ 대기 중</span>
        </div>`).join('')}*/
    const totalSteps = sc.steps.length;

    function updateProgress(doneCount, label, status) {
        const pct = totalSteps > 0 ? Math.round((doneCount / totalSteps) * 100) : 0;
        const bar = document.getElementById(`prog-bar-${id}`);
        const lbl = document.getElementById(`prog-label-${id}`);
        if (bar) bar.style.width = pct + '%';
        if (lbl) lbl.textContent = label;
        //has-error 클래스가 없고 status가 error인 경우에만 추가
        if (bar && !bar.classList.contains('has-error') && status === 'error') {
			bar.classList.add('has-error');
		}
    }

    try {
        const apiSteps = sc.steps.map(({ selector, interactionType, fillText, waitMs, order }) =>
            ({ selector, interactionType, fillText, waitMs, order }));

        // 비동기 실행 시작 → executionId 획득
        const startRes = await fetch('/api/browser/test-scenario-async', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                url: sc.url,
                browser: sc.browser,
                headless: sc.headless,
                timeout: sc.timeout,
                steps: apiSteps,
                scenarioId: sc.id,
                scenarioName: sc.name,
                viewport: sc.viewport || null,
                fullPageScreenshot: sc.fullPageScreenshot || false
            })
        });
        const { executionId } = await startRes.json();

        // SSE 연결로 실시간 진행률 수신
        await new Promise((resolve, reject) => {
            const es = new EventSource(`/api/browser/test-scenario-progress/${executionId}`);
            let doneCount = 0;

            es.addEventListener('scenario-start', e => {
                const d = JSON.parse(e.data);
                updateProgress(0, `0 / ${d.totalSteps} 단계 실행 중…`);
            });

            es.addEventListener('step-start', e => {
                const d = JSON.parse(e.data);
                const row = document.getElementById(`sr-${id}-${d.step}`);
                if (row) {
                    const statusSpan = row.querySelector('.step-status-pending, .step-status-running, .badge-success, .badge-error, .error-msg');
                    if (statusSpan) statusSpan.remove();
                    row.insertAdjacentHTML('beforeend', '<span class="step-status-running"><span class="spinner"></span> 실행 중…</span>');
                }
                updateProgress(doneCount, `${d.step} / ${d.totalSteps} 단계 실행 중…`);
            });

            es.addEventListener('step-result', e => {
                const d = JSON.parse(e.data);
                console.log('Step result:', d);
                doneCount = d.step;
                const row = document.getElementById(`sr-${id}-${d.step}`);
                if (row) {
                    // 기존 상태 span 제거
                    row.querySelectorAll('.step-status-pending, .step-status-running').forEach(el => el.remove());

                    if (d.status === 'success') {
                        const thumb = d.screenshotUrl
                            ? `<a class="thumb-sm" href="${escapeHtml(d.screenshotUrl)}" target="_blank"><img src="${escapeHtml(d.screenshotUrl)}" alt="screenshot"/></a>`
                            : '';
                        row.insertAdjacentHTML('beforeend', `<span class="badge badge-success">✅ 성공</span>${thumb}`);
                    } else {
                        const thumb = d.screenshotUrl
                            ? `<a class="thumb-sm" href="${escapeHtml(d.screenshotUrl)}" target="_blank"><img src="${escapeHtml(d.screenshotUrl)}" alt="screenshot"/></a>`
                            : '';
                        const errSpan = d.errorMessage
                            ? `<span class="error-msg" title="클릭 시 펼치기" onclick="this.classList.toggle('expanded')">${escapeHtml(d.errorMessage)}</span>`
                            : '';
                        row.insertAdjacentHTML('beforeend', `<span class="badge badge-error">❌ 실패</span>${thumb}${errSpan}`);
                        
                    }
                }
                updateProgress(doneCount, `${doneCount} / ${d.totalSteps} 단계 완료`, d.status);
            });

            es.addEventListener('scenario-complete', e => {
                const d = JSON.parse(e.data);
                updateProgress(d.totalSteps, `완료 — 성공: ${d.successCount}, 실패: ${d.failCount}`);
                // 진행 바 색상
                const bar = document.getElementById(`prog-bar-${id}`);
                /*if (bar) bar.classList.toggle('has-error', d.failCount > 0);*/
                if(bar) bar.classList.toggle('has-success', d.failCount == 0);
                es.close();
                resolve();
            });

            es.addEventListener('scenario-error', e => {
                const d = JSON.parse(e.data);
                es.close();
                reject(new Error(d.errorMessage));
            });

            es.onerror = () => {
                es.close();
                reject(new Error('SSE 연결 오류'));
            };
        });

    } catch(e) {
        // 오류는 진행 바 라벨에만 표시
        const lbl = document.getElementById(`prog-label-${id}`);
        if (lbl) lbl.textContent = '오류: ' + e.message;
    } finally {
        runBtn.disabled = false;
        runBtn.textContent = '실행';
    }
}
