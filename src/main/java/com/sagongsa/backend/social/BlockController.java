package com.sagongsa.backend.social;

import com.sagongsa.backend.auth.CurrentUserId;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
class BlockController {

    private final BlockService blockService;

    BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @PostMapping("/{targetUserId}/block")
    public ResponseEntity<Void> blockUser(
        @CurrentUserId UUID userId,
        @PathVariable UUID targetUserId) {
        blockService.blockUser(userId, targetUserId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{targetUserId}/block")
    public ResponseEntity<Void> unblockUser(
        @CurrentUserId UUID userId,
        @PathVariable UUID targetUserId) {
        blockService.unblockUser(userId, targetUserId);
        return ResponseEntity.noContent().build();
    }
}
