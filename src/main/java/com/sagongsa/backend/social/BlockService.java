package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.social.UserBlock;
import com.sagongsa.backend.domain.social.UserBlockRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserAccountRepository userAccountRepository;

    public BlockService(UserBlockRepository userBlockRepository,
        UserAccountRepository userAccountRepository) {
        this.userBlockRepository = userBlockRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public void blockUser(UUID blockerId, UUID targetUserId) {
        if (blockerId.equals(targetUserId)) {
            throw new SocialFeedForbiddenException("자기 자신을 차단할 수 없습니다.");
        }
        if (userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, targetUserId)) {
            throw new SocialFeedForbiddenException("이미 차단한 사용자입니다.");
        }
        UserAccount blocker = findUserOrThrow(blockerId);
        UserAccount blocked = findUserOrThrow(targetUserId);
        userBlockRepository.save(new UserBlock(blocker, blocked));
    }

    @Transactional
    public void unblockUser(UUID blockerId, UUID targetUserId) {
        UserBlock block = userBlockRepository.findByBlockerIdAndBlockedId(blockerId, targetUserId)
            .orElseThrow(() -> new SocialFeedNotFoundException("차단하지 않은 사용자입니다."));
        userBlockRepository.delete(block);
    }

    public List<UUID> getBlockedUserIds(UUID userId) {
        return userBlockRepository.findBlockedUserIdsByBlockerId(userId);
    }

    private UserAccount findUserOrThrow(UUID userId) {
        return userAccountRepository.findById(userId)
            .orElseThrow(() -> new SocialFeedNotFoundException("사용자를 찾을 수 없습니다."));
    }
}
