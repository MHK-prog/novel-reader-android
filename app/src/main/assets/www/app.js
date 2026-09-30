'use strict';
const META_KEY = 'novel-library-meta-v1';
const DB_NAME = 'novel-library-files-v1';
const DB_STORE = 'texts';
const APP_VERSION = '0.1.0';
let state = { books: [], tags: [] };
let activeBookId = null;
let activeChapterIndex = 0;
let activeText = '';
let scrollTimer = 0;
let restoringScroll = false;
let activeTags = new Set();
let readerMode = 'read';
let nativeStorageReady = false;
let pendingNativeStorageResult = null;
const $ = id => document.getElementById(id);
const fa = value => new Intl.NumberFormat('fa-IR').format(value);
const makeId = () => crypto.randomUUID();

function normalizeState(value) {
  let books = Array.isArray(value?.books) ? value.books : [];
  if (!books.length && Array.isArray(value?.works)) {
    books = value.works.flatMap(work => (work.books || []).map(book => ({
      ...book, author: book.author || work.author || '', tags: book.tags || [], reaction: book.reaction || 'none'
    })));
  }
  const bookTags = books.flatMap(book => Array.isArray(book.tags) ? book.tags : []);
  const tags = [...new Set([...(Array.isArray(value?.tags) ? value.tags : []), ...bookTags].map(tag => String(tag).trim()).filter(Boolean))];
  return {
    tags,
    books: books.map(book => ({
      ...book,
      id: book.id || makeId(),
      title: String(book.title || 'بدون عنوان'),
      author: String(book.author || ''),
      tags: [...new Set((Array.isArray(book.tags) ? book.tags : []).map(tag => String(tag).trim()).filter(Boolean))],
      reaction: ['like', 'dislike'].includes(book.reaction) ? book.reaction : 'none',
      chapters: (Array.isArray(book.chapters) ? book.chapters : []).map(chapter => ({
        hasText: !!chapter.hasText, percent: Number(chapter.percent) || 0, done: !!chapter.done, scroll: Number(chapter.scroll) || 0
      }))
    }))
  };
}

async function transformText(text, mode, format = 'gzip') {
  const Transformer = mode === 'compress' ? window.CompressionStream : window.DecompressionStream;
  if (!Transformer) return null;
  const stream = new Blob([text]).stream().pipeThrough(new Transformer(format));
  return await new Response(stream).arrayBuffer();
}
function bytesToBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(binary);
}
function base64ToBuffer(value) {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}
async function loadMeta() {
  try {
    const saved = nativeStorageReady ? window.NovelStorage.loadNovel('library.json') : localStorage.getItem(META_KEY);
    if (!saved) return;
    if (!nativeStorageReady && saved.startsWith('gzip:')) {
      const raw = await transformText(new Blob([base64ToBuffer(saved.slice(5))]), 'decompress');
      state = normalizeState(JSON.parse(await new Response(raw).text()));
    } else {
      state = normalizeState(JSON.parse(!nativeStorageReady && saved.startsWith('json:') ? saved.slice(5) : saved));
    }
  } catch (error) {
    console.error(error);
    state = { books: [], tags: [] };
    showToast('بازیابی اطلاعات محلی انجام نشد.');
  }
}
async function saveMeta() {
  const json = JSON.stringify(state);
  if (nativeStorageReady) {
    if (window.NovelStorage.saveNovel('library.json', json) !== 'true') throw new Error('Unable to save library.json');
    return;
  }
  try {
    const compressed = await transformText(json, 'compress');
    localStorage.setItem(META_KEY, compressed ? `gzip:${bytesToBase64(compressed)}` : `json:${json}`);
  } catch (error) {
    console.error(error);
    localStorage.setItem(META_KEY, `json:${json}`);
  }
}
function openDB() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(DB_STORE, { keyPath: 'id' });
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}
async function putText(id, text) {
  if (nativeStorageReady) {
    const fileName = `chapter-${id.replace(/[^a-zA-Z0-9._-]/g, '-')}.txt`;
    if (window.NovelStorage.saveNovel(fileName, text) !== 'true') throw new Error(`Unable to save ${fileName}`);
    return;
  }
  const db = await openDB();
  let record = { id, text };
  try {
    const compressed = await transformText(text, 'compress');
    if (compressed) record = { id, data: compressed, encoding: 'gzip' };
  } catch {}
  return new Promise((resolve, reject) => {
    const req = db.transaction(DB_STORE, 'readwrite').objectStore(DB_STORE).put(record);
    req.onsuccess = () => resolve();
    req.onerror = () => reject(req.error);
  });
}
async function getText(id) {
  if (nativeStorageReady) {
    const fileName = `chapter-${id.replace(/[^a-zA-Z0-9._-]/g, '-')}.txt`;
    return window.NovelStorage.loadNovel(fileName) || '';
  }
  const db = await openDB();
  const record = await new Promise((resolve, reject) => {
    const req = db.transaction(DB_STORE).objectStore(DB_STORE).get(id);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  if (!record) return '';
  if (record.encoding === 'gzip') return await new Response(await transformText(new Blob([record.data]), 'decompress')).text();
  return record.text || '';
}
async function clearTexts() {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const req = db.transaction(DB_STORE, 'readwrite').objectStore(DB_STORE).clear();
    req.onsuccess = () => resolve();
    req.onerror = () => reject(req.error);
  });
}
function textId(bookId, index) { return `${bookId}:${index + 1}`; }
function currentBook() { return state.books.find(book => book.id === activeBookId); }
function esc(value) { return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char])); }
function allTags() { return [...new Set([...(state.tags || []), ...state.books.flatMap(book => book.tags)])].sort((a, b) => a.localeCompare(b, 'fa')); }

