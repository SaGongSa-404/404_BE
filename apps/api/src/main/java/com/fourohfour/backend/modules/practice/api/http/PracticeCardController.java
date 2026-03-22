package com.fourohfour.backend.modules.practice.api.http;

import com.fourohfour.backend.modules.auth.domain.CurrentAuthenticatedUser;
import com.fourohfour.backend.modules.content.application.ContentService;
import com.fourohfour.backend.modules.practice.application.PracticeCardService;
import com.fourohfour.backend.modules.practice.application.PracticeCardService.CompletePracticeCardCommand;
import com.fourohfour.backend.modules.practice.application.PracticeCardService.PracticeCardView;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/practice-cards")
public class PracticeCardController {

    private final PracticeCardService practiceCardService;
    private final ContentService contentService;

    public PracticeCardController(PracticeCardService practiceCardService, ContentService contentService) {
        this.practiceCardService = practiceCardService;
        this.contentService = contentService;
    }

    @GetMapping("/{cardId}")
    public PracticeCardView getCard(
            Authentication authentication,
            @PathVariable UUID cardId
    ) {
        return practiceCardService.getCard(CurrentAuthenticatedUser.userId(authentication), cardId);
    }

    @PostMapping("/{cardId}/complete")
    public PracticeCardView completeCard(
            Authentication authentication,
            @PathVariable UUID cardId,
            @RequestBody(required = false) CompletePracticeCardRequest request
    ) {
        String note = request == null ? null : request.completionNote();
        return practiceCardService.completeCard(new CompletePracticeCardCommand(
                CurrentAuthenticatedUser.userId(authentication),
                cardId,
                note
        ));
    }

    @PostMapping("/{cardId}/regenerate")
    public PracticeCardView regenerateCard(
            Authentication authentication,
            @PathVariable UUID cardId
    ) {
        return contentService.requestRegeneration(CurrentAuthenticatedUser.userId(authentication), cardId);
    }

    @PostMapping("/{cardId}/report-issue")
    public PracticeCardView reportIssue(
            Authentication authentication,
            @PathVariable UUID cardId,
            @RequestBody(required = false) ReportIssueRequest request
    ) {
        String reason = request == null ? null : request.reason();
        return practiceCardService.reportIssue(CurrentAuthenticatedUser.userId(authentication), cardId, reason);
    }

    public record CompletePracticeCardRequest(String completionNote) {
    }

    public record ReportIssueRequest(String reason) {
    }
}
