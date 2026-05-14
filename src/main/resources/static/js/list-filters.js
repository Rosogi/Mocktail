function lower(value) {
  return (value || '').toString().toLowerCase();
}

function confirmDelete(form) {
  const name = form.dataset.name || '';
  return confirm(form.dataset.confirmMessage || `Delete ${name}?`);
}

function bindReset(container, onChange) {
  const reset = container.querySelector('[data-filter-reset]');
  if (!reset) return;
  reset.addEventListener('click', () => {
    container.querySelectorAll('[data-filter]').forEach(control => {
      if (control.tagName === 'SELECT') {
        control.selectedIndex = 0;
      } else {
        control.value = '';
      }
    });
    container.dispatchEvent(new CustomEvent('mocktail:filters-reset'));
    onChange();
  });
}

function bindMocksFilters() {
  const container = document.querySelector('[data-filter-scope="mocks"]');
  if (!container) return;

  const rows = Array.from(document.querySelectorAll('.mock-row'));
  const empty = document.getElementById('mocksEmptyFiltered');
  const filters = {
    search: container.querySelector('[data-filter="search"]'),
    method: container.querySelector('[data-filter="method"]'),
    collection: container.querySelector('[data-filter="collection"]'),
    active: container.querySelector('[data-filter="active"]'),
    type: container.querySelector('[data-filter="type"]'),
    priorityExact: container.querySelector('[data-filter="priority-exact"]'),
    priorityMin: container.querySelector('[data-filter="priority-min"]'),
    priorityMax: container.querySelector('[data-filter="priority-max"]'),
    status: container.querySelector('[data-filter="status"]'),
  };
  const priorityFilter = container.querySelector('[data-priority-filter]');
  const priorityLabel = priorityFilter?.querySelector('[data-priority-label]');
  const priorityToggle = priorityFilter?.querySelector('[data-bs-toggle="dropdown"]');
  const priorityChoices = priorityFilter
      ? Array.from(priorityFilter.querySelectorAll('[data-priority-choice]'))
      : [];
  let priorityMode = '';

  function priorityMatches(exactValue, minValue, maxValue, priority) {
    const number = parseInt(priority || '0', 10);
    const exact = exactValue === '' ? null : parseInt(exactValue, 10);
    const min = minValue === '' ? null : parseInt(minValue, 10);
    const max = maxValue === '' ? null : parseInt(maxValue, 10);
    if (Number.isNaN(number)) return false;
    if (exact !== null && !Number.isNaN(exact) && number !== exact) return false;
    if (min !== null && !Number.isNaN(min) && number < min) return false;
    if (max !== null && !Number.isNaN(max) && number > max) return false;
    return true;
  }

  function apply() {
    const search = lower(filters.search.value);
    let visible = 0;

    rows.forEach(row => {
      const matchesSearch = !search ||
          lower(row.dataset.name).includes(search) ||
          lower(row.dataset.path).includes(search) ||
          lower(row.dataset.collectionName).includes(search);
      const matchesMethod = !filters.method.value || row.dataset.method === filters.method.value;
      const matchesCollection = !filters.collection.value ||
          row.dataset.collection === filters.collection.value;
      const matchesActive = !filters.active.value || row.dataset.active === filters.active.value;
      const matchesType = !filters.type.value || row.dataset.type === filters.type.value;
      const matchesPriority = priorityMatches(
          filters.priorityExact.value,
          filters.priorityMin.value,
          filters.priorityMax.value,
          row.dataset.priority);
      const matchesStatus = !filters.status.value || row.dataset.statusGroup === filters.status.value;
      const isVisible = matchesSearch && matchesMethod && matchesCollection &&
          matchesActive && matchesType && matchesPriority && matchesStatus;

      row.classList.toggle('d-none', !isVisible);
      if (isVisible) visible++;
    });

    if (empty) empty.classList.toggle('d-none', visible !== 0 || rows.length === 0);
  }

  function prioritySummary() {
    const exact = filters.priorityExact?.value || '';
    const min = filters.priorityMin?.value || '';
    const max = filters.priorityMax?.value || '';
    const anyLabel = priorityFilter?.dataset.priorityAnyLabel || 'Any priority';
    const prefix = priorityFilter?.dataset.priorityPrefix || 'Priority';
    const rangeLabel = priorityFilter?.dataset.priorityRangeLabel || 'Priority range';

    if (exact) {
      return `${prefix}: ${exact}`;
    }
    if (priorityMode === 'custom' || min || max) {
      if (min || max) {
        return `${rangeLabel}: ${min || '0'} - ${max || '10'}`;
      }
      return rangeLabel;
    }
    return anyLabel;
  }

  function updatePriorityLabel() {
    if (priorityLabel) {
      priorityLabel.textContent = prioritySummary();
    }
  }

  function hidePriorityMenu() {
    if (!priorityToggle || !window.bootstrap?.Dropdown) return;
    bootstrap.Dropdown.getOrCreateInstance(priorityToggle).hide();
  }

  Object.values(filters).forEach(control => control && control.addEventListener('input', apply));
  Object.values(filters).forEach(control => control && control.addEventListener('change', apply));
  priorityChoices.forEach(choice => {
    choice.addEventListener('click', () => {
      const value = choice.dataset.priorityChoice || '';
      if (value === 'custom') {
        priorityMode = 'custom';
        filters.priorityExact.value = '';
        updatePriorityLabel();
        filters.priorityMin?.focus();
        return;
      }
      priorityMode = value ? 'exact' : '';
      filters.priorityExact.value = value;
      filters.priorityMin.value = '';
      filters.priorityMax.value = '';
      updatePriorityLabel();
      apply();
      hidePriorityMenu();
    });
  });
  [filters.priorityMin, filters.priorityMax].forEach(control => {
    control?.addEventListener('input', () => {
      priorityMode = 'custom';
      filters.priorityExact.value = '';
      updatePriorityLabel();
      apply();
    });
  });
  container.addEventListener('mocktail:filters-reset', () => {
    priorityMode = '';
    updatePriorityLabel();
  });
  bindReset(container, apply);
  updatePriorityLabel();
  apply();
}

