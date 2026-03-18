package com.fourohfour.backend.api.facades.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fourohfour.backend.modules.auth.application.AuthService;
import com.fourohfour.backend.modules.house.application.HouseService;
import com.fourohfour.backend.modules.space.application.SpaceService;
import com.fourohfour.backend.modules.user.application.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnboardingFacadeServiceTest {

    @Mock
    private AuthService authService;
    @Mock
    private UserService userService;
    @Mock
    private HouseService houseService;
    @Mock
    private SpaceService spaceService;

    private OnboardingFacadeService onboardingFacadeService;

    @BeforeEach
    void setUp() {
        onboardingFacadeService = new OnboardingFacadeService(authService, userService, houseService, spaceService);
    }

    @Test
    void bootstrap_returnsAcceptTermsStep_whenRequiredTermsRemain() {
        UUID userId = UUID.randomUUID();

        when(authService.listMissingRequiredTerms(userId)).thenReturn(List.of("SERVICE", "PRIVACY"));
        when(houseService.getActiveHouse(userId)).thenReturn(Optional.empty());

        OnboardingFacadeService.BootstrapView result = onboardingFacadeService.getBootstrap(userId);

        assertThat(result.requiredTermsPending()).isTrue();
        assertThat(result.nextStep()).isEqualTo("ACCEPT_REQUIRED_TERMS");
        assertThat(result.isNewUser()).isTrue();
    }
}

