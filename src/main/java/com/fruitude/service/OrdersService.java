package com.fruitude.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fruitude.entity.Member;
import com.fruitude.entity.Orders;
import com.fruitude.repository.MemberRepository;
import com.fruitude.repository.OrdersRepository;

import jakarta.persistence.Tuple;

@Service
public class OrdersService {
	@Autowired
	private OrdersRepository ordersRepository;

	@Autowired
	private MemberRepository memberRepository;

	public List<Orders> findAll() {
		return ordersRepository.findAll();
	}

	public List<Tuple> findAllWithJoin() {
		return ordersRepository.findAllWithJoin();
	}

	public Optional<Orders> findById(Integer id) {
		return ordersRepository.findById(id);
	}

	public List<Member> getMemberList() {
		return memberRepository.findAll();
	}

	public boolean insert(Orders entity) {
		try {
			entity.setOrdersId(null); // 交給資料庫自動編號
			if (entity.getOrdersDate() == null) {
				entity.setOrdersDate(LocalDateTime.now());
			}
			ordersRepository.save(entity);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean delete(Integer ordersId) {
		if (!ordersRepository.existsById(ordersId)) {
			return false;
		}
		ordersRepository.deleteById(ordersId);
		return true;
	}

	// 先查出既有的訂單，只覆蓋表單可以修改的欄位（ordersId、ordersDate 不動）
	public boolean updateOrders(Integer ordersId, Orders form) {
		Optional<Orders> optional = ordersRepository.findById(ordersId);
		if (optional.isEmpty()) {
			return false;
		}
		Orders orders = optional.get();
		orders.setMemberId(form.getMemberId());
		orders.setShippingAddress(form.getShippingAddress());
		orders.setPaymentMethod(form.getPaymentMethod());
		orders.setProductTotal(form.getProductTotal());
		orders.setDiscount(form.getDiscount());
		orders.setShippingFee(form.getShippingFee());
		orders.setShoppingCredit(form.getShoppingCredit());
		orders.setActualPaymentAmount(form.getActualPaymentAmount());
		orders.setReceiverName(form.getReceiverName());
		orders.setEmail(form.getEmail());
		orders.setPhoneNumber(form.getPhoneNumber());
		orders.setLogisticsNote(form.getLogisticsNote());
		orders.setOrdersNote(form.getOrdersNote());
		orders.setOrdersStatus(form.getOrdersStatus());
		orders.setEmployeeId(form.getEmployeeId());
		ordersRepository.save(orders);
		return true;
	}

	// 做法一：先查出既有的 entity，只改 ordersStatus，再存回去
	public boolean updateStatusByLoad(Integer ordersId, Integer ordersStatus) {
		Optional<Orders> optional = ordersRepository.findById(ordersId);
		if (optional.isEmpty()) {
			return false;
		}
		Orders orders = optional.get();
		orders.setOrdersStatus(ordersStatus);
		ordersRepository.save(orders);
		return true;
	}

	// 做法二：直接下 JPQL UPDATE，只改 ordersStatus
	@Transactional
	public boolean updateStatusByQuery(Integer ordersId, Integer ordersStatus) {
		int updatedRows = ordersRepository.updateStatus(ordersId, ordersStatus);
		return updatedRows > 0;
	}
}
