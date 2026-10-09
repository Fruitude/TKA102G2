package com.fruitude.po.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.employee.model.Employee;
import com.fruitude.employee.model.EmployeeRepository;
import com.fruitude.podetail.model.PoDetailRepository;
import com.fruitude.podetail.model.PoDetailVO;

// 進貨系統的資料操作：repository 和 PoService 共用，只處理到貨、驗收、入庫相關的欄位
@Service
public class ReceivingService {

	@Autowired
	private PoRepository poRepository;

	@Autowired
	private PoDetailRepository poDetailRepository;

	@Autowired
	private EmployeeRepository employeeRepository;

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

	// 驗收時以登入的員工編號取出驗收員工
	public Employee getOneEmployee(Integer employeeId) {
		return employeeRepository.findById(employeeId).orElse(null);
	}

	// 驗收前，把表單沒送的欄位從資料庫補回表單物件，檢查失敗重新顯示驗收頁時才有資料可以顯示
	// 表單只採用驗收狀態、每筆明細的到貨數量與不良品數量；其餘欄位一律以資料庫為準
	// 入庫數量、入庫小計、驗收實付金額不收表單的值，由這裡依到貨數量、不良品數量算出來
	// 採購單不存在、不是待驗收、明細和資料庫對不上時丟 IllegalArgumentException（不是欄位填錯，無法顯示在欄位下方）
	@Transactional(readOnly = true)
	public void prepareReceiving(PoVO formPo) {
		if (formPo.getPoId() == null) {
			throw new IllegalArgumentException("查無此採購單");
		}
		PoVO dbPo = poRepository.findById(formPo.getPoId())
				.orElseThrow(() -> new IllegalArgumentException("查無此採購單"));

		// 以資料庫的狀態判斷，不採信表單；開著驗收頁期間已經被別人驗收的單也會被擋下
		if (!dbPo.isReceivable()) {
			throw new IllegalArgumentException("此採購單不是待驗收狀態，無法驗收");
		}

		// 驗收頁不能增刪明細，送回來的明細要和資料庫的筆數、順序、明細編號完全一致，
		// 欄位錯誤訊息的位置（poDetails[索引]）才會對到正確的那一列
		List<PoDetailVO> dbDetails = dbPo.getPoDetails();
		List<PoDetailVO> formDetails = formPo.getPoDetails();
		if (formDetails == null || formDetails.size() != dbDetails.size()) {
			throw new IllegalArgumentException("採購明細與資料庫不一致，請重新進入驗收頁");
		}

		formPo.setPoNo(dbPo.getPoNo());
		formPo.setVendor(dbPo.getVendor());
		formPo.setPoEmployeeId(dbPo.getPoEmployeeId());
		formPo.setOrderDate(dbPo.getOrderDate());
		formPo.setPoStatus(dbPo.getPoStatus());
		formPo.setTotalAmount(dbPo.getTotalAmount());

		boolean inboundFailed = isInboundFailed(formPo.getInboundStatus());
		int inboundAmount = 0;
		for (int index = 0; index < dbDetails.size(); index++) {
			PoDetailVO dbDetail = dbDetails.get(index);
			PoDetailVO formDetail = formDetails.get(index);
			if (formDetail == null || !dbDetail.getPoDetailId().equals(formDetail.getPoDetailId())) {
				throw new IllegalArgumentException("採購明細與資料庫不一致，請重新進入驗收頁");
			}

			// 驗收失敗時不採用表單填的數量，到貨、不良品一律歸零，入庫數量與金額也就跟著是 0
			if (inboundFailed) {
				formDetail.setArrivedPcs(0);
				formDetail.setDefectPcs(0);
			}

			formDetail.setPoId(formPo);
			formDetail.setSkuId(dbDetail.getSkuId());
			formDetail.setQuantity(dbDetail.getQuantity());
			formDetail.setUnitPrice(dbDetail.getUnitPrice());
			formDetail.setSubtotal(dbDetail.getSubtotal());

			inboundAmount += fillInbound(formDetail);
		}
		formPo.setInboundAmount(inboundAmount);
	}

