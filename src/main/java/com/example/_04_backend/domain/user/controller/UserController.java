package com.example._04_backend.domain.user.controller;

import com.example._04_backend.domain.social.dto.response.PostListResponse;
import com.example._04_backend.domain.user.dto.request.NotificationSettingsRequest;
import com.example._04_backend.domain.user.dto.request.UpdateBudgetRequest;
import com.example._04_backend.domain.user.dto.request.UpdateNicknameRequest;
import com.example._04_backend.domain.user.dto.request.UpdateProfileRequest;
import com.example._04_backend.domain.user.dto.response.*;
import com.example._04_backend.domain.user.service.UserService;
import com.example._04_backend.domain.wish.enums.WishStatus;
import com.example._04_backend.global.auth.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ─── 내 정보 ───────────────────────────────────────────

    @GetMapping
    public ResponseEntity<MyProfileResponse> getMyProfile(
            @AuthenticationPrincipal LoginUser loginUser) {
        return ResponseEntity.ok(userService.getMyProfile(loginUser.getId()));
    }

    /** 닉네임 + 너구리 이름 수정 */
    @PatchMapping("/profile")
    public ResponseEntity<MyProfileResponse> updateProfile(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(loginUser.getId(), request));
    }

    /** 기존 닉네임 전용 PATCH (하위 호환) */
    @PatchMapping
    public ResponseEntity<MyProfileResponse> updateNickname(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody UpdateNicknameRequest request) {
        return ResponseEntity.ok(userService.updateNickname(loginUser.getId(), request));
    }

    /** 월 예산 설정 */
    @PatchMapping("/budget")
    public ResponseEntity<BudgetUpdateResponse> updateBudget(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody UpdateBudgetRequest request) {
        return ResponseEntity.ok(userService.updateBudget(loginUser.getId(), request));
    }

    /** 알림 설정 변경 */
    @PatchMapping("/notification-settings")
    public ResponseEntity<NotificationSettingsResponse> updateNotificationSettings(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody NotificationSettingsRequest request) {
        return ResponseEntity.ok(userService.updateNotificationSettings(loginUser.getId(), request));
    }

    /** 계정 탈퇴 */
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal LoginUser loginUser) {
        userService.deleteAccount(loginUser.getId());
        return ResponseEntity.noContent().build();
    }

    // ─── 소비 관리 ─────────────────────────────────────────

    /** 소비 통계 */
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) String yearMonth) {
        return ResponseEntity.ok(userService.getStats(loginUser.getId(), yearMonth));
    }

    /** 소비/절제 기록 목록 */
    @GetMapping("/wishes/history")
    public ResponseEntity<WishHistoryResponse> getWishHistory(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) WishStatus status,
            @RequestParam(required = false) String yearMonth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.getWishHistory(loginUser.getId(), status, yearMonth, page, size));
    }

    // ─── 나의 게시글 ────────────────────────────────────────

    @GetMapping("/posts")
    public ResponseEntity<PostListResponse> getMyPosts(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.getMyPosts(loginUser.getId(), cursor, size));
    }

    @GetMapping("/votes")
    public ResponseEntity<PostListResponse> getMyVotedPosts(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.getMyVotedPosts(loginUser.getId(), cursor, size));
    }
}
