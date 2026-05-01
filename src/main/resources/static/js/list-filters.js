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
    priority: container.querySelector('[data-filter="priority"]'),
    status: container.querySelector('[data-filter="status"]'),
  };

  function priorityMatches(value, priority) {
    if (!value) return true;
    const number = parseInt(priority || '0', 10);
    if (value === '0') return number === 0;
    if (value === '1-9') return number >= 1 && number <= 9;
    if (value === '10+') return number >= 10;
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
      const matchesPriority = priorityMatches(filters.priority.value, row.dataset.priority);
      const matchesStatus = !filters.status.value || row.dataset.statusGroup === filters.status.value;
      const isVisible = matchesSearch && matchesMethod && matchesCollection &&
          matchesActive && matchesType && matchesPriority && matchesStatus;

      row.classList.toggle('d-none', !isVisible);
      if (isVisible) visible++;
    });

    if (empty) empty.classList.toggle('d-none', visible !== 0 || rows.length === 0);
  }

  Object.values(filters).forEach(control => control && control.addEventListener('input', apply));
  Object.values(filters).forEach(control => control && control.addEventListener('change', apply));
  bindReset(container, apply);
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
