package com.fruitude.podetail.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PoDetailRepository extends JpaRepository<PoDetailVO, Integer> {
    List<PoDetailVO> findByPoIdPoId(Integer poId);   // 查詢條件是 PoDetailVO.poId（PoVO 物件）的 poId

    // 待審核（poStatus = 0）採購單裡，各商品規格的採購數量加總；每列是 [規格編號, 數量加總]
    @Query("""
            select d.skuId.skuId, sum(d.quantity)
            from PoDetailVO d
            where d.poId.poStatus = 0
            group by d.skuId.skuId
            """)
    List<Object[]> sumPendingQuantityBySkuId();

}
