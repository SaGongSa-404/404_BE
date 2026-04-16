package com.example._04_backend.domain.social.repository;

import com.example._04_backend.domain.social.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    Page<Comment> findByPostIdOrderByCreatedAtAsc(UUID postId, Pageable pageable);

    long countByPostId(UUID postId);

    void deleteByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.post.userId = :userId")
    void deleteByPostUserId(@Param("userId") UUID userId);
}
