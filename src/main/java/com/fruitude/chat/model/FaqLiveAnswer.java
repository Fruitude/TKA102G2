package com.fruitude.chat.model;

import java.util.Set;

// 會被自動回覆換成即時活動資料的 FAQ 問題（要完全相同）
// 回覆內容由 CannedReplyService.liveFaqAnswer 的 switch 決定，這裡只給 FAQ 管理功能用來鎖定這幾題：
// 不能刪除、不能改問題文字。兩邊的題目要一致，修改 liveFaqAnswer 時這裡要跟著改
public final class FaqLiveAnswer {

	public static final Set<String> LIVE_QUESTIONS = Set.of(
			"最近有哪些活動？", "生日優惠是幾折？", "新會員有優惠嗎？", "運費多少？多少免運？");

	public static boolean isLiveQuestion(String question) {
		return question != null && LIVE_QUESTIONS.contains(question);
	}

	private FaqLiveAnswer() {
	}

}
