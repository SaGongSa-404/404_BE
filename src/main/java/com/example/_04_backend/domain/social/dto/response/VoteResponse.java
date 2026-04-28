package com.example._04_backend.domain.social.dto.response;

import com.example._04_backend.domain.social.enums.PostVoteType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VoteResponse {

    private PostVoteType myVote;
    private int goCount;
    private int stopCount;
}
