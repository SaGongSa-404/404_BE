package com.fourohfour.backend.api.facades.onboarding;

import com.fourohfour.backend.modules.auth.application.AuthService;
import com.fourohfour.backend.modules.house.application.HouseService;
import com.fourohfour.backend.modules.shared.api.ApiException;
import com.fourohfour.backend.modules.space.application.SpaceService;
import com.fourohfour.backend.modules.user.application.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OnboardingFacadeService {

    private final AuthService authService;
    private final UserService userService;
    private final HouseService houseService;
    private final SpaceService spaceService;

    public OnboardingFacadeService(
            AuthService authService,
            UserService userService,
            HouseService houseService,
            SpaceService spaceService
    ) {
        this.authService = authService;
        this.userService = userService;
        this.houseService = houseService;
        this.spaceService = spaceService;
    }

    public BootstrapView getBootstrap(UUID userId) {
        List<String> missingTerms = authService.listMissingRequiredTerms(userId);
        HouseService.ActiveHouseView activeHouse = houseService.getActiveHouse(userId).orElse(null);

        String nextStep;
        if (!missingTerms.isEmpty()) {
            nextStep = "ACCEPT_REQUIRED_TERMS";
        } else if (activeHouse == null) {
            nextStep = "CREATE_OR_JOIN_HOUSE";
        } else if (spaceService.listSpaces(userId, activeHouse.houseId()).isEmpty()) {
            nextStep = "ADD_SPACES";
        } else {
            nextStep = "READY";
        }

        boolean isNewUser = activeHouse == null && missingTerms.size() == 2;
        return new BootstrapView(isNewUser, activeHouse != null, activeHouse == null ? null : activeHouse.houseId(), !missingTerms.isEmpty(), nextStep);
    }

    public HouseSetupView getHouseSetup(UUID userId) {
        UserService.UserProfileView profile = userService.getMyProfile(userId);
        HouseService.ActiveHouseView activeHouse = houseService.getActiveHouse(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HOUSE_NOT_FOUND", "현재 참여 중인 집이 없습니다."));
        HouseService.InviteCodeView inviteCode = houseService.getLatestInviteCode(userId, activeHouse.houseId()).orElse(null);
        String cleanlinessVote = houseService.getMyCleanlinessVote(userId, activeHouse.houseId()).orElse(null);
        List<SpaceService.SpaceView> spaces = spaceService.listSpaces(userId, activeHouse.houseId());

        return new HouseSetupView(
                profile.userId(),
                profile.nickname(),
                activeHouse.houseId(),
                activeHouse.name(),
                activeHouse.cleanlinessLevel(),
                activeHouse.memberCount(),
                inviteCode == null ? null : inviteCode.code(),
                inviteCode == null ? null : inviteCode.expiresAt(),
                cleanlinessVote,
                spaces.stream()
                        .map(space -> new HouseSetupSpaceView(space.spaceId(), space.name(), space.sortOrder()))
                        .toList()
        );
    }

    public record BootstrapView(
            boolean isNewUser,
            boolean hasActiveHouse,
            UUID activeHouseId,
            boolean requiredTermsPending,
            String nextStep
    ) {
    }

    public record HouseSetupView(
            UUID userId,
            String nickname,
            UUID houseId,
            String houseName,
            String cleanlinessLevel,
            int memberCount,
            String inviteCode,
            java.time.Instant inviteCodeExpiresAt,
            String cleanlinessVote,
            List<HouseSetupSpaceView> spaces
    ) {
    }

    public record HouseSetupSpaceView(
            UUID spaceId,
            String name,
            int sortOrder
    ) {
    }
}
