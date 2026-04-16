package com.example._04_backend.domain.terms.dto;

import com.example._04_backend.domain.terms.enums.TermsType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TermsSummaryResponse {
    private TermsType type;
    private String title;
    private boolean required;
}
