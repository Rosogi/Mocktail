/* ============================================================
   mocks.js — helpers for the mock create/edit form
   ============================================================ */

// ── Content-Type preset bodies ───────────────────────────────
const PRESETS = {
  'application/json': '{\n  "message": "ok"\n}',
  'application/xml':  '<?xml version="1.0" encoding="UTF-8"?>\n<response>\n  <message>ok</message>\n</response>',
  'text/xml':
      `<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <Response>
      <message>ok</message>
    </Response>
  </soap:Body>
</soap:Envelope>`,
  'text/plain': 'OK',
  'text/html':  '<!DOCTYPE html>\n<html><body><p>OK</p></body></html>',
};

const mockFormRoot = document.getElementById('mock-form-root');
const UI = mockFormRoot ? mockFormRoot.dataset : {};

function uiText(key, fallback) {
  return UI[key] || fallback;
}

function formatTemplate(template, ...args) {
  return template.replace(/\{(\d+)}/g, (_, index) =>
      args[index] === undefined ? '' : args[index]);
}

function confirmDelete(form) {
  const name = form.dataset.name;
  return confirm(form.dataset.confirmMessage || `Delete ${name}?`);
}

const ctSelect = document.getElementById('ctSelect');
const bodyArea = document.getElementById('responseBody');
const fillBtn  = document.getElementById('fillPresetBtn');
const fmtBtn   = document.getElementById('formatBodyBtn');

// ── Fill preset ──────────────────────────────────────────────
if (fillBtn && ctSelect && bodyArea) {
  fillBtn.addEventListener('click', function () {
    const preset = PRESETS[ctSelect.value];
    if (!preset) return;
    const message = formatTemplate(
        uiText('confirmReplacePreset', 'Replace current body with preset for {0}?'),
        ctSelect.value);
    if (!bodyArea.value.trim() || confirm(message)) {
      bodyArea.value = preset;
      updateCounter();
    }
  });
}

// ── Format body ──────────────────────────────────────────────
if (fmtBtn && ctSelect && bodyArea) {
  fmtBtn.addEventListener('click', function () {
    const ct  = ctSelect.value;
    const raw = bodyArea.value.trim();
    if (!raw) return;

    try {
      if (ct === 'application/json') {
        bodyArea.value = JSON.stringify(JSON.parse(raw), null, 2);
      } else if (ct === 'application/xml' || ct === 'text/xml') {
        bodyArea.value = formatXml(raw);
      } else {
        // nothing to format for plain text / html
        showFmtFeedback(formatTemplate(uiText('formatNothing', 'Nothing to format for {0}'), ct), 'warning');
        return;
      }
      updateCounter();
      showFmtFeedback(uiText('formatSuccess', 'Formatted'), 'success');
    } catch (e) {
      showFmtFeedback(formatTemplate(
          uiText('formatInvalid', 'Invalid {0}: {1}'),
          ct.split('/')[1].toUpperCase(),
          e.message), 'danger');
    }
  });
}

function showFmtFeedback(msg, type) {
  const el = document.getElementById('fmtFeedback');
  if (!el) return;
  el.textContent = msg;
  el.className = 'ms-2 small text-' + type;
  setTimeout(() => { el.textContent = ''; }, 2500);
}

// ── XML formatter ────────────────────────────────────────────
function formatXml(xml) {
  let formatted = '';
  let indent     = 0;
  const tab      = '  ';
  // Split on tags, keeping them
  xml.replace(/>\s*</g, '>\n<').split('\n').forEach(node => {
    node = node.trim();
    if (!node) return;
    if (node.match(/^<\/\w/)) indent--;                     // closing tag
    formatted += tab.repeat(Math.max(indent, 0)) + node + '\n';
    if (node.match(/^<\w[^>]*[^/]>.*$/) &&                 // opening tag
        !node.match(/^<\?/) &&
        !node.includes('</')) {
      indent++;
    }
  });
  return formatted.trim();
}

// ── Character counter ────────────────────────────────────────
function updateCounter() {
  const counter = document.getElementById('bodyCharCount');
  if (counter && bodyArea) {
    counter.textContent = formatTemplate(uiText('charCount', '{0} chars'), bodyArea.value.length);
  }
}
if (bodyArea) {
  bodyArea.addEventListener('input', updateCounter);
  updateCounter();
}
