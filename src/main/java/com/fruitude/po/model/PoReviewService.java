package com.fruitude.po.model;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 採購單審核的資料操作：repository 和 PoService 共用，處理待審核採購單的審核
@Service
public class PoReviewService {

	@Autowired
	private PoRepository poRepository;

	// 待審核（poStatus = 0）的採購單，採購單審核首頁列出這些單
	public List<PoVO> getPendingReviewPos() {
		return poRepository.getByPoStatus((byte) 0);
	}

	// 審核頁的單筆資料：只取待審核的採購單，查不到或不是待審核時回傳 null
	public PoVO getOnePendingReviewPo(Integer poId) {
		PoVO poVO = poRepository.findById(poId).orElse(null);
		if (poVO == null || !poVO.isEditable()) {
			return null;
		}
		return poVO;
	}

}
