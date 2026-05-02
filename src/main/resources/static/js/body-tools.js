/* ============================================================
   Shared response body helpers
   ============================================================ */

(function () {
  const PRESETS = {
    'application/json': '{\n  "message": "ok"\n}',
    'application/xml': '<?xml version="1.0" encoding="UTF-8"?>\n<response>\n  <message>ok</message>\n</response>',
    'application/soap+xml':
        `<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
  <soap:Body>
    <Response>
      <message>ok</message>
    </Response>
  </soap:Body>
</soap:Envelope>`,
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
    'text/html': '<!DOCTYPE html>\n<html>\n  <body>\n    <p>OK</p>\n  </body>\n</html>',
    'application/x-www-form-urlencoded': 'status=ok&message=OK'
  };

  function normalizeContentType(contentType) {
    return (contentType || '').split(';')[0].trim().toLowerCase();
  }

  function formatTemplate(template, ...args) {
    return template.replace(/\{(\d+)}/g, (_, index) =>
        args[index] === undefined ? '' : args[index]);
  }

  function formatBody(raw, contentType) {
    const ct = normalizeContentType(contentType);
    const value = raw == null ? '' : String(raw).trim();
    if (!value) {
      return { handled: true, formatted: '' };
    }

    if (ct === 'application/json' || ct.endsWith('+json')) {
      return { handled: true, formatted: JSON.stringify(JSON.parse(value), null, 2) };
    }
    if (ct === 'application/xml' || ct === 'text/xml' || ct === 'application/soap+xml' || ct.endsWith('+xml')) {
      return { handled: true, formatted: formatXml(value) };
    }
    if (ct === 'text/html') {
      return { handled: true, formatted: formatXml(value) };
    }
    if (ct === 'application/x-www-form-urlencoded') {
      return { handled: true, formatted: formatFormUrlEncoded(value) };
    }
    return { handled: false, formatted: value };
  }

  function prettyText(raw, contentType) {
    const value = raw == null ? '' : String(raw);
    if (!value.trim()) return '';

    try {
      const formatted = formatBody(value, contentType);
      if (formatted.handled) return formatted.formatted;
    } catch (_) {
      // Fall through to content sniffing.
    }

    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch (_) {
      // Not JSON.
    }

    if (value.trim().startsWith('<')) {
      try {
        return formatXml(value);
      } catch (_) {
        return value;
      }
    }
    return value;
  }

  function formatFormUrlEncoded(raw) {
    const params = new URLSearchParams(raw);
    const lines = [];
    params.forEach((value, key) => lines.push(`${key}=${value}`));
    return lines.length ? lines.join('\n') : raw;
  }

  function formatXml(xml) {
    let formatted = '';
    let indent = 0;
    const tab = '  ';

    xml.replace(/>\s*</g, '>\n<').split('\n').forEach(node => {
      node = node.trim();
      if (!node) return;
      if (/^<\//.test(node)) indent--;
      formatted += tab.repeat(Math.max(indent, 0)) + node + '\n';
      if (/^<[^!?/][^>]*[^/]>\s*$/.test(node) && !node.includes('</')) {
        indent++;
      }
    });
    return formatted.trim();
  }

  function initResponseBodyTools(root) {
    const container = typeof root === 'string' ? document.querySelector(root) : root;
    if (!container) return;

    const data = container.dataset || {};
    const ctSelect = container.querySelector('[data-body-tool="content-type"]');
    const bodyArea = container.querySelector('[data-body-tool="body"]');
    const fillBtn = container.querySelector('[data-body-tool="fill-preset"]');
    const formatBtn = container.querySelector('[data-body-tool="format"]');
    const feedback = container.querySelector('[data-body-tool="feedback"]');
    const counter = container.querySelector('[data-body-tool="counter"]');

    if (!ctSelect || !bodyArea) return;

    const uiText = (key, fallback) => data[key] || fallback;
    const showFeedback = (message, type) => {
      if (!feedback) return;
      feedback.textContent = message;
      feedback.className = 'ms-2 small text-' + type;
      setTimeout(() => { feedback.textContent = ''; }, 2500);
    };
    const updateCounter = () => {
      if (!counter) return;
      counter.textContent = formatTemplate(uiText('charCount', '{0} chars'), bodyArea.value.length);
    };

    if (fillBtn) {
      fillBtn.addEventListener('click', function () {
        const ct = normalizeContentType(ctSelect.value);
        const preset = PRESETS[ct];
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

    if (formatBtn) {
      formatBtn.addEventListener('click', function () {
        const ct = ctSelect.value;
        const raw = bodyArea.value;
        if (!raw.trim()) return;

        try {
          const result = formatBody(raw, ct);
          if (!result.handled) {
            showFeedback(formatTemplate(uiText('formatNothing', 'Nothing to format for {0}'), ct), 'warning');
            return;
          }
          bodyArea.value = result.formatted;
          updateCounter();
          showFeedback(uiText('formatSuccess', 'Formatted'), 'success');
        } catch (e) {
          showFeedback(formatTemplate(
              uiText('formatInvalid', 'Invalid {0}: {1}'),
              normalizeContentType(ct).split('/').pop().toUpperCase(),
              e.message), 'danger');
        }
      });
    }

    bodyArea.addEventListener('input', updateCounter);
    updateCounter();
  }

  window.MocktailBodyTools = {
    PRESETS,
    formatBody,
    prettyText,
    formatXml,
    initResponseBodyTools
  };

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-body-tools]').forEach(initResponseBodyTools);
  });
})();
