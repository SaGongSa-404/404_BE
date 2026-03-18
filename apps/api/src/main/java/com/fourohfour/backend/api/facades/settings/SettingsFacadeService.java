package com.fourohfour.backend.api.facades.settings;

import com.fourohfour.backend.modules.house.application.HouseService;
import com.fourohfour.backend.modules.review.application.ReviewService;
import com.fourohfour.backend.modules.shared.api.ApiException;
import com.fourohfour.backend.modules.user.application.UserService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SettingsFacadeService {

    private final UserService userService;
    private final HouseService houseService;
    private final ReviewService reviewService;

    public SettingsFacadeService(UserService userService, HouseService houseService, ReviewService reviewService) {
        this.userService = userService;
        this.houseService = houseService;
        this.reviewService = reviewService;
    }

    public SettingsView getSettings(UUID userId) {
        UserService.UserProfileView profile = userService.getMyProfile(userId);
        HouseService.ActiveHouseView house = houseService.getActiveHouse(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HOUSE_NOT_FOUND", "현재 참여 중인 집이 없습니다."));
        ReviewService.WeeklyRecapView latestWeeklyRecap = reviewService.findLatestWeeklyRecap(userId, house.houseId()).orElse(null);
        String weeklySatisfactionStatus = latestWeeklyRecap == null
                ? "NOT_READY"
                : (latestWeeklyRecap.satisfactionSubmitted() ? "SUBMITTED" : "PENDING");
        return new SettingsView(profile, house, latestWeeklyRecap, weeklySatisfactionStatus);
    }

    public record SettingsView(
            UserService.UserProfileView profile,
            HouseService.ActiveHouseView houseSummary,
            ReviewService.WeeklyRecapView latestWeeklyRecap,
            String weeklySatisfactionStatus
    ) {
    }
}
