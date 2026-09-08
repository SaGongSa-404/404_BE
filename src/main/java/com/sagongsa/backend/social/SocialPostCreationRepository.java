package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.item.SavedItem;
import com.sagongsa.backend.domain.social.FeedPost;
import com.sagongsa.backend.domain.social.FeedPostRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class SocialPostCreationRepository {
	private static final Duration POST_DUPLICATE_WINDOW = Duration.ofSeconds(30);

	private final FeedPostRepository feedPostRepository;
	private final JdbcTemplate jdbcTemplate;

	SocialPostCreationRepository(FeedPostRepository feedPostRepository,
		JdbcTemplate jdbcTemplate) {
		this.feedPostRepository = feedPostRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	FeedPost findRecentDuplicatePost(UUID userId, CreatePostRequest request, SavedItem item, String imageUrl) {
		return feedPostRepository.findRecentDuplicates(
				userId,
				item == null ? null : item.getId(),
				request.title(),
				request.body(),
				imageUrl,
				request.price(),
				Instant.now().minus(POST_DUPLICATE_WINDOW),
				PageRequest.of(0, 1)
			)
			.stream()
			.findFirst()
			.orElse(null);
	}

	void acquireDuplicateLock(long lockKey) {
		jdbcTemplate.query("select pg_advisory_xact_lock(?)", resultSet -> {
		}, lockKey);
	}
}
