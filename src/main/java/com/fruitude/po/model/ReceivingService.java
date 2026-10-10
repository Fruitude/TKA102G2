package com.fruitude.po.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.employee.model.Employee;
import com.fruitude.employee.model.EmployeeRepository;
import com.fruitude.podetail.model.PoDetailVO;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

// 進貨系統的資料操作：repository 和 PoService 共用，只處理到貨、驗收、入庫相關的欄位
@Service
public class ReceivingService {

	@Autowired
	private PoRepository poRepository;

	@Autowired
	private PoSkuStockRepository poSkuStockRepository;

	@Autowired
	private EmployeeRepository employeeRepository;

	@PersistenceContext
	private EntityManager entityManager;

	// 申請通過（poStatus = 1）且尚未驗收（inboundStatus = 0）的採購單，進貨系統首頁列出這些單
	public List<PoVO> getPendingInboundPos() {
		return poRepository.findByPoStatusAndInboundStatus((byte) 1, (byte) 0);
	}

	// 已結案（poStatus = 4，驗收過）的採購單，驗收紀錄頁列出這些單
	public List<PoVO> getClosedPos() {
		return poRepository.getByPoStatus((byte) 4);
	}

	// 進貨系統的單筆資料：取申請通過（1）或已結案（4，驗收完成）的採購單，查不到或是其他狀態時回傳 null
	public PoVO getOneApprovedPo(Integer poId) {
		PoVO poVO = poRepository.findById(poId).orElse(null);
		if (poVO == null || poVO.getPoStatus() == null || (poVO.getPoStatus() != 1 && poVO.getPoStatus() != 4)) {
			return null;
		}
		return poVO;
	}

	// 驗收時以登入的員工編號取出驗收員工
	public Employee getOneEmployee(Integer employeeId) {
		return employeeRepository.findById(employeeId).orElse(null);
	}

	// 驗收頁一開始顯示的表單資料：另外建立一份表單物件，不直接把資料庫查出來的那一筆交給頁面
	// 尚未驗收的採購單：驗收狀態、到貨數量、不良品數量都是空白，由使用者填寫（資料庫的預設值是 0，直接顯示會被誤以為已經填了）
	// 已結案的採購單（修改驗收紀錄）：帶入上次驗收存的驗收狀態、到貨數量、不良品數量
	@Transactional(readOnly = true)
	public PoVO getReceivingForm(PoVO dbPo) {
		boolean closed = dbPo.isClosed();

		PoVO formPo = new PoVO();
		formPo.setPoId(dbPo.getPoId());
		formPo.setPoNo(dbPo.getPoNo());
		formPo.setVendor(dbPo.getVendor());
		formPo.setPoEmployeeId(dbPo.getPoEmployeeId());
		formPo.setOrderDate(dbPo.getOrderDate());
		formPo.setPoStatus(dbPo.getPoStatus());
		formPo.setTotalAmount(dbPo.getTotalAmount());
		// 驗收員工、驗收日期只用來顯示：修改驗收紀錄時頁面顯示第一次驗收的紀錄（尚未驗收的單這兩個是空的，頁面改顯示登入的員工與現在的時間）
		formPo.setInboundEmployeeId(dbPo.getInboundEmployeeId());
		formPo.setInboundDate(dbPo.getInboundDate());
		formPo.setInboundStatus(closed ? dbPo.getInboundStatus() : null);
		formPo.setInboundAmount(closed ? dbPo.getInboundAmount() : Integer.valueOf(0));

		List<PoDetailVO> formDetails = new ArrayList<>();
		for (PoDetailVO dbDetail : dbPo.getPoDetails()) {
			PoDetailVO formDetail = new PoDetailVO();
			formDetail.setPoDetailId(dbDetail.getPoDetailId());
			formDetail.setPoId(formPo);
			formDetail.setSkuId(dbDetail.getSkuId());
			formDetail.setQuantity(dbDetail.getQuantity());
			formDetail.setUnitPrice(dbDetail.getUnitPrice());
			formDetail.setSubtotal(dbDetail.getSubtotal());
			formDetail.setArrivedPcs(closed ? dbDetail.getArrivedPcs() : null);
			formDetail.setDefectPcs(closed ? dbDetail.getDefectPcs() : null);
			formDetail.setInboundPcs(closed ? dbDetail.getInboundPcs() : Integer.valueOf(0));
			formDetail.setInboundSubtotal(closed ? dbDetail.getInboundSubtotal() : Integer.valueOf(0));
			formDetails.add(formDetail);
		}
		formPo.setPoDetails(formDetails);
		return formPo;
	}

