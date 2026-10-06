/*
 * Site-wide shopping cart, backed by localStorage.
 * Replaces Webflow's built-in commerce runtime (which requires a live
 * Webflow backend that this static export does not have).
 */
(function () {
  "use strict";

  var STORAGE_KEY = "ssx-cart-v1";
  var CURRENCY = window.__WEBFLOW_CURRENCY_SETTINGS || {
    symbol: "$",
    decimal: ".",
    fractionDigits: 2,
    group: ",",
    currencyCode: "NT"
  };

  // ---- storage helpers -----------------------------------------------

  function readCart() {
    try {
      var raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch (e) {
      return [];
    }
  }

  function writeCart(items) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
    } catch (e) {
      /* ignore quota / privacy-mode errors */
    }
  }

  function formatMoney(amount) {
    var n = Number(amount) || 0;
    var fixed = n.toFixed(CURRENCY.fractionDigits);
    var parts = fixed.split(".");
    parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, CURRENCY.group);
    var joined = parts.join(CURRENCY.decimal);
    return CURRENCY.symbol + "\u00A0" + joined + "\u00A0" + CURRENCY.currencyCode;
  }

  function getCount(items) {
    return items.reduce(function (sum, item) {
      return sum + item.qty;
    }, 0);
  }

  function getSubtotal(items) {
    return items.filter(function (item) { return !item.unavailable; }).reduce(function (sum, item) {
      return sum + item.qty * item.price;
    }, 0);
  }

  // Items have no "checked" field until the first time this feature touches
  // them, so a missing field defaults to checked - existing cart contents
  // stay included in checkout rather than silently dropping out.
  function isChecked(item) {
    return !item.unavailable && item.checked !== false;
  }

  function getCheckedItems(items) {
    return items.filter(isChecked);
  }

  // ---- mutations --------------------------------------------------------

  function addToCart(newItem) {
    var items = readCart();
    var existing = null;
    for (var i = 0; i < items.length; i++) {
      if (items[i].skuId === newItem.skuId) {
        existing = items[i];
        break;
      }
    }
    if (existing) {
      existing.qty += newItem.qty;
      if (newItem.skuName) {
        existing.skuName = newItem.skuName;
        existing.name = newItem.name; existing.price = newItem.price; existing.image = newItem.image;
        existing.stock = newItem.stock; existing.skuStatus = newItem.skuStatus; existing.unavailable = false;
      }
    } else {
      newItem.checked = true;
      items.push(newItem);
    }
    writeCart(items);
    renderAll();
    bumpCartIcon();
  }

  // "立即購買" (Buy Now): checkout only ever looks at checked items, so a
  // real buy-now has to (a) make sure this exact product/qty is in the cart
  // and (b) uncheck everything else, otherwise checkout would show whatever
  // was already checked from before - which could be unrelated products, or
  // nothing at all if the cart was empty. Existing items are kept, just
  // unchecked, so they're not lost, only excluded from this checkout run.
  function buyNow(newItem) {
    var items = readCart();
    items.forEach(function (item) {
      item.checked = false;
    });
    var existing = null;
    for (var i = 0; i < items.length; i++) {
      if (items[i].skuId === newItem.skuId) {
        existing = items[i];
        break;
      }
    }
    if (existing) {
      existing.qty = newItem.qty;
      existing.checked = true;
    } else {
      newItem.checked = true;
      items.push(newItem);
    }
    writeCart(items);
    renderAll();
  }

  function setChecked(skuId, checked) {
    var items = readCart();
    for (var i = 0; i < items.length; i++) {
      if (items[i].skuId === skuId) {
        items[i].checked = checked;
        break;
      }
    }
    writeCart(items);
    renderAll();
  }

  function setQty(skuId, qty) {
    if (qty <= 0) {
      removeItemAnimated(skuId);
      return;
    }
    var items = readCart();
    var pulsed = false;
    for (var i = 0; i < items.length; i++) {
      if (items[i].skuId === skuId) {
        if (items[i].unavailable) return;
        if (items[i].stock != null) qty = Math.min(qty, items[i].stock);
        pulsed = qty < items[i].qty;
        items[i].qty = qty;
        break;
      }
    }
    writeCart(items);
    renderAll({ pulseSkuId: pulsed ? skuId : null });
  }

  function removeItemAnimated(skuId) {
    var row = document.querySelector('[data-cart-row="' + cssEscape(skuId) + '"]');
    if (row) {
      row.classList.add("cart-item-removing");
      window.setTimeout(function () {
        writeCart(readCart().filter(function (item) {
          return item.skuId !== skuId;
        }));
        renderAll();
      }, 260);
    } else {
      writeCart(readCart().filter(function (item) {
        return item.skuId !== skuId;
      }));
      renderAll();
    }
  }

  function cssEscape(value) {
    return String(value).replace(/["\\]/g, "\\$&");
  }

  // ---- rendering ----------------------------------------------------------

  function bumpCartIcon() {
    document.querySelectorAll(".w-commerce-commercecartopenlink").forEach(function (link) {
      link.classList.remove("cart-bump");
      void link.offsetWidth; // restart animation
      link.classList.add("cart-bump");
    });
  }

  function renderBadges(items) {
    var count = getCount(items);
    document.querySelectorAll(".cart-quantity").forEach(function (el) {
      el.textContent = String(count);
    });
  }

  function buildItemRow(item, opts) {
    var row = document.createElement("div");
    row.className = "w-commerce-commercecartitem";
    row.setAttribute("data-cart-row", item.skuId);
    if (opts && opts.pulseSkuId === item.skuId) {
      row.classList.add("cart-item-pulse");
    }

    var checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.className = "cart-item-checkbox";
    checkbox.checked = isChecked(item);
    checkbox.disabled = !!item.unavailable;
    checkbox.setAttribute("data-cart-action", "toggle-checked");
    checkbox.setAttribute("aria-label", "選取此商品加入結帳");

    var img = document.createElement("img");
    img.className = "w-commerce-commercecartitemimage";
    img.alt = "";
    img.src = item.image || "";

    var info = document.createElement("div");
    info.className = "w-commerce-commercecartiteminfo";

    var name = document.createElement("div");
    name.className = "w-commerce-commercecartproductname";
    name.textContent = item.name;

    var price = document.createElement("div");
    price.textContent = formatMoney(item.price);
    if (item.unavailable) price.textContent = "已下架或無可訂購數量，無法購買";
    var notice = quantityNotice(item.skuStatus, item.qty);
    if (notice && !item.unavailable) {
      var warning = document.createElement("p"); warning.textContent = notice; warning.style.cssText = "font-size:12px;color:#a65b00;margin:4px 0"; warning.setAttribute("role", "status"); info.appendChild(warning);
    }

    var stepper = document.createElement("div");
    stepper.className = "cart-qty-stepper";

    var decBtn = document.createElement("button");
    decBtn.type = "button";
    decBtn.className = "cart-qty-btn";
    decBtn.setAttribute("data-cart-action", "dec");
    decBtn.setAttribute("aria-label", "Decrease quantity");
    decBtn.textContent = "\u2212";

    var qtyInput = document.createElement("input");
    qtyInput.type = "number";
    qtyInput.min = "0";
    qtyInput.className = "w-commerce-commercecartquantity form-input quantity-input cart-qty-input";
    qtyInput.setAttribute("aria-label", "Update quantity");
    qtyInput.value = String(item.qty);
    qtyInput.disabled = !!item.unavailable;
    if (item.stock != null) qtyInput.max = String(item.stock);

    var incBtn = document.createElement("button");
    incBtn.type = "button";
    incBtn.className = "cart-qty-btn";
    incBtn.setAttribute("data-cart-action", "inc");
    incBtn.setAttribute("aria-label", "Increase quantity");
    incBtn.textContent = "+";
    decBtn.disabled = !!item.unavailable;
    incBtn.disabled = !!item.unavailable || (item.stock != null && item.qty >= item.stock);

    stepper.appendChild(decBtn);
    stepper.appendChild(qtyInput);
    stepper.appendChild(incBtn);

    var removeLink = document.createElement("a");
    removeLink.href = "#";
    removeLink.className = "cart-remove-link";
    removeLink.setAttribute("data-cart-action", "remove");
    removeLink.setAttribute("role", "button");
    removeLink.setAttribute("aria-label", "Remove item from cart");
    removeLink.textContent = "移除";

    info.appendChild(name);
    if (item.skuName) {
      var skuLabel = document.createElement("div");
      skuLabel.className = "cart-item-sku";
      skuLabel.textContent = item.skuName;
      info.appendChild(skuLabel);
    }
    info.appendChild(price);
    info.appendChild(stepper);
    info.appendChild(removeLink);

    row.appendChild(checkbox);
    row.appendChild(img);
    row.appendChild(info);
    return row;
  }

  function renderCartList(items, opts) {
    document.querySelectorAll(".w-commerce-commercecartform").forEach(function (form) {
      var list = form.querySelector(".w-commerce-commercecartlist");
      var subtotalEl = form.querySelector(".w-commerce-commercecartordervalue");
      if (!list) return;

      list.innerHTML = "";
      items.forEach(function (item) {
        list.appendChild(buildItemRow(item, opts));
      });

      var wrapper = form.closest(".w-commerce-commercecartcontainer") || document;
      var emptyState = wrapper.querySelector(".w-commerce-commercecartemptystate");
      var hasItems = items.length > 0;
      var checkedItems = getCheckedItems(items);

      form.style.display = hasItems ? "" : "none";
      if (emptyState) emptyState.style.display = hasItems ? "none" : "";
      // Only checked items are what "前往結帳" will actually carry through,
      // so the subtotal shown here should match that, not the full cart.
      if (subtotalEl) subtotalEl.textContent = formatMoney(getSubtotal(checkedItems));

      // Nothing checked -> there's nothing to check out, so disable the
      // button instead of letting it lead to an empty/confusing checkout.
      var checkoutBtn = form.querySelector(".w-commerce-commercecartcheckoutbutton");
      if (checkoutBtn) {
        var noneChecked = checkedItems.length === 0;
        checkoutBtn.classList.toggle("is-cart-checkout-disabled", noneChecked);
        checkoutBtn.setAttribute("aria-disabled", noneChecked ? "true" : "false");
      }
    });
  }

  // The checkout page (checkout/index.html) has its own separate order
  // summary markup (not the cart sidebar's), statically baked in by Webflow
  // at export time with whatever sample product was in the editor when it
  // was last published. It never reflected the real cart, since that needs
  // Webflow's live backend. Rebuild it from the same localStorage cart data
  // used everywhere else on the site.
  function buildCheckoutItemRow(item) {
    var row = document.createElement("div");
    row.className = "w-commerce-commercecheckoutorderitem";
    row.setAttribute("role", "listitem");

    var img = document.createElement("img");
    img.className = "w-commerce-commercecartitemimage";
    img.alt = "";
    img.src = item.image || "";

    var descWrapper = document.createElement("div");
    descWrapper.className = "w-commerce-commercecheckoutorderitemdescriptionwrapper";

    var name = document.createElement("div");
    name.className = "w-commerce-commerceboldtextblock checkout-item-name";
    name.textContent = item.name;

    var qtyWrapper = document.createElement("div");
    qtyWrapper.className = "w-commerce-commercecheckoutorderitemquantitywrapper";

    var qtyLabel = document.createElement("div");
    qtyLabel.className = "paragraph-18";
    qtyLabel.textContent = "數量：";

    var qtyValue = document.createElement("div");
    qtyValue.className = "paragraph-18";
    qtyValue.textContent = String(item.qty);

    qtyWrapper.appendChild(qtyLabel);
    qtyWrapper.appendChild(qtyValue);

    var subtotalWrapper = document.createElement("div");
    subtotalWrapper.className = "w-commerce-commercecheckoutorderitemquantitywrapper";

    var subtotalLabel = document.createElement("div");
    subtotalLabel.className = "paragraph-18";
    subtotalLabel.textContent = "小計：";

    var subtotalValue = document.createElement("div");
    subtotalValue.className = "paragraph-18";
    subtotalValue.textContent = formatMoney(item.price * item.qty);

    subtotalWrapper.appendChild(subtotalLabel);
    subtotalWrapper.appendChild(subtotalValue);

    descWrapper.appendChild(name);
    descWrapper.appendChild(qtyWrapper);
    descWrapper.appendChild(subtotalWrapper);

    row.appendChild(img);
    row.appendChild(descWrapper);
    return row;
  }

  // 固定運費，跟「配送方式」的宅配選項、confirm 頁的運費一致
  var CHECKOUT_SHIPPING_FEE = 45;

  function renderCheckoutSummary(items) {
    var list = document.querySelector(".w-commerce-commercecheckoutorderitemslist");
    if (!list) return; // not on the checkout page

    var checkedItems = getCheckedItems(items);

    list.innerHTML = "";
    checkedItems.forEach(function (item) {
      list.appendChild(buildCheckoutItemRow(item));
    });

    var subtotal = getSubtotal(checkedItems);
    var shippingFee = checkedItems.length > 0 ? CHECKOUT_SHIPPING_FEE : 0;
    var subtotalEl = document.querySelector('[data-wf-bindings*="commerceOrder.subtotal"]');
    var shippingFeeEl = document.getElementById("checkout-summary-shipping-fee");
    var totalEl = document.querySelector(".w-commerce-commercecheckoutsummarytotal");
    // No tax modelled in this static cart, so total == subtotal + shipping fee.
    if (subtotalEl) subtotalEl.textContent = formatMoney(subtotal);
    if (shippingFeeEl) shippingFeeEl.textContent = formatMoney(shippingFee);
    if (totalEl) totalEl.textContent = formatMoney(subtotal + shippingFee);
  }

  function renderAll(opts) {
    var items = readCart();
    renderBadges(items);
    renderCartList(items, opts);
    renderCheckoutSummary(items);
  }

  // ---- open / close sidebar --------------------------------------------

  function showCartNotice(text) {
    document.querySelectorAll(".w-commerce-commercecartcontainer").forEach(function (container) {
      var notice = container.querySelector(".cart-live-notice");
      if (!notice) {
        notice = document.createElement("p"); notice.className = "cart-live-notice";
        notice.setAttribute("role", "status"); notice.style.cssText = "margin:8px 24px;color:#8b3535;font-size:13px";
        var header = container.querySelector(".w-commerce-commercecartheader");
        if (header) header.after(notice); else container.prepend(notice);
      }
      notice.textContent = text; notice.hidden = !text;
    });
  }

  function quantityNotice(status, quantity) {
    if (Number(status) === 1 && quantity >= 10) return "若數量需求超過10箱，請電話聯繫。";
    if (Number(status) === 2 && quantity > 2) return "此商品規格目前需較長備貨時間，敬請見諒！";
    return "";
  }

  function fetchLiveSkus(ids) {
    ids = ids.filter(function (id) { return /^\d+$/.test(String(id)) && Number(id) > 0 && Number(id) <= 2147483647; });
    if (!ids.length) return Promise.resolve([]);
    return fetch(getContextPath() + "/front/api/cart-products?skuIds=" + ids.map(encodeURIComponent).join(","), { cache: "no-store", credentials: "same-origin" })
      .then(function (response) { if (!response.ok) throw new Error("無法確認商品資料，請稍後再試"); return response.json(); });
  }

  function verifyCart() {
    var ids = readCart().map(function (item) { return String(item.skuId); });
    return fetchLiveSkus(ids).then(function (skus) {
      var byId = {}; skus.forEach(function (sku) { byId[String(sku.skuId)] = sku; });
      var changed = false;
      var items = readCart().map(function (item) {
        if (ids.indexOf(String(item.skuId)) < 0) return item;
        var sku = byId[String(item.skuId)];
        if (!sku || !sku.available) {
          item.unavailable = true; item.checked = false; changed = true;
        } else {
          if (item.price !== sku.price || item.qty > sku.stock || item.unavailable) changed = true;
          item.unavailable = false; item.stock = sku.stock; item.skuStatus = sku.skuStatus;
          item.price = sku.price; item.name = sku.name; item.skuName = sku.skuName;
          item.qty = Math.min(item.qty, sku.stock);
        }
        return item;
      });
      writeCart(items); renderAll();
      showCartNotice(changed ? "商品價格、庫存或上架狀態已更新，請確認購物車內容。" : "");
      return items;
    });
  }

  function addVerifiedProduct(product, quantity, button, message, checkoutUrl) {
    button.disabled = true;
    fetchLiveSkus([product.skuId]).then(function (skus) {
      var sku = skus[0];
      if (!sku || !sku.available) throw new Error("此商品已下架或已無可訂購數量，請選擇其他商品。");
      var existing = readCart().find(function (item) { return String(item.skuId) === String(sku.skuId); });
      if ((checkoutUrl ? 0 : (existing ? existing.qty : 0)) + quantity > sku.stock) throw new Error("此規格最多可訂購 " + sku.stock + " 箱（含購物車已有數量）。");
      var item = { skuId: String(sku.skuId), skuName: sku.skuName, qty: quantity, name: sku.name, price: sku.price, image: product.image, stock: sku.stock, skuStatus: sku.skuStatus };
      if (checkoutUrl) { buyNow(item); window.location.href = checkoutUrl; }
      else { addToCart(item); openCart(); }
    }).catch(function (error) {
      if (message) { message.textContent = error.message; message.hidden = false; }
      else window.alert(error.message);
    }).finally(function () { button.disabled = false; });
  }

  function openCart() {
    showCartNotice("正在確認最新商品資料…");
    verifyCart().catch(function () { showCartNotice("無法確認最新商品資料，請稍後重試；結帳時會再次檢查。"); });
    document.querySelectorAll(".w-commerce-commercecartcontainerwrapper").forEach(function (wrapper) {
      wrapper.style.display = "flex";
      void wrapper.offsetWidth; // force reflow so the transition plays
      wrapper.classList.add("cart-is-open");
    });
  }

  function closeCart() {
    document.querySelectorAll(".w-commerce-commercecartcontainerwrapper").forEach(function (wrapper) {
      wrapper.classList.remove("cart-is-open");
      window.setTimeout(function () {
        if (!wrapper.classList.contains("cart-is-open")) {
          wrapper.style.display = "none";
        }
      }, 350);
    });
  }

  // ---- disable Webflow's own (backend-dependent) commerce runtime -------

  function neutralizeWebflowCommerce() {
    // Webflow's own export names most commerce nodes "commerce-*", but the
    // checkout button is the one exception - it's "cart-checkout-button"
    // with no "commerce-" prefix. Left un-renamed, Webflow's bundled JS still
    // recognizes it, intercepts the click, and (since this static export has
    // no live Stripe/Apollo backend) shows its own "This site is currently
    // unsecured so you cannot enter checkout." alert instead of letting the
    // link's href navigate normally.
    document.querySelectorAll('[data-node-type^="commerce-"], [data-node-type="cart-checkout-button"]').forEach(function (el) {
      el.setAttribute("data-node-type", "x-" + el.getAttribute("data-node-type"));
    });
    document.querySelectorAll("[data-wf-cart-query]").forEach(function (el) {
      el.removeAttribute("data-wf-cart-query");
    });
    // Webflow finds the cart-count badge by CSS class (not by data-node-type), captures a
    // direct element reference during its own init, and later asynchronously overwrites its
    // text via this data-wf-bindings metadata once its (backend-less) cart query settles.
    // Stripping the metadata now keeps that later, async write from ever having anywhere to
    // write to, so our own count isn't clobbered after the fact.
    document.querySelectorAll(".w-commerce-commercecartwrapper [data-wf-bindings]").forEach(function (el) {
      el.removeAttribute("data-wf-bindings");
    });
  }

  // ---- events -------------------------------------------------------------

  function readProductFromForm(form) {
    var details = form.closest(".shop-product-details") || document;
    var nameEl = details.querySelector(".heading-h3-product") || document.querySelector(".heading-h3-product");
    var priceEl = details.querySelector('[data-wf-sku-bindings*="f_price_"]');
    var imgEl = document.querySelector(".shop-product-image");
    var priceText = priceEl ? priceEl.textContent : "0";
    var priceNum = parseFloat(priceText.replace(/[^0-9.]/g, "")) || 0;

    var imageUrl = "";
    if (imgEl && imgEl.getAttribute("src")) {
      // resolve to an absolute URL so it still loads once rendered on a different page
      imageUrl = new URL(imgEl.getAttribute("src"), window.location.href).href;
    }

    return {
      name: nameEl ? nameEl.textContent.trim() : "Product",
      price: priceNum,
      image: imageUrl
    };
  }

  function readProductFromCard(card) {
    var nameEl = card.querySelector(".heading-h4");
    var priceEl = card.querySelector('[data-wf-sku-bindings*="f_price_"]');
    var imgEl = card.querySelector(".products-item-image");
    var priceText = priceEl ? priceEl.textContent : "0";
    var priceNum = parseFloat(priceText.replace(/[^0-9.]/g, "")) || 0;

    var imageUrl = "";
    if (imgEl && imgEl.getAttribute("src")) {
      imageUrl = new URL(imgEl.getAttribute("src"), window.location.href).href;
    }

    var name = nameEl ? (nameEl.getAttribute("title") || nameEl.textContent).trim() : "Product";

    // The card itself carries no CMS sku id, so its own name is used as a
    // stable, page-independent skuId instead. This is NOT the card's href:
    // the home page's decorative "best sellers" grid has 60 differently
    // named cards but only 6 real product pages to link to, so most cards'
    // hrefs point at the same handful of URLs. Keying by href collapsed all
    // of those differently-named cards onto the same 3-6 cart lines -
    // clicking the cart icon on card #4 onward would silently add its
    // quantity onto whichever of the first few products happened to create
    // that line first, showing the wrong name entirely. Keying by name
    // instead gives every distinct product its own line, while same-named
    // cards (e.g. the same real product shown on the home page, a category
    // page and "all products") still correctly merge into one.
    var skuId = card.getAttribute("data-commerce-sku-id") || "card:" + name;
    var select = card.querySelector(".home-card-sku");
    var option = select && select.options[select.selectedIndex];
    if (option) {
      return {
        skuId: String(option.value), name: name, skuName: option.getAttribute("data-sku-name") || option.textContent.trim(),
        price: Number(option.getAttribute("data-price")),
        image: new URL(option.getAttribute("data-image-url"), window.location.href).href
      };
    }

    return {
      skuId: skuId,
      name: name,
      price: priceNum,
      image: imageUrl
    };
  }

  function initHomePurchaseCards() {
    document.querySelectorAll(".products-purchase-card").forEach(function (card) {
      var select = card.querySelector(".home-card-sku");
      var qty = card.querySelector(".home-card-quantity");
      var button = card.querySelector(".home-card-add");
      var message = card.querySelector(".home-card-message");
      var dropdown = card.querySelector(".home-sku-dropdown");
      var trigger = dropdown.querySelector(".home-sku-trigger");
      var menu = dropdown.querySelector(".home-sku-menu");
      var minus = card.querySelector(".home-qty-minus");
      var plus = card.querySelector(".home-qty-plus");
      select.hidden = true;
      dropdown.hidden = false;
      function setOpen(open) {
        dropdown.classList.toggle("is-open", open);
        trigger.setAttribute("aria-expanded", String(open));
      }
      function updateQuantityButtons() {
        minus.disabled = qty.disabled || Number(qty.value) <= 1;
        plus.disabled = qty.disabled || Number(qty.value) >= Number(qty.max);
      }
      function updateTotalPrice() {
        var option = select.options[select.selectedIndex];
        var price = card.querySelector(".home-card-price");
        var total = Number(option ? option.getAttribute("data-price") : 0) * Math.max(1, Number(qty.value) || 1);
        price.textContent = qty.validity.valid ? "NT$ " + total.toLocaleString("zh-TW") : "NT$ —";
        price.title = price.textContent;
      }
      function showQuantityNotice() {
        var option = select.options[select.selectedIndex];
        message.textContent = quantityNotice(option && option.getAttribute("data-sku-status"), Number(qty.value));
        message.hidden = !message.textContent;
      }
      function changeQuantity(amount) {
        qty.value = String(Math.min(Math.max(1, Number(qty.max)), Math.max(1, Math.floor(Number(qty.value) || 1) + amount)));
        updateQuantityButtons();
        updateTotalPrice();
        showQuantityNotice();
      }
      dropdown.addEventListener("mouseenter", function () { setOpen(true); });
      dropdown.addEventListener("mouseleave", function () { setOpen(false); });
      trigger.addEventListener("click", function () { setOpen(true); });
      dropdown.addEventListener("keydown", function (event) {
        if (event.key === "Escape") { setOpen(false); trigger.focus(); }
      });
      document.addEventListener("click", function (event) {
        if (!dropdown.contains(event.target)) setOpen(false);
      });
      menu.querySelectorAll("button").forEach(function (item) {
        item.addEventListener("click", function () {
          select.value = item.getAttribute("data-sku-value");
          update();
          setOpen(false);
          trigger.focus();
        });
      });
      minus.addEventListener("click", function () { changeQuantity(-1); });
      plus.addEventListener("click", function () { changeQuantity(1); });
      function update() {
        var option = select.options[select.selectedIndex];
        if (!option) { button.disabled = true; return; }
        var stock = Math.max(0, Number(option.getAttribute("data-stock")) || 0);
        card.setAttribute("data-commerce-sku-id", option.value);
          card.querySelector(".products-item-image").src = option.getAttribute("data-image-url");
          card.dispatchEvent(new Event("product-sku-change"));
        qty.max = String(stock); qty.disabled = stock === 0;
        qty.value = String(Math.min(Math.max(1, Math.floor(Number(qty.value) || 1)), Math.max(1, stock)));
        button.disabled = stock === 0;
        button.title = stock === 0 ? "目前缺貨" : "加入購物車";
        button.setAttribute("aria-label", button.title);
        dropdown.querySelector(".home-sku-caption").textContent = option.textContent;
        trigger.title = option.textContent;
        menu.querySelectorAll("button").forEach(function (item) { item.setAttribute("aria-pressed", String(item.getAttribute("data-sku-value") === option.value)); });
        updateQuantityButtons();
        updateTotalPrice();
        showQuantityNotice();
      }
      select.addEventListener("change", update);
      qty.addEventListener("input", function () { showQuantityNotice(); updateQuantityButtons(); updateTotalPrice(); });
      qty.addEventListener("change", function () { changeQuantity(0); });
      update();
    });
  }

  function initProductImageCarousels() {
    var slideshows = [];
    document.querySelectorAll(".products-card-link").forEach(function (card) {
      var sources = card.querySelectorAll("[data-carousel-url]");
      var image = card.querySelector(".products-item-image");
      if (!sources.length || !image) return;
      var wrapper = image.parentElement;
      wrapper.style.position = "relative";
      wrapper.style.overflow = "hidden";
      var state = { image: image, urls: [], index: 0, busy: false, generation: 0, next: null, due: Date.now() + 5000 };
      function reset() {
        state.generation++;
        state.image.getAnimations().forEach(function (animation) { animation.cancel(); });
        if (state.next) { state.next.remove(); state.next = null; }
        state.busy = false;
        var selected = card.querySelector(".home-card-sku");
        state.urls = Array.from(sources).filter(function (source) {
          return !selected || source.getAttribute("data-carousel-sku") === selected.value;
        }).map(function (source) { return new URL(source.getAttribute("data-carousel-url"), window.location.href).href; });
        state.index = Math.max(0, state.urls.indexOf(state.image.src));
        state.due = Date.now() + 5000;
      }
      card.addEventListener("product-sku-change", reset);
      reset();
      slideshows.push(state);
    });
    if (!slideshows.length) return;
    function advance() {
      if (document.hidden) return;
      slideshows.forEach(function (state) {
        if (state.urls.length < 2 || state.busy || Date.now() < state.due) return;
        var rect = state.image.getBoundingClientRect();
        if (!rect.width || !rect.height || rect.bottom <= 0 || rect.top >= window.innerHeight || rect.right <= 0 || rect.left >= window.innerWidth) return;
        state.busy = true;
        var generation = state.generation;
        var index = (state.index + 1) % state.urls.length;
        var incoming = new Image();
        state.next = incoming;
        incoming.className = state.image.className;
        incoming.alt = state.image.alt;
        incoming.setAttribute("aria-hidden", "true");
        incoming.style.cssText = "position:absolute;inset:0;width:100%;height:100%;object-fit:" + getComputedStyle(state.image).objectFit;
        incoming.onload = function () {
          if (generation !== state.generation) return;
          state.image.parentElement.appendChild(incoming);
          var options = { duration: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 400, easing: "ease-in-out", fill: "forwards" };
          var outgoingAnimation = state.image.animate([{ transform: "translateX(0)" }, { transform: "translateX(-100%)" }], options);
          var incomingAnimation = incoming.animate([{ transform: "translateX(100%)" }, { transform: "translateX(0)" }], options);
          incomingAnimation.finished.then(function () {
            if (generation !== state.generation) return;
            state.image.src = incoming.src;
            outgoingAnimation.cancel();
            incoming.remove();
            state.next = null;
            state.index = index;
            state.busy = false;
          }).catch(function () {});
        };
        incoming.onerror = function () {
          if (generation !== state.generation) return;
          incoming.remove(); state.next = null; state.busy = false;
        };
        state.due = Date.now() + 5000;
        incoming.src = state.urls[index];
      });
    }
    var timer = setInterval(advance, 500);
    window.addEventListener("pagehide", function () { clearInterval(timer); });
    window.addEventListener("pageshow", function (event) { if (event.persisted) timer = setInterval(advance, 500); });
  }

  // Small quick-add cart icon overlaid on the bottom-right corner of every
  // product listing card (home page, category pages, all-products), next to
  // the price. Runs once at page load since these cards are static HTML.
  function injectCardAddToCartButtons() {
    document.querySelectorAll(".products-card-link").forEach(function (card) {
      if (card.querySelector(".card-add-to-cart-btn") || card.getAttribute("data-disable-quick-add") === "true") return;

      var btn = document.createElement("button");
      btn.type = "button";
      btn.className = "card-add-to-cart-btn";
      btn.setAttribute("aria-label", "加入購物車");
      btn.innerHTML =
        '<svg viewBox="0 0 17 17" width="15" height="15" fill="none" xmlns="http://www.w3.org/2000/svg">' +
        '<path d="M2.60592789,2 L0,2 L0,0 L4.39407211,0 L4.84288393,4 L16,4 L16,9.93844589 L3.76940945,12.3694378 L2.60592789,2 Z ' +
        'M15.5,17 C14.6715729,17 14,16.3284271 14,15.5 C14,14.6715729 14.6715729,14 15.5,14 C16.3284271,14 17,14.6715729 17,15.5 ' +
        'C17,16.3284271 16.3284271,17 15.5,17 Z M5.5,17 C4.67157288,17 4,16.3284271 4,15.5 C4,14.6715729 4.67157288,14 5.5,14 ' +
        'C6.32842712,14 7,14.6715729 7,15.5 C7,16.3284271 6.32842712,17 5.5,17 Z" fill="currentColor" fill-rule="evenodd"/>' +
        "</svg>";

      card.appendChild(btn);
    });
  }

  // "移除全部" sits to the left of "前往結帳" in the cart drawer footer.
  // The checkout button itself is static markup (only its disabled class
  // toggles on each render), so this only needs to run once at startup
  // rather than on every renderCartList() pass.
  function injectClearCartButton() {
    document.querySelectorAll(".w-commerce-commercecartcheckoutbutton").forEach(function (checkoutBtn) {
      var wrapper = checkoutBtn.parentElement;
      if (!wrapper || wrapper.querySelector(".cart-clear-all-btn")) return;

      wrapper.classList.add("cart-footer-actions");

      var btn = document.createElement("button");
      btn.type = "button";
      btn.className = "cart-clear-all-btn";
      btn.setAttribute("data-cart-action", "clear-all");
      btn.textContent = "移除全部";

      wrapper.insertBefore(btn, checkoutBtn);
    });
  }

  // ---- member navigation -------------------------------------------------

  // 所有前台頁面的深度不同，因此從網址中的 /front/ 反推專案路徑，
  // 確保首頁、商品頁與登入頁都會呼叫到同一組會員 API。
  function getContextPath() {
    var frontIndex = window.location.pathname.indexOf("/front/");
    return frontIndex >= 0 ? window.location.pathname.substring(0, frontIndex) : "";
  }

  function findLoginLinks() {
    return Array.prototype.filter.call(document.querySelectorAll(".nav-link"), function (link) {
      return link.textContent.trim() === "會員註冊/登入";
    });
  }

  // 會員選單是獨立元件，動態載入自己的樣式即可套用到所有前台頁面，
  // 不必逐一修改每位組員維護的 HTML 檔案。
  function loadMemberMenuStyles() {
    if (document.querySelector('link[data-member-menu-styles]')) return;
    var stylesheet = document.createElement("link");
    stylesheet.rel = "stylesheet";
    stylesheet.href = getContextPath() + "/front/css/member-menu.css?v=2";
    stylesheet.setAttribute("data-member-menu-styles", "");
    document.head.appendChild(stylesheet);
  }

  function closeMemberMenus(restoreFocus) {
    document.querySelectorAll(".member-menu.is-open").forEach(function (menu) {
      menu.classList.remove("is-open");
      var trigger = menu.querySelector(".member-menu-trigger");
      var dropdown = menu.querySelector(".member-menu-dropdown");
      if (trigger) trigger.setAttribute("aria-expanded", "false");
      if (dropdown) dropdown.hidden = true;
      if (restoreFocus && trigger) trigger.focus();
    });
  }

  // 登入成功後以會員圖示取代原本的登入連結，並顯示 session 中的
  // 姓名與會員編號；不將完整個資放進每一頁的導覽列。
  function renderMemberMenu(loginLink, member) {
    var menu = document.createElement("div");
    menu.className = "member-menu";

    var trigger = document.createElement("button");
    trigger.type = "button";
    trigger.className = "member-menu-trigger";
    trigger.setAttribute("aria-label", "開啟會員選單");
    trigger.setAttribute("aria-haspopup", "menu");
    trigger.setAttribute("aria-expanded", "false");
    trigger.innerHTML =
      '<svg aria-hidden="true" viewBox="0 0 24 24" width="22" height="22" fill="none" xmlns="http://www.w3.org/2000/svg">' +
      '<path d="M20 21a8 8 0 0 0-16 0M12 13a5 5 0 1 0 0-10 5 5 0 0 0 0 10Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>' +
      "</svg>";

    var dropdown = document.createElement("div");
    dropdown.className = "member-menu-dropdown";
    dropdown.setAttribute("role", "menu");
    dropdown.hidden = true;

    var profile = document.createElement("div");
    profile.className = "member-menu-profile";

    var name = document.createElement("div");
    name.className = "member-menu-name";
    name.textContent = member.memberName || "鮮果鋪會員";

    var number = document.createElement("div");
    number.className = "member-menu-number";
    number.textContent = "會員 #" + member.memberId;

    var profileLink = document.createElement("a");
    profileLink.className = "member-menu-profile-link member-menu-link";
    profileLink.href = getContextPath() + "/front/about/member/";
    profileLink.setAttribute("role", "menuitem");
    profileLink.innerHTML =
      '<svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" xmlns="http://www.w3.org/2000/svg">' +
      '<path d="M4 20v-2a4 4 0 0 1 4-4h4M10 10a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM16 13l2 2 4-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>' +
      "</svg><span>會員資料</span>";

    // 三個常用資料入口共用同一個管理頁，query string 會直接開啟對應分頁。
    var addressLink = document.createElement("a");
    addressLink.className = "member-menu-link";
    addressLink.href = getContextPath() + "/front/about/member/manage/?view=address";
    addressLink.setAttribute("role", "menuitem");
    addressLink.innerHTML =
      '<svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" xmlns="http://www.w3.org/2000/svg">' +
      '<path d="M12 21s7-5.1 7-12A7 7 0 1 0 5 9c0 6.9 7 12 7 12Zm0-9a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>' +
      "</svg><span>我的地址</span>";

    var cardLink = document.createElement("a");
    cardLink.className = "member-menu-link";
    cardLink.href = getContextPath() + "/front/about/member/manage/?view=card";
    cardLink.setAttribute("role", "menuitem");
    cardLink.innerHTML =
      '<svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" xmlns="http://www.w3.org/2000/svg">' +
      '<path d="M3 6.5h18M3 10h18M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Zm2 12h4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>' +
      "</svg><span>我的信用卡</span>";

    var phoneLink = document.createElement("a");
    phoneLink.className = "member-menu-link";
    phoneLink.href = getContextPath() + "/front/about/member/manage/?view=phone";
    phoneLink.setAttribute("role", "menuitem");
    phoneLink.innerHTML =
      '<svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" xmlns="http://www.w3.org/2000/svg">' +
      '<path d="M7.5 3h-3A1.5 1.5 0 0 0 3 4.5C3 13.6 10.4 21 19.5 21a1.5 1.5 0 0 0 1.5-1.5v-3l-4-1-1.5 2a14.2 14.2 0 0 1-9-9L8.5 7l-1-4Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>' +
      "</svg><span>我的電話</span>";

    var logout = document.createElement("button");
    logout.type = "button";
    logout.className = "member-menu-logout";
    logout.setAttribute("role", "menuitem");
    logout.setAttribute("data-member-action", "logout");
    logout.innerHTML =
      '<svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" xmlns="http://www.w3.org/2000/svg">' +
      '<path d="M10 17l5-5-5-5M15 12H3M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>' +
      "</svg><span>登出</span>";

    profile.appendChild(name);
    profile.appendChild(number);
    dropdown.appendChild(profile);
    dropdown.appendChild(profileLink);
    dropdown.appendChild(addressLink);
    dropdown.appendChild(cardLink);
    dropdown.appendChild(phoneLink);
    dropdown.appendChild(logout);
    menu.appendChild(trigger);
    menu.appendChild(dropdown);
    loginLink.parentNode.replaceChild(menu, loginLink);
  }

  function initMemberMenu() {
    var loginLinks = findLoginLinks();
    if (!loginLinks.length) return;
    loadMemberMenuStyles();

    fetch(getContextPath() + "/api/members/session", {
      method: "GET",
      credentials: "same-origin",
      headers: { "Accept": "application/json" }
    })
      .then(function (response) {
        if (!response.ok) throw new Error("Unable to read member session");
        return response.json();
      })
      .then(function (member) {
        if (!member.loggedIn) return;
        loginLinks.forEach(function (link) {
          renderMemberMenu(link, member);
        });
      })
      .catch(function () {
        // Session 狀態查詢失敗時保留原登入連結，避免導覽列失去入口。
      });
  }

  function logoutMember(button) {
    button.disabled = true;
    button.querySelector("span").textContent = "登出中...";

    fetch(getContextPath() + "/api/members/logout", {
      method: "POST",
      credentials: "same-origin"
    })
      .then(function (response) {
        if (!response.ok) throw new Error("Logout failed");
        window.location.href = getContextPath() + "/front/";
      })
      .catch(function () {
        button.disabled = false;
        button.classList.add("is-error");
        button.querySelector("span").textContent = "登出失敗，請再試一次";
      });
  }

  function bindEvents() {
    document.addEventListener("click", function (e) {
      var memberTrigger = e.target.closest(".member-menu-trigger");
      if (memberTrigger) {
        e.preventDefault();
        var memberMenu = memberTrigger.closest(".member-menu");
        var memberDropdown = memberMenu.querySelector(".member-menu-dropdown");
        var willOpen = !memberMenu.classList.contains("is-open");
        closeMemberMenus(false);
        memberMenu.classList.toggle("is-open", willOpen);
        memberTrigger.setAttribute("aria-expanded", String(willOpen));
        memberDropdown.hidden = !willOpen;
        return;
      }

      var memberLogout = e.target.closest('[data-member-action="logout"]');
      if (memberLogout) {
        e.preventDefault();
        logoutMember(memberLogout);
        return;
      }

      if (!e.target.closest(".member-menu")) closeMemberMenus(false);

      var buyNowBtn = e.target.closest(".w-commerce-commercebuynowbutton");
      if (buyNowBtn) {
        e.preventDefault();
        // No preventDefault: its href already points at checkout/ from the
        // right relative depth, so just stage the cart data first (this
        // runs synchronously before the browser follows the link) and let
        // the normal navigation happen.
        var buyForm = buyNowBtn.closest('[data-node-type="x-commerce-add-to-cart-form"]');
        if (buyForm) {
          var buySkuId = buyForm.getAttribute("data-commerce-sku-id") || buyForm.getAttribute("data-commerce-product-id");
          var buyQtyInput = buyForm.querySelector('input[name="commerce-add-to-cart-quantity-input"]');
          var buyQty = Math.max(1, parseInt(buyQtyInput && buyQtyInput.value, 10) || 1);
          var buyProduct = readProductFromForm(buyForm);

          buyProduct.skuId = buySkuId;
          addVerifiedProduct(buyProduct, buyQty, buyNowBtn, null, buyNowBtn.href);
        }
        return;
      }

      var disabledCheckoutBtn = e.target.closest(".w-commerce-commercecartcheckoutbutton.is-cart-checkout-disabled");
      if (disabledCheckoutBtn) {
        // Belt-and-braces: CSS (pointer-events: none) already blocks mouse
        // clicks, this covers keyboard activation (Enter/Space on a
        // focused link) too.
        e.preventDefault();
        return;
      }

      var cardAddBtn = e.target.closest(".card-add-to-cart-btn");
      if (cardAddBtn) {
        e.preventDefault(); // the button sits inside a <a class="products-card-link">
        var card = cardAddBtn.closest(".products-card-link");
        if (card) {
          var qtyInput = card.querySelector(".home-card-quantity");
          if (cardAddBtn.disabled) return;
          if (qtyInput && !qtyInput.reportValidity()) return;
          var product = readProductFromCard(card);
          var quantity = qtyInput ? Number(qtyInput.value) : 1;
          addVerifiedProduct(product, quantity, cardAddBtn, card.querySelector(".home-card-message"));
        }
        return;
      }

      var openLink = e.target.closest(".w-commerce-commercecartopenlink");
      if (openLink) {
        e.preventDefault();
        openCart();
        return;
      }

      var closeLink = e.target.closest(".w-commerce-commercecartcloselink");
      if (closeLink) {
        e.preventDefault();
        closeCart();
        return;
      }

      // click on the dimmed backdrop (the wrapper itself, not its content) closes the cart
      if (e.target.classList && e.target.classList.contains("w-commerce-commercecartcontainerwrapper")) {
        closeCart();
        return;
      }

      var decBtn = e.target.closest('[data-cart-action="dec"]');
      if (decBtn) {
        var decRow = decBtn.closest("[data-cart-row]");
        var decInput = decRow.querySelector(".cart-qty-input");
        setQty(decRow.getAttribute("data-cart-row"), Math.max(0, (parseInt(decInput.value, 10) || 0) - 1));
        return;
      }

      var incBtn = e.target.closest('[data-cart-action="inc"]');
      if (incBtn) {
        var incRow = incBtn.closest("[data-cart-row]");
        var incInput = incRow.querySelector(".cart-qty-input");
        setQty(incRow.getAttribute("data-cart-row"), (parseInt(incInput.value, 10) || 0) + 1);
        return;
      }

      var removeLink = e.target.closest('[data-cart-action="remove"]');
      if (removeLink) {
        e.preventDefault();
        var removeRow = removeLink.closest("[data-cart-row]");
        removeItemAnimated(removeRow.getAttribute("data-cart-row"));
        return;
      }

      var clearAllBtn = e.target.closest('[data-cart-action="clear-all"]');
      if (clearAllBtn) {
        writeCart([]);
        renderAll();
        return;
      }
    });

    document.addEventListener("change", function (e) {
      if (e.target.classList && e.target.classList.contains("cart-qty-input")) {
        var row = e.target.closest("[data-cart-row]");
        setQty(row.getAttribute("data-cart-row"), Math.max(0, parseInt(e.target.value, 10) || 0));
      }
      if (e.target.classList && e.target.classList.contains("cart-item-checkbox")) {
        var checkRow = e.target.closest("[data-cart-row]");
        setChecked(checkRow.getAttribute("data-cart-row"), e.target.checked);
      }
    });

    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape") {
        closeCart();
        closeMemberMenus(true);
      }
    });

    document.addEventListener("submit", function (e) {
      var form = e.target.closest('[data-node-type="x-commerce-add-to-cart-form"]');
      if (!form) return;
      e.preventDefault();

      var skuId = form.getAttribute("data-commerce-sku-id") || form.getAttribute("data-commerce-product-id");
      var qtyInput = form.querySelector('input[name="commerce-add-to-cart-quantity-input"]');
      var qty = Math.max(1, parseInt(qtyInput && qtyInput.value, 10) || 1);
      var product = readProductFromForm(form);

      product.skuId = skuId;
      addVerifiedProduct(product, qty, form.querySelector('[type="submit"]'), null);
    });
  }

  neutralizeWebflowCommerce();
  bindEvents();
  renderAll();
  initHomePurchaseCards();
  initProductImageCarousels();
  injectCardAddToCartButtons();
  injectClearCartButton();
  initMemberMenu();
  if (window.location.pathname.indexOf("/front/checkout/") >= 0) {
    verifyCart().catch(function () { window.alert("無法確認最新商品資料，請稍後再試。"); });
    if (new URLSearchParams(window.location.search).get("error") === "products")
      window.alert("商品價格、庫存或狀態已變更，請確認購物車後重新結帳。");
  }

  // Small public surface so standalone pages (e.g. checkout/confirm/) that
  // don't need the full cart sidebar can still read/clear the same
  // localStorage-backed cart and format money consistently.
  window.SSXCart = {
    readCart: readCart,
    getSubtotal: getSubtotal,
    getCheckedItems: getCheckedItems,
    formatMoney: formatMoney,
    // Exposed so pages that build extra .products-card-link cards after
    // this script's own startup pass (e.g. product/view/'s related-items
    // grid) can request the quick-add icon for those new cards too.
    injectCardAddToCartButtons: injectCardAddToCartButtons,
    verify: verifyCart,
    quantityNotice: quantityNotice,
    clear: function () {
      writeCart([]);
      renderAll();
    },
    // Removes just the given skuIds (e.g. the items that were actually
    // checked out), leaving any unchecked/untouched items in the cart.
    removeItems: function (skuIds) {
      var idSet = {};
      skuIds.forEach(function (id) {
        idSet[id] = true;
      });
      writeCart(readCart().filter(function (item) {
        return !idSet[item.skuId];
      }));
      renderAll();
    }
  };
})();
