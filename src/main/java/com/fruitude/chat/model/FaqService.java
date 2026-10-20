package com.fruitude.chat.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 後台 FAQ 管理：新增、修改 faq_category 與 faq，類別啟用／停用，FAQ 拖拉排序
// 資料給 CannedReplyService.faqReply 的自動回覆使用；檢查不通過時丟 IllegalArgumentException，
// 由 AdminFaqController 回 400 與原因，頁面顯示在彈出視窗裡
@Service
public class FaqService {

	public static final int STATUS_HIDDEN = 0;
	public static final int STATUS_SHOWN = 1;

	// 自動回覆最多送出 1000 字（CannedReplyService.faqReply 超過會截斷），所以答案也限制 1000 字
	public static final int MAX_ANSWER_LENGTH = 1000;

	// 頁面送來的類別內容；上層類別沒選時是 null
	public record CategoryForm(String categoryName, String categoryDesc, Integer parentCategoryId) {
	}

	// 頁面送來的 FAQ 內容；顯示順序不從這裡改，由拖拉排序決定
	public record FaqForm(Integer faqCategoryId, String question, String answer, String keyword) {
	}

	@Autowired
	private FaqCategoryRepository faqCategoryRepository;

	@Autowired
	private FaqRepository faqRepository;

	public List<FaqCategory> getCategories() {
		return faqCategoryRepository.findAllByOrderByFaqCategoryIdAsc();
	}

	public List<Faq> getFaqs() {
		return faqRepository.findAllByOrderByFaqCategoryIdAscSortOrderAscFaqIdAsc();
	}

	// 新增類別：狀態照資料表預設為 0（停用），FAQ 都準備好後再到列表啟用
	@Transactional
	public FaqCategory addCategory(CategoryForm form) {
		String categoryName = requiredText(form.categoryName(), "類別名稱", 50);
		if (faqCategoryRepository.existsByCategoryName(categoryName)) {
			throw new IllegalArgumentException("類別名稱已經存在");
		}
		checkParent(null, form.parentCategoryId());

		FaqCategory category = new FaqCategory();
		category.setCategoryName(categoryName);
		category.setCategoryDesc(optionalText(form.categoryDesc(), "類別描述", 255));
		category.setParentCategoryId(form.parentCategoryId());
		category.setStatus(STATUS_HIDDEN);
		category.setCreatedAt(now());
		return faqCategoryRepository.save(category);
	}

	// 修改類別：名稱、描述、上層類別；狀態由 setCategoryStatus 改
	@Transactional
	public FaqCategory updateCategory(Integer faqCategoryId, CategoryForm form) {
		FaqCategory category = findCategory(faqCategoryId);
		String categoryName = requiredText(form.categoryName(), "類別名稱", 50);
		if (faqCategoryRepository.existsByCategoryNameAndFaqCategoryIdNot(categoryName, faqCategoryId)) {
			throw new IllegalArgumentException("類別名稱已經存在");
		}
		checkParent(faqCategoryId, form.parentCategoryId());

		category.setCategoryName(categoryName);
		category.setCategoryDesc(optionalText(form.categoryDesc(), "類別描述", 255));
		category.setParentCategoryId(form.parentCategoryId());
		category.setUpdatedAt(now());
		return faqCategoryRepository.save(category);
	}

	// 啟用（status=1）或停用（status=0）；停用後這個類別和底下子類別的 FAQ 都不會自動回覆
	@Transactional
	public FaqCategory setCategoryStatus(Integer faqCategoryId, boolean enabled) {
		FaqCategory category = findCategory(faqCategoryId);
		category.setStatus(enabled ? STATUS_SHOWN : STATUS_HIDDEN);
		category.setUpdatedAt(now());
		return faqCategoryRepository.save(category);
	}

	// 刪除類別：只能刪除空的類別，有子類別或 FAQ 時丟錯（資料表有外來鍵，直接刪也會失敗）
	@Transactional
	public void deleteCategory(Integer faqCategoryId) {
		findCategory(faqCategoryId);
		if (faqCategoryRepository.existsByParentCategoryId(faqCategoryId)) {
			throw new IllegalArgumentException("請先刪除或移走底下的子類別");
		}
		if (faqRepository.existsByFaqCategoryId(faqCategoryId)) {
			throw new IllegalArgumentException("請先把這個類別的 FAQ 移到其他類別");
		}
		faqCategoryRepository.deleteById(faqCategoryId);
	}

	// 新增 FAQ：排在這個類別的最後面
	@Transactional
	public Faq addFaq(FaqForm form) {
		Integer faqCategoryId = findCategory(form.faqCategoryId()).getFaqCategoryId();
		String question = requiredText(form.question(), "問題", 255);
		if (faqRepository.existsByFaqCategoryIdAndQuestion(faqCategoryId, question)) {
			throw new IllegalArgumentException("這個類別已經有相同的問題");
		}

		Faq faq = new Faq();
		faq.setFaqCategoryId(faqCategoryId);
		faq.setQuestion(question);
		faq.setAnswer(requiredText(form.answer(), "答案", MAX_ANSWER_LENGTH));
		faq.setKeyword(optionalText(form.keyword(), "keyword", 255));
		faq.setSortOrder(faqRepository.findMaxSortOrder(faqCategoryId) + 1);
		faq.setCreatedAt(now());
		return faqRepository.save(faq);
	}

