package com.sagongsa.backend.dev;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QaFeedScenarioResponse(
	UUID userId,
	String nickname,
	UUID peerUserId,
	String peerNickname,
	List<UUID> postIds,
	UUID myPostId,
	UUID peerPostId,
	List<UUID> commentIds,
	List<UUID> voteIds,
	Map<String, String> paths
) {
}
