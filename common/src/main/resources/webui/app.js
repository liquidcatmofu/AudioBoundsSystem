'use strict';

// ── state ──────────────────────────────────────────────
let me = null;                       // { uuid, name, isOp }
let folders = new Map();             // id -> folder
let currentId = null;                // null = トップ（ルート一覧）
let settingsOpen = false;

// ── API ────────────────────────────────────────────────
async function api(method, path, body) {
  const opts = { method, headers: { 'Content-Type': 'application/json' } };
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) opts.headers['X-ABS-CSRF'] = '1';
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
  if (me.isOp) document.getElementById('btn-speakers').hidden = false;

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
  renderSequenceSection(current);
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
        <div class="audio-meta">${fmtDuration(e.durationTicks)}</div>
      </div>
      <button class="btn-icon copy" title="ライブラリ参照をコピー">⧉</button>
      ${me.isOp ? '<button class="btn-icon assign" title="スピーカーに割り当て">📌</button>' : ''}
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
    row.querySelector('.copy').addEventListener('click', () => copyText('lib:' + folderId + '/audio/' + e.id));
    if (me.isOp) row.querySelector('.assign').addEventListener('click', () => openAssignDialog('lib:' + folderId + '/audio/' + e.id, e.displayName));
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
      headers: { 'X-Filename': encodeURIComponent(file.name), 'X-ABS-CSRF': '1' },
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
let ttsMeta = null;            // { installed, available, engines:[...] }
let editingTtsEntry = null;   // 編集中の TtsEntry、null なら新規

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

  const isOwner = canManage(folders.get(folderId));
  entries.forEach(e => {
    const row = document.createElement('div');
    row.className = 'audio-row';
    row.innerHTML = `
      <button class="btn-icon play" title="再生">▶</button>
      <div class="audio-info">
        <div class="audio-name">${esc(e.displayName)}</div>
        <div class="audio-meta">${esc(e.speakerName || e.speakerId)} ・ ${fmtDuration(e.durationTicks)}</div>
      </div>
      <button class="btn-icon copy" title="ライブラリ参照をコピー">⧉</button>
      ${me.isOp ? '<button class="btn-icon assign" title="スピーカーに割り当て">📌</button>' : ''}
      ${isOwner ? '<button class="btn-icon edit" title="編集・再合成">✎</button>' : ''}
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
    row.querySelector('.copy').addEventListener('click', () => copyText('lib:' + folderId + '/tts/' + e.id));
    if (me.isOp) row.querySelector('.assign').addEventListener('click', () => openAssignDialog('lib:' + folderId + '/tts/' + e.id, e.displayName));
    if (isOwner) row.querySelector('.edit').addEventListener('click', () => openEditTtsDialog(e));
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
  editingTtsEntry = null;
  document.getElementById('tts-dialog-title').textContent = 'セリフを合成';
  document.getElementById('btn-tts-synth').textContent = '合成して保存';

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

function openEditTtsDialog(entry) {
  if (!currentId || !ttsMeta || !ttsMeta.available) return;
  editingTtsEntry = entry;
  document.getElementById('tts-dialog-title').textContent = '再合成・編集';
  document.getElementById('btn-tts-synth').textContent = '再合成して保存';

  const engineSel = document.getElementById('tts-engine');
  engineSel.innerHTML = '';
  ttsMeta.engines.forEach(en => {
    const o = document.createElement('option');
    o.value = en.id; o.textContent = en.name;
    engineSel.appendChild(o);
  });
  engineSel.value = entry.engineId;
  populateEngine();

  // 話者をプリフィル
  const spSel = document.getElementById('tts-speaker');
  spSel.value = entry.speakerId;

  // テキスト・表示名をプリフィル
  document.getElementById('tts-text').value = entry.text || '';
  document.getElementById('tts-name').value = entry.displayName || '';
  document.getElementById('tts-error').hidden = true;

  // パラメータをプリフィル（保存済み値があればそちらを優先）
  prefillTtsParams(entry.params || {});
  document.getElementById('tts-overlay').hidden = false;
}

function prefillTtsParams(savedParams) {
  document.querySelectorAll('#tts-params input[type=range]').forEach(range => {
    const key = range.dataset.key;
    if (savedParams[key] !== undefined) {
      range.value = savedParams[key];
      const valEl = document.getElementById('pv-' + key);
      if (valEl) valEl.textContent = formatParamVal(key, parseFloat(savedParams[key]));
    }
  });
}

function closeTtsDialog() {
  document.getElementById('tts-overlay').hidden = true;
  editingTtsEntry = null;
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
      <label>${esc(p.label)} <span class="param-val" id="pv-${p.key}">${formatParamVal(p.key, p.def)}</span></label>
      <input type="range" data-key="${p.key}" min="${p.min}" max="${p.max}" step="${p.step}" value="${p.def}">`;
    const range = wrap.querySelector('input');
    range.addEventListener('input', () => {
      document.getElementById('pv-' + p.key).textContent = formatParamVal(p.key, parseFloat(range.value));
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

  const payload = {
    engineId: engine.id,
    speakerId: speakerSel.value,
    speakerName: speakerSel.options[speakerSel.selectedIndex].text,
    text,
    displayName: document.getElementById('tts-name').value.trim(),
    params
  };

  const btn = document.getElementById('btn-tts-synth');
  const origLabel = btn.textContent;
  btn.disabled = true; btn.textContent = '合成中...';
  errEl.hidden = true;
  try {
    let res;
    if (editingTtsEntry) {
      res = await api('PATCH', '/library/' + currentId + '/tts/' + editingTtsEntry.id, payload);
    } else {
      res = await api('POST', '/library/' + currentId + '/tts', payload);
    }
    if (!res) return;
    if (!res.ok) {
      const e = await res.json().catch(() => ({}));
      ttsError(e.error || '合成に失敗しました。');
      return;
    }
    closeTtsDialog();
    loadTts(currentId);
  } finally {
    btn.disabled = false; btn.textContent = origLabel;
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
  stopSeqPreview();
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
function formatParamVal(key, val) {
  if (key === 'pauseLength' && val < 0) return '自動';
  return String(val);
}
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
document.getElementById('btn-new-seq').addEventListener('click', () => openSeqDialog(null));
document.getElementById('btn-add-seq-step').addEventListener('click', addSeqStep);
document.getElementById('btn-seq-cancel').addEventListener('click', closeSeqDialog);
document.getElementById('btn-seq-save').addEventListener('click', saveSeq);
document.getElementById('seq-overlay').addEventListener('click', e => {
  if (e.target === e.currentTarget) closeSeqDialog();
});
document.getElementById('btn-assign-cancel').addEventListener('click', closeAssignDialog);
document.getElementById('assign-overlay').addEventListener('click', e => {
  if (e.target === e.currentTarget) closeAssignDialog();
});
document.getElementById('btn-speakers').addEventListener('click', openSpeakersPanel);
document.getElementById('btn-speakers-close').addEventListener('click', () => {
  document.getElementById('speakers-overlay').hidden = true;
});
document.getElementById('btn-speakers-refresh').addEventListener('click', refreshSpeakersPanel);
document.getElementById('speakers-overlay').addEventListener('click', e => {
  if (e.target === e.currentTarget) document.getElementById('speakers-overlay').hidden = true;
});
document.getElementById('btn-picker-close').addEventListener('click', closePickerOverlay);
document.getElementById('picker-overlay').addEventListener('click', e => {
  if (e.target === e.currentTarget) closePickerOverlay();
});

init();

// ── Sequence ──────────────────────────────────────────
let editingSeqEntry   = null;
let seqDialogSteps    = [];    // [{audioRef, delayTicks, label}]
let seqAvailableItems = [];    // [{ref, displayName, type}]

// playback state
let seqPlayingId    = null;
let seqPlaybackAudio = null;
let seqStepResolve  = null;
let seqDelayTimer   = null;
let seqStopped      = false;

async function previewSeq(folderId, entry) {
  if (seqPlayingId === entry.id) { stopSeqPreview(); return; }
  stopSeqPreview();

  const [audioRes, ttsRes] = await Promise.all([
    api('GET', '/library/' + folderId + '/audio'),
    api('GET', '/library/' + folderId + '/tts')
  ]);
  const cacheMap = new Map();
  if (audioRes?.ok) {
    (await audioRes.json()).forEach(a => {
      const url = '/api/library/' + folderId + '/audio/' + a.id + '/preview';
      cacheMap.set('lib:' + folderId + '/audio/' + a.id, url);
    });
  }
  if (ttsRes?.ok) {
    (await ttsRes.json()).forEach(t => {
      const url = '/api/library/' + folderId + '/tts/' + t.id + '/preview';
      cacheMap.set('lib:' + folderId + '/tts/' + t.id, url);
    });
  }

  const steps = (entry.steps || []).filter(s => s.audioRef && cacheMap.has(s.audioRef));
  if (steps.length === 0) return;

  seqStopped = false;
  seqPlayingId = entry.id;
  updateSeqPlayBtn(entry.id);

  for (const step of steps) {
    if (seqStopped) break;
    await new Promise(resolve => {
      seqStepResolve = resolve;
      seqPlaybackAudio = new Audio(cacheMap.get(step.audioRef));
      seqPlaybackAudio.addEventListener('ended', () => {
        seqPlaybackAudio = null;
        if (!seqStopped && step.delayTicks > 0) {
          seqDelayTimer = setTimeout(() => { seqDelayTimer = null; resolve(); }, step.delayTicks * 50);
        } else {
          resolve();
        }
      });
      seqPlaybackAudio.addEventListener('error', resolve);
      seqPlaybackAudio.play();
    });
    seqStepResolve = null;
  }

  if (seqPlayingId === entry.id) {
    seqPlayingId = null;
    updateSeqPlayBtn(null);
  }
}

function stopSeqPreview() {
  seqStopped = true;
  if (seqDelayTimer) { clearTimeout(seqDelayTimer); seqDelayTimer = null; }
  if (seqPlaybackAudio) { seqPlaybackAudio.pause(); seqPlaybackAudio = null; }
  if (seqStepResolve) { seqStepResolve(); seqStepResolve = null; }
  seqPlayingId = null;
  updateSeqPlayBtn(null);
}

function updateSeqPlayBtn(playingId) {
  document.querySelectorAll('#seq-list .seq-play').forEach(btn => {
    btn.textContent = btn.dataset.seqId === playingId ? '⏹' : '▶';
  });
}

function renderSequenceSection(current) {
  const section = document.getElementById('seq-section');
  if (!current) { section.hidden = true; return; }
  section.hidden = false;
  loadSequences(current.id);
}

async function loadSequences(folderId) {
  const res = await api('GET', '/library/' + folderId + '/sequences');
  if (!res || !res.ok) return;
  if (currentId !== folderId) return;
  renderSequenceList(await res.json(), folderId);
}

function renderSequenceList(entries, folderId) {
  const list  = document.getElementById('seq-list');
  const empty = document.getElementById('seq-empty');
  list.innerHTML = '';
  if (entries.length === 0) { empty.hidden = false; return; }
  empty.hidden = true;

  const isOwner = canManage(folders.get(folderId));
  entries.forEach(e => {
    const row = document.createElement('div');
    row.className = 'audio-row';
    row.innerHTML = `
      <button class="btn-icon seq-play" data-seq-id="${esc(e.id)}" title="試聴">▶</button>
      <div class="audio-info">
        <div class="audio-name">${esc(e.displayName)}</div>
        <div class="audio-meta">${(e.steps || []).length} ステップ</div>
      </div>
      ${isOwner ? '<button class="btn-icon edit" title="編集">✎</button>' : ''}
      ${isOwner ? '<button class="btn-icon del" title="削除">✕</button>' : ''}`;
    const playBtn = row.querySelector('.seq-play');
    if (seqPlayingId === e.id) playBtn.textContent = '⏹';
    playBtn.addEventListener('click', () => previewSeq(folderId, e));
    if (isOwner) row.querySelector('.edit').addEventListener('click', () => openSeqDialog(e));
    if (isOwner) row.querySelector('.del').addEventListener('click', () => deleteSeq(folderId, e));
    list.appendChild(row);
  });
}

async function openSeqDialog(entry) {
  editingSeqEntry = entry || null;
  seqDialogSteps = entry ? (entry.steps || []).map(s => ({ ...s })) : [];

  document.getElementById('seq-dialog-title').textContent = entry ? 'シーケンスを編集' : '新規シーケンス';
  document.getElementById('seq-name').value = entry ? (entry.displayName || '') : '';
  document.getElementById('seq-error').hidden = true;

  // フォルダ内の音声・TTS を取得してドロップダウン用リストを構築
  const [audioRes, ttsRes] = await Promise.all([
    api('GET', '/library/' + currentId + '/audio'),
    api('GET', '/library/' + currentId + '/tts')
  ]);
  seqAvailableItems = [];
  if (audioRes && audioRes.ok) {
    (await audioRes.json()).forEach(a =>
      seqAvailableItems.push({
        ref: 'lib:' + currentId + '/audio/' + a.id,
        displayName: a.displayName, type: 'audio'
      }));
  }
  if (ttsRes && ttsRes.ok) {
    (await ttsRes.json()).forEach(t =>
      seqAvailableItems.push({
        ref: 'lib:' + currentId + '/tts/' + t.id,
        displayName: t.displayName, type: 'tts'
      }));
  }

  renderSeqSteps();
  document.getElementById('seq-overlay').hidden = false;
}

function renderSeqSteps() {
  const container = document.getElementById('seq-steps');
  container.innerHTML = '';
  if (seqDialogSteps.length === 0) {
    container.innerHTML = '<p class="hint" style="margin:8px 0">ステップがありません。「＋ ステップを追加」で追加してください。</p>';
    return;
  }

  const audioOpts = seqAvailableItems.filter(it => it.type === 'audio');
  const ttsOpts   = seqAvailableItems.filter(it => it.type === 'tts');

  seqDialogSteps.forEach((step, i) => {
    let opts = `<option value="">── 選択してください ──</option>`;
    if (audioOpts.length > 0) {
      opts += `<optgroup label="音声ファイル">`;
      audioOpts.forEach(it => {
        const selected = it.ref === step.audioRef;
        opts += `<option value="${esc(it.ref)}"${selected ? ' selected' : ''}>${esc(it.displayName)}</option>`;
      });
      opts += `</optgroup>`;
    }
    if (ttsOpts.length > 0) {
      opts += `<optgroup label="TTS セリフ">`;
      ttsOpts.forEach(it => {
        const selected = it.ref === step.audioRef;
        opts += `<option value="${esc(it.ref)}"${selected ? ' selected' : ''}>${esc(it.displayName)}</option>`;
      });
      opts += `</optgroup>`;
    }

    const row = document.createElement('div');
    row.className = 'seq-step-row';
    row.innerHTML = `
      <div class="seq-step-header">
        <span class="seq-step-num">${i + 1}</span>
        <select class="input seq-audio-sel">${opts}</select>
        <button class="btn-icon" title="上へ" ${i === 0 ? 'disabled' : ''}>▲</button>
        <button class="btn-icon" title="下へ" ${i === seqDialogSteps.length - 1 ? 'disabled' : ''}>▼</button>
        <button class="btn-icon del" title="削除">✕</button>
      </div>
      <div class="seq-step-meta">
        <label class="seq-delay-label">遅延
          <input type="number" class="input seq-delay" min="0" value="${step.delayTicks || 0}">
          <span>ticks</span>
        </label>
        <input type="text" class="input seq-label-inp" placeholder="ラベル（省略可）" value="${esc(step.label || '')}">
      </div>`;

    const btns = row.querySelectorAll('.seq-step-header .btn-icon');
    btns[0].addEventListener('click', () => { syncSeqStepsFromDom(); moveSeqStep(i, -1); });
    btns[1].addEventListener('click', () => { syncSeqStepsFromDom(); moveSeqStep(i, +1); });
    btns[2].addEventListener('click', () => { syncSeqStepsFromDom(); removeSeqStep(i); });
    container.appendChild(row);
  });
}

function syncSeqStepsFromDom() {
  seqDialogSteps = Array.from(document.querySelectorAll('#seq-steps .seq-step-row')).map(row => ({
    audioRef:   row.querySelector('.seq-audio-sel').value,
    delayTicks: parseInt(row.querySelector('.seq-delay').value) || 0,
    label:      row.querySelector('.seq-label-inp').value.trim()
  }));
}

function addSeqStep() {
  syncSeqStepsFromDom();
  seqDialogSteps.push({ audioRef: '', delayTicks: 0, label: '' });
  renderSeqSteps();
}

function moveSeqStep(i, dir) {
  const j = i + dir;
  if (j < 0 || j >= seqDialogSteps.length) return;
  [seqDialogSteps[i], seqDialogSteps[j]] = [seqDialogSteps[j], seqDialogSteps[i]];
  renderSeqSteps();
}

function removeSeqStep(i) {
  seqDialogSteps.splice(i, 1);
  renderSeqSteps();
}

function closeSeqDialog() {
  document.getElementById('seq-overlay').hidden = true;
  editingSeqEntry = null;
  seqDialogSteps = [];
}

async function saveSeq() {
  const name = document.getElementById('seq-name').value.trim();
  if (!name) { seqError('表示名を入力してください。'); return; }

  const steps = Array.from(document.querySelectorAll('#seq-steps .seq-step-row')).map(row => ({
    audioRef:   row.querySelector('.seq-audio-sel').value,
    delayTicks: parseInt(row.querySelector('.seq-delay').value) || 0,
    label:      row.querySelector('.seq-label-inp').value.trim()
  }));

  const payload = { displayName: name, steps };
  const btn = document.getElementById('btn-seq-save');
  btn.disabled = true;
  try {
    let res;
    if (editingSeqEntry) {
      res = await api('PATCH', '/library/' + currentId + '/sequences/' + editingSeqEntry.id, payload);
    } else {
      res = await api('POST', '/library/' + currentId + '/sequences', payload);
    }
    if (!res) return;
    if (!res.ok) {
      const e = await res.json().catch(() => ({}));
      seqError(e.error || '保存に失敗しました。');
      return;
    }
    closeSeqDialog();
    loadSequences(currentId);
  } finally {
    btn.disabled = false;
  }
}

function seqError(msg) {
  const el = document.getElementById('seq-error');
  el.textContent = msg; el.hidden = false;
}

async function deleteSeq(folderId, entry) {
  if (!confirm(`「${entry.displayName}」を削除しますか？`)) return;
  const res = await api('DELETE', '/library/' + folderId + '/sequences/' + entry.id);
  if (res && res.ok) loadSequences(folderId);
}

// ── スピーカー割り当てダイアログ ──────────────────────────
let assignRef  = null;
let assignName = '';

async function openAssignDialog(ref, name) {
  assignRef  = ref;
  assignName = name;
  document.getElementById('assign-audio-name').textContent = name;
  document.getElementById('assign-error').hidden = true;
  document.getElementById('assign-list').innerHTML = '<p class="hint">読み込み中...</p>';
  document.getElementById('assign-overlay').hidden = false;

  const res = await api('GET', '/blocks/speakers');
  if (!res || !res.ok) {
    document.getElementById('assign-list').innerHTML = '<p class="hint" style="color:var(--danger)">スピーカー一覧の取得に失敗しました。</p>';
    return;
  }
  const speakers = await res.json();
  renderAssignList(speakers);
}

function renderAssignList(speakers) {
  const list = document.getElementById('assign-list');
  if (speakers.length === 0) {
    list.innerHTML = '<p class="hint">設定済みのスピーカーがありません。<br>インゲームで Speaker Block を右クリックして一度設定を保存してください。</p>';
    return;
  }
  list.innerHTML = '';
  speakers.forEach(sp => {
    const row = document.createElement('div');
    row.className = 'audio-row';
    const posLabel = `${sp.x}, ${sp.y}, ${sp.z}`;
    const label    = sp.displayName || `${sp.dim} (${posLabel})`;
    const audioLabel = sp.audioDisplayName || (sp.audioRef ? '(不明)' : '（未設定）');
    row.innerHTML = `
      <div class="audio-info">
        <div class="audio-name">${esc(label)}</div>
        <div class="audio-meta">${esc(sp.dim)} (${esc(posLabel)}) ・ 現在: ${esc(audioLabel)}</div>
      </div>
      <button class="btn btn-primary btn-sm">割り当て</button>`;
    row.querySelector('button').addEventListener('click', () => assignToSpeaker(sp));
    list.appendChild(row);
  });
}

async function assignToSpeaker(sp) {
  const errEl = document.getElementById('assign-error');
  errEl.hidden = true;
  const res = await api('POST', '/blocks/speakers/assign', {
    dim: sp.dim, x: sp.x, y: sp.y, z: sp.z,
    audioRef: assignRef,
    trackTitle: '', subtitle: ''
  });
  if (!res) return;
  if (!res.ok) {
    const e = await res.json().catch(() => ({}));
    errEl.textContent = e.error || '割り当てに失敗しました。';
    errEl.hidden = false;
    return;
  }
  closeAssignDialog();
}

function closeAssignDialog() {
  document.getElementById('assign-overlay').hidden = true;
  assignRef = null;
}

// ── スピーカー管理パネル ────────────────────────────────────
let speakersCache = [];   // 最後に取得したスピーカー一覧

async function openSpeakersPanel() {
  document.getElementById('speakers-overlay').hidden = false;
  document.getElementById('speakers-empty').hidden = true;
  document.getElementById('speakers-list').innerHTML = '<p class="hint">読み込み中...</p>';
  await refreshSpeakersPanel();
}

async function refreshSpeakersPanel() {
  const res = await api('GET', '/blocks/speakers');
  if (!res || !res.ok) {
    document.getElementById('speakers-list').innerHTML = '<p class="hint" style="color:var(--danger)">取得に失敗しました。</p>';
    return;
  }
  speakersCache = await res.json();
  renderSpeakerList(speakersCache);
}

function renderSpeakerList(speakers) {
  const list  = document.getElementById('speakers-list');
  const empty = document.getElementById('speakers-empty');
  list.innerHTML = '';
  if (speakers.length === 0) {
    empty.hidden = false;
    return;
  }
  empty.hidden = true;

  speakers.forEach(sp => {
    const posLabel = `${sp.x}, ${sp.y}, ${sp.z}`;
    const nameText = sp.displayName || '';
    const audioLabel = sp.audioDisplayName || (sp.audioRef ? '(不明)' : '（未設定）');

    const row = document.createElement('div');
    row.className = 'audio-row';
    row.innerHTML = `
      <div class="audio-info sp-info">
        <div class="sp-name-row">
          <span class="audio-name sp-name-text">${esc(nameText || `${sp.dim} (${posLabel})`)}</span>
          <input class="input sp-name-input" type="text" value="${esc(nameText)}" placeholder="スピーカー名" hidden>
          <button class="btn-icon sp-edit-btn" title="名前を変更">✎</button>
          <button class="btn-icon sp-save-btn" title="保存" hidden>✓</button>
          <button class="btn-icon sp-cancel-btn" title="キャンセル" hidden>✕</button>
        </div>
        <div class="audio-meta">${esc(sp.dim)} (${esc(posLabel)})</div>
        <div class="audio-meta sp-audio-label">音声: <strong>${esc(audioLabel)}</strong></div>
      </div>
      <button class="btn btn-primary btn-sm sp-pick-btn">音声を選ぶ</button>`;

    const nameText2 = row.querySelector('.sp-name-text');
    const nameInput = row.querySelector('.sp-name-input');
    const editBtn   = row.querySelector('.sp-edit-btn');
    const saveBtn   = row.querySelector('.sp-save-btn');
    const cancelBtn = row.querySelector('.sp-cancel-btn');

    editBtn.addEventListener('click', () => {
      nameText2.hidden = true;
      editBtn.hidden   = true;
      nameInput.hidden = false;
      saveBtn.hidden   = false;
      cancelBtn.hidden = false;
      nameInput.focus();
      nameInput.select();
    });
    cancelBtn.addEventListener('click', () => {
      nameInput.hidden = true;
      saveBtn.hidden   = true;
      cancelBtn.hidden = true;
      nameText2.hidden = false;
      editBtn.hidden   = false;
    });
    saveBtn.addEventListener('click', () => saveSpeakerName(sp, nameInput.value.trim(), row));
    nameInput.addEventListener('keydown', e => {
      if (e.key === 'Enter') saveSpeakerName(sp, nameInput.value.trim(), row);
      if (e.key === 'Escape') cancelBtn.click();
    });

    row.querySelector('.sp-pick-btn').addEventListener('click', () => openPickerForSpeaker(sp));
    list.appendChild(row);
  });
}

async function saveSpeakerName(sp, name, rowEl) {
  const res = await api('POST', '/blocks/speakers/rename', { dim: sp.dim, x: sp.x, y: sp.y, z: sp.z, name });
  if (!res || !res.ok) return;
  sp.displayName = name;
  const posLabel  = `${sp.x}, ${sp.y}, ${sp.z}`;
  const nameText2 = rowEl.querySelector('.sp-name-text');
  nameText2.textContent = name || `${sp.dim} (${posLabel})`;
  rowEl.querySelector('.sp-name-input').hidden = true;
  rowEl.querySelector('.sp-save-btn').hidden   = true;
  rowEl.querySelector('.sp-cancel-btn').hidden = true;
  nameText2.hidden = false;
  rowEl.querySelector('.sp-edit-btn').hidden = false;
}

// ── ライブラリピッカー ──────────────────────────────────────
let pickerSpeaker    = null;  // 割り当て先のスピーカー
let pickerFolderStack = [];   // [{id, displayName}]

function openPickerForSpeaker(sp) {
  pickerSpeaker     = sp;
  pickerFolderStack = [];
  document.getElementById('picker-speaker-name').textContent =
    sp.displayName || `${sp.dim} (${sp.x}, ${sp.y}, ${sp.z})`;
  document.getElementById('picker-overlay').hidden = false;
  renderPickerLevel();
}

function closePickerOverlay() {
  document.getElementById('picker-overlay').hidden = true;
  pickerSpeaker     = null;
  pickerFolderStack = [];
}

function renderPickerBreadcrumb() {
  const bc = document.getElementById('picker-breadcrumb');
  bc.innerHTML = '';
  const addCrumb = (label, depth) => {
    const sp = document.createElement('span');
    sp.className = 'crumb';
    sp.textContent = label;
    sp.addEventListener('click', () => {
      pickerFolderStack = pickerFolderStack.slice(0, depth);
      renderPickerLevel();
    });
    bc.appendChild(sp);
  };
  addCrumb('ライブラリ', 0);
  pickerFolderStack.forEach((f, i) => {
    const sep = document.createElement('span');
    sep.className = 'crumb-sep';
    sep.textContent = '/';
    bc.appendChild(sep);
    addCrumb(f.displayName, i + 1);
  });
}

function renderPickerLevel() {
  renderPickerBreadcrumb();

  const currentFolderEntry = pickerFolderStack.length > 0
    ? pickerFolderStack[pickerFolderStack.length - 1]
    : null;
  const currentFolderId = currentFolderEntry ? currentFolderEntry.id : null;

  const subFolders = currentFolderId
    ? childrenOf(currentFolderId)
    : topLevel();

  const grid = document.getElementById('picker-folder-grid');
  grid.innerHTML = '';
  subFolders.forEach(f => {
    const card = document.createElement('div');
    card.className = 'folder-card';
    card.innerHTML = `<div class="name">${esc(f.displayName)}</div>`;
    card.addEventListener('click', () => {
      pickerFolderStack.push({ id: f.id, displayName: f.displayName });
      renderPickerLevel();
    });
    grid.appendChild(card);
  });

  const audioListEl = document.getElementById('picker-audio-list');
  const emptyEl     = document.getElementById('picker-empty');
  audioListEl.innerHTML = '';

  if (!currentFolderId) {
    emptyEl.hidden = subFolders.length > 0;
    emptyEl.textContent = 'フォルダを選択してください。';
    return;
  }

  // フォルダ内の音声・TTS を取得してリスト表示
  Promise.all([
    api('GET', '/library/' + currentFolderId + '/audio'),
    api('GET', '/library/' + currentFolderId + '/tts')
  ]).then(async ([audioRes, ttsRes]) => {
    const audioEntries = (audioRes && audioRes.ok) ? await audioRes.json() : [];
    const ttsEntries   = (ttsRes   && ttsRes.ok)  ? await ttsRes.json()   : [];
    renderPickerEntries(currentFolderId, audioEntries, ttsEntries);
  });
}

function renderPickerEntries(folderId, audioEntries, ttsEntries) {
  const audioListEl = document.getElementById('picker-audio-list');
  const emptyEl     = document.getElementById('picker-empty');
  audioListEl.innerHTML = '';

  if (audioEntries.length === 0 && ttsEntries.length === 0) {
    emptyEl.hidden = false;
    emptyEl.textContent = 'このフォルダに音声がありません。';
    return;
  }
  emptyEl.hidden = true;

  const makePickerRow = (ref, displayName, meta) => {
    const row = document.createElement('div');
    row.className = 'audio-row';
    row.innerHTML = `
      <div class="audio-info">
        <div class="audio-name">${esc(displayName)}</div>
        <div class="audio-meta">${esc(meta)}</div>
      </div>
      <button class="btn btn-primary btn-sm">選択</button>`;
    row.querySelector('button').addEventListener('click', () => pickAudioForSpeaker(ref, displayName));
    return row;
  };

  if (audioEntries.length > 0) {
    const hdr = document.createElement('p');
    hdr.className = 'hint'; hdr.style.margin = '4px 0';
    hdr.textContent = '音声ファイル';
    audioListEl.appendChild(hdr);
    audioEntries.forEach(e => {
      audioListEl.appendChild(makePickerRow(
        'lib:' + folderId + '/audio/' + e.id,
        e.displayName,
        fmtDuration(e.durationTicks)
      ));
    });
  }
  if (ttsEntries.length > 0) {
    const hdr = document.createElement('p');
    hdr.className = 'hint'; hdr.style.margin = '8px 0 4px';
    hdr.textContent = 'TTS セリフ';
    audioListEl.appendChild(hdr);
    ttsEntries.forEach(e => {
      audioListEl.appendChild(makePickerRow(
        'lib:' + folderId + '/tts/' + e.id,
        e.displayName,
        `${esc(e.speakerName || e.speakerId)} ・ ${fmtDuration(e.durationTicks)}`
      ));
    });
  }
}

async function pickAudioForSpeaker(ref, displayName) {
  if (!pickerSpeaker) return;
  const sp = pickerSpeaker;
  const res = await api('POST', '/blocks/speakers/assign', {
    dim: sp.dim, x: sp.x, y: sp.y, z: sp.z,
    audioRef: ref,
    trackTitle: '', subtitle: ''
  });
  if (!res || !res.ok) return;
  const data = await res.json().catch(() => ({}));
  closePickerOverlay();
  // スピーカー管理パネルを更新
  sp.audioRef         = ref;
  sp.audioDisplayName = data.audioDisplayName || displayName;
  renderSpeakerList(speakersCache);
}
