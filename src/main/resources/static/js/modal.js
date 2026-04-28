createScenarioButton.addEventListener('click', openModal);
createScenarioInspectorButton.addEventListener('click', function() {
    openModal();
    document.querySelectorAll('.modal-tab').forEach(function(t) { t.classList.remove('active'); });
    document.querySelectorAll('.modal-tab-content').forEach(function(c) { c.classList.remove('active'); });
    document.querySelector('.modal-tab[data-tab="inspector"]').classList.add('active');
    document.getElementById('tab-inspector').classList.add('active');
});

function openModal() {
    scenarioNameInput.value = '';
    modalUrlInput.value        = scanParams.url     || urlInput.value.trim();
    modalBrowserSelect.value   = scanParams.browser || browserSelect.value;
    modalTimeoutInput.value    = scanParams.timeout || timeoutInput.value || 30000;
    modalHeadlessInput.checked = scanParams.headless !== undefined ? scanParams.headless : headlessInput.checked;

    // 기기 프리셋 초기화
    const presetEl = document.getElementById('modalDevicePreset');
    if (presetEl) { presetEl.value = ''; applyDevicePreset(); }

    inspectorPicked = [];
    renderInspectorList();
    document.querySelectorAll('.modal-tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.modal-tab-content').forEach(c => c.classList.remove('active'));
    document.querySelector('.modal-tab[data-tab="scan"]').classList.add('active');
    document.getElementById('tab-scan').classList.add('active');

    renderModalTable();
    scenarioModal.classList.add('open');
}

function closeModal() {
    scenarioModal.classList.remove('open');
    stopInspector();
}

modalCloseBtn.addEventListener('click', closeModal);
modalCancelBtn.addEventListener('click', closeModal);
scenarioModal.addEventListener('click', e => { if (e.target === scenarioModal) closeModal(); });

function renderModalTable() {
    modalTableBody.innerHTML = '';
    if (!scannedElements.length) {
        modalScanNotice.style.display  = '';
        modalFilterBar.style.display   = 'none';
        modalTableWrap.style.display   = 'none';
        return;
    }
    modalScanNotice.style.display = 'none';
    modalFilterBar.style.display  = '';
    modalTableWrap.style.display  = '';

    scannedElements.forEach((el, idx) => {
        const tr = document.createElement('tr');
        tr.dataset.index = idx;
        const fillCell = el.interactionType === 'fill'
            ? '<input type="text" class="fill-text-input" placeholder="playwright test" />'
            : '<span style="color:#bbb;">-</span>';
        tr.innerHTML = `
            <td><input type="number" class="step-order-input" min="1" placeholder="-" /></td>
            <td>${idx + 1}</td>
            <td>${escapeHtml(el.tag||'')}</td>
            <td>${escapeHtml(el.type||'')}</td>
            <td>${escapeHtml((el.id ? '#'+el.id : '')||el.name||'-')}</td>
            <td title="${escapeHtml(el.className||'')}" style="font-size:12px;color:#666;">${escapeHtml((el.className||'-').substring(0,28))}</td>
            <td title="${escapeHtml(el.text||el.placeholder||'')}">${escapeHtml((el.text||el.placeholder||'-').substring(0,38))}</td>
            <td><span class="badge ${badgeCls[el.interactionType]||''}">${escapeHtml(el.interactionType||'')}</span></td>
            <td>${fillCell}</td>
        `;
        tr.querySelector('.step-order-input').addEventListener('input', updateStepCount);
        modalTableBody.appendChild(tr);
    });

    updateStepCount();
    mFilterReset();
}

function updateStepCount() {
    const cnt = [...modalTableBody.querySelectorAll('.step-order-input')]
        .filter(i => i.value.trim() !== '').length;
    mStepCount.textContent = cnt;
}

var mFilterTag         = document.getElementById('mFilterTag');
var mFilterIdName      = document.getElementById('mFilterIdName');
var mFilterClass       = document.getElementById('mFilterClass');
var mFilterText        = document.getElementById('mFilterText');
var mFilterInteraction = document.getElementById('mFilterInteraction');

document.getElementById('mFilterApplyBtn').addEventListener('click', mFilterApply);
document.getElementById('mFilterResetBtn').addEventListener('click', mFilterReset);
[mFilterTag, mFilterIdName, mFilterClass, mFilterText].forEach(i =>
    i.addEventListener('keydown', e => { if (e.key==='Enter') mFilterApply(); }));
mFilterInteraction.addEventListener('change', mFilterApply);

function mFilterApply() {
    const tag  = mFilterTag.value.trim().toLowerCase();
    const idn  = mFilterIdName.value.trim().toLowerCase();
    const cls  = mFilterClass.value.trim().toLowerCase();
    const txt  = mFilterText.value.trim().toLowerCase();
    const iact = mFilterInteraction.value;
    let visible = 0;
    [...modalTableBody.querySelectorAll('tr')].forEach(tr => {
        const el = scannedElements[Number(tr.dataset.index)];
        const show = (!tag  || (el.tag||'').toLowerCase().includes(tag))
                  && (!idn  || (el.id||'').toLowerCase().includes(idn) || (el.name||'').toLowerCase().includes(idn))
                  && (!cls  || (el.className||'').toLowerCase().includes(cls))
                  && (!txt  || (el.text||'').toLowerCase().includes(txt)  || (el.placeholder||'').toLowerCase().includes(txt))
                  && (!iact || el.interactionType === iact);
        tr.style.display = show ? '' : 'none';
        if (show) visible++;
    });
    document.getElementById('mFilterCount').textContent = visible + ' items';
}

