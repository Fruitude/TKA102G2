package com.fruitude.po.model;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fruitude.podetail.model.PoDetailRepository;

// 進貨系統的資料操作：repository 和 PoService 共用，只處理到貨、驗收、入庫相關的欄位
@Service
public class ReceivingService {

	@Autowired
	private PoRepository poRepository;

	@Autowired
	private PoDetailRepository poDetailRepository;

	// 申請通過（poStatus = 1）且尚未驗收（inboundStatus = 0）的採購單，進貨系統首頁列出這些單
	public List<PoVO> getPendingInboundPos() {
		return poRepository.findByPoStatusAndInboundStatus((byte) 1, (byte) 0);
	}

	// 進貨系統的單筆資料：只取申請通過的採購單，查不到或不是申請通過時回傳 null
	public PoVO getOneApprovedPo(Integer poId) {
		PoVO poVO = poRepository.findById(poId).orElse(null);
		if (poVO == null || !Byte.valueOf((byte) 1).equals(poVO.getPoStatus())) {
			return null;
		}
		return poVO;
	}

}
