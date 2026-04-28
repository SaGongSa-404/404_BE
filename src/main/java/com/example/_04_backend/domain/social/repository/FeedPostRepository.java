package com.example._04_backend.domain.social.repository;

import com.example._04_backend.domain.social.entity.FeedPost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FeedPostRepository extends JpaRepository<FeedPost, UUID> {

    @Query("SELECT p FROM FeedPost p WHERE p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<FeedPost> findAllVisibleOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT p FROM FeedPost p WHERE p.deletedAt IS NULL AND p.id < :cursor ORDER BY p.createdAt DESC")
    List<FeedPost> findVisibleByCursorOrderByCreatedAtDesc(@Param("cursor") UUID cursor, Pageable pageable);

    @Query("SELECT p FROM FeedPost p WHERE p.user.id = :userId AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<FeedPost> findByUserIdVisibleOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT p FROM FeedPost p WHERE p.user.id = :userId AND p.deletedAt IS NULL AND p.id < :cursor ORDER BY p.createdAt DESC")
    List<FeedPost> findByUserIdVisibleWithCursorOrderByCreatedAtDesc(@Param("userId") UUID userId, @Param("cursor") UUID cursor, Pageable pageable);

    long countByUserIdAndDeletedAtIsNull(UUID userId);

    void deleteByUserId(UUID userId);
}