function openModal(title, html) {
  $('modalTitle').textContent = title;
  $('modalBody').innerHTML = html;
  $('appModal').showModal();
}
function closeModal() { $('appModal').close(); $('modalBody').replaceChildren(); }
window.openModal = openModal;
window.closeModal = closeModal;
$('modalClose').onclick = closeModal;
$('appModal').addEventListener('click', event => { if (event.target === $('appModal')) closeModal(); });
$('modalBody').addEventListener('click', event => { if (event.target.closest('[data-modal-close]')) closeModal(); });
function showToast(text) {
  const el = $('toast');
  el.textContent = text;
  el.classList.add('show');
  setTimeout(() => el.classList.remove('show'), 2400);
}

function showPage(name, title, writeHistory = true) {
  const current = document.querySelector('.page:not(.hidden)')?.id.replace('page-', '');
  document.querySelectorAll('.page').forEach(page => page.classList.toggle('hidden', page.id !== `page-${name}`));
  $('topTitle').textContent = title || 'کتابخانه‌ی کتاب‌ها';
  document.body.classList.toggle('reader-mode', name === 'reader');
  document.body.classList.toggle('reading-mode', name === 'reader' && readerMode === 'read');
  if (writeHistory && current !== name) history.pushState({ appPage: name, title: title || '' }, '', `#${name}`);
  window.scrollTo(0, 0);
}
window.addEventListener('popstate', event => {
  const name = event.state?.appPage || 'dashboard';
  if (name === 'reader' && !currentBook()) return showPage('dashboard', 'کتابخانه‌ی کتاب‌ها', false);
  showPage(name, event.state?.title || (name === 'dashboard' ? 'کتابخانه‌ی کتاب‌ها' : currentBook()?.title), false);
  closeSidebar();
});

