/**
 * auth.js – 세션 만료 감지 + 헤더 사용자명/로그아웃
 * 초기 인증은 서버 인터셉터가 담당(미로그인 시 /login.html 리다이렉트).
 * 이 스크립트는 이미 로그인된 상태에서:
 *  1) 헤더에 사용자명 표시 + 로그아웃 버튼 연결
 *  2) 앱 사용 중 세션 만료로 401이 오면 /login.html 으로 이동
 */
(function () {
    'use strict';

    /* ── 로그아웃 ── */
    async function doLogout() {
        try { await fetch('/api/auth/logout', { method: 'POST' }); } catch (e) { /* ignore */ }
        location.href = '/login.html';
    }

    /* ── 헤더 사용자명 표시 + 로그아웃 버튼 연결 ── */
    async function applyHeader() {
        try {
            const res = await fetch('/api/auth/status');
            const data = await res.json();
            if (data.loggedIn) {
                const label = document.getElementById('headerUsername');
                if (label) label.textContent = data.user;
            }
        } catch (e) { /* ignore */ }

        const logoutBtn = document.getElementById('logoutBtn');
        if (logoutBtn) {
            logoutBtn.addEventListener('click', doLogout);
        }
    }

    /* ── 401 전역 감지: 세션 만료 시 로그인 페이지로 이동 ── */
    const _origFetch = window.fetch.bind(window);
    window.fetch = async function (...args) {
        const res = await _origFetch(...args);
        if (res.status === 401) {
            const url = typeof args[0] === 'string' ? args[0] : (args[0] && args[0].url) || '';
            if (!url.includes('/api/auth/')) {
                // 세션 만료 – 현재 URL을 redirect 파라미터로 넘겨 로그인 후 복귀
                const redirect = encodeURIComponent(location.pathname + location.search);
                location.href = '/login.html?redirect=' + redirect;
            }
        }
        return res;
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', applyHeader);
    } else {
        applyHeader();
    }
})();