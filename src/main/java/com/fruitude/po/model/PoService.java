package com.fruitude.po.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.employee.model.Employee;
import com.fruitude.employee.model.EmployeeRepository;
import com.fruitude.podetail.model.PoDetailRepository;
import com.fruitude.podetail.model.PoDetailVO;
import com.fruitude.product.model.ProductSku;
import com.fruitude.product.model.ProductSkuRepository;
import com.fruitude.vendor.model.VendorRepository;

@Service
public class PoService {

	@Autowired
	private PoRepository repository;

	@Autowired
	private PoDetailRepository poDetailRepository;

	@Autowired
	private VendorRepository vendorRepository;

	@Autowired
	private ProductSkuRepository productSkuRepository;

	@Autowired
	private EmployeeRepository employeeRepository;
	
	

	public void addPo(PoVO poVO) {
		repository.save(poVO);
	}

	public void updatePo(PoVO poVO) {
		repository.save(poVO);
	}

	// 修改採購明細：以表單送回來的明細為準，可以修改、新增、刪除明細；小計與總金額由這裡重算
	// 不直接 save(formPo)，避免表單沒送的欄位（到貨數量、驗收欄位等）被寫成 null
	//
	// 表單的每一列要寫回哪一筆資料庫明細，是依「商品規格」決定，不是依明細編號：
	// 資料表有 po_id + sku_id 的唯一限制，而資料庫是一筆一筆寫入的（先新增、再更新、最後才刪除）。
	// 如果照明細編號直接改規格，或刪掉某規格的明細又新增一筆同規格，寫到一半會短暫出現兩筆相同規格而被擋下。
	// 所以分成三種情況處理：
	// 1. 規格原本就在這張單裡：把數量、單價寫到原本是那個規格的明細（不改規格）
	// 2. 規格是這張單原本沒有的：先沿用一筆「這次已經沒人要用」的明細改成新規格，不夠用才新增一筆
	// 3. 原本有、這次沒送回來的規格：沒被第 2 種沿用的才刪除
	// 這樣新增的一定是這張單原本沒有的規格、有新增就不會有刪除，任何時候都不會重複
	@Transactional
	public void updatePoWithDetails(PoVO formPo) {
		PoVO dbPo = repository.findById(formPo.getPoId())
				.orElseThrow(() -> new IllegalArgumentException("查無此採購單"));

		// 以資料庫的狀態判斷，不採信表單；開著修改頁期間被審核掉的單也會被擋下
		if (!dbPo.isEditable()) {
			throw new IllegalArgumentException("此採購單不是待審核狀態，無法修改");
		}

		if (formPo.getPoDetails() == null || formPo.getPoDetails().isEmpty()) {
			throw new IllegalArgumentException("採購明細至少要有一筆");
		}

		Set<Integer> formSkuIds = new HashSet<>();
		for (PoDetailVO formDetail : formPo.getPoDetails()) {
			if (formDetail == null || formDetail.getQuantity() == null || formDetail.getUnitPrice() == null) {
				throw new IllegalArgumentException("採購數量、進貨單價，請勿空白");
			}

			if (formDetail.getQuantity() < 1) {
				throw new IllegalArgumentException("採購數量必須大於0");
			}

			if (formDetail.getUnitPrice() < 0) {
				throw new IllegalArgumentException("進貨單價不可為負數");
			}

			if (formDetail.getSkuId() == null || formDetail.getSkuId().getSkuId() == null) {
				throw new IllegalArgumentException("商品規格，請勿空白");
			}

			// 同一張採購單不可有重複的商品規格
			if (!formSkuIds.add(formDetail.getSkuId().getSkuId())) {
				throw new IllegalArgumentException("同一張採購單不可有重複的商品規格");
			}
		}

		// 資料庫現有的明細依規格編號整理；這次沒送回來的規格是要拿掉的，已經有到貨或入庫記錄的不可拿掉
		Map<Integer, PoDetailVO> dbDetailsBySkuId = new HashMap<>();
		List<PoDetailVO> removedDbDetails = new ArrayList<>();
		for (PoDetailVO dbDetail : dbPo.getPoDetails()) {
			dbDetailsBySkuId.put(dbDetail.getSkuId().getSkuId(), dbDetail);
			if (!formSkuIds.contains(dbDetail.getSkuId().getSkuId())) {
				if (hasInboundRecord(dbDetail)) {
					throw new IllegalArgumentException(
							"「" + dbDetail.getSkuId().getDisplayName() + "」已有到貨或入庫數量，不可刪除或更換規格");
				}
				removedDbDetails.add(dbDetail);
			}
		}

		// 情況 1：規格原本就在這張單裡，數量、單價直接寫到原本是那個規格的明細
		List<PoDetailVO> newSkuFormDetails = new ArrayList<>();
		for (PoDetailVO formDetail : formPo.getPoDetails()) {
			PoDetailVO dbDetail = dbDetailsBySkuId.get(formDetail.getSkuId().getSkuId());
			if (dbDetail == null) {
				newSkuFormDetails.add(formDetail);
			} else {
				applyFormDetail(dbDetail, formDetail);
			}
		}

		// 情況 2：規格是這張單原本沒有的
		for (PoDetailVO formDetail : newSkuFormDetails) {
			ProductSku productSku = productSkuRepository.findById(formDetail.getSkuId().getSkuId())
					.orElseThrow(() -> new IllegalArgumentException("查無此商品規格"));

			// 下拉選單只列這張單供應商的規格，這裡擋直接送出其他編號的情況
			if (productSku.getProduct() == null || productSku.getProduct().getVendor() == null
					|| !productSku.getProduct().getVendor().getVendorId().equals(dbPo.getVendor().getVendorId())) {
				throw new IllegalArgumentException("商品規格不屬於此採購單的供應商");
			}

			if (ProductSku.STATUS_DISCONTINUED.equals(productSku.getStatus())) {
				throw new IllegalArgumentException("商品規格已永久停產，無法採購");
			}

			PoDetailVO dbDetail;
			if (removedDbDetails.isEmpty()) {
				// 沒有可以沿用的明細，新增一筆；poDetails 設了 cascade ALL，會跟著採購單一起存
				dbDetail = new PoDetailVO();
				dbDetail.setPoId(dbPo);
				dbPo.getPoDetails().add(dbDetail);
			} else {
				// 沿用一筆這次要拿掉的明細；優先用表單這一列原本的那筆，明細編號才會盡量維持不變
				dbDetail = removedDbDetails.get(0);
				for (PoDetailVO candidate : removedDbDetails) {
					if (candidate.getPoDetailId().equals(formDetail.getPoDetailId())) {
						dbDetail = candidate;
						break;
					}
				}
				removedDbDetails.remove(dbDetail);
			}

			dbDetail.setSkuId(productSku);
			applyFormDetail(dbDetail, formDetail);
		}

		// 情況 3：沒被沿用的才刪除；poDetails 設了 orphanRemoval，從清單移除就會刪掉資料列
		dbPo.getPoDetails().removeAll(removedDbDetails);

		// 總金額不收表單的值，以這張採購單所有明細的小計加總
		long totalAmount = 0;
		for (PoDetailVO dbDetail : dbPo.getPoDetails()) {
			totalAmount += dbDetail.getSubtotal();
		}
		if (totalAmount > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("總金額超過上限");
		}
		dbPo.setTotalAmount((int) totalAmount);
	}

