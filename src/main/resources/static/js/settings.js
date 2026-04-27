async function loadScreenshotDirectory() {
        if (!screenshotDirectoryInput) return;
        settingsStatusMessage.textContent = '';
        try {
            const res = await fetch('/api/browser/settings/screenshot-directory');
            const data = await res.json();
            screenshotDirectoryInput.value = data.directory || '';
            const current = data.absolutePath || data.directory || '-';
            screenshotDirectoryCurrent.textContent = '(' + current + ')';
        } catch (e) {
            settingsStatusMessage.textContent = '?ㅽ겕由곗꺑 寃쎈줈瑜?遺덈윭?ㅼ? 紐삵뻽?듬땲??';
            settingsStatusMessage.className = 'status error';
        }
    }

    async function saveScreenshotDirectory() {
        const directory = screenshotDirectoryInput.value.trim();
        if (!directory) {
            settingsStatusMessage.textContent = '???寃쎈줈瑜??낅젰?섏꽭??';
            settingsStatusMessage.className = 'status error';
            return false;
        }

        settingsStatusMessage.textContent = '???以?..';
        settingsStatusMessage.className = 'status';
        try {
            const res = await fetch('/api/browser/settings/screenshot-directory', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ directory })
            });
            const data = await res.json();
            if (!res.ok || data.ok === false) throw new Error(data.error || '????ㅽ뙣');
            screenshotDirectoryInput.value = data.directory || directory;
            const current = data.absolutePath || data.directory || directory;
            screenshotDirectoryCurrent.textContent = '(' + current + ')';
            settingsStatusMessage.textContent = '??λ릺?덉뒿?덈떎. ?ㅼ쓬 ?ㅽ겕由곗꺑遺????寃쎈줈???앹꽦?⑸땲??';
            settingsStatusMessage.className = 'status success';
            return true;
        } catch (e) {
            settingsStatusMessage.textContent = '????ㅽ뙣: ' + e.message;
            settingsStatusMessage.className = 'status error';
            return false;
        }
    }

    async function loadScenarioFilePath() {
        if (!scenarioFileInput) return;
        settingsStatusMessage.textContent = '';
        try {
            const res = await fetch('/api/scenarios/settings/path');
            const data = await res.json();
            scenarioFileInput.value = data.path || '';
            const current = data.absolutePath || data.path || '-';
            scenarioFileCurrent.textContent = '(' + current + ')';
        } catch (e) {
            settingsStatusMessage.textContent = '?쒕굹由ъ삤 ?뚯씪 寃쎈줈瑜?遺덈윭?ㅼ? 紐삵뻽?듬땲??';
            settingsStatusMessage.className = 'status error';
        }
    }

    async function saveScenarioFilePath() {
        const path = scenarioFileInput.value.trim();
        if (!path) {
            settingsStatusMessage.textContent = '?쒕굹由ъ삤 ?뚯씪 寃쎈줈瑜??낅젰?섏꽭??';
            settingsStatusMessage.className = 'status error';
            return false;
        }

        settingsStatusMessage.textContent = '???以?..';
        settingsStatusMessage.className = 'status';
        try {
            const res = await fetch('/api/scenarios/settings/path', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ path })
            });
            const data = await res.json();
            if (!res.ok || data.ok === false) throw new Error(data.error || '????ㅽ뙣');
            scenarioFileInput.value = data.path || path;
            const current = data.absolutePath || data.path || path;
            scenarioFileCurrent.textContent = '(' + current + ')';
            scenarios = [];
            await loadScenarios();
            settingsStatusMessage.textContent = '??λ릺?덉뒿?덈떎. ?ㅼ쓬 ?쒕굹由ъ삤 ??λ??????뚯씪???ъ슜?⑸땲??';
            settingsStatusMessage.className = 'status success';
            return true;
        } catch (e) {
            settingsStatusMessage.textContent = '????ㅽ뙣: ' + e.message;
            settingsStatusMessage.className = 'status error';
            return false;
        }
    }

    async function loadSettings() {
        await loadScreenshotDirectory();
        await loadScenarioFilePath();
    }

    async function saveSettings() {
        settingsSaveBtn.disabled = true;
        settingsStatusMessage.textContent = '???以?..';
        settingsStatusMessage.className = 'status';
        try {
            const screenshotSaved = await saveScreenshotDirectory();
            if (!screenshotSaved) return;
            const scenarioSaved = await saveScenarioFilePath();
            if (!scenarioSaved) return;
            settingsStatusMessage.textContent = '?ㅼ젙????λ릺?덉뒿?덈떎.';
            settingsStatusMessage.className = 'status success';
        } finally {
            settingsSaveBtn.disabled = false;
        }
    }

    settingsSaveBtn?.addEventListener('click', saveSettings);
    settingsReloadBtn?.addEventListener('click', loadSettings);
    screenshotDirectoryInput?.addEventListener('keydown', e => {
        if (e.key === 'Enter') saveSettings();
    });
    scenarioFileInput?.addEventListener('keydown', e => {
        if (e.key === 'Enter') saveSettings();
    });
