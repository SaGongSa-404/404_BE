package com.fourohfour.backend.modules.content.application;

import java.time.LocalDate;
import java.util.UUID;

public record PracticeCardEnhancementRequestedEvent(
        UUID userId,
        UUID practiceCardId,
        ActionCardGenerationSource source,
        LocalDate today
) {
}
