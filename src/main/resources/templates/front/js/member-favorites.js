(function() {
  "use strict";

  var contextPath = window.location.pathname.split("/front/")[0];
  var emptyState = document.getElementById("favorite-empty");
  var grid = document.getElementById("favorite-grid");
  var count = document.getElementById("favorite-count");

  // 最愛頁需要登入才能查看，資料由共用 FavoriteStore 管理。
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
    var cartMessage = document.createElement("span");
    cartMessage.className = "favorite-card-message";
    cartMessage.setAttribute("role", "status");
    cartMessage.hidden = true;
    var addToCart = document.createElement("button");
    addToCart.className = "favorite-card-add";
    addToCart.type = "button";
    addToCart.textContent = "加入購物車";
    addToCart.setAttribute("aria-label", "將「" + (product.productName || "收藏商品") + "」加入購物車");
    addToCart.addEventListener("click", function() {
      if (!product.skuId || !window.SSXCart || !window.SSXCart.addProduct) {
        cartMessage.textContent = "此商品規格資料尚未準備好，請先開啟商品頁選擇規格。";
        cartMessage.hidden = false;
        return;
      }
      cartMessage.hidden = true;
      window.SSXCart.addProduct({
        skuId: String(product.skuId),
        name: product.productName || "收藏商品",
        price: Number(product.price || 0),
        image: product.imageUrl || ""
      }, 1, addToCart, cartMessage);
    });
    var remove = document.createElement("button");
    remove.className = "favorite-card-remove";
    remove.type = "button";
    remove.title = "移除收藏";
    remove.setAttribute("aria-label", "移除「" + (product.productName || "收藏商品") + "」");
    remove.textContent = "×";
    remove.addEventListener("click", function() {
      window.FavoriteStore.remove(product.productId);
    });
    body.appendChild(title);
    body.appendChild(price);
    body.appendChild(addToCart);
    body.appendChild(cartMessage);
    body.appendChild(remove);
    article.appendChild(imageLink);
    article.appendChild(body);
    return article;
  }

  window.renderMemberFavorites = function(products) {
    var favorites = Array.isArray(products) ? products : [];
    grid.replaceChildren();
    favorites.forEach(function(product) { grid.appendChild(createProductCard(product)); });
    count.textContent = String(favorites.length);
    emptyState.hidden = favorites.length > 0;
    grid.hidden = favorites.length === 0;
  };

  window.FavoriteStore.ready.then(function(session) {
    if (!session || !session.loggedIn) {
      window.location.replace(contextPath + "/front/about/login/");
      return;
    }
    window.renderMemberFavorites(window.FavoriteStore.all());
    window.addEventListener("favorite:changed", function() {
      window.renderMemberFavorites(window.FavoriteStore.all());
    });
  }).catch(function() {
    window.location.replace(contextPath + "/front/about/login/");
  });
})();
