'use strict';

// ── state ──────────────────────────────────────────────
let me = null;                       // { uuid, name, isOp }
let folders = new Map();             // id -> folder
let currentId = null;                // null = トップ（ルート一覧）
let settingsOpen = false;

// ── API ────────────────────────────────────────────────
async function api(method, path, body) {
  const opts = { method, headers: { 'Content-Type': 'application/json' } };
  if (body !== undefined) opts.body = JSON.stringify(body);
  const res = await fetch('/api' + path, opts);
  if (res.status === 401) { showError('セッションが切れています。ゲーム内で /abs ui を再実行してください。'); return null; }
  return res;
}

// ── init ───────────────────────────────────────────────
async function init() {
  const meRes = await api('GET', '/me');
  if (!meRes || !meRes.ok) { showError('認証に失敗しました。'); return; }
  me = await meRes.json();
  document.getElementById('player-name').textContent = me.name + (me.isOp ? ' (OP)' : '');

  await reload();
}

async function reload() {
  const res = await api('GET', '/library');
  if (!res || !res.ok) { showError('フォルダの取得に失敗しました。'); return; }
  const list = await res.json();
  folders = new Map(list.map(f => [f.id, f]));
  render();
}

// ── derived ────────────────────────────────────────────
function childrenOf(parentId) {
  return [...folders.values()].filter(f => f.parentId === parentId);
}
// トップレベル = parentId が null、または取得集合に親がいないフォルダ（共有された入口）
function topLevel() {
  return [...folders.values()].filter(f => f.parentId === null || !folders.has(f.parentId));
}
function canManage(folder) {
  return folder && (folder.ownerUuid === me.uuid || me.isOp);
}
function pathTo(id) {
  const chain = [];
  let cur = folders.get(id);
  while (cur) {
    chain.unshift(cur);
    cur = cur.parentId && folders.has(cur.parentId) ? folders.get(cur.parentId) : null;
  }
  return chain;
}

// ── render ─────────────────────────────────────────────
function render() {
  const current = currentId ? folders.get(currentId) : null;
  if (currentId && !current) { currentId = null; }   // 削除等で消えた場合

  renderBreadcrumb();

  const titleEl = document.getElementById('current-title');
  const btnNew  = document.getElementById('btn-new-folder');
  const btnSet  = document.getElementById('btn-settings');

  if (!current) {
    titleEl.textContent = 'ライブラリ';
    btnNew.hidden = true;          // トップにはルート（=自分の UUID）が自動である前提
    btnSet.hidden = true;
    settingsOpen = false;
  } else {
    titleEl.textContent = current.displayName;
    const manage = canManage(current);
    btnNew.hidden = !manage;
    btnSet.hidden = !manage;
  }
  document.getElementById('btn-settings').textContent = settingsOpen ? '✕ 閉じる' : '⚙ 設定';

  renderFolderGrid(current);
  renderSettings(current);
}

function renderBreadcrumb() {
  const bc = document.getElementById('breadcrumb');
  bc.innerHTML = '';
  const addCrumb = (label, id) => {
    const a = document.createElement('span');
    a.className = 'crumb';
    a.textContent = label;
    a.addEventListener('click', () => navigate(id));
    bc.appendChild(a);
  };
  addCrumb('ライブラリ', null);
  if (currentId) {
    pathTo(currentId).forEach(f => {
      const sep = document.createElement('span');
      sep.className = 'crumb-sep';
      sep.textContent = '/';
      bc.appendChild(sep);
      addCrumb(f.displayName, f.id);
    });
  }
}

function renderFolderGrid(current) {
  const grid  = document.getElementById('folder-list');
  const empty = document.getElementById('folders-empty');
  grid.innerHTML = '';

  const children = current ? childrenOf(current.id) : topLevel();
  if (children.length === 0) {
    empty.hidden = false;
    return;
  }
  empty.hidden = true;

  children.forEach(folder => {
    const owner = folder.ownerUuid === me.uuid;
    const card = document.createElement('div');
    card.className = 'folder-card';
    card.innerHTML = `
      <div class="name">${esc(folder.displayName)}</div>
      <div class="id">${esc(owner ? '自分' : (folder.ownerName || ''))}</div>
      ${folder.parentId === null
        ? `<span class="badge ${owner ? 'badge-owner' : 'badge-allowed'}">${owner ? 'マイライブラリ' : '共有'}</span>`
        : ''}`;
    card.addEventListener('click', () => navigate(folder.id));
    grid.appendChild(card);
  });
}

