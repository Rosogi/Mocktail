/* ============================================================
   dashboard.js
   – Connects to STOMP/WebSocket
   – Appends incoming log rows in real time
   – Opens a detail modal on row click
   ============================================================ */

// ── Globals injected from Thymeleaf via data- attributes ────
const dashboardEl = document.getElementById('dashboard-root');
const LOG_TOPIC   = dashboardEl.dataset.logTopic;
const I18N        = dashboardEl.dataset;
const CSRF_PARAM  = dashboardEl.dataset.csrfParam;
const CSRF_TOKEN  = dashboardEl.dataset.csrfToken;
const ACTIVE_FILTERS = {
  search: (dashboardEl.dataset.filterSearch || '').toLowerCase(),
  method: dashboardEl.dataset.filterMethod || '',
  status: dashboardEl.dataset.filterStatus || '',
  fromTimestamp: parseTimestamp(dashboardEl.dataset.filterFromTimestamp),
  toTimestamp: parseTimestamp(dashboardEl.dataset.filterToTimestamp),
};

function uiText(key, fallback) {
  return I18N[key] || fallback;
}

function parseTimestamp(value) {
  if (!value) return null;
  const parsed = Date.parse(value);
  return Number.isNaN(parsed) ? null : parsed;
}

function initDashboardPeriodFilter() {
  const filter = document.querySelector('[data-dashboard-period-filter]');
  if (!filter) return;

  const valueInput = filter.querySelector('[data-dashboard-period-value]');
  const label = filter.querySelector('[data-dashboard-period-label]');
  const toggle = filter.querySelector('[data-bs-toggle="dropdown"]');
  const dateFrom = filter.querySelector('[data-dashboard-date-from]');
  const dateTo = filter.querySelector('[data-dashboard-date-to]');
  const choices = Array.from(filter.querySelectorAll('[data-dashboard-period-choice]'));
  const labels = new Map(choices.map(choice => [choice.dataset.dashboardPeriodChoice || '', choice.textContent.trim()]));
  const customLabel = filter.dataset.periodCustomLabel || labels.get('custom') || 'Custom range';

  function setCustomEnabled(enabled) {
    [dateFrom, dateTo].forEach(input => {
      if (input) {
        input.disabled = !enabled;
      }
    });
  }

  function currentLabel() {
    const value = valueInput?.value || '';
    if (value !== 'custom') {
      return labels.get(value) || labels.get('') || '';
    }
    const from = dateFrom?.value || '';
    const to = dateTo?.value || '';
    return from || to
        ? `${customLabel}: ${from || '...'} - ${to || '...'}`
        : customLabel;
  }

  function updateLabel() {
    if (label) {
      label.textContent = currentLabel();
    }
  }

  function hideMenu() {
    if (!toggle || !window.bootstrap?.Dropdown) return;
    bootstrap.Dropdown.getOrCreateInstance(toggle).hide();
  }

  function choose(value, focusCustom) {
    if (valueInput) {
      valueInput.value = value;
    }
    const custom = value === 'custom';
    setCustomEnabled(custom);
    updateLabel();
    if (custom && focusCustom) {
      dateFrom?.focus();
    } else if (!custom) {
      hideMenu();
    }
  }

  choices.forEach(choice => {
    choice.addEventListener('click', () => {
      choose(choice.dataset.dashboardPeriodChoice || '', choice.dataset.dashboardPeriodChoice === 'custom');
    });
  });

  [dateFrom, dateTo].forEach(input => {
    input?.addEventListener('focus', () => choose('custom', false));
    input?.addEventListener('input', () => choose('custom', false));
    input?.addEventListener('change', () => choose('custom', false));
  });

  choose(valueInput?.value || '', false);
}

initDashboardPeriodFilter();

// ── State ───────────────────────────────────────────────────
let totalCount = parseInt(document.getElementById('log-count').textContent, 10) || 0;

// ── Helpers ─────────────────────────────────────────────────
function escHtml(str) {
  if (!str) return '';
  return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
}

