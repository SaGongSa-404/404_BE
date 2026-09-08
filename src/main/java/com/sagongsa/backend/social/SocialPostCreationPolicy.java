package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.item.SavedItem;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class SocialPostCreationPolicy {
	private static final int MAX_IMAGE_URL_LENGTH = 2048;
	private static final Pattern INTERNAL_UPLOAD_IMAGE_PATH = Pattern.compile(
		"^/uploads/social/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(?:jpg|png|gif)$"
	);

	long postDuplicateLockKey(UUID userId, CreatePostRequest request, SavedItem item, String imageUrl) {
		return lockKey(
			"social-post-create",
			userId,
			item == null ? null : item.getId(),
			request.title(),
			request.body(),
			imageUrl,
			request.price()
		);
	}

	private long lockKey(String namespace, Object... values) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			updateDigest(digest, namespace);
			for (Object value : values) {
				updateDigest(digest, value == null ? null : value.toString());
			}
			return ByteBuffer.wrap(digest.digest()).getLong();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 digest is not available", exception);
		}
	}

	private void updateDigest(MessageDigest digest, String value) {
		byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
		digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
		digest.update(bytes);
	}

	String resolveImageUrl(String requestImageUrl, SavedItem item) {
		String imageUrl = StringUtils.hasText(requestImageUrl)
			? requestImageUrl
			: item != null ? item.getImageUrl() : null;
		if (!StringUtils.hasText(imageUrl)) {
			return null;
		}

		String normalized = imageUrl.trim();
		if (normalized.length() > MAX_IMAGE_URL_LENGTH) {
			throw new SocialFeedBadRequestException("이미지 URL은 최대 2048자까지 가능합니다.");
		}
		if (INTERNAL_UPLOAD_IMAGE_PATH.matcher(normalized).matches()) {
			return normalized;
		}

		try {
			URI uri = URI.create(normalized);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			if (!("http".equals(scheme) || "https".equals(scheme))
				|| !StringUtils.hasText(uri.getHost())
				|| StringUtils.hasText(uri.getUserInfo())) {
				throw new SocialFeedBadRequestException("이미지 URL은 유효한 http/https 주소여야 합니다.");
			}
			return normalized;
		} catch (IllegalArgumentException exception) {
			throw new SocialFeedBadRequestException("이미지 URL은 유효한 http/https 주소여야 합니다.");
		}
	}
}
