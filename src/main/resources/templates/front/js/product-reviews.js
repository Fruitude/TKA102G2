(function () {
  'use strict';
  var section = document.querySelector('.pv-reviews');
  var dialog = document.getElementById('pv-reviews-dialog');
  if (!section || !dialog) return;
  var trigger = document.getElementById('pv-reviews-all');
  var search = document.getElementById('pv-reviews-search');
  var sort = document.getElementById('pv-reviews-sort');
  var more = document.getElementById('pv-reviews-more');
  var preview = {list: section.querySelector('.pv-review-list'), message: section.querySelector('.pv-reviews-empty'), version: 0};
  var modal = {list: document.getElementById('pv-reviews-dialog-list'), message: document.getElementById('pv-reviews-message'), version: 0, page: 0, loading: false};
  var selectedSku = '', searchTimer;
  var context = window.location.pathname.split('/front/')[0];
  var avatarTemplate = section.querySelector('.pv-review-avatar');
  function element(tag, className, text) {
    var node = document.createElement(tag); node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }
  if (!avatarTemplate) {
    avatarTemplate = element('span', 'pv-review-avatar');
    avatarTemplate.innerHTML = '<svg viewBox="0 0 32 32" aria-hidden="true"><circle cx="16" cy="11" r="6"/><path d="M5 30v-3a11 11 0 0 1 22 0v3Z"/></svg>';
  }
  function render(review, list) {
    var row = element('article', 'pv-review'); row.append(avatarTemplate.cloneNode(true));
    var body = element('div', 'pv-review-body');
    var member = element('div', 'pv-review-member'); member.append(element('span', '', review.account));
    if (review.date) member.append(element('time', '', review.date.slice(0, 10)));
    body.append(member);
    var stars = element('span', 'pv-rating pv-review-stars');
    stars.setAttribute('role', 'img'); stars.setAttribute('aria-label', review.stars + ' 顆星');
    var empty = element('span', 'pv-rating-empty', '★★★★★');
    var filled = element('span', 'pv-rating-filled', '★★★★★');
    empty.setAttribute('aria-hidden', 'true'); filled.setAttribute('aria-hidden', 'true');
    filled.style.width = Math.max(0, Math.min(5, review.stars)) * 20 + '%';
    stars.append(empty, filled); body.append(stars);
    body.append(element('div', 'pv-review-sku', '購買規格：' + (review.skuName || '')));
    body.append(element('p', 'pv-review-text', review.text)); row.append(body); list.append(row);
  }
  function renderSummary(data) {
    document.getElementById('pv-reviews-dialog-average').textContent = Number(data.average).toFixed(1);
    document.getElementById('pv-reviews-dialog-stars').setAttribute('aria-label', '評分 ' + Number(data.average).toFixed(1) + '／5');
    document.getElementById('pv-reviews-dialog-filled').style.width = Math.max(0, Math.min(5, data.average)) * 20 + '%';
    document.getElementById('pv-reviews-dialog-count').textContent = data.totalCount + ' 則評論';
    var maxCount = Math.max(0, ...data.distribution.map(function (bucket) { return bucket.count; }));
    data.distribution.forEach(function (bucket) {
      var row = dialog.querySelector('[data-stars="' + bucket.stars + '"]');
      row.querySelector('.pv-reviews-bar-count').textContent = bucket.count;
      row.querySelector('.pv-reviews-bar-track').setAttribute('aria-label', bucket.stars + ' 星，' + bucket.count + ' 則評論');
      row.querySelector('.pv-reviews-bar-fill').style.width = (maxCount ? bucket.count * 100 / maxCount : 0) + '%';
    });
  }
  function read(state, reset, isModal) {
    if (!reset && state.loading) return;
    if (state.controller) state.controller.abort();
    state.controller = new AbortController();
    var version = ++state.version;
    // Keep the current comments visible until the replacement is ready.
    if (!isModal) section.style.minHeight = section.getBoundingClientRect().height + 'px';
    if (reset) state.page = 0;
    state.loading = true; state.list.setAttribute('aria-busy', 'true');
    if (isModal) { state.message.hidden = false; state.message.textContent = '正在載入評論…'; }
    if (isModal) more.disabled = true;
    var query = isModal ? search.value.trim() : '';
    var params = new URLSearchParams({productId: section.dataset.productId, page: String(state.page || 0), size: isModal ? '20' : '3', keyword: query, sort: isModal ? sort.value : 'latest'});
    if (selectedSku) params.set('skuId', selectedSku);
    fetch(context + '/front/api/product-reviews?' + params.toString(), {headers: {'Accept': 'application/json'}, cache: 'no-store', signal: state.controller.signal})
      .then(function (response) { if (!response.ok) throw new Error('評論讀取失敗，請稍後再試。'); return response.json(); })
      .then(function (data) {
        if (version !== state.version) return;
        var rows = document.createDocumentFragment();
        data.reviews.forEach(function (review) { render(review, rows); });
        var scrollTop = dialog.scrollTop;
        if (reset) state.list.replaceChildren(rows); else state.list.append(rows);
        if (isModal) {
          renderSummary(data); state.page++; more.hidden = !data.hasMore;
          state.message.textContent = data.count ? '已顯示 ' + state.list.children.length + '／' + data.count + ' 則' + (query ? '符合的' : '') + '評論' : (query ? '沒有符合搜尋條件的評論。' : (selectedSku ? '此規格目前尚無會員評論。' : '目前尚無會員評論。'));
        } else {
          section.querySelector('.pv-reviews-average').textContent = Number(data.average).toFixed(1);
          document.getElementById('pv-reviews-heading').textContent = '商品評價(' + data.totalCount + ')';
          state.message.hidden = data.reviews.length > 0;
          state.message.textContent = selectedSku ? '此規格目前尚無會員評論。' : '目前尚無會員評論。';
          section.style.minHeight = section.getBoundingClientRect().height + 'px';
        }
        if (isModal) dialog.scrollTop = scrollTop;
      })
      .catch(function (error) {
        if (version !== state.version || error.name === 'AbortError') return;
        state.message.hidden = false; state.message.textContent = error.message;
        if (isModal) more.hidden = false;
      })
      .finally(function () {
        if (version === state.version) { state.loading = false; state.list.setAttribute('aria-busy', 'false'); if (isModal) more.disabled = false; }
      });
  }
  function resetResults() { clearTimeout(searchTimer); read(modal, true, true); }
  document.querySelectorAll('.pv-review-specs [data-review-sku]').forEach(function (button) {
    button.addEventListener('click', function () {
      if (selectedSku === button.dataset.reviewSku) return;
      selectedSku = button.dataset.reviewSku;
      document.querySelectorAll('.pv-review-specs [data-review-sku]').forEach(function (other) {
        var active = other.dataset.reviewSku === selectedSku;
        other.classList.toggle('is-active', active); other.setAttribute('aria-pressed', String(active));
      });
      read(preview, true, false);
      if (dialog.open) resetResults();
    });
  });
  search.addEventListener('input', function () {
    clearTimeout(searchTimer);
    if (!search.value.trim()) resetResults(); else searchTimer = setTimeout(resetResults, 500);
  });
  search.addEventListener('keydown', function (event) { if (event.key === 'Enter') { event.preventDefault(); resetResults(); } });
  sort.addEventListener('change', resetResults);
  if (trigger) trigger.addEventListener('click', function () { dialog.showModal(); resetResults(); });
  var pressedOutside = false;
  function outside(event) {
    var bounds = dialog.getBoundingClientRect();
    return event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom;
  }
  dialog.addEventListener('pointerdown', function (event) { pressedOutside = event.target === dialog && outside(event); });
  dialog.addEventListener('click', function (event) { if (pressedOutside && event.target === dialog && outside(event)) dialog.close(); pressedOutside = false; });
  dialog.addEventListener('close', function () { clearTimeout(searchTimer); if (modal.controller) modal.controller.abort(); modal.version++; modal.loading = false; });
  more.addEventListener('click', function () { read(modal, false, true); });
  dialog.querySelectorAll('.pv-reviews-close').forEach(function (button) { button.addEventListener('click', function () { dialog.close(); }); });
})();