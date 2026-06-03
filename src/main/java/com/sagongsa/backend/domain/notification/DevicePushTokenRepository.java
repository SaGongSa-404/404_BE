package com.sagongsa.backend.domain.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DevicePushTokenRepository extends JpaRepository<DevicePushToken, UUID> {

	Optional<DevicePushToken> findByPushToken(String pushToken);

	List<DevicePushToken> findByUserIdAndIsActiveTrue(UUID userId);

	@Modifying
	@Query("DELETE FROM DevicePushToken t WHERE t.user.id = :userId")
	void deleteByUserId(@Param("userId") UUID userId);
}
