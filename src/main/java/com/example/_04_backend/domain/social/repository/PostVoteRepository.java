package com.example._04_backend.domain.social.repository;

import com.example._04_backend.domain.social.entity.PostVote;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostVoteRepository extends JpaRepository<PostVote, UUID> {

    Optional<PostVote> findByPostIdAndUserId(UUID postId, UUID userId);

    @Query("SELECT v FROM PostVote v WHERE v.user.id = :userId AND v.canceledAt IS NULL ORDER BY v.createdAt DESC")
    List<PostVote> findActiveByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT v FROM PostVote v WHERE v.user.id = :userId AND v.canceledAt IS NULL AND v.post.id < :cursor ORDER BY v.createdAt DESC")
    List<PostVote> findActiveByUserIdWithCursorOrderByCreatedAtDesc(@Param("userId") UUID userId, @Param("cursor") UUID cursor, Pageable pageable);

    void deleteByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM PostVote v WHERE v.post.user.id = :userId")
    void deleteByPostUserId(@Param("userId") UUID userId);
}
