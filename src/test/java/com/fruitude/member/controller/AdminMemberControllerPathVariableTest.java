package com.fruitude.member.controller;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;

import org.junit.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 確認後台會員 API 不依賴編譯器保留 Java 參數名稱。 */
public class AdminMemberControllerPathVariableTest {

	@Test
	public void reviewRequestIdHasExplicitPathVariableName() throws Exception {
		Method method = AdminMemberController.class.getMethod("reviewCreditAdjustmentRequest",
				Integer.class, AdminMemberController.CreditAdjustmentReviewForm.class,
				jakarta.servlet.http.HttpServletRequest.class);
		PathVariable annotation = method.getParameters()[0].getAnnotation(PathVariable.class);

		assertEquals("requestId", annotation.value());
	}

	@Test
	public void reviewRouteCanBindRequestIdWithoutCompilerParameterMetadata() throws Exception {
		// 表單缺少 approved 時會在服務與資料庫呼叫前停止，可安全驗證網址參數綁定。
		MockMvc mvc = MockMvcBuilders.standaloneSetup(
				new AdminMemberController(null, null, null, null)).build();

		mvc.perform(patch("/api/admin/members/credit-adjustment-requests/1/review")
				.contentType("application/json")
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("{\"message\":\"請選擇核准或退回\"}"));
	}
}