function renderDashboard() {
  $('activeTagCount').textContent = activeTags.size ? `(${fa(activeTags.size)})` : '';
  const query = $('bookSearch').value.trim().toLocaleLowerCase('fa');
  const books = state.books.filter(book => [...activeTags].every(tag => book.tags.includes(tag)) &&
    (`${book.title} ${book.author} ${book.tags.join(' ')}`).toLocaleLowerCase('fa').includes(query));
  const list = $('worksList');
  if (!books.length) {
    list.innerHTML = `<div class="empty">${state.books.length ? 'نتیجه‌ای پیدا نشد.' : 'هنوز کتابی ندارید.'}</div>`;
    return;
  }
  list.innerHTML = books.map(book => `<article class="work-card">
    <button type="button" class="book-open" data-open-book="${esc(book.id)}"><strong>${esc(book.title)}</strong><small>${esc(book.author || '—')} · ${fa(book.chapters.length)} فصل</small></button>
    <div class="book-row"><div class="book-tags">${book.tags.map(tag => `<span class="book-tag">${esc(tag)}</span>`).join('')}</div>
    <div class="book-actions"><button type="button" class="reaction ${book.reaction === 'like' ? 'selected' : ''}" data-react="like" data-book="${esc(book.id)}" aria-label="${book.reaction === 'like' ? 'برداشتن پسند' : 'پسندیدن'}"><img src="./icons/like-${book.reaction === 'like' ? 'filled' : 'empty'}.svg" alt=""></button>
    <button type="button" class="reaction ${book.reaction === 'dislike' ? 'selected' : ''}" data-react="dislike" data-book="${esc(book.id)}" aria-label="${book.reaction === 'dislike' ? 'برداشتن نپسند' : 'نپسندیدن'}"><img src="./icons/dislike-${book.reaction === 'dislike' ? 'filled' : 'empty'}.svg" alt=""></button></div></div></article>`).join('');
}
$('bookSearch').oninput = renderDashboard;
$('openTagFilter').onclick = openTagFilterDialog;
$('worksList').addEventListener('click', async event => {
  const open = event.target.closest('[data-open-book]');
  if (open) return openChapters(state.books.find(book => book.id === open.dataset.openBook));
  const reaction = event.target.closest('[data-react]');
  if (reaction) {
    const book = state.books.find(item => item.id === reaction.dataset.book);
    if (!book) return;
    book.reaction = book.reaction === reaction.dataset.react ? 'none' : reaction.dataset.react;
    await persist();
    renderDashboard();
  }
});

