package com.example._04_backend.domain.user.service;

import com.example._04_backend.domain.social.dto.response.PostListResponse;
import com.example._04_backend.domain.social.dto.response.PostResponse;
import com.example._04_backend.domain.social.entity.SocialPost;
import com.example._04_backend.domain.social.entity.Vote;
import com.example._04_backend.domain.social.enums.VoteType;
import com.example._04_backend.domain.social.repository.CommentRepository;
import com.example._04_backend.domain.social.repository.SocialPostRepository;
import com.example._04_backend.domain.social.repository.VoteRepository;
import com.example._04_backend.domain.user.dto.request.UpdateNicknameRequest;
import com.example._04_backend.domain.user.dto.response.MyProfileResponse;
import com.example._04_backend.domain.user.entity.User;
import com.example._04_backend.domain.user.repository.UserRepository;
import com.example._04_backend.global.error.BusinessException;
import com.example._04_backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final SocialPostRepository socialPostRepository;
    private final VoteRepository voteRepository;
    private final CommentRepository commentRepository;

    public MyProfileResponse getMyProfile(UUID userId) {
        User user = findUserOrThrow(userId);
        long postCount = socialPostRepository.countByUserId(userId);
        return MyProfileResponse.of(user, postCount);
    }

    @Transactional
    public MyProfileResponse updateNickname(UUID userId, UpdateNicknameRequest request) {
        User user = findUserOrThrow(userId);
        user.updateNickname(request.getNickname());
        long postCount = socialPostRepository.countByUserId(userId);
        return MyProfileResponse.of(user, postCount);
    }

    public PostListResponse getMyPosts(UUID userId, UUID cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<SocialPost> posts;

        if (cursor != null) {
            posts = socialPostRepository.findByUserIdWithCursorOrderByCreatedAtDesc(userId, cursor, pageable);
        } else {
            posts = socialPostRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        boolean hasMore = posts.size() > size;
        if (hasMore) {
            posts = posts.subList(0, size);
        }

        List<PostResponse> postResponses = posts.stream()
                .map(post -> {
                    long commentCount = commentRepository.countByPostId(post.getId());
                    VoteType myVote = voteRepository.findByPostIdAndUserId(post.getId(), userId)
                            .map(Vote::getVoteType).orElse(null);
                    return PostResponse.of(post, commentCount, myVote);
                })
                .toList();

        UUID nextCursor = hasMore && !posts.isEmpty()
                ? posts.get(posts.size() - 1).getId()
                : null;

        return PostListResponse.builder()
                .posts(postResponses)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    public PostListResponse getMyVotedPosts(UUID userId, UUID cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<Vote> votes;

        if (cursor != null) {
            votes = voteRepository.findByUserIdWithCursorOrderByCreatedAtDesc(userId, cursor, pageable);
        } else {
            votes = voteRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        boolean hasMore = votes.size() > size;
        if (hasMore) {
            votes = votes.subList(0, size);
        }

        List<PostResponse> postResponses = votes.stream()
                .map(vote -> {
                    SocialPost post = vote.getPost();
                    long commentCount = commentRepository.countByPostId(post.getId());
                    return PostResponse.of(post, commentCount, vote.getVoteType());
                })
                .toList();

        UUID nextCursor = hasMore && !votes.isEmpty()
                ? votes.get(votes.size() - 1).getPost().getId()
                : null;

        return PostListResponse.builder()
                .posts(postResponses)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
