package com.fourohfour.backend.api.facades.settings;

import com.fourohfour.backend.modules.auth.domain.CurrentAuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/facades/settings")
public class SettingsFacadeController {

    private final SettingsFacadeService settingsFacadeService;

    public SettingsFacadeController(SettingsFacadeService settingsFacadeService) {
        this.settingsFacadeService = settingsFacadeService;
    }

    @GetMapping
    public SettingsFacadeService.SettingsView getSettings(Authentication authentication) {
        return settingsFacadeService.getSettings(CurrentAuthenticatedUser.userId(authentication));
    }
}
