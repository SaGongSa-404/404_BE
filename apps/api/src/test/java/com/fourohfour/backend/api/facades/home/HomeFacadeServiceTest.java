package com.fourohfour.backend.api.facades.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fourohfour.backend.modules.adjustment.application.AdjustmentService;
import com.fourohfour.backend.modules.chore.application.ChoreService;
import com.fourohfour.backend.modules.house.application.HouseService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HomeFacadeServiceTest {

    @Mock
    private HouseService houseService;
    @Mock
    private ChoreService choreService;
    @Mock
    private AdjustmentService adjustmentService;

    private HomeFacadeService homeFacadeService;

    @BeforeEach
    void setUp() {
        homeFacadeService = new HomeFacadeService(houseService, choreService, adjustmentService);
    }

    @Test
    void getHome_composesHouseChoreAndAdjustmentState() {
        UUID userId = UUID.randomUUID();
        UUID houseId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        when(houseService.getActiveHouse(userId)).thenReturn(Optional.of(new HouseService.ActiveHouseView(houseId, "demo-house", "BALANCED", 2)));
        when(choreService.listTodayChores(userId, houseId, today)).thenReturn(List.of());
        when(choreService.getDailyProgress(userId, houseId, today)).thenReturn(new ChoreService.DailyProgressView(1, 2, 50.0));
        when(choreService.countPendingActions(userId, houseId, today)).thenReturn(1L);
        when(adjustmentService.listOpenRequests(userId, houseId)).thenReturn(List.of());

        HomeFacadeService.HomeView result = homeFacadeService.getHome(userId, today);

        assertThat(result.house().houseId()).isEqualTo(houseId);
        assertThat(result.dailyProgress().completionRate()).isEqualTo(50.0);
        assertThat(result.myPendingActions()).isEqualTo(1L);
    }
}

