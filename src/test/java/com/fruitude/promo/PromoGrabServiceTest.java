package com.fruitude.promo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import com.fruitude.member.model.MemberCreditTransaction;
import com.fruitude.member.model.MemberCreditTransactionRepository;
import com.fruitude.orders.model.MemberRepository;
import com.fruitude.promo.model.PromoGrab;
import com.fruitude.promo.model.PromoGrabRepository;
import com.fruitude.promo.model.PromoGrabService;
import com.fruitude.promo.model.PromoGrabService.GrabResult;
import com.fruitude.promo.model.PromoGrabService.Status;
import com.fruitude.promo.model.PromoProject;
import com.fruitude.promo.model.PromoRepository;
import com.fruitude.promo.model.PromoType;

// 搶購物金：活動期間檢查、名額用完、重複搶、入帳與流水。資料庫用 Proxy 假資料表，名額計數寫在這裡模擬 claimSlot 的 UPDATE
public class PromoGrabServiceTest {

	private static class Fixture {
		PromoProject promo = new PromoProject();
		int granted = 0;
		Map<Integer, Integer> credit = new HashMap<>();
		List<PromoGrab> grabs = new ArrayList<>();
		List<MemberCreditTransaction> transactions = new ArrayList<>();
		PromoGrabService service;
	}

	private Fixture fixture(LocalDateTime start, LocalDateTime end, int quota, int value) throws Exception {
		Fixture fx = new Fixture();
		fx.promo.setPromoProjectId(1);
		fx.promo.setPromoType(PromoType.WALLET_GRAB.name());
		fx.promo.setBenefitType("WALLET_CREDIT");
		fx.promo.setBenefitValue(value);
		fx.promo.setQuota(quota);
		fx.promo.setStatus(1);
		fx.promo.setPromoProjectTitle("搶購物金");
		fx.promo.setPromoProjectStart(start);
		fx.promo.setPromoProjectEnd(end);

		PromoRepository promoRepo = proxy(PromoRepository.class, (name, args) -> switch (name) {
			case "findById" -> Optional.of(fx.promo);
			case "claimSlot" -> {
				if (fx.granted >= fx.promo.getQuota()) yield 0;
				fx.granted++;
				yield 1;
			}
			case "findGrantedCount" -> fx.granted;
			default -> throw new AssertionError(name);
		});
		PromoGrabRepository grabRepo = proxy(PromoGrabRepository.class, (name, args) -> switch (name) {
			case "findByPromoProjectIdAndMemberId" -> fx.grabs.stream()
					.filter(g -> g.getMemberId().equals(args[1])).findFirst();
			case "saveAndFlush" -> {
				fx.grabs.add((PromoGrab) args[0]);
				yield args[0];
			}
			default -> throw new AssertionError(name);
		});
		MemberRepository memberRepo = proxy(MemberRepository.class, (name, args) -> switch (name) {
			case "addShoppingCredit" -> {
				fx.credit.merge((Integer) args[0], (Integer) args[1], Integer::sum);
				yield 1;
			}
			case "findShoppingCredit" -> fx.credit.getOrDefault(args[0], 0);
			default -> throw new AssertionError(name);
		});
		MemberCreditTransactionRepository txRepo = proxy(MemberCreditTransactionRepository.class, (name, args) -> {
			if (name.equals("save")) {
				fx.transactions.add((MemberCreditTransaction) args[0]);
				return args[0];
			}
			throw new AssertionError(name);
		});

		fx.service = new PromoGrabService();
		inject(fx.service, "promoRepository", promoRepo);
		inject(fx.service, "promoGrabRepository", grabRepo);
		inject(fx.service, "memberRepository", memberRepo);
		inject(fx.service, "creditTransactionRepository", txRepo);
		return fx;
	}

	private interface Handler {
		Object handle(String name, Object[] args);
	}

	@SuppressWarnings("unchecked")
	private <T> T proxy(Class<T> type, Handler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
				(p, method, args) -> handler.handle(method.getName(), args));
	}

	private void inject(Object target, String field, Object value) throws Exception {
		Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}

	private LocalDateTime ago() { return LocalDateTime.now().minusHours(1); }
	private LocalDateTime later() { return LocalDateTime.now().plusHours(1); }

	@Test
	public void beforeStartIsRejectedWithoutTouchingQuota() throws Exception {
		Fixture fx = fixture(later(), later().plusHours(1), 3, 500);
		GrabResult r = fx.service.grab(1, 1);
		assertEquals(Status.NOT_STARTED, r.status());
		assertEquals(0, fx.granted);
		assertEquals(0, fx.grabs.size());
	}

	@Test
	public void afterEndIsRejected() throws Exception {
		Fixture fx = fixture(ago().minusHours(2), ago(), 3, 500);
		assertEquals(Status.ENDED, fx.service.grab(1, 1).status());
	}

	@Test
	public void disabledPromoIsUnavailable() throws Exception {
		Fixture fx = fixture(ago(), later(), 3, 500);
		fx.promo.setStatus(0);
		assertEquals(Status.UNAVAILABLE, fx.service.grab(1, 1).status());
	}

	@Test
	public void winnerGetsSlotCreditAndLedgerRow() throws Exception {
		Fixture fx = fixture(ago(), later(), 3, 500);
		fx.credit.put(7, 100);
		GrabResult r = fx.service.grab(7, 1);
		assertEquals(Status.WON, r.status());
		assertEquals(Integer.valueOf(1), r.slotNo());
		assertEquals(500, r.credit());
		assertEquals(600, r.shoppingCredit());
		assertEquals(1, fx.transactions.size());
		MemberCreditTransaction tx = fx.transactions.get(0);
		assertEquals(Byte.valueOf(MemberCreditTransaction.TYPE_WALLET_GRAB), tx.getTransactionType());
		assertEquals(Integer.valueOf(500), tx.getAmount());
		assertEquals(Integer.valueOf(100), tx.getBalanceBefore());
		assertEquals(Integer.valueOf(600), tx.getBalanceAfter());
		assertEquals(Integer.valueOf(1), tx.getPromoProjectId());
	}

	@Test
	public void slotsBeyondQuotaLoseAndGetNoCredit() throws Exception {
		Fixture fx = fixture(ago(), later(), 2, 500);
		assertEquals(Status.WON, fx.service.grab(1, 1).status());
		assertEquals(Status.WON, fx.service.grab(2, 1).status());
		GrabResult third = fx.service.grab(3, 1);
		assertEquals(Status.LOST, third.status());
		assertNull(third.slotNo());
		assertEquals(0, third.credit());
		assertEquals(0, fx.credit.getOrDefault(3, 0).intValue());
		assertEquals(2, fx.transactions.size());
		assertEquals(2, fx.granted);
	}

	@Test
	public void sameMemberCannotGrabTwice() throws Exception {
		Fixture fx = fixture(ago(), later(), 5, 500);
		assertEquals(Status.WON, fx.service.grab(9, 1).status());
		GrabResult again = fx.service.grab(9, 1);
		assertEquals(Status.ALREADY_WON, again.status());
		assertEquals(0, again.credit());
		assertEquals(500, fx.credit.get(9).intValue()); // 只入帳一次
		assertEquals(1, fx.granted);
	}

	@Test
	public void loserCannotRetry() throws Exception {
		Fixture fx = fixture(ago(), later(), 1, 500);
		fx.service.grab(1, 1);
		assertEquals(Status.LOST, fx.service.grab(2, 1).status());
		assertEquals(Status.ALREADY_LOST, fx.service.grab(2, 1).status());
	}
}
