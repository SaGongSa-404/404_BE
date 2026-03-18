package com.fourohfour.backend.modules.auth.api.http;

import com.fourohfour.backend.modules.auth.application.AuthService;
import com.fourohfour.backend.modules.auth.domain.CurrentAuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/kakao/login")
    public LoginResponse loginWithKakao(@Valid @RequestBody KakaoLoginRequest request) {
        AuthService.LoginResult result = authService.loginWithKakao(
                new AuthService.LoginCommand(request.authorizationCode(), request.deviceId(), request.deviceName())
        );
        return new LoginResponse(result.userId(), result.isNewUser(), result.accessToken(), result.refreshToken(), result.requiredTerms());
    }

    @PostMapping("/terms/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptTerms(Authentication authentication, @Valid @RequestBody AcceptTermsRequest request) {
        authService.acceptRequiredTerms(
                CurrentAuthenticatedUser.userId(authentication),
                new AuthService.AcceptTermsCommand(
                        request.terms().stream()
                                .map(term -> new AuthService.TermsAgreementInput(term.type(), term.version()))
                                .toList()
                )
        );
    }

    @PostMapping("/session/refresh")
    public RefreshSessionResponse refreshSession(@Valid @RequestBody RefreshSessionRequest request) {
        AuthService.RefreshSessionResult result = authService.refreshSession(request.refreshToken());
        return new RefreshSessionResponse(result.accessToken(), result.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {
        authService.logout(CurrentAuthenticatedUser.sessionId(authentication));
    }

    @GetMapping("/me")
    public AuthenticatedUserResponse me(Authentication authentication) {
        UUID userId = CurrentAuthenticatedUser.userId(authentication);
        AuthService.AuthenticatedUserView result = authService.getAuthenticatedUser(userId);
        return new AuthenticatedUserResponse(result.userId(), result.nickname(), result.profileImageUrl(), result.email());
    }

    public record KakaoLoginRequest(
            @NotBlank String authorizationCode,
            String redirectUri,
            String deviceId,
            String deviceName
    ) {
    }

    public record LoginResponse(
            UUID userId,
            boolean isNewUser,
            String accessToken,
            String refreshToken,
            List<String> requiredTerms
    ) {
    }

    public record AcceptTermsRequest(@NotEmpty List<@Valid TermsRequest> terms) {
    }

    public record TermsRequest(
            @NotBlank String type,
            @NotBlank String version
    ) {
    }

    public record RefreshSessionRequest(@NotBlank String refreshToken) {
    }

    public record RefreshSessionResponse(
            String accessToken,
            String refreshToken
    ) {
    }

    public record AuthenticatedUserResponse(
            UUID userId,
            String nickname,
            String profileImageUrl,
            String email
    ) {
    }
}
