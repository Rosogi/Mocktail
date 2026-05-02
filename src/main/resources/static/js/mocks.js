/* ============================================================
   mocks.js — helpers for the mock list and form
   ============================================================ */

function confirmDelete(form) {
  const name = form.dataset.name;
  return confirm(form.dataset.confirmMessage || `Delete ${name}?`);
}
