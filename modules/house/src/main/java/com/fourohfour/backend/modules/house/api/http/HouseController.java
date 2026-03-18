package com.fourohfour.backend.modules.house.api.http;

import com.fourohfour.backend.modules.auth.domain.CurrentAuthenticatedUser;
import com.fourohfour.backend.modules.house.application.HouseService;
import com.fourohfour.backend.modules.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/houses")
public class HouseController {

    private final HouseService houseService;

    public HouseController(HouseService houseService) {
        this.houseService = houseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateHouseResponse createHouse(Authentication authentication, @Valid @RequestBody CreateHouseRequest request) {
        HouseService.CreateHouseResult result = houseService.createHouse(
                CurrentAuthenticatedUser.userId(authentication),
                new HouseService.CreateHouseCommand(request.name(), request.initialCleanlinessVote())
        );

        return new CreateHouseResponse(
                result.houseId(),
                result.membershipId(),
                new InviteCodeResponse(result.inviteCode().code(), result.inviteCode().expiresAt())
        );
    }

    @PostMapping("/join")
    public JoinHouseResponse joinHouse(Authentication authentication, @Valid @RequestBody JoinHouseRequest request) {
        HouseService.JoinHouseResult result = houseService.joinHouseByInviteCode(
                CurrentAuthenticatedUser.userId(authentication),
                request.inviteCode()
        );
        return new JoinHouseResponse(
                new HouseSummaryResponse(
                        result.house().houseId(),
                        result.house().name(),
                        result.house().cleanlinessLevel(),
                        result.house().memberCount()
                ),
                result.membershipId()
        );
    }

    @GetMapping("/me")
    public HouseSummaryResponse getMyHouse(Authentication authentication) {
        HouseService.ActiveHouseView house = houseService.getActiveHouse(CurrentAuthenticatedUser.userId(authentication))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HOUSE_NOT_FOUND", "현재 참여 중인 집이 없습니다."));
        return new HouseSummaryResponse(house.houseId(), house.name(), house.cleanlinessLevel(), house.memberCount());
    }

    @PostMapping("/{houseId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveHouse(Authentication authentication, @PathVariable UUID houseId) {
        houseService.leaveHouse(CurrentAuthenticatedUser.userId(authentication), houseId);
    }

    @PostMapping("/{houseId}/owner/transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transferOwner(Authentication authentication, @PathVariable UUID houseId, @Valid @RequestBody TransferOwnerRequest request) {
        houseService.transferOwner(CurrentAuthenticatedUser.userId(authentication), houseId, request.targetMembershipId());
    }

    @PostMapping("/{houseId}/cleanliness-votes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void voteCleanliness(Authentication authentication, @PathVariable UUID houseId, @Valid @RequestBody VoteCleanlinessRequest request) {
        houseService.voteCleanliness(CurrentAuthenticatedUser.userId(authentication), houseId, request.voteLevel());
    }

    @GetMapping("/{houseId}/cleanliness-summary")
    public List<CleanlinessSummaryResponse> getCleanlinessSummary(Authentication authentication, @PathVariable UUID houseId) {
        return houseService.getCleanlinessSummary(CurrentAuthenticatedUser.userId(authentication), houseId).stream()
                .map(item -> new CleanlinessSummaryResponse(item.voteLevel(), item.count()))
                .toList();
    }

    @PostMapping("/{houseId}/invite-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteCodeResponse createInviteCode(Authentication authentication, @PathVariable UUID houseId) {
        HouseService.InviteCodeView inviteCodeView = houseService.createInviteCode(CurrentAuthenticatedUser.userId(authentication), houseId);
        return new InviteCodeResponse(inviteCodeView.code(), inviteCodeView.expiresAt());
    }

    public record CreateHouseRequest(
            @NotBlank String name,
            String initialCleanlinessVote
    ) {
    }

    public record JoinHouseRequest(@NotBlank String inviteCode) {
    }

    public record TransferOwnerRequest(@NotNull UUID targetMembershipId) {
    }

    public record VoteCleanlinessRequest(@NotBlank String voteLevel) {
    }

    public record CreateHouseResponse(
            UUID houseId,
            UUID membershipId,
            InviteCodeResponse inviteCode
    ) {
    }

    public record JoinHouseResponse(
            HouseSummaryResponse house,
            UUID membershipId
    ) {
    }

    public record HouseSummaryResponse(
            UUID houseId,
            String name,
            String cleanlinessLevel,
            int memberCount
    ) {
    }

    public record InviteCodeResponse(
            String code,
            java.time.Instant expiresAt
    ) {
    }

    public record CleanlinessSummaryResponse(
            String voteLevel,
            long count
    ) {
    }
}
