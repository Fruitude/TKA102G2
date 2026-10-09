package com.fruitude.promo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.fruitude.promo.model.DiscountLine;
import com.fruitude.promo.model.ProductDiscount;
import com.fruitude.promo.model.PromoProject;
import com.fruitude.promo.model.PromoRepository;
import com.fruitude.promo.model.PromoService;
import com.fruitude.promo.model.PromoType;

// 壽星優惠每年限用一次、由會員勾選：勾選才參與折扣比較；勾選了但其他折扣更划算時，結果不是壽星月（不會用掉今年的資格）
public class BirthdayPromoDiscountTest {

	private PromoProject promo(int id, PromoType type, String title, int percentValue) {
		PromoProject p = new PromoProject();
		p.setPromoProjectId(id);
		p.setPromoType(type.name());
		p.setBenefitType("PERCENT_OFF");
		p.setBenefitValue(percentValue); // 90 = 9 折、70 = 7 折
		p.setPromoProjectTitle(title);
		p.setStatus(1);
		p.setPromoProjectStart(LocalDateTime.now().minusDays(1));
		p.setPromoProjectEnd(LocalDateTime.now().plusDays(1));
		return p;
	}

	private PromoService service(int storewideValue, int birthdayValue) throws Exception {
		PromoRepository repo = (PromoRepository) Proxy.newProxyInstance(PromoRepository.class.getClassLoader(),
				new Class<?>[] { PromoRepository.class }, (proxy, method, args) -> {
					if (method.getName().equals("findActiveByType")) {
						List<PromoProject> list = new ArrayList<>();
						if (PromoType.STOREWIDE.name().equals(args[0])) {
							list.add(promo(1, PromoType.STOREWIDE, "全館折扣", storewideValue));
						} else if (PromoType.BIRTHDAY_MONTH.name().equals(args[0])) {
							list.add(promo(2, PromoType.BIRTHDAY_MONTH, "壽星月", birthdayValue));
						}
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

	private List<DiscountLine> lines() {
		return List.of(new DiscountLine(1000, 1000, 1)); // 一件 1000 元，沒有活動價
	}

	@Test
	public void birthdayNotChosenIsIgnoredEvenIfBetter() throws Exception {
		ProductDiscount d = service(90, 70).calcProductDiscount(false, false, lines());
		assertEquals(100, d.amount()); // 只有全館 9 折
		assertEquals(PromoType.STOREWIDE.name(), d.promoType());
	}

	@Test
	public void birthdayChosenAndBetterWins() throws Exception {
		ProductDiscount d = service(90, 70).calcProductDiscount(true, false, lines());
		assertEquals(300, d.amount()); // 壽星 7 折
		assertEquals(PromoType.BIRTHDAY_MONTH.name(), d.promoType());
		assertEquals(Integer.valueOf(2), d.promoProjectId());
	}

	@Test
	public void birthdayChosenButOtherDiscountBetterIsNotApplied() throws Exception {
		ProductDiscount d = service(50, 70).calcProductDiscount(true, false, lines());
		assertEquals(500, d.amount()); // 全館 5 折比壽星 7 折划算
		assertEquals(PromoType.STOREWIDE.name(), d.promoType()); // 不是壽星月，下單時就不會記錄使用
	}

	@Test
	public void noDiscountHasNoPromoType() throws Exception {
		ProductDiscount d = ProductDiscount.NONE;
		assertEquals(0, d.amount());
		assertNull(d.promoType());
		assertNull(d.promoProjectId());
	}
}