function renderSettings(current) {
  const panel = document.getElementById('settings-panel');
  if (!current || !settingsOpen || !canManage(current)) {
    panel.hidden = true;
    return;
  }
  panel.hidden = false;

  document.getElementById('input-display-name').value = current.displayName || '';

  // ルートは削除不可
  document.getElementById('delete-card').hidden = current.parentId === null;

  renderAllowedPlayers(current.allowedPlayers || []);
}

function renderAllowedPlayers(list) {
  const container = document.getElementById('allowed-list');
  container.innerHTML = '';
  if (list.length === 0) {
    container.innerHTML = '<span class="hint">許可プレイヤーはいません</span>';
    return;
  }
  list.forEach(uuid => {
    const el = document.createElement('div');
    el.className = 'player-entry';
    el.innerHTML = `<span>${esc(uuid)}</span>
      <button class="btn-remove" data-uuid="${esc(uuid)}" title="削除">✕</button>`;
    container.appendChild(el);
  });
  container.querySelectorAll('.btn-remove').forEach(btn =>
    btn.addEventListener('click', () => removePlayer(btn.dataset.uuid)));
}

// ── navigation ─────────────────────────────────────────
function navigate(id) {
  currentId = id;
  settingsOpen = false;
  render();
}

// ── actions ────────────────────────────────────────────
async function saveDisplayName() {
  const name = document.getElementById('input-display-name').value.trim();
  if (!name || !currentId) return;
  const res = await api('PATCH', '/library/' + currentId, { displayName: name });
  if (res && res.ok) { folders.set(currentId, await res.json()); render(); }
}

async function addPlayer() {
  const uuid = document.getElementById('input-new-player').value.trim();
  if (!uuid || !currentId) return;
  const cur = folders.get(currentId);
  const list = [...(cur.allowedPlayers || [])];
  if (!list.includes(uuid)) list.push(uuid);
  await patchPlayers(list);
  document.getElementById('input-new-player').value = '';
}
async function removePlayer(uuid) {
  const cur = folders.get(currentId);
  await patchPlayers((cur.allowedPlayers || []).filter(u => u !== uuid));
}
async function patchPlayers(list) {
  const res = await api('PATCH', '/library/' + currentId + '/players', { allowedPlayers: list });
  if (res && res.ok) {
    folders.set(currentId, await res.json());
    renderAllowedPlayers(folders.get(currentId).allowedPlayers || []);
  }
}

async function deleteFolder() {
  const cur = folders.get(currentId);
  if (!cur) return;
  if (!confirm(`「${cur.displayName}」とその中身をすべて削除しますか？\nこの操作は取り消せません。`)) return;
  const res = await api('DELETE', '/library/' + currentId);
  if (res && res.ok) {
    const parent = cur.parentId;
    currentId = folders.has(parent) ? parent : null;
    settingsOpen = false;
    await reload();
  }
}

// ── new folder dialog ──────────────────────────────────
function openDialog() {
  if (!currentId) return;   // トップでは作成しない（ルートは自動）
  document.getElementById('new-folder-name').value = '';
  document.getElementById('dialog-error').hidden = true;
  document.getElementById('dialog-overlay').hidden = false;
  document.getElementById('new-folder-name').focus();
}
function closeDialog() {
  document.getElementById('dialog-overlay').hidden = true;
}
async function createFolder() {
  const name = document.getElementById('new-folder-name').value.trim();
  if (!name) { showDialogError('表示名を入力してください。'); return; }
  const res = await api('POST', '/library', { parentId: currentId, displayName: name });
  if (!res) return;
  if (!res.ok) { showDialogError('作成に失敗しました。'); return; }
  closeDialog();
  await reload();
}
function showDialogError(msg) {
  const el = document.getElementById('dialog-error');
  el.textContent = msg;
  el.hidden = false;
}

// ── utils ──────────────────────────────────────────────
function esc(str) {
  return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
function showError(msg) {
  document.body.innerHTML = `<p style="color:var(--danger);padding:32px">${esc(msg)}</p>`;
}

// ── event bindings ─────────────────────────────────────
document.getElementById('btn-new-folder').addEventListener('click', openDialog);
document.getElementById('btn-settings').addEventListener('click', () => { settingsOpen = !settingsOpen; render(); });
document.getElementById('btn-dialog-cancel').addEventListener('click', closeDialog);
document.getElementById('btn-dialog-create').addEventListener('click', createFolder);
document.getElementById('btn-save-name').addEventListener('click', saveDisplayName);
document.getElementById('btn-add-player').addEventListener('click', addPlayer);
document.getElementById('btn-delete-folder').addEventListener('click', deleteFolder);
document.getElementById('dialog-overlay').addEventListener('click', e => {
  if (e.target === e.currentTarget) closeDialog();
});

init();
