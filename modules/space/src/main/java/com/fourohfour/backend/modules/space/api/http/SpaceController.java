package com.fourohfour.backend.modules.space.api.http;

import com.fourohfour.backend.modules.auth.domain.CurrentAuthenticatedUser;
import com.fourohfour.backend.modules.space.application.SpaceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SpaceController {

    private final SpaceService spaceService;

    public SpaceController(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    @PostMapping("/houses/{houseId}/spaces")
    @ResponseStatus(HttpStatus.CREATED)
    public SpaceResponse addSpace(Authentication authentication, @PathVariable UUID houseId, @Valid @RequestBody AddSpaceRequest request) {
        SpaceService.SpaceView view = spaceService.addSpace(
                CurrentAuthenticatedUser.userId(authentication),
                houseId,
                new SpaceService.AddSpaceCommand(request.name(), request.sortOrder())
        );
        return new SpaceResponse(view.spaceId(), view.houseId(), view.name(), view.sortOrder());
    }

    @GetMapping("/houses/{houseId}/spaces")
    public List<SpaceResponse> listSpaces(Authentication authentication, @PathVariable UUID houseId) {
        return spaceService.listSpaces(CurrentAuthenticatedUser.userId(authentication), houseId).stream()
                .map(view -> new SpaceResponse(view.spaceId(), view.houseId(), view.name(), view.sortOrder()))
                .toList();
    }

    @PatchMapping("/spaces/{spaceId}")
    public SpaceResponse renameSpace(Authentication authentication, @PathVariable UUID spaceId, @Valid @RequestBody RenameSpaceRequest request) {
        SpaceService.SpaceView view = spaceService.renameSpace(
                CurrentAuthenticatedUser.userId(authentication),
                spaceId,
                request.name()
        );
        return new SpaceResponse(view.spaceId(), view.houseId(), view.name(), view.sortOrder());
    }

    @GetMapping("/houses/{houseId}/spaces/chore-counts")
    public List<SpaceChoreCountResponse> listSpaceChoreCounts(Authentication authentication, @PathVariable UUID houseId) {
        return spaceService.listSpaceChoreCounts(CurrentAuthenticatedUser.userId(authentication), houseId).stream()
                .map(view -> new SpaceChoreCountResponse(view.spaceId(), view.name(), view.choreCount()))
                .toList();
    }

    public record AddSpaceRequest(
            @NotBlank String name,
            Integer sortOrder
    ) {
    }

    public record RenameSpaceRequest(@NotBlank String name) {
    }

    public record SpaceResponse(
            UUID spaceId,
            UUID houseId,
            String name,
            int sortOrder
    ) {
    }

    public record SpaceChoreCountResponse(
            UUID spaceId,
            String name,
            long choreCount
    ) {
    }
}

