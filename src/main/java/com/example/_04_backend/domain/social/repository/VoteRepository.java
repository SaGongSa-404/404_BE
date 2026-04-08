package com.example._04_backend.domain.social.repository;

import com.example._04_backend.domain.social.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, UUID> {

    Optional<Vote> findByPostIdAndUserId(UUID postId, UUID userId);
}