function methodClass(method) {
  const map = {
    GET: 'method-GET', POST: 'method-POST', PUT: 'method-PUT',
    PATCH: 'method-PATCH', DELETE: 'method-DELETE',
    HEAD: 'method-HEAD', OPTIONS: 'method-OPTIONS'
  };
  return map[method] || 'method-ANY';
}

function statusClass(status) {
  if (!status) return 'status-unknown';
  if (status < 300) return 'status-2xx';
  if (status < 400) return 'status-3xx';
  if (status < 500) return 'status-4xx';
  return 'status-5xx';
}

function tryPrettyJson(str) {
  if (!str) return '';
  try {
    return JSON.stringify(JSON.parse(str), null, 2);
  } catch (_) {
    return str;
  }
}

function prettyText(str, contentType) {
  if (!str) return '';
  if (window.MocktailBodyTools) {
    return window.MocktailBodyTools.prettyText(str, contentType);
  }
  return tryPrettyJson(str);
}

function statusGroup(status) {
  const value = Number.parseInt(status || '0', 10);
  if (value >= 200 && value < 300) return '2xx';
  if (value >= 300 && value < 400) return '3xx';
  if (value >= 400 && value < 500) return '4xx';
  if (value >= 500 && value < 600) return '5xx';
  return '';
}

function matchesActiveFilters(log) {
  if (ACTIVE_FILTERS.method && log.method !== ACTIVE_FILTERS.method) {
    return false;
  }
  if (ACTIVE_FILTERS.status && statusGroup(log.status) !== ACTIVE_FILTERS.status) {
    return false;
  }
  const timestamp = parseTimestamp(log.timestamp);
  if (ACTIVE_FILTERS.fromTimestamp !== null &&
      (timestamp === null || timestamp < ACTIVE_FILTERS.fromTimestamp)) {
    return false;
  }
  if (ACTIVE_FILTERS.toTimestamp !== null &&
      (timestamp === null || timestamp >= ACTIVE_FILTERS.toTimestamp)) {
    return false;
  }
  if (ACTIVE_FILTERS.search) {
    const searchable = [
      log.path,
      log.queryParams,
      log.remoteAddr,
      log.remoteDisplayName,
      log.matchedMock,
    ].filter(Boolean).join(' ').toLowerCase();
    if (!searchable.includes(ACTIVE_FILTERS.search)) {
      return false;
    }
  }
  return true;
}

// ── Request detail modal ─────────────────────────────────────
const detailModal     = new bootstrap.Modal(document.getElementById('requestDetailModal'));
const modalMethod     = document.getElementById('modal-method');
const modalPath       = document.getElementById('modal-path');
const modalTime       = document.getElementById('modal-time');
const modalRemoteName = document.getElementById('modal-remote-name');
const modalRemoteAddr = document.getElementById('modal-remote-address');
const modalStatus     = document.getElementById('modal-status');
const modalMock       = document.getElementById('modal-mock');
const modalReqHeaders = document.getElementById('modal-req-headers');
const modalReqBody    = document.getElementById('modal-req-body');
const modalResBody    = document.getElementById('modal-res-body');
const modalContentType= document.getElementById('modal-content-type');
const modalQuery      = document.getElementById('modal-query');
const modalDeleteForm = document.getElementById('modal-delete-log-form');

const detailBlocks = {
  'modal-req-headers': { raw: '', pretty: '', mode: 'pretty', element: modalReqHeaders },
  'modal-req-body':    { raw: '', pretty: '', mode: 'pretty', element: modalReqBody },
  'modal-res-body':    { raw: '', pretty: '', mode: 'pretty', element: modalResBody },
};

// Store full log data keyed by id (for live rows)
const logStore = {};

