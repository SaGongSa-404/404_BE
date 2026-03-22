package com.fourohfour.backend.modules.content.application;

import com.fourohfour.backend.modules.content.domain.ActionCardCategory;
import com.fourohfour.backend.modules.practice.domain.EnergyLevel;
import java.time.LocalDate;
import java.util.List;

public record GeneratedPracticeCard(
        ActionCardCategory category,
        String actionTitle,
        String actionDetail,
        String detailTitle,
        String detailBody,
        List<PracticeCardSection> detailSections,
        List<String> ideaOptions,
        String encouragementMessage,
        String rationale,
        int estimatedMinutes,
        EnergyLevel energyLevel,
        LocalDate scheduledFor
) {
}
