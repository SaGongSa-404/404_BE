package com.example._04_backend.domain.social.service;

import com.example._04_backend.domain.social.dto.response.VoteResponse;
import com.example._04_backend.domain.social.entity.SocialPost;
import com.example._04_backend.domain.social.entity.Vote;
import com.example._04_backend.domain.social.enums.VoteType;
import com.example._04_backend.domain.social.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class VoteService {

    private final VoteRepository voteRepository;
    private final SocialPostService socialPostService;

    public VoteResponse vote(UUID userId, UUID postId, VoteType voteType) {
        SocialPost post = socialPostService.findPostOrThrow(postId);
        Optional<Vote> existingVote = voteRepository.findByPostIdAndUserId(postId, userId);

        VoteType resultVote;

        if (existingVote.isPresent()) {
            Vote vote = existingVote.get();
            if (vote.getVoteType() == voteType) {
                // 같은 타입 -> 투표 취소
                if (voteType == VoteType.GO) {
                    post.decrementGoCount();
                } else {
                    post.decrementStopCount();
                }
                voteRepository.delete(vote);
                resultVote = null;
            } else {
                // 다른 타입 -> 투표 변경
                if (vote.getVoteType() == VoteType.GO) {
                    post.decrementGoCount();
                    post.incrementStopCount();
                } else {
                    post.decrementStopCount();
                    post.incrementGoCount();
                }
                vote.changeVoteType(voteType);
                resultVote = voteType;
            }
        } else {
            // 새 투표
            Vote newVote = Vote.builder()
                    .post(post)
                    .userId(userId)
                    .voteType(voteType)
                    .build();
            voteRepository.save(newVote);

            if (voteType == VoteType.GO) {
                post.incrementGoCount();
            } else {
                post.incrementStopCount();
            }
            resultVote = voteType;
        }

        return VoteResponse.builder()
                .myVote(resultVote)
                .goCount(post.getGoCount())
                .stopCount(post.getStopCount())
                .build();
    }
}
