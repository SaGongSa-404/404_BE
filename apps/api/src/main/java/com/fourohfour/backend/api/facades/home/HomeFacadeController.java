package com.fourohfour.backend.api.facades.home;

import com.fourohfour.backend.modules.auth.domain.CurrentAuthenticatedUser;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/facades/home")
public class HomeFacadeController {

    private final HomeFacadeService homeFacadeService;

    public HomeFacadeController(HomeFacadeService homeFacadeService) {
        this.homeFacadeService = homeFacadeService;
    }

    @GetMapping
    public HomeFacadeService.HomeView getHome(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        return homeFacadeService.getHome(CurrentAuthenticatedUser.userId(authentication), targetDate);
    }
}

