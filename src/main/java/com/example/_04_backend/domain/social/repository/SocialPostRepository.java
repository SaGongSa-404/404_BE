package com.example._04_backend.domain.social.repository;

import com.example._04_backend.domain.social.entity.SocialPost;
import com.example._04_backend.global.common.enums.Category;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SocialPostRepository extends JpaRepository<SocialPost, UUID> {

    @Query("SELECT p FROM SocialPost p WHERE p.id < :cursor ORDER BY p.createdAt DESC")
    List<SocialPost> findByCursorOrderByCreatedAtDesc(@Param("cursor") UUID cursor, Pageable pageable);

    @Query("SELECT p FROM SocialPost p WHERE p.id < :cursor AND p.category = :category ORDER BY p.createdAt DESC")
    List<SocialPost> findByCursorAndCategoryOrderByCreatedAtDesc(
            @Param("cursor") UUID cursor, @Param("category") Category category, Pageable pageable);

    @Query("SELECT p FROM SocialPost p ORDER BY p.createdAt DESC")
    List<SocialPost> findAllOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT p FROM SocialPost p WHERE p.category = :category ORDER BY p.createdAt DESC")
    List<SocialPost> findByCategoryOrderByCreatedAtDesc(@Param("category") Category category, Pageable pageable);

    List<SocialPost> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("SELECT p FROM SocialPost p WHERE p.userId = :userId AND p.id < :cursor ORDER BY p.createdAt DESC")
    List<SocialPost> findByUserIdWithCursorOrderByCreatedAtDesc(@Param("userId") UUID userId, @Param("cursor") UUID cursor, Pageable pageable);

    long countByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
