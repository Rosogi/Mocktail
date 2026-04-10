/* ============================================================
   mocks.js — helpers for the mock create/edit form
   ============================================================ */

// ── Content-Type preset bodies ───────────────────────────────
const PRESETS = {
  'application/json': '{\n  "message": "ok"\n}',
  'application/xml':  '<?xml version="1.0" encoding="UTF-8"?>\n<response>\n  <message>ok</message>\n</response>',
  'text/xml':         '<?xml version="1.0" encoding="UTF-8"?>\n<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">\n  <soap:Body>\n    <Response>\n      <message>ok</message>\n    </Response>\n  </soap:Body>\n</soap:Envelope>',
  'text/plain':       'OK',
  'text/html':        '<!DOCTYPE html>\n<html><body><p>OK</p></body></html>',
};

const ctSelect   = document.getElementById('ctSelect');
const bodyArea   = document.getElementById('responseBody');
const fillBtn    = document.getElementById('fillPresetBtn');

function confirmDelete(form) {
  const name = form.dataset.name;
  return confirm(`Delete ${name}?`);
}

if (ctSelect && bodyArea && fillBtn) {
  fillBtn.addEventListener('click', function () {
    const ct      = ctSelect.value;
    const preset  = PRESETS[ct];
    if (preset && (!bodyArea.value || bodyArea.value.trim() === '')) {
      bodyArea.value = preset;
    } else if (preset) {
      if (confirm('Replace current body with preset for ' + ct + '?')) {
        bodyArea.value = preset;
      }
    }
  });
}

// ── Live character counter for response body ─────────────────
if (bodyArea) {
  const counter = document.getElementById('bodyCharCount');
  function updateCounter() {
    if (counter) counter.textContent = bodyArea.value.length + ' chars';
  }
  bodyArea.addEventListener('input', updateCounter);
  updateCounter();
}
