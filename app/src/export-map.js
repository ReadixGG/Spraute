function formatBytes(n) {
  if (!n || n < 0) return '';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

function isCoveredBySelection(rel, selectedSet) {
  if (selectedSet.has(rel)) return true;
  const parts = rel.split('/');
  for (let i = 1; i < parts.length; i++) {
    const anc = parts.slice(0, i).join('/');
    if (selectedSet.has(anc)) return true;
  }
  return false;
}

function pruneSelectionOnAdd(rel, selectedSet) {
  selectedSet.add(rel);
  for (const p of [...selectedSet]) {
    if (p !== rel && (p.startsWith(rel + '/') || rel.startsWith(p + '/'))) {
      if (p.startsWith(rel + '/')) selectedSet.delete(p);
    }
  }
}

function buildExportTreeRow({ name, rel, isDir, level, checked, disabled, inputType, groupName, icon, iconColor }) {
  const row = document.createElement('div');
  row.className = 'export-tree-row flex items-center gap-2 py-1 hover:bg-white/5 rounded-md cursor-pointer text-sm text-on-surface';
  row.style.paddingLeft = `${level * 16 + 8}px`;
  row.dataset.rel = rel;
  row.dataset.isDir = isDir ? '1' : '0';

  const input = document.createElement('input');
  input.type = inputType;
  input.className = 'export-tree-input shrink-0 accent-primary';
  input.name = groupName || '';
  input.value = rel;
  input.checked = checked;
  input.disabled = disabled;
  input.addEventListener('click', (e) => e.stopPropagation());

  const iconEl = document.createElement('span');
  iconEl.className = `material-symbols-outlined text-[16px] ${iconColor || 'text-on-variant'}`;
  iconEl.textContent = icon || (isDir ? 'folder' : 'draft');

  const label = document.createElement('span');
  label.className = 'truncate flex-1';
  label.textContent = name;

  row.appendChild(input);
  row.appendChild(iconEl);
  row.appendChild(label);
  return row;
}

function createCheckboxTree(container, { area, selectedSet, onChange }) {
  const expanded = new Set();

  async function renderLevel(relPath, parentEl, level) {
    let items;
    try {
      items = await window.spraute.listExportDir(area, relPath);
    } catch (err) {
      parentEl.innerHTML = `<div class="text-xs text-red-400 px-3 py-2">${err.message || 'Ошибка загрузки'}</div>`;
      return;
    }

    if (!items.length && level === 0) {
      parentEl.innerHTML = '<div class="text-xs text-on-variant italic px-3 py-4">Пусто</div>';
      return;
    }

    for (const item of items) {
      const wrapper = document.createElement('div');
      wrapper.className = 'export-tree-node';

      const covered = isCoveredBySelection(item.rel, selectedSet);
      const row = buildExportTreeRow({
        name: item.name,
        rel: item.rel,
        isDir: item.isDir,
        level,
        checked: covered,
        disabled: covered && !selectedSet.has(item.rel) && [...selectedSet].some((p) => item.rel.startsWith(p + '/')),
        inputType: 'checkbox',
        icon: item.isDir ? 'folder' : 'description',
        iconColor: item.isDir ? 'text-secondary' : 'text-primary',
      });

      const input = row.querySelector('input');
      input.addEventListener('change', () => {
        if (input.checked) {
          pruneSelectionOnAdd(item.rel, selectedSet);
        } else {
          selectedSet.delete(item.rel);
        }
        onChange?.();
        refreshChecks(container, selectedSet);
      });

      const childrenEl = document.createElement('div');
      childrenEl.className = 'export-tree-children hidden';

      if (item.isDir) {
        row.addEventListener('click', async (e) => {
          if (e.target === input) return;
          const isOpen = !childrenEl.classList.contains('hidden');
          if (isOpen) {
            childrenEl.classList.add('hidden');
            expanded.delete(item.rel);
          } else {
            childrenEl.classList.remove('hidden');
            expanded.add(item.rel);
            if (!childrenEl.dataset.loaded) {
              childrenEl.dataset.loaded = '1';
              await renderLevel(item.rel, childrenEl, level + 1);
            }
          }
        });
      }

      wrapper.appendChild(row);
      wrapper.appendChild(childrenEl);
      parentEl.appendChild(wrapper);
    }
  }

  container.innerHTML = '';
  return renderLevel('', container, 0);
}

function refreshChecks(container, selectedSet) {
  container.querySelectorAll('.export-tree-row').forEach((row) => {
    const rel = row.dataset.rel;
    const input = row.querySelector('input');
    if (!input || input.type !== 'checkbox') return;
    const covered = isCoveredBySelection(rel, selectedSet);
    input.checked = covered;
    input.disabled = covered && !selectedSet.has(rel) && [...selectedSet].some((p) => rel.startsWith(p + '/'));
  });
}

export function initExportMap({ appAlert, setStatus }) {
  const menuBtn = document.getElementById('menu-export-map');
  const modal = document.getElementById('export-map-modal');
  const modalBox = document.getElementById('export-map-modal-box');
  const btnClose = document.getElementById('btn-close-export-map');
  const btnCloseFooter = document.getElementById('btn-close-export-map-footer');
  const btnExport = document.getElementById('btn-run-export-map');
  const nameInput = document.getElementById('export-map-name');
  const modsTree = document.getElementById('export-mods-tree');
  const sprauteTree = document.getElementById('export-spraute-tree');
  const worldsTree = document.getElementById('export-worlds-tree');
  const includeWorldCb = document.getElementById('export-include-world');
  const worldsSection = document.getElementById('export-worlds-section');
  const exportStatus = document.getElementById('export-map-status');

  if (!menuBtn || !modal) return;

  let selectedMod = null;
  const selectedSpraute = new Set();
  let selectedWorld = null;
  let exporting = false;

  function openModal() {
    modal.classList.remove('hidden');
    setTimeout(() => modalBox?.classList.remove('scale-95', 'opacity-0'), 10);
    loadExportData();
  }

  function closeModal() {
    modalBox?.classList.add('scale-95', 'opacity-0');
    setTimeout(() => modal.classList.add('hidden'), 200);
  }

  async function loadModsTree() {
    if (!modsTree) return;
    modsTree.innerHTML = '<div class="text-xs text-on-variant animate-pulse px-3 py-2">Загрузка...</div>';
    selectedMod = null;

    try {
      const mods = await window.spraute.listExportMods();
      modsTree.innerHTML = '';

      const rootRow = document.createElement('div');
      rootRow.className = 'flex items-center gap-2 py-1.5 px-2 text-xs font-bold text-on-variant uppercase tracking-wider';
      rootRow.innerHTML = '<span class="material-symbols-outlined text-[16px]">folder</span><span>mods/</span>';
      modsTree.appendChild(rootRow);

      if (!mods.length) {
        const empty = document.createElement('div');
        empty.className = 'text-xs text-on-variant italic px-6 py-2';
        empty.textContent = 'JAR-файлы Spraute Engine не найдены в папке mods';
        modsTree.appendChild(empty);
        return;
      }

      mods.forEach((mod, idx) => {
        const row = buildExportTreeRow({
          name: `${mod.name} (${formatBytes(mod.size)})`,
          rel: mod.name,
          isDir: false,
          level: 1,
          checked: idx === 0,
          disabled: false,
          inputType: 'radio',
          groupName: 'export-mod',
          icon: 'inventory_2',
          iconColor: 'text-emerald-400',
        });
        const input = row.querySelector('input');
        if (idx === 0) {
          selectedMod = mod.name;
          input.checked = true;
        }
        input.addEventListener('change', () => {
          if (input.checked) selectedMod = mod.name;
        });
        modsTree.appendChild(row);
      });
    } catch (err) {
      modsTree.innerHTML = `<div class="text-xs text-red-400 px-3 py-2">${err.message}</div>`;
    }
  }

  async function loadSprauteTree() {
    if (!sprauteTree) return;
    selectedSpraute.clear();
    sprauteTree.innerHTML = '<div class="text-xs text-on-variant animate-pulse px-3 py-2">Загрузка...</div>';
    try {
      sprauteTree.innerHTML = '';
      const rootRow = document.createElement('div');
      rootRow.className = 'flex items-center gap-2 py-1.5 px-2 text-xs font-bold text-on-variant uppercase tracking-wider';
      rootRow.innerHTML = '<span class="material-symbols-outlined text-[16px]">folder</span><span>spraute_engine/</span>';
      sprauteTree.appendChild(rootRow);

      const inner = document.createElement('div');
      sprauteTree.appendChild(inner);
      await createCheckboxTree(inner, {
        area: 'spraute',
        selectedSet: selectedSpraute,
        onChange: updateExportStatus,
      });
    } catch (err) {
      sprauteTree.innerHTML = `<div class="text-xs text-red-400 px-3 py-2">${err.message}</div>`;
    }
    updateExportStatus();
  }

  async function loadWorldsTree() {
    if (!worldsTree) return;
    selectedWorld = null;
    worldsTree.innerHTML = '<div class="text-xs text-on-variant animate-pulse px-3 py-2">Загрузка...</div>';

    try {
      const worlds = await window.spraute.listExportDir('saves', '');
      worldsTree.innerHTML = '';

      const rootRow = document.createElement('div');
      rootRow.className = 'flex items-center gap-2 py-1.5 px-2 text-xs font-bold text-on-variant uppercase tracking-wider';
      rootRow.innerHTML = '<span class="material-symbols-outlined text-[16px]">folder</span><span>saves/</span>';
      worldsTree.appendChild(rootRow);

      const worldItems = worlds.filter((w) => w.isDir && w.isWorld);
      if (!worldItems.length) {
        const empty = document.createElement('div');
        empty.className = 'text-xs text-on-variant italic px-6 py-2';
        empty.textContent = 'Миры не найдены';
        worldsTree.appendChild(empty);
        return;
      }

      worldItems.forEach((world, idx) => {
        const row = buildExportTreeRow({
          name: world.name,
          rel: world.rel,
          isDir: true,
          level: 1,
          checked: idx === 0,
          disabled: false,
          inputType: 'radio',
          groupName: 'export-world',
          icon: 'public',
          iconColor: 'text-sky-400',
        });
        const input = row.querySelector('input');
        if (idx === 0) {
          selectedWorld = world.name;
          input.checked = true;
        }
        input.addEventListener('change', () => {
          if (input.checked) selectedWorld = world.name;
        });
        worldsTree.appendChild(row);
      });
    } catch (err) {
      worldsTree.innerHTML = `<div class="text-xs text-red-400 px-3 py-2">${err.message}</div>`;
    }
  }

  function updateExportStatus() {
    if (!exportStatus) return;
    const parts = [];
    if (selectedMod) parts.push('мод');
    if (selectedSpraute.size) parts.push(`spraute_engine (${selectedSpraute.size})`);
    if (includeWorldCb?.checked && selectedWorld) parts.push('мир');
    exportStatus.textContent = parts.length
      ? `В архив: ${parts.join(', ')}`
      : 'Выберите содержимое для архива';
  }

  async function loadExportData() {
    if (exportStatus) exportStatus.textContent = 'Загрузка...';
    nameInput.value = nameInput.value.trim() || 'Моя карта';
    await Promise.all([loadModsTree(), loadSprauteTree(), loadWorldsTree()]);
    updateExportStatus();
  }

  includeWorldCb?.addEventListener('change', () => {
    if (worldsSection) {
      worldsSection.classList.toggle('opacity-50', !includeWorldCb.checked);
      worldsSection.classList.toggle('pointer-events-none', !includeWorldCb.checked);
    }
    updateExportStatus();
  });

  menuBtn.addEventListener('click', openModal);
  btnClose?.addEventListener('click', closeModal);
  btnCloseFooter?.addEventListener('click', closeModal);
  modal.addEventListener('click', (e) => {
    if (e.target === modal) closeModal();
  });

  btnExport?.addEventListener('click', async () => {
    if (exporting) return;

    const mapName = (nameInput?.value || '').trim();
    if (!mapName) {
      appAlert('Укажите название карты');
      return;
    }
    if (!selectedMod) {
      appAlert('Выберите JAR Spraute Engine в папке mods');
      return;
    }
    if (!selectedSpraute.size) {
      appAlert('Выберите хотя бы один файл или папку из spraute_engine');
      return;
    }
    if (includeWorldCb?.checked && !selectedWorld) {
      appAlert('Выберите мир или отключите опцию «Включить мир»');
      return;
    }

    exporting = true;
    btnExport.disabled = true;
    btnExport.textContent = 'Создание архива...';
    setStatus('Экспорт карты...');

    try {
      const result = await window.spraute.createMapExport({
        mapName,
        modFileName: selectedMod,
        sprautePaths: [...selectedSpraute],
        includeWorld: !!includeWorldCb?.checked,
        worldName: includeWorldCb?.checked ? selectedWorld : null,
      });

      if (result.success) {
        setStatus(`Экспорт готов: ${result.path}`);
        closeModal();
      } else if (result.error && result.error !== 'Отменено пользователем') {
        appAlert(`Ошибка экспорта: ${result.error}`);
        setStatus('Ошибка экспорта');
      } else {
        setStatus('Экспорт отменён');
      }
    } catch (err) {
      appAlert(`Ошибка экспорта: ${err.message}`);
      setStatus('Ошибка экспорта');
    } finally {
      exporting = false;
      btnExport.disabled = false;
      btnExport.innerHTML = '<span class="material-symbols-outlined text-[16px]">archive</span> Создать ZIP';
    }
  });
}
