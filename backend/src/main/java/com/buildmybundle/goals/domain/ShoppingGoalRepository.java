package com.buildmybundle.goals.domain;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoppingGoalRepository extends JpaRepository<ShoppingGoal, UUID> {

	Page<ShoppingGoal> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
