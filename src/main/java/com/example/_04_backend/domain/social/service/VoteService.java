package com.example._04_backend.domain.social.service;

import com.example._04_backend.domain.social.dto.response.VoteResponse;
import com.example._04_backend.domain.social.entity.FeedPost;
import com.example._04_backend.domain.social.entity.PostVote;
import com.example._04_backend.domain.social.enums.PostVoteType;
import com.example._04_backend.domain.social.repository.PostVoteRepository;
import com.example._04_backend.domain.user.entity.User;
import com.example._04_backend.domain.user.repository.UserRepository;
import com.example._04_backend.global.error.BusinessException;
import com.example._04_backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class VoteService {

    private final PostVoteRepository postVoteRepository;
    private final SocialPostService socialPostService;
    private final UserRepository userRepository;

    public VoteResponse vote(UUID userId, UUID postId, PostVoteType voteType) {
        FeedPost post = socialPostService.findPostOrThrow(postId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Optional<PostVote> existingVote = postVoteRepository.findByPostIdAndUserId(postId, userId);
        PostVoteType resultVote;

        if (existingVote.isPresent()) {
            PostVote vote = existingVote.get();
            if (!vote.isActive()) {
                // 취소된 투표 재활성화
                vote.changeVoteType(voteType);
                applyVote(post, voteType, true);
                resultVote = voteType;
            } else if (vote.getVoteType() == voteType) {
                // 같은 타입 → 투표 취소
                cancelVote(post, voteType);
                vote.cancel();
                resultVote = null;
            } else {
                // 다른 타입 → 투표 변경
                cancelVote(post, vote.getVoteType());
                applyVote(post, voteType, true);
                vote.changeVoteType(voteType);
                resultVote = voteType;
            }
        } else {
            PostVote newVote = PostVote.builder()
                    .post(post)
                    .user(user)
                    .voteType(voteType)
                    .build();
            postVoteRepository.save(newVote);
            applyVote(post, voteType, true);
            resultVote = voteType;
        }

        return VoteResponse.builder()
                .myVote(resultVote)
                .goCount(post.getGoCount())
                .stopCount(post.getStopCount())
                .build();
    }

    private void applyVote(FeedPost post, PostVoteType voteType, boolean increment) {
        if (voteType == PostVoteType.GO) {
            if (increment) post.incrementGoCount(); else post.decrementGoCount();
        } else {
            if (increment) post.incrementStopCount(); else post.decrementStopCount();
        }
    }

    private void cancelVote(FeedPost post, PostVoteType voteType) {
        applyVote(post, voteType, false);
    }
}
