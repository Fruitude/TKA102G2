(function () {
  'use strict';
  var trigger = document.getElementById('pv-reviews-all');
  if (!trigger) return;
  var section = document.querySelector('.pv-reviews');
  var dialog = document.getElementById('pv-reviews-dialog');
  var list = document.getElementById('pv-reviews-dialog-list');
  var message = document.getElementById('pv-reviews-message');
  var more = document.getElementById('pv-reviews-more');
  var search = document.getElementById('pv-reviews-search');
  var sort = document.getElementById('pv-reviews-sort');
  var page = 0, loading = false, requestVersion = 0, requestController, searchTimer;
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
  function element(tag, className, text) {
    var node = document.createElement(tag); node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }
  function render(review) {
    var row = element('article', 'pv-review');
    var avatar = section.querySelector('.pv-review-avatar');
    if (avatar) row.append(avatar.cloneNode(true));
    var body = element('div', 'pv-review-body');
    var member = element('div', 'pv-review-member');
    member.append(element('span', '', review.account));
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
  function load(reset) {
    if (!reset && loading) return;
    if (requestController) requestController.abort();
    requestController = new AbortController();
    var version = ++requestVersion;
    if (reset) { page = 0; list.replaceChildren(); more.hidden = true; }
    loading = true; more.disabled = true; message.textContent = '正在載入評論…';
    var context = window.location.pathname.split('/front/')[0];
    var query = search.value.trim();
    var params = new URLSearchParams({productId: section.dataset.productId, page: String(page), keyword: query, sort: sort.value});
    fetch(context + '/front/api/product-reviews?' + params.toString(), {headers: {'Accept': 'application/json'}, cache: 'no-store', signal: requestController.signal})
      .then(function (response) { if (!response.ok) throw new Error('評論讀取失敗，請稍後再試。'); return response.json(); })
      .then(function (data) {
        if (version !== requestVersion) return;
        renderSummary(data);
        data.reviews.forEach(render); page++; more.hidden = !data.hasMore;
        message.textContent = data.count ? '已顯示 ' + list.children.length + '／' + data.count + ' 則' + (query ? '符合的' : '') + '評論' : (query ? '沒有符合搜尋條件的評論。' : '目前尚無會員評論。');
      })
      .catch(function (error) { if (version !== requestVersion || error.name === 'AbortError') return; message.textContent = error.message; more.hidden = false; })
      .finally(function () { if (version === requestVersion) { loading = false; more.disabled = false; } });
  }
  function resetResults() { clearTimeout(searchTimer); load(true); }
  search.addEventListener('input', function () {
    clearTimeout(searchTimer);
    if (!search.value.trim()) resetResults();
    else searchTimer = setTimeout(resetResults, 500);
  });
  search.addEventListener('keydown', function (event) { if (event.key === 'Enter') { event.preventDefault(); resetResults(); } });
  sort.addEventListener('change', resetResults);
  trigger.addEventListener('click', function () { dialog.showModal(); resetResults(); });
  // Close only if both press and release are on the backdrop, not when dragging text out of the dialog.
  var pressedOutside = false;
  function outside(event) {
    var bounds = dialog.getBoundingClientRect();
    return event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom;
  }
  dialog.addEventListener('pointerdown', function (event) { pressedOutside = event.target === dialog && outside(event); });
  dialog.addEventListener('click', function (event) {
    if (pressedOutside && event.target === dialog && outside(event)) dialog.close();
    pressedOutside = false;
  });
  more.addEventListener('click', function () { load(false); });
  dialog.querySelectorAll('.pv-reviews-close').forEach(function (button) { button.addEventListener('click', function () { dialog.close(); }); });
})();
