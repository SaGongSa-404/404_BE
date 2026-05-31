package com.sagongsa.backend.domain.social;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {

	@Query("SELECT c FROM PostComment c WHERE c.post.id = :postId AND c.deletedAt IS NULL ORDER BY c.createdAt ASC")
	Page<PostComment> findVisibleByPostId(@Param("postId") UUID postId, Pageable pageable);

	@Query("SELECT c FROM PostComment c WHERE c.post.id = :postId AND c.deletedAt IS NULL AND c.user.id NOT IN :blockedIds ORDER BY c.createdAt ASC")
	Page<PostComment> findVisibleByPostIdExcludingBlockers(@Param("postId") UUID postId, @Param("blockedIds") List<UUID> blockedIds, Pageable pageable);

	long countByPostIdAndDeletedAtIsNull(UUID postId);

	@Query("SELECT c.post.id, COUNT(c) FROM PostComment c WHERE c.post.id IN :postIds AND c.deletedAt IS NULL GROUP BY c.post.id")
	List<Object[]> countVisibleByPostIds(@Param("postIds") List<UUID> postIds);

	@Query("SELECT c.post.id, COUNT(c) FROM PostComment c WHERE c.post.id IN :postIds AND c.deletedAt IS NULL AND c.user.id NOT IN :blockedIds GROUP BY c.post.id")
	List<Object[]> countVisibleByPostIdsExcludingBlockers(@Param("postIds") List<UUID> postIds, @Param("blockedIds") List<UUID> blockedIds);

	@Query("SELECT c.user.id FROM PostComment c WHERE c.post.id = :postId AND c.deletedAt IS NULL GROUP BY c.user.id ORDER BY MIN(c.createdAt) ASC")
	List<UUID> findCommenterIdsByPostIdOrderedByFirstComment(@Param("postId") UUID postId);

	@Modifying
	@Query("DELETE FROM PostComment c WHERE c.user.id = :userId")
	void deleteByUserId(@Param("userId") UUID userId);

	@Modifying
	@Query("DELETE FROM PostComment c WHERE c.post.user.id = :userId")
	void deleteByPostUserId(@Param("userId") UUID userId);
}
