(function () {
  const KEYWORDS = new Set([
    'and', 'def', 'elif', 'else', 'for', 'if', 'in', 'load', 'not', 'or', 'return'
  ]);
  const CONSTANTS = new Set(['True', 'False', 'None']);

  function escapeHtml(value) {
    return String(value || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
  }

  function highlight(source) {
    const text = String(source || '');
    const token = /("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|#.*|\b\d+(?:\.\d+)?\b|\b[A-Za-z_][A-Za-z0-9_]*\b)/gm;
    let result = '';
    let last = 0;
    let match;
    while ((match = token.exec(text)) !== null) {
      const raw = match[0];
      result += escapeHtml(text.slice(last, match.index));
      if (raw.startsWith('#')) {
        result += '<span class="starlark-comment">' + escapeHtml(raw) + '</span>';
      } else if (raw.startsWith('"') || raw.startsWith("'")) {
        result += '<span class="starlark-str">' + escapeHtml(raw) + '</span>';
      } else if (/^\d/.test(raw)) {
        result += '<span class="starlark-number">' + escapeHtml(raw) + '</span>';
      } else if (KEYWORDS.has(raw)) {
        result += '<span class="starlark-kw">' + escapeHtml(raw) + '</span>';
      } else if (CONSTANTS.has(raw)) {
        result += '<span class="starlark-const">' + escapeHtml(raw) + '</span>';
      } else {
        result += escapeHtml(raw);
      }
      last = match.index + raw.length;
    }
    result += escapeHtml(text.slice(last));
    return result || ' ';
  }

  function initEditor(textarea) {
    const wrapper = textarea.closest('.starlark-editor');
    const preview = wrapper ? wrapper.querySelector('[data-starlark-highlight]') : null;
    if (!preview) return;
    function update() {
      preview.innerHTML = highlight(textarea.value);
      preview.style.minHeight = textarea.offsetHeight + 'px';
    }
    textarea.addEventListener('scroll', function () {
      preview.scrollTop = textarea.scrollTop;
      preview.scrollLeft = textarea.scrollLeft;
    });
    textarea.addEventListener('keydown', function (event) {
      if (event.key !== 'Tab') return;
      event.preventDefault();
      if (event.shiftKey) {
        unindentSelection(textarea);
      } else {
        indentSelection(textarea);
      }
      update();
      textarea.dispatchEvent(new Event('input', {bubbles: true}));
    });
    textarea.addEventListener('input', update);
    update();
  }

  function indentSelection(textarea) {
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const value = textarea.value;
    if (start === end) {
      textarea.setRangeText('    ', start, end, 'end');
      return;
    }
    const lineStart = value.lastIndexOf('\n', start - 1) + 1;
    const selected = value.slice(lineStart, end);
    const indented = selected.replace(/^/gm, '    ');
    textarea.setRangeText(indented, lineStart, end, 'select');
    textarea.selectionStart = start + 4;
    textarea.selectionEnd = end + (indented.length - selected.length);
  }

  function unindentSelection(textarea) {
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const value = textarea.value;
    const lineStart = value.lastIndexOf('\n', start - 1) + 1;
    const selected = value.slice(lineStart, end);
    const unindented = selected.replace(/^( {1,4}|\t)/gm, '');
    textarea.setRangeText(unindented, lineStart, end, 'select');
    const removedBeforeStart = selected.slice(0, start - lineStart).length -
        selected.slice(0, start - lineStart).replace(/^( {1,4}|\t)/gm, '').length;
    textarea.selectionStart = Math.max(lineStart, start - removedBeforeStart);
    textarea.selectionEnd = Math.max(textarea.selectionStart, end - (selected.length - unindented.length));
  }

  function splitParams(raw) {
    const result = [];
    let quote = '';
    let depth = 0;
    let start = 0;
    for (let i = 0; i < raw.length; i += 1) {
      const ch = raw[i];
      if (quote) {
        if (ch === quote && raw[i - 1] !== '\\') quote = '';
        continue;
      }
      if (ch === '"' || ch === "'") quote = ch;
      else if (ch === '(' || ch === '[' || ch === '{') depth += 1;
      else if (ch === ')' || ch === ']' || ch === '}') depth -= 1;
      else if (ch === ',' && depth === 0) {
        result.push(raw.slice(start, i));
        start = i + 1;
      }
    }
    result.push(raw.slice(start));
    return result.map(function (part) { return part.trim(); }).filter(Boolean);
  }

  function parseTypedParams(raw) {
    return splitParams(raw).map(function (part) {
      const beforeDefault = part.split('=')[0].trim();
      const tokens = beforeDefault.replace(':', ' ').replace(/\s+/g, ' ').split(' ');
      return {
        name: (tokens[0] || '').replace(/[^A-Za-z0-9_]/g, ''),
        type: (tokens[1] || 'string').replace(/\?$/, '').toLowerCase()
      };
    }).filter(function (param) { return param.name; });
  }

  function parseSignature(signature) {
    const text = String(signature || '');
    const open = text.indexOf('(');
    const close = text.indexOf(')', open + 1);
    if (open < 0 || close < open) return [];
    return parseTypedParams(text.slice(open + 1, close));
  }

  function parseSourceParams(source) {
    const lines = String(source || '').split(/\r?\n/);
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed.startsWith('def ') || !trimmed.endsWith(':')) continue;
      const open = trimmed.indexOf('(');
      const close = trimmed.indexOf(')', open + 1);
      if (open < 0 || close < open) continue;
      return splitParams(trimmed.slice(open + 1, close)).map(function (part) {
        return {
          name: part.split('=')[0].trim().replace(/[^A-Za-z0-9_]/g, ''),
          type: 'string'
        };
      }).filter(function (param) { return param.name; });
    }
    return [];
  }

  function isNumberType(type) {
    return ['number', 'int', 'integer', 'long', 'float', 'double'].includes(String(type || '').toLowerCase());
  }

  function isBooleanType(type) {
    return ['bool', 'boolean'].includes(String(type || '').toLowerCase());
  }

  function renderTestArgs(container, params) {
    const values = Array.from(container.querySelectorAll('[name="testArgValues"]')).map(function (input) {
      return input.value;
    });
    const emptyLabel = container.dataset.emptyLabel || 'The function has no arguments.';
    const valueLabel = container.dataset.valueLabel || 'Value';
    if (!params.length) {
      container.innerHTML = '<div class="text-muted small py-2" data-empty-args>' + escapeHtml(emptyLabel) + '</div>';
      return;
    }
    container.innerHTML = params.map(function (param, index) {
      const type = param.type || 'string';
      const value = values[index] || '';
      let control;
      if (isBooleanType(type)) {
        control = '<select class="form-select form-select-sm" name="testArgValues">' +
            '<option value=""' + (value === '' ? ' selected' : '') + '></option>' +
            '<option value="true"' + (value === 'true' ? ' selected' : '') + '>true</option>' +
            '<option value="false"' + (value === 'false' ? ' selected' : '') + '>false</option>' +
            '</select>';
      } else if (isNumberType(type)) {
        control = '<input type="number" step="any" class="form-control form-control-sm" name="testArgValues" ' +
            'value="' + escapeHtml(value) + '" placeholder="' + escapeHtml(valueLabel) + '"/>';
      } else {
        control = '<input type="text" class="form-control form-control-sm font-monospace" name="testArgValues" ' +
            'value="' + escapeHtml(value) + '" placeholder="' + escapeHtml(valueLabel) + '"/>';
      }
      return '<div class="function-test-arg" data-test-arg>' +
          '<input type="hidden" name="testArgNames" value="' + escapeHtml(param.name) + '"/>' +
          '<input type="hidden" name="testArgTypes" value="' + escapeHtml(type) + '"/>' +
          '<label class="form-label small mb-1">' +
          '<span class="font-monospace">' + escapeHtml(param.name) + '</span>' +
          '<span class="text-muted"> ' + escapeHtml(type) + '</span>' +
          '</label>' +
          control +
          '</div>';
    }).join('');
  }

  function insertAtCursor(textarea, text) {
    if (!textarea || textarea.readOnly) return;
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    textarea.setRangeText(text, start, end, 'end');
    textarea.focus();
    textarea.dispatchEvent(new Event('input', {bubbles: true}));
  }

  function renderFunctionParameters(container, params) {
    const label = container.dataset.label || 'Parameters';
    const editor = document.querySelector('[data-starlark-editor]');
    container.innerHTML = '';
    if (!params.length) return;
    const labelEl = document.createElement('span');
    labelEl.className = 'small text-muted me-1';
    labelEl.textContent = label;
    container.appendChild(labelEl);
    params.forEach(function (param) {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'btn btn-sm btn-outline-secondary font-monospace function-parameter';
      button.dataset.name = param.name;
      button.textContent = param.name;
      button.addEventListener('click', function () {
        insertAtCursor(editor, param.name);
      });
      container.appendChild(button);
    });
  }

  function initFunctionParameters(container) {
    const form = container.closest('form');
    if (!form) return;
    const signature = form.querySelector('[data-function-signature]');
    function update() {
      renderFunctionParameters(container, parseSignature(signature ? signature.value : ''));
    }
    if (signature) signature.addEventListener('input', update);
    update();
  }

  function initTestArgs(container) {
    const form = container.closest('form');
    if (!form) return;
    const signature = form.querySelector('[data-function-signature]');
    const source = form.querySelector('[data-starlark-editor]');
    function update() {
      const params = parseSignature(signature ? signature.value : '');
      renderTestArgs(container, params.length ? params : parseSourceParams(source ? source.value : ''));
    }
    if (signature) signature.addEventListener('input', update);
    if (source) source.addEventListener('input', update);
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-starlark-editor]').forEach(initEditor);
    document.querySelectorAll('[data-function-parameters]').forEach(initFunctionParameters);
    document.querySelectorAll('[data-test-args]').forEach(initTestArgs);
  });
})();
