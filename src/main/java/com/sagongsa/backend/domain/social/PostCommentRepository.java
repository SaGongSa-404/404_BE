package com.sagongsa.backend.domain.social;

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

	long countByPostIdAndDeletedAtIsNull(UUID postId);

	@Modifying
	@Query("DELETE FROM PostComment c WHERE c.user.id = :userId")
	void deleteByUserId(@Param("userId") UUID userId);

	@Modifying
	@Query("DELETE FROM PostComment c WHERE c.post.user.id = :userId")
	void deleteByPostUserId(@Param("userId") UUID userId);
}
