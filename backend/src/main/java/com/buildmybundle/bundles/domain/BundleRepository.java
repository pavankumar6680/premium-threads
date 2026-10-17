package com.buildmybundle.bundles.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BundleRepository extends JpaRepository<Bundle, UUID> {

	@EntityGraph(attributePaths = "items")
	Optional<Bundle> findWithItemsById(UUID id);

	@EntityGraph(attributePaths = "items")
	List<Bundle> findByRunId(UUID runId);

	@EntityGraph(attributePaths = "items")
	List<Bundle> findByUserIdAndStatusInOrderByUpdatedAtDesc(Long userId, Collection<String> statuses);
}
