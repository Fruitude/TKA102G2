package com.fruitude.promo.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.member.model.MemberCreditTransaction;
import com.fruitude.member.model.MemberCreditTransactionRepository;
import com.fruitude.orders.model.MemberRepository;

/**
 * 搶購物金（PromoType.WALLET_GRAB）：會員在活動期間內搶名額，搶到的入帳購物金。
 * 名額靠 PromoRepository.claimSlot 的單一 UPDATE 原子扣除；每位會員每場只有一筆參加紀錄（promo_grab 唯一鍵）。
 */
@Service
public class PromoGrabService {

	public enum Status {
		WON, LOST, ALREADY_WON, ALREADY_LOST, NOT_STARTED, ENDED, UNAVAILABLE
	}

	// credit：本次搶到的點數（沒搶到為 0）；shoppingCredit：會員目前的購物金
	public record GrabResult(Status status, Integer slotNo, int credit, int shoppingCredit) {
	}

	// 給前台倒數面板：start / end 是 epoch 毫秒，serverNow 是伺服器目前時間，前端用它校正本機時鐘的誤差
	public record GrabEvent(Integer promoProjectId, String title, long startMillis, long endMillis, int benefitValue,
			int quota, int granted, String myResult, Integer mySlotNo) {
	}

	public record GrabState(long serverNow, boolean loggedIn, Integer shoppingCredit, List<GrabEvent> events) {
	}

	public record Slot(int slotNo, Integer memberId) {
	}

	public record AdminView(int quota, int granted, List<Slot> slots) {
	}

	@Autowired
	private PromoRepository promoRepository;

	@Autowired
	private PromoGrabRepository promoGrabRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private MemberCreditTransactionRepository creditTransactionRepository;

	@Transactional
	public GrabResult grab(Integer memberId, Integer promoProjectId) {
		PromoProject promo = promoRepository.findById(promoProjectId).orElse(null);
		if (promo == null || !PromoType.WALLET_GRAB.name().equals(promo.getPromoType())
				|| !Integer.valueOf(1).equals(promo.getStatus()) || promo.getQuota() == null
				|| promo.getBenefitValue() == null || promo.getBenefitValue() < 1
				|| promo.getPromoProjectStart() == null || promo.getPromoProjectEnd() == null) {
			return new GrabResult(Status.UNAVAILABLE, null, 0, balanceOf(memberId));
		}
		LocalDateTime now = LocalDateTime.now();
		if (now.isBefore(promo.getPromoProjectStart())) {
			return new GrabResult(Status.NOT_STARTED, null, 0, balanceOf(memberId));
		}
		if (now.isAfter(promo.getPromoProjectEnd())) {
			return new GrabResult(Status.ENDED, null, 0, balanceOf(memberId));
		}
		PromoGrab existing = promoGrabRepository.findByPromoProjectIdAndMemberId(promoProjectId, memberId).orElse(null);
		if (existing != null) {
			return new GrabResult(existing.getSlotNo() != null ? Status.ALREADY_WON : Status.ALREADY_LOST,
					existing.getSlotNo(), 0, balanceOf(memberId));
		}

		// 先記下這位會員參加過（唯一鍵擋掉同時送出的重複請求；撞到唯一鍵會丟 DataIntegrityViolationException，由呼叫端處理）
		PromoGrab grab = promoGrabRepository.saveAndFlush(new PromoGrab(promoProjectId, memberId));

		if (promoRepository.claimSlot(promoProjectId, now) == 0) {
			return new GrabResult(Status.LOST, null, 0, balanceOf(memberId)); // 名額已滿：保留沒搶到的紀錄
		}
		// 名額那一列已被這個交易鎖住，讀到的就是剛剛 +1 後的值，當作得標序號
		int slotNo = promoRepository.findGrantedCount(promoProjectId);
		grab.setSlotNo(slotNo);

		int value = promo.getBenefitValue();
		if (memberRepository.addShoppingCredit(memberId, value) == 0) {
			throw new IllegalStateException("找不到會員，無法發放購物金"); // 例外讓名額、參加紀錄一起 rollback
		}
		int after = balanceOf(memberId);
		MemberCreditTransaction tx = new MemberCreditTransaction();
		tx.setMemberId(memberId);
		tx.setTransactionType(MemberCreditTransaction.TYPE_WALLET_GRAB);
		tx.setAmount(value);
		tx.setBalanceBefore(after - value);
		tx.setBalanceAfter(after);
		tx.setPromoProjectId(promoProjectId);
		tx.setReason("搶購物金：" + promo.getPromoProjectTitle());
		creditTransactionRepository.save(tx);
		return new GrabResult(Status.WON, slotNo, value, after);
	}

	// 搶購面板用：進行中與尚未開始的搶購物金活動（開始早的在前），連同這位會員的結果；memberId 為 null 代表未登入
	@Transactional(readOnly = true)
	public GrabState state(Integer memberId) {
		LocalDateTime now = LocalDateTime.now();
		List<GrabEvent> events = new ArrayList<>();
		for (PromoProject p : promoRepository.findUpcomingOrActiveByType(PromoType.WALLET_GRAB.name(), now)) {
			if (p.getQuota() == null || p.getBenefitValue() == null || p.getPromoProjectStart() == null
					|| p.getPromoProjectEnd() == null) {
				continue;
			}
			String myResult = "NONE";
			Integer mySlot = null;
			if (memberId != null) {
				PromoGrab mine = promoGrabRepository.findByPromoProjectIdAndMemberId(p.getPromoProjectId(), memberId)
						.orElse(null);
				if (mine != null) {
					mySlot = mine.getSlotNo();
					myResult = mySlot != null ? "WON" : "LOST";
				}
			}
			events.add(new GrabEvent(p.getPromoProjectId(), p.getPromoProjectTitle(), millis(p.getPromoProjectStart()),
					millis(p.getPromoProjectEnd()), p.getBenefitValue(), p.getQuota(), p.getGrantedCount(), myResult,
					mySlot));
		}
		return new GrabState(System.currentTimeMillis(), memberId != null, memberId == null ? null : balanceOf(memberId),
				events);
	}

	// 後台方塊畫面：名額、已搶出數，以及每個已搶到名額的序號與會員編號。找不到或不是搶購物金活動回傳 null
	@Transactional(readOnly = true)
	public AdminView adminView(Integer promoProjectId) {
		PromoProject promo = promoRepository.findById(promoProjectId).orElse(null);
		if (promo == null || !PromoType.WALLET_GRAB.name().equals(promo.getPromoType()) || promo.getQuota() == null) {
			return null;
		}
		List<Slot> slots = new ArrayList<>();
		for (PromoGrab g : promoGrabRepository.findByPromoProjectIdAndSlotNoIsNotNullOrderBySlotNo(promoProjectId)) {
			slots.add(new Slot(g.getSlotNo(), g.getMemberId()));
		}
		return new AdminView(promo.getQuota(), promo.getGrantedCount(), slots);
	}

	private int balanceOf(Integer memberId) {
		Integer credit = memberRepository.findShoppingCredit(memberId);
		return credit == null ? 0 : credit;
	}

	private static long millis(LocalDateTime time) {
		return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}
}
