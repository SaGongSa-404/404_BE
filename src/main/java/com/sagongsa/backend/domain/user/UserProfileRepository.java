package com.sagongsa.backend.domain.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

	Optional<UserProfile> findByUserId(UUID userId);

	@Query(value = "SELECT user_id FROM user_profiles WHERE user_id IN (:ids)", nativeQuery = true)
	List<UUID> findExistingProfileUserIds(@Param("ids") Collection<UUID> ids);

	@Modifying
	@Query("DELETE FROM UserProfile p WHERE p.user.id = :userId")
	void deleteByUserId(@Param("userId") UUID userId);
}
