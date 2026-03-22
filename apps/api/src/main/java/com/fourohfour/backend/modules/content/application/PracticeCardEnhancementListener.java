package com.fourohfour.backend.modules.content.application;

import com.fourohfour.backend.modules.content.infrastructure.OllamaProperties;
import com.fourohfour.backend.modules.practice.application.PracticeCardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PracticeCardEnhancementListener {

    private static final Logger log = LoggerFactory.getLogger(PracticeCardEnhancementListener.class);

    private final OllamaActionCardGenerator ollamaActionCardGenerator;
    private final ActionCardSpecificityPolicy actionCardSpecificityPolicy;
    private final PracticeCardService practiceCardService;
    private final OllamaProperties ollamaProperties;

    public PracticeCardEnhancementListener(
            OllamaActionCardGenerator ollamaActionCardGenerator,
            ActionCardSpecificityPolicy actionCardSpecificityPolicy,
            PracticeCardService practiceCardService,
            OllamaProperties ollamaProperties
    ) {
        this.ollamaActionCardGenerator = ollamaActionCardGenerator;
        this.actionCardSpecificityPolicy = actionCardSpecificityPolicy;
        this.practiceCardService = practiceCardService;
        this.ollamaProperties = ollamaProperties;
    }

    @Async("ollamaEnhancementExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PracticeCardEnhancementRequestedEvent event) {
        if (!ollamaProperties.enabled() || !ollamaProperties.backgroundEnhancementEnabled()) {
            return;
        }

        log.info("Starting background Ollama enhancement. cardId={}", event.practiceCardId());

        try {
            GeneratedPracticeCard generatedPracticeCard = ollamaActionCardGenerator.generate(event.source(), event.today());
            if (!actionCardSpecificityPolicy.isSpecificEnough(event.source(), generatedPracticeCard)) {
                log.warn("Skipping Ollama enhancement because generated card was too generic. cardId={}", event.practiceCardId());
                practiceCardService.markEnhancementSkipped(event.userId(), event.practiceCardId(), "AI 결과가 기존 카드보다 충분히 명확하지 않았어요.");
                return;
            }

            boolean updated = practiceCardService.replaceCardContentIfOpen(
                    event.userId(),
                    event.practiceCardId(),
                    generatedPracticeCard
            );
            if (updated) {
                log.info("Practice card enhanced with Ollama. cardId={}", event.practiceCardId());
            }
        } catch (Exception exception) {
            practiceCardService.markEnhancementFailed(event.userId(), event.practiceCardId(), exception.getMessage());
            log.warn("Background Ollama enhancement failed. cardId={}, reason={}",
                    event.practiceCardId(),
                    exception.getMessage());
        }
    }
}
