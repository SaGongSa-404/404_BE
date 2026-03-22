package com.fourohfour.backend.api.facades.reports;

import com.fourohfour.backend.modules.auth.domain.CurrentAuthenticatedUser;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/facades/reports")
public class WeeklyReportFacadeController {

    private final WeeklyReportFacadeService weeklyReportFacadeService;

    public WeeklyReportFacadeController(WeeklyReportFacadeService weeklyReportFacadeService) {
        this.weeklyReportFacadeService = weeklyReportFacadeService;
    }

    @GetMapping("/weekly")
    public WeeklyReportFacadeService.WeeklyReportView getWeeklyReport(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate anchorDate = date == null ? LocalDate.now() : date;
        return weeklyReportFacadeService.getWeeklyReport(
                CurrentAuthenticatedUser.userId(authentication),
                anchorDate
        );
    }
}
