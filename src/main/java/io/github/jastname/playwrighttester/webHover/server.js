const express = require("express");
const path = require("path");
const { chromium } = require("playwright");

const app = express();
const PORT = 3001;
const HOST = "http://localhost:" + PORT;

app.use(express.json({ limit: "1mb" }));
app.use(express.static(path.join(__dirname, "public")));

let latestElementInfo = {
  url: "",
  tag: "-",
  id: "-",
  className: "-",
  text: "-",
  selector: "-",
  rect: null,
  timestamp: null,
};

app.get("/api/hover-info", (req, res) => {
  res.json(latestElementInfo);
});

function buildInjectedScript(panelOrigin) {
  const panelOriginLiteral = JSON.stringify(panelOrigin);

  return [
    "(function () {",
    "  if (window.__hoverInspectorInstalled) return;",
    "  if (location.origin === " + panelOriginLiteral + " && location.pathname === '/panel.html') return;",
    "  window.__hoverInspectorInstalled = true;",
    "",
    "  var lastSignature = '';",
    "",
    "  function getSimpleSelector(el) {",
    "    if (!el || el.nodeType !== 1) return '';",
    "",
    "    var parts = [];",
    "    var current = el;",
    "",
    "    while (current && current.nodeType === 1) {",
    "      var part = current.tagName.toLowerCase();",
    "",
    "      if (current.id) {",
    "        part += '#' + current.id;",
    "        parts.unshift(part);",
    "        break;",
    "      }",
    "",
    "      var classList = Array.from(current.classList || []).slice(0, 2);",
    "      if (classList.length) {",
    "        part += '.' + classList.join('.');",
    "      }",
    "",
    "      var parent = current.parentElement;",
    "      if (parent) {",
    "        var sameTagSiblings = Array.from(parent.children).filter(function (child) {",
    "          return child.tagName === current.tagName;",
    "        });",
    "",
    "        if (sameTagSiblings.length > 1) {",
    "          var index = sameTagSiblings.indexOf(current) + 1;",
    "          part += ':nth-of-type(' + index + ')';",
    "        }",
    "      }",
    "",
    "      parts.unshift(part);",
    "      current = current.parentElement;",
    "    }",
    "",
    "    return parts.join(' > ');",
    "  }",
    "",
    "  function ensureOverlay() {",
    "    var overlay = document.getElementById('__hover_inspector_overlay__');",
    "    if (overlay) return overlay;",
    "",
    "    overlay = document.createElement('div');",
    "    overlay.id = '__hover_inspector_overlay__';",
    "    Object.assign(overlay.style, {",
    "      position: 'fixed',",
    "      pointerEvents: 'none',",
    "      zIndex: '2147483647',",
    "      border: '2px solid red',",
    "      background: 'rgba(255, 0, 0, 0.08)',",
    "      left: '0px',",
    "      top: '0px',",
    "      width: '0px',",
    "      height: '0px',",
    "      boxSizing: 'border-box'",
    "    });",
    "",
    "    document.documentElement.appendChild(overlay);",
    "    return overlay;",
    "  }",
    "",
    "  function updateOverlay(el) {",
    "    if (!el || !(el instanceof Element)) return;",
    "",
    "    var rect = el.getBoundingClientRect();",
    "    var overlay = ensureOverlay();",
    "",
    "    overlay.style.left = rect.left + 'px';",
    "    overlay.style.top = rect.top + 'px';",
    "    overlay.style.width = rect.width + 'px';",
    "    overlay.style.height = rect.height + 'px';",
    "  }",
    "",
    "  function buildInfo(el) {",
    "    if (!el) return null;",
    "",
    "    var rect = el.getBoundingClientRect();",
    "    var className = '-';",
    "    if (typeof el.className === 'string' && el.className.trim()) {",
    "      className = el.className;",
    "    }",
    "",
    "    var text = (el.innerText || el.textContent || '').trim().slice(0, 300) || '-';",
    "",
    "    return {",
    "      url: location.href,",
    "      tag: el.tagName ? el.tagName.toLowerCase() : '-',",
    "      id: el.id || '-',",
    "      className: className,",
    "      text: text,",
    "      selector: getSimpleSelector(el) || '-',",
    "      rect: {",
    "        x: Math.round(rect.x),",
    "        y: Math.round(rect.y),",
    "        width: Math.round(rect.width),",
    "        height: Math.round(rect.height)",
    "      }",
    "    };",
    "  }",
    "",
    "  document.addEventListener('mousemove', function (e) {",
    "    var el = document.elementFromPoint(e.clientX, e.clientY);",
    "    if (!el) return;",
    "    updateOverlay(el);",
    "  }, true);",
    "",
    "  document.addEventListener('contextmenu', function (e) {",
    "    var el = document.elementFromPoint(e.clientX, e.clientY);",
    "    if (!el) return;",
    "",
    "    updateOverlay(el);",
    "    e.preventDefault();",
    "    e.stopPropagation();",
    "",
    "    var info = buildInfo(el);",
    "    if (!info) return;",
    "",
    "    var signature = JSON.stringify(info);",
    "    if (signature !== lastSignature) {",
    "      lastSignature = signature;",
    "    }",
    "",
    "    if (typeof window.__reportHoverInfo === 'function') {",
    "      window.__reportHoverInfo(info);",
    "    }",
    "  }, true);",
    "})();",
  ].join("\n");
}

async function main() {
  const server = app.listen(PORT, () => {
    console.log("Server running at " + HOST);
  });

  const browser = await chromium.launch({
    headless: false,
  });

  const context = await browser.newContext({
    viewport: { width: 1400, height: 900 },
  });

  await context.exposeBinding("__reportHoverInfo", async ({ page }, info) => {
    if (!page) return;
    const pageUrl = page.url();

    if (pageUrl === HOST + "/panel.html") {
      return;
    }

    latestElementInfo = {
      url: info?.url || "",
      tag: info?.tag || "-",
      id: info?.id || "-",
      className: info?.className || "-",
      text: info?.text || "-",
      selector: info?.selector || "-",
      rect: info?.rect || null,
      timestamp: new Date().toISOString(),
    };

    console.log("hover:", latestElementInfo.tag, latestElementInfo.selector);
  });

  await context.addInitScript({
    content: buildInjectedScript(HOST),
  });

  const targetPage = await context.newPage();
  const panelPage = await context.newPage();

  await panelPage.goto(HOST + "/panel.html", {
    waitUntil: "domcontentloaded",
  });

  await targetPage.goto("https://www.museum.go.kr/MUSEUM/main/index.do", {
    waitUntil: "domcontentloaded",
  });

  console.log("Target:", targetPage.url());
  console.log("Panel:", panelPage.url());

  async function shutdown() {
    try {
      await browser.close();
    } catch (e) {}

    await new Promise((resolve) => server.close(resolve));
    process.exit(0);
  }

  process.on("SIGINT", shutdown);
  process.on("SIGTERM", shutdown);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});