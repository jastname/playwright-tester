// ?? URL ?묒냽 ?뚯뒪????????????????????????????????????????????????
    testButton.addEventListener('click', async () => {
        const url    = urlInput.value.trim();
        const browser = browserSelect.value;
        const timeout = Number(timeoutInput.value);
        const headless = headlessInput.checked;
        const fullPageScreenshot = fullPageInput.checked;

        if (!url) { setStatus('URL???낅젰?댁＜?몄슂.','error'); return; }
        if (!timeout || timeout < 1000) { setStatus('timeout? 1000ms ?댁긽?댁뼱???⑸땲??','error'); return; }

        setStatus('?뚯뒪???ㅽ뻾 以?..','');
        resultBox.textContent = '?붿껌 ?꾩넚 以?..';
        testButton.disabled = true;

        try {
            const res  = await fetch('/api/browser/check', {
                method:'POST', headers:{'Content-Type':'application/json'},
                body: JSON.stringify({ url, browser, headless, timeout, fullPageScreenshot })
            });
            const data = await res.json();
            if (!res.ok) { setStatus('?뚯뒪???ㅽ뙣','error'); resultBox.textContent = JSON.stringify(data,null,2); return; }
            setStatus('?뚯뒪???깃났','success');
            resultBox.innerHTML = `
<div class="row"><span class="label">URL</span>${escapeHtml(data.url)}</div>
<div class="row"><span class="label">釉뚮씪?곗?</span>${escapeHtml(data.browser)}</div>
<div class="row"><span class="label">?섏씠吏 ?쒕ぉ</span>${escapeHtml(data.title)}</div>
<div class="row"><span class="label">?곹깭 肄붾뱶</span>${escapeHtml(data.status)}</div>
<div class="row"><span class="label">?뚯슂 ?쒓컙(ms)</span>${escapeHtml(data.durationMs)}</div>
${data.screenshotUrl ? `<div class="row"><a class="screenshot-link" href="${escapeHtml(data.screenshotUrl)}" target="_blank">?ㅽ겕由곗꺑 蹂닿린</a></div>` : ''}`;
        } catch(e) {
            setStatus('?쒕쾭 ?몄텧 以??ㅻ쪟 諛쒖깮','error');
            resultBox.textContent = e.message;
        } finally {
            testButton.disabled = false;
        }
    });

    // ?? ?붿냼 ?ㅼ틪 ????????????????????????????????????????????????????
    scanButton.addEventListener('click', async () => {
    	console.log('?붿냼 ?ㅼ틪 ?쒖옉');
        const url    = urlInput.value.trim();
        const browser = browserSelect.value;
        const timeout = Number(timeoutInput.value);
        const headless = headlessInput.checked;

        if (!url) { setStatus('URL???낅젰?댁＜?몄슂.','error'); return; }

        setStatus('?붿냼 ?ㅼ틪 以?..','');
        resultBox.textContent = '';
        scanButton.disabled = true;
        createScenarioButton.disabled = true;
        scannedElements = [];

        try {
            const res  = await fetch('/api/browser/scan-elements', {
                method:'POST', headers:{'Content-Type':'application/json'},
                body: JSON.stringify({ url, browser, headless, timeout })
            });
            const data = await res.json();
            if (!res.ok) { setStatus('?ㅼ틪 ?ㅽ뙣','error'); resultBox.textContent = JSON.stringify(data,null,2); return; }

            scannedElements = data;
            scanParams      = { url, browser, headless, timeout };
            setStatus(`???ㅼ틪 ?꾨즺 ??${data.length}媛??붿냼 諛쒓껄. [?뱷 ?쒕굹由ъ삤 ?묒꽦] 踰꾪듉?쇰줈 ?쒕굹由ъ삤瑜?援ъ꽦?섏꽭??`, 'success');
            createScenarioButton.disabled = false;
        } catch(e) {
            setStatus('?ㅼ틪 以??ㅻ쪟 諛쒖깮','error');
            resultBox.textContent = e.message;
        } finally {
            scanButton.disabled = false;
        }
    });
