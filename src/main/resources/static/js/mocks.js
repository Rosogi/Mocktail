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

function confirmDelete(form) {
  const name = form.dataset.name;
  return confirm(`Delete ${name}?`);
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
    if (!bodyArea.value.trim() || confirm('Replace current body with preset for ' + ctSelect.value + '?')) {
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
        showFmtFeedback('Nothing to format for ' + ct, 'warning');
        return;
      }
      updateCounter();
      showFmtFeedback('Formatted ✓', 'success');
    } catch (e) {
      showFmtFeedback('Invalid ' + ct.split('/')[1].toUpperCase() + ': ' + e.message, 'danger');
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
    counter.textContent = bodyArea.value.length + ' chars';
  }
}
if (bodyArea) {
  bodyArea.addEventListener('input', updateCounter);
  updateCounter();
}