	// 把表單一列的數量、單價寫進資料庫的明細，小計由這裡重算
	private void applyFormDetail(PoDetailVO dbDetail, PoDetailVO formDetail) {
		if (formDetail.getQuantity() < dbDetail.getArrivedPcs()) {
			throw new IllegalArgumentException("採購數量不可小於已到貨數量");
		}
		long subtotal = (long) formDetail.getQuantity() * formDetail.getUnitPrice();
		if (subtotal > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("小計超過上限");
		}
		dbDetail.setQuantity(formDetail.getQuantity());
		dbDetail.setUnitPrice(formDetail.getUnitPrice());
		dbDetail.setSubtotal((int) subtotal);
	}

	// 這筆明細是否已經有到貨、不良品或入庫的數量；有的話不可刪除或更換規格
	public boolean hasInboundRecord(PoDetailVO poDetailVO) {
		return poDetailVO.getArrivedPcs() > 0 || poDetailVO.getDefectPcs() > 0 || poDetailVO.getInboundPcs() > 0;
	}

	// 新增採購單前，把不由使用者決定的欄位填好，之後才能用 PoVO、PoDetailVO 上的註解驗證
	// 表單只採用供應商、每筆明細的規格、數量、單價（採購員工由 PoController 的 insert 填入登入的員工）；其餘欄位即使請求有帶也一律蓋掉
	public void prepareNewPo(PoVO poVO) {
		LocalDateTime now = LocalDateTime.now();

		poVO.setPoId(null); // 編號由資料庫產生，不採用請求帶來的值（避免覆蓋既有資料）
		poVO.setPoNo(generatePoNo(now.toLocalDate()));
		poVO.setOrderDate(now); // 採購日期為送出當下的時間
		poVO.setPoStatus((byte) 0); // 待審核
		poVO.setInboundEmployeeId(null);
		poVO.setInboundDate(null);
		poVO.setInboundStatus((byte) 0);
		poVO.setInboundAmount(0);

		if (poVO.getPoDetails() == null) {
			poVO.setPoDetails(new ArrayList<>());
		}
		poVO.getPoDetails().removeIf(Objects::isNull);

		long totalAmount = 0;
		for (PoDetailVO poDetailVO : poVO.getPoDetails()) {
			poDetailVO.setPoDetailId(null);
			poDetailVO.setPoId(poVO);
			poDetailVO.setArrivedPcs(0);
			poDetailVO.setDefectPcs(0);
			poDetailVO.setInboundPcs(0);
			poDetailVO.setInboundSubtotal(0);

			totalAmount += fillSubtotal(poDetailVO);
		}
		// 超過上限時放 null，由 PoVO 的 @NotNull（總金額）擋下
		poVO.setTotalAmount(totalAmount > Integer.MAX_VALUE ? null : (int) totalAmount);
	}

