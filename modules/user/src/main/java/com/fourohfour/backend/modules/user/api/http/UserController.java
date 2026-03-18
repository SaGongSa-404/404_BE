package com.fourohfour.backend.modules.user.api.http;

import com.fourohfour.backend.modules.auth.domain.CurrentAuthenticatedUser;
import com.fourohfour.backend.modules.user.application.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserProfileResponse getMyProfile(Authentication authentication) {
        UUID userId = CurrentAuthenticatedUser.userId(authentication);
        UserService.UserProfileView view = userService.getMyProfile(userId);
        return new UserProfileResponse(view.userId(), view.nickname(), view.profileImageUrl(), view.email());
    }

    @PatchMapping
    public UserProfileResponse updateProfile(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = CurrentAuthenticatedUser.userId(authentication);
        UserService.UserProfileView view = userService.updateProfile(
                userId,
                new UserService.UpdateProfileCommand(request.nickname(), request.profileImageUrl())
        );
        return new UserProfileResponse(view.userId(), view.nickname(), view.profileImageUrl(), view.email());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdrawUser(Authentication authentication) {
        userService.withdrawUser(CurrentAuthenticatedUser.userId(authentication));
    }

    public record UpdateProfileRequest(
            @Size(max = 40) String nickname,
            String profileImageUrl
    ) {
    }

    public record UserProfileResponse(
            UUID userId,
            String nickname,
            String profileImageUrl,
            String email
    ) {
    }
}

