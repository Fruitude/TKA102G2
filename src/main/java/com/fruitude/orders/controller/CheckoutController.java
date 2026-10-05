package com.fruitude.orders.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.fruitude.orders.model.CheckoutForm;
import com.fruitude.orders.model.CheckoutItem;
import com.fruitude.orders.model.Orders;
import com.fruitude.orders.model.OrdersService;
import com.fruitude.utils.PostalCodes;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/front/checkout")
public class CheckoutController {
    // session 的 key 集中定義，避免各處字串打錯
    private static final String SESSION_KEY = "checkoutForm";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private OrdersService ordersService;

    /**
     * checkout 頁送出 → 把 cartItemsJson 解析成 items → 存進 session → redirect 到 confirm
     * （PRG，重新整理不會重送）
     * confirm 頁由 FruitudeWebConfig 自動顯示，網址結尾要有 /，頁面裡的 ../../css 相對路徑才正確
     */
    @PostMapping("/confirm")
    public String saveToSession(@ModelAttribute CheckoutForm form, HttpSession session) {
    	// 前端也會檢查，這裡是最後一道：不能信任瀏覽器送來的值
    	if (!PostalCodes.isValid(form.getShippingZip())) {
    		return "redirect:/front/checkout/?error=zip";
    	}
    	form.setShippingZip(form.getShippingZip().trim());
    	form.setItems(parseItems(form.getCartItemsJson()));
    	if (form.getItems().isEmpty()) {
    		// 購物車是空的，或 JS 沒能把資料塞進 cartItemsJson，擋回 checkout 頁重填
    		return "redirect:/front/checkout/";
    	}
    	session.setAttribute(SESSION_KEY, form);
		return "redirect:/front/checkout/confirm/";
    }

    /** 給結帳頁前端檢查郵遞區號用：所有合法的 3 碼區號，清單只維護 PostalCodes 一處 */
    @GetMapping("/postal-codes")
    @ResponseBody
    public List<String> postalCodes() {
    	return PostalCodes.areaCodes();
    }

    /** 把前端送來的 JSON 字串解析成品項清單，格式不對就當作空清單，不要讓下單流程整個炸掉 */
    private List<CheckoutItem> parseItems(String cartItemsJson) {
    	if (cartItemsJson == null || cartItemsJson.isBlank()) {
    		return List.of();
    	}
    	try {
    		return objectMapper.readValue(cartItemsJson, new TypeReference<List<CheckoutItem>>() {});
    	} catch (Exception e) {
    		return List.of();
    	}
    }

    /** confirm 頁用 fetch 呼叫：取出 session 資料 + 購物金 → 建立訂單 → 移除 session，回傳狀態碼給前端 */
    @PostMapping("/place-order")
    public ResponseEntity<String> placeOrder(@RequestParam(value = "storeCredit", defaultValue = "0") Integer storeCredit,
                    HttpSession session) {
            CheckoutForm form = (CheckoutForm) session.getAttribute(SESSION_KEY);
            if (form == null) { // 逾時、伺服器重啟，或沒經過 checkout 頁直接進 confirm
                    return ResponseEntity.status(HttpStatus.GONE).body("結帳資料已逾時，請重新填寫");
            }

            // form.getItems() 裡的 skuId 目前是 1~3 的暫定值，之後接上真正的商品資料時
            // 換成實際的 sku_id；OrdersService.toOrders()/toOrdersDetails() 裡也還沒有
            // 拿 skuId 重新查詢資料庫目前的價格與庫存，一樣要等商品資料表接上後再補
            Orders orders;
            try {
            	orders = ordersService.placeOrder(form, storeCredit);
            } catch (Exception e) {
            	e.printStackTrace();
            	return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("訂單建立失敗，請稍後再試");
            }

            session.removeAttribute(SESSION_KEY); // 用完就清掉，按上一頁也不會重複下單
            return ResponseEntity.ok("訂單編號：" + orders.getOrdersId());
    }
}