	// 修改採購單前，把表單沒送的欄位從資料庫補回表單物件，之後才能用 PoVO、PoDetailVO 上的註解驗證，
	// 檢查失敗重新顯示修改頁時，供應商、採購員工、日期、狀態也才有資料可以顯示
	// 表單只採用每筆明細的規格、數量、單價（可以比資料庫多或少幾筆）；其餘欄位即使請求有帶也一律以資料庫為準
	// 採購單不存在、不是待審核時丟 IllegalArgumentException（不是欄位填錯，無法顯示在欄位下方）
	@Transactional(readOnly = true)
	public void prepareUpdatePo(PoVO formPo) {
		if (formPo.getPoId() == null) {
			throw new IllegalArgumentException("查無此採購單");
		}
		PoVO dbPo = repository.findById(formPo.getPoId())
				.orElseThrow(() -> new IllegalArgumentException("查無此採購單"));

		// 以資料庫的狀態判斷，不採信表單；開著修改頁期間被審核掉的單也會被擋下
		if (!dbPo.isEditable()) {
			throw new IllegalArgumentException("此採購單不是待審核狀態，無法修改");
		}

		formPo.setPoNo(dbPo.getPoNo());
		formPo.setVendor(dbPo.getVendor());
		formPo.setPoEmployeeId(dbPo.getPoEmployeeId());
		formPo.setOrderDate(dbPo.getOrderDate());
		formPo.setPoStatus(dbPo.getPoStatus());
		formPo.setInboundEmployeeId(dbPo.getInboundEmployeeId());
		formPo.setInboundDate(dbPo.getInboundDate());
		formPo.setInboundStatus(dbPo.getInboundStatus());
		formPo.setInboundAmount(dbPo.getInboundAmount());

		if (formPo.getPoDetails() == null) {
			formPo.setPoDetails(new ArrayList<>());
		}
		formPo.getPoDetails().removeIf(Objects::isNull);

		// 和 updatePoWithDetails 一樣依商品規格對應資料庫的明細：規格原本就在這張單裡的，到貨等數量取那一筆的值
		Map<Integer, PoDetailVO> dbDetailsBySkuId = new HashMap<>();
		Set<Integer> dbDetailIds = new HashSet<>();
		for (PoDetailVO dbDetail : dbPo.getPoDetails()) {
			dbDetailsBySkuId.put(dbDetail.getSkuId().getSkuId(), dbDetail);
			dbDetailIds.add(dbDetail.getPoDetailId());
		}

		long totalAmount = 0;
		for (PoDetailVO formDetail : formPo.getPoDetails()) {
			// 請求沒帶商品規格時補一個空的，頁面與後續檢查才不用處理 null
			if (formDetail.getSkuId() == null) {
				formDetail.setSkuId(new ProductSku());
			}
			// 明細編號只用來在沿用明細時盡量維持原本的編號；不屬於這張單的編號一律當成沒有
			if (formDetail.getPoDetailId() != null && !dbDetailIds.contains(formDetail.getPoDetailId())) {
				formDetail.setPoDetailId(null);
			}

			PoDetailVO dbDetail = dbDetailsBySkuId.get(formDetail.getSkuId().getSkuId());
			formDetail.setPoId(formPo);
			// 新的規格還沒有任何到貨記錄
			formDetail.setArrivedPcs(dbDetail == null ? 0 : dbDetail.getArrivedPcs());
			formDetail.setDefectPcs(dbDetail == null ? 0 : dbDetail.getDefectPcs());
			formDetail.setInboundPcs(dbDetail == null ? 0 : dbDetail.getInboundPcs());
			formDetail.setInboundSubtotal(dbDetail == null ? 0 : dbDetail.getInboundSubtotal());

			totalAmount += fillSubtotal(formDetail);
		}
		// 超過上限時放 null，由 PoVO 的 @NotNull（總金額）擋下
		formPo.setTotalAmount(totalAmount > Integer.MAX_VALUE ? null : (int) totalAmount);
	}

