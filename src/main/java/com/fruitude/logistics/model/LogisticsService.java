package com.fruitude.logistics.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.orders.model.Orders;

@Service
public class LogisticsService {

	// 配送頁每頁顯示的筆數
	private static final int PAGE_SIZE = 10;

	// 訂單日期由舊到新，同一時間的再依訂單編號由小到大
	private static final Sort DELIVERING_ORDERS_SORT = Sort.by("ordersDate", "ordersId");

	@Autowired
	private LogisticsOrderRepository ordersRepository;

	// 配送中的訂單（orders_status = 1）的其中一頁，訂單日期由舊到新
	// page 是畫面上的頁碼，從 1 開始；小於 1 當作第 1 頁，超過最後一頁就回傳最後一頁
	// ordersId 是搜尋用的訂單編號，null 代表不篩選
	public Page<Orders> getDeliveringOrders(int page, Integer ordersId) {
		int pageIndex = Math.max(page, 1) - 1; // PageRequest 的頁碼從 0 開始
		int ordersIdFilter = ordersId == null ? 0 : ordersId; // 0 代表不篩選，對應 LogisticsOrderRepository 的 searchByOrdersStatus
		Page<Orders> ordersPage = ordersRepository.searchByOrdersStatus(1, ordersIdFilter,
				PageRequest.of(pageIndex, PAGE_SIZE, DELIVERING_ORDERS_SORT));
		if (pageIndex > 0 && pageIndex >= ordersPage.getTotalPages()) {
			int lastPageIndex = Math.max(ordersPage.getTotalPages() - 1, 0);
			ordersPage = ordersRepository.searchByOrdersStatus(1, ordersIdFilter,
					PageRequest.of(lastPageIndex, PAGE_SIZE, DELIVERING_ORDERS_SORT));
		}
		return ordersPage;
	}

	// 配送：把勾選的訂單狀態由配送中（1）改成已配送（7），回傳實際配送的筆數
	// 全部寫在同一個交易，任何一張訂單不存在或已經不是配送中就丟 IllegalArgumentException，這一批訂單都不會被改到
	@Transactional
	public int deliverOrders(List<Integer> ordersIds) {
		// 去掉空值與重複的編號；一張都沒勾就擋下
		Set<Integer> distinctIds = new LinkedHashSet<>();
		if (ordersIds != null) {
			for (Integer ordersId : ordersIds) {
				if (ordersId != null) {
					distinctIds.add(ordersId);
				}
			}
		}
		if (distinctIds.isEmpty()) {
			throw new IllegalArgumentException("請先勾選要配送的訂單");
		}

		for (Integer ordersId : distinctIds) {
			if (ordersRepository.updateStatusToDelivered(ordersId) == 0) {
				throw new IllegalArgumentException("訂單編號-" + ordersId + " 已經不是配送中，無法配送");
			}
		}
		return distinctIds.size();
	}
}
