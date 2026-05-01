package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.enums.PostVoteType;
import jakarta.validation.constraints.NotNull;

record VoteRequest(
	@NotNull(message = "투표 타입은 필수입니다.")
	PostVoteType voteType
) {}