	// 驗收前，把表單沒送的欄位從資料庫補回表單物件，檢查失敗重新顯示驗收頁時才有資料可以顯示
	// 表單只採用驗收狀態、每筆明細的到貨數量與不良品數量；其餘欄位一律以資料庫為準
	// 入庫數量、入庫小計、驗收實付金額不收表單的值，由這裡依到貨數量、不良品數量算出來
	// 採購單不存在、不是待驗收也不是已結案、明細和資料庫對不上時丟 IllegalArgumentException（不是欄位填錯，無法顯示在驗收頁）
	@Transactional(readOnly = true)
	public void prepareReceiving(PoVO formPo) {
		if (formPo.getPoId() == null) {
			throw new IllegalArgumentException("查無此採購單");
		}
		PoVO dbPo = poRepository.findById(formPo.getPoId())
				.orElseThrow(() -> new IllegalArgumentException("查無此採購單"));

		// 以資料庫的狀態判斷，不採信表單：待驗收的單是第一次驗收，已結案的單是修改驗收紀錄，其他狀態都不能驗收
		if (!dbPo.isReceivable() && !dbPo.isClosed()) {
			throw new IllegalArgumentException("此採購單不是待驗收或已結案狀態，無法驗收");
		}

		// 驗收頁不能增刪明細，送回來的明細要和資料庫的筆數、順序、明細編號完全一致，
		// 錯誤訊息裡的「第幾筆」才會對到正確的那一列
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
		// 驗收員工、驗收日期只用來顯示：修改驗收紀錄時頁面顯示第一次驗收的紀錄（尚未驗收的單這兩個是空的，頁面改顯示登入的員工與現在的時間）
		formPo.setInboundEmployeeId(dbPo.getInboundEmployeeId());
		formPo.setInboundDate(dbPo.getInboundDate());

		int inboundAmount = 0;
		for (int index = 0; index < dbDetails.size(); index++) {
			PoDetailVO dbDetail = dbDetails.get(index);
			PoDetailVO formDetail = formDetails.get(index);
			if (formDetail == null || !dbDetail.getPoDetailId().equals(formDetail.getPoDetailId())) {
				throw new IllegalArgumentException("採購明細與資料庫不一致，請重新進入驗收頁");
			}

			// 驗收失敗、已取消時，不由使用者決定的數量在這裡填好
			applyInboundStatus(formDetail, formPo.getInboundStatus());

			formDetail.setPoId(formPo);
			formDetail.setSkuId(dbDetail.getSkuId());
			formDetail.setQuantity(dbDetail.getQuantity());
			formDetail.setUnitPrice(dbDetail.getUnitPrice());
			formDetail.setSubtotal(dbDetail.getSubtotal());

			inboundAmount += fillInbound(formDetail);
		}
		formPo.setInboundAmount(inboundAmount);
	}

	// 採購單驗收內容的摘要：採購單狀態、驗收狀態，加上每筆明細的到貨數量、不良品數量（依明細編號由小到大）
	// 驗收頁顯示時算一次放在表單裡，存檔時再以資料庫當下的內容算一次，兩個不一樣就代表驗收頁開著的期間這張單被別人驗收或修改過
	public String getReceivedContent(PoVO dbPo) {
		Map<Integer, String> quantityByDetailId = new TreeMap<>();
		for (PoDetailVO dbDetail : dbPo.getPoDetails()) {
			quantityByDetailId.put(dbDetail.getPoDetailId(), dbDetail.getArrivedPcs() + "/" + dbDetail.getDefectPcs());
		}
		return dbPo.getPoStatus() + ";" + dbPo.getInboundStatus() + ";" + quantityByDetailId;
	}

