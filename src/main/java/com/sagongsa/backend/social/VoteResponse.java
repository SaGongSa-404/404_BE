package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.enums.PostVoteType;

record VoteResponse(
	PostVoteType myVote,
	int goCount,
	int stopCount
) {}
