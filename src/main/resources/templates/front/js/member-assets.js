(function() {
  "use strict";

  var contextPath = window.location.pathname.split("/front/")[0];
  var allowedViews = ["address", "card", "phone"];
  var dataByView = { address: [], card: [], phone: [] };
  var endpoints = {
    address: "/api/members/me/addresses",
    card: "/api/members/me/credit-cards",
    phone: "/api/members/me/phones"
  };
  var labels = {
    address: { add: "新增地址", update: "儲存地址", empty: "目前還沒有常用地址，請從上方新增第一筆。" },
    card: { add: "新增信用卡", update: "儲存信用卡", empty: "目前還沒有信用卡資料，請從上方新增第一張。" },
    phone: { add: "新增電話", update: "儲存電話", empty: "目前還沒有常用電話，請從上方新增第一筆。" }
  };

  function currentView() {
    var value = new URLSearchParams(window.location.search).get("view");
    return allowedViews.indexOf(value) >= 0 ? value : "address";
  }

  function redirectToLogin() {
    window.location.replace(contextPath + "/front/about/login/?next=" + encodeURIComponent(window.location.pathname + window.location.search));
  }

  function readResponse(response) {
    if (response.status === 204) {
      return Promise.resolve({ ok: true, status: response.status, body: null });
    }
    return response.json().catch(function() { return {}; }).then(function(body) {
      return { ok: response.ok, status: response.status, body: body };
    });
  }

  function apiRequest(path, options) {
    var requestOptions = options || {};
    requestOptions.credentials = "same-origin";
    requestOptions.headers = Object.assign({ "Accept": "application/json" }, requestOptions.headers || {});
    return fetch(contextPath + path, requestOptions).then(readResponse).then(function(result) {
      if (result.status === 401) {
        redirectToLogin();
        throw new Error("請先登入會員");
      }
      if (!result.ok) {
        throw new Error(result.body && result.body.message ? result.body.message : "資料處理失敗");
      }
      return result.body;
    });
  }

  function showFeedback(view, message, type) {
    var feedback = document.querySelector('[data-form="' + view + '"] [data-feedback]');
    feedback.textContent = message || "";
    feedback.className = "asset-feedback" + (type ? " is-" + type : "");
  }

  function setView(view, updateUrl) {
    if (allowedViews.indexOf(view) < 0) view = "address";
    document.querySelectorAll("[data-view]").forEach(function(tab) {
      var active = tab.getAttribute("data-view") === view;
      tab.classList.toggle("is-active", active);
      if (active) tab.setAttribute("aria-current", "page");
      else tab.removeAttribute("aria-current");
    });
    document.querySelectorAll("[data-panel]").forEach(function(panel) {
      panel.hidden = panel.getAttribute("data-panel") !== view;
    });
    if (updateUrl) {
      var url = new URL(window.location.href);
      url.searchParams.set("view", view);
      window.history.pushState({}, "", url);
    }
  }

  function idFor(view, item) {
    if (view === "address") return item.addressId;
    if (view === "card") return item.creditCardId;
    return item.phoneId;
  }

  function payloadFor(view, item, makeDefault) {
    if (view === "address") {
      return { contactAddress: item.contactAddress, makeDefault: makeDefault };
    }
    if (view === "phone") {
      return { contactPhone: item.contactPhone, makeDefault: makeDefault };
    }
    return {
      cardBrand: item.cardBrand,
      cardLastFour: item.cardLastFour,
      cardholderName: item.cardholderName,
      expiryYearMonth: item.expiryYearMonth,
      makeDefault: makeDefault
    };
  }

  function formPayload(view) {
    var form = document.querySelector('[data-form="' + view + '"]');
    var makeDefault = form.querySelector("[data-default]").checked;
    if (view === "address") {
      return { contactAddress: document.getElementById("contact-address").value.trim(), makeDefault: makeDefault };
    }
    if (view === "phone") {
      return { contactPhone: document.getElementById("contact-phone").value.trim(), makeDefault: makeDefault };
    }
    return {
      cardBrand: document.getElementById("card-brand").value.trim(),
      cardLastFour: document.getElementById("card-last-four").value.trim(),
      cardholderName: document.getElementById("card-holder").value.trim(),
      expiryYearMonth: document.getElementById("card-expiry").value.trim(),
      makeDefault: makeDefault
    };
  }

  function resetForm(view) {
    var form = document.querySelector('[data-form="' + view + '"]');
    form.reset();
    form.querySelectorAll(".asset-field-error").forEach(function(el) { el.textContent = ""; });
    form.querySelector("[data-id]").value = "";
    form.querySelector("[data-cancel]").hidden = true;
    form.querySelector("[data-save]").textContent = labels[view].add;
  }

  function editItem(view, item) {
    var form = document.querySelector('[data-form="' + view + '"]');
    form.querySelector("[data-id]").value = idFor(view, item);
    form.querySelector("[data-default]").checked = item.defaultValue === 1;
    form.querySelector("[data-cancel]").hidden = false;
    form.querySelector("[data-save]").textContent = labels[view].update;
    if (view === "address") document.getElementById("contact-address").value = item.contactAddress;
    if (view === "phone") document.getElementById("contact-phone").value = item.contactPhone;
    if (view === "card") {
      document.getElementById("card-brand").value = item.cardBrand;
      document.getElementById("card-last-four").value = item.cardLastFour;
      document.getElementById("card-holder").value = item.cardholderName;
      document.getElementById("card-expiry").value = item.expiryYearMonth;
    }
    showFeedback(view, "正在編輯這筆資料", "");
    form.scrollIntoView({ behavior: "smooth", block: "center" });
  }

  function displayText(view, item) {
    if (view === "address") return { title: item.contactAddress, meta: "常用收件地址" };
    if (view === "phone") return { title: item.contactPhone, meta: "常用聯絡電話" };
    var expiry = item.expiryYearMonth || "";
    return {
      title: item.cardBrand + " •••• " + item.cardLastFour,
      meta: item.cardholderName + "　到期 20" + expiry.slice(0, 2) + "/" + expiry.slice(2)
    };
  }

  function makeButton(text, className, handler) {
    var button = document.createElement("button");
    button.type = "button";
    button.className = "asset-row-button" + (className ? " " + className : "");
    button.textContent = text;
    button.addEventListener("click", handler);
    return button;
  }

  function setDefault(view, item, button) {
    button.disabled = true;
    apiRequest(endpoints[view] + "/" + idFor(view, item), {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payloadFor(view, item, true))
    }).then(function() {
      showFeedback(view, "已設為預設資料", "success");
      return loadView(view);
    }).catch(function(error) {
      showFeedback(view, error.message, "error");
      button.disabled = false;
    });
  }

  function deleteItem(view, item, button) {
    if (!button.classList.contains("is-confirming")) {
      button.classList.add("is-confirming");
      button.textContent = "確認刪除";
      showFeedback(view, "再按一次「確認刪除」即可刪除這筆資料", "");
      window.setTimeout(function() {
        button.classList.remove("is-confirming");
        button.textContent = "刪除";
      }, 5000);
      return;
    }
    button.disabled = true;
    apiRequest(endpoints[view] + "/" + idFor(view, item), { method: "DELETE" })
      .then(function() {
        resetForm(view);
        showFeedback(view, "資料已刪除", "success");
        return loadView(view);
      }).catch(function(error) {
        showFeedback(view, error.message, "error");
        button.disabled = false;
      });
  }

  function renderList(view) {
    var list = document.querySelector('[data-list="' + view + '"]');
    var items = dataByView[view];
    list.replaceChildren();
    document.getElementById(view + "-count").textContent = items.length;
    if (!items.length) {
      var empty = document.createElement("div");
      empty.className = "asset-empty";
      empty.textContent = labels[view].empty;
      list.appendChild(empty);
      return;
    }

    items.forEach(function(item) {
      var row = document.createElement("article");
      row.className = "asset-row" + (item.defaultValue === 1 ? " is-default" : "");
      var content = document.createElement("div");
      content.className = "asset-row-content";
      var title = document.createElement("p");
      title.className = "asset-row-title";
      title.textContent = displayText(view, item).title;
      if (item.defaultValue === 1) {
        var defaultLabel = document.createElement("span");
        defaultLabel.className = "asset-default-label";
        defaultLabel.textContent = "預設";
        title.appendChild(defaultLabel);
      }
      var meta = document.createElement("p");
      meta.className = "asset-row-meta";
      meta.textContent = displayText(view, item).meta;
      content.appendChild(title);
      content.appendChild(meta);

      var actions = document.createElement("div");
      actions.className = "asset-row-actions";
      if (item.defaultValue !== 1) {
        actions.appendChild(makeButton("設為預設", "", function(event) {
          setDefault(view, item, event.currentTarget);
        }));
      }
      actions.appendChild(makeButton("編輯", "", function() { editItem(view, item); }));
      actions.appendChild(makeButton("刪除", "is-danger", function(event) {
        deleteItem(view, item, event.currentTarget);
      }));
      row.appendChild(content);
      row.appendChild(actions);
      list.appendChild(row);
    });
  }

  function loadView(view) {
    var list = document.querySelector('[data-list="' + view + '"]');
    list.innerHTML = '<div class="asset-loading">正在讀取資料...</div>';
    return apiRequest(endpoints[view]).then(function(items) {
      dataByView[view] = items;
      renderList(view);
    }).catch(function(error) {
      list.innerHTML = "";
      var failed = document.createElement("div");
      failed.className = "asset-empty";
      failed.textContent = error.message || "目前無法讀取資料";
      list.appendChild(failed);
    });
  }

  // 卡片品牌（大小寫不拘）→ 標準名稱，以及各品牌的卡號開頭與長度規則
  var CARD_BRANDS = {
    visa: { name: "Visa", lengths: [13, 16, 19], prefix: /^4/ },
    mastercard: { name: "Mastercard", lengths: [16], prefix: /^(5[1-5]|222[1-9]|22[3-9]\d|2[3-6]\d{2}|27[01]\d|2720)/ },
    jcb: { name: "JCB", lengths: [16, 17, 18, 19], prefix: /^35(2[89]|[3-8]\d)/ },
    amex: { name: "Amex", lengths: [15], prefix: /^3[47]/ }
  };

  function luhnValid(number) {
    var sum = 0;
    var double = false;
    for (var i = number.length - 1; i >= 0; i--) {
      var digit = number.charCodeAt(i) - 48;
      if (double) {
        digit *= 2;
        if (digit > 9) digit -= 9;
      }
      sum += digit;
      double = !double;
    }
    return sum % 10 === 0;
  }

  // 回傳錯誤訊息；通過回傳 ""。卡號只在瀏覽器檢查，不會送到伺服器
  function checkCardForm(form) {
    var brand = CARD_BRANDS[document.getElementById("card-brand").value.trim().toLowerCase()];
    if (!brand) return { field: "brand", message: "卡片品牌只能輸入 Visa、Mastercard、JCB、Amex" };
    var number = document.getElementById("card-number").value.replace(/[\s-]/g, "");
    var lastFourEl = document.getElementById("card-last-four");
    if (!number) {
      // 編輯時沒重新輸入卡號：只有「品牌與末四碼都沒變」才放行，否則無從驗證
      var original = form.querySelector("[data-id]").value
        ? dataByView.card.filter(function(c) { return String(c.creditCardId) === form.querySelector("[data-id]").value; })[0]
        : null;
      if (original && original.cardBrand.toLowerCase() === brand.name.toLowerCase()) return null;
      return { field: "number", message: "請輸入信用卡卡號，以檢查是否與發卡組織相符" };
    }
    if (!/^\d+$/.test(number)) return { field: "number", message: "卡號只能是數字" };
    if (!luhnValid(number)) return { field: "number", message: "卡號不正確" };
    if (!brand.prefix.test(number) || brand.lengths.indexOf(number.length) < 0) {
      return { field: "number", message: "卡號與所選的發卡組織（" + brand.name + "）不相符" };
    }
    lastFourEl.value = number.slice(-4);
    return null;
  }

  // 品牌的錯誤顯示在品牌欄位下方，卡號的錯誤顯示在卡號欄位下方
  function showCardError(result) {
    document.getElementById("card-brand-error").textContent = result && result.field === "brand" ? result.message : "";
    document.getElementById("card-number-error").textContent = result && result.field === "number" ? result.message : "";
  }

  // 品牌或卡號欄位離開焦點時就即時檢查；還沒輸入的欄位不報「必填」，留到按送出時再提示
  function checkCardOnBlur() {
    var form = document.querySelector('[data-form="card"]');
    var brandText = document.getElementById("card-brand").value.trim();
    var number = document.getElementById("card-number").value.replace(/[\s-]/g, "");
    if ((!brandText && !number) || (!number && CARD_BRANDS[brandText.toLowerCase()])) {
      showCardError(null);
      return;
    }
    showCardError(checkCardForm(form));
  }

  // 卡號輸入時只留數字，並每四碼加一個「-」（1234-5678-9012-3456）；游標位置依「前面有幾個數字」還原
  document.getElementById("card-number").addEventListener("input", function(event) {
    var input = event.target;
    var caret = input.selectionStart;
    var digitsBeforeCaret = input.value.slice(0, caret).replace(/\D/g, "").length;
    var digits = input.value.replace(/\D/g, "").slice(0, 19);
    var formatted = digits.replace(/(\d{4})(?=\d)/g, "$1-");
    input.value = formatted;
    var pos = 0;
    var seen = 0;
    while (pos < formatted.length && seen < digitsBeforeCaret) {
      if (formatted.charAt(pos) !== "-") seen++;
      pos++;
    }
    input.setSelectionRange(pos, pos);
  });

  document.getElementById("card-brand").addEventListener("blur", checkCardOnBlur);
  document.getElementById("card-number").addEventListener("blur", checkCardOnBlur);

  function submitForm(view, form) {
    if (view === "card") {
      var cardError = checkCardForm(form);
      showCardError(cardError);
      if (cardError) {
        document.getElementById(cardError.field === "brand" ? "card-brand" : "card-number").focus();
        return;
      }
      document.getElementById("card-brand").value = CARD_BRANDS[document.getElementById("card-brand").value.trim().toLowerCase()].name;
    }
    if (!form.reportValidity()) return;
    var id = form.querySelector("[data-id]").value;
    var button = form.querySelector("[data-save]");
    button.disabled = true;
    showFeedback(view, id ? "正在儲存變更..." : "正在新增資料...", "");
    apiRequest(endpoints[view] + (id ? "/" + id : ""), {
      method: id ? "PUT" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(formPayload(view))
    }).then(function() {
      resetForm(view);
      showFeedback(view, id ? "資料已更新" : "資料已新增", "success");
      return loadView(view);
    }).catch(function(error) {
      showFeedback(view, error.message, "error");
    }).finally(function() {
      button.disabled = false;
    });
  }

  document.querySelectorAll("[data-view]").forEach(function(tab) {
    tab.addEventListener("click", function(event) {
      event.preventDefault();
      setView(tab.getAttribute("data-view"), true);
    });
  });

  document.querySelectorAll("[data-form]").forEach(function(form) {
    var view = form.getAttribute("data-form");
    form.addEventListener("submit", function(event) {
      event.preventDefault();
      submitForm(view, form);
    });
    form.querySelector("[data-cancel]").addEventListener("click", function() {
      resetForm(view);
      showFeedback(view, "已取消編輯", "");
    });
  });

  window.addEventListener("popstate", function() { setView(currentView(), false); });
  setView(currentView(), false);
  allowedViews.forEach(loadView);
})();
