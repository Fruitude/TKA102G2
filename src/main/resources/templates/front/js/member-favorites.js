(function() {
  "use strict";

  var contextPath = window.location.pathname.split("/front/")[0];
  var emptyState = document.getElementById("favorite-empty");
  var grid = document.getElementById("favorite-grid");
  var count = document.getElementById("favorite-count");

  // 我的最愛屬於會員功能，沒有登入時直接回登入頁，不顯示其他會員的資料。
  fetch(contextPath + "/api/members/session", {
    credentials: "same-origin",
    headers: { "Accept": "application/json" }
  }).then(function(response) {
    if (!response.ok) throw new Error("無法確認會員登入狀態");
    return response.json();
  }).then(function(session) {
    if (!session.loggedIn) {
      window.location.replace(contextPath + "/front/about/login/");
    }
  }).catch(function() {
    window.location.replace(contextPath + "/front/about/login/");
  });

  function productUrl(productId) {
    return contextPath + "/front/product/view/?productId=" + encodeURIComponent(productId);
  }

  function createProductCard(product) {
    var article = document.createElement("article");
    article.className = "favorite-card";

    var imageLink = document.createElement("a");
    imageLink.className = "favorite-card-image-link";
    imageLink.href = productUrl(product.productId);

    var image = document.createElement("img");
    image.className = "favorite-card-image";
    image.alt = product.productName || "收藏商品";
    image.loading = "lazy";
    image.src = product.imageUrl || contextPath + "/front/img/image-placeholder.svg";
    imageLink.appendChild(image);

    var body = document.createElement("div");
    body.className = "favorite-card-body";

    var title = document.createElement("h3");
    title.className = "favorite-card-title";
    title.textContent = product.productName || "未命名商品";

    var price = document.createElement("p");
    price.className = "favorite-card-price";
    price.textContent = "NT$ " + Number(product.price || 0).toLocaleString("zh-TW");

    body.appendChild(title);
    body.appendChild(price);
    article.appendChild(imageLink);
    article.appendChild(body);
    return article;
  }

  /**
   * 商品模組取得收藏資料後可呼叫此方法，不需要改動會員頁面的版型。
   * 每筆資料使用 productId、productName、price、imageUrl 四個欄位。
   */
  window.renderMemberFavorites = function(products) {
    var favorites = Array.isArray(products) ? products : [];
    grid.replaceChildren();
    favorites.forEach(function(product) {
      grid.appendChild(createProductCard(product));
    });
    count.textContent = String(favorites.length);
    emptyState.hidden = favorites.length > 0;
    grid.hidden = favorites.length === 0;
  };
})();
