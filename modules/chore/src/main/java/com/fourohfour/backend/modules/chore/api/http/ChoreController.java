package com.fourohfour.backend.modules.chore.api.http;

import com.fourohfour.backend.modules.auth.domain.CurrentAuthenticatedUser;
import com.fourohfour.backend.modules.chore.application.ChoreService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ChoreController {

    private final ChoreService choreService;

    public ChoreController(ChoreService choreService) {
        this.choreService = choreService;
    }

    @PostMapping("/houses/{houseId}/chores")
    @ResponseStatus(HttpStatus.CREATED)
    public ChoreRuleResponse createChore(Authentication authentication, @PathVariable UUID houseId, @Valid @RequestBody CreateChoreRequest request) {
        ChoreService.ChoreRuleView view = choreService.createChore(
                CurrentAuthenticatedUser.userId(authentication),
                houseId,
                new ChoreService.CreateChoreCommand(
                        request.spaceId(),
                        request.title(),
                        request.description(),
                        request.estimatedMinutes(),
                        request.defaultAssigneeMembershipId(),
                        new ChoreService.Recurrence(
                                request.recurrence().frequency(),
                                request.recurrence().interval(),
                                request.recurrence().daysOfWeek(),
                                request.recurrence().startDate()
                        )
                )
        );
        return new ChoreRuleResponse(view.choreRuleId(), view.houseId(), view.spaceId(), view.title(), view.description(), view.estimatedMinutes());
    }

    @PatchMapping("/chores/{choreRuleId}")
    public ChoreRuleResponse updateChore(Authentication authentication, @PathVariable UUID choreRuleId, @Valid @RequestBody UpdateChoreRequest request) {
        ChoreService.ChoreRuleView view = choreService.updateChore(
                CurrentAuthenticatedUser.userId(authentication),
                choreRuleId,
                new ChoreService.UpdateChoreCommand(request.title(), request.description(), request.estimatedMinutes())
        );
        return new ChoreRuleResponse(view.choreRuleId(), view.houseId(), view.spaceId(), view.title(), view.description(), view.estimatedMinutes());
    }

    @DeleteMapping("/chores/{choreRuleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChore(Authentication authentication, @PathVariable UUID choreRuleId) {
        choreService.deleteChore(CurrentAuthenticatedUser.userId(authentication), choreRuleId);
    }

    @GetMapping("/houses/{houseId}/chores/today")
    public List<TodayChoreResponse> listTodayChores(
            Authentication authentication,
            @PathVariable UUID houseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        return choreService.listTodayChores(CurrentAuthenticatedUser.userId(authentication), houseId, targetDate).stream()
                .map(view -> new TodayChoreResponse(
                        view.choreInstanceId(),
                        view.houseId(),
                        view.title(),
                        view.spaceName(),
                        view.assigneeName(),
                        view.status(),
                        view.scheduledDate(),
                        view.completed()
                ))
                .toList();
    }

    @GetMapping("/houses/{houseId}/chores/daily-progress")
    public DailyProgressResponse getDailyProgress(
            Authentication authentication,
            @PathVariable UUID houseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        ChoreService.DailyProgressView view = choreService.getDailyProgress(CurrentAuthenticatedUser.userId(authentication), houseId, targetDate);
        return new DailyProgressResponse(view.completedCount(), view.totalCount(), view.completionRate());
    }

    @PostMapping("/chore-instances/{choreInstanceId}/completion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeChore(Authentication authentication, @PathVariable UUID choreInstanceId, @Valid @RequestBody(required = false) ToggleCompletionRequest request) {
        ToggleCompletionRequest actualRequest = request == null ? new ToggleCompletionRequest(null, null) : request;
        choreService.toggleCompletion(
                CurrentAuthenticatedUser.userId(authentication),
                choreInstanceId,
                new ChoreService.ToggleCompletionCommand(actualRequest.memo(), actualRequest.proofImageUrl())
        );
    }

    @DeleteMapping("/chore-instances/{choreInstanceId}/completion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelCompletion(Authentication authentication, @PathVariable UUID choreInstanceId) {
        choreService.cancelCompletion(CurrentAuthenticatedUser.userId(authentication), choreInstanceId);
    }

    public record CreateChoreRequest(
            @NotNull UUID spaceId,
            @NotBlank String title,
            String description,
            Integer estimatedMinutes,
            UUID defaultAssigneeMembershipId,
            @Valid @NotNull RecurrenceRequest recurrence
    ) {
    }

    public record RecurrenceRequest(
            @NotBlank String frequency,
            @NotNull Integer interval,
            List<String> daysOfWeek,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate
    ) {
    }

    public record UpdateChoreRequest(
            String title,
            String description,
            Integer estimatedMinutes
    ) {
    }

    public record ToggleCompletionRequest(
            String memo,
            String proofImageUrl
    ) {
    }

    public record ChoreRuleResponse(
            UUID choreRuleId,
            UUID houseId,
            UUID spaceId,
            String title,
            String description,
            Integer estimatedMinutes
    ) {
    }

    public record TodayChoreResponse(
            UUID choreInstanceId,
            UUID houseId,
            String title,
            String spaceName,
            String assigneeName,
            String status,
            LocalDate scheduledDate,
            boolean completed
    ) {
    }

    public record DailyProgressResponse(
            int completedCount,
            int totalCount,
            double completionRate
    ) {
    }
}

