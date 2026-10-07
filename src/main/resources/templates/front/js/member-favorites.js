(function() {
  "use strict";

  var contextPath = window.location.pathname.split("/front/")[0];
  var emptyState = document.getElementById("favorite-empty");
  var grid = document.getElementById("favorite-grid");
  var count = document.getElementById("favorite-count");
  var memberId = null;
  var storageKey = null;

  // 我的最愛屬於會員功能，沒有登入時直接回登入頁，不顯示其他會員的資料。
  function getStoredFavorites() {
    if (!storageKey) return [];
    try {
      var saved = JSON.parse(window.localStorage.getItem(storageKey) || "[]");
      return Array.isArray(saved) ? saved.filter(function(product) {
        return product && product.productId != null;
      }) : [];
    } catch (error) {
      return [];
    }
  }

  function saveFavorites(products) {
    if (!storageKey) return;
    window.localStorage.setItem(storageKey, JSON.stringify(products));
  }

  function removeFavorite(productId) {
    var products = getStoredFavorites().filter(function(product) {
      return String(product.productId) !== String(productId);
    });
    saveFavorites(products);
    window.renderMemberFavorites(products);
  }

  fetch(contextPath + "/api/members/session", {
    credentials: "same-origin",
    headers: { "Accept": "application/json" }
  }).then(function(response) {
    if (!response.ok) throw new Error("無法確認會員登入狀態");
    return response.json();
  }).then(function(session) {
    if (!session.loggedIn) {
      window.location.replace(contextPath + "/front/about/login/");
      return;
    }
    memberId = session.memberId;
    storageKey = "fruitude:member-favorites:" + memberId;
    window.FavoriteStore = {
      add: function(product) {
        if (!product || product.productId == null) return;
        var products = getStoredFavorites().filter(function(item) {
          return String(item.productId) !== String(product.productId);
        });
        products.unshift(product);
        saveFavorites(products);
        window.renderMemberFavorites(products);
      },
      remove: removeFavorite,
      all: getStoredFavorites,
      clear: function() { saveFavorites([]); window.renderMemberFavorites([]); }
    };
    window.addEventListener("favorite:add", function(event) {
      window.FavoriteStore.add(event.detail);
    });
    window.addEventListener("storage", function(event) {
      if (event.key === storageKey) window.renderMemberFavorites(getStoredFavorites());
    });
    window.renderMemberFavorites(getStoredFavorites());
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

    var remove = document.createElement("button");
    remove.className = "favorite-card-remove";
    remove.type = "button";
    remove.title = "移除收藏";
    remove.setAttribute("aria-label", "移除「" + (product.productName || "收藏商品") + "」");
    remove.textContent = "×";
    remove.addEventListener("click", function() { removeFavorite(product.productId); });

    body.appendChild(title);
    body.appendChild(price);
    body.appendChild(remove);
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
