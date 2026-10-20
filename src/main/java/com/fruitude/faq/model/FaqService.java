package com.fruitude.faq.model;

import java.util.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fruitude.promo.model.PromoRepository;

/** Matches member FAQ keywords and resolves current promotional wording. */
@Service
public class FaqService {
    public static final int STATUS_HIDDEN=0;
    public static final int STATUS_SHOWN=1;
    public static final int MAX_ANSWER_LENGTH=1000;
    private final FaqRepository faqs;
    private final PromoRepository faqPromos;
    private final FaqCategoryRepository categories;
    @org.springframework.beans.factory.annotation.Autowired
    public FaqService(FaqRepository faqs,FaqCategoryRepository categories,PromoRepository faqPromos){this.faqs=faqs;this.categories=categories;this.faqPromos=faqPromos;}
    public FaqService(FaqRepository faqs,PromoRepository faqPromos){this(faqs,null,faqPromos);}

    private static final Map<String,String> LIVE_PROMO_TYPES=Map.of(
        "最近有哪些活動？","ALL", "生日優惠是幾折？","BIRTHDAY_MONTH",
        "新會員有優惠嗎？","NEW_MEMBER_FIRST_ORDER", "運費多少？多少免運？","FREE_SHIPPING");
    public static Set<String> liveQuestions(){return LIVE_PROMO_TYPES.keySet();}
    private static boolean isLiveQuestion(String question){return question!=null && LIVE_PROMO_TYPES.containsKey(question);}

    public record CategoryForm(String categoryName,String categoryDesc,Integer parentCategoryId) {}
    public record FaqForm(Integer faqCategoryId,String question,String answer,String keyword) {}
    // Preserve the admin page's flat JSON contract without exposing lazy entity relationships.
    public record CategoryView(Integer faqCategoryId,Integer parentCategoryId,String categoryName,
            String categoryDesc,Integer status,java.time.LocalDateTime createdAt,java.time.LocalDateTime updatedAt,Integer sortOrder) {
        static CategoryView of(FaqCategory c){return new CategoryView(c.getFaqCategoryId(),c.getParentCategory()==null?null:c.getParentCategory().getFaqCategoryId(),
            c.getCategoryName(),c.getCategoryDesc(),c.getStatus(),c.getCreatedAt(),c.getUpdatedAt(),c.getSortOrder());}
    }
    public record FaqView(Integer faqId,Integer faqCategoryId,String question,String answer,String keyword,
            Integer sortOrder,java.time.LocalDateTime createdAt,java.time.LocalDateTime updatedAt) {
        static FaqView of(Faq f){return new FaqView(f.getFaqId(),f.getFaqCategory().getFaqCategoryId(),f.getQuestion(),f.getAnswer(),f.getKeyword(),f.getSortOrder(),f.getCreatedAt(),f.getUpdatedAt());}
    }


