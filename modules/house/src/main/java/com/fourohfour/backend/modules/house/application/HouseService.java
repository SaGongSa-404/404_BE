package com.fourohfour.backend.modules.house.application;

import com.fourohfour.backend.modules.house.infrastructure.HouseJdbcRepository;
import com.fourohfour.backend.modules.shared.api.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseService {

    private final HouseJdbcRepository houseJdbcRepository;

    public HouseService(HouseJdbcRepository houseJdbcRepository) {
        this.houseJdbcRepository = houseJdbcRepository;
    }

    @Transactional
    public CreateHouseResult createHouse(UUID userId, CreateHouseCommand command) {
        if (houseJdbcRepository.findActiveHouseByUserId(userId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "HOUSE_ALREADY_JOINED", "이미 참여 중인 집이 있습니다.");
        }

        Instant now = Instant.now();
        UUID houseId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        houseJdbcRepository.createHouse(houseId, command.name(), now);
        houseJdbcRepository.createMembership(membershipId, houseId, userId, "OWNER", now);
        houseJdbcRepository.assignOwnerMembership(houseId, membershipId, now);

        if (command.initialCleanlinessVote() != null && !command.initialCleanlinessVote().isBlank()) {
            houseJdbcRepository.upsertCleanlinessVote(houseId, userId, command.initialCleanlinessVote(), now);
            houseJdbcRepository.updateHouseCleanlinessLevel(houseId, command.initialCleanlinessVote(), now);
        }

        InviteCodeView inviteCodeView = createInviteCode(userId, houseId);
        return new CreateHouseResult(houseId, membershipId, inviteCodeView);
    }

    @Transactional
    public JoinHouseResult joinHouseByInviteCode(UUID userId, String inviteCode) {
        if (houseJdbcRepository.findActiveHouseByUserId(userId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "HOUSE_ALREADY_JOINED", "이미 참여 중인 집이 있습니다.");
        }

        HouseJdbcRepository.InviteCodeRecord inviteCodeRecord = houseJdbcRepository.findActiveInviteCodeByCode(inviteCode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HOUSE_INVITE_INVALID", "유효한 초대코드가 아닙니다."));

        Instant now = Instant.now();
        UUID membershipId = UUID.randomUUID();
        houseJdbcRepository.createMembership(membershipId, inviteCodeRecord.houseId(), userId, "MEMBER", now);

        ActiveHouseView activeHouseView = getHouseSummary(inviteCodeRecord.houseId());
        return new JoinHouseResult(activeHouseView, membershipId);
    }

    @Transactional(readOnly = true)
    public Optional<ActiveHouseView> getActiveHouse(UUID userId) {
        return houseJdbcRepository.findActiveHouseByUserId(userId)
                .map(record -> new ActiveHouseView(record.houseId(), record.name(), record.cleanlinessLevel(), record.memberCount()));
    }

    @Transactional(readOnly = true)
    public ActiveHouseView getHouseSummary(UUID houseId) {
        return houseJdbcRepository.findHouseSummaryByHouseId(houseId)
                .map(record -> new ActiveHouseView(record.houseId(), record.name(), record.cleanlinessLevel(), record.memberCount()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HOUSE_NOT_FOUND", "집 정보를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public MembershipView assertActiveMember(UUID userId, UUID houseId) {
        HouseJdbcRepository.MembershipRecord membershipRecord = houseJdbcRepository.findActiveMembership(houseId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "HOUSE_PERMISSION_DENIED", "집 구성원만 접근할 수 있습니다."));
        return new MembershipView(membershipRecord.membershipId(), membershipRecord.houseId(), membershipRecord.userId(), membershipRecord.role());
    }

    @Transactional(readOnly = true)
    public MembershipView assertOwner(UUID userId, UUID houseId) {
        MembershipView membershipView = assertActiveMember(userId, houseId);
        if (!"OWNER".equals(membershipView.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "HOUSE_PERMISSION_DENIED", "방장만 수행할 수 있습니다.");
        }
        return membershipView;
    }

    @Transactional
    public InviteCodeView createInviteCode(UUID userId, UUID houseId) {
        assertActiveMember(userId, houseId);
        Instant now = Instant.now();
        String inviteCode = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        houseJdbcRepository.createInviteCode(UUID.randomUUID(), houseId, inviteCode, userId, now);
        return new InviteCodeView(inviteCode, null);
    }

    @Transactional(readOnly = true)
    public Optional<InviteCodeView> getLatestInviteCode(UUID userId, UUID houseId) {
        assertActiveMember(userId, houseId);
        return houseJdbcRepository.findLatestInviteCodeByHouseId(houseId)
                .map(record -> new InviteCodeView(record.code(), record.expiresAt()));
    }

    @Transactional
    public void voteCleanliness(UUID userId, UUID houseId, String voteLevel) {
        assertActiveMember(userId, houseId);
        Instant now = Instant.now();
        houseJdbcRepository.upsertCleanlinessVote(houseId, userId, voteLevel, now);
        houseJdbcRepository.updateHouseCleanlinessLevel(houseId, voteLevel, now);
    }

    @Transactional(readOnly = true)
    public List<CleanlinessSummaryItem> getCleanlinessSummary(UUID userId, UUID houseId) {
        assertActiveMember(userId, houseId);
        return houseJdbcRepository.listCleanlinessSummary(houseId).stream()
                .map(record -> new CleanlinessSummaryItem(record.voteLevel(), record.count()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<String> getMyCleanlinessVote(UUID userId, UUID houseId) {
        assertActiveMember(userId, houseId);
        return houseJdbcRepository.findMyCleanlinessVote(houseId, userId);
    }

    @Transactional
    public void leaveHouse(UUID userId, UUID houseId) {
        MembershipView membershipView = assertActiveMember(userId, houseId);
        if ("OWNER".equals(membershipView.role())) {
            throw new ApiException(HttpStatus.CONFLICT, "HOUSE_OWNER_CANNOT_LEAVE", "방장은 위임 후 나갈 수 있습니다.");
        }
        houseJdbcRepository.markMembershipLeft(membershipView.membershipId(), Instant.now());
    }

    @Transactional
    public void transferOwner(UUID userId, UUID houseId, UUID targetMembershipId) {
        MembershipView ownerMembership = assertOwner(userId, houseId);
        HouseJdbcRepository.MembershipRecord targetMembership = houseJdbcRepository.findActiveMembershipById(targetMembershipId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HOUSE_MEMBER_NOT_FOUND", "대상 구성원을 찾을 수 없습니다."));

        if (!targetMembership.houseId().equals(houseId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HOUSE_MEMBER_INVALID", "다른 집의 구성원에게 위임할 수 없습니다.");
        }

        Instant now = Instant.now();
        houseJdbcRepository.updateMembershipRole(ownerMembership.membershipId(), "MEMBER", now);
        houseJdbcRepository.updateMembershipRole(targetMembershipId, "OWNER", now);
        houseJdbcRepository.assignOwnerMembership(houseId, targetMembershipId, now);
    }

    public record CreateHouseCommand(
            String name,
            String initialCleanlinessVote
    ) {
    }

    public record CreateHouseResult(
            UUID houseId,
            UUID membershipId,
            InviteCodeView inviteCode
    ) {
    }

    public record JoinHouseResult(
            ActiveHouseView house,
            UUID membershipId
    ) {
    }

    public record ActiveHouseView(
            UUID houseId,
            String name,
            String cleanlinessLevel,
            int memberCount
    ) {
    }

    public record MembershipView(
            UUID membershipId,
            UUID houseId,
            UUID userId,
            String role
    ) {
    }

    public record InviteCodeView(
            String code,
            Instant expiresAt
    ) {
    }

    public record CleanlinessSummaryItem(
            String voteLevel,
            long count
    ) {
    }
}
