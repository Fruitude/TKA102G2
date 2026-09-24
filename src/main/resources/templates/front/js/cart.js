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
    return items.reduce(function (sum, item) {
      return sum + item.qty * item.price;
    }, 0);
  }

  // Items have no "checked" field until the first time this feature touches
  // them, so a missing field defaults to checked - existing cart contents
  // stay included in checkout rather than silently dropping out.
  function isChecked(item) {
    return item.checked !== false;
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

    var incBtn = document.createElement("button");
    incBtn.type = "button";
    incBtn.className = "cart-qty-btn";
    incBtn.setAttribute("data-cart-action", "inc");
    incBtn.setAttribute("aria-label", "Increase quantity");
    incBtn.textContent = "+";

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
    name.className = "w-commerce-commerceboldtextblock heading-h5";
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

    descWrapper.appendChild(name);
    descWrapper.appendChild(qtyWrapper);

    row.appendChild(img);
    row.appendChild(descWrapper);
    return row;
  }

  function renderCheckoutSummary(items) {
    var list = document.querySelector(".w-commerce-commercecheckoutorderitemslist");
    if (!list) return; // not on the checkout page

    var checkedItems = getCheckedItems(items);

    list.innerHTML = "";
    checkedItems.forEach(function (item) {
      list.appendChild(buildCheckoutItemRow(item));
    });

    var subtotal = getSubtotal(checkedItems);
    var subtotalEl = document.querySelector('[data-wf-bindings*="commerceOrder.subtotal"]');
    var totalEl = document.querySelector(".w-commerce-commercecheckoutsummarytotal");
    // No shipping/tax modelled in this static cart, so total == subtotal.
    if (subtotalEl) subtotalEl.textContent = formatMoney(subtotal);
    if (totalEl) totalEl.textContent = formatMoney(subtotal);
  }

  function renderAll(opts) {
    var items = readCart();
    renderBadges(items);
    renderCartList(items, opts);
    renderCheckoutSummary(items);
  }

  // ---- open / close sidebar --------------------------------------------

  function openCart() {
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

    var name = nameEl ? nameEl.textContent.trim() : "Product";

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
    var skuId = "card:" + name;

    return {
      skuId: skuId,
      name: name,
      price: priceNum,
      image: imageUrl
    };
  }

  // Small quick-add cart icon overlaid on the bottom-right corner of every
  // product listing card (home page, category pages, all-products), next to
  // the price. Runs once at page load since these cards are static HTML.
  function injectCardAddToCartButtons() {
    document.querySelectorAll(".products-card-link").forEach(function (card) {
      if (card.querySelector(".card-add-to-cart-btn")) return;

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

  function bindEvents() {
    document.addEventListener("click", function (e) {
      var buyNowBtn = e.target.closest(".w-commerce-commercebuynowbutton");
      if (buyNowBtn) {
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

          buyNow({
            skuId: buySkuId,
            qty: buyQty,
            name: buyProduct.name,
            price: buyProduct.price,
            image: buyProduct.image
          });
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
          var product = readProductFromCard(card);
          addToCart({
            skuId: product.skuId,
            qty: 1,
            name: product.name,
            price: product.price,
            image: product.image
          });
          openCart();
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
      if (e.key === "Escape") closeCart();
    });

    document.addEventListener("submit", function (e) {
      var form = e.target.closest('[data-node-type="x-commerce-add-to-cart-form"]');
      if (!form) return;
      e.preventDefault();

      var skuId = form.getAttribute("data-commerce-sku-id") || form.getAttribute("data-commerce-product-id");
      var qtyInput = form.querySelector('input[name="commerce-add-to-cart-quantity-input"]');
      var qty = Math.max(1, parseInt(qtyInput && qtyInput.value, 10) || 1);
      var product = readProductFromForm(form);

      addToCart({
        skuId: skuId,
        qty: qty,
        name: product.name,
        price: product.price,
        image: product.image
      });

      openCart();
    });
  }

  neutralizeWebflowCommerce();
  bindEvents();
  renderAll();
  injectCardAddToCartButtons();
  injectClearCartButton();

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
