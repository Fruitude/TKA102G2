package com.fruitude.promo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.fruitude.promo.model.MemberPromoUsage;
import com.fruitude.promo.model.MemberPromoUsageRepository;
import com.fruitude.orders.model.OrdersService;
import com.fruitude.promo.model.HomePromos;
import com.fruitude.promo.model.MemberPromoState;
import com.fruitude.promo.model.PromoProject;
import com.fruitude.promo.model.PromoRepository;
import com.fruitude.promo.model.PromoService;
import com.fruitude.promo.model.PromoType;

// 常態福利的標籤：壽星月「本月適用／已使用」、新會員首購「首購適用／已使用」；訪客不顯示。
// 另外確認首購是否已用掉是看 member_promo_usage（終身紀錄，usage_year 為 0），不是看訂單
public class PromoBadgeStateTest {

	private PromoProject promo(int id, PromoType type, String title) {
		PromoProject p = new PromoProject();
		p.setPromoProjectId(id);
		p.setPromoType(type.name());
		p.setPromoProjectTitle(title);
		p.setBenefitType("PERCENT_OFF");
		p.setBenefitValue(70);
		p.setStatus(1);
		p.setPromoProjectStart(LocalDateTime.now().minusDays(1));
		p.setPromoProjectEnd(LocalDateTime.now().plusDays(1));
		return p;
	}

	private PromoService service() throws Exception {
		PromoRepository repo = (PromoRepository) Proxy.newProxyInstance(PromoRepository.class.getClassLoader(),
				new Class<?>[] { PromoRepository.class }, (proxy, method, args) -> {
					if (method.getName().equals("findActive")) {
						List<PromoProject> list = new ArrayList<>();
						list.add(promo(6, PromoType.BIRTHDAY_MONTH, "壽星月"));
						list.add(promo(9, PromoType.NEW_MEMBER_FIRST_ORDER, "新會員首購"));
						return list;
					}
					throw new AssertionError(method.getName());
				});
		PromoService service = new PromoService();
		Field f = PromoService.class.getDeclaredField("promoRepository");
		f.setAccessible(true);
		f.set(service, repo);
		return service;
	}

	private HomePromos.Item perk(HomePromos promos, int projectId) {
		for (HomePromos.Item item : promos.perks()) {
			if (item.promoProjectId() == projectId) {
				return item;
			}
		}
		throw new AssertionError("找不到活動 " + projectId);
	}

	@Test
	public void anonymousVisitorSeesNoBadges() throws Exception {
		HomePromos promos = service().findHomePromos(MemberPromoState.ANONYMOUS);
		assertFalse(perk(promos, 6).highlight() || perk(promos, 6).used());
		assertFalse(perk(promos, 9).highlight() || perk(promos, 9).used());
	}

	@Test
	public void firstPurchaseIsAvailableUntilItIsUsed() throws Exception {
		HomePromos noOrder = service().findHomePromos(new MemberPromoState(true, false, false, false));
		assertTrue(perk(noOrder, 9).highlight());
		assertEquals("首購適用", perk(noOrder, 9).highlightText());
		assertFalse(perk(noOrder, 9).used());

		HomePromos usedUp = service().findHomePromos(new MemberPromoState(true, false, false, true));
		assertTrue(perk(usedUp, 9).used());
		assertFalse(perk(usedUp, 9).highlight());
	}

	@Test
	public void birthdayBadgeStillWorks() throws Exception {
		HomePromos available = service().findHomePromos(new MemberPromoState(true, true, false, true));
		assertTrue(perk(available, 6).highlight());
		assertEquals("本月適用", perk(available, 6).highlightText());

		HomePromos used = service().findHomePromos(new MemberPromoState(true, true, true, true));
		assertTrue(perk(used, 6).used());
		assertFalse(perk(used, 6).highlight());
	}

	@Test
	public void firstPurchaseUsedLooksAtUsageRecordNotOrders() throws Exception {
		List<Object[]> asked = new ArrayList<>();
		MemberPromoUsageRepository repo = (MemberPromoUsageRepository) Proxy.newProxyInstance(
				MemberPromoUsageRepository.class.getClassLoader(), new Class<?>[] { MemberPromoUsageRepository.class },
				(proxy, method, args) -> {
					if (method.getName().equals("existsByMemberIdAndUsageYearAndPromoType")) {
						asked.add(args);
						return Integer.valueOf(5).equals(args[0]); // 只有 5 號會員有首購紀錄
					}
					throw new AssertionError(method.getName());
				});
		OrdersService service = new OrdersService();
		Field f = OrdersService.class.getDeclaredField("memberPromoUsageRepository");
		f.setAccessible(true);
		f.set(service, repo);
		assertTrue(service.isFirstPurchaseUsed(5));
		assertFalse(service.isFirstPurchaseUsed(6));
		// 首購是終身限用一次：usage_year 固定是 0，活動類型是 NEW_MEMBER_FIRST_ORDER
		assertEquals(Integer.valueOf(MemberPromoUsage.LIFETIME_YEAR), asked.get(0)[1]);
		assertEquals(PromoType.NEW_MEMBER_FIRST_ORDER.name(), asked.get(0)[2]);
	}
}
