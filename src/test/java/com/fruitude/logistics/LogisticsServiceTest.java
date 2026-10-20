package com.fruitude.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fruitude.logistics.model.LogisticsOrderRepository;
import com.fruitude.logistics.model.LogisticsService;

@ExtendWith(MockitoExtension.class)
public class LogisticsServiceTest {

	@Mock
	private LogisticsOrderRepository ordersRepository;

	@InjectMocks
	private LogisticsService logisticsService;

	@Test
	public void deliverOrdersRejectsEmptySelection() {
		assertThrows(IllegalArgumentException.class, () -> logisticsService.deliverOrders(List.of()));
		assertThrows(IllegalArgumentException.class, () -> logisticsService.deliverOrders(null));
		verify(ordersRepository, never()).updateStatusToDelivered(org.mockito.ArgumentMatchers.any());
	}

	@Test
	public void deliverOrdersUpdatesEachDistinctOrderOnce() {
		when(ordersRepository.updateStatusToDelivered(1)).thenReturn(1);
		when(ordersRepository.updateStatusToDelivered(2)).thenReturn(1);

		int count = logisticsService.deliverOrders(Arrays.asList(1, 2, 2, null));

		assertEquals(2, count);
		verify(ordersRepository).updateStatusToDelivered(1);
		verify(ordersRepository).updateStatusToDelivered(2);
	}

	@Test
	public void deliverOrdersThrowsWhenOrderIsNoLongerDelivering() {
		when(ordersRepository.updateStatusToDelivered(1)).thenReturn(1);
		when(ordersRepository.updateStatusToDelivered(2)).thenReturn(0);

		// @Transactional 會讓整批還原；這裡確認有丟例外，且訊息指出是哪一張
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> logisticsService.deliverOrders(List.of(1, 2)));
		assertEquals("訂單編號-2 已經不是配送中，無法配送", error.getMessage());
	}
}
