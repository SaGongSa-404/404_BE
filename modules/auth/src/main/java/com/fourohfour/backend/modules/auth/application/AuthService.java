package com.fourohfour.backend.modules.auth.application;

import com.fourohfour.backend.modules.auth.domain.SessionUserPrincipal;
import com.fourohfour.backend.modules.auth.infrastructure.AuthJdbcRepository;
import com.fourohfour.backend.modules.shared.api.ApiException;
import com.fourohfour.backend.packages.kakao.KakaoProfileClient;
import com.fourohfour.backend.packages.kakao.KakaoUserProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final List<String> REQUIRED_TERMS = List.of("SERVICE", "PRIVACY");
    private static final Duration SESSION_DURATION = Duration.ofDays(30);

    private final AuthJdbcRepository authJdbcRepository;
    private final KakaoProfileClient kakaoProfileClient;

    public AuthService(AuthJdbcRepository authJdbcRepository, KakaoProfileClient kakaoProfileClient) {
        this.authJdbcRepository = authJdbcRepository;
        this.kakaoProfileClient = kakaoProfileClient;
    }

    @Transactional
    public LoginResult loginWithKakao(LoginCommand command) {
        KakaoUserProfile kakaoUserProfile = kakaoProfileClient.fetchProfile(command.authorizationCode());
        Instant now = Instant.now();

        Optional<AuthJdbcRepository.SocialAccountRecord> socialAccount = authJdbcRepository.findKakaoAccount(kakaoUserProfile.providerUserId());
        UUID userId;
        boolean isNewUser;

        if (socialAccount.isPresent()) {
            userId = socialAccount.get().userId();
            isNewUser = false;
            authJdbcRepository.touchLogin(userId, kakaoUserProfile.providerUserId(), now);
        } else {
            userId = UUID.randomUUID();
            isNewUser = true;
            authJdbcRepository.createUser(userId, kakaoUserProfile.email(), now);
            authJdbcRepository.createUserProfile(UUID.randomUUID(), userId, kakaoUserProfile.nickname(), kakaoUserProfile.profileImageUrl(), now);
            authJdbcRepository.createSocialAccount(UUID.randomUUID(), userId, kakaoUserProfile.providerUserId(), kakaoUserProfile.email(), now);
        }

        String sessionToken = newSessionToken();
        UUID sessionId = UUID.randomUUID();
        authJdbcRepository.createSession(
                sessionId,
                userId,
                hashToken(sessionToken),
                command.deviceId(),
                command.deviceName(),
                now.plus(SESSION_DURATION),
                now
        );

        return new LoginResult(
                userId,
                isNewUser,
                sessionToken,
                sessionToken,
                listMissingRequiredTerms(userId)
        );
    }

    @Transactional(readOnly = true)
    public Optional<SessionUserPrincipal> authenticate(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return Optional.empty();
        }

        return authJdbcRepository.findActiveSessionByTokenHash(hashToken(sessionToken))
                .filter(session -> session.expiresAt().isAfter(Instant.now()))
                .map(session -> new SessionUserPrincipal(session.sessionId(), session.userId()));
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserView getAuthenticatedUser(UUID userId) {
        AuthJdbcRepository.AuthenticatedUserRecord record = authJdbcRepository.getAuthenticatedUser(userId);
        return new AuthenticatedUserView(record.userId(), record.nickname(), record.profileImageUrl(), record.email());
    }

    @Transactional
    public void acceptRequiredTerms(UUID userId, AcceptTermsCommand command) {
        Instant now = Instant.now();
        for (TermsAgreementInput input : command.terms()) {
            authJdbcRepository.saveTermsAgreement(
                    UUID.randomUUID(),
                    userId,
                    input.type(),
                    input.version(),
                    REQUIRED_TERMS.contains(input.type()),
                    now
            );
        }
    }

    @Transactional
    public RefreshSessionResult refreshSession(String refreshToken) {
        AuthJdbcRepository.SessionRecord sessionRecord = authJdbcRepository.findActiveSessionByTokenHash(hashToken(refreshToken))
                .filter(session -> session.expiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "유효하지 않은 세션입니다."));

        Instant now = Instant.now();
        String rotatedToken = newSessionToken();
        authJdbcRepository.rotateSession(sessionRecord.sessionId(), hashToken(rotatedToken), now.plus(SESSION_DURATION), now);
        return new RefreshSessionResult(rotatedToken, rotatedToken);
    }

    @Transactional
    public void logout(UUID sessionId) {
        authJdbcRepository.revokeSession(sessionId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<String> listMissingRequiredTerms(UUID userId) {
        Set<String> agreedTerms = new LinkedHashSet<>(authJdbcRepository.findAgreedRequiredTerms(userId));
        return REQUIRED_TERMS.stream()
                .filter(requiredTerm -> !agreedTerms.contains(requiredTerm))
                .toList();
    }

    private String newSessionToken() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    }

    private String hashToken(String sessionToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = messageDigest.digest(sessionToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    public record LoginCommand(
            String authorizationCode,
            String deviceId,
            String deviceName
    ) {
    }

    public record LoginResult(
            UUID userId,
            boolean isNewUser,
            String accessToken,
            String refreshToken,
            List<String> requiredTerms
    ) {
    }

    public record TermsAgreementInput(
            String type,
            String version
    ) {
    }

    public record AcceptTermsCommand(List<TermsAgreementInput> terms) {
    }

    public record RefreshSessionResult(
            String accessToken,
            String refreshToken
    ) {
    }

    public record AuthenticatedUserView(
            UUID userId,
            String nickname,
            String profileImageUrl,
            String email
    ) {
    }
}