	// 驗收狀態是否為可以選的結果：驗收成功（1）、驗收失敗（2）、已取消（3）
	public boolean isValidInboundStatus(Byte inboundStatus) {
		return inboundStatus != null && inboundStatus >= 1 && inboundStatus <= 3;
	}

	// 驗收數量的檢查，回傳錯誤訊息（沒有錯誤時是空的），由 ReceivingController 的 update 顯示在驗收頁「採購明細」的旁邊
	// 要在 prepareReceiving 之後呼叫：採購數量、規格、驗收失敗與已取消時的數量都已經填好
	// 每一筆明細：到貨數量不可空白、不可為負數、不可大於採購數量；不良品數量不可空白、不可為負數、不可大於到貨數量
	// （輸入的不是整數時在綁定階段就會被擋下，不會進到這裡）
	// 驗收失敗時不良品數量等於到貨數量，不是使用者填的，不檢查；已取消時到貨、不良品都是 0，全部不檢查
	// 驗收成功、驗收失敗：整張採購單至少要有一筆到貨數量大於 0，個別明細可以是 0
	public List<String> checkReceiving(PoVO formPo) {
		List<String> errorMessages = new ArrayList<>();
		Byte inboundStatus = formPo.getInboundStatus();
		if (isInboundCancelled(inboundStatus)) {
			return errorMessages;
		}
		boolean inboundFailed = isInboundFailed(inboundStatus);

		boolean anyArrived = false;
		for (int index = 0; index < formPo.getPoDetails().size(); index++) {
			PoDetailVO poDetailVO = formPo.getPoDetails().get(index);
			String rowLabel = "第 " + (index + 1) + " 筆（" + poDetailVO.getSkuId().getDisplayName() + "）：";
			Integer arrivedPcs = poDetailVO.getArrivedPcs();
			Integer defectPcs = poDetailVO.getDefectPcs();

			if (arrivedPcs == null) {
				errorMessages.add(rowLabel + "到貨數量，請勿空白");
			} else if (arrivedPcs < 0) {
				errorMessages.add(rowLabel + "到貨數量不可為負數");
			} else if (arrivedPcs > poDetailVO.getQuantity()) {
				errorMessages.add(rowLabel + "到貨數量不可大於採購數量");
			} else if (arrivedPcs > 0) {
				anyArrived = true;
			}

			if (!inboundFailed) {
				if (defectPcs == null) {
					errorMessages.add(rowLabel + "不良品數量，請勿空白");
				} else if (defectPcs < 0) {
					errorMessages.add(rowLabel + "不良品數量不可為負數");
				} else if (arrivedPcs != null && defectPcs > arrivedPcs) {
					errorMessages.add(rowLabel + "不良品數量不可大於到貨數量");
				}
			}
		}

		// 每一筆的數量都合規定之後才檢查整張單，避免同時出現兩種訊息
		if (errorMessages.isEmpty() && !anyArrived) {
			errorMessages.add("整張採購單至少要有一筆到貨數量大於 0；廠商的貨沒有來請改選「已取消」");
		}
		return errorMessages;
	}

	// 以到貨數量、不良品數量算出入庫數量與入庫小計並填入明細，回傳入庫小計供加總
	// 入庫數量 = 到貨數量 - 不良品數量；入庫小計 = 入庫數量 x 進貨單價
	// 數量不合規定時都放 0，錯誤由 checkReceiving 回報
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

	// 驗收狀態是否為驗收失敗（2）；驗收失敗時使用者只填到貨數量，不良品數量等於到貨數量，入庫數量與金額都是 0
	public boolean isInboundFailed(Byte inboundStatus) {
		return inboundStatus != null && inboundStatus == 2;
	}

	// 驗收狀態是否為已取消（3，廠商的貨沒有來）；已取消時到貨、不良品、入庫數量與金額都是 0
	public boolean isInboundCancelled(Byte inboundStatus) {
		return inboundStatus != null && inboundStatus == 3;
	}