function tagOptionList(tags, selected, query = '') {
  const filtered = tags.filter(tag => tag.toLocaleLowerCase('fa').includes(query.trim().toLocaleLowerCase('fa')));
  return filtered.length ? filtered.map(tag => `<label class="tag-option ${selected.has(tag) ? 'selected' : ''}"><input type="checkbox" data-tag-choice="${esc(tag)}" ${selected.has(tag) ? 'checked' : ''}><span>${esc(tag)}</span></label>`).join('') : '<span class="tag-empty">تگی پیدا نشد</span>';
}
function bindTagChooser(prefix, selected) {
  const search = $(`${prefix}Search`), options = $(`${prefix}Options`);
  const render = () => { options.innerHTML = tagOptionList(allTags(), selected, search.value); };
  search.oninput = render;
  options.onchange = event => {
    const input = event.target.closest('[data-tag-choice]');
    if (!input) return;
    if (input.checked) selected.add(input.dataset.tagChoice); else selected.delete(input.dataset.tagChoice);
    render();
  };
  render();
}
function openTagFilterDialog() {
  const draft = new Set(activeTags);
  openModal('فیلتر تگ‌ها', `<div class="tag-chooser"><input id="filterTagSearch" type="search" placeholder="جست‌وجوی تگ‌ها" aria-label="جست‌وجوی تگ‌ها"><div class="tag-options" id="filterTagOptions"></div></div><div class="modal-actions"><button class="button" type="button" data-modal-close>انصراف</button><button class="button primary" id="applyTagFilter" type="button">اعمال فیلتر</button></div>`);
  bindTagChooser('filterTag', draft);
  $('applyTagFilter').onclick = () => { activeTags = draft; renderDashboard(); closeModal(); };
}
function tagFieldHtml(prefix, label) {
  return `<div class="field"><label>${label}</label><div class="tag-chooser"><input id="${prefix}Search" type="search" placeholder="جست‌وجوی تگ‌ها" aria-label="جست‌وجوی تگ‌ها"><div class="tag-options" id="${prefix}Options"></div></div></div>`;
}
function addBookDialog() {
  const selected = new Set();
  openModal('افزودن کتاب', `<form class="modal-form" id="newBookForm"><div class="field"><label for="authorInput">نام نویسنده</label><input id="authorInput" autocomplete="name"></div><div class="field"><label for="titleInput">نام کتاب</label><input id="titleInput" required></div><div class="field"><label for="chapterCountInput">تعداد فصل‌ها</label><input id="chapterCountInput" type="number" min="1" max="5000" value="1" required></div>${tagFieldHtml('bookTags', 'تگ‌ها')}<div class="modal-actions"><button class="button" type="button" data-modal-close>انصراف</button><button class="button primary" type="submit">ذخیره</button></div></form>`);
  bindTagChooser('bookTags', selected);
  $('newBookForm').onsubmit = async event => {
    event.preventDefault();
    const count = Math.max(1, Math.min(5000, Number($('chapterCountInput').value) || 1));
    const book = { id: makeId(), title: $('titleInput').value.trim(), author: $('authorInput').value.trim(), tags: [...selected], reaction: 'none', chapters: chapterTemplate(count) };
    state.books.push(book);
    await persist(); closeModal(); closeSidebar(); renderDashboard(); openChapters(book);
  };
}
function editBookDialog(id) {
  const book = state.books.find(item => item.id === id);
  if (!book) return;
  const selected = new Set(book.tags);
  openModal('ویرایش کتاب', `<form class="modal-form" id="editBookForm"><div class="field"><label for="editAuthor">نام نویسنده</label><input id="editAuthor" value="${esc(book.author)}"></div><div class="field"><label for="editTitle">نام کتاب</label><input id="editTitle" value="${esc(book.title)}" required></div>${tagFieldHtml('editTags', 'تگ‌ها')}<div class="modal-actions"><button class="button" type="button" data-modal-close>انصراف</button><button class="button primary" type="submit">ذخیره</button></div></form>`);
  bindTagChooser('editTags', selected);
  $('editBookForm').onsubmit = async event => {
    event.preventDefault(); book.author = $('editAuthor').value.trim(); book.title = $('editTitle').value.trim(); book.tags = [...selected];
    await persist(); closeModal(); $('chapterBookTitle').textContent = book.title; $('chapterAuthor').textContent = book.author; $('readerBookTitle').textContent = book.title; renderDashboard(); renderChapters(book);
  };
}
function addTagDialog() {
  openModal('افزودن تگ', `<form class="modal-form" id="newTagForm"><div class="field"><label for="newTagName">نام تگ</label><input id="newTagName" maxlength="40" required autofocus></div><div class="modal-actions"><button class="button" type="button" data-modal-close>انصراف</button><button class="button primary" type="submit">افزودن</button></div></form>`);
  $('newTagForm').onsubmit = async event => {
    event.preventDefault();
    const tag = $('newTagName').value.trim();
    if (!tag) return;
    if (allTags().some(item => item.toLocaleLowerCase('fa') === tag.toLocaleLowerCase('fa'))) return showToast('این تگ از قبل وجود دارد.');
    state.tags.push(tag); await persist(); closeModal(); renderDashboard(); showToast('تگ اضافه شد.');
  };
}

