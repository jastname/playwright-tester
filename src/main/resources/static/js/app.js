// Page-level tab switching
document.querySelectorAll('.page-tab').forEach(tab => {
    tab.addEventListener('click', () => {
        document.querySelectorAll('.page-tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.page-tab-content').forEach(c => c.classList.remove('active'));
        tab.classList.add('active');
        document.getElementById('page-' + tab.dataset.page).classList.add('active');
        if (tab.dataset.page === 'screenshots') loadScreenshotStats();
        if (tab.dataset.page === 'settings') {
            loadScreenshotDirectory();
            loadScenarioFilePath();
        }
    });
});

// Helpers
function escapeHtml(v) {
    return String(v ?? '')
        .replaceAll('&', '&amp;').replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;').replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}

function setStatus(msg, type) {
    statusMessage.textContent = msg;
    statusMessage.className = 'status' + (type ? ' ' + type : '');
}

var badgeCls = { click: 'badge-click', fill: 'badge-fill', select: 'badge-select' };

// DOM refs
var urlInput = document.getElementById('urlInput');
var browserSelect = document.getElementById('browserSelect');
var timeoutInput = document.getElementById('timeoutInput');
var headlessInput = document.getElementById('headlessInput');
var fullPageInput = document.getElementById('fullPageInput');
var testButton = document.getElementById('testButton');
var scanButton = document.getElementById('scanButton');
var createScenarioInspectorButton = document.getElementById('createScenarioInspectorButton');
var fillSampleButton = document.getElementById('fillSampleButton');
var statusMessage = document.getElementById('statusMessage');
var resultBox = document.getElementById('resultBox');
var scenarioSection = document.getElementById('scenarioSection');
var scenarioList = document.getElementById('scenarioList');
var screenshotDirectoryInput = document.getElementById('screenshotDirectoryInput');
var screenshotDirectoryCurrent = document.getElementById('screenshotDirectoryCurrent');
var scenarioFileInput = document.getElementById('scenarioFileInput');
var scenarioFileCurrent = document.getElementById('scenarioFileCurrent');
var settingsSaveBtn = document.getElementById('settingsSaveBtn');
var settingsReloadBtn = document.getElementById('settingsReloadBtn');
var settingsStatusMessage = document.getElementById('settingsStatusMessage');

var scenarioModal = document.getElementById('scenarioModal');
var modalCloseBtn = document.getElementById('modalCloseBtn');
var modalCancelBtn = document.getElementById('modalCancelBtn');
var modalSaveBtn = document.getElementById('modalSaveBtn');
var scenarioNameInput = document.getElementById('scenarioNameInput');
var modalScanNotice = document.getElementById('modalScanNotice');
var modalFilterBar = document.getElementById('modalFilterBar');
var modalTableWrap = document.getElementById('modalTableWrap');
var modalTableBody = document.getElementById('modalTableBody');
var mStepCount = document.getElementById('mStepCount');

// State
var scannedElements = [];
var scanParams = {};
var scenarios = [];
