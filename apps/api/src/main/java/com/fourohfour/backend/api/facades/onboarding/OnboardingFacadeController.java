package com.fourohfour.backend.api.facades.onboarding;

import com.fourohfour.backend.modules.auth.domain.CurrentAuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/facades/onboarding")
public class OnboardingFacadeController {

    private final OnboardingFacadeService onboardingFacadeService;

    public OnboardingFacadeController(OnboardingFacadeService onboardingFacadeService) {
        this.onboardingFacadeService = onboardingFacadeService;
    }

    @GetMapping("/bootstrap")
    public OnboardingFacadeService.BootstrapView bootstrap(Authentication authentication) {
        return onboardingFacadeService.getBootstrap(CurrentAuthenticatedUser.userId(authentication));
    }

    @GetMapping("/house-setup")
    public OnboardingFacadeService.HouseSetupView houseSetup(Authentication authentication) {
        return onboardingFacadeService.getHouseSetup(CurrentAuthenticatedUser.userId(authentication));
    }
}