function openDetail(logId) {
  const data = logStore[logId];
  if (!data) {
    // Row came from server-side render — read data- attrs from the row
    const row = document.getElementById('log-' + logId);
    if (!row) return;
    fillModal({
      method:      row.dataset.method,
      path:        row.dataset.path,
      time:        row.dataset.time,
      timestamp:   row.dataset.timestamp,
      remoteAddr:  row.dataset.remote,
      remoteDisplayName: row.dataset.remoteName,
      status:      row.dataset.status,
      matchedMock: row.dataset.mock,
      queryParams: row.dataset.query,
      contentType: row.dataset.ct,
      requestHeaders: row.dataset.reqHeaders,
      requestBody: row.dataset.reqBody,
      responseBody: row.dataset.resBody,
      id: logId,
    });
  } else {
    fillModal(data);
  }
  detailModal.show();
}

function fillModal(d) {
  const logId = d.id || d.logId;
  modalMethod.textContent      = d.method || '—';
  modalMethod.className        = 'method-badge ' + methodClass(d.method);
  modalPath.textContent        = d.path || '—';
  modalTime.textContent        = d.time || '—';
  if (modalRemoteName) {
    const remoteName = d.remoteDisplayName || '';
    modalRemoteName.textContent = remoteName;
    modalRemoteName.classList.toggle('d-none', !remoteName);
  }
  if (modalRemoteAddr) {
    modalRemoteAddr.textContent = d.remoteAddr || '—';
  }
  modalStatus.textContent      = d.status || '—';
  modalStatus.className        = 'badge ' + statusClass(parseInt(d.status));
  modalMock.textContent        = d.matchedMock || '— ' + uiText('i18nNoMatch', 'no match') + ' —';
  modalQuery.textContent       = d.queryParams || '—';
  modalContentType.textContent = d.contentType || '—';
  setDetailBlock('modal-req-headers', d.requestHeaders, 'application/json');
  setDetailBlock('modal-req-body', d.requestBody, d.contentType);
  setDetailBlock('modal-res-body', d.responseBody, '');
  if (modalDeleteForm && logId) {
    modalDeleteForm.action = `/dashboard/logs/${logId}/delete`;
  }
}

function setDetailBlock(id, raw, contentType) {
  const block = detailBlocks[id];
  if (!block) return;
  block.raw = raw || '';
  block.pretty = prettyText(block.raw, contentType);
  renderDetailBlock(id);
}

function renderDetailBlock(id) {
  const block = detailBlocks[id];
  if (!block || !block.element) return;
  const value = block.mode === 'raw' ? block.raw : block.pretty;
  block.element.textContent = value || uiText('i18nEmpty', '(empty)');
}

document.querySelectorAll('.detail-view-toggle').forEach(group => {
  const target = group.dataset.detailTarget;
  group.querySelectorAll('button[data-view-mode]').forEach(button => {
    button.addEventListener('click', function () {
      const block = detailBlocks[target];
      if (!block) return;
      block.mode = button.dataset.viewMode;
      group.querySelectorAll('button[data-view-mode]').forEach(btn =>
          btn.classList.toggle('active', btn === button));
      renderDetailBlock(target);
    });
  });
});

// Make rows from server-side render clickable
document.querySelectorAll('tr[data-log-id]').forEach(row => {
  row.style.cursor = 'pointer';
  row.addEventListener('click', () => openDetail(row.dataset.logId));
});

// ── Live row builder ─────────────────────────────────────────
function clearEmptyState() {
  const empty = document.querySelector('#log-table-body .empty-state');
  if (empty) empty.closest('tr').remove();
}

