package com.fourohfour.backend.api.facades.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fourohfour.backend.modules.house.application.HouseService;
import com.fourohfour.backend.modules.review.application.ReviewService;
import com.fourohfour.backend.modules.user.application.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettingsFacadeServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private HouseService houseService;
    @Mock
    private ReviewService reviewService;

    private SettingsFacadeService settingsFacadeService;

    @BeforeEach
    void setUp() {
        settingsFacadeService = new SettingsFacadeService(userService, houseService, reviewService);
    }

    @Test
    void getSettings_returnsNotReady_whenWeeklyRecapDoesNotExistYet() {
        UUID userId = UUID.randomUUID();
        UUID houseId = UUID.randomUUID();

        when(userService.getMyProfile(userId)).thenReturn(new UserService.UserProfileView(userId, "demo", null, "demo@404.local"));
        when(houseService.getActiveHouse(userId)).thenReturn(Optional.of(new HouseService.ActiveHouseView(houseId, "demo-house", "BALANCED", 2)));
        when(reviewService.findLatestWeeklyRecap(userId, houseId)).thenReturn(Optional.empty());

        SettingsFacadeService.SettingsView result = settingsFacadeService.getSettings(userId);

        assertThat(result.latestWeeklyRecap()).isNull();
        assertThat(result.weeklySatisfactionStatus()).isEqualTo("NOT_READY");
    }
}
