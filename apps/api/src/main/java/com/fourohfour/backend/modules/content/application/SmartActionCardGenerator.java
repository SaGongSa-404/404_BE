package com.fourohfour.backend.modules.content.application;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class SmartActionCardGenerator implements ActionCardGenerator {

    private static final Logger log = LoggerFactory.getLogger(SmartActionCardGenerator.class);

    private final OllamaActionCardGenerator ollamaActionCardGenerator;
    private final HeuristicActionCardGenerator heuristicActionCardGenerator;
    private final ActionCardSpecificityPolicy actionCardSpecificityPolicy;

    public SmartActionCardGenerator(
            OllamaActionCardGenerator ollamaActionCardGenerator,
            HeuristicActionCardGenerator heuristicActionCardGenerator,
            ActionCardSpecificityPolicy actionCardSpecificityPolicy
    ) {
        this.ollamaActionCardGenerator = ollamaActionCardGenerator;
        this.heuristicActionCardGenerator = heuristicActionCardGenerator;
        this.actionCardSpecificityPolicy = actionCardSpecificityPolicy;
    }

    @Override
    public GeneratedPracticeCard generate(ActionCardGenerationSource source, LocalDate today) {
        try {
            GeneratedPracticeCard generatedPracticeCard = ollamaActionCardGenerator.generate(source, today);
            if (!actionCardSpecificityPolicy.isSpecificEnough(source, generatedPracticeCard)) {
                log.warn("Ollama action card was too generic for source. Falling back to heuristic generator.");
                throw new IllegalStateException("Ollama action card was too generic.");
            }
            return generatedPracticeCard;
        } catch (Exception exception) {
            log.warn("Ollama action card generation failed. Falling back to heuristic generator: {}", exception.getMessage());
        }

        return heuristicActionCardGenerator.generate(source, today);
    }
}
