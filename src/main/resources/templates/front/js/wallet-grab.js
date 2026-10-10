/* 搶購物金（活動總覽頁）：搶購區塊放在該活動的卡片裡（倒數、名額、搶購按鈕、結果），
 * 右上角另有一個小標籤顯示會員購物金與最近一場活動的倒數。
 * 倒數以伺服器時間為準：載入時用 serverNow 與本機時間算出時差，之後每次都用「校正後的現在時間」重算剩餘時間，
 * 不靠累減，所以不會越走越偏；每 30 秒重新向伺服器對時。 */
(function () {
  "use strict";

  var ctx = (function () {
    var i = window.location.pathname.indexOf("/front/");
    return i > 0 ? window.location.pathname.substring(0, i) : "";
  })();
  var STATE_URL = ctx + "/api/promos/grab/state";
  var MIN_DRAW_MS = 1500; // 抽選動畫最短時間
  var RESYNC_MS = 30000;

  var chip = null;
  var chipLine = null;
  var offset = 0; // 伺服器時間 − 本機時間（毫秒）
  var state = null;
  var blocks = []; // { event, root, countdown, button, message, info }
  var busy = false;
  var timer = null;

  function now() { return Date.now() + offset; }
  function pad(n) { return (n < 10 ? "0" : "") + n; }

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }

  function formatLeft(ms) {
    var total = Math.max(0, Math.ceil(ms / 1000));
    var d = Math.floor(total / 86400);
    var h = Math.floor(total % 86400 / 3600);
    var m = Math.floor(total % 3600 / 60);
    var s = total % 60;
    return d + " 天 " + pad(h) + " 時 " + pad(m) + " 分 " + pad(s) + " 秒";
  }

  function fetchState() {
    var t0 = Date.now();
    return fetch(STATE_URL, { cache: "no-store", credentials: "same-origin" })
      .then(function (response) {
        if (!response.ok) throw new Error("state " + response.status);
        return response.json();
      })
      .then(function (data) {
        var t1 = Date.now();
        offset = data.serverNow - (t0 + t1) / 2; // 扣掉來回一半的網路時間
        state = data;
        return data;
      });
  }

  function loginHref() {
    return ctx + "/front/about/login/?next=" + encodeURIComponent(window.location.pathname + window.location.search);
  }

  // ---- 右上角小標籤：購物金與最近一場活動的倒數 ------------------------------

  function renderChip() {
    if (!state || !state.events || !state.events.length) {
      if (chip) { chip.remove(); chip = null; chipLine = null; }
      return;
    }
    if (!chip) {
      chip = el("aside", "wallet-grab-chip");
      document.body.appendChild(chip);
    }
    chip.textContent = "";
    if (state.loggedIn) {
      chip.appendChild(document.createTextNode("我的購物金 "));
      chip.appendChild(el("strong", "wallet-grab-credit-value", String(state.shoppingCredit)));
      chip.appendChild(document.createTextNode(" 點"));
    } else {
      var link = el("a", "wallet-grab-login", "登入後參加搶購");
      link.href = loginHref();
      chip.appendChild(link);
    }
    chipLine = el("div", "wallet-grab-chip-countdown");
    chip.appendChild(chipLine);
  }

  // ---- 活動卡片裡的搶購區塊 ---------------------------------------------------

  function clearMounted() {
    document.querySelectorAll(".wallet-grab-box").forEach(function (node) { node.remove(); });
    document.querySelectorAll(".wallet-grab-card").forEach(function (node) { node.remove(); });
    blocks = [];
  }

  // 頁面上已經有這場活動的卡片就放在卡片裡；還沒開始的活動頁面上沒有卡片，另外建一張放在限時活動最上面
  function cardFor(event) {
    var card = document.getElementById("promo-" + event.promoProjectId);
    if (card) return card;
    var panelNode = document.querySelector('.asset-panel[data-panel="limited"]');
    if (!panelNode) return null;
    card = el("article", "promo-page-card wallet-grab-card");
    card.id = "promo-" + event.promoProjectId;
    var head = el("div", "promo-page-head");
    var left = el("div");
    left.appendChild(el("div", "promo-page-type", "搶購物金"));
    left.appendChild(el("h3", "heading-h4 promo-page-name", event.title));
    head.appendChild(left);
    head.appendChild(el("div", "promo-page-benefit", "贈送 " + event.benefitValue + " 點"));
    card.appendChild(head);
    var heading = panelNode.querySelector(".asset-panel-heading");
    if (heading && heading.nextSibling) panelNode.insertBefore(card, heading.nextSibling);
    else panelNode.appendChild(card);
    // 「目前沒有進行中的限時活動」的說明不再適用
    Array.prototype.forEach.call(panelNode.children, function (child) {
      if (child.tagName === "P") child.hidden = true;
    });
    return card;
  }

  function render() {
    clearMounted();
    renderChip();
    if (!state || !state.events || !state.events.length) return;

    state.events.forEach(function (event) {
      var card = cardFor(event);
      if (!card) return;
      var root = el("div", "wallet-grab-box");
      root.appendChild(el("div", "wallet-grab-box-title", "搶購物金"));
      var info = el("div", "wallet-grab-info");
      root.appendChild(info);
      var countdown = el("div", "wallet-grab-countdown");
      root.appendChild(countdown);
      var button = el("button", "wallet-grab-button primary-button", "搶購物金");
      button.type = "button";
      root.appendChild(button);
      var message = el("p", "wallet-grab-message");
      message.setAttribute("role", "status");
      root.appendChild(message);
      var block = { event: event, root: root, countdown: countdown, button: button, message: message, info: info };
      button.addEventListener("click", function () { grab(block); });
      var actions = card.querySelector(".promo-page-actions");
      if (actions) card.insertBefore(root, actions); else card.appendChild(root);
      blocks.push(block);
    });
    tick();
  }

  function resultText(block) {
    var r = block.event.myResult;
    if (r === "WON") return "你已搶到這場活動的購物金（第 " + block.event.mySlotNo + " 名）";
    if (r === "LOST") return "你已參加過這場活動，這次沒有搶到";
    return "";
  }

  function tick() {
    var next = Infinity;
    var t = now();
    var chipText = "";
    blocks.forEach(function (block) {
      var e = block.event;
      var untilStart = e.startMillis - t;
      var untilEnd = e.endMillis - t;
      var full = e.granted >= e.quota;
      block.info.textContent = "贈送 " + e.benefitValue + " 點　名額 " + Math.min(e.granted, e.quota) + " / " + e.quota;

      if (untilStart > 0) {
        block.countdown.textContent = "距離開始 " + formatLeft(untilStart);
        block.button.textContent = "尚未開始";
        block.button.disabled = true;
        if (!chipText) chipText = "距離搶購開始 " + formatLeft(untilStart);
        next = Math.min(next, untilStart % 1000 || 1000);
      } else if (untilEnd > 0) {
        var done = e.myResult !== "NONE";
        block.countdown.textContent = "活動進行中，距離結束 " + formatLeft(untilEnd);
        if (done) {
          block.button.textContent = e.myResult === "WON" ? "已搶到" : "已參加";
          block.button.disabled = true;
        } else if (full) {
          block.button.textContent = "名額已滿";
          block.button.disabled = true;
        } else {
          block.button.textContent = "搶購物金";
          block.button.disabled = busy || !state.loggedIn;
        }
        if (!chipText) chipText = "搶購進行中，距離結束 " + formatLeft(untilEnd);
        next = Math.min(next, untilEnd % 1000 || 1000);
      } else {
        block.countdown.textContent = "活動已結束";
        block.button.textContent = "已結束";
        block.button.disabled = true;
      }
      if (!block.message.textContent || block.message.getAttribute("data-auto") === "1") {
        block.message.textContent = resultText(block);
        block.message.setAttribute("data-auto", "1");
      }
    });
    if (chipLine) chipLine.textContent = chipText;
    if (timer) window.clearTimeout(timer);
    if (next !== Infinity) timer = window.setTimeout(tick, Math.max(30, next));
  }

  function setMessage(block, text, kind) {
    block.message.textContent = text;
    block.message.setAttribute("data-auto", "0");
    block.message.className = "wallet-grab-message" + (kind ? " is-" + kind : "");
  }

  function updateCredit(value) {
    state.shoppingCredit = value;
    var node = chip && chip.querySelector(".wallet-grab-credit-value");
    if (node) node.textContent = String(value);
    // 頁首會員選單的購物金（cart.js 產生，格式與那邊一致）
    document.querySelectorAll(".member-menu-credit").forEach(function (item) {
      item.textContent = "購物金 NT$ " + value;
    });
  }

  function delay(ms) {
    return new Promise(function (resolve) { window.setTimeout(resolve, ms); });
  }

  function grab(block) {
    if (busy) return;
    busy = true;
    block.button.disabled = true;
    block.root.classList.add("is-drawing");
    setMessage(block, "搶購中…", "pending");

    var request = fetch(ctx + "/api/promos/" + block.event.promoProjectId + "/grab", {
      method: "POST",
      credentials: "same-origin",
      cache: "no-store"
    }).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (body) {
        return { status: response.status, body: body };
      });
    });

    // 至少等 MIN_DRAW_MS，讓抽選動畫跑完；後端結果先回來也不會立刻跳出
    Promise.all([request, delay(MIN_DRAW_MS)]).then(function (values) {
      var res = values[0];
      var body = res.body || {};
      if (res.status === 401) {
        setMessage(block, "請先登入會員再搶購", "error");
        return;
      }
      if (res.status !== 200) {
        setMessage(block, body.message || "搶購失敗，請稍後再試", "error");
        return fetchState().then(render);
      }
      if (typeof body.shoppingCredit === "number") updateCredit(body.shoppingCredit);
      if (body.result === "WON") {
        block.event.myResult = "WON";
        block.event.mySlotNo = body.slotNo;
        block.event.granted = Math.max(block.event.granted, body.slotNo || 0);
        setMessage(block, "恭喜搶到！已加入 " + body.credit + " 點購物金", "won");
      } else if (body.result === "ALREADY_WON") {
        block.event.myResult = "WON";
        block.event.mySlotNo = body.slotNo;
        setMessage(block, "你已經搶到這場活動的購物金了", "won");
      } else if (body.result === "ALREADY_LOST") {
        block.event.myResult = "LOST";
        setMessage(block, "你已參加過這場活動，沒有搶到", "lost");
      } else {
        block.event.myResult = "LOST";
        block.event.granted = block.event.quota;
        setMessage(block, "很可惜，名額已被搶完，沒有搶到購物金", "lost");
      }
    }).catch(function () {
      setMessage(block, "連線失敗，請稍後再試（若已搶購請重新整理確認結果）", "error");
    }).then(function () {
      busy = false;
      block.root.classList.remove("is-drawing");
      tick();
    });
  }

  function start() {
    fetchState().then(render).catch(function () { /* 沒有搶購資料就不顯示，不影響活動頁 */ });
    window.setInterval(function () {
      if (busy) return;
      fetchState().then(render).catch(function () {});
    }, RESYNC_MS);
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start);
  else start();
})();
