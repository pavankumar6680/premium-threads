package com.buildmybundle.account.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AddressRepository extends JpaRepository<Address, Long> {

	List<Address> findByUserIdOrderByDefaultAddressDescCreatedAtDesc(Long userId);

	Optional<Address> findByIdAndUserId(Long id, Long userId);

	long countByUserId(Long userId);

	Optional<Address> findFirstByUserIdAndIdNotOrderByCreatedAtDesc(Long userId, Long excludedId);

	/** Executed immediately, so the partial unique index (one default per user) is never violated. */
	@Modifying(flushAutomatically = true)
	@Query("update Address a set a.defaultAddress = false where a.userId = :userId and a.id <> :keepId and a.defaultAddress = true")
	int clearDefaultExcept(@Param("userId") Long userId, @Param("keepId") Long keepId);
}
