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

	@Query("SELECT p FROM FeedPost p WHERE p.deletedAt IS NULL ORDER BY p.createdAt DESC")
	List<FeedPost> findAllVisible(Pageable pageable);

	@Query("SELECT p FROM FeedPost p WHERE p.deletedAt IS NULL AND p.createdAt < :cursor ORDER BY p.createdAt DESC")
	List<FeedPost> findAllVisibleBefore(@Param("cursor") Instant cursor, Pageable pageable);

	@Query("SELECT p FROM FeedPost p WHERE p.user.id = :userId AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
	List<FeedPost> findByUserIdVisible(@Param("userId") UUID userId, Pageable pageable);

	@Query("SELECT p FROM FeedPost p WHERE p.user.id = :userId AND p.deletedAt IS NULL AND p.createdAt < :cursor ORDER BY p.createdAt DESC")
	List<FeedPost> findByUserIdVisibleBefore(@Param("userId") UUID userId, @Param("cursor") Instant cursor, Pageable pageable);

	long countByUserIdAndDeletedAtIsNull(UUID userId);

	@Query("SELECT p.id, COUNT(c) FROM PostComment c JOIN c.post p WHERE p.id IN :postIds AND c.deletedAt IS NULL GROUP BY p.id")
	List<Object[]> countCommentsByPostIds(@Param("postIds") List<UUID> postIds);

	@Modifying
	@Query("UPDATE FeedPost p SET p.deletedAt = CURRENT_TIMESTAMP WHERE p.user.id = :userId")
	void softDeleteByUserId(@Param("userId") UUID userId);
}
