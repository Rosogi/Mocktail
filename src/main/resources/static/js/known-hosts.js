/* ============================================================
   known-hosts.js - Inline settings table helpers
   ============================================================ */

(function () {
  function bindRow(row) {
    if (!row || row.dataset.knownHostBound === 'true') return;
    row.dataset.knownHostBound = 'true';
    row.querySelector('[data-known-host-remove-row]')?.addEventListener('click', () => {
      row.remove();
      refreshEmptyState();
    });
  }

  function refreshEmptyState() {
    const table = document.querySelector('[data-known-host-table]');
    if (!table) return;
    const body = table.querySelector('tbody');
    const existingEmpty = body.querySelector('[data-known-host-empty-row]');
    const rows = body.querySelectorAll('[data-known-host-row]');
    if (rows.length > 0) {
      existingEmpty?.remove();
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    const table = document.querySelector('[data-known-host-table]');
    const template = document.querySelector('[data-known-host-row-template]');
    const addButton = document.querySelector('[data-known-host-add-row]');
    if (!table || !template || !addButton) return;

    table.querySelectorAll('[data-known-host-row]').forEach(bindRow);
    addButton.addEventListener('click', () => {
      table.querySelector('[data-known-host-empty-row]')?.remove();
      const row = template.content.firstElementChild.cloneNode(true);
      table.querySelector('tbody').appendChild(row);
      bindRow(row);
      row.querySelector('input')?.focus();
    });
  });
})();

