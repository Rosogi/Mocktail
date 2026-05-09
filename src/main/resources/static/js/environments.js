/* ============================================================
   environments.js — Environments table helpers
   ============================================================ */

(function () {
  function lower(value) {
    return (value || '').toString().toLowerCase();
  }

  function setButtonState(button, hidden) {
    const icon = button.querySelector('i');
    const label = hidden ? button.dataset.showLabel : button.dataset.hideLabel;
    if (icon) {
      icon.className = hidden ? 'bi bi-eye' : 'bi bi-eye-slash';
    }
    if (label) {
      button.title = label;
      button.setAttribute('aria-label', label);
    }
  }

  function setRowHidden(row, hidden) {
    row.querySelectorAll('.env-value-input').forEach(input => {
      input.type = hidden ? 'password' : 'text';
    });
    row.dataset.envHidden = hidden ? 'true' : 'false';
    const hiddenInput = row.querySelector('[data-env-hidden-input]');
    if (hiddenInput) {
      hiddenInput.value = hidden ? 'true' : 'false';
    }
    const button = row.querySelector('[data-env-hide-toggle]');
    if (button) {
      setButtonState(button, hidden);
    }
  }

  function sampleValues() {
    return {
      address: 'http://localhost',
      port: ':9000',
      companyId: 'rosogisoft',
      callbackPath: '/webhooks/mocktail'
    };
  }

  function valuesFromTable(table) {
    const values = sampleValues();
    table.querySelectorAll('[data-env-value-row]').forEach(row => {
      const key = row.querySelector('td:first-child input')?.value?.trim();
      const raw = row.querySelector('[data-env-raw-value]')?.value || '';
      if (key) {
        values[key] = raw;
      }
    });
    return values;
  }

  function resolveValue(value, values) {
    let resolved = value || '';
    for (let depth = 0; depth < 10; depth += 1) {
      const next = resolved.replace(/\{\{\s*(?:env\.|global\.)?([a-zA-Z0-9_.-]+)\s*}}/g, function (match, key) {
        return Object.prototype.hasOwnProperty.call(values, key) ? values[key] : match;
      });
      if (next === resolved) {
        break;
      }
      resolved = next;
    }
    return resolved;
  }

  function updateResolvedPreviews(table) {
    const values = valuesFromTable(table);
    table.querySelectorAll('[data-env-value-row]').forEach(row => {
      const raw = row.querySelector('[data-env-raw-value]');
      const resolved = row.querySelector('[data-env-resolved-value]');
      if (raw && resolved) {
        resolved.value = resolveValue(raw.value, values);
      }
    });
  }

  function bindRow(row) {
    if (row.dataset.envBound === 'true') return;
    row.dataset.envBound = 'true';

    const table = row.closest('[data-env-variable-table]');
    const hideButton = row.querySelector('[data-env-hide-toggle]');
    const removeButton = row.querySelector('[data-env-remove-row]');

    hideButton?.addEventListener('click', () => {
      setRowHidden(row, row.dataset.envHidden !== 'true');
    });

    removeButton?.addEventListener('click', () => {
      row.remove();
      if (table) {
        updateResolvedPreviews(table);
      }
    });

    row.querySelectorAll('input').forEach(input => {
      input.addEventListener('input', () => {
        if (table) {
          updateResolvedPreviews(table);
        }
      });
    });

    setRowHidden(row, row.dataset.envHidden === 'true');
  }

  function initVariableTables() {
    document.querySelectorAll('[data-env-variable-table]').forEach(table => {
      table.querySelectorAll('[data-env-value-row]').forEach(bindRow);
      updateResolvedPreviews(table);
    });

    document.querySelectorAll('[data-env-add-row]').forEach(button => {
      button.addEventListener('click', () => {
        const table = document.querySelector('[data-env-variable-table]');
        const template = document.querySelector('[data-env-row-template]');
        if (!table || !template) return;
        const row = template.content.firstElementChild.cloneNode(true);
        table.querySelector('tbody').appendChild(row);
        bindRow(row);
        updateResolvedPreviews(table);
        row.querySelector('input')?.focus();
      });
    });
  }

  function initValueVisibility() {
    document.querySelectorAll('[data-env-hide-all]').forEach(button => {
      button.addEventListener('click', () => {
        const rows = Array.from(document.querySelectorAll('[data-env-value-row]'));
        const shouldHide = rows.some(row => row.dataset.envHidden !== 'true');
        rows.forEach(row => setRowHidden(row, shouldHide));

        const icon = button.querySelector('i');
        const label = button.querySelector('span');
        if (icon) {
          icon.className = shouldHide ? 'bi bi-eye me-1' : 'bi bi-eye-slash me-1';
        }
        if (label) {
          label.textContent = shouldHide
              ? (button.dataset.showAllLabel || 'Show all')
              : (button.dataset.hideAllLabel || 'Hide all');
        }
      });
    });
  }

  function initPackageFilters() {
    const container = document.querySelector('[data-env-filter-scope="packages"]');
    if (!container) return;

    const cards = Array.from(document.querySelectorAll('.env-package-card-wrap'));
    const empty = document.getElementById('environmentsEmptyFiltered');
    const filters = {
      search: container.querySelector('[data-env-filter="search"]'),
      state: container.querySelector('[data-env-filter="state"]')
    };

    function apply() {
      const search = lower(filters.search?.value);
      const state = filters.state?.value || '';
      let visible = 0;

      cards.forEach(card => {
        const matchesSearch = !search ||
            lower(card.dataset.name).includes(search) ||
            lower(card.dataset.description).includes(search);
        const matchesState = !state || card.dataset.state === state;
        const isVisible = matchesSearch && matchesState;
        card.classList.toggle('d-none', !isVisible);
        if (isVisible) visible++;
      });

      if (empty) {
        empty.classList.toggle('d-none', visible !== 0 || cards.length === 0);
      }
    }

    Object.values(filters).forEach(control => {
      if (!control) return;
      control.addEventListener('input', apply);
      control.addEventListener('change', apply);
    });

    container.querySelector('[data-env-filter-reset]')?.addEventListener('click', () => {
      Object.values(filters).forEach(control => {
        if (!control) return;
        if (control.tagName === 'SELECT') {
          control.selectedIndex = 0;
        } else {
          control.value = '';
        }
      });
      apply();
    });

    apply();
  }

  function initDuplicateModal() {
    const sourceInput = document.querySelector('[data-env-duplicate-source]');
    const sourceIdInput = document.querySelector('[data-env-duplicate-source-id]');
    const nameInput = document.querySelector('[data-env-duplicate-name]');
    if (!sourceInput || !nameInput) return;

    document.querySelectorAll('[data-env-duplicate]').forEach(button => {
      button.addEventListener('click', () => {
        const source = button.dataset.envName || 'Environment';
        const sourceId = button.dataset.envId || '';
        sourceInput.value = source;
        if (sourceIdInput) {
          sourceIdInput.value = sourceId;
        }
        nameInput.value = `${source} copy`;
      });
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    initVariableTables();
    initPackageFilters();
    initDuplicateModal();
    initValueVisibility();
  });
})();
