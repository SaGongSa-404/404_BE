package com.example._04_backend.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AvailableMonthsResponse {
    private List<String> months;
    private String currentMonth;
}
