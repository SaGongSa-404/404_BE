package com.fourohfour.backend.modules.content.application;

import java.time.LocalDate;

public interface ActionCardGenerator {

    GeneratedPracticeCard generate(ActionCardGenerationSource source, LocalDate today);
}