	// 依驗收狀態填好不由使用者決定的數量，不採信表單送來的值：
	// 驗收失敗（2）：不良品數量 = 到貨數量（到貨數量由使用者填），入庫數量就會是 0
	// 已取消（3）：到貨數量、不良品數量都是 0
	// 驗收成功（1）：到貨數量、不良品數量都照使用者填的，不更動
	private void applyInboundStatus(PoDetailVO poDetailVO, Byte inboundStatus) {
		if (isInboundCancelled(inboundStatus)) {
			poDetailVO.setArrivedPcs(0);
			poDetailVO.setDefectPcs(0);
		} else if (isInboundFailed(inboundStatus)) {
			poDetailVO.setDefectPcs(poDetailVO.getArrivedPcs());
		}
	}

	// 到貨數量、不良品數量是否合規定：都有填、不是負數、到貨數量不超過採購數量（資料表有 arrived_pcs <= quantity 的限制）、不良品不超過到貨數量
	private boolean isValidQuantity(PoDetailVO poDetailVO) {
		Integer arrivedPcs = poDetailVO.getArrivedPcs();
		Integer defectPcs = poDetailVO.getDefectPcs();
		return arrivedPcs != null && defectPcs != null && arrivedPcs >= 0 && defectPcs >= 0
				&& arrivedPcs <= poDetailVO.getQuantity() && defectPcs <= arrivedPcs;
	}

