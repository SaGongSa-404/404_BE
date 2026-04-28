package com.example._04_backend.domain.social.service;

import com.example._04_backend.domain.social.dto.request.CreatePostRequest;
import com.example._04_backend.domain.social.dto.response.PostListResponse;
import com.example._04_backend.domain.social.dto.response.PostResponse;
import com.example._04_backend.domain.social.entity.FeedPost;
import com.example._04_backend.domain.social.entity.PostVote;
import com.example._04_backend.domain.social.enums.PostVoteType;
import com.example._04_backend.domain.social.repository.FeedPostRepository;
import com.example._04_backend.domain.social.repository.PostCommentRepository;
import com.example._04_backend.domain.social.repository.PostVoteRepository;
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
public class SocialPostService {

    private final FeedPostRepository feedPostRepository;
    private final PostVoteRepository postVoteRepository;
    private final PostCommentRepository postCommentRepository;
    private final UserRepository userRepository;

    @Transactional
    public PostResponse createPost(UUID userId, CreatePostRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        FeedPost post = FeedPost.builder()
                .user(user)
                .title(request.getTitle())
                .body(request.getBody())
                .imageUrl(request.getImageUrl())
                .price(request.getPrice())
                .build();

        feedPostRepository.save(post);
        return PostResponse.of(post, 0, null);
    }

    public PostListResponse getPosts(UUID userId, UUID cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<FeedPost> posts;

        if (cursor != null) {
            posts = feedPostRepository.findVisibleByCursorOrderByCreatedAtDesc(cursor, pageable);
        } else {
            posts = feedPostRepository.findAllVisibleOrderByCreatedAtDesc(pageable);
        }

        boolean hasMore = posts.size() > size;
        if (hasMore) posts = posts.subList(0, size);

        List<PostResponse> postResponses = posts.stream()
                .map(post -> {
                    long commentCount = postCommentRepository.countByPostIdAndDeletedAtIsNull(post.getId());
                    PostVoteType myVote = (userId != null)
                            ? postVoteRepository.findByPostIdAndUserId(post.getId(), userId)
                                    .filter(PostVote::isActive)
                                    .map(PostVote::getVoteType).orElse(null)
                            : null;
                    return PostResponse.of(post, commentCount, myVote);
                })
                .toList();

        UUID nextCursor = hasMore && !posts.isEmpty() ? posts.get(posts.size() - 1).getId() : null;

        return PostListResponse.builder()
                .posts(postResponses)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    public PostResponse getPost(UUID userId, UUID postId) {
        FeedPost post = findPostOrThrow(postId);
        long commentCount = postCommentRepository.countByPostIdAndDeletedAtIsNull(postId);
        PostVoteType myVote = (userId != null)
                ? postVoteRepository.findByPostIdAndUserId(postId, userId)
                        .filter(PostVote::isActive)
                        .map(PostVote::getVoteType).orElse(null)
                : null;
        return PostResponse.of(post, commentCount, myVote);
    }

    @Transactional
    public void deletePost(UUID userId, UUID postId) {
        FeedPost post = findPostOrThrow(postId);
        if (!post.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        post.softDelete();
    }

    public FeedPost findPostOrThrow(UUID postId) {
        FeedPost post = feedPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (post.isDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }
}
