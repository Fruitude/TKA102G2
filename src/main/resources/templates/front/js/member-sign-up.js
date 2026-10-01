(function () {
  "use strict";

  const form = document.getElementById("member-sign-up-form");
  if (!form) {
    return;
  }

  const account = document.getElementById("member-account");
  const email = document.getElementById("member-email");
  const accountStatus = document.getElementById("member-account-status");
  const emailStatus = document.getElementById("member-email-status");
  const feedback = document.getElementById("sign-up-feedback");
  const submit = document.getElementById("sign-up-submit");
  const birthday = document.getElementById("member-birthday");
  const contextPath = window.location.pathname.split("/front/")[0];
  const accountPattern = /^[A-Za-z0-9_]{6,20}$/;
  const checkedValues = { account: "", email: "" };
  const availableValues = { account: false, email: false };

  // 生日不能晚於今天，先在瀏覽器端阻止明顯不合法的日期。
  birthday.max = new Date().toISOString().slice(0, 10);

  function showFeedback(message, type) {
    feedback.textContent = message;
    feedback.className = "sign-up-feedback" + (type ? " is-" + type : "");
  }

  function showFieldStatus(input, statusElement, message, type) {
    statusElement.textContent = message;
    statusElement.className =
      "field-validation-hint" + (type ? " is-" + type : "");
    input.classList.toggle("validation-success", type === "success");
    input.classList.toggle("validation-error", type === "error");
    input.setAttribute("aria-invalid", type === "error" ? "true" : "false");
  }

  function resetAvailability(field, input, statusElement, hint) {
    checkedValues[field] = "";
    availableValues[field] = false;
    showFieldStatus(input, statusElement, hint, "");
  }

  function readErrorMessage(response, body) {
    if (body && typeof body.message === "string" && body.message.trim()) {
      return body.message;
    }
    if (response.status === 400) {
      return "資料格式不正確，請檢查各欄位後再試一次";
    }
    return "目前無法完成註冊，請稍後再試";
  }

  function requestAvailability(field, value, input, statusElement) {
    if (checkedValues[field] === value) {
      return Promise.resolve(availableValues[field]);
    }

    showFieldStatus(input, statusElement, "檢查中...", "checking");
    return fetch(
      contextPath +
        "/api/members/availability/" +
        field +
        "?value=" +
        encodeURIComponent(value),
      { credentials: "same-origin" },
    )
      .then(function (response) {
        if (!response.ok) {
          throw new Error("目前無法確認是否可用，請稍後再試");
        }
        return response.json();
      })
      .then(function (result) {
        const currentValue =
          field === "email"
            ? input.value.trim().toLowerCase()
            : input.value.trim();
        if (currentValue !== value) {
          return false;
        }

        checkedValues[field] = value;
        availableValues[field] = Boolean(result.valid && result.available);
        showFieldStatus(
          input,
          statusElement,
          result.message,
          availableValues[field] ? "success" : "error",
        );
        return availableValues[field];
      })
      .catch(function (error) {
        showFieldStatus(input, statusElement, error.message, "error");
        return false;
      });
  }

  function checkAccountAvailability() {
    const value = account.value.trim();
    account.value = value;
    if (!accountPattern.test(value)) {
      showFieldStatus(
        account,
        accountStatus,
        "帳號需為 6～20 個英文字母、數字或底線",
        "error",
      );
      return Promise.resolve(false);
    }
    return requestAvailability("account", value, account, accountStatus);
  }

  function checkEmailAvailability() {
    const value = email.value.trim().toLowerCase();
    email.value = value;
    if (!value || !email.checkValidity()) {
      showFieldStatus(email, emailStatus, "電子郵件格式不正確", "error");
      return Promise.resolve(false);
    }
    return requestAvailability("email", value, email, emailStatus);
  }

  account.addEventListener("input", function () {
    resetAvailability(
      "account",
      account,
      accountStatus,
      "請輸入 6～20 個英文字母、數字或底線",
    );
  });
  email.addEventListener("input", function () {
    resetAvailability(
      "email",
      email,
      emailStatus,
      "請輸入可正常收信的電子郵件",
    );
  });
  account.addEventListener("blur", checkAccountAvailability);
  email.addEventListener("blur", checkEmailAvailability);

  // 使用捕捉階段接管送出事件，避免 Webflow 攔截密碼欄位並跳出 alert。
  form.addEventListener(
    "submit",
    function (event) {
      event.preventDefault();
      event.stopImmediatePropagation();
      showFeedback("", "");

      if (!form.reportValidity()) {
        return;
      }

      submit.disabled = true;
      submit.value = "檢查中...";
      Promise.all([checkAccountAvailability(), checkEmailAvailability()]).then(
        function (results) {
          if (!results[0] || !results[1]) {
            showFeedback("請先修正帳號或電子郵件欄位", "error");
            submit.disabled = false;
            submit.value = "註冊";
            (!results[0] ? account : email).focus();
            return;
          }

          submit.value = "註冊中...";
          const formData = new FormData(form);
          const requestBody = {
            memberName: String(formData.get("memberName")).trim(),
            memberBirthday: formData.get("memberBirthday") || null,
            memberAccount: String(formData.get("memberAccount")).trim(),
            memberEmail: String(formData.get("memberEmail"))
              .trim()
              .toLowerCase(),
            memberPassword: String(formData.get("memberPassword")),
          };

          fetch(contextPath + "/api/members/register", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "same-origin",
            body: JSON.stringify(requestBody),
          })
            .then(function (response) {
              return response
                .json()
                .catch(function () {
                  return {};
                })
                .then(function (body) {
                  return { response: response, body: body };
                });
            })
            .then(function (result) {
              if (!result.response.ok) {
                throw new Error(readErrorMessage(result.response, result.body));
              }
              showFeedback("註冊成功，正在前往登入頁...", "success");
              window.location.assign(contextPath + "/front/about/login/");
            })
            .catch(function (error) {
              showFeedback(
                error.message || "目前無法完成註冊，請稍後再試",
                "error",
              );
              submit.disabled = false;
              submit.value = "註冊";
            });
        },
      );
    },
    true,
  );
})();