	// 驗收存檔，回傳這次是不是修改驗收紀錄（true：已結案的單重新存驗收結果；false：待驗收的單第一次驗收）
	// 不直接 save(formPo)，只把驗收相關的欄位寫進資料庫查出來的採購單，其餘欄位不會被動到；金額由這裡重算
	//
	// 第一次驗收：採購單狀態改為已結案（4），驗收成功、驗收失敗、已取消都一樣；
	//   驗收員工由 ReceivingController 從 session 取得，驗收日期為存檔當下的時間；
	//   規格的 stock 加上入庫數量、inbound_qty 扣掉採購數量（驗收失敗、已取消時入庫數量是 0，只扣待進貨）
	// 修改驗收紀錄：驗收員工、驗收日期維持第一次驗收的值；
	//   規格的 stock 只調整入庫數量的差額（新的入庫數量 - 原本的入庫數量），inbound_qty 第一次驗收時已經扣過，不再動；
	//   差額是負的、而庫存已經不夠扣時（貨已經賣出去）不能修改
	// receivedContent 是驗收頁顯示當時的內容摘要（getReceivedContent），和資料庫當下的內容不一樣時不存檔
	// 全部在同一個交易裡，任何一步丟出例外，驗收欄位、採購單狀態和已經更新的庫存都會還原
	@Transactional
	public boolean receive(PoVO formPo, Employee inboundEmployee, String receivedContent) {
		if (formPo.getPoId() == null) {
			throw new IllegalArgumentException("查無此採購單");
		}
		// 先鎖住這張採購單並以資料庫當下的狀態決定是第一次驗收還是修改：
		// 兩個人同時送出同一張單時，後到的要等前一個存完，接著在下面比對內容摘要時被擋下
		boolean firstReceiving = !poRepository.lockReceivablePo(formPo.getPoId()).isEmpty();
		if (!firstReceiving && poRepository.lockClosedPo(formPo.getPoId()).isEmpty()) {
			throw new IllegalArgumentException("此採購單不是待驗收或已結案狀態，無法驗收");
		}

		PoVO dbPo = poRepository.findById(formPo.getPoId())
				.orElseThrow(() -> new IllegalArgumentException("查無此採購單"));
		// prepareReceiving 在同一個請求裡已經查過這張單，這裡拿到的可能是當時的舊資料；
		// 鎖住之後重新讀一次（明細也會一起重讀），原本的入庫數量才是資料庫當下的值，差額才算得對
		entityManager.refresh(dbPo);

		// 採購單已經鎖住，別人從這裡開始改不了，比對的內容不會再變
		// 不一樣（或沒有帶 receivedContent）代表驗收頁開著的期間，這張單已經被別人驗收或修改過，使用者看到的不是最新的內容
		if (!getReceivedContent(dbPo).equals(receivedContent)) {
			throw new IllegalArgumentException("這張採購單的驗收內容已經被其他人更新，下方是最新的內容；需要修改請重新進入驗收頁");
		}

		if (inboundEmployee == null) {
			throw new IllegalArgumentException("查無驗收員工");
		}

		Byte inboundStatus = formPo.getInboundStatus();
		if (!isValidInboundStatus(inboundStatus)) {
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

		// key 是規格編號；資料表有 po_id + sku_id 的唯一限制，一張單裡同一個規格只會有一筆明細
		Map<Integer, PoDetailVO> dbDetailsBySkuId = new TreeMap<>();
		// 各規格原本的入庫數量（第一次驗收時是 0），修改驗收紀錄時用來算庫存要調整的差額
		Map<Integer, Integer> oldInboundPcsBySkuId = new HashMap<>();
		boolean anyArrived = false;
		int inboundAmount = 0;
		for (PoDetailVO dbDetail : dbPo.getPoDetails()) {
			PoDetailVO formDetail = formDetailsById.get(dbDetail.getPoDetailId());
			if (formDetail == null) {
				throw new IllegalArgumentException("採購明細與資料庫不一致，請重新進入驗收頁");
			}
			Integer skuId = dbDetail.getSkuId().getSkuId();
			oldInboundPcsBySkuId.put(skuId, dbDetail.getInboundPcs() == null ? 0 : dbDetail.getInboundPcs());

			// 先放進表單填的數量，再依驗收狀態蓋掉不由使用者決定的（驗收失敗的不良品、已取消的到貨與不良品）
			dbDetail.setArrivedPcs(formDetail.getArrivedPcs());
			dbDetail.setDefectPcs(formDetail.getDefectPcs());
			applyInboundStatus(dbDetail, inboundStatus);
			// 數量、單價用資料庫的那一筆來檢查與計算，不採信表單
			if (!isValidQuantity(dbDetail)) {
				throw new IllegalArgumentException("到貨數量或不良品數量不正確，請重新進入驗收頁");
			}
			if (dbDetail.getArrivedPcs() > 0) {
				anyArrived = true;
			}
			inboundAmount += fillInbound(dbDetail);
			dbDetailsBySkuId.put(skuId, dbDetail);
		}

		// 驗收成功、驗收失敗：整張採購單至少要有一筆到貨數量大於 0；貨完全沒來要選已取消
		if (!isInboundCancelled(inboundStatus) && !anyArrived) {
			throw new IllegalArgumentException("整張採購單至少要有一筆到貨數量大於 0，請重新進入驗收頁");
		}

		// 依規格編號由小到大更新庫存，和訂單鎖定規格、審核加待進貨的順序一致，才不會互相等待
		for (Map.Entry<Integer, PoDetailVO> entry : dbDetailsBySkuId.entrySet()) {
			Integer skuId = entry.getKey();
			PoDetailVO dbDetail = entry.getValue();
			if (firstReceiving) {
				if (poSkuStockRepository.receiveStock(skuId, dbDetail.getInboundPcs(), dbDetail.getQuantity()) != 1) {
					throw new IllegalArgumentException("商品規格編號 " + skuId + " 不存在，無法更新庫存");
				}
				continue;
			}
			int stockChange = dbDetail.getInboundPcs() - oldInboundPcsBySkuId.get(skuId);
			if (stockChange != 0 && poSkuStockRepository.adjustStock(skuId, stockChange) != 1) {
				throw new IllegalArgumentException("商品規格「" + dbDetail.getSkuId().getDisplayName()
						+ "」的庫存不足以扣回原本的入庫數量（可能已經出貨），無法修改驗收紀錄");
			}
		}

		if (firstReceiving) {
			dbPo.setPoStatus((byte) 4); // 已結案
			dbPo.setInboundEmployeeId(inboundEmployee);
			dbPo.setInboundDate(LocalDateTime.now());
		}
		dbPo.setInboundStatus(inboundStatus);
		dbPo.setInboundAmount(inboundAmount);
		return !firstReceiving;
	}

}
