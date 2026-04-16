package com.example._04_backend.domain.social.service;

import com.example._04_backend.domain.social.dto.request.CreatePostRequest;
import com.example._04_backend.domain.social.dto.response.PostListResponse;
import com.example._04_backend.domain.social.dto.response.PostResponse;
import com.example._04_backend.domain.social.entity.SocialPost;
import com.example._04_backend.domain.social.entity.Vote;
import com.example._04_backend.domain.social.enums.VoteType;
import com.example._04_backend.domain.social.repository.CommentRepository;
import com.example._04_backend.domain.social.repository.SocialPostRepository;
import com.example._04_backend.domain.social.repository.VoteRepository;
import com.example._04_backend.global.common.enums.Category;
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

    private final SocialPostRepository socialPostRepository;
    private final VoteRepository voteRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public PostResponse createPost(UUID userId, CreatePostRequest request) {
        if (request.getImageUrl() == null && request.getProductUrl() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 URL 또는 상품 URL 중 하나는 필수입니다.");
        }

        SocialPost post = SocialPost.builder()
                .userId(userId)
                .wishId(request.getWishId())
                .productUrl(request.getProductUrl())
                .imageUrl(request.getImageUrl())
                .title(request.getTitle())
                .body(request.getBody())
                .price(request.getPrice())
                .category(request.getCategory())
                .build();

        socialPostRepository.save(post);
        return PostResponse.of(post, 0, null);
    }

    public PostListResponse getPosts(UUID userId, Category category, UUID cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<SocialPost> posts;

        if (cursor != null && category != null) {
            posts = socialPostRepository.findByCursorAndCategoryOrderByCreatedAtDesc(cursor, category, pageable);
        } else if (cursor != null) {
            posts = socialPostRepository.findByCursorOrderByCreatedAtDesc(cursor, pageable);
        } else if (category != null) {
            posts = socialPostRepository.findByCategoryOrderByCreatedAtDesc(category, pageable);
        } else {
            posts = socialPostRepository.findAllOrderByCreatedAtDesc(pageable);
        }

        boolean hasMore = posts.size() > size;
        if (hasMore) {
            posts = posts.subList(0, size);
        }

        List<PostResponse> postResponses = posts.stream()
                .map(post -> {
                    long commentCount = commentRepository.countByPostId(post.getId());
                    VoteType myVote = (userId != null)
                            ? voteRepository.findByPostIdAndUserId(post.getId(), userId)
                                    .map(Vote::getVoteType).orElse(null)
                            : null;
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

    public PostResponse getPost(UUID userId, UUID postId) {
        SocialPost post = findPostOrThrow(postId);
        long commentCount = commentRepository.countByPostId(postId);
        VoteType myVote = (userId != null)
                ? voteRepository.findByPostIdAndUserId(postId, userId)
                        .map(Vote::getVoteType).orElse(null)
                : null;
        return PostResponse.of(post, commentCount, myVote);
    }

    @Transactional
    public void deletePost(UUID userId, UUID postId) {
        SocialPost post = findPostOrThrow(postId);
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        socialPostRepository.delete(post);
    }

    public SocialPost findPostOrThrow(UUID postId) {
        return socialPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }
}