	// 以數量乘單價算出小計並填入明細，回傳小計供加總
	// 數量、單價不合規定時小計放 0，錯誤由欄位自己的註解回報，不讓小計多報一次
	private long fillSubtotal(PoDetailVO poDetailVO) {
		long subtotal = 0;
		if (poDetailVO.getQuantity() != null && poDetailVO.getQuantity() >= 1
				&& poDetailVO.getUnitPrice() != null && poDetailVO.getUnitPrice() >= 0) {
			// 以 long 計算，避免數量乘單價超過 Integer 上限時變成錯誤的數字
			subtotal = (long) poDetailVO.getQuantity() * poDetailVO.getUnitPrice();
		}
		// 超過上限時放 null，由 PoDetailVO 的 @NotNull（小計）擋下
		poDetailVO.setSubtotal(subtotal > Integer.MAX_VALUE ? null : (int) subtotal);
		return subtotal;
	}

	// 採購單編號：PO + 日期 8 碼 + 當天流水號 4 碼，例如 PO202610080001
	// 取當天編號最大的一筆加 1；當天還沒有採購單時從 0001 開始
	private String generatePoNo(LocalDate orderDate) {
		String poNoPrefix = "PO" + orderDate.format(DateTimeFormatter.BASIC_ISO_DATE);

		int serialNumber = 1;
		Optional<PoVO> lastPo = repository.findTopByPoNoStartingWithOrderByPoNoDesc(poNoPrefix);
		if (lastPo.isPresent()) {
			try {
				serialNumber = Integer.parseInt(lastPo.get().getPoNo().substring(poNoPrefix.length())) + 1;
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("現有的採購單編號格式不符，無法產生新編號：" + lastPo.get().getPoNo());
			}
		}
		return poNoPrefix + String.format("%04d", serialNumber);
	}

	public void deletePo(Integer PoId) {
		if (repository.existsById(PoId))
			repository.deleteById(PoId);
	}


	public PoVO getOnePo(Integer PoId) {
		Optional<PoVO> optional = repository.findById(PoId);
		return optional.orElse(null); // public T orElse(T other) : 如果值存在就回傳其值，否則回傳other的值
	}

	public List<PoVO> getAll() {
		return repository.findAll();
	}
	
	public List<PoVO> getByPoStatus(Byte poStatus){
		return repository.getByPoStatus(poStatus);
	}

	// 以下四個給 PoNoController 的條件查詢使用
	public PoVO getOneByPoNo(String poNo) {
		return repository.findByPoNo(poNo).orElse(null);
	}

	public List<PoVO> getByVendorId(Integer vendorId) {
		return repository.findByVendor_VendorIdOrderByPoIdDesc(vendorId);
	}

	public List<PoVO> getByPoEmployeeId(Integer employeeId) {
		return repository.findByPoEmployeeId_EmployeeIdOrderByPoIdDesc(employeeId);
	}

	public List<PoVO> getByVendorName(String vendorName) {
		return repository.findByVendor_VendorNameContainingOrderByPoIdDesc(vendorName);
	}

	// 新增採購單時以登入的員工編號取出採購員工
	public Employee getOneEmployee(Integer employeeId) {
		return employeeRepository.findById(employeeId).orElse(null);
	}
	


}
