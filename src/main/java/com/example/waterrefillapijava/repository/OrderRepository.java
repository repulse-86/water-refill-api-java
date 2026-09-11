package com.example.waterrefillapijava.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.waterrefillapijava.dto.BoardOrderResponse;
import com.example.waterrefillapijava.model.Order;
import com.example.waterrefillapijava.model.OrderStatus;
import com.example.waterrefillapijava.model.OrderType;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

	Page<Order> findByOrderType(OrderType orderType, Pageable pageable);

	Page<Order> findByStatus(OrderStatus status, Pageable pageable);

	Page<Order> findByOrderTypeAndStatus(OrderType orderType, OrderStatus status, Pageable pageable);

	@Query("SELECT o FROM Order o WHERE o.customer.name LIKE %:search% OR CAST(o.id AS string) LIKE %:search%")
	Page<Order> findBySearch(@Param("search") String search, Pageable pageable);

	@Query("SELECT o FROM Order o WHERE o.orderType = :orderType AND (o.customer.name LIKE %:search% OR CAST(o.id AS string) LIKE %:search%)")
	Page<Order> findByOrderTypeAndSearch(@Param("orderType") OrderType orderType, @Param("search") String search, Pageable pageable);

	@Query("SELECT o FROM Order o WHERE o.status = :status AND (o.customer.name LIKE %:search% OR CAST(o.id AS string) LIKE %:search%)")
	Page<Order> findByStatusAndSearch(@Param("status") OrderStatus status, @Param("search") String search, Pageable pageable);

	@Query("SELECT o FROM Order o WHERE o.orderType = :orderType AND o.status = :status AND (o.customer.name LIKE %:search% OR CAST(o.id AS string) LIKE %:search%)")
	Page<Order> findByOrderTypeAndStatusAndSearch(@Param("orderType") OrderType orderType, @Param("status") OrderStatus status, @Param("search") String search, Pageable pageable);

	@Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer ORDER BY o.id DESC")
	List<Order> findAllForBoard();

	@Query("""
		SELECT new com.example.waterrefillapijava.dto.BoardOrderResponse(
			o.id,
			c.id,
			COALESCE(c.name, 'Walk-in'),
			o.orderType,
			o.status,
			o.paymentMethod,
			o.totalAmount,
			o.amountPaid,
			o.changeReturned,
			o.deliveryFee,
			o.notes,
			o.deliveryAddress,
			o.deliveryStatus,
			o.deliveredAt,
			o.bottlesReturnedAtDelivery,
			o.cashCollectedAtDelivery,
			o.createdAt,
			o.modifiedAt
		)
		FROM Order o
		LEFT JOIN o.customer c
		WHERE o.status IN :statuses
		ORDER BY CASE o.status
		  WHEN com.example.waterrefillapijava.model.OrderStatus.queued THEN 1
		  WHEN com.example.waterrefillapijava.model.OrderStatus.processing THEN 2
		  WHEN com.example.waterrefillapijava.model.OrderStatus.transit THEN 3
		  WHEN com.example.waterrefillapijava.model.OrderStatus.completed THEN 4
		END, o.id DESC
		""")
	List<BoardOrderResponse> findAllForBoardGrouped(@Param("statuses") List<OrderStatus> statuses);

	List<Order> findByStatusNotOrderByCreatedAtDesc(OrderStatus status);

	long countByStatusNot(OrderStatus status);

	@Query("SELECT COALESCE(SUM(o.bottlesReturnedAtDelivery), 0) FROM Order o")
	Integer sumBottlesReturned();

	@Query("SELECT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.status = :status ORDER BY o.createdAt DESC")
	List<Order> findByStatusWithItems(@Param("status") OrderStatus status);
}
