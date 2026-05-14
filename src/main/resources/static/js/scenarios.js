/**
 * 목록 페이지가 로드/복귀될 때 이미 진행 중인 실행이 있으면 SSE 에 재연결합니다.
 */
async function checkAndResumeActiveExecutions() {
    try {
        const res = await fetch('/api/browser/active-executions');
        const data = await res.json();
        const active = data.active || {};
        for (const [scId, execId] of Object.entries(active)) {
            const id = Number(scId);
            const sc = scenarios.find(s => s.id === id);
            if (!sc) continue;
            // 이미 해당 카드가 "실행 중" 상태이면 중복 재연결 방지
            const runBtn = scenarioList.querySelector(`.run-btn[data-id="${id}"]`);
            if (runBtn && runBtn.disabled) continue;
            resumeScenarioProgress(id, execId, sc.steps.length);
        }
    } catch(e) { /* ignore */ }
}

/**
 * 이미 시작된 실행(executionId)에 SSE 를 재연결해 목록 카드에 진행률을 표시합니다.
 */
async function resumeScenarioProgress(id, executionId, totalSteps) {
    const runBtn    = scenarioList.querySelector(`.run-btn[data-id="${id}"]`);
    const resultArea = document.getElementById(`result-${id}`);
    if (!resultArea || !runBtn) return;

    runBtn.disabled    = true;
    runBtn.textContent = '실행 중...';

    resultArea.innerHTML = `
        <div class="scenario-progress-bar-wrap" id="prog-wrap-${id}">
            <div class="scenario-progress-bar" id="prog-bar-${id}" style="width:0%"></div>
        </div>
        <div class="scenario-progress-label" id="prog-label-${id}">진행 중 (재연결)…</div>
    `;

    function updateProgress(doneCount, label, status) {
        const pct = totalSteps > 0 ? Math.round((doneCount / totalSteps) * 100) : 0;
        const bar = document.getElementById(`prog-bar-${id}`);
        const lbl = document.getElementById(`prog-label-${id}`);
        if (bar) bar.style.width = pct + '%';
        if (lbl) lbl.textContent = label;
        if (bar && !bar.classList.contains('has-error') && status === 'error') {
            bar.classList.add('has-error');
        }
    }

    try {
        await new Promise((resolve, reject) => {
            const es = new EventSource(`/api/browser/test-scenario-progress/${executionId}`);
            let doneCount = 0;

            es.addEventListener('scenario-start', e => {
                const d = JSON.parse(e.data);
                updateProgress(0, `0 / ${d.totalSteps} 단계 실행 중…`);
            });
            es.addEventListener('step-start', e => {
                const d = JSON.parse(e.data);
                updateProgress(doneCount, `${d.step} / ${d.totalSteps} 단계 실행 중…`);
            });
            es.addEventListener('step-result', e => {
                const d = JSON.parse(e.data);
                doneCount = d.step;
                updateProgress(doneCount, `${doneCount} / ${d.totalSteps} 단계 완료`, d.status);
            });
            es.addEventListener('scenario-complete', e => {
                const d = JSON.parse(e.data);
                updateProgress(d.totalSteps, `완료 — 성공: ${d.successCount}, 실패: ${d.failCount}`);
                const bar = document.getElementById(`prog-bar-${id}`);
                if (bar) bar.classList.toggle('has-success', d.failCount === 0);
                es.close();
                resolve();
            });
            es.addEventListener('scenario-error', e => {
                const d = JSON.parse(e.data);
                es.close();
                reject(new Error(d.errorMessage));
            });
            es.onerror = () => { es.close(); reject(new Error('SSE 연결 오류')); };
        });
    } catch(e) {
        const lbl = document.getElementById(`prog-label-${id}`);
        if (lbl) lbl.textContent = '오류: ' + e.message;
    } finally {
        if (runBtn) { runBtn.disabled = false; runBtn.textContent = '실행'; }
    }
}

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

    // 이미 진행 중인 실행이 있으면 카드에 재연결
    checkAndResumeActiveExecutions();
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

    const totalSteps = sc.steps.length;
    const collectedResults = [];  // 단계별 결과 수집

    function updateProgress(doneCount, label, status) {
        const pct = totalSteps > 0 ? Math.round((doneCount / totalSteps) * 100) : 0;
        const bar = document.getElementById(`prog-bar-${id}`);
        const lbl = document.getElementById(`prog-label-${id}`);
        if (bar) bar.style.width = pct + '%';
        if (lbl) lbl.textContent = label;
        if (bar && !bar.classList.contains('has-error') && status === 'error') {
            bar.classList.add('has-error');
        }
    }

    function renderStepResults() {
        const wrap = document.getElementById(`prog-wrap-${id}`);
        if (!wrap) return;
        // 기존 step 결과 div 제거 후 재렌더링
        const existing = document.getElementById(`step-results-${id}`);
        if (existing) existing.remove();

        const errorSteps = collectedResults.filter(r => r.status === 'error');
        if (errorSteps.length === 0) return;

        const div = document.createElement('div');
        div.id = `step-results-${id}`;
        div.style.cssText = 'margin-top:8px; display:flex; flex-direction:column; gap:4px;';
        div.innerHTML = errorSteps.map(r => {
            const step = sc.steps.find(s => s.order === r.order) || {};
            const label = escapeHtml(step.labelText || step.idName || r.selector || `${r.order}단계`);
            const thumb = r.screenshotUrl
                ? `<a href="${escapeHtml(r.screenshotUrl)}" target="_blank" style="flex-shrink:0;"><img src="${escapeHtml(r.screenshotUrl)}" style="width:60px;height:38px;object-fit:cover;border-radius:4px;border:1px solid #f5c6cb;" /></a>`
                : '';
            return `
                <div style="display:flex;align-items:flex-start;gap:8px;background:#fff3f3;border:1px solid #f5c6cb;border-radius:8px;padding:7px 10px;font-size:12px;">
                    <span style="background:#c62828;color:#fff;border-radius:12px;padding:2px 8px;font-size:11px;font-weight:bold;flex-shrink:0;">Step ${r.order}</span>
                    <span style="color:#555;flex-shrink:0;">${label}</span>
                    ${thumb}
                    <span style="color:#c62828;flex:1;word-break:break-all;cursor:pointer;max-height:38px;overflow:hidden;transition:max-height 0.2s;"
                          onclick="this.style.maxHeight=this.style.maxHeight==='none'?'38px':'none';"
                          title="클릭하여 전체 보기">${escapeHtml(r.errorMessage || '알 수 없는 오류')}</span>
                </div>`;
        }).join('');
        wrap.after(div);
    }

    try {
        const apiSteps = sc.steps.map(({ selector, interactionType, fillText, waitMs, order }) =>
            ({ selector, interactionType, fillText, waitMs, order }));

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

        await new Promise((resolve, reject) => {
            const es = new EventSource(`/api/browser/test-scenario-progress/${executionId}`);
            let doneCount = 0;

            es.addEventListener('scenario-start', e => {
                const d = JSON.parse(e.data);
                updateProgress(0, `0 / ${d.totalSteps} 단계 실행 중…`);
            });

            es.addEventListener('step-start', e => {
                const d = JSON.parse(e.data);
                updateProgress(doneCount, `${d.step} / ${d.totalSteps} 단계 실행 중…`);
            });

            es.addEventListener('step-result', e => {
                const d = JSON.parse(e.data);
                doneCount = d.step;
                // 단계 결과 수집
                const step = sc.steps[d.stepIndex] || sc.steps[d.step - 1] || {};
                collectedResults.push({
                    order:        step.order ?? d.step,
                    selector:     step.selector || '',
                    status:       d.status,
                    errorMessage: d.errorMessage || null,
                    screenshotUrl:d.screenshotUrl || null
                });
                updateProgress(doneCount, `${doneCount} / ${d.totalSteps} 단계 완료`, d.status);
            });

            es.addEventListener('scenario-complete', e => {
                const d = JSON.parse(e.data);
                const hasErr = d.failCount > 0;
                updateProgress(d.totalSteps,
                    `완료 — ✅ 성공 ${d.successCount}단계${hasErr ? ` / ❌ 실패 ${d.failCount}단계` : ''}`);
                const bar = document.getElementById(`prog-bar-${id}`);
                if (bar) bar.classList.toggle('has-success', d.failCount === 0);
                renderStepResults();
                es.close();
                resolve();
            });

            es.addEventListener('scenario-error', e => {
                const d = JSON.parse(e.data);
                es.close();
                reject(new Error(d.errorMessage));
            });

            es.onerror = () => { es.close(); reject(new Error('SSE 연결 오류')); };
        });

    } catch(e) {
        const lbl = document.getElementById(`prog-label-${id}`);
        if (lbl) lbl.textContent = '오류: ' + e.message;
    } finally {
        runBtn.disabled = false;
        runBtn.textContent = '실행';
    }
}
