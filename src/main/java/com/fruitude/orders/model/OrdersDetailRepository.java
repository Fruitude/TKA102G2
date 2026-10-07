package com.fruitude.orders.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.Tuple;

public interface OrdersDetailRepository extends JpaRepository<OrdersDetail, Integer> {
	
	@Query("SELECT D as details, O as orders, S as sku, M as member "
			+ "FROM OrdersDetail as D \r\n"
			+ "JOIN Orders O on D.ordersId = O.ordersId\r\n"
			+ "JOIN ProductSku S on D.skuId = S.skuId\r\n"
			+ "JOIN MemberVO M on O.memberId = M.memberId\r\n"
			+ " where D.ordersId = :orderId")
	List<Tuple> getDetail(Integer orderId);

}
