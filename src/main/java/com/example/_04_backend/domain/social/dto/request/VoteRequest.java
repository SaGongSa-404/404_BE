package com.example._04_backend.domain.social.dto.request;

import com.example._04_backend.domain.social.enums.PostVoteType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VoteRequest {

    @NotNull(message = "투표 타입은 필수입니다.")
    private PostVoteType voteType;
}