	// 修改 FAQ：換到別的類別時排到新類別的最後面
	@Transactional
	public Faq updateFaq(Integer faqId, FaqForm form) {
		Faq faq = faqRepository.findById(faqId)
				.orElseThrow(() -> new IllegalArgumentException("FAQ 不存在，請重新整理頁面"));
		Integer faqCategoryId = findCategory(form.faqCategoryId()).getFaqCategoryId();
		String question = requiredText(form.question(), "問題", 255);
		if (faqRepository.existsByFaqCategoryIdAndQuestionAndFaqIdNot(faqCategoryId, question, faqId)) {
			throw new IllegalArgumentException("這個類別已經有相同的問題");
		}
		// 即時回覆的題目改了問題文字，CannedReplyService 就對不到，所以不能改；keyword、答案、類別仍然可以改
		if (FaqLiveAnswer.isLiveQuestion(faq.getQuestion()) && !faq.getQuestion().equals(question)) {
			throw new IllegalArgumentException("這題會自動回覆即時活動資料，問題文字不能修改");
		}

		if (!faqCategoryId.equals(faq.getFaqCategoryId())) {
			faq.setSortOrder(faqRepository.findMaxSortOrder(faqCategoryId) + 1);
			faq.setFaqCategoryId(faqCategoryId);
		}
		faq.setQuestion(question);
		faq.setAnswer(requiredText(form.answer(), "答案", MAX_ANSWER_LENGTH));
		faq.setKeyword(optionalText(form.keyword(), "keyword", 255));
		faq.setUpdatedAt(now());
		return faqRepository.save(faq);
	}

	// 刪除 FAQ：即時回覆的題目（FaqLiveAnswer）不能刪除
	// 刪除後同類別的 sort_order 會有空號，不影響順序，下次拖拉排序時重新編號
	@Transactional
	public void deleteFaq(Integer faqId) {
		Faq faq = faqRepository.findById(faqId)
				.orElseThrow(() -> new IllegalArgumentException("FAQ 不存在，請重新整理頁面"));
		if (FaqLiveAnswer.isLiveQuestion(faq.getQuestion())) {
			throw new IllegalArgumentException("這題會自動回覆即時活動資料，不能刪除");
		}
		faqRepository.delete(faq);
	}

	// 拖拉排序：faqIds 是這個類別拖拉後的順序，依序把 sort_order 改成 1、2、3…
	// 送來的編號要剛好是這個類別目前全部的 FAQ；不符合代表別人改過，請使用者重新整理
	@Transactional
	public void reorderFaqs(Integer faqCategoryId, List<Integer> faqIds) {
		List<Faq> categoryFaqs = faqRepository.findByFaqCategoryIdOrderBySortOrderAscFaqIdAsc(faqCategoryId);
		Set<Integer> currentFaqIds = new HashSet<>();
		for (Faq faq : categoryFaqs) {
			currentFaqIds.add(faq.getFaqId());
		}
		if (faqIds == null || faqIds.size() != categoryFaqs.size() || !currentFaqIds.equals(new HashSet<>(faqIds))) {
			throw new IllegalArgumentException("FAQ 已經被修改過，請重新整理頁面後再排序");
		}
		for (Faq faq : categoryFaqs) {
			faq.setSortOrder(faqIds.indexOf(faq.getFaqId()) + 1);
		}
		faqRepository.saveAll(categoryFaqs);
	}

	// 上層類別要存在，而且不能是自己或自己的子孫：從上層一路往上找，碰到自己就代表會形成迴圈
	// visited 防止資料庫裡原本就有迴圈時無限循環
	private void checkParent(Integer faqCategoryId, Integer parentCategoryId) {
		Set<Integer> visited = new HashSet<>();
		Integer currentCategoryId = parentCategoryId;
		while (currentCategoryId != null && visited.add(currentCategoryId)) {
			if (currentCategoryId.equals(faqCategoryId)) {
				throw new IllegalArgumentException("上層類別不能選自己或自己的子類別");
			}
			currentCategoryId = findCategory(currentCategoryId).getParentCategoryId();
		}
	}

	private FaqCategory findCategory(Integer faqCategoryId) {
		if (faqCategoryId == null) {
			throw new IllegalArgumentException("請選擇類別");
		}
		return faqCategoryRepository.findById(faqCategoryId)
				.orElseThrow(() -> new IllegalArgumentException("類別不存在，請重新整理頁面"));
	}

	// 必填文字：去掉前後空白，空白或超過長度時丟錯
	private String requiredText(String text, String label, int maxLength) {
		String value = optionalText(text, label, maxLength);
		if (value == null) {
			throw new IllegalArgumentException(label + "：請勿空白");
		}
		return value;
	}

	// 選填文字：去掉前後空白，空白當作沒填（null），超過長度時丟錯
	private String optionalText(String text, String label, int maxLength) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String value = text.strip();
		if (value.length() > maxLength) {
			throw new IllegalArgumentException(label + "：最多 " + maxLength + " 字");
		}
		return value;
	}

	private LocalDateTime now() {
		return LocalDateTime.now(ZoneId.of("Asia/Taipei"));
	}

}
