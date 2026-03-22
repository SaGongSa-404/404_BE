package com.fourohfour.backend.modules.content.application;

import com.fourohfour.backend.modules.content.infrastructure.ContentJdbcRepository;
import com.fourohfour.backend.modules.content.infrastructure.ContentJdbcRepository.SavedContentRecord;
import com.fourohfour.backend.modules.content.infrastructure.OllamaProperties;
import com.fourohfour.backend.modules.practice.application.PracticeCardService;
import com.fourohfour.backend.modules.practice.application.PracticeCardService.PracticeCardView;
import com.fourohfour.backend.modules.practice.domain.CardStatus;
import com.fourohfour.backend.modules.shared.api.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

@Service
public class ContentService {

    private final ContentJdbcRepository contentJdbcRepository;
    private final ContentLinkPolicy contentLinkPolicy;
    private final ContentScraper contentScraper;
    private final HeuristicActionCardGenerator heuristicActionCardGenerator;
    private final PracticeCardService practiceCardService;
    private final Clock clock;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final OllamaProperties ollamaProperties;

    public ContentService(
            ContentJdbcRepository contentJdbcRepository,
            ContentLinkPolicy contentLinkPolicy,
            ContentScraper contentScraper,
            HeuristicActionCardGenerator heuristicActionCardGenerator,
            PracticeCardService practiceCardService,
            Clock clock,
            ApplicationEventPublisher applicationEventPublisher,
            OllamaProperties ollamaProperties
    ) {
        this.contentJdbcRepository = contentJdbcRepository;
        this.contentLinkPolicy = contentLinkPolicy;
        this.contentScraper = contentScraper;
        this.heuristicActionCardGenerator = heuristicActionCardGenerator;
        this.practiceCardService = practiceCardService;
        this.clock = clock;
        this.applicationEventPublisher = applicationEventPublisher;
        this.ollamaProperties = ollamaProperties;
    }

    @Transactional
    public SaveContentResult save(SaveContentCommand command) {
        contentLinkPolicy.validate(command.url());
        contentJdbcRepository.ensureUser(command.userId());
        ScrapedContent scrapedContent = contentScraper.scrape(command.url());
        ActionCardGenerationSource generationSource = new ActionCardGenerationSource(
                scrapedContent.effectiveUrl(),
                scrapedContent.sourceDomain(),
                command.title(),
                command.note(),
                command.tags(),
                scrapedContent.title(),
                scrapedContent.description(),
                scrapedContent.text(),
                scrapedContent.sourceType(),
                scrapedContent.author(),
                scrapedContent.siteName(),
                scrapedContent.imageUrls()
        );

        LocalDate today = LocalDate.now(clock);
        GeneratedPracticeCard generatedPracticeCard = heuristicActionCardGenerator.generate(
                generationSource,
                today
        );

        OffsetDateTime now = OffsetDateTime.now(clock);
        SavedContentRecord savedContentRecord = contentJdbcRepository.save(
                command.userId(),
                scrapedContent.effectiveUrl(),
                fallbackTitle(command.title(), scrapedContent.title(), command.url()),
                fallbackNote(command.note(), scrapedContent.description()),
                generatedPracticeCard.category(),
                command.tags(),
                now
        );
        PracticeCardView practiceCard = practiceCardService.createFromGeneratedCard(
                command.userId(),
                savedContentRecord.id(),
                generatedPracticeCard,
                now,
                initialEnhancementStatus(),
                initialEnhancementNote()
        );
        publishEnhancementRequest(command.userId(), practiceCard.id(), generationSource, today);

        return new SaveContentResult(
                new SavedContentView(
                        savedContentRecord.id(),
                        savedContentRecord.url(),
                        savedContentRecord.title(),
                        savedContentRecord.note(),
                        savedContentRecord.sourceDomain(),
                        savedContentRecord.category().name(),
                        savedContentRecord.category().displayName(),
                        savedContentRecord.createdAt()
                ),
                practiceCard
        );
    }

    private void publishEnhancementRequest(
            UUID userId,
            UUID practiceCardId,
            ActionCardGenerationSource generationSource,
            LocalDate today
    ) {
        if (!ollamaProperties.enabled() || !ollamaProperties.backgroundEnhancementEnabled()) {
            return;
        }
        applicationEventPublisher.publishEvent(new PracticeCardEnhancementRequestedEvent(
                userId,
                practiceCardId,
                generationSource,
                today
        ));
    }

    @Transactional
    public PracticeCardView requestRegeneration(UUID userId, UUID cardId) {
        PracticeCardView card = practiceCardService.getCard(userId, cardId);
        if (!CardStatus.OPEN.name().equals(card.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CARD_NOT_OPEN", "열린 카드만 다시 생성할 수 있어요.");
        }

        SavedContentRecord savedContent = contentJdbcRepository.getById(userId, card.savedContentId());
        ScrapedContent scrapedContent = contentScraper.scrape(savedContent.url());
        ActionCardGenerationSource generationSource = new ActionCardGenerationSource(
                scrapedContent.effectiveUrl(),
                scrapedContent.sourceDomain(),
                savedContent.title(),
                savedContent.note(),
                savedContent.tags(),
                scrapedContent.title(),
                scrapedContent.description(),
                scrapedContent.text(),
                scrapedContent.sourceType(),
                scrapedContent.author(),
                scrapedContent.siteName(),
                scrapedContent.imageUrls()
        );

        PracticeCardView updatedCard = practiceCardService.markEnhancementPending(userId, cardId, "사용자가 AI 재생성을 요청했어요.");
        publishEnhancementRequest(userId, cardId, generationSource, LocalDate.now(clock));
        return updatedCard;
    }

    private String initialEnhancementStatus() {
        if (!ollamaProperties.enabled()) {
            return "DISABLED";
        }
        return ollamaProperties.backgroundEnhancementEnabled() ? "PENDING" : "DISABLED";
    }

    private String initialEnhancementNote() {
        if (!ollamaProperties.enabled()) {
            return "AI 업그레이드가 꺼져 있어요.";
        }
        return ollamaProperties.backgroundEnhancementEnabled()
                ? "AI 업그레이드를 준비 중이에요."
                : "AI 업그레이드가 비활성화돼 있어요.";
    }

    private String fallbackTitle(String requestedTitle, String scrapedTitle, String url) {
        if (requestedTitle != null && !requestedTitle.isBlank()) {
            return requestedTitle.trim();
        }
        if (scrapedTitle != null && !scrapedTitle.isBlank()) {
            return scrapedTitle.trim();
        }
        return url;
    }

    private String fallbackNote(String requestedNote, String scrapedDescription) {
        if (requestedNote != null && !requestedNote.isBlank()) {
            return requestedNote.trim();
        }
        if (scrapedDescription != null && !scrapedDescription.isBlank()) {
            return scrapedDescription.trim();
        }
        return null;
    }

    public record SaveContentCommand(
            UUID userId,
            String url,
            String title,
            String note,
            List<String> tags
    ) {
    }

    public record SaveContentResult(
            SavedContentView savedContent,
            PracticeCardView practiceCard
    ) {
    }

    public record SavedContentView(
            UUID id,
            String url,
            String title,
            String note,
            String sourceDomain,
            String category,
            String categoryLabel,
            OffsetDateTime createdAt
    ) {
    }
}
