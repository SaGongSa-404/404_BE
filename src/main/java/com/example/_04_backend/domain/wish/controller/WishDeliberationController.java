package com.example._04_backend.domain.wish.controller;

import com.example._04_backend.domain.wish.dto.request.SubmitDeliberationRequest;
import com.example._04_backend.domain.wish.dto.response.DeliberationInfoResponse;
import com.example._04_backend.domain.wish.dto.response.DeliberationResultResponse;
import com.example._04_backend.domain.wish.service.WishDeliberationService;
import com.example._04_backend.global.auth.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/wishes/{wishId}/deliberation")
@RequiredArgsConstructor
public class WishDeliberationController {

    private final WishDeliberationService wishDeliberationService;

    /**
     * 구매 숙려 화면 조회
     * - 상품 정보, 이번 달 예산/남은 예산, 이달 소비 통계, 점검 질문 4가지 반환
     */
    @GetMapping
    public ResponseEntity<DeliberationInfoResponse> getDeliberationInfo(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID wishId) {
        return ResponseEntity.ok(
                wishDeliberationService.getDeliberationInfo(loginUser.getId(), wishId));
    }

    /**
     * 숙려 답변 제출 및 구매 결정
     * - answers: 질문 4개에 대한 Y(true)/N(false) 배열
     * - decision: BOUGHT(살게요) 또는 RESTRAINED(참을게요)
     */
    @PostMapping
    public ResponseEntity<DeliberationResultResponse> submitDeliberation(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID wishId,
            @Valid @RequestBody SubmitDeliberationRequest request) {
        return ResponseEntity.ok(
                wishDeliberationService.submitDeliberation(loginUser.getId(), wishId, request));
    }
}