function chapterTemplate(count) { return Array.from({ length: count }, () => ({ hasText: false, percent: 0, done: false, scroll: 0 })); }
function openChapters(book) {
  if (!book) return;
  activeBookId = book.id;
  $('chapterBookTitle').textContent = book.title;
  $('chapterAuthor').textContent = book.author;
  renderChapters(book);
  showPage('chapters', book.title);
  const next = book.chapters.findIndex(chapter => !chapter.done);
  const target = next < 0 ? Math.max(0, book.chapters.length - 1) : next;
  requestAnimationFrame(() => document.querySelector(`[data-chapter-open="${target}"]`)?.scrollIntoView({ block: 'center', behavior: 'smooth' }));
}
function renderChapters(book) {
  $('chapterList').innerHTML = book.chapters.map((chapter, index) => {
    const cls = chapter.done ? 'status-done' : chapter.hasText ? 'status-writing' : 'status-empty';
    const label = chapter.done ? 'علامت‌گذاری به‌عنوان خوانده‌نشده' : 'علامت‌گذاری به‌عنوان خوانده‌شده';
    return `<article class="chapter-row ${cls}"><button class="chapter-open" data-chapter-open="${index}" aria-label="باز کردن فصل ${fa(index + 1)}"><i class="chapter-dot"></i><span class="chapter-detail"><span class="chapter-name">فصل ${fa(index + 1)}</span><span class="chapter-meter"><span style="width:${chapter.done ? 100 : chapter.percent}%"></span></span></span></button><button class="chapter-status-toggle" type="button" data-chapter-toggle="${index}" aria-label="${label}" title="${label}" aria-pressed="${chapter.done}"><img src="./icons/read-${chapter.done ? 'done' : 'empty'}.svg" alt=""></button></article>`;
  }).join('');
}
$('chapterList').addEventListener('click', async event => {
  const toggle = event.target.closest('[data-chapter-toggle]');
  if (toggle) {
    const chapter = currentBook()?.chapters[Number(toggle.dataset.chapterToggle)];
    if (!chapter) return;
    chapter.done = !chapter.done;
    await persist(); renderChapters(currentBook()); return;
  }
  const open = event.target.closest('[data-chapter-open]');
  if (open) openReader(Number(open.dataset.chapterOpen));
});
$('chapterBack').onclick = () => navigateBack('dashboard');
$('editChapterBook').onclick = () => editBookDialog(activeBookId);
function navigateBack(fallback) {
  if (history.state?.appPage && history.state.appPage !== fallback) history.back();
  else showPage(fallback, fallback === 'dashboard' ? 'کتابخانه‌ی کتاب‌ها' : currentBook()?.title);
}
async function openReader(index) {
  activeChapterIndex = index;
  const book = currentBook(), chapter = book?.chapters[index];
  if (!chapter) return;
  activeText = await getText(textId(book.id, index));
  $('readerBookTitle').textContent = book.title;
  $('readerChapterTitle').textContent = `فصل ${fa(index + 1)} از ${fa(book.chapters.length)}`;
  $('chapterEditor').value = activeText;
  $('readingText').textContent = activeText;
  $('readerProgressFill').style.width = `${chapter.done ? 100 : chapter.percent}%`;
  $('readerProgressFill').classList.toggle('done', chapter.done);
  $('prevReaderChapter').disabled = index === 0;
  $('nextReaderChapter').disabled = index === book.chapters.length - 1;
  setReaderMode(activeText ? 'read' : 'write');
  showPage('reader', book.title);
  restoringScroll = true;
  requestAnimationFrame(() => {
    const bounds = readerScrollBounds();
    window.scrollTo(0, Math.max(0, bounds.start + (chapter.scroll || 0)));
    setTimeout(() => { restoringScroll = false; updateReadProgress(); }, 80);
  });
}
function readerScrollBounds() {
  const article = $('readingText').getBoundingClientRect();
  const articleTop = article.top + window.scrollY;
  const articleBottom = article.bottom + window.scrollY;
  const stickyHeight = $('readerSticky').offsetHeight;
  const toolbarHeight = $('readerToolbar').offsetHeight;
  return {
    start: articleTop - stickyHeight,
    end: articleBottom - window.innerHeight + toolbarHeight
  };
}
function setReaderMode(mode) {
  readerMode = mode;
  const writing = mode === 'write';
  $('chapterEditor').classList.toggle('hidden', !writing);
  $('readArea').classList.toggle('hidden', writing);
  $('importChapter').classList.toggle('hidden', !writing);
  $('saveText').classList.toggle('hidden', !writing);
  $('modeIcon').src = writing ? './icons/open-book.svg' : './icons/pencil.svg';
  $('modeToggle').setAttribute('aria-label', writing ? 'رفتن به خواندن' : 'رفتن به نوشتن');
  $('modeToggle').title = writing ? 'خواندن' : 'نوشتن';
  document.body.classList.toggle('reading-mode', !writing && !$('page-reader').classList.contains('hidden'));
}
$('modeToggle').onclick = () => {
  if (readerMode === 'read') { $('chapterEditor').value = activeText; setReaderMode('write'); return; }
  if ($('chapterEditor').value !== activeText) return showToast('متن تغییر کرده؛ اول ذخیره‌اش کن.');
  if (!activeText) return showToast('اول متن فصل را ذخیره کن.');
  $('readingText').textContent = activeText; setReaderMode('read');
};
$('importChapter').onclick = () => $('chapterFile').click();
$('chapterFile').onchange = async () => {
  const file = $('chapterFile').files?.[0]; if (!file) return;
  try {
    let text = (await file.text()).replace(/^\uFEFF/, '');
    if (/\.md$/i.test(file.name)) text = markdownToText(text);
    $('chapterEditor').value = text; setReaderMode('write'); $('chapterEditor').focus();
    showToast('فایل وارد شد؛ برای ثبت تغییرات ذخیره را بزن.');
  } catch (error) { console.error(error); showToast('خواندن فایل انجام نشد.'); }
  finally { $('chapterFile').value = ''; }
};
function markdownToText(value) {
  return value.replace(/\r\n?/g, '\n').replace(/^\s*```[^\n]*\n?/gm, '').replace(/^\s*~~~[^\n]*\n?/gm, '')
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1').replace(/\[([^\]]+)\]\((?:[^()]|\([^()]*\))*\)/g, '$1')
    .replace(/`([^`]*)`/g, '$1').replace(/^\s{0,3}#{1,6}\s*/gm, '').replace(/^\s*>\s?/gm, '')
    .replace(/^\s*(?:[-+*]|\d+[.)])\s+/gm, '').replace(/^\s*(?:---+|___+|\*\*\*+)\s*$/gm, '')
    .replace(/\|/g, ' ').replace(/\[([^\]]+)\]\[[^\]]*\]/g, '$1').replace(/^\[[^\]]+\]:\s+\S+.*$/gm, '')
    .replace(/\*\*([^*]+)\*\*|__([^_]+)__/g, '$1$2').replace(/~~([^~]+)~~/g, '$1').replace(/\*([^*]+)\*|_([^_]+)_/g, '$1$2')
    .replace(/<br\s*\/?\s*>/gi, '\n').replace(/<[^>]+>/g, '').replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
}
$('saveText').onclick = async () => {
  const book = currentBook(), chapter = book.chapters[activeChapterIndex], value = $('chapterEditor').value;
  activeText = value; chapter.hasText = !!value.trim();
  await putText(textId(book.id, activeChapterIndex), value); await persist();
  $('readingText').textContent = value; setReaderMode(value.trim() ? 'read' : 'write'); renderChapters(book); showToast('متن فصل ذخیره شد.');
};
function updateReadProgress() {
  const book = currentBook(), chapter = book?.chapters[activeChapterIndex];
  if (!chapter || !activeText || readerMode !== 'read' || $('page-reader').classList.contains('hidden')) return;
  const { start, end } = readerScrollBounds();
  const distance = end - start;
  const pct = distance <= 1 ? 100 : Math.max(0, Math.min(100, Math.round((window.scrollY - start) / distance * 100)));
  chapter.scroll = Math.max(0, window.scrollY - start);
  chapter.percent = pct;
  $('readerProgressFill').style.width = `${chapter.done ? 100 : pct}%`;
  $('readerProgressFill').classList.toggle('done', chapter.done);
  clearTimeout(scrollTimer); scrollTimer = setTimeout(() => persist(), 450);
}
window.addEventListener('scroll', () => { if (!restoringScroll) updateReadProgress(); }, { passive: true });
$('readerBack').onclick = () => navigateBack('chapters');
$('prevReaderChapter').onclick = () => { if (activeChapterIndex > 0) openReader(activeChapterIndex - 1); };
$('nextReaderChapter').onclick = () => { const book = currentBook(); if (activeChapterIndex < book.chapters.length - 1) openReader(activeChapterIndex + 1); };