function bindCollectionsFilters() {
  const container = document.querySelector('[data-filter-scope="collections"]');
  if (!container) return;

  const grid = document.getElementById('collectionsGrid');
  const cards = Array.from(document.querySelectorAll('.collection-card-wrap'));
  const empty = document.getElementById('collectionsEmptyFiltered');
  const filters = {
    search: container.querySelector('[data-filter="search"]'),
    type: container.querySelector('[data-filter="type"]'),
    state: container.querySelector('[data-filter="state"]'),
    sort: container.querySelector('[data-filter="sort"]'),
  };

  function typeMatches(value, card) {
    if (!value) return true;
    if (value === 'updates') return card.dataset.update === 'true';
    return card.dataset.type === value;
  }

  function sortCards() {
    if (!grid) return;
    const sort = filters.sort.value || 'name';
    const sorted = [...cards].sort((a, b) => {
      if (sort === 'mocks-desc') {
        return parseInt(b.dataset.mocks || '0', 10) - parseInt(a.dataset.mocks || '0', 10);
      }
      if (sort === 'revision-desc') {
        return parseInt(b.dataset.revision || '0', 10) - parseInt(a.dataset.revision || '0', 10);
      }
      return lower(a.dataset.name).localeCompare(lower(b.dataset.name));
    });
    sorted.forEach(card => grid.appendChild(card));
  }

  function apply() {
    const search = lower(filters.search.value);
    let visible = 0;

    sortCards();
    cards.forEach(card => {
      const matchesSearch = !search ||
          lower(card.dataset.name).includes(search) ||
          lower(card.dataset.description).includes(search) ||
          lower(card.dataset.author).includes(search);
      const matchesType = typeMatches(filters.type.value, card);
      const matchesState = !filters.state.value || card.dataset.state === filters.state.value;
      const isVisible = matchesSearch && matchesType && matchesState;

      card.classList.toggle('d-none', !isVisible);
      if (isVisible) visible++;
    });

    if (empty) empty.classList.toggle('d-none', visible !== 0 || cards.length === 0);
  }

  Object.values(filters).forEach(control => control && control.addEventListener('input', apply));
  Object.values(filters).forEach(control => control && control.addEventListener('change', apply));
  bindReset(container, apply);
  apply();
}

document.addEventListener('DOMContentLoaded', () => {
  bindMocksFilters();
  bindCollectionsFilters();
});
