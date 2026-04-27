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
        settingsStatusMessage.textContent = '스크린샷 저장 경로를 불러오지 못했습니다.';
        settingsStatusMessage.className = 'status error';
    }
}

async function saveScreenshotDirectory() {
    const directory = screenshotDirectoryInput.value.trim();
    if (!directory) {
        settingsStatusMessage.textContent = '스크린샷 저장 경로를 입력하세요.';
        settingsStatusMessage.className = 'status error';
        return false;
    }

    settingsStatusMessage.textContent = '저장 중...';
    settingsStatusMessage.className = 'status';
    try {
        const res = await fetch('/api/browser/settings/screenshot-directory', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ directory })
        });
        const data = await res.json();
        if (!res.ok || data.ok === false) throw new Error(data.error || '저장 실패');
        screenshotDirectoryInput.value = data.directory || directory;
        const current = data.absolutePath || data.directory || directory;
        screenshotDirectoryCurrent.textContent = '(' + current + ')';
        settingsStatusMessage.textContent = '스크린샷 저장 경로가 저장되었습니다.';
        settingsStatusMessage.className = 'status success';
        return true;
    } catch (e) {
        settingsStatusMessage.textContent = '저장 실패: ' + e.message;
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
        settingsStatusMessage.textContent = '시나리오 파일 경로를 불러오지 못했습니다.';
        settingsStatusMessage.className = 'status error';
    }
}

async function saveScenarioFilePath() {
    const path = scenarioFileInput.value.trim();
    if (!path) {
        settingsStatusMessage.textContent = '시나리오 파일 경로를 입력하세요.';
        settingsStatusMessage.className = 'status error';
        return false;
    }

    settingsStatusMessage.textContent = '저장 중...';
    settingsStatusMessage.className = 'status';
    try {
        const res = await fetch('/api/scenarios/settings/path', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ path })
        });
        const data = await res.json();
        if (!res.ok || data.ok === false) throw new Error(data.error || '저장 실패');
        scenarioFileInput.value = data.path || path;
        const current = data.absolutePath || data.path || path;
        scenarioFileCurrent.textContent = '(' + current + ')';
        scenarios = [];
        await loadScenarios();
        settingsStatusMessage.textContent = '시나리오 파일 경로가 저장되었습니다.';
        settingsStatusMessage.className = 'status success';
        return true;
    } catch (e) {
        settingsStatusMessage.textContent = '저장 실패: ' + e.message;
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
    settingsStatusMessage.textContent = '저장 중...';
    settingsStatusMessage.className = 'status';
    try {
        const screenshotSaved = await saveScreenshotDirectory();
        if (!screenshotSaved) return;
        const scenarioSaved = await saveScenarioFilePath();
        if (!scenarioSaved) return;
        settingsStatusMessage.textContent = '설정이 저장되었습니다.';
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