    // FAQ keywords: commas separate alternatives; & means all words in that alternative must match.
    // Only enabled categories with an enabled ancestor path can answer. More specific matches win.
    @Transactional(readOnly=true)
    public String reply(String text) {
        if(text==null || text.isBlank())return null;
        String normalized=normalizeFaq(text);
        var rows=faqs.findAvailableForReply();
        Faq best=null;int bestScore=0;
        for(var row:rows){int longest=0,count=0;
            for(String alternative:splitKeywords(row.getKeyword())){
                int length=0;boolean matches=true;
                for(String part:alternative.split("&",-1)){
                    String term=normalizeFaq(part);if(term.isEmpty()||!normalized.contains(term)){matches=false;break;}length+=term.length();
                }
                if(matches){longest=Math.max(longest,length);count++;}
            }
            int score=longest*100+Math.min(count,99);
            if(score>bestScore && row.getAnswer()!=null && !row.getAnswer().isBlank()){best=row;bestScore=score;}
        }
        if(best==null)return null;
        String answer=liveFaqAnswer(best.getQuestion(),best.getAnswer());
        return answer.length()>1000?answer.substring(0,997)+"…":answer;
    }
    private static String normalizeFaq(String text){return java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}]+","");}
    private String liveFaqAnswer(String question,String fallback){
        String type=LIVE_PROMO_TYPES.get(question);
        if(type==null)return fallback;
        var now=java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Taipei"));
        var promos=type.equals("ALL")?faqPromos.findActive(now):faqPromos.findActiveByType(type,now);
        if(promos.isEmpty())return type.equals("ALL")?"目前沒有進行中的優惠活動，歡迎留意商城的限時優惠頁面。":"目前沒有進行中的這類優惠，實際金額請以結帳畫面為準。";
        var lines=new ArrayList<String>();
        for(var promo:promos){
            String benefit=com.fruitude.promo.model.BenefitType.describe(promo.getBenefitType(),promo.getBenefitValue());
            String line=promo.getPromoProjectTitle()+"："+benefit;
            if(promo.getMinOrderAmount()!=null && promo.getMinOrderAmount()>0)line+="，訂單滿 "+promo.getMinOrderAmount()+" 元適用";
            if("BIRTHDAY_MONTH".equals(promo.getPromoType()))line+="，生日當月每年限用一次，結帳時需勾選使用";
            if("NEW_MEMBER_FIRST_ORDER".equals(promo.getPromoType()))line+="，首次下單適用，限用一次";
            if(promo.getPromoProjectEnd()!=null && promo.getPromoProjectEnd().getYear()<2100)line+="，至 "+promo.getPromoProjectEnd().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
            lines.add(line);
        }
        return String.join("\n",lines)+"。實際資格、運費與優惠金額，以結帳畫面為準。";
    }
	private List<String> splitKeywords(String keyword) {
		List<String> keywords = new ArrayList<>();
		if (keyword == null) {
			return keywords;
		}
		for (String part : keyword.split("[,，、]")) {
			String trimmed = part.strip();
			if (!trimmed.isEmpty()) {
				keywords.add(trimmed);
			}
		}
		return keywords;
	}


    // Back-office management shares the same entities and tables as automatic replies.
	@Transactional(readOnly=true)
	public List<CategoryView> getCategories() {
		return categories.findAllByOrderByFaqCategoryIdAsc().stream().map(CategoryView::of).toList();
	}

	@Transactional(readOnly=true)
	public List<FaqView> getFaqs() {
		return faqs.findForAdministration().stream().map(FaqView::of).toList();
	}

	// 新增類別：狀態照資料表預設為 0（停用），FAQ 都準備好後再到列表啟用
	@Transactional
	public CategoryView addCategory(CategoryForm form) {
        if(form==null)throw new IllegalArgumentException("缺少類別資料");
		String categoryName = requiredText(form.categoryName(), "類別名稱", 50);
		if (categories.existsSiblingName(categoryName, form.parentCategoryId(), null)) {
			throw new IllegalArgumentException("同一上層類別已有相同的類別名稱");
		}
		checkParent(null, form.parentCategoryId());

		FaqCategory category = new FaqCategory();
		category.setCategoryName(categoryName);
		category.setCategoryDesc(optionalText(form.categoryDesc(), "類別描述", 255));
		category.setParentCategory(form.parentCategoryId()==null ? null : findCategory(form.parentCategoryId()));
		category.setStatus(STATUS_HIDDEN);
		category.setCreatedAt(now());
		return CategoryView.of(categories.saveAndFlush(category));
	}

	// 修改類別：名稱、描述、上層類別；狀態由 setCategoryStatus 改
	@Transactional
	public CategoryView updateCategory(Integer faqCategoryId, CategoryForm form) {
        if(form==null)throw new IllegalArgumentException("缺少類別資料");
		FaqCategory category = findCategory(faqCategoryId);
		String categoryName = requiredText(form.categoryName(), "類別名稱", 50);
		if (categories.existsSiblingName(categoryName, form.parentCategoryId(), faqCategoryId)) {
			throw new IllegalArgumentException("同一上層類別已有相同的類別名稱");
		}
		checkParent(faqCategoryId, form.parentCategoryId());

		category.setCategoryName(categoryName);
		category.setCategoryDesc(optionalText(form.categoryDesc(), "類別描述", 255));
		category.setParentCategory(form.parentCategoryId()==null ? null : findCategory(form.parentCategoryId()));
		category.setUpdatedAt(now());
		return CategoryView.of(categories.saveAndFlush(category));
	}

	// 啟用（status=1）或停用（status=0）；停用後這個類別和底下子類別的 FAQ 都不會自動回覆
	@Transactional
	public CategoryView setCategoryStatus(Integer faqCategoryId, boolean enabled) {
		FaqCategory category = findCategory(faqCategoryId);
		category.setStatus(enabled ? STATUS_SHOWN : STATUS_HIDDEN);
		category.setUpdatedAt(now());
		return CategoryView.of(categories.saveAndFlush(category));
	}

	// 刪除類別：只能刪除空的類別，有子類別或 FAQ 時丟錯（資料表有外來鍵，直接刪也會失敗）
	@Transactional
	public void deleteCategory(Integer faqCategoryId) {
		findCategory(faqCategoryId);
		if (categories.existsByParentCategory_FaqCategoryId(faqCategoryId)) {
			throw new IllegalArgumentException("請先刪除或移走底下的子類別");
		}
		if (faqs.existsByFaqCategory_FaqCategoryId(faqCategoryId)) {
			throw new IllegalArgumentException("請先把這個類別的 FAQ 移到其他類別");
		}
		categories.deleteById(faqCategoryId);
	}

	// 新增 FAQ：排在這個類別的最後面
	@Transactional
	public FaqView addFaq(FaqForm form) {
        if(form==null)throw new IllegalArgumentException("缺少 FAQ 資料");
		Integer faqCategoryId = findCategory(form.faqCategoryId()).getFaqCategoryId();
		String question = requiredText(form.question(), "問題", 255);
		if (faqs.existsByFaqCategory_FaqCategoryIdAndQuestion(faqCategoryId, question)) {
			throw new IllegalArgumentException("這個類別已經有相同的問題");
		}

		Faq faq = new Faq();
		faq.setFaqCategory(findCategory(faqCategoryId));
		faq.setQuestion(question);
		faq.setAnswer(requiredText(form.answer(), "答案", MAX_ANSWER_LENGTH));
		faq.setKeyword(optionalText(form.keyword(), "keyword", 255));
		faq.setSortOrder(faqs.findMaxSortOrder(faqCategoryId) + 1);
		faq.setCreatedAt(now());
		return FaqView.of(faqs.saveAndFlush(faq));
	}

	// 修改 FAQ：換到別的類別時排到新類別的最後面
	@Transactional
	public FaqView updateFaq(Integer faqId, FaqForm form) {
        if(form==null)throw new IllegalArgumentException("缺少 FAQ 資料");
		Faq faq = faqs.findById(faqId)
				.orElseThrow(() -> new IllegalArgumentException("FAQ 不存在，請重新整理頁面"));
		Integer faqCategoryId = findCategory(form.faqCategoryId()).getFaqCategoryId();
		String question = requiredText(form.question(), "問題", 255);
		if (faqs.existsByFaqCategory_FaqCategoryIdAndQuestionAndFaqIdNot(faqCategoryId, question, faqId)) {
			throw new IllegalArgumentException("這個類別已經有相同的問題");
		}
		// 即時回覆的題目改了問題文字，CannedReplyService 就對不到，所以不能改；keyword、答案、類別仍然可以改
		if (isLiveQuestion(faq.getQuestion()) && !faq.getQuestion().equals(question)) {
			throw new IllegalArgumentException("這題會自動回覆即時活動資料，問題文字不能修改");
		}

		if (!faqCategoryId.equals(faq.getFaqCategory().getFaqCategoryId())) {
			faq.setSortOrder(faqs.findMaxSortOrder(faqCategoryId) + 1);
			faq.setFaqCategory(findCategory(faqCategoryId));
		}
		faq.setQuestion(question);
		faq.setAnswer(requiredText(form.answer(), "答案", MAX_ANSWER_LENGTH));
		faq.setKeyword(optionalText(form.keyword(), "keyword", 255));
		faq.setUpdatedAt(now());
		return FaqView.of(faqs.saveAndFlush(faq));
	}

	// 刪除 FAQ：共用即時活動題目清單內的問題不能刪除。
	// 刪除後同類別的 sort_order 會有空號，不影響順序，下次拖拉排序時重新編號
	@Transactional
	public void deleteFaq(Integer faqId) {
		Faq faq = faqs.findById(faqId)
				.orElseThrow(() -> new IllegalArgumentException("FAQ 不存在，請重新整理頁面"));
		if (isLiveQuestion(faq.getQuestion())) {
			throw new IllegalArgumentException("這題會自動回覆即時活動資料，不能刪除");
		}
		faqs.delete(faq);
	}

	// 拖拉排序：faqIds 是這個類別拖拉後的順序，依序把 sort_order 改成 1、2、3…
	// 送來的編號要剛好是這個類別目前全部的 FAQ；不符合代表別人改過，請使用者重新整理
	@Transactional
	public void reorderFaqs(Integer faqCategoryId, List<Integer> faqIds) {
        findCategory(faqCategoryId);
		List<Faq> categoryFaqs = faqs.findByCategoryForAdministration(faqCategoryId);
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
		faqs.saveAll(categoryFaqs);
	}

	// 上層類別要存在，而且不能是自己或自己的子孫：從上層一路往上找，碰到自己就代表會形成迴圈
	// visited 防止資料庫裡原本就有迴圈時無限循環
	private void checkParent(Integer faqCategoryId, Integer parentCategoryId) {
		Set<Integer> visited = new HashSet<>();
		Integer currentCategoryId = parentCategoryId;
		while (currentCategoryId != null) {
			if (!visited.add(currentCategoryId) || currentCategoryId.equals(faqCategoryId)) {
				throw new IllegalArgumentException("上層類別不能選自己或自己的子類別");
			}
			var parent=findCategory(currentCategoryId).getParentCategory();
            currentCategoryId=parent==null ? null : parent.getFaqCategoryId();
		}
	}

	private FaqCategory findCategory(Integer faqCategoryId) {
		if (faqCategoryId == null) {
			throw new IllegalArgumentException("請選擇類別");
		}
		return categories.findById(faqCategoryId)
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