async function persist() { await saveMeta(); }
async function exportLibrary() {
  const texts = [];
  for (const book of state.books) for (let index = 0; index < book.chapters.length; index++) {
    const text = await getText(textId(book.id, index));
    if (text) texts.push({ bookId: book.id, index, text });
  }
  const backup = { format: 'novel-reader-backup-v1', state: normalizeState(state), texts };
  const json = JSON.stringify(backup);
  if (nativeStorageReady) {
    const fileName = `novel-library-${new Date().toISOString().slice(0, 10)}.json`;
    if (window.NovelStorage.saveNovel(fileName, json) !== 'true') return showToast('ساخت نسخهٔ پشتیبان انجام نشد.');
    closeSidebar();
    return showToast('نسخهٔ پشتیبان در پوشهٔ NovelReader ذخیره شد.');
  }
  const raw = new TextEncoder().encode(json);
  let best = { data: raw, format: 'json' };
  for (const format of ['brotli', 'gzip', 'deflate']) {
    try {
      const data = await transformText(raw, 'compress', format);
      if (data && data.byteLength < best.data.byteLength) best = { data, format };
    } catch {}
  }
  const extensions = { json: 'json', brotli: 'json.br', gzip: 'json.gz', deflate: 'json.deflate' };
  const blob = new Blob([best.data], { type: best.format === 'gzip' ? 'application/gzip' : 'application/octet-stream' });
  const url = URL.createObjectURL(blob), link = document.createElement('a');
  link.href = url; link.download = `novel-library-${new Date().toISOString().slice(0, 10)}.${extensions[best.format]}`; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000); closeSidebar();
  const reduction = Math.max(0, Math.round((1 - best.data.byteLength / raw.byteLength) * 100));
  showToast(best.format === 'json' ? 'پشتیبان دریافت شد.' : `پشتیبان با ${best.format} فشرده شد؛ ${fa(reduction)}٪ حجم کمتر.`);
}
async function importLibrary(file) {
  let json;
  const extension = file.name.toLocaleLowerCase();
  const format = extension.endsWith('.json.br') ? 'brotli' : extension.endsWith('.json.gz') ? 'gzip' : extension.endsWith('.json.deflate') ? 'deflate' : null;
  if (format) {
    const decoded = await transformText(await file.arrayBuffer(), 'decompress', format);
    if (!decoded) throw new Error('Decompression is unavailable for this backup format.');
    json = await new Response(decoded).text();
  } else json = await file.text();
  const backup = JSON.parse(json);
  if (backup.format !== 'novel-reader-backup-v1' || !Array.isArray(backup.state?.books)) throw new Error('ساختار فایل پشتیبان معتبر نیست.');
  const incoming = normalizeState(backup.state), existing = new Set(state.books.map(book => book.id));
  const additions = incoming.books.filter(book => !existing.has(book.id));
  for (const entry of Array.isArray(backup.texts) ? backup.texts : []) {
    if (!additions.some(book => book.id === entry.bookId) || typeof entry.text !== 'string') continue;
    await putText(textId(entry.bookId, Number(entry.index) || 0), entry.text);
    const chapter = incoming.books.find(book => book.id === entry.bookId)?.chapters[Number(entry.index) || 0];
    if (chapter) chapter.hasText = !!entry.text.trim();
  }
  state.tags = [...new Set([...state.tags, ...incoming.tags])];
  state.books.push(...additions); await persist(); renderDashboard();
  showToast(`${fa(additions.length)} کتاب اضافه شد.`);
}
function importDialog() {
  openModal('وارد کردن پشتیبان', `<form class="modal-form" id="importForm"><p class="modal-note">کتاب‌های تازه به کتابخانه اضافه می‌شوند؛ کتاب‌های موجود باقی می‌مانند.</p><div class="field"><label for="backupFile">فایل پشتیبان</label><input id="backupFile" type="file" accept=".json,.br,.gz,.deflate,application/json,application/gzip,application/octet-stream" required></div><div class="modal-actions"><button class="button" type="button" data-modal-close>انصراف</button><button class="button primary" type="submit">وارد کردن</button></div></form>`);
  $('importForm').onsubmit = async event => {
    event.preventDefault();
    try { await importLibrary($('backupFile').files[0]); closeModal(); }
    catch (error) { console.error(error); showToast('فایل پشتیبان خوانده نشد.'); }
  };
}

