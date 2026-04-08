package com.example._04_backend.domain.social.dto.response;

import com.example._04_backend.domain.social.enums.VoteType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VoteResponse {

    private VoteType myVote;
    private int goCount;
    private int stopCount;
}
