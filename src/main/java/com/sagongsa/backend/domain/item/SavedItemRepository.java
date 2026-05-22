package com.sagongsa.backend.domain.item;

import com.sagongsa.backend.domain.enums.ItemStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedItemRepository extends JpaRepository<SavedItem, UUID> {

	@Query("""
		SELECT s FROM SavedItem s
		WHERE s.user.id = :userId
		  AND (:status IS NULL OR s.status = :status)
		  AND s.updatedAt >= :from
		  AND s.updatedAt < :to
		ORDER BY s.updatedAt DESC
		""")
	List<SavedItem> findByUserIdAndStatusBetween(
		@Param("userId") UUID userId,
		@Param("status") ItemStatus status,
		@Param("from") Instant from,
		@Param("to") Instant to,
		Pageable pageable);

	@Query("""
		SELECT COALESCE(SUM(s.listedPrice), 0) FROM SavedItem s
		WHERE s.user.id = :userId
		  AND s.status = :status
		  AND s.updatedAt >= :from
		  AND s.updatedAt < :to
		""")
	Integer sumPriceByUserIdAndStatusBetween(
		@Param("userId") UUID userId,
		@Param("status") ItemStatus status,
		@Param("from") Instant from,
		@Param("to") Instant to);

	@Query("""
		SELECT COUNT(s) FROM SavedItem s
		WHERE s.user.id = :userId
		  AND s.status = :status
		  AND s.updatedAt >= :from
		  AND s.updatedAt < :to
		""")
	long countByUserIdAndStatusBetween(
		@Param("userId") UUID userId,
		@Param("status") ItemStatus status,
		@Param("from") Instant from,
		@Param("to") Instant to);

	@Modifying
	@Query("DELETE FROM SavedItem s WHERE s.user.id = :userId")
	void deleteByUserId(@Param("userId") UUID userId);
}