function openSidebar() { document.body.classList.add('sidebar-open'); $('menuButton').setAttribute('aria-expanded', 'true'); }
function closeSidebar() { document.body.classList.remove('sidebar-open'); $('menuButton').setAttribute('aria-expanded', 'false'); }
$('menuButton').onclick = () => document.body.classList.contains('sidebar-open') ? closeSidebar() : openSidebar();
$('sidebarScrim').onclick = closeSidebar;
$('sideAddBook').onclick = () => { closeSidebar(); addBookDialog(); };
$('sideAddTag').onclick = () => { closeSidebar(); addTagDialog(); };
$('sideExport').onclick = exportLibrary;
$('sideImport').onclick = () => { closeSidebar(); importDialog(); };
$('sideFilterTags').onclick = () => { closeSidebar(); openTagFilterDialog(); };
$('sidebar').addEventListener('click', event => { if (event.target.closest('[data-close-sidebar]')) closeSidebar(); });
let touchStart = null;
document.addEventListener('touchstart', event => {
  const target = event.target;
  if (target.closest('input,textarea,select,[contenteditable="true"]')) { touchStart = null; return; }
  const touch = event.changedTouches[0]; touchStart = { x: touch.clientX, y: touch.clientY };
}, { passive: true });
document.addEventListener('touchend', event => {
  if (!touchStart) return;
  const touch = event.changedTouches[0], dx = touch.clientX - touchStart.x, dy = touch.clientY - touchStart.y;
  touchStart = null;
  if (Math.abs(dx) < 70 || Math.abs(dx) < Math.abs(dy) * 1.25) return;
  if (document.body.classList.contains('sidebar-open')) { if (dx > 0) closeSidebar(); }
  else if (dx < 0) openSidebar();
}, { passive: true });
window.addEventListener('keydown', event => { if (event.key === 'Escape') closeSidebar(); });

