package com.fourohfour.backend.modules.user.application;

import com.fourohfour.backend.modules.shared.api.ApiException;
import com.fourohfour.backend.modules.user.infrastructure.UserJdbcRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserJdbcRepository userJdbcRepository;

    public UserService(UserJdbcRepository userJdbcRepository) {
        this.userJdbcRepository = userJdbcRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileView getMyProfile(UUID userId) {
        UserJdbcRepository.UserProfileRecord record = userJdbcRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        return new UserProfileView(record.userId(), record.nickname(), record.profileImageUrl(), record.email());
    }

    @Transactional
    public UserProfileView updateProfile(UUID userId, UpdateProfileCommand command) {
        UserProfileView current = getMyProfile(userId);
        String nickname = command.nickname() == null || command.nickname().isBlank() ? current.nickname() : command.nickname();
        userJdbcRepository.updateProfile(userId, nickname, command.profileImageUrl(), Instant.now());
        return getMyProfile(userId);
    }

    @Transactional
    public void withdrawUser(UUID userId) {
        getMyProfile(userId);
        userJdbcRepository.markWithdrawn(userId, Instant.now());
    }

    public record UpdateProfileCommand(
            String nickname,
            String profileImageUrl
    ) {
    }

    public record UserProfileView(
            UUID userId,
            String nickname,
            String profileImageUrl,
            String email
    ) {
    }
}

