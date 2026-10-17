package com.buildmybundle.orders.domain;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

	Optional<CustomerOrder> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

	Optional<CustomerOrder> findByIdAndUserId(Long id, Long userId);

	Page<CustomerOrder> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

	Page<CustomerOrder> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

	Page<CustomerOrder> findByStatusOrderByCreatedAtDescIdDesc(OrderStatus status, Pageable pageable);

	Optional<CustomerOrder> findByOrderNumber(String orderNumber);

	/** Row lock for staff status changes (same reason as {@link #findForUpdate}). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from CustomerOrder o where o.id = :id")
	Optional<CustomerOrder> findByIdForUpdate(@Param("id") Long id);

	/** Row lock so concurrent cancellations of the same order cannot both restock. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from CustomerOrder o where o.id = :id and o.userId = :userId")
	Optional<CustomerOrder> findForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
