package com.example._04_backend.domain.social.controller;

import com.example._04_backend.domain.social.dto.request.CreateCommentRequest;
import com.example._04_backend.domain.social.dto.request.CreatePostRequest;
import com.example._04_backend.domain.social.dto.request.VoteRequest;
import com.example._04_backend.domain.social.dto.response.*;
import com.example._04_backend.domain.social.service.CommentService;
import com.example._04_backend.domain.social.service.FileUploadService;
import com.example._04_backend.domain.social.service.SocialPostService;
import com.example._04_backend.domain.social.service.VoteService;
import com.example._04_backend.global.auth.LoginUser;
import com.example._04_backend.global.common.enums.Category;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/social/posts")
@RequiredArgsConstructor
public class SocialController {

    private final SocialPostService socialPostService;
    private final VoteService voteService;
    private final CommentService commentService;
    private final FileUploadService fileUploadService;

    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody CreatePostRequest request) {
        PostResponse response = socialPostService.createPost(loginUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/uploads", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        String url = fileUploadService.saveImage(file);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping
    public ResponseEntity<PostListResponse> getPosts(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = loginUser != null ? loginUser.getId() : null;
        PostListResponse response = socialPostService.getPosts(userId, category, cursor, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID postId) {
        UUID userId = loginUser != null ? loginUser.getId() : null;
        PostResponse response = socialPostService.getPost(userId, postId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID postId) {
        socialPostService.deletePost(loginUser.getId(), postId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{postId}/votes")
    public ResponseEntity<VoteResponse> vote(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID postId,
            @Valid @RequestBody VoteRequest request) {
        VoteResponse response = voteService.vote(loginUser.getId(), postId, request.getVoteType());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse response = commentService.createComment(loginUser.getId(), postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{postId}/comments")
    public ResponseEntity<CommentListResponse> getComments(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = loginUser != null ? loginUser.getId() : null;
        CommentListResponse response = commentService.getComments(userId, postId, page, size);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID postId,
            @PathVariable UUID commentId) {
        commentService.deleteComment(loginUser.getId(), postId, commentId);
        return ResponseEntity.noContent().build();
    }
}
