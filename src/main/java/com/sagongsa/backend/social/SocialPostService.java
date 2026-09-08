package com.sagongsa.backend.social;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SocialPostService {

	private final SocialPostCommands commands;
	private final SocialPostQueries queries;

	SocialPostService(SocialPostCommands commands,
		SocialPostQueries queries) {
		this.commands = commands;
		this.queries = queries;
	}

	PostResponse createPost(UUID userId, CreatePostRequest request) {
		return commands.createPost(userId, request);
	}

	PostResponse updatePost(UUID userId, UUID postId, UpdatePostRequest request) {
		return commands.updatePost(userId, postId, request);
	}

	void deletePost(UUID userId, UUID postId) {
		commands.deletePost(userId, postId);
	}

	PostListResponse getPosts(UUID userId, Instant cursor, int size) {
		return queries.getPosts(userId, cursor, size);
	}

	PostResponse getPost(UUID userId, UUID postId) {
		return queries.getPost(userId, postId);
	}

	PostListResponse getMyPosts(UUID userId, Instant cursor, int size) {
		return queries.getMyPosts(userId, cursor, size);
	}
}
