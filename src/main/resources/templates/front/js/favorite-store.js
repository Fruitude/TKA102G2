(function() {
  "use strict";

  // 所有會員最愛功能共用同一份資料，並依會員編號分開儲存。
  var contextPath = window.location.pathname.split("/front/")[0];
  var storageKey = null;
  var ready = fetch(contextPath + "/api/members/session", {
    credentials: "same-origin",
    headers: { "Accept": "application/json" }
  }).then(function(response) {
    if (!response.ok) throw new Error("無法確認會員登入狀態");
    return response.json();
  }).then(function(session) {
    if (session && session.loggedIn && session.memberId != null) {
      storageKey = "fruitude:member-favorites:" + session.memberId;
    }
    return session || { loggedIn: false };
  });

  function read() {
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

  function write(products) {
    if (!storageKey) return;
    window.localStorage.setItem(storageKey, JSON.stringify(products));
    window.dispatchEvent(new CustomEvent("favorite:changed", { detail: { products: products } }));
  }

  function add(product) {
    if (!product || product.productId == null || !storageKey) return;
    var products = read().filter(function(item) {
      return String(item.productId) !== String(product.productId);
    });
    products.unshift(product);
    write(products);
  }

  function remove(productId) {
    write(read().filter(function(item) {
      return String(item.productId) !== String(productId);
    }));
  }

  window.FavoriteStore = {
    ready: ready,
    all: read,
    has: function(productId) {
      return read().some(function(item) {
        return String(item.productId) === String(productId);
      });
    },
    add: add,
    remove: remove,
    clear: function() { write([]); }
  };

  // 其他分頁修改收藏時，同步目前頁面的愛心與清單。
  window.addEventListener("storage", function(event) {
    if (event.key === storageKey) {
      window.dispatchEvent(new CustomEvent("favorite:changed", { detail: { products: read() } }));
    }
  });
})();