function mFilterReset() {
    mFilterTag.value=''; mFilterIdName.value=''; mFilterClass.value='';
    mFilterText.value=''; mFilterInteraction.value='';
    [...modalTableBody.querySelectorAll('tr')].forEach(tr => tr.style.display='');
    document.getElementById('mFilterCount').textContent='';
}

modalSaveBtn.addEventListener('click', () => {
    const name = scenarioNameInput.value.trim() || 'Scenario ' + (scenarios.length + 1);

    const orderedRows = [...modalTableBody.querySelectorAll('tr')]
        .map(tr => ({ tr, order: parseInt(tr.querySelector('.step-order-input').value) }))
        .filter(item => !isNaN(item.order))
        .sort((a, b) => a.order - b.order);

    const hasInspector = inspectorPicked.length > 0;
    const hasScan      = orderedRows.length > 0;

    if (!hasScan && !hasInspector) {
        alert('No scan or inspector elements selected.');
        return;
    }

    if (hasScan) {
        const orders = orderedRows.map(r => r.order);
        if (orders.length !== new Set(orders).size) {
            alert('Step order is duplicated.');
            return;
        }
    }

    const scanSteps = orderedRows.map(({ tr, order }) => {
        const el = scannedElements[Number(tr.dataset.index)];
        const fi = tr.querySelector('.fill-text-input');
        return {
            order,
            selector:        el.selector,
            interactionType: el.interactionType,
            tag:             el.tag,
            idName:          (el.id ? '#'+el.id : '') || el.name || '-',
            labelText:       (el.text || el.placeholder || '').substring(0, 30),
            fillText:        fi ? (fi.value.trim() || null) : null,
            waitMs:          500
        };
    });

    const baseOrder = hasScan ? Math.max(...scanSteps.map(s => s.order)) : 0;
    const inspSteps = inspectorPicked.map((el, i) => ({
        order:           baseOrder + i + 1,
        selector:        el.selector,
        interactionType: el.interactionType,
        tag:             el.tag,
        idName:          (el.id ? '#'+el.id : '') || el.name || '-',
        labelText:       (el.text || el.placeholder || '').substring(0, 30),
        fillText:        el.interactionType === 'select' ? (el.selectedValue || el.fillText || null) : (el.fillText || null),
        waitMs:          500
    }));

    const steps = [...scanSteps, ...inspSteps];

    scenarios.push({
        id:       Date.now(),
        name,
        url:      modalUrlInput.value.trim() || urlInput.value.trim(),
        browser:  modalBrowserSelect.value,
        headless: modalHeadlessInput.checked,
        timeout:  Number(modalTimeoutInput.value) || 30000,
        viewport: buildViewportFromModal(),
        steps
    });
    closeModal();
    renderScenarioList();
    persistScenarios();
    setStatus('Scenario "' + name + '" saved.', 'success');
});

// ── 기기 프리셋 핸들러 ────────────────────────────────────────────────────
(function() {
    const presetEl  = document.getElementById('modalDevicePreset');
    const customDiv = document.getElementById('modalCustomViewport');
    if (!presetEl) return;

    presetEl.addEventListener('change', applyDevicePreset);

    function applyDevicePreset() {
        const opt = presetEl.options[presetEl.selectedIndex];
        const isCustom = presetEl.value === '__custom__';
        customDiv.style.display = isCustom ? 'grid' : 'none';
        if (!isCustom && presetEl.value) {
            const w = opt.dataset.w;
            const h = opt.dataset.h;
            document.getElementById('modalVpWidth').value  = w || '';
            document.getElementById('modalVpHeight').value = h || '';
        }
    }

    window.applyDevicePreset = applyDevicePreset;
})();

function buildViewportFromModal() {
    const presetEl = document.getElementById('modalDevicePreset');
    if (!presetEl || !presetEl.value) return null;

    const isCustom = presetEl.value === '__custom__';
    const opt = isCustom ? null : presetEl.options[presetEl.selectedIndex];

    const w   = isCustom ? parseInt(document.getElementById('modalVpWidth').value)  : parseInt(opt.dataset.w || '0');
    const h   = isCustom ? parseInt(document.getElementById('modalVpHeight').value) : parseInt(opt.dataset.h || '0');
    if (!w || !h) return null;

    return {
        width:             w,
        height:            h,
        deviceName:        isCustom ? null : (presetEl.value || null),
        userAgent:         isCustom ? null : (opt.dataset.ua  || null),
        deviceScaleFactor: isCustom ? null : (opt.dataset.dpr ? parseFloat(opt.dataset.dpr) : null),
        isMobile:          isCustom ? false : (opt.dataset.mobile === '1'),
        hasTouch:          isCustom ? false : (opt.dataset.touch  === '1')
    };
}