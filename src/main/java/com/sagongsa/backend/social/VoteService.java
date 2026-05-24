package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.enums.PostVoteType;
import com.sagongsa.backend.domain.social.FeedPost;
import com.sagongsa.backend.domain.social.PostVote;
import com.sagongsa.backend.domain.social.PostVoteRepository;
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

	VoteService(PostVoteRepository postVoteRepository,
		SocialPostService socialPostService,
		UserAccountRepository userAccountRepository) {
		this.postVoteRepository = postVoteRepository;
		this.socialPostService = socialPostService;
		this.userAccountRepository = userAccountRepository;
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
				applyVote(post, voteType, true);
				resultVote = voteType;
			} else if (vote.getVoteType() == voteType) {
				applyVote(post, voteType, false);
				vote.cancel();
				resultVote = null;
			} else {
				applyVote(post, vote.getVoteType(), false);
				applyVote(post, voteType, true);
				vote.changeVoteType(voteType);
				resultVote = voteType;
			}
		} else {
			PostVote newVote = new PostVote(post, user, voteType);
			postVoteRepository.save(newVote);
			applyVote(post, voteType, true);
			resultVote = voteType;
		}

		return new VoteResponse(resultVote, post.getGoCount(), post.getStopCount());
	}

	private void applyVote(FeedPost post, PostVoteType voteType, boolean increment) {
		if (voteType == PostVoteType.GO) {
			if (increment) post.incrementGoCount(); else post.decrementGoCount();
		} else {
			if (increment) post.incrementStopCount(); else post.decrementStopCount();
		}
	}
}
