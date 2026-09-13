package com.example.waterrefillapijava.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.waterrefillapijava.dto.BoardOrderResponse;
import com.example.waterrefillapijava.dto.projection.DailySalesAggregate;
import com.example.waterrefillapijava.dto.projection.GallonsByDate;
import com.example.waterrefillapijava.dto.projection.OrderItemProjection;
import com.example.waterrefillapijava.dto.projection.PaymentMixAggregate;
import com.example.waterrefillapijava.dto.projection.ProductSalesAggregate;
import com.example.waterrefillapijava.model.Order;
import com.example.waterrefillapijava.model.OrderStatus;
import com.example.waterrefillapijava.model.OrderType;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

	Page<Order> findByDeletedFalse(Pageable pageable);

	Page<Order> findByOrderTypeAndDeletedFalse(OrderType orderType, Pageable pageable);

	Page<Order> findByStatusAndDeletedFalse(OrderStatus status, Pageable pageable);

	Page<Order> findByOrderTypeAndStatusAndDeletedFalse(OrderType orderType, OrderStatus status, Pageable pageable);

	@Query("SELECT o FROM Order o WHERE o.deleted = false AND (o.customer.name LIKE %:search% OR CAST(o.id AS string) LIKE %:search%)")
	Page<Order> findBySearchAndDeletedFalse(@Param("search") String search, Pageable pageable);

	@Query("SELECT o FROM Order o WHERE o.deleted = false AND o.orderType = :orderType AND (o.customer.name LIKE %:search% OR CAST(o.id AS string) LIKE %:search%)")
	Page<Order> findByOrderTypeAndSearchAndDeletedFalse(@Param("orderType") OrderType orderType, @Param("search") String search, Pageable pageable);

	@Query("SELECT o FROM Order o WHERE o.deleted = false AND o.status = :status AND (o.customer.name LIKE %:search% OR CAST(o.id AS string) LIKE %:search%)")
	Page<Order> findByStatusAndSearchAndDeletedFalse(@Param("status") OrderStatus status, @Param("search") String search, Pageable pageable);

	@Query("SELECT o FROM Order o WHERE o.deleted = false AND o.orderType = :orderType AND o.status = :status AND (o.customer.name LIKE %:search% OR CAST(o.id AS string) LIKE %:search%)")
	Page<Order> findByOrderTypeAndStatusAndSearchAndDeletedFalse(@Param("orderType") OrderType orderType, @Param("status") OrderStatus status, @Param("search") String search, Pageable pageable);

	@Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer WHERE o.deleted = false ORDER BY o.id DESC")
	List<Order> findAllForBoard();

	@Query(
		"""
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
		WHERE o.deleted = false AND o.status IN :statuses
		ORDER BY CASE o.status
		  WHEN com.example.waterrefillapijava.model.OrderStatus.queued THEN 1
		  WHEN com.example.waterrefillapijava.model.OrderStatus.processing THEN 2
		  WHEN com.example.waterrefillapijava.model.OrderStatus.transit THEN 3
		  WHEN com.example.waterrefillapijava.model.OrderStatus.completed THEN 4
		END, o.id DESC
	""")
	List<BoardOrderResponse> findAllForBoardGrouped(@Param("statuses") List<OrderStatus> statuses);

	List<Order> findByStatusNotAndDeletedFalseOrderByCreatedAtDesc(OrderStatus status);

	long countByStatusNotAndDeletedFalse(OrderStatus status);

	@Query("SELECT COALESCE(SUM(o.bottlesReturnedAtDelivery), 0) FROM Order o WHERE o.deleted = false")
	Integer sumBottlesReturned();

	@Query("SELECT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.deleted = false AND o.status = :status ORDER BY o.createdAt DESC")
	List<Order> findByStatusWithItems(@Param("status") OrderStatus status);

	@Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id = :id")
	Order findByIdWithDetails(@Param("id") Long id);

	boolean existsByCustomerId(Long customerId);

	Page<Order> findByStatus(OrderStatus status, Pageable pageable);

	Page<Order> findByDeletedTrue(Pageable pageable);

	Page<Order> findByOrderTypeAndDeletedTrue(OrderType orderType, Pageable pageable);

	Page<Order> findByStatusAndDeletedTrue(OrderStatus status, Pageable pageable);

	Page<Order> findByOrderTypeAndStatusAndDeletedTrue(OrderType orderType, OrderStatus status, Pageable pageable);

	@Query("SELECT o FROM Order o WHERE o.deleted = true AND (o.customer.name LIKE %:search% OR CAST(o.id AS string) LIKE %:search%)")
	Page<Order> findBySearchAndDeletedTrue(@Param("search") String search, Pageable pageable);

	@Query("SELECT o FROM Order o WHERE o.deleted = true AND o.orderType = :orderType AND (o.customer.name LIKE %:search% OR CAST(o.id AS string) LIKE %:search%)")
	Page<Order> findByOrderTypeAndSearchAndDeletedTrue(@Param("orderType") OrderType orderType, @Param("search") String search, Pageable pageable);

	@Query("SELECT o FROM Order o WHERE o.deleted = true AND o.status = :status AND (o.customer.name LIKE %:search% OR CAST(o.id AS string) LIKE %:search%)")
	Page<Order> findByStatusAndSearchAndDeletedTrue(@Param("status") OrderStatus status, @Param("search") String search, Pageable pageable);

	@Query("SELECT o FROM Order o WHERE o.deleted = true AND o.orderType = :orderType AND o.status = :status AND (o.customer.name LIKE %:search% OR CAST(o.id AS string) LIKE %:search%)")
	Page<Order> findByOrderTypeAndStatusAndSearchAndDeletedTrue(@Param("orderType") OrderType orderType, @Param("status") OrderStatus status, @Param("search") String search, Pageable pageable);


	@Query(
		"""
		SELECT new com.example.waterrefillapijava.dto.projection.DailySalesAggregate(
			CAST(o.createdAt AS date),
			COUNT(o),
			COALESCE(SUM(o.totalAmount), 0),
			COALESCE(SUM(CASE WHEN o.paymentMethod = com.example.waterrefillapijava.model.PaymentMethod.cash THEN o.totalAmount ELSE 0 END), 0),
			COALESCE(SUM(CASE WHEN o.paymentMethod = com.example.waterrefillapijava.model.PaymentMethod.e_wallet THEN o.totalAmount ELSE 0 END), 0),
			COALESCE(SUM(CASE WHEN o.paymentMethod = com.example.waterrefillapijava.model.PaymentMethod.credit THEN o.totalAmount ELSE 0 END), 0)
		)
		FROM Order o
		WHERE o.status = com.example.waterrefillapijava.model.OrderStatus.completed AND o.deleted = false
		GROUP BY CAST(o.createdAt AS date)
		ORDER BY CAST(o.createdAt AS date) DESC
		"""
	)
	List<DailySalesAggregate> aggregateDailySales();

	@Query(
		"""
		SELECT new com.example.waterrefillapijava.dto.projection.GallonsByDate(
			CAST(o.createdAt AS date),
			COALESCE(SUM(p.volumeGallons * oi.quantity), 0)
		)
		FROM Order o JOIN o.items oi JOIN oi.product p
		WHERE o.status = com.example.waterrefillapijava.model.OrderStatus.completed
			AND o.deleted = false
			AND p.type = com.example.waterrefillapijava.model.ProductType.water_refill
			AND p.volumeGallons IS NOT NULL
		GROUP BY CAST(o.createdAt AS date)
		"""
	)
	List<GallonsByDate> aggregateGallonsByDate();

	@Query(
		"""
		SELECT new com.example.waterrefillapijava.dto.projection.ProductSalesAggregate(
			p.id,
			p.name,
			CAST(p.type AS string),
			SUM(oi.quantity),
			SUM(oi.subtotal)
		)
		FROM OrderItem oi JOIN oi.order o JOIN oi.product p
		WHERE o.status = com.example.waterrefillapijava.model.OrderStatus.completed AND o.deleted = false
		GROUP BY p.id, p.name, CAST(p.type AS string)
		ORDER BY SUM(oi.subtotal) DESC
		"""
	)
	List<ProductSalesAggregate> aggregateProductPerformance();

	@Query(
		"""
		SELECT new com.example.waterrefillapijava.dto.projection.PaymentMixAggregate(
			CAST(o.paymentMethod AS string),
			COALESCE(SUM(o.totalAmount), 0)
		)
		FROM Order o
		WHERE o.status = com.example.waterrefillapijava.model.OrderStatus.completed AND o.deleted = false
		GROUP BY CAST(o.paymentMethod AS string)
		"""
	)
	List<PaymentMixAggregate> aggregatePaymentMix();

	@Query(
		"""
		SELECT new com.example.waterrefillapijava.dto.projection.OrderItemProjection(
			oi.order.id,
			p.id,
			p.name,
			oi.quantity,
			oi.unitPrice,
			oi.subtotal
		)
		FROM OrderItem oi JOIN oi.product p
		WHERE oi.order.id IN :orderIds
		"""
	)
	List<OrderItemProjection> findItemProjectionsByOrderIds(@Param("orderIds") Collection<Long> orderIds);
}
