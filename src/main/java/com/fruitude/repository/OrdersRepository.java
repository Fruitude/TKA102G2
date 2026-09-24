package com.fruitude.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fruitude.entity.Orders;

public interface OrdersRepository extends JpaRepository<Orders, Integer>{

}
