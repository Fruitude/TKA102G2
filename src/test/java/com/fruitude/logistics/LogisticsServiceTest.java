package com.fruitude.logistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.fruitude.logistics.model.LogisticsOrderRepository;
import com.fruitude.logistics.model.LogisticsService;

public class LogisticsServiceTest {

	// 假的 repository：只實作 updateStatusToDelivered，記下被呼叫的訂單編號；staleIds 裡的訂單視為已經不是配送中（更新 0 筆）
	private final List<Integer> updatedIds = new ArrayList<>();
	private Set<Integer> staleIds = Set.of();
	private LogisticsService logisticsService;

	@Before
	public void setUp() throws Exception {
		LogisticsOrderRepository repository = (LogisticsOrderRepository) Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[] { LogisticsOrderRepository.class },
				(proxy, method, args) -> {
					if (method.getName().equals("updateStatusToDelivered")) {
						Integer ordersId = (Integer) args[0];
						updatedIds.add(ordersId);
						return staleIds.contains(ordersId) ? 0 : 1;
					}
					throw new UnsupportedOperationException(method.getName());
				});
		logisticsService = new LogisticsService();
		Field field = LogisticsService.class.getDeclaredField("ordersRepository");
		field.setAccessible(true);
		field.set(logisticsService, repository);
	}

	@Test
	public void deliverOrdersRejectsEmptySelection() {
		assertThrows(IllegalArgumentException.class, () -> logisticsService.deliverOrders(List.of()));
		assertThrows(IllegalArgumentException.class, () -> logisticsService.deliverOrders(null));
		assertEquals(List.of(), updatedIds);
	}

	@Test
	public void deliverOrdersUpdatesEachDistinctOrderOnce() {
		int count = logisticsService.deliverOrders(Arrays.asList(1, 2, 2, null));

		assertEquals(2, count);
		assertEquals(List.of(1, 2), updatedIds);
	}

	@Test
	public void deliverOrdersThrowsWhenOrderIsNoLongerDelivering() {
		staleIds = Set.of(2);

		// @Transactional 會讓整批還原；這裡確認有丟例外，且訊息指出是哪一張
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> logisticsService.deliverOrders(List.of(1, 2, 3)));
		assertEquals("訂單編號-2 已經不是配送中，無法配送", error.getMessage());
		assertEquals(List.of(1, 2), updatedIds);
	}
}
