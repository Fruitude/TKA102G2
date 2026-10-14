package com.fruitude.stock.model;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fruitude.product.model.ProductSku;

// 庫存管理對商品規格（product_sku）的查詢與庫存更新，和商品、訂單、出貨、採購模組的 repository 分開，避免互相修改
// 以下的查詢都只查 pageable 指定的那一頁，排序也由 pageable 帶入
// 商品一起查出來（join fetch），列表顯示商品名稱時才不會每一列各查一次
public interface StockSkuRepository extends JpaRepository<ProductSku,Integer>
{

	// 全部的規格
	@Query(value = "select s from ProductSku s left join fetch s.product",
			countQuery = "select count(s) from ProductSku s")
	Page<ProductSku> findSkusWithProduct(Pageable pageable);

	// 指定狀態（product_sku 的 status）的規格
	@Query(value = "select s from ProductSku s left join fetch s.product where s.status = :status",
			countQuery = "select count(s) from ProductSku s where s.status = :status")
	Page<ProductSku> findByStatusWithProduct(@Param("status") Byte status, Pageable pageable);

	// 指定編號的規格，查不到時回傳沒有內容的一頁；回傳 Page 是為了和其他查詢共用同一個列表
	@Query(value = "select s from ProductSku s left join fetch s.product where s.skuId = :skuId",
			countQuery = "select count(s) from ProductSku s where s.skuId = :skuId")
	Page<ProductSku> findBySkuIdWithProduct(@Param("skuId") Integer skuId, Pageable pageable);

	// 商品名稱符合 pattern 的規格；pattern 是 LIKE 的樣式（例如 %芒果%），裡面的 %、_、! 要先用 ! 跳脫（StockService 的 toLikePattern）
	@Query(value = "select s from ProductSku s left join fetch s.product p where p.productName like :pattern escape '!'",
			countQuery = "select count(s) from ProductSku s where s.product.productName like :pattern escape '!'")
	Page<ProductSku> findByProductNameWithProduct(@Param("pattern") String pattern, Pageable pageable);

	// 規格名稱（sku_name，不含別名）符合 pattern 的規格；pattern 的規則和 findByProductNameWithProduct 相同
	@Query(value = "select s from ProductSku s left join fetch s.product where s.skuName like :pattern escape '!'",
			countQuery = "select count(s) from ProductSku s where s.skuName like :pattern escape '!'")
	Page<ProductSku> findBySkuNameWithProduct(@Param("pattern") String pattern, Pageable pageable);

	// 調整頁可以改的四個數字組成的內容字串：庫存;安全庫存;允許超賣量;採購溢額量（例如 50;10;10;20）
	// 顯示調整頁與存檔時都用這一段 SQL 由資料庫算出來，兩邊的格式才會完全一樣
	String STOCK_CONTENT_SQL = """
			SELECT CONCAT_WS(';', COALESCE(stock, 0), COALESCE(safety_stock, 0),
			       COALESCE(max_backorder_qty, 0), COALESCE(purchase_add_on_qty, 0))
			FROM product_sku
			WHERE sku_id = :skuId
			""";

	// 這個規格目前的內容字串，查無此規格回傳 null；調整頁顯示時放在表單裡
	@Query(value = STOCK_CONTENT_SQL, nativeQuery = true)
	String findStockContent(@Param("skuId") Integer skuId);

	// 存檔前鎖住這個規格（FOR UPDATE）並取得資料庫當下的內容字串，一直到交易結束才放開，查無此規格回傳 null
	// 鎖住期間出貨、驗收、下單都改不了這個規格，所以鎖住之後讀到的內容才是準的
	// 必須在交易裡呼叫（StockService 的 updateStockQuantities）
	@Query(value = STOCK_CONTENT_SQL + " FOR UPDATE", nativeQuery = true)
	String lockStockContent(@Param("skuId") Integer skuId);

	// 調整頁存檔：只更新庫存、安全庫存、允許超賣量、採購溢額量四個欄位，回傳更新的筆數
	// 不把整筆規格寫回去，待進貨、待出貨、狀態等其他欄位才不會被蓋掉；回傳 0 代表查無此規格
	// 必須在交易裡呼叫（StockService 的 updateStockQuantities）
	@Modifying
	@Query(value = """
			UPDATE product_sku
			SET stock = :stock,
			    safety_stock = :safetyStock,
			    max_backorder_qty = :maxBackorderQty,
			    purchase_add_on_qty = :purchaseAddOnQty,
			    updated_at = CURRENT_TIMESTAMP
			WHERE sku_id = :skuId
			""", nativeQuery = true)
	int updateQuantities(@Param("skuId") Integer skuId, @Param("stock") Integer stock,
			@Param("safetyStock") Integer safetyStock, @Param("maxBackorderQty") Integer maxBackorderQty,
			@Param("purchaseAddOnQty") Integer purchaseAddOnQty);

}
