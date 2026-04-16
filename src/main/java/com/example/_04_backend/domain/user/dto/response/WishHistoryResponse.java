package com.example._04_backend.domain.user.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WishHistoryResponse {
    private List<WishSummaryResponse> wishes;
    private long total;
    private int page;
    private int size;
}
