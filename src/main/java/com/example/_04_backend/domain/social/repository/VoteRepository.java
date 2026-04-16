package com.example._04_backend.domain.social.repository;

import com.example._04_backend.domain.social.entity.Vote;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, UUID> {

    Optional<Vote> findByPostIdAndUserId(UUID postId, UUID userId);

    @Query("SELECT v FROM Vote v WHERE v.userId = :userId ORDER BY v.createdAt DESC")
    List<Vote> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT v FROM Vote v WHERE v.userId = :userId AND v.post.id < :cursor ORDER BY v.createdAt DESC")
    List<Vote> findByUserIdWithCursorOrderByCreatedAtDesc(@Param("userId") UUID userId, @Param("cursor") UUID cursor, Pageable pageable);

    long countByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM Vote v WHERE v.post.userId = :userId")
    void deleteByPostUserId(@Param("userId") UUID userId);
}