function addLiveRow(log) {
  totalCount++;
  document.getElementById('log-count').textContent = totalCount;
  if (!matchesActiveFilters(log)) {
    return;
  }

  clearEmptyState();
  logStore[log.id] = log;

  const path = log.path + (log.queryParams ? '?' + log.queryParams : '');

  const tr = document.createElement('tr');
  tr.id                      = 'log-' + log.id;
  tr.className               = 'live-row';
  tr.dataset.logId           = log.id;
  tr.dataset.method          = log.method;
  tr.dataset.path            = log.path;
  tr.dataset.time            = log.time;
  tr.dataset.timestamp       = log.timestamp || '';
  tr.dataset.remote          = log.remoteAddr || '';
  tr.dataset.remoteName      = log.remoteDisplayName || '';
  tr.dataset.status          = log.status || '';
  tr.dataset.mock            = log.matchedMock || '';
  tr.dataset.query           = log.queryParams || '';
  tr.dataset.ct              = log.contentType || '';
  tr.dataset.reqHeaders      = log.requestHeaders || '';
  tr.dataset.reqBody         = log.requestBody || '';
  tr.dataset.resBody         = log.responseBody || '';
  tr.style.cursor            = 'pointer';

  tr.innerHTML = `
    <td class="log-time">${escHtml(log.time)}</td>
    <td><span class="method-badge ${methodClass(log.method)}">${escHtml(log.method)}</span></td>
    <td class="log-path" title="${escHtml(path)}">${escHtml(path)}</td>
    <td><span class="badge ${statusClass(log.status)}">${log.status ?? '?'}</span></td>
    <td class="log-mock-name">${escHtml(log.matchedMock) || '<span class="text-muted fst-italic">' + escHtml(uiText('i18nNoMatch', 'no match')) + '</span>'}</td>
    <td class="log-remote">${remoteHostHtml(log.remoteDisplayName, log.remoteAddr)}</td>
    <td class="actions-cell">${deleteLogFormHtml(log.id)}</td>`;

  tr.addEventListener('click', () => openDetail(log.id));

  const tbody = document.getElementById('log-table-body');
  tbody.prepend(tr);

}

function deleteLogFormHtml(logId) {
  const csrfInput = CSRF_PARAM && CSRF_TOKEN
      ? `<input type="hidden" name="${escHtml(CSRF_PARAM)}" value="${escHtml(CSRF_TOKEN)}"/>`
      : '';
  return `
    <form action="/dashboard/logs/${logId}/delete" method="post" class="d-inline" onclick="event.stopPropagation()">
      ${csrfInput}
      <button type="submit"
              class="btn btn-sm btn-outline-danger py-0 px-2"
              title="${escHtml(uiText('actionsDelete', 'Delete'))}"
              data-confirm-message="${escHtml(uiText('i18nDeleteLogConfirm', 'Delete this request log?'))}"
              onclick="event.stopPropagation(); return confirm(this.dataset.confirmMessage)">
        <i class="bi bi-trash"></i>
      </button>
    </form>`;
}

function remoteHostHtml(displayName, address) {
  const name = displayName
      ? `<div class="log-remote-name">${escHtml(displayName)}</div>`
      : '';
  return `${name}<div class="log-remote-address">${escHtml(address) || ''}</div>`;
}

// ── WebSocket / STOMP ────────────────────────────────────────
const liveBadge = document.getElementById('live-badge');

const socket = new SockJS('/ws');
const stomp  = Stomp.over(socket);
stomp.debug  = null; // silence console noise

stomp.connect({}, function onConnected() {
  liveBadge.className   = 'badge bg-success';
  liveBadge.textContent = uiText('i18nLive', '● LIVE');

  stomp.subscribe('/topic/logs/' + LOG_TOPIC, function(msg) {
    const log = JSON.parse(msg.body);
    addLiveRow(log);
  });

}, function onError() {
  liveBadge.className   = 'badge bg-secondary';
  liveBadge.textContent = uiText('i18nOffline', 'OFFLINE');
});

