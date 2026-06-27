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
  renderAudioSection(current);
  renderTtsSection(current);
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

// ── audio ──────────────────────────────────────────────
function renderAudioSection(current) {
  const section = document.getElementById('audio-section');
  if (!current) { section.hidden = true; return; }
  section.hidden = false;
  loadAudio(current.id);
}

async function loadAudio(folderId) {
  const res = await api('GET', '/library/' + folderId + '/audio');
  if (!res || !res.ok) return;
  // ナビゲーションが変わっていたら破棄
  if (currentId !== folderId) return;
  renderAudioList(await res.json(), folderId);
}

function renderAudioList(entries, folderId) {
  const list  = document.getElementById('audio-list');
  const empty = document.getElementById('audio-empty');
  list.innerHTML = '';
  if (entries.length === 0) { empty.hidden = false; return; }
  empty.hidden = true;

  entries.forEach(e => {
    const row = document.createElement('div');
    row.className = 'audio-row';
    row.innerHTML = `
      <button class="btn-icon play" title="再生">▶</button>
      <div class="audio-info">
        <div class="audio-name">${esc(e.displayName)}</div>
        <div class="audio-meta">${fmtDuration(e.durationTicks)} ・ <span class="mono">${esc(e.cacheFile)}</span></div>
      </div>
      <button class="btn-icon copy" title="ファイル名をコピー">⧉</button>
      <button class="btn-icon del" title="削除">✕</button>
      <audio preload="none" src="/api/library/${folderId}/audio/${e.id}/preview"></audio>`;

    const audio = row.querySelector('audio');
    const playBtn = row.querySelector('.play');
    playBtn.addEventListener('click', () => {
      if (audio.paused) {
        document.querySelectorAll('#audio-list audio').forEach(a => { if (a !== audio) { a.pause(); } });
        audio.play(); playBtn.textContent = '⏸';
      } else { audio.pause(); playBtn.textContent = '▶'; }
    });
    audio.addEventListener('ended', () => { playBtn.textContent = '▶'; });
    row.querySelector('.copy').addEventListener('click', () => copyText(e.cacheFile));
    row.querySelector('.del').addEventListener('click', () => deleteAudio(folderId, e));
    list.appendChild(row);
  });
}

async function uploadAudio(file) {
  if (!currentId || !file) return;
  const status = document.getElementById('upload-status');
  status.hidden = false;
  status.textContent = `アップロード中: ${file.name} ...`;
  try {
    const res = await fetch('/api/library/' + currentId + '/audio', {
      method: 'POST',
      headers: { 'X-Filename': encodeURIComponent(file.name) },
      body: file
    });
    if (res.status === 401) { showError('セッションが切れています。/abs ui を再実行してください。'); return; }
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      status.textContent = '失敗: ' + (err.error || res.status);
      return;
    }
    status.textContent = `完了: ${file.name}`;
    setTimeout(() => { status.hidden = true; }, 2500);
    loadAudio(currentId);
  } catch (e) {
    status.textContent = 'アップロードに失敗しました。';
  }
}

async function deleteAudio(folderId, entry) {
  if (!confirm(`「${entry.displayName}」を削除しますか？`)) return;
  const res = await api('DELETE', '/library/' + folderId + '/audio/' + entry.id);
  if (res && res.ok) loadAudio(folderId);
}

function fmtDuration(ticks) {
  const sec = Math.round((ticks || 0) / 20);
  const m = Math.floor(sec / 60), s = sec % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}
function copyText(text) {
  navigator.clipboard?.writeText(text);
}

// ── TTS ────────────────────────────────────────────────
let ttsMeta = null;   // { installed, available, engines:[...] }

function renderTtsSection(current) {
  const section = document.getElementById('tts-section');
  if (!current) { section.hidden = true; return; }
  section.hidden = false;
  loadTtsMeta();
  loadTts(current.id);
}

async function loadTtsMeta() {
  if (ttsMeta) { applyTtsMeta(); return; }
  const res = await api('GET', '/tts');
  if (!res || !res.ok) return;
  ttsMeta = await res.json();
  applyTtsMeta();
}
function applyTtsMeta() {
  const btn = document.getElementById('btn-new-tts');
  const note = document.getElementById('tts-unavailable');
  if (!ttsMeta.installed) {
    btn.disabled = true; note.hidden = false;
    note.textContent = 'TTS アドオンが導入されていません。';
  } else if (!ttsMeta.available) {
    btn.disabled = true; note.hidden = false;
    note.textContent = 'TTS エンジン（VOICEVOX）に接続できません。エンジンを起動してください。';
  } else {
    btn.disabled = false; note.hidden = true;
  }
}

async function loadTts(folderId) {
  const res = await api('GET', '/library/' + folderId + '/tts');
  if (!res || !res.ok) return;
  if (currentId !== folderId) return;
  renderTtsList(await res.json(), folderId);
}

