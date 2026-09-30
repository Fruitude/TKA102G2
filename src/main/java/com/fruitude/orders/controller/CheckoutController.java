package com.fruitude.orders.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fruitude.orders.model.CheckoutForm;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/front/checkout")
public class CheckoutController {
    // session 的 key 集中定義，避免各處字串打錯
    private static final String SESSION_KEY = "checkoutForm";

    /**
     * checkout 頁送出 → 存進 session → redirect 到 confirm（PRG，重新整理不會重送）
     * confirm 頁由 FruitudeWebConfig 自動顯示，網址結尾要有 /，頁面裡的 ../../css 相對路徑才正確
     */
    @PostMapping("/confirm")
    public String saveToSession(@ModelAttribute CheckoutForm form, HttpSession session) {
    	session.setAttribute(SESSION_KEY, form);
		return "redirect:/front/checkout/confirm/";
    }

    /** confirm 頁用 fetch 呼叫：取出 session 資料 + 購物金 → 建立訂單 → 移除 session，回傳狀態碼給前端 */
    @PostMapping("/place-order")
    public ResponseEntity<String> placeOrder(@RequestParam(value = "storeCredit", defaultValue = "0") Integer storeCredit,
                    HttpSession session) {
            CheckoutForm form = (CheckoutForm) session.getAttribute(SESSION_KEY);
            if (form == null) { // 逾時、伺服器重啟，或沒經過 checkout 頁直接進 confirm
                    return ResponseEntity.status(HttpStatus.GONE).body("結帳資料已逾時，請重新填寫");
            }

            // TODO 把 form + storeCredit 轉成 Orders，交給 OrdersService 存檔（金額在這裡重新計算）
            System.out.println("下單：" + form.getReceiverName() + "，購物金 " + storeCredit);

            session.removeAttribute(SESSION_KEY); // 用完就清掉，按上一頁也不會重複下單
            return ResponseEntity.ok("OK");
    }
}
