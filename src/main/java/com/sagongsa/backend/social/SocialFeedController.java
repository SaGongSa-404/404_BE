package com.sagongsa.backend.social;

import com.sagongsa.backend.auth.CurrentUserId;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/social/posts")
public class SocialFeedController {

	private final SocialPostService socialPostService;
	private final VoteService voteService;
	private final CommentService commentService;
	private final FileUploadService fileUploadService;

	public SocialFeedController(SocialPostService socialPostService,
		VoteService voteService,
		CommentService commentService,
		FileUploadService fileUploadService) {
		this.socialPostService = socialPostService;
		this.voteService = voteService;
		this.commentService = commentService;
		this.fileUploadService = fileUploadService;
	}

	@PostMapping
	public ResponseEntity<PostResponse> createPost(
		@CurrentUserId UUID userId,
		@Valid @RequestBody CreatePostRequest request) {
		PostResponse response = socialPostService.createPost(userId, request);
		return ResponseEntity
			.created(URI.create("/api/v1/social/posts/" + response.id()))
			.body(response);
	}

	@PostMapping(value = "/uploads", consumes = "multipart/form-data")
	public ResponseEntity<Map<String, String>> uploadImage(
		@RequestParam("file") MultipartFile file) {
		String url = fileUploadService.saveImage(file);
		return ResponseEntity.ok(Map.of("url", url));
	}

	@GetMapping
	public ResponseEntity<PostListResponse> getPosts(
		@CurrentUserId UUID userId,
		@RequestParam(required = false) UUID cursor,
		@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(socialPostService.getPosts(userId, cursor, size));
	}

	@GetMapping("/{postId}")
	public ResponseEntity<PostResponse> getPost(
		@CurrentUserId UUID userId,
		@PathVariable UUID postId) {
		return ResponseEntity.ok(socialPostService.getPost(userId, postId));
	}

	@DeleteMapping("/{postId}")
	public ResponseEntity<Void> deletePost(
		@CurrentUserId UUID userId,
		@PathVariable UUID postId) {
		socialPostService.deletePost(userId, postId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{postId}/votes")
	public ResponseEntity<VoteResponse> vote(
		@CurrentUserId UUID userId,
		@PathVariable UUID postId,
		@Valid @RequestBody VoteRequest request) {
		return ResponseEntity.ok(voteService.vote(userId, postId, request.voteType()));
	}

	@PostMapping("/{postId}/comments")
	public ResponseEntity<CommentResponse> createComment(
		@CurrentUserId UUID userId,
		@PathVariable UUID postId,
		@Valid @RequestBody CreateCommentRequest request) {
		CommentResponse response = commentService.createComment(userId, postId, request);
		return ResponseEntity
			.created(URI.create("/api/v1/social/posts/" + postId + "/comments/" + response.id()))
			.body(response);
	}

	@GetMapping("/{postId}/comments")
	public ResponseEntity<CommentListResponse> getComments(
		@CurrentUserId UUID userId,
		@PathVariable UUID postId,
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(commentService.getComments(userId, postId, page, size));
	}

	@DeleteMapping("/{postId}/comments/{commentId}")
	public ResponseEntity<Void> deleteComment(
		@CurrentUserId UUID userId,
		@PathVariable UUID postId,
		@PathVariable UUID commentId) {
		commentService.deleteComment(userId, postId, commentId);
		return ResponseEntity.noContent().build();
	}
}
