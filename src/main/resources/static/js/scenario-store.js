async function persistScenarios() {
    try {
        const res = await fetch('/api/scenarios', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(scenarios)
        });
        const data = await res.json();
        console.log('[Scenarios] saved to', data.path, `(${scenarios.length}개)`);
    } catch(e) {
        console.error('[Scenarios] save error:', e);
    }
}

async function loadScenarios() {
    try {
        const res = await fetch('/api/scenarios');
        const data = await res.json();
        if (Array.isArray(data)) {
            scenarios = data;
            renderScenarioList();
            setStatus(`저장된 시나리오 ${data.length}개를 불러왔습니다.`, 'success');
        }
    } catch(e) {
        console.warn('[Scenarios] load error:', e);
    }
}
