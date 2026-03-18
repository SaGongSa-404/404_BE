package com.fourohfour.backend.modules.space.application;

import com.fourohfour.backend.modules.house.application.HouseService;
import com.fourohfour.backend.modules.shared.api.ApiException;
import com.fourohfour.backend.modules.space.infrastructure.SpaceJdbcRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpaceService {

    private final SpaceJdbcRepository spaceJdbcRepository;
    private final HouseService houseService;

    public SpaceService(SpaceJdbcRepository spaceJdbcRepository, HouseService houseService) {
        this.spaceJdbcRepository = spaceJdbcRepository;
        this.houseService = houseService;
    }

    @Transactional
    public SpaceView addSpace(UUID userId, UUID houseId, AddSpaceCommand command) {
        houseService.assertActiveMember(userId, houseId);
        int sortOrder = command.sortOrder() == null ? spaceJdbcRepository.nextSortOrder(houseId) : command.sortOrder();
        UUID spaceId = UUID.randomUUID();
        spaceJdbcRepository.createSpace(spaceId, houseId, command.name(), sortOrder, Instant.now());
        return new SpaceView(spaceId, houseId, command.name(), sortOrder);
    }

    @Transactional(readOnly = true)
    public List<SpaceView> listSpaces(UUID userId, UUID houseId) {
        houseService.assertActiveMember(userId, houseId);
        return spaceJdbcRepository.listSpaces(houseId).stream()
                .map(space -> new SpaceView(space.spaceId(), space.houseId(), space.name(), space.sortOrder()))
                .toList();
    }

    @Transactional
    public SpaceView renameSpace(UUID userId, UUID spaceId, String name) {
        SpaceJdbcRepository.SpaceRecord spaceRecord = spaceJdbcRepository.findById(spaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "공간을 찾을 수 없습니다."));
        houseService.assertActiveMember(userId, spaceRecord.houseId());
        spaceJdbcRepository.renameSpace(spaceId, name, Instant.now());
        return new SpaceView(spaceRecord.spaceId(), spaceRecord.houseId(), name, spaceRecord.sortOrder());
    }

    @Transactional(readOnly = true)
    public List<SpaceChoreCountView> listSpaceChoreCounts(UUID userId, UUID houseId) {
        houseService.assertActiveMember(userId, houseId);
        return spaceJdbcRepository.listSpaceChoreCounts(houseId).stream()
                .map(record -> new SpaceChoreCountView(record.spaceId(), record.name(), record.choreCount()))
                .toList();
    }

    public record AddSpaceCommand(
            String name,
            Integer sortOrder
    ) {
    }

    public record SpaceView(
            UUID spaceId,
            UUID houseId,
            String name,
            int sortOrder
    ) {
    }

    public record SpaceChoreCountView(
            UUID spaceId,
            String name,
            long choreCount
    ) {
    }
}

