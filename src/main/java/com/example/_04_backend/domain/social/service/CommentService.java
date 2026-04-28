package com.example._04_backend.domain.social.service;

import com.example._04_backend.domain.social.dto.request.CreateCommentRequest;
import com.example._04_backend.domain.social.dto.response.CommentListResponse;
import com.example._04_backend.domain.social.dto.response.CommentResponse;
import com.example._04_backend.domain.social.entity.FeedPost;
import com.example._04_backend.domain.social.entity.PostComment;
import com.example._04_backend.domain.social.repository.PostCommentRepository;
import com.example._04_backend.domain.user.entity.User;
import com.example._04_backend.domain.user.repository.UserRepository;
import com.example._04_backend.global.error.BusinessException;
import com.example._04_backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final PostCommentRepository postCommentRepository;
    private final SocialPostService socialPostService;
    private final UserRepository userRepository;

    @Transactional
    public CommentResponse createComment(UUID userId, UUID postId, CreateCommentRequest request) {
        FeedPost post = socialPostService.findPostOrThrow(postId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        PostComment comment = PostComment.builder()
                .post(post)
                .user(user)
                .body(request.getBody())
                .build();

        postCommentRepository.save(comment);
        return CommentResponse.of(comment, userId);
    }

    public CommentListResponse getComments(UUID userId, UUID postId, int page, int size) {
        socialPostService.findPostOrThrow(postId);

        Page<PostComment> commentPage = postCommentRepository.findVisibleByPostIdOrderByCreatedAtAsc(
                postId, PageRequest.of(page - 1, size));

        List<CommentResponse> commentResponses = commentPage.getContent().stream()
                .map(comment -> CommentResponse.of(comment, userId))
                .toList();

        return CommentListResponse.builder()
                .comments(commentResponses)
                .total(commentPage.getTotalElements())
                .build();
    }

    @Transactional
    public void deleteComment(UUID userId, UUID postId, UUID commentId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        comment.softDelete();
    }
}
