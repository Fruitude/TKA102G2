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

	// 修改採購明細：從資料庫取出原資料，只覆蓋採購數量與進貨單價，小計與總金額由這裡重算
	// 不直接 save(formPo)，避免表單沒送的欄位被寫成 null、沒送回來的明細被 orphanRemoval 刪除
	@Transactional
	public void updatePoWithDetails(PoVO formPo) {
		PoVO dbPo = repository.findById(formPo.getPoId())
				.orElseThrow(() -> new IllegalArgumentException("查無此採購單"));

		// 以資料庫的狀態判斷，不採信表單；開著修改頁期間被審核掉的單也會被擋下
		if (!dbPo.isEditable()) {
			throw new IllegalArgumentException("此採購單不是待審核狀態，無法修改");
		}

		for (PoDetailVO formDetail : formPo.getPoDetails()) {
			if (formDetail.getPoDetailId() == null) {
				throw new IllegalArgumentException("採購明細編號遺失");
			}

			PoDetailVO dbDetail = poDetailRepository.findById(formDetail.getPoDetailId())
					.orElseThrow(() -> new IllegalArgumentException("查無此採購明細"));

			// 確認這筆明細真的屬於這張採購單
			if (!dbDetail.getPoId().getPoId().equals(dbPo.getPoId())) {
				throw new IllegalArgumentException("採購明細不屬於此採購單");
			}

			if (formDetail.getQuantity() == null || formDetail.getUnitPrice() == null) {
				throw new IllegalArgumentException("採購數量、進貨單價，請勿空白");
			}

			if (formDetail.getQuantity() < 1) {
				throw new IllegalArgumentException("採購數量必須大於0");
			}

			if (formDetail.getUnitPrice() < 0) {
				throw new IllegalArgumentException("進貨單價不可為負數");
			}

			if (formDetail.getQuantity() < dbDetail.getArrivedPcs()) {
				throw new IllegalArgumentException("採購數量不可小於已到貨數量");
			}

			if (formDetail.getSkuId() == null || formDetail.getSkuId().getSkuId() == null) {
				throw new IllegalArgumentException("商品規格，請勿空白");
			}

			// 規格有更換時才檢查；沒換的明細即使原規格已停產也照原樣保留
			Integer formSkuId = formDetail.getSkuId().getSkuId();
			if (!formSkuId.equals(dbDetail.getSkuId().getSkuId())) {
				ProductSku productSku = productSkuRepository.findById(formSkuId)
						.orElseThrow(() -> new IllegalArgumentException("查無此商品規格"));

				// 下拉選單只列這張單供應商的規格，這裡擋直接送出其他編號的情況
				if (productSku.getProduct() == null || productSku.getProduct().getVendor() == null
						|| !productSku.getProduct().getVendor().getVendorId().equals(dbPo.getVendor().getVendorId())) {
					throw new IllegalArgumentException("商品規格不屬於此採購單的供應商");
				}

				if (ProductSku.STATUS_DISCONTINUED.equals(productSku.getStatus())) {
					throw new IllegalArgumentException("商品規格已永久停產，無法採購");
				}

				dbDetail.setSkuId(productSku);
			}

			dbDetail.setQuantity(formDetail.getQuantity());
			dbDetail.setUnitPrice(formDetail.getUnitPrice());
			dbDetail.setSubtotal(formDetail.getQuantity() * formDetail.getUnitPrice());
		}

		// 同一張採購單不可有重複的商品規格（資料表有 po_id + sku_id 的唯一限制）
		Set<Integer> skuIds = new HashSet<>();
		for (PoDetailVO dbDetail : dbPo.getPoDetails()) {
			if (!skuIds.add(dbDetail.getSkuId().getSkuId())) {
				throw new IllegalArgumentException("同一張採購單不可有重複的商品規格");
			}
		}

		// 總金額不收表單的值，以這張採購單所有明細的小計加總
		int totalAmount = 0;
		for (PoDetailVO dbDetail : dbPo.getPoDetails()) {
			totalAmount += dbDetail.getSubtotal();
		}
		dbPo.setTotalAmount(totalAmount);
	}

	// 新增採購單前，把不由使用者決定的欄位填好，之後才能用 PoVO、PoDetailVO 上的註解驗證
	// 表單只採用供應商、採購員工、每筆明細的規格、數量、單價；其餘欄位即使請求有帶也一律蓋掉
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
	// 表單只採用每筆明細的規格、數量、單價；其餘欄位即使請求有帶也一律以資料庫為準
	// 採購單不存在、不是待審核、明細和資料庫對不起來時丟 IllegalArgumentException（不是欄位填錯，無法顯示在欄位下方）
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

		// 修改頁會把這張單的每一筆明細都送回來，筆數或編號對不上代表頁面資料已過期或請求被改過
		Map<Integer, PoDetailVO> dbDetails = new HashMap<>();
		for (PoDetailVO dbDetail : dbPo.getPoDetails()) {
			dbDetails.put(dbDetail.getPoDetailId(), dbDetail);
		}
		if (formPo.getPoDetails().size() != dbDetails.size()) {
			throw new IllegalArgumentException("採購明細與資料庫不一致，請重新進入修改頁");
		}

		Set<Integer> poDetailIds = new HashSet<>();
		long totalAmount = 0;
		for (PoDetailVO formDetail : formPo.getPoDetails()) {
			if (formDetail.getPoDetailId() == null) {
				throw new IllegalArgumentException("採購明細編號遺失");
			}
			PoDetailVO dbDetail = dbDetails.get(formDetail.getPoDetailId());
			if (dbDetail == null) {
				throw new IllegalArgumentException("採購明細不屬於此採購單");
			}
			if (!poDetailIds.add(formDetail.getPoDetailId())) {
				throw new IllegalArgumentException("採購明細與資料庫不一致，請重新進入修改頁");
			}

			formDetail.setPoId(formPo);
			formDetail.setArrivedPcs(dbDetail.getArrivedPcs());
			formDetail.setDefectPcs(dbDetail.getDefectPcs());
			formDetail.setInboundPcs(dbDetail.getInboundPcs());
			formDetail.setInboundSubtotal(dbDetail.getInboundSubtotal());
			// 請求沒帶商品規格時補一個空的，頁面與後續檢查才不用處理 null
			if (formDetail.getSkuId() == null) {
				formDetail.setSkuId(new ProductSku());
			}

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

	// 新增採購單頁面的採購員工下拉選單
	public List<Employee> getAllEmployees() {
		return employeeRepository.findAll();
	}

	public Employee getOneEmployee(Integer employeeId) {
		return employeeRepository.findById(employeeId).orElse(null);
	}

}
