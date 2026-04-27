// ?? ?쒕굹由ъ삤 ?쒕쾭 ??????????????????????????????????????????????
    async function persistScenarios() {
        try {
            const res = await fetch('/api/scenarios', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(scenarios)
            });
            const data = await res.json();
            console.log('[Scenarios] saved to', data.path, `(${scenarios.length}媛?`);
        } catch(e) {
            console.error('[Scenarios] save error:', e);
        }
    }

    // ?? ?섏씠吏 濡쒕뱶 ???쒕쾭?먯꽌 遺덈윭?ㅺ린 ?????????????????????????????
    async function loadScenarios() {
        try {
            const res = await fetch('/api/scenarios');
            const data = await res.json();
            if (Array.isArray(data)) {
                scenarios = data;
                renderScenarioList();
                setStatus(`????λ맂 ?쒕굹由ъ삤 ${data.length}媛쒕? 遺덈윭?붿뒿?덈떎.`, 'success');
            }
        } catch(e) {
            console.warn('[Scenarios] load error:', e);
        }
    }