// ── Modal drag (за header) ───────────────────────────────────
(function initModalDrag() {
  const modalEl = document.getElementById('requestDetailModal');
  if (!modalEl) return;

  modalEl.addEventListener('shown.bs.modal', function () {
    const dialog  = modalEl.querySelector('.modal-dialog');
    const header  = modalEl.querySelector('.modal-header-custom');
    if (!dialog || !header) return;

    // Позиционируем абсолютно чтобы drag работал
    dialog.style.position = 'relative';

    let startX, startY, startLeft, startTop;

    header.addEventListener('mousedown', function (e) {
      // Игнорируем клики по кнопке закрытия
      if (e.target.closest('.btn-close')) return;

      const rect = dialog.getBoundingClientRect();
      startX    = e.clientX;
      startY    = e.clientY;
      startLeft = rect.left;
      startTop  = rect.top;

      function onMouseMove(e) {
        const dx = e.clientX - startX;
        const dy = e.clientY - startY;
        dialog.style.position  = 'fixed';
        dialog.style.margin    = '0';
        dialog.style.left      = Math.max(0, startLeft + dx) + 'px';
        dialog.style.top       = Math.max(0, startTop  + dy) + 'px';
      }

      function onMouseUp() {
        document.removeEventListener('mousemove', onMouseMove);
        document.removeEventListener('mouseup',   onMouseUp);
      }

      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup',   onMouseUp);
    });
  });

  // Сброс позиции при закрытии
  modalEl.addEventListener('hidden.bs.modal', function () {
    const dialog = modalEl.querySelector('.modal-dialog');
    if (dialog) {
      dialog.style.position = '';
      dialog.style.margin   = '';
      dialog.style.left     = '';
      dialog.style.top      = '';
    }
    const content = modalEl.querySelector('.modal-content');
    if (content) {
      content.style.width  = '';
      content.style.height = '';
    }
  });
})();

// ── Expand/collapse individual pre blocks ────────────────────
function toggleExpand(btn) {
  const label = btn.closest('.detail-label');
  const pre   = label.nextElementSibling;
  const icon  = btn.querySelector('i');

  if (pre.dataset.expanded === 'true') {
    pre.style.height    = '';
    pre.dataset.expanded = 'false';
    icon.className      = 'bi bi-arrows-expand';
    btn.title           = uiText('i18nExpand', 'Expand');
  } else {
    pre.style.height    = '480px';
    pre.dataset.expanded = 'true';
    icon.className      = 'bi bi-arrows-collapse';
    btn.title           = uiText('i18nCollapse', 'Collapse');
  }
}
// ── Fullscreen toggle ────────────────────────────────────────
(function initFullscreen() {
  const btn     = document.getElementById('btnExpandModal');
  const content = document.getElementById('detailModalContent');
  const dialog  = document.getElementById('detailModalDialog');
  const icon    = btn ? btn.querySelector('i') : null;
  if (!btn || !content || !dialog) return;

  let isFullscreen = false;
  let savedStyles  = {};

  btn.addEventListener('click', function () {
    if (!isFullscreen) {
      savedStyles = {
        width:    content.style.width,
        height:   content.style.height,
        position: dialog.style.position,
        margin:   dialog.style.margin,
        left:     dialog.style.left,
        top:      dialog.style.top,
      };

      dialog.style.position = 'fixed';
      dialog.style.margin   = '0';
      dialog.style.left     = '0';
      dialog.style.top      = '0';
      content.style.width   = '100vw';
      content.style.height  = '100vh';
      content.style.maxWidth  = '100vw';
      content.style.maxHeight = '100vh';
      content.style.borderRadius = '0';

      icon.className = 'bi bi-fullscreen-exit';
      btn.title      = uiText('i18nExitFullscreen', 'Exit fullscreen');
      isFullscreen   = true;
    } else {
      dialog.style.position = savedStyles.position;
      dialog.style.margin   = savedStyles.margin;
      dialog.style.left     = savedStyles.left;
      dialog.style.top      = savedStyles.top;
      content.style.width      = savedStyles.width;
      content.style.height     = savedStyles.height;
      content.style.maxWidth   = '';
      content.style.maxHeight  = '';
      content.style.borderRadius = '';

      icon.className = 'bi bi-fullscreen';
      btn.title      = uiText('i18nToggleFullscreen', 'Toggle fullscreen');
      isFullscreen   = false;
    }
  });

  document.getElementById('requestDetailModal')
      .addEventListener('hidden.bs.modal', function () {
        isFullscreen             = false;
        icon.className           = 'bi bi-fullscreen';
        btn.title                = uiText('i18nToggleFullscreen', 'Toggle fullscreen');
        content.style.width      = '';
        content.style.height     = '';
        content.style.maxWidth   = '';
        content.style.maxHeight  = '';
        content.style.borderRadius = '';
      });
})();
