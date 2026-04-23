package io.github.jastname.playwrighttester.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import io.github.jastname.playwrighttester.controller.ScenarioRequest;
import org.springframework.stereotype.Service;
import io.github.jastname.playwrighttester.dto.ButtonCandidate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlaywrightService {

    // ── UI 인스펙터: 브라우저에 주입할 스크립트 ───────────────────────────────
    private static final String INSPECTOR_SCRIPT = """
        (function() {
          if (window.__inspectorInjected) return;
          window.__inspectorInjected = true;

          function __initInspector() {
            if (!document.body) {
              document.addEventListener('DOMContentLoaded', __initInspector);
              return;
            }

            // ── 오버레이 (server.js 방식) ──────────────────────────
            var overlay = document.getElementById('__insp_overlay');
            if (!overlay) {
              overlay = document.createElement('div');
              overlay.id = '__insp_overlay';
              Object.assign(overlay.style, {
                position: 'fixed', pointerEvents: 'none', zIndex: '2147483647',
                border: '2px solid red', background: 'rgba(255,0,0,0.08)',
                left: '0px', top: '0px', width: '0px', height: '0px',
                boxSizing: 'border-box'
              });
              document.documentElement.appendChild(overlay);
            }

            // ── 배지 ──────────────────────────────────────────────
            var badge = document.getElementById('__insp_badge');
            if (!badge) {
              badge = document.createElement('div');
              badge.id = '__insp_badge';
              Object.assign(badge.style, {
                position: 'fixed', top: '10px', right: '10px', zIndex: '2147483648',
                background: '#e74c3c', color: 'white', fontSize: '13px', fontWeight: 'bold',
                padding: '6px 14px', borderRadius: '20px', cursor: 'pointer',
                boxShadow: '0 2px 10px rgba(0,0,0,0.4)', userSelect: 'none',
                fontFamily: 'sans-serif', lineHeight: '1.4'
              });
              badge.textContent = '🔴 인스펙터 ON (우클릭=선택 | ESC=ON/OFF)';
              document.documentElement.appendChild(badge);
            }

            // ── 배너 ──────────────────────────────────────────────
            var banner = document.getElementById('__insp_banner');
            if (!banner) {
              banner = document.createElement('div');
              banner.id = '__insp_banner';
              Object.assign(banner.style, {
                position: 'fixed', bottom: '24px', left: '50%',
                transform: 'translateX(-50%)', zIndex: '2147483648',
                background: '#27ae60', color: 'white', fontSize: '13px',
                fontWeight: 'bold', padding: '8px 20px', borderRadius: '20px',
                pointerEvents: 'none', display: 'none', fontFamily: 'sans-serif',
                boxShadow: '0 2px 10px rgba(0,0,0,0.3)'
              });
              document.documentElement.appendChild(banner);
            }

            var active = true;
            var lastSignature = '';

            function showBanner(msg) {
              banner.textContent = msg;
              banner.style.display = 'block';
              clearTimeout(banner.__timer);
              banner.__timer = setTimeout(function() { banner.style.display = 'none'; }, 1500);
            }

            function updateOverlay(el) {
              if (!el || !(el instanceof Element)) return;
              var rect = el.getBoundingClientRect();
              overlay.style.left   = rect.left   + 'px';
              overlay.style.top    = rect.top    + 'px';
              overlay.style.width  = rect.width  + 'px';
              overlay.style.height = rect.height + 'px';
            }

            function getSimpleSelector(el) {
              if (!el || el.nodeType !== 1) return '';
              var parts = [];
              var current = el;
              while (current && current.nodeType === 1) {
                var part = current.tagName.toLowerCase();
                // 랜덤/동적 ID 스킵: 숫자만이거나 hex 패턴이거나 8자 이상 숫자포함
                var cid = current.id ? current.id.trim() : '';
                var isDynamicId = cid && (/[0-9]{4,}/.test(cid) || /^[a-f0-9]{8,}$/i.test(cid) || /[-_][a-f0-9]{6,}/i.test(cid));
                if (cid && !isDynamicId) {
                  part += '#' + cid;
                  parts.unshift(part);
                  break;
                }
                var classList = Array.from(current.classList || []).slice(0, 2);
                if (classList.length) part += '.' + classList.join('.');
                var parent = current.parentElement;
                if (parent) {
                  var sameTag = Array.from(parent.children).filter(function(c) { return c.tagName === current.tagName; });
                  if (sameTag.length > 1) part += ':nth-of-type(' + (sameTag.indexOf(current) + 1) + ')';
                }
                parts.unshift(part);
                current = current.parentElement;
              }
              return parts.join(' > ');
            }

            function getInteraction(el) {
              var tag  = el.tagName.toLowerCase();
              var type = (el.getAttribute('type') || '').toLowerCase();
              if (tag === 'select') return 'select';
              if (tag === 'textarea' || (tag === 'input' &&
                  ['text','password','email','number','search','url','tel',''].includes(type)))
                return 'fill';
              return 'click';
            }

            function buildInfo(el) {
              if (!el) return null;
              var className = '-';
              if (typeof el.className === 'string' && el.className.trim())
                className = el.className.trim().slice(0, 120);
              var text = (el.innerText || el.textContent || '').trim().slice(0, 300) || '-';
              return {
                url:             location.href,
                tag:             el.tagName ? el.tagName.toLowerCase() : '-',
                type:            el.getAttribute('type') || '',
                id:              el.id || '',
                name:            el.name || '',
                className:       className,
                text:            text,
                placeholder:     el.placeholder || '',
                interactionType: getInteraction(el),
                selector:        getSimpleSelector(el) || '-',
                pageUrl:         location.href
              };
            }

            function toggle() {
              active = !active;
              badge.style.background = active ? '#e74c3c' : '#555';
              badge.textContent = active
                ? '🔴 인스펙터 ON (우클릭=선택 | ESC=ON/OFF)'
                : '⚫ 인스펙터 OFF (ESC=ON/OFF)';
              if (!active) {
                overlay.style.width = '0px';
                overlay.style.height = '0px';
              }
            }

            badge.addEventListener('click', function(e) { e.stopPropagation(); toggle(); });
            document.addEventListener('keydown', function(e) { if (e.key === 'Escape') toggle(); }, true);

            // ── 마우스 이동: 하이라이팅 ───────────────────────────
            document.addEventListener('mousemove', function(e) {
              if (!active) return;
              var el = document.elementFromPoint(e.clientX, e.clientY);
              if (!el || el === badge || badge.contains(el)) return;
              updateOverlay(el);
            }, true);

            // ── 우클릭: 캡처 (server.js 동일 방식) ──────────────
            document.addEventListener('contextmenu', function(e) {
              if (!active) return;
              var el = document.elementFromPoint(e.clientX, e.clientY);
              if (!el || el === badge || badge.contains(el)) return;

              updateOverlay(el);
              e.preventDefault();
              e.stopPropagation();

              var info = buildInfo(el);
              if (!info) return;

              var signature = JSON.stringify(info);
              if (signature !== lastSignature) lastSignature = signature;

              // 캡처 시각적 피드백
              var origBorder = overlay.style.border;
              overlay.style.border = '3px solid #27ae60';
              overlay.style.background = 'rgba(46,204,113,0.18)';
              setTimeout(function() {
                overlay.style.border = '2px solid red';
                overlay.style.background = 'rgba(255,0,0,0.08)';
              }, 400);

              showBanner('✅ 캡처: ' + (info.text !== '-' ? info.text : info.selector).slice(0, 40));
              fetch('http://localhost:8081/api/browser/inspector/capture', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ sessionId: window.__inspectorSessionId, element: info })
              }).then(function(r){ console.log('[Inspector] captured, status:', r.status); })
                .catch(function(e){ console.error('[Inspector] fetch error:', e); });
            }, true);

          } // end __initInspector

          __initInspector();
        })();
    """;

    /**
     * UI 인스펙터 세션을 백그라운드 스레드로 시작합니다.
     * 브라우저가 열리면 사용자가 클릭하는 요소가 session.elements 에 추가됩니다.
     */
    public void startInspector(String url, String browserName, int timeout,
                               InspectorSessionStore.Session session) {
        // running은 브라우저가 실제로 열린 뒤에 true 로 바꿈
        session.running = false;

        Thread t = new Thread(() -> {
            Playwright playwright = null;
            Browser browser = null;
            try {
                playwright = Playwright.create();
                browser    = launchBrowser(playwright, browserName, false); // 항상 non-headless

                BrowserContext ctx = browser.newContext();

                // ── sessionId 먼저 주입 (INSPECTOR_SCRIPT에서 사용) ──
                ctx.addInitScript("window.__inspectorSessionId = '" + session.id + "';");

                // ── 매 페이지 로드마다 인스펙터 스크립트 주입 ──
                ctx.addInitScript(INSPECTOR_SCRIPT);

                Page page = ctx.newPage();
                page.setDefaultTimeout(timeout);

                // 네비게이션 실패해도 브라우저는 살아있어야 하므로 별도 try-catch
                try {
                    page.navigate(url, new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.COMMIT)
                            .setTimeout(30000));
                } catch (Exception navErr) {
                    session.errorMessage = "네비게이션 경고 (브라우저는 열림): " + navErr.getMessage();
                    // 네비게이션 실패해도 브라우저 창은 유지 → 계속 진행
                }

                // ── 브라우저가 실제로 열린 시점에 running = true ──
                session.running = true;

                // ── stop 신호 또는 브라우저/컨텍스트 강제 종료까지 대기 ──
                while (session.stopSignal.getCount() > 0) {
                    // 모든 페이지가 닫혔으면 종료
                    if (ctx.pages().isEmpty()) break;
                    Thread.sleep(400);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                session.errorMessage = "인스펙터 오류: " + e.getMessage();
            } finally {
                session.running = false;
                session.stopSignal.countDown();
                // 리소스 정리
                try { if (browser    != null) browser.close();    } catch (Exception ignored) {}
                try { if (playwright != null) playwright.close();  } catch (Exception ignored) {}
            }
        });
        t.setDaemon(true);
        session.browserThread = t;
        t.start();
    }

    /**
     * 인스펙터 세션을 종료합니다.
     */
    public void stopInspector(InspectorSessionStore.Session session) {
        session.stopSignal.countDown();
    }

    public Map<String, Object> checkPage(
            String url,
            String browserName,
            boolean headless,
            int timeout,
            boolean fullPageScreenshot
    ) {
        long start = System.currentTimeMillis();

        try (Playwright playwright = Playwright.create()) {
            Browser browser = launchBrowser(playwright, browserName, headless);

            try (browser) {
                BrowserContext context = browser.newContext();
                try (context) {
                    Page page = context.newPage();
                    page.setDefaultTimeout(timeout);

                    Response response = page.navigate(url);

                    String title = page.title();
                    String finalUrl = page.url();

                    Path screenshotDir = Paths.get("screenshots");
                    Files.createDirectories(screenshotDir);

                    String fileName = UUID.randomUUID() + ".png";
                    Path screenshotPath = screenshotDir.resolve(fileName);

                    page.screenshot(new Page.ScreenshotOptions()
                            .setPath(screenshotPath)
                            .setFullPage(fullPageScreenshot));

                    long durationMs = System.currentTimeMillis() - start;

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("url", url);
                    result.put("browser", browserName);
                    result.put("headless", headless);
                    result.put("timeout", timeout);
                    result.put("fullPageScreenshot", fullPageScreenshot);
                    result.put("title", title);
                    result.put("status", response != null ? response.status() : null);
                    result.put("finalUrl", finalUrl);
                    result.put("durationMs", durationMs);
                    result.put("screenshotPath", screenshotPath.toString().replace("\\", "/"));
                    result.put("screenshotUrl", "/screenshots/" + fileName);
                    return result;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("페이지 테스트 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    private static final String EXTRACT_ELEMENTS_SCRIPT = """
        () => {
            try {
                function buildSelector(el) {
                    // 1) id가 있고 문서 내 유일한 경우만 #id 사용
                    if (el.id && el.id.trim() !== '') {
                        const id = el.id.trim();
                        if (/^[a-zA-Z][\\w-]*$/.test(id) && document.querySelectorAll('#' + id).length === 1) {
                            return '#' + id;
                        }
                    }
                    // 2) name 속성 활용 (input 등)
                    if (el.name && el.name.trim() !== '' && ['input','select','textarea'].includes(el.tagName.toLowerCase())) {
                        const nameVal = el.name.trim();
                        const candidates = document.querySelectorAll(el.tagName.toLowerCase() + '[name="' + nameVal + '"]');
                        if (candidates.length === 1) return el.tagName.toLowerCase() + '[name="' + nameVal + '"]';
                    }
                    // 3) 부모 경로 기반 nth-of-type
                    const parts = [];
                    let cur = el;
                    while (cur && cur !== document.documentElement && cur.tagName) {
                        const tag = cur.tagName.toLowerCase();
                        let parent = cur.parentElement;
                        if (!parent) { parts.unshift(tag); break; }
                        if (cur.id && cur.id.trim() !== '' && /^[a-zA-Z][\\w-]*$/.test(cur.id.trim())
                                && document.querySelectorAll('#' + cur.id.trim()).length === 1) {
                            parts.unshift('#' + cur.id.trim());
                            break;
                        }
                        const siblings = Array.from(parent.children).filter(c => c.tagName === cur.tagName);
                        const nth = siblings.indexOf(cur) + 1;
                        parts.unshift(siblings.length > 1 ? tag + ':nth-of-type(' + nth + ')' : tag);
                        cur = parent;
                        if (parts.length > 6) break;
                    }
                    return parts.join(' > ');
                }

                const seen = new WeakSet();
                const results = [];

                // 1) 명시적 선택자로 수집 (클릭 가능한 요소를 a[href]보다 먼저)
                const SELECTORS = [
                    'button',
                    'input',
                    'select',
                    'textarea',
                    '[onclick]',                          // onclick 속성 보유 요소 최우선
                    'a[onclick]',                         // href 없는 onclick a 태그
                    '[role="button"]',
                    '[role="tab"]',
                    '[role="menuitem"]',
                    '[role="link"]',
                    'a[href]',                            // 일반 링크는 후순위
                    '[tabindex]:not([tabindex="-1"])'     // 접근 가능한 tabindex만
                ];

                for (const sel of SELECTORS) {
                    let nodes;
                    try { nodes = document.querySelectorAll(sel); } catch(e) { continue; }
                    for (const el of nodes) {
                        if (seen.has(el)) continue;
                        seen.add(el);

                        const tag = (el.tagName || '').toLowerCase();
                        const type = (el.getAttribute('type') || '').toLowerCase();
                        if (type === 'hidden') continue;

                        let interactionType;
                        if (tag === 'select') {
                            interactionType = 'select';
                        } else if (tag === 'textarea' || (tag === 'input' && ['text','password','email','number','search','url','tel',''].includes(type))) {
                            interactionType = 'fill';
                        } else {
                            interactionType = 'click';
                        }

                        const rect = el.getBoundingClientRect();
                        const visible = (rect.width > 0 && rect.height > 0) || el.offsetParent !== null;
                        const text = (el.innerText || el.textContent || el.value || el.placeholder || el.getAttribute('aria-label') || el.getAttribute('title') || '').trim().slice(0, 100);
                        const selector = buildSelector(el);
                        const className = (el.className && typeof el.className === 'string') ? el.className.trim().slice(0, 120) : '';
                        const onclickAttr = el.getAttribute('onclick') || '';

                        results.push({ tag, type, id: el.id || '', name: el.name || '', className, onclickAttr, text, placeholder: el.placeholder || '', interactionType, selector, visible });
                    }
                }

                // 2) cursor:pointer 기반 추가 감지 (addEventListener 등 동적 이벤트 요소 포함)
                const allElements = document.querySelectorAll('*');
                for (const el of allElements) {
                    if (seen.has(el)) continue;
                    const tag = (el.tagName || '').toLowerCase();
                    if (['script','style','meta','link','head','html','body','br','hr','img','svg','path'].includes(tag)) continue;
                    const style = window.getComputedStyle(el);
                    if (style.cursor !== 'pointer') continue;
                    const rect = el.getBoundingClientRect();
                    if (rect.width === 0 || rect.height === 0) continue;
                    seen.add(el);
                    const text = (el.innerText || el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '').trim().slice(0, 100);
                    const className = (el.className && typeof el.className === 'string') ? el.className.trim().slice(0, 120) : '';
                    const onclickAttr = el.getAttribute('onclick') || '';
                    const selector = buildSelector(el);
                    results.push({ tag, type: el.getAttribute('type') || '', id: el.id || '', name: el.name || '', className, onclickAttr, text, placeholder: el.placeholder || '', interactionType: 'click', selector, visible: true, detectedBy: 'cursor:pointer' });
                }

                return results;
            } catch(e) {
                return [{ tag: 'error', type: '', id: '', name: '', className: '', onclickAttr: '', text: e.message, placeholder: '', interactionType: 'click', selector: '', visible: false }];
            }
        }
    """;

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> scanElements(
            String url, String browserName, boolean headless, int timeout) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = launchBrowser(playwright, browserName, headless);
            try (browser) {
                try (BrowserContext ctx = browser.newContext()) {
                    Page page = ctx.newPage();
                    page.setDefaultTimeout(timeout);
                    page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
                    // 동적 렌더링 요소 대기
                    try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                            new Page.WaitForLoadStateOptions().setTimeout(10000)); } catch (Exception ignored) {}
                    Object raw = page.evaluate(EXTRACT_ELEMENTS_SCRIPT);
                    if (!(raw instanceof List)) return List.of();
                    List<Map<String, Object>> elements = (List<Map<String, Object>>) raw;
                    // add index for frontend reference
                    for (int i = 0; i < elements.size(); i++) {
                        elements.get(i).put("index", i);
                    }
                    return elements;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("요소 스캔 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> testElement(
            String url, String browserName, boolean headless, int timeout,
            String selector, String interactionType, String fillText) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selector", selector);
        result.put("interactionType", interactionType);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = launchBrowser(playwright, browserName, headless);
            try (browser) {
                try (BrowserContext ctx = browser.newContext()) {
                    Page page = ctx.newPage();
                    page.setDefaultTimeout(Math.min(timeout, 15000));
                    page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));

                    Locator locator = resolveLocator(page, selector);

                    // 뷰포트 안으로 스크롤
                    try { locator.scrollIntoViewIfNeeded(
                            new Locator.ScrollIntoViewIfNeededOptions().setTimeout(3000)); }
                    catch (Exception ignored) {}

                    switch (interactionType) {
                        case "click" -> locator.click(new Locator.ClickOptions().setTimeout(5000));
                        case "fill" -> {
                            String text = (fillText != null && !fillText.isBlank()) ? fillText : "playwright테스트";
                            try {
                                locator.fill(text, new Locator.FillOptions().setTimeout(5000));
                            } catch (Exception fillEx) {
                                // fallback: click to focus then type character by character
                                try { locator.click(new Locator.ClickOptions().setTimeout(3000).setForce(true)); } catch (Exception ignored) {}
                                locator.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(30));
                            }
                        }
                        case "select" -> {
                            @SuppressWarnings("unchecked")
                            String firstOption = (String) page.evaluate(
                                "sel => { const e = document.querySelector(sel); return e && e.options && e.options.length > 0 ? e.options[0].value : null; }",
                                selector
                            );
                            if (firstOption != null) {
                                locator.selectOption(firstOption, new Locator.SelectOptionOptions().setTimeout(5000));
                            }
                        }
                    }

                    Path screenshotDir = Paths.get("screenshots");
                    Files.createDirectories(screenshotDir);
                    String fileName = UUID.randomUUID() + ".png";
                    Path screenshotPath = screenshotDir.resolve(fileName);
                    page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(false));

                    result.put("status", "success");
                    result.put("screenshotUrl", "/screenshots/" + fileName);
                }
            }
        } catch (Exception e) {
            result.put("status", "error");
            String msg = e.getMessage() != null ? e.getMessage() : "알 수 없는 오류";
            // cause chain 포함
            Throwable cause = e.getCause();
            if (cause != null && cause.getMessage() != null && !msg.contains(cause.getMessage())) {
                msg = msg + "\n원인: " + cause.getMessage();
            }
            result.put("errorMessage", msg.length() > 1000 ? msg.substring(0, 1000) + "..." : msg);
        }

        return result;
    }

    public Map<String, Object> testScenario(
            String url, String browserName, boolean headless, int timeout,
            List<ScenarioRequest.ScenarioStep> steps) {

        Map<String, Object> scenarioResult = new LinkedHashMap<>();
        List<Map<String, Object>> stepResults = new ArrayList<>();
        scenarioResult.put("url", url);
        scenarioResult.put("totalSteps", steps.size());

        try (Playwright playwright = Playwright.create()) {
            Browser browser = launchBrowser(playwright, browserName, headless);
            try (browser) {
                try (BrowserContext ctx = browser.newContext()) {
                    Page page = ctx.newPage();
                    page.setDefaultTimeout(timeout);
                    page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));

                    // 페이지 로드 후 초기 안정화 대기 (동적 렌더링 완료 대기)
                    page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                            new Page.WaitForLoadStateOptions().setTimeout(10000));
                    page.waitForTimeout(1500);

                    Path screenshotDir = Paths.get("screenshots");
                    Files.createDirectories(screenshotDir);

                    for (int i = 0; i < steps.size(); i++) {
                        ScenarioRequest.ScenarioStep step = steps.get(i);
                        Map<String, Object> stepResult = new LinkedHashMap<>();
                        stepResult.put("step", i + 1);
                        stepResult.put("selector", step.getSelector());
                        stepResult.put("interactionType", step.getInteractionType());
                        stepResult.put("fillText", step.getFillText());

                        try {
                            // 셀렉터 후보: 원본 → 마지막 파트만 → 클래스만
                            Locator locator = resolveLocator(page, step.getSelector());

                            // 뷰포트 안으로 스크롤
                            try { locator.scrollIntoViewIfNeeded(
                                    new Locator.ScrollIntoViewIfNeededOptions().setTimeout(3000)); }
                            catch (Exception ignored) {}

                            switch (step.getInteractionType()) {
                                case "click" -> {
                                    try {
                                        locator.click(new Locator.ClickOptions().setTimeout(8000));
                                    } catch (Exception clickEx) {
                                        // fallback: force click (bypasses actionability checks, e.g. overlapping elements)
                                        locator.click(new Locator.ClickOptions().setTimeout(8000).setForce(true));
                                    }
                                }
                                case "fill" -> {
                                    String text = (step.getFillText() != null && !step.getFillText().isBlank())
                                            ? step.getFillText() : "playwright테스트";
                                    try {
                                        locator.fill(text, new Locator.FillOptions().setTimeout(8000));
                                    } catch (Exception fillEx) {
                                        // fallback: click to focus then type character by character
                                        try { locator.click(new Locator.ClickOptions().setTimeout(3000).setForce(true)); } catch (Exception ignored) {}
                                        locator.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(30));
                                    }
                                    // Dispatch input/change events so React/Vue controlled components reflect the value
                                    try {
                                        page.evaluate(
                                            "selector => { const el = document.querySelector(selector); if (el) { " +
                                            "el.dispatchEvent(new Event('input', { bubbles: true })); " +
                                            "el.dispatchEvent(new Event('change', { bubbles: true })); } }",
                                            step.getSelector()
                                        );
                                    } catch (Exception ignored) {}
                                    // Short extra wait for autocomplete/dropdown to settle, then dismiss it
                                    page.waitForTimeout(400);
                                    try { page.keyboard().press("Escape"); } catch (Exception ignored) {}
                                    page.waitForTimeout(200);
                                }
                                case "select" -> {
                                    @SuppressWarnings("unchecked")
                                    String firstOption = (String) page.evaluate(
                                        "sel => { const e = document.querySelector(sel); return e && e.options && e.options.length > 0 ? e.options[0].value : null; }",
                                        step.getSelector()
                                    );
                                    if (firstOption != null) {
                                        locator.selectOption(firstOption,
                                                new Locator.SelectOptionOptions().setTimeout(8000));
                                    }
                                }
                            }

                            // 액션 후 대기 (지정된 경우)
                            int waitMs = step.getWaitMs() != null ? step.getWaitMs() : 500;
                            if (waitMs > 0) page.waitForTimeout(waitMs);

                            // 단계별 스크린샷
                            String fileName = UUID.randomUUID() + ".png";
                            Path screenshotPath = screenshotDir.resolve(fileName);
                            page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(false));

                            stepResult.put("status", "success");
                            stepResult.put("currentUrl", page.url());
                            stepResult.put("screenshotUrl", "/screenshots/" + fileName);

                        } catch (Exception e) {
                            String msg = e.getMessage() != null ? e.getMessage() : "알 수 없는 오류";
                            Throwable cause = e.getCause();
                            if (cause != null && cause.getMessage() != null && !msg.contains(cause.getMessage())) {
                                msg = msg + "\n원인: " + cause.getMessage();
                            }
                            stepResult.put("status", "error");
                            stepResult.put("errorMessage", msg.length() > 1000 ? msg.substring(0, 1000) + "..." : msg);

                            // 실패해도 스크린샷 남기기
                            try {
                                String fileName = UUID.randomUUID() + ".png";
                                Path screenshotPath = screenshotDir.resolve(fileName);
                                page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(false));
                                stepResult.put("screenshotUrl", "/screenshots/" + fileName);
                            } catch (Exception ignored) {}
                        }

                        stepResults.add(stepResult);
                    }
                }
            }
        } catch (Exception e) {
            scenarioResult.put("status", "error");
            scenarioResult.put("errorMessage", e.getMessage());
            scenarioResult.put("steps", stepResults);
            return scenarioResult;
        }

        long successCount = stepResults.stream().filter(s -> "success".equals(s.get("status"))).count();
        scenarioResult.put("status", successCount == steps.size() ? "success" : "partial");
        scenarioResult.put("successCount", successCount);
        scenarioResult.put("failCount", steps.size() - successCount);
        scenarioResult.put("steps", stepResults);
        return scenarioResult;
    }

    /**
     * 셀렉터로 Locator를 찾되, 실패 시 단순화된 셀렉터로 재시도합니다.
     * 1) 원본 셀렉터
     * 2) " > " 로 분리된 마지막 파트만
     * 3) 첫 번째 클래스만 (예: .my-class)
     */
    private Locator resolveLocator(Page page, String selector) {
        // 원본 시도
        try {
            Locator loc = page.locator(selector).first();
            loc.waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.ATTACHED)
                    .setTimeout(8000));
            return loc;
        } catch (Exception ignored) {}

        // 마지막 파트만 (부모 경로 제거)
        if (selector.contains(" > ")) {
            String lastPart = selector.substring(selector.lastIndexOf(" > ") + 3).trim();
            try {
                Locator loc = page.locator(lastPart).first();
                loc.waitFor(new Locator.WaitForOptions()
                        .setState(com.microsoft.playwright.options.WaitForSelectorState.ATTACHED)
                        .setTimeout(5000));
                return loc;
            } catch (Exception ignored) {}
        }

        // 클래스만 추출 (예: div.foo.bar → .foo)
        String classOnly = selector.replaceAll("^.*?(\\.[\\w-]+).*$", "$1");
        if (classOnly.startsWith(".") && !classOnly.equals(selector)) {
            try {
                Locator loc = page.locator(classOnly).first();
                loc.waitFor(new Locator.WaitForOptions()
                        .setState(com.microsoft.playwright.options.WaitForSelectorState.ATTACHED)
                        .setTimeout(5000));
                return loc;
            } catch (Exception ignored) {}
        }

        // 모두 실패 → 원본으로 반환 (이후 단계에서 에러 처리)
        return page.locator(selector).first();
    }

    private Browser launchBrowser(Playwright playwright, String browserName, boolean headless) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(headless);

        return switch (browserName.toLowerCase()) {
            case "firefox" -> playwright.firefox().launch(options);
            case "webkit" -> playwright.webkit().launch(options);
            case "chromium" -> playwright.chromium().launch(options);
            default -> throw new IllegalArgumentException("지원하지 않는 브라우저입니다: " + browserName);
        };
    }

}