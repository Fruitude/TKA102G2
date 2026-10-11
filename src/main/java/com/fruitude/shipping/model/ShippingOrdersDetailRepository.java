package com.fruitude.shipping.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fruitude.orders.model.OrdersDetail;

public interface ShippingOrdersDetailRepository extends JpaRepository<OrdersDetail,Integer>{

	// 一張訂單的全部明細，依明細編號由小到大
	List<OrdersDetail> findByOrdersIdOrderByOrdersDetailIdAsc(Integer ordersId);

}
