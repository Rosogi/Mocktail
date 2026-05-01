/* ============================================================
   dashboard.js
   – Connects to STOMP/WebSocket
   – Appends incoming log rows in real time
   – Opens a detail modal on row click
   ============================================================ */

// ── Globals injected from Thymeleaf via data- attributes ────
const dashboardEl = document.getElementById('dashboard-root');
const USERNAME    = dashboardEl.dataset.username;
const I18N        = dashboardEl.dataset;

function uiText(key, fallback) {
  return I18N[key] || fallback;
}

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

// ── Request detail modal ─────────────────────────────────────
const detailModal     = new bootstrap.Modal(document.getElementById('requestDetailModal'));
const modalMethod     = document.getElementById('modal-method');
const modalPath       = document.getElementById('modal-path');
const modalTime       = document.getElementById('modal-time');
const modalRemote     = document.getElementById('modal-remote');
const modalStatus     = document.getElementById('modal-status');
const modalMock       = document.getElementById('modal-mock');
const modalReqHeaders = document.getElementById('modal-req-headers');
const modalReqBody    = document.getElementById('modal-req-body');
const modalResBody    = document.getElementById('modal-res-body');
const modalContentType= document.getElementById('modal-content-type');
const modalQuery      = document.getElementById('modal-query');

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
      remoteAddr:  row.dataset.remote,
      status:      row.dataset.status,
      matchedMock: row.dataset.mock,
      queryParams: row.dataset.query,
      contentType: row.dataset.ct,
      requestHeaders: tryPrettyJson(row.dataset.reqHeaders) || row.dataset.reqHeaders,
      requestBody: row.dataset.reqBody,
      responseBody: row.dataset.resBody,
    });
  } else {
    fillModal(data);
  }
  detailModal.show();
}

function fillModal(d) {
  modalMethod.textContent      = d.method || '—';
  modalMethod.className        = 'method-badge ' + methodClass(d.method);
  modalPath.textContent        = d.path || '—';
  modalTime.textContent        = d.time || '—';
  modalRemote.textContent      = d.remoteAddr || '—';
  modalStatus.textContent      = d.status || '—';
  modalStatus.className        = 'badge ' + statusClass(parseInt(d.status));
  modalMock.textContent        = d.matchedMock || '— ' + uiText('i18nNoMatch', 'no match') + ' —';
  modalQuery.textContent       = d.queryParams || '—';
  modalContentType.textContent = d.contentType || '—';
  modalReqHeaders.textContent  = d.requestHeaders || '—';
  modalReqBody.textContent     = tryPrettyJson(d.requestBody) || uiText('i18nEmpty', '(empty)');
  modalResBody.textContent     = tryPrettyJson(d.responseBody) || uiText('i18nEmpty', '(empty)');
}

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
  clearEmptyState();

  // Store for modal
  logStore[log.id] = log;

  const path = log.path + (log.queryParams ? '?' + log.queryParams : '');

  const tr = document.createElement('tr');
  tr.id                      = 'log-' + log.id;
  tr.className               = 'live-row';
  tr.dataset.logId           = log.id;
  tr.dataset.method          = log.method;
  tr.dataset.path            = log.path;
  tr.dataset.time            = log.time;
  tr.dataset.remote          = log.remoteAddr || '';
  tr.dataset.status          = log.status || '';
  tr.dataset.mock            = log.matchedMock || '';
  tr.dataset.query           = log.queryParams || '';
  tr.dataset.ct              = log.contentType || '';
  tr.dataset.reqBody         = log.requestBody || '';
  tr.dataset.resBody         = '';
  tr.style.cursor            = 'pointer';

  tr.innerHTML = `
    <td class="log-time">${escHtml(log.time)}</td>
    <td><span class="method-badge ${methodClass(log.method)}">${escHtml(log.method)}</span></td>
    <td class="log-path" title="${escHtml(path)}">${escHtml(path)}</td>
    <td><span class="badge ${statusClass(log.status)}">${log.status ?? '?'}</span></td>
    <td class="log-mock-name">${escHtml(log.matchedMock) || '<span class="text-muted fst-italic">' + escHtml(uiText('i18nNoMatch', 'no match')) + '</span>'}</td>
    <td class="log-remote">${escHtml(log.remoteAddr) || ''}</td>`;

  tr.addEventListener('click', () => openDetail(log.id));

  const tbody = document.getElementById('log-table-body');
  tbody.prepend(tr);

  // Update counter
  totalCount++;
  document.getElementById('log-count').textContent = totalCount;
}

// ── WebSocket / STOMP ────────────────────────────────────────
const liveBadge = document.getElementById('live-badge');

const socket = new SockJS('/ws');
const stomp  = Stomp.over(socket);
stomp.debug  = null; // silence console noise

stomp.connect({}, function onConnected() {
  liveBadge.className   = 'badge bg-success';
  liveBadge.textContent = uiText('i18nLive', '● LIVE');

  stomp.subscribe('/topic/logs/' + USERNAME, function(msg) {
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