async function initialize() {
  try {
    if (window.NovelStorage) nativeStorageReady = await initializeNativeStorage();
    if (window.NovelStorage && !nativeStorageReady) showToast('برای ذخیره‌سازی، دسترسی پوشهٔ Documents را انتخاب کن.');
    await loadMeta(); renderDashboard();
  }
  catch (error) { console.error(error); showToast('بارگذاری کتابخانه ناموفق بود.'); }
  history.replaceState({ appPage: 'dashboard', title: 'کتابخانه‌ی کتاب‌ها' }, '', `${location.pathname}${location.search}#dashboard`);
  $('versionLabel').textContent = `نسخهٔ ${APP_VERSION}`;
  if ('serviceWorker' in navigator && location.protocol !== 'file:') navigator.serviceWorker.register('./sw.js').catch(() => {});
}

function initializeNativeStorage() {
  return new Promise(resolve => {
    pendingNativeStorageResult = resolve;
    let result;
    try { result = JSON.parse(window.NovelStorage.initDirectory()); }
    catch (error) { pendingNativeStorageResult = null; resolve(false); return; }
    if (result?.status === 'ready') { pendingNativeStorageResult = null; resolve(true); }
    else if (result?.status !== 'pending') { pendingNativeStorageResult = null; resolve(false); }
  });
}
window.onNovelStorageReady = result => {
  const resolve = pendingNativeStorageResult;
  pendingNativeStorageResult = null;
  if (resolve) resolve(result === true || result?.status === 'ready');
};
initialize();
