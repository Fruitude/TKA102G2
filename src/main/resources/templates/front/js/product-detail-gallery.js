(function () {
  'use strict';
  var select = document.getElementById('pv-sku');
  if (!select) return;
  // Use the same trigger/menu classes as home cards while preserving the original select and its change handlers.
  var dropdown = document.getElementById('pv-sku-dropdown');
  var trigger = document.getElementById('pv-sku-trigger');
  var menu = document.getElementById('pv-sku-menu');
  var choices = [];
  function setDropdownOpen(open) {
    dropdown.classList.toggle('is-open', open);
    trigger.setAttribute('aria-expanded', String(open));
  }
  function updateDropdown() {
    var option = select.options[select.selectedIndex];
    if (!option) return;
    trigger.querySelector('.home-sku-caption').textContent = option.textContent;
    trigger.title = option.textContent;
    choices.forEach(function (button) {
      var on = button.dataset.skuValue === select.value;
      button.setAttribute('aria-selected', String(on));
      button.setAttribute('aria-pressed', String(on));
    });
  }
  Array.from(select.options).forEach(function (option) {
    var button = document.createElement('button');
    button.type = 'button'; button.setAttribute('role', 'option');
    button.dataset.skuValue = option.value; button.textContent = option.textContent;
    button.addEventListener('click', function () {
      select.value = option.value;
      select.dispatchEvent(new Event('change', {bubbles: true}));
      setDropdownOpen(false); trigger.focus();
    });
    menu.append(button); choices.push(button);
  });
  select.hidden = true; dropdown.hidden = false;
  dropdown.addEventListener('mouseenter', function () { setDropdownOpen(true); });
  dropdown.addEventListener('mouseleave', function () { setDropdownOpen(false); });
  trigger.addEventListener('click', function () { setDropdownOpen(true); });
  dropdown.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') { setDropdownOpen(false); trigger.focus(); }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault(); setDropdownOpen(true);
      var index = choices.indexOf(document.activeElement);
      var nextIndex = index < 0 ? select.selectedIndex : (index + (event.key === 'ArrowDown' ? 1 : -1) + choices.length) % choices.length;
      if (choices[nextIndex]) choices[nextIndex].focus();
    }
  });
  document.addEventListener('click', function (event) { if (!dropdown.contains(event.target)) setDropdownOpen(false); });
  select.addEventListener('change', updateDropdown); updateDropdown();

  var main = document.getElementById('pv-image');
  var thumbnails = Array.from(document.querySelectorAll('#pv-thumbnails .pv-thumbnail'));
  var prev = document.getElementById('pv-image-prev');
  var next = document.getElementById('pv-image-next');
  var current = -1;
  function showThumbnailWindow(index) {
    var start = Math.max(0, Math.min(thumbnails.length - 3, index - 1));
    thumbnails.forEach(function (item, i) { item.hidden = i < start || i >= start + 3; });
  }
  function show(index) {
    if (index < 0 || index >= thumbnails.length) return;
    current = index;
    var button = thumbnails[index];
    main.src = button.dataset.imageUrl;
    main.alt = button.querySelector('img').alt;
    thumbnails.forEach(function (item, i) { item.setAttribute('aria-pressed', String(i === index)); });
    showThumbnailWindow(index);
    prev.disabled = index === 0;
    next.disabled = index === thumbnails.length - 1;
  }
  function selectImage() {
    var index = thumbnails.findIndex(function (button) { return button.dataset.skuId === select.value; });
    if (index >= 0) show(index);
    else {
      current = -1;
      showThumbnailWindow(0);
      thumbnails.forEach(function (button) { button.setAttribute('aria-pressed', 'false'); });
      main.src = select.options[select.selectedIndex].dataset.imageUrl;
      prev.disabled = true;
      next.disabled = thumbnails.length === 0;
    }
  }
  thumbnails.forEach(function (button, index) { button.addEventListener('click', function () { show(index); }); });
  prev.addEventListener('click', function () { show(current - 1); });
  next.addEventListener('click', function () { show(current + 1); });
  select.addEventListener('change', selectImage);
  selectImage();
  var qty = document.getElementById('pv-qty');
  var minus = document.getElementById('pv-qty-minus');
  var plus = document.getElementById('pv-qty-plus');
  function refreshQuantityButtons() {
    minus.disabled = Number(qty.value) <= 1;
    plus.disabled = Number(qty.max) <= 0 || Number(qty.value) >= Number(qty.max);
  }
  function step(amount) {
    qty.value = String(Math.max(1, Math.min(Math.max(1, Number(qty.max)), (Number(qty.value) || 1) + amount)));
    qty.dispatchEvent(new Event('change', { bubbles: true }));
  }
  minus.addEventListener('click', function () { step(-1); });
  plus.addEventListener('click', function () { step(1); });
  qty.addEventListener('input', refreshQuantityButtons);
  qty.addEventListener('change', refreshQuantityButtons);
  select.addEventListener('change', refreshQuantityButtons);
  refreshQuantityButtons();
})();