function renderTtsList(entries, folderId) {
  const list  = document.getElementById('tts-list');
  const empty = document.getElementById('tts-empty');
  list.innerHTML = '';
  if (entries.length === 0) { empty.hidden = false; return; }
  empty.hidden = true;

  entries.forEach(e => {
    const row = document.createElement('div');
    row.className = 'audio-row';
    row.innerHTML = `
      <button class="btn-icon play" title="再生">▶</button>
      <div class="audio-info">
        <div class="audio-name">${esc(e.displayName)}</div>
        <div class="audio-meta">${esc(e.speakerName || e.speakerId)} ・ ${fmtDuration(e.durationTicks)} ・ <span class="mono">${esc(e.cacheFile)}</span></div>
      </div>
      <button class="btn-icon copy" title="ファイル名をコピー">⧉</button>
      <button class="btn-icon del" title="削除">✕</button>
      <audio preload="none" src="/api/library/${folderId}/tts/${e.id}/preview"></audio>`;
    const audio = row.querySelector('audio');
    const playBtn = row.querySelector('.play');
    playBtn.addEventListener('click', () => {
      if (audio.paused) {
        document.querySelectorAll('#tts-list audio, #audio-list audio').forEach(a => { if (a !== audio) a.pause(); });
        audio.play(); playBtn.textContent = '⏸';
      } else { audio.pause(); playBtn.textContent = '▶'; }
    });
    audio.addEventListener('ended', () => { playBtn.textContent = '▶'; });
    row.querySelector('.copy').addEventListener('click', () => copyText(e.cacheFile));
    row.querySelector('.del').addEventListener('click', () => deleteTts(folderId, e));
    list.appendChild(row);
  });
}

async function deleteTts(folderId, entry) {
  if (!confirm(`「${entry.displayName}」を削除しますか？`)) return;
  const res = await api('DELETE', '/library/' + folderId + '/tts/' + entry.id);
  if (res && res.ok) loadTts(folderId);
}

// ── TTS dialog ─────────────────────────────────────────
function openTtsDialog() {
  if (!currentId || !ttsMeta || !ttsMeta.available) return;
  const engineSel = document.getElementById('tts-engine');
  engineSel.innerHTML = '';
  ttsMeta.engines.forEach(en => {
    const o = document.createElement('option');
    o.value = en.id; o.textContent = en.name;
    engineSel.appendChild(o);
  });
  document.getElementById('tts-text').value = '';
  document.getElementById('tts-name').value = '';
  document.getElementById('tts-error').hidden = true;
  populateEngine();
  document.getElementById('tts-overlay').hidden = false;
}
function closeTtsDialog() {
  document.getElementById('tts-overlay').hidden = true;
}
function currentEngine() {
  const id = document.getElementById('tts-engine').value;
  return ttsMeta.engines.find(e => e.id === id);
}
function populateEngine() {
  const engine = currentEngine();
  if (!engine) return;
  // 話者
  const spSel = document.getElementById('tts-speaker');
  spSel.innerHTML = '';
  (engine.speakers || []).forEach(sp => {
    const o = document.createElement('option');
    o.value = sp.id; o.textContent = sp.name;
    spSel.appendChild(o);
  });
  // パラメータ
  const box = document.getElementById('tts-params');
  box.innerHTML = '';
  (engine.params || []).forEach(p => {
    const wrap = document.createElement('div');
    wrap.className = 'param-row';
    wrap.innerHTML = `
      <label>${esc(p.label)} <span class="param-val" id="pv-${p.key}">${p.def}</span></label>
      <input type="range" data-key="${p.key}" min="${p.min}" max="${p.max}" step="${p.step}" value="${p.def}">`;
    const range = wrap.querySelector('input');
    range.addEventListener('input', () => {
      document.getElementById('pv-' + p.key).textContent = range.value;
    });
    box.appendChild(wrap);
  });
}
async function synthesizeTts() {
  const engine = currentEngine();
  const speakerSel = document.getElementById('tts-speaker');
  const text = document.getElementById('tts-text').value.trim();
  const errEl = document.getElementById('tts-error');
  if (!text) { ttsError('セリフを入力してください。'); return; }
  if (!speakerSel.value) { ttsError('話者を選択してください。'); return; }

  const params = {};
  document.querySelectorAll('#tts-params input[type=range]').forEach(r => {
    params[r.dataset.key] = parseFloat(r.value);
  });

  const btn = document.getElementById('btn-tts-synth');
  btn.disabled = true; btn.textContent = '合成中...';
  errEl.hidden = true;
  try {
    const res = await api('POST', '/library/' + currentId + '/tts', {
      engineId: engine.id,
      speakerId: speakerSel.value,
      speakerName: speakerSel.options[speakerSel.selectedIndex].text,
      text,
      displayName: document.getElementById('tts-name').value.trim(),
      params
    });
    if (!res) return;
    if (!res.ok) {
      const e = await res.json().catch(() => ({}));
      ttsError(e.error || '合成に失敗しました。');
      return;
    }
    closeTtsDialog();
    loadTts(currentId);
  } finally {
    btn.disabled = false; btn.textContent = '合成して保存';
  }
}
function ttsError(msg) {
  const el = document.getElementById('tts-error');
  el.textContent = msg; el.hidden = false;
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
document.getElementById('audio-upload').addEventListener('change', e => {
  const file = e.target.files[0];
  if (file) uploadAudio(file);
  e.target.value = '';
});
document.getElementById('btn-new-tts').addEventListener('click', openTtsDialog);
document.getElementById('btn-tts-cancel').addEventListener('click', closeTtsDialog);
document.getElementById('btn-tts-synth').addEventListener('click', synthesizeTts);
document.getElementById('tts-engine').addEventListener('change', populateEngine);
document.getElementById('tts-overlay').addEventListener('click', e => {
  if (e.target === e.currentTarget) closeTtsDialog();
});
document.getElementById('dialog-overlay').addEventListener('click', e => {
  if (e.target === e.currentTarget) closeDialog();
});

init();
