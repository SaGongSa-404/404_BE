package com.example._04_backend.domain.user.controller;

import com.example._04_backend.domain.social.dto.response.PostListResponse;
import com.example._04_backend.domain.user.dto.request.UpdateNicknameRequest;
import com.example._04_backend.domain.user.dto.response.MyProfileResponse;
import com.example._04_backend.domain.user.service.UserService;
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

    @GetMapping
    public ResponseEntity<MyProfileResponse> getMyProfile(
            @AuthenticationPrincipal LoginUser loginUser) {
        return ResponseEntity.ok(userService.getMyProfile(loginUser.getId()));
    }

    @PatchMapping
    public ResponseEntity<MyProfileResponse> updateNickname(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody UpdateNicknameRequest request) {
        return ResponseEntity.ok(userService.updateNickname(loginUser.getId(), request));
    }

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
