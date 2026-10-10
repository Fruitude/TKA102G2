package com.fruitude.po.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fruitude.product.model.ProductSku;

// 採購、審核、進貨對商品規格（product_sku）的查詢與庫存更新，和商品模組的 ProductSkuRepository 分開，避免互相修改
public interface PoSkuStockRepository extends JpaRepository<ProductSku, Integer> {

    // 某供應商底下所有商品的規格，排除指定狀態（採購單的規格下拉選單用來排除永久停產）
    List<ProductSku> findByProduct_Vendor_VendorIdAndStatusNotOrderBySkuIdAsc(
            Integer vendorId,
            Byte status);

    // 指定狀態、且低於安全庫存的規格：待出貨 + 安全庫存量 > 庫存量 + 待進貨
    @Query("""
            select s from ProductSku s
            join fetch s.product
            where s.status in :statuses
              and coalesce(s.outboundQty, 0) + coalesce(s.safetyStock, 0)
                > coalesce(s.stock, 0) + coalesce(s.inboundQty, 0)
            order by s.skuId asc
            """)
    List<ProductSku> findBelowSafetyStockByStatusIn(@Param("statuses") List<Byte> statuses);
    @Query("select s from ProductSku s join fetch s.product p where s.status in (1,2,3,4) and p.status<>2 and p.autoRestockEnabled=true order by s.skuId")
    List<ProductSku> findSupplyMonitoringCandidates();

}
