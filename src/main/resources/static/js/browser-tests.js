testButton.addEventListener('click', async () => {
    const url = urlInput.value.trim();
    const browser = browserSelect.value;
    const timeout = Number(timeoutInput.value);
    const headless = headlessInput.checked;
    const fullPageScreenshot = fullPageInput.checked;

    if (!url) {
        setStatus('URL을 입력하세요.', 'error');
        return;
    }
    if (!timeout || timeout < 1000) {
        setStatus('timeout은 1000ms 이상이어야 합니다.', 'error');
        return;
    }

    setStatus('테스트 실행 중...', '');
    resultBox.textContent = '요청 전송 중...';
    testButton.disabled = true;

    try {
        const res = await fetch('/api/browser/check', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url, browser, headless, timeout, fullPageScreenshot })
        });
        const data = await res.json();
        if (!res.ok) {
            setStatus('테스트 실패', 'error');
            resultBox.textContent = JSON.stringify(data, null, 2);
            return;
        }
        setStatus('테스트 성공', 'success');
        resultBox.innerHTML = `
<div class="row"><span class="label">URL</span>${escapeHtml(data.url)}</div>
<div class="row"><span class="label">브라우저</span>${escapeHtml(data.browser)}</div>
<div class="row"><span class="label">페이지 제목</span>${escapeHtml(data.title)}</div>
<div class="row"><span class="label">상태 코드</span>${escapeHtml(data.status)}</div>
<div class="row"><span class="label">소요 시간(ms)</span>${escapeHtml(data.durationMs)}</div>
${data.screenshotUrl ? `<div class="row"><a class="screenshot-link" href="${escapeHtml(data.screenshotUrl)}" target="_blank">스크린샷 보기</a></div>` : ''}`;
    } catch(e) {
        setStatus('서버 호출 중 오류 발생', 'error');
        resultBox.textContent = e.message;
    } finally {
        testButton.disabled = false;
    }
});

scanButton.addEventListener('click', async () => {
    console.log('요소 스캔 시작');
    const url = urlInput.value.trim();
    const browser = browserSelect.value;
    const timeout = Number(timeoutInput.value);
    const headless = headlessInput.checked;

    if (!url) {
        setStatus('URL을 입력하세요.', 'error');
        return;
    }

    setStatus('요소 스캔 중...', '');
    resultBox.textContent = '';
    scanButton.disabled = true;
    scannedElements = [];

    try {
        const res = await fetch('/api/browser/scan-elements', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url, browser, headless, timeout })
        });
        const data = await res.json();
        if (!res.ok) {
            setStatus('스캔 실패', 'error');
            resultBox.textContent = JSON.stringify(data, null, 2);
            return;
        }

        scannedElements = data;
        scanParams = { url, browser, headless, timeout };
        setStatus(`스캔 완료 - ${data.length}개 요소 발견.`, 'success');
        openModal('scan');
    } catch(e) {
        setStatus('스캔 중 오류 발생', 'error');
        resultBox.textContent = e.message;
    } finally {
        scanButton.disabled = false;
    }
});
