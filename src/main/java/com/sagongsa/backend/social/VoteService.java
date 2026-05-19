package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.enums.PostVoteType;
import com.sagongsa.backend.domain.social.FeedPost;
import com.sagongsa.backend.domain.social.PostVote;
import com.sagongsa.backend.domain.social.PostVoteRepository;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class VoteService {

	private final PostVoteRepository postVoteRepository;
	private final SocialPostService socialPostService;
	private final UserAccountRepository userAccountRepository;
	private final EntityManager em;

	VoteService(PostVoteRepository postVoteRepository,
		SocialPostService socialPostService,
		UserAccountRepository userAccountRepository,
		EntityManager em) {
		this.postVoteRepository = postVoteRepository;
		this.socialPostService = socialPostService;
		this.userAccountRepository = userAccountRepository;
		this.em = em;
	}

	VoteResponse vote(UUID userId, UUID postId, PostVoteType voteType) {
		FeedPost post = socialPostService.findPostOrThrow(postId);
		UserAccount user = userAccountRepository.findById(userId)
			.orElseThrow(() -> new SocialFeedNotFoundException("사용자를 찾을 수 없습니다."));

		Optional<PostVote> existing = postVoteRepository.findByPostIdAndUserId(postId, userId);
		PostVoteType resultVote;

		if (existing.isPresent()) {
			PostVote vote = existing.get();
			if (!vote.isActive()) {
				vote.changeVoteType(voteType);
				resultVote = voteType;
			} else if (vote.getVoteType() == voteType) {
				vote.cancel();
				resultVote = null;
			} else {
				vote.changeVoteType(voteType);
				resultVote = voteType;
			}
		} else {
			PostVote newVote = new PostVote(post, user, voteType);
			postVoteRepository.save(newVote);
			resultVote = voteType;
		}

		// DB 트리거 sync_feed_post_vote_counts가 post_votes 변경 시 go_count/stop_count를 자동 동기화함.
		// flush 후 refresh해야 트리거가 반영한 카운트를 응답에 담을 수 있음.
		postVoteRepository.flush();
		em.refresh(post);

		return new VoteResponse(resultVote, post.getGoCount(), post.getStopCount());
	}
}
