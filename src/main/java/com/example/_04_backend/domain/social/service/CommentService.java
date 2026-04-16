package com.example._04_backend.domain.social.service;

import com.example._04_backend.domain.social.dto.request.CreateCommentRequest;
import com.example._04_backend.domain.social.dto.response.CommentListResponse;
import com.example._04_backend.domain.social.dto.response.CommentResponse;
import com.example._04_backend.domain.social.entity.Comment;
import com.example._04_backend.domain.social.entity.SocialPost;
import com.example._04_backend.domain.social.repository.CommentRepository;
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

    private final CommentRepository commentRepository;
    private final SocialPostService socialPostService;

    @Transactional
    public CommentResponse createComment(UUID userId, UUID postId, CreateCommentRequest request) {
        SocialPost post = socialPostService.findPostOrThrow(postId);

        Comment comment = Comment.builder()
                .post(post)
                .userId(userId)
                .body(request.getBody())
                .build();

        commentRepository.save(comment);
        return CommentResponse.of(comment, userId);
    }

    public CommentListResponse getComments(UUID userId, UUID postId, int page, int size) {
        socialPostService.findPostOrThrow(postId);

        Page<Comment> commentPage = commentRepository.findByPostIdOrderByCreatedAtAsc(
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
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        commentRepository.delete(comment);
    }
}
