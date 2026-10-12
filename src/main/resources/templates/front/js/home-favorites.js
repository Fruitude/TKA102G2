// Presentation only: no favorites API or persisted user data.
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
  button.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"/></svg>';
  button.addEventListener('click', function () {
    var selected = button.getAttribute('aria-pressed') !== 'true';
    button.setAttribute('aria-pressed', String(selected));
    label(selected);
  });
  row.append(button);
});
