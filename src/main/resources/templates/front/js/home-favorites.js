// Reuse the existing member store on catalog pages; other pages retain their visual toggle.
document.querySelectorAll('.products-purchase-card .products-item-text-wrapper > .home-product-detail-link').forEach(function (link) {
  var row = document.createElement('div');
  row.className = 'home-card-title-row';
  link.before(row);
  row.append(link);
  var button = document.createElement('button');
  button.type = 'button';
  button.className = 'home-card-favorite';
  button.setAttribute('aria-pressed', 'false');
  var name = link.textContent.trim();
  var heading = link.querySelector('[title]');
  if (heading) name = heading.title;
  function label(selected) {
    button.setAttribute('aria-label', (selected ? '取消喜愛：' : '喜愛：') + name);
    button.title = selected ? '取消喜愛' : '加入我的最愛';
  }
  label(false);
  var card = link.closest('.products-purchase-card');
  var productId = Number(card.dataset.productId);
  var session = null;
  function syncFavorite() {
    if (!session || !session.loggedIn) return;
    var selected = window.FavoriteStore.has(productId);
    button.setAttribute('aria-pressed', String(selected));
    label(selected);
  }
  if (window.FavoriteStore) {
    window.FavoriteStore.ready.then(function (current) { session = current; syncFavorite(); })
      .catch(function () { button.title = '無法確認登入狀態，請重新整理'; });
    window.addEventListener('favorite:changed', syncFavorite);
  }
  button.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"/></svg>';
  button.addEventListener('click', function () {
    if (window.FavoriteStore) {
      if (!session) return;
      if (!session.loggedIn) {
        var contextPath = location.pathname.split('/front/')[0];
        location.href = contextPath + '/front/about/login/?next=' + encodeURIComponent(location.pathname + location.search);
        return;
      }
      if (window.FavoriteStore.has(productId)) window.FavoriteStore.remove(productId);
      else {
        var option = card.querySelector('.home-card-sku')?.selectedOptions[0];
        window.FavoriteStore.add({productId:productId, productName:name,
          skuId:Number(option?.value || card.dataset.commerceSkuId),
          price:Number(option?.dataset.price || 0), imageUrl:card.querySelector('.products-item-image')?.src || ''});
      }
      return;
    }
    var selected = button.getAttribute('aria-pressed') !== 'true';
    button.setAttribute('aria-pressed', String(selected));
    label(selected);
  });
  row.append(button);
});
