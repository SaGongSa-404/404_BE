package com.sagongsa.backend.domain.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

	Optional<UserProfile> findByUserId(UUID userId);

	@Modifying
	@Query("DELETE FROM UserProfile p WHERE p.user.id = :userId")
	void deleteByUserId(@Param("userId") UUID userId);
}
