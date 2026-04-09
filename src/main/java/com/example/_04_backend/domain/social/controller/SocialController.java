package com.example._04_backend.domain.social.controller;

import com.example._04_backend.domain.social.dto.request.CreateCommentRequest;
import com.example._04_backend.domain.social.dto.request.CreatePostRequest;
import com.example._04_backend.domain.social.dto.request.VoteRequest;
import com.example._04_backend.domain.social.dto.response.*;
import com.example._04_backend.domain.social.service.CommentService;
import com.example._04_backend.domain.social.service.FileUploadService;
import com.example._04_backend.domain.social.service.SocialPostService;
import com.example._04_backend.domain.social.service.VoteService;
import com.example._04_backend.global.common.enums.Category;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import java.util.UUID;

@RestController
@RequestMapping("/social/posts")
@RequiredArgsConstructor
public class SocialController {

    private static final UUID DEFAULT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final SocialPostService socialPostService;
    private final VoteService voteService;
    private final CommentService commentService;
    private final FileUploadService fileUploadService;

    // TODO: JWT 인증 연동 후 SecurityContext에서 userId 추출로 변경
    private UUID getCurrentUserId(String userIdHeader) {
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            return UUID.fromString(userIdHeader);
        }
        return DEFAULT_USER_ID;
    }

    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @Valid @RequestBody CreatePostRequest request) {
        PostResponse response = socialPostService.createPost(getCurrentUserId(userIdHeader), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/uploads", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        String url = fileUploadService.saveImage(file);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping
    public ResponseEntity<PostListResponse> getPosts(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") int size) {
        PostListResponse response = socialPostService.getPosts(getCurrentUserId(userIdHeader), category, cursor, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable UUID postId) {
        PostResponse response = socialPostService.getPost(getCurrentUserId(userIdHeader), postId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable UUID postId) {
        socialPostService.deletePost(getCurrentUserId(userIdHeader), postId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{postId}/votes")
    public ResponseEntity<VoteResponse> vote(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable UUID postId,
            @Valid @RequestBody VoteRequest request) {
        VoteResponse response = voteService.vote(getCurrentUserId(userIdHeader), postId, request.getVoteType());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse response = commentService.createComment(getCurrentUserId(userIdHeader), postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{postId}/comments")
    public ResponseEntity<CommentListResponse> getComments(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        CommentListResponse response = commentService.getComments(getCurrentUserId(userIdHeader), postId, page, size);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable UUID postId,
            @PathVariable UUID commentId) {
        commentService.deleteComment(getCurrentUserId(userIdHeader), postId, commentId);
        return ResponseEntity.noContent().build();
    }
}
