package com.fourohfour.backend.api.facades.home;

import com.fourohfour.backend.modules.adjustment.application.AdjustmentService;
import com.fourohfour.backend.modules.chore.application.ChoreService;
import com.fourohfour.backend.modules.house.application.HouseService;
import com.fourohfour.backend.modules.shared.api.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class HomeFacadeService {

    private final HouseService houseService;
    private final ChoreService choreService;
    private final AdjustmentService adjustmentService;

    public HomeFacadeService(HouseService houseService, ChoreService choreService, AdjustmentService adjustmentService) {
        this.houseService = houseService;
        this.choreService = choreService;
        this.adjustmentService = adjustmentService;
    }

    public HomeView getHome(UUID userId, LocalDate date) {
        HouseService.ActiveHouseView activeHouse = houseService.getActiveHouse(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HOUSE_NOT_FOUND", "현재 참여 중인 집이 없습니다."));

        List<ChoreService.TodayChoreView> todayChores = choreService.listTodayChores(userId, activeHouse.houseId(), date);
        ChoreService.DailyProgressView dailyProgress = choreService.getDailyProgress(userId, activeHouse.houseId(), date);
        long myPendingActions = choreService.countPendingActions(userId, activeHouse.houseId(), date);
        List<AdjustmentService.AdjustmentRequestView> openAdjustmentRequests = adjustmentService.listOpenRequests(userId, activeHouse.houseId());

        return new HomeView(
                activeHouse,
                todayChores,
                dailyProgress,
                openAdjustmentRequests,
                myPendingActions
        );
    }

    public record HomeView(
            HouseService.ActiveHouseView house,
            List<ChoreService.TodayChoreView> todayChores,
            ChoreService.DailyProgressView dailyProgress,
            List<AdjustmentService.AdjustmentRequestView> openAdjustmentRequests,
            long myPendingActions
    ) {
    }
}
