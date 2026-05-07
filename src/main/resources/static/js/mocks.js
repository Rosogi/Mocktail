/* ============================================================
   mocks.js — helpers for the mock list and form
   ============================================================ */

function confirmDelete(form) {
  const name = form.dataset.name;
  return confirm(form.dataset.confirmMessage || `Delete ${name}?`);
}

(function () {
  function selectedText(select) {
    return select.options[select.selectedIndex]?.textContent.trim() || '';
  }

  function escapeHtml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
  }

  function initMatchModeToggle(container) {
    const radios = Array.from(container.querySelectorAll('[data-match-mode-radio]'));
    const basicPanel = container.querySelector('[data-match-basic-panel]');
    const advancedPanel = container.querySelector('[data-match-advanced-panel]');

    function syncMode() {
      const selected = radios.find(radio => radio.checked)?.value || 'basic';
      basicPanel.classList.toggle('d-none', selected !== 'basic');
      advancedPanel.classList.toggle('d-none', selected !== 'advanced');
    }

    radios.forEach(radio => radio.addEventListener('change', syncMode));
    syncMode();
  }

  function setInputState(row) {
    const source = row.querySelector('[data-condition-source]').value;
    const xmlMode = row.querySelector('[data-condition-xml-mode]').value;
    const operator = row.querySelector('[data-condition-operator]').value;
    const targetLabel = row.querySelector('[data-condition-target-label]');
    const targetInput = row.querySelector('[data-condition-target]');
    const valueInput = row.querySelector('[data-condition-value]');
    const builder = row.closest('[data-match-builder]');
    const data = builder.dataset || {};

    row.dataset.source = source;
    row.dataset.xmlMode = xmlMode;

    const labelMap = {
      json_body: data.fieldLabelJson,
      xml_body: xmlMode === 'xpath' ? data.fieldLabelXpath : data.fieldLabelXml,
      query_param: data.fieldLabelParam,
      header: data.fieldLabelHeader,
      raw_body: data.fieldLabelRaw
    };
    const placeholderMap = {
      json_body: data.fieldPlaceholderJson,
      xml_body: xmlMode === 'xpath' ? data.fieldPlaceholderXpath : data.fieldPlaceholderXml,
      query_param: data.fieldPlaceholderParam,
      header: data.fieldPlaceholderHeader,
      raw_body: data.fieldPlaceholderRaw
    };

    targetLabel.textContent = labelMap[source] || labelMap.json_body || '';
    targetInput.placeholder = placeholderMap[source] || '';
    valueInput.placeholder = data.valuePlaceholder || '';

    if (operator === 'exists') {
      valueInput.value = '';
      valueInput.disabled = true;
    } else {
      valueInput.disabled = false;
    }
  }

  function readCondition(item) {
    const row = item.querySelector('[data-condition-row]');
    return {
      connector: item.querySelector('[data-condition-connector-select]').value,
      source: row.querySelector('[data-condition-source]').value,
      xmlMode: row.querySelector('[data-condition-xml-mode]').value,
      target: row.querySelector('[data-condition-target]').value,
      operator: row.querySelector('[data-condition-operator]').value,
      value: row.querySelector('[data-condition-value]').value,
      whitespace: row.querySelector('[data-condition-whitespace]').value
    };
  }

  function readGroup(group) {
    return {
      connector: group.querySelector('[data-condition-group-connector-select]').value,
      conditions: Array.from(group.querySelectorAll('[data-condition-item]')).map(readCondition)
    };
  }

  function parseStoredGroups(raw) {
    if (!raw || !raw.trim()) {
      return [];
    }
    try {
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : [];
    } catch (_) {
      return [];
    }
  }

  function writeStoredGroups(builder) {
    const raw = builder.querySelector('[data-match-groups-raw]');
    if (!raw) return;

    const groups = Array.from(builder.querySelectorAll('[data-condition-group]'))
        .map(readGroup)
        .map(group => ({
          connector: group.connector,
          conditions: group.conditions.filter(conditionValuesAreComplete)
        }))
        .filter(group => group.conditions.length > 0);

    raw.value = groups.length ? JSON.stringify(groups) : '';
  }

  function conditionValuesAreComplete(values) {
    if (values.source !== 'raw_body' && !String(values.target || '').trim()) {
      return false;
    }
    return values.operator === 'exists' || String(values.value || '').trim().length > 0;
  }

  function conditionIsComplete(item) {
    const values = readCondition(item);
    return conditionValuesAreComplete(values);
  }

  function describeCondition(item) {
    const row = item.querySelector('[data-condition-row]');
    const values = readCondition(item);
    const source = selectedText(row.querySelector('[data-condition-source]'));
    const xmlMode = selectedText(row.querySelector('[data-condition-xml-mode]'));
    const operator = selectedText(row.querySelector('[data-condition-operator]'));
    const whitespace = selectedText(row.querySelector('[data-condition-whitespace]'));
    const target = values.target.trim();
    const value = values.value.trim();
    const isExists = values.operator === 'exists';

    if (values.source === 'raw_body') {
      return isExists
          ? `${source} ${operator}`
          : `${source} ${operator} "${value}" (${whitespace})`;
    }

    const sourceLabel = values.source === 'xml_body' ? `${source} / ${xmlMode}` : source;
    return isExists
        ? `${sourceLabel}: ${target} ${operator}`
        : `${sourceLabel}: ${target} ${operator} "${value}"`;
  }

  function refreshConditionConnectors(group) {
    const items = Array.from(group.querySelectorAll('[data-condition-item]'));
    items.forEach((item, index) => {
      item.querySelector('[data-condition-connector]').classList.toggle('d-none', index === 0);
    });
  }

  function refreshGroups(builder) {
    const groups = Array.from(builder.querySelectorAll('[data-condition-group]'));
    groups.forEach((group, index) => {
      group.querySelector('[data-condition-group-connector]').classList.toggle('d-none', index === 0);
      group.querySelector('[data-condition-group-number]').textContent = String(index + 1);
      refreshConditionConnectors(group);
    });
  }

  function describeConditionWithConnector(item, index) {
    const connector = selectedText(item.querySelector('[data-condition-connector-select]'));
    const description = describeCondition(item);
    return index === 0 ? description : `${connector} ${description}`;
  }

  function describeGroup(group, index) {
    const builder = group.closest('[data-match-builder]');
    const data = builder.dataset || {};
    const connector = selectedText(group.querySelector('[data-condition-group-connector-select]'));
    const groupNumber = group.querySelector('[data-condition-group-number]').textContent;
    const completeItems = Array.from(group.querySelectorAll('[data-condition-item]')).filter(conditionIsComplete);
    const conditions = completeItems
        .map((item, conditionIndex) => describeConditionWithConnector(item, conditionIndex))
        .join('; ');
    const label = `${data.groupLabel || 'Group'} ${groupNumber}: ${conditions}`;
    return index === 0 ? label : `${connector} ${label}`;
  }

  function updateSummary(builder) {
    const summary = builder.querySelector('[data-condition-summary]');
    const completeGroups = Array.from(builder.querySelectorAll('[data-condition-group]'))
        .filter(group => Array.from(group.querySelectorAll('[data-condition-item]')).some(conditionIsComplete));
    const data = builder.dataset || {};

    refreshGroups(builder);
    writeStoredGroups(builder);

    if (completeGroups.length === 0) {
      summary.innerHTML = `<span class="text-muted">${escapeHtml(data.conditionEmpty || 'No additional conditions')}</span>`;
      return;
    }

    const items = completeGroups
        .map((group, index) => `<li>${escapeHtml(describeGroup(group, index))}</li>`)
        .join('');
    summary.innerHTML = `
      <div class="condition-summary-title">${escapeHtml(data.conditionSummaryTitle || 'Mock will match when:')}</div>
      <ul>${items}</ul>
    `;
  }

  function addCondition(group, builder, initialValues, beforeNode) {
    const list = group.querySelector('[data-condition-list]');
    const template = builder.querySelector('[data-condition-template]');
    const item = template.content.firstElementChild.cloneNode(true);
    const row = item.querySelector('[data-condition-row]');

    row.querySelector('[data-condition-source]').value = initialValues?.source || 'json_body';
    row.querySelector('[data-condition-xml-mode]').value = initialValues?.xmlMode || 'tag';
    item.querySelector('[data-condition-connector-select]').value = initialValues?.connector || 'and';
    row.querySelector('[data-condition-target]').value = initialValues?.target || '';
    row.querySelector('[data-condition-operator]').value = initialValues?.operator || 'equals';
    row.querySelector('[data-condition-value]').value = initialValues?.value || '';
    row.querySelector('[data-condition-whitespace]').value = initialValues?.whitespace || 'exact';

    item.querySelectorAll('select, input').forEach(control => {
      control.addEventListener('input', () => {
        setInputState(row);
        updateSummary(builder);
      });
      control.addEventListener('change', () => {
        setInputState(row);
        updateSummary(builder);
      });
    });

    row.querySelector('[data-condition-copy]').addEventListener('click', () => {
      addCondition(group, builder, readCondition(item), item.nextSibling);
    });

    row.querySelector('[data-condition-remove]').addEventListener('click', () => {
      item.remove();
      if (!group.querySelector('[data-condition-item]')) {
        addCondition(group, builder);
      }
      updateSummary(builder);
    });

    list.insertBefore(item, beforeNode || null);
    setInputState(row);
    updateSummary(builder);
  }

  function addGroup(builder, initialValues, beforeNode) {
    const list = builder.querySelector('[data-condition-group-list]');
    const template = builder.querySelector('[data-condition-group-template]');
    const group = template.content.firstElementChild.cloneNode(true);

    group.querySelector('[data-condition-group-connector-select]').value = initialValues?.connector || 'or';
    group.querySelector('[data-condition-add]').addEventListener('click', () => {
      addCondition(group, builder);
    });
    group.querySelector('[data-condition-group-copy]').addEventListener('click', () => {
      addGroup(builder, readGroup(group), group.nextSibling);
    });
    group.querySelector('[data-condition-group-remove]').addEventListener('click', () => {
      group.remove();
      if (!builder.querySelector('[data-condition-group]')) {
        addGroup(builder);
      }
      updateSummary(builder);
    });
    group.querySelector('[data-condition-group-connector-select]').addEventListener('change', () => {
      updateSummary(builder);
    });

    list.insertBefore(group, beforeNode || null);
    const conditions = initialValues?.conditions?.length ? initialValues.conditions : [{}];
    conditions.forEach(condition => addCondition(group, builder, condition));
    updateSummary(builder);
  }

  function parseHeaderLines(raw) {
    if (!raw || !raw.trim()) {
      return [];
    }
    return raw.split(/\r?\n/)
        .map(line => {
          const index = line.indexOf(':');
          if (index <= 0) {
            return null;
          }
          return {
            name: line.slice(0, index).trim(),
            value: line.slice(index + 1).trim()
          };
        })
        .filter(header => header && header.name);
  }

  function updateRawHeaders(builder) {
    const raw = builder.querySelector('[data-response-headers-raw]');
    const lines = Array.from(builder.querySelectorAll('[data-response-header-row]'))
        .map(row => {
          const name = row.querySelector('[data-response-header-name]').value.trim();
          const value = row.querySelector('[data-response-header-value]').value.trim();
          return name ? `${name}: ${value}` : '';
        })
        .filter(Boolean);
    raw.value = lines.join('\n');
  }

  function addResponseHeader(builder, initialValues) {
    const list = builder.querySelector('[data-response-header-list]');
    const template = builder.querySelector('[data-response-header-template]');
    const row = template.content.firstElementChild.cloneNode(true);

    row.querySelector('[data-response-header-name]').value = initialValues?.name || '';
    row.querySelector('[data-response-header-value]').value = initialValues?.value || '';

    row.querySelectorAll('input').forEach(input => {
      input.addEventListener('input', () => updateRawHeaders(builder));
    });

    row.querySelector('[data-response-header-remove]').addEventListener('click', () => {
      row.remove();
      updateRawHeaders(builder);
    });

    list.appendChild(row);
    updateRawHeaders(builder);
  }

  function initResponseHeadersBuilder(builder) {
    const raw = builder.querySelector('[data-response-headers-raw]');
    const addButton = builder.querySelector('[data-response-header-add]');
    const headers = parseHeaderLines(raw.value);

    addButton.addEventListener('click', () => addResponseHeader(builder));

    if (headers.length) {
      headers.forEach(header => addResponseHeader(builder, header));
    } else {
      addResponseHeader(builder);
    }
  }

  function initMatchBuilder(builder) {
    const addButton = builder.querySelector('[data-condition-group-add]');
    const legacyBodyContains = document.querySelector('[name="requestBodyContains"]')?.value?.trim();
    const storedGroups = parseStoredGroups(builder.querySelector('[data-match-groups-raw]')?.value);

    addButton.addEventListener('click', () => addGroup(builder));

    if (storedGroups.length) {
      storedGroups.forEach(group => addGroup(builder, group));
    } else if (legacyBodyContains) {
      addGroup(builder, {
        conditions: [{
          source: 'raw_body',
          operator: 'contains',
          value: legacyBodyContains
        }]
      });
    } else {
      addGroup(builder);
    }

    builder.closest('form')?.addEventListener('submit', () => writeStoredGroups(builder));
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-match-mode]').forEach(initMatchModeToggle);
    document.querySelectorAll('[data-match-builder]').forEach(initMatchBuilder);
    document.querySelectorAll('[data-response-headers-builder]').forEach(initResponseHeadersBuilder);
  });
})();
