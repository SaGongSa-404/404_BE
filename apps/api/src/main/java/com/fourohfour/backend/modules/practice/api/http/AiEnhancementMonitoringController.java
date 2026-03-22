package com.fourohfour.backend.modules.practice.api.http;

import com.fourohfour.backend.modules.auth.domain.CurrentAuthenticatedUser;
import com.fourohfour.backend.modules.practice.application.PracticeCardService;
import com.fourohfour.backend.modules.practice.application.PracticeCardService.AiEnhancementMonitoringView;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/monitoring")
public class AiEnhancementMonitoringController {

    private final PracticeCardService practiceCardService;

    public AiEnhancementMonitoringController(PracticeCardService practiceCardService) {
        this.practiceCardService = practiceCardService;
    }

    @GetMapping("/ai-enhancements")
    public AiEnhancementMonitoringView getAiEnhancementMonitoring(Authentication authentication) {
        return practiceCardService.getEnhancementMonitoringView(CurrentAuthenticatedUser.userId(authentication));
    }
}