(function () {
  'use strict';
  var section = document.getElementById('pv-related-section');
  var track = document.getElementById('pv-related');
  var viewport = document.getElementById('pv-related-viewport');
  if (!section || !track || !viewport) return;
  // Shared card fragments also include scripts; only cards have measurable carousel widths.
  var items = Array.from(track.children).filter(function (item) { return item.classList.contains('w-dyn-item'); });
  if (!items.length) return;
  var controls = section.querySelector('.pv-related-controls');
  var previous = document.getElementById('pv-related-prev');
  var next = document.getElementById('pv-related-next');
  var page = 0, perPage = 1, pages = 1, hovered = false, timer;
  function render() {
    var gap = parseFloat(getComputedStyle(track).columnGap) || 0;
    var width = items[0].getBoundingClientRect().width;
    var start = page * perPage;
    track.style.transform = 'translateX(-' + start * (width + gap) + 'px)';
    items.forEach(function (item, index) {
      var visible = index >= start && index < start + perPage;
      item.inert = !visible;
      item.setAttribute('aria-hidden', String(!visible));
    });
    controls.hidden = pages < 2;
    track.dataset.page = String(page + 1);
    track.dataset.perPage = String(perPage);
    track.dataset.totalPages = String(pages);
  }
  function resize() {
    var firstVisible = page * perPage;
    var gap = parseFloat(getComputedStyle(track).columnGap) || 0;
    var width = items[0].getBoundingClientRect().width;
    if (width <= 0 || viewport.clientWidth <= 0) return;
    perPage = Math.max(1, Math.round((viewport.clientWidth + gap) / (width + gap)));
    pages = Math.max(1, Math.ceil(items.length / perPage));
    page = Math.min(pages - 1, Math.floor(firstVisible / perPage));
    render();
  }
  function advance(direction) {
    track.querySelectorAll('.home-sku-dropdown.is-open').forEach(function (dropdown) {
      dropdown.classList.remove('is-open');
      dropdown.querySelector('.home-sku-trigger').setAttribute('aria-expanded', 'false');
    });
    page = (page + direction + pages) % pages;
    render();
  }
  function startTimer() {
    clearInterval(timer);
    if (hovered) return;
    timer = setInterval(function () {
      var rect = section.getBoundingClientRect();
      if (hovered || document.hidden || pages < 2 || rect.bottom <= 0 || rect.top >= window.innerHeight
          || track.contains(document.activeElement) || track.querySelector('.home-sku-dropdown.is-open')) return;
      advance(1);
    }, 5000);
  }
  function markPressed(button) {
    button.classList.add('is-pressed');
    clearTimeout(button.melonPressedTimer);
    button.melonPressedTimer = setTimeout(function () {button.classList.remove('is-pressed');}, 350);
  }
  previous.addEventListener('click', function () {markPressed(previous);advance(-1);startTimer();});
  next.addEventListener('click', function () {markPressed(next);advance(1);startTimer();});
  section.addEventListener('pointerenter', function (event) {
    if (event.pointerType === 'touch') return;
    hovered = true;
    clearInterval(timer);
  });
  section.addEventListener('pointerleave', function (event) {
    if (event.pointerType === 'touch') return;
    hovered = false;
    startTimer();
  });
  if (typeof ResizeObserver !== 'undefined') {
    var observer = new ResizeObserver(resize);
    observer.observe(viewport);
    window.addEventListener('pagehide', function () {observer.disconnect();});
    window.addEventListener('pageshow', function (event) {if (event.persisted) observer.observe(viewport);});
  } else window.addEventListener('resize', resize);
  window.addEventListener('pagehide', function () {clearInterval(timer);});
  window.addEventListener('pageshow', function (event) {if (event.persisted) {resize();startTimer();}});
  resize();
  startTimer();
})();
