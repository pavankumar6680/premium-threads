package com.buildmybundle.cart.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, Long> {

	@EntityGraph(attributePaths = "items")
	Optional<Cart> findByUserId(Long userId);

	/** Race-free "create if missing": concurrent first adds cannot violate the unique user_id constraint. */
	@Modifying
	@Query(value = "INSERT INTO carts (user_id) VALUES (:userId) ON CONFLICT (user_id) DO NOTHING", nativeQuery = true)
	void insertIfMissing(@Param("userId") Long userId);
}
