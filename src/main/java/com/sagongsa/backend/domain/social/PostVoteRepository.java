package com.sagongsa.backend.domain.social;

import com.sagongsa.backend.domain.enums.PostVoteType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostVoteRepository extends JpaRepository<PostVote, UUID> {

	Optional<PostVote> findByPostIdAndUserId(UUID postId, UUID userId);

	@Query("SELECT v FROM PostVote v WHERE v.post.id IN :postIds AND v.user.id = :userId")
	List<PostVote> findByPostIdsAndUserId(@Param("postIds") List<UUID> postIds, @Param("userId") UUID userId);

	@Query("SELECT v FROM PostVote v WHERE v.user.id = :userId AND v.canceledAt IS NULL AND v.post.deletedAt IS NULL AND v.post.moderationStatus = com.sagongsa.backend.domain.enums.ModerationStatus.ACTIVE ORDER BY v.createdAt DESC")
	List<PostVote> findActiveByUserId(@Param("userId") UUID userId, Pageable pageable);

	@Query("SELECT v FROM PostVote v WHERE v.user.id = :userId AND v.canceledAt IS NULL AND v.post.deletedAt IS NULL AND v.post.moderationStatus = com.sagongsa.backend.domain.enums.ModerationStatus.ACTIVE AND v.createdAt < :cursor ORDER BY v.createdAt DESC")
	List<PostVote> findActiveByUserIdBefore(@Param("userId") UUID userId, @Param("cursor") Instant cursor, Pageable pageable);

	@Modifying
	@Query("DELETE FROM PostVote v WHERE v.user.id = :userId")
	void deleteByUserId(@Param("userId") UUID userId);

	@Modifying
	@Query("DELETE FROM PostVote v WHERE v.post.user.id = :userId")
	void deleteByPostUserId(@Param("userId") UUID userId);
}
