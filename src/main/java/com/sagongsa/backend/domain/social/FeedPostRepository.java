package com.sagongsa.backend.domain.social;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedPostRepository extends JpaRepository<FeedPost, UUID> {

	@Query("SELECT p FROM FeedPost p LEFT JOIN FETCH p.item WHERE p.deletedAt IS NULL AND p.moderationStatus = com.sagongsa.backend.domain.enums.ModerationStatus.ACTIVE ORDER BY p.createdAt DESC")
	List<FeedPost> findAllVisible(Pageable pageable);

	@Query("SELECT p FROM FeedPost p LEFT JOIN FETCH p.item WHERE p.deletedAt IS NULL AND p.moderationStatus = com.sagongsa.backend.domain.enums.ModerationStatus.ACTIVE AND p.createdAt < :cursor ORDER BY p.createdAt DESC")
	List<FeedPost> findAllVisibleBefore(@Param("cursor") Instant cursor, Pageable pageable);

	@Query("SELECT p FROM FeedPost p LEFT JOIN FETCH p.item WHERE p.deletedAt IS NULL AND p.moderationStatus = com.sagongsa.backend.domain.enums.ModerationStatus.ACTIVE AND p.user.id NOT IN :excludedIds ORDER BY p.createdAt DESC")
	List<FeedPost> findAllVisibleExcluding(@Param("excludedIds") List<UUID> excludedIds, Pageable pageable);

	@Query("SELECT p FROM FeedPost p LEFT JOIN FETCH p.item WHERE p.deletedAt IS NULL AND p.moderationStatus = com.sagongsa.backend.domain.enums.ModerationStatus.ACTIVE AND p.createdAt < :cursor AND p.user.id NOT IN :excludedIds ORDER BY p.createdAt DESC")
	List<FeedPost> findAllVisibleBeforeExcluding(@Param("cursor") Instant cursor, @Param("excludedIds") List<UUID> excludedIds, Pageable pageable);

	@Query("SELECT p FROM FeedPost p LEFT JOIN FETCH p.item WHERE p.user.id = :userId AND p.deletedAt IS NULL AND p.moderationStatus = com.sagongsa.backend.domain.enums.ModerationStatus.ACTIVE ORDER BY p.createdAt DESC")
	List<FeedPost> findByUserIdVisible(@Param("userId") UUID userId, Pageable pageable);

	@Query("SELECT p FROM FeedPost p LEFT JOIN FETCH p.item WHERE p.user.id = :userId AND p.deletedAt IS NULL AND p.moderationStatus = com.sagongsa.backend.domain.enums.ModerationStatus.ACTIVE AND p.createdAt < :cursor ORDER BY p.createdAt DESC")
	List<FeedPost> findByUserIdVisibleBefore(@Param("userId") UUID userId, @Param("cursor") Instant cursor, Pageable pageable);

	@Query("""
		SELECT p
		FROM FeedPost p
		LEFT JOIN FETCH p.item item
		WHERE p.user.id = :userId
		  AND p.deletedAt IS NULL
		  AND p.moderationStatus = com.sagongsa.backend.domain.enums.ModerationStatus.ACTIVE
		  AND p.createdAt >= :cutoff
		  AND ((:itemId IS NULL AND item IS NULL) OR item.id = :itemId)
		  AND ((:title IS NULL AND p.title IS NULL) OR p.title = :title)
		  AND ((:body IS NULL AND p.body IS NULL) OR p.body = :body)
		  AND ((:imageUrl IS NULL AND p.imageUrl IS NULL) OR p.imageUrl = :imageUrl)
		  AND ((:price IS NULL AND p.price IS NULL) OR p.price = :price)
		ORDER BY p.createdAt DESC, p.id DESC
		""")
	List<FeedPost> findRecentDuplicates(
		@Param("userId") UUID userId,
		@Param("itemId") UUID itemId,
		@Param("title") String title,
		@Param("body") String body,
		@Param("imageUrl") String imageUrl,
		@Param("price") Integer price,
		@Param("cutoff") Instant cutoff,
		Pageable pageable
	);

	long countByUserIdAndDeletedAtIsNullAndModerationStatus(UUID userId, com.sagongsa.backend.domain.enums.ModerationStatus moderationStatus);

	@Modifying
	@Query("UPDATE FeedPost p SET p.deletedAt = CURRENT_TIMESTAMP WHERE p.user.id = :userId")
	void softDeleteByUserId(@Param("userId") UUID userId);
}