	// 以到貨數量、不良品數量算出入庫數量與入庫小計並填入明細，回傳入庫小計供加總
	// 入庫數量 = 到貨數量 - 不良品數量；入庫小計 = 入庫數量 x 進貨單價
	// 數量不合規定時都放 0，錯誤由 ReceivingController 的 checkReceiving 記在對應欄位
	// 入庫數量不會超過採購數量，所以入庫小計不會超過原本的小計，不用另外檢查上限
	private int fillInbound(PoDetailVO poDetailVO) {
		int inboundPcs = 0;
		if (isValidQuantity(poDetailVO)) {
			inboundPcs = poDetailVO.getArrivedPcs() - poDetailVO.getDefectPcs();
		}
		int inboundSubtotal = inboundPcs * poDetailVO.getUnitPrice();
		poDetailVO.setInboundPcs(inboundPcs);
		poDetailVO.setInboundSubtotal(inboundSubtotal);
		return inboundSubtotal;
	}

	// 驗收狀態是否為驗收失敗（2）；驗收失敗的採購單，到貨、不良品、入庫數量與金額都是 0
	public boolean isInboundFailed(Byte inboundStatus) {
		return inboundStatus != null && inboundStatus == 2;
	}

	// 到貨數量、不良品數量是否合規定：都有填、不是負數、到貨數量不超過採購數量（資料表有 arrived_pcs <= quantity 的限制）、不良品不超過到貨數量
	private boolean isValidQuantity(PoDetailVO poDetailVO) {
		Integer arrivedPcs = poDetailVO.getArrivedPcs();
		Integer defectPcs = poDetailVO.getDefectPcs();
		return arrivedPcs != null && defectPcs != null && arrivedPcs >= 0 && defectPcs >= 0
				&& arrivedPcs <= poDetailVO.getQuantity() && defectPcs <= arrivedPcs;
	}

	// 驗收存檔：一張採購單只驗收一次，存檔後驗收狀態就不再是尚未驗收
	// 不直接 save(formPo)，只把驗收相關的欄位寫進資料庫查出來的採購單，其餘欄位不會被動到
	// 驗收員工由 ReceivingController 從 session 取得，驗收日期為存檔當下的時間，金額由這裡重算
	@Transactional
	public void receive(PoVO formPo, Employee inboundEmployee) {
		PoVO dbPo = poRepository.findById(formPo.getPoId())
				.orElseThrow(() -> new IllegalArgumentException("查無此採購單"));

		if (!dbPo.isReceivable()) {
			throw new IllegalArgumentException("此採購單不是待驗收狀態，無法驗收");
		}

		if (inboundEmployee == null) {
			throw new IllegalArgumentException("查無驗收員工");
		}

		Byte inboundStatus = formPo.getInboundStatus();
		if (inboundStatus == null || (inboundStatus != 1 && inboundStatus != 2)) {
			throw new IllegalArgumentException("請選擇驗收狀態");
		}

		Map<Integer, PoDetailVO> formDetailsById = new HashMap<>();
		if (formPo.getPoDetails() != null) {
			for (PoDetailVO formDetail : formPo.getPoDetails()) {
				if (formDetail != null && formDetail.getPoDetailId() != null) {
					formDetailsById.put(formDetail.getPoDetailId(), formDetail);
				}
			}
		}

		int inboundAmount = 0;
		for (PoDetailVO dbDetail : dbPo.getPoDetails()) {
			PoDetailVO formDetail = formDetailsById.get(dbDetail.getPoDetailId());
			if (formDetail == null) {
				throw new IllegalArgumentException("採購明細與資料庫不一致，請重新進入驗收頁");
			}

			// 驗收失敗時不採用表單填的數量，一律歸零
			boolean inboundFailed = isInboundFailed(inboundStatus);
			dbDetail.setArrivedPcs(inboundFailed ? Integer.valueOf(0) : formDetail.getArrivedPcs());
			dbDetail.setDefectPcs(inboundFailed ? Integer.valueOf(0) : formDetail.getDefectPcs());
			// 數量、單價用資料庫的那一筆來檢查與計算，不採信表單
			if (!isValidQuantity(dbDetail)) {
				throw new IllegalArgumentException("到貨數量或不良品數量不正確，請重新進入驗收頁");
			}
			inboundAmount += fillInbound(dbDetail);
		}

		dbPo.setInboundStatus(inboundStatus);
		dbPo.setInboundEmployeeId(inboundEmployee);
		dbPo.setInboundDate(LocalDateTime.now());
		dbPo.setInboundAmount(inboundAmount);
	}

}
