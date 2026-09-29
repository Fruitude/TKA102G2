package com.fruitude.example.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.Tuple;

public interface ExampleOrdersRepository extends JpaRepository<ExampleOrders, Integer>{
	//注意，不用寫 SessionFactory/Session 下 HQL，Spring 已經包裝好，
	//使用 @Query 即可下指令
	@Query("SELECT O AS orders, M AS member FROM ExampleOrders O JOIN ExampleMember M ON O.memberId = M.memberId order by O.ordersId")
	List<Tuple> findAllWithJoin();

	@Modifying
	@Query("UPDATE ExampleOrders o SET o.ordersStatus = :ordersStatus WHERE o.ordersId = :ordersId")
	int updateStatus(@Param("ordersId") Integer ordersId, @Param("ordersStatus") Integer ordersStatus);
}
