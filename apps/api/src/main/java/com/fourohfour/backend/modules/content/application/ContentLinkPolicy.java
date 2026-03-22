package com.fourohfour.backend.modules.content.application;

import com.fourohfour.backend.modules.shared.api.ApiException;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ContentLinkPolicy {

    public void validate(String rawUrl) {
        String normalized = normalizeUrl(rawUrl);
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LINK", "처리할 수 없는 링크 형식이에요.");
        }

        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);

        if (host.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LINK", "처리할 수 없는 링크 형식이에요.");
        }
        if (host.contains("heepsy.com") || host.contains("linktr.ee") || host.contains("huntertuber.com")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_LINK",
                    "프로필/링크모음/통계 페이지 말고 개별 콘텐츠 링크를 넣어주세요.");
        }
        if (host.contains("24vids.com") && path.startsWith("/channel")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_LINK",
                    "채널 페이지 말고 개별 영상 링크를 넣어주세요.");
        }
        if (isYouTubeProfileOrChannel(host, path)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_LINK",
                    "유튜브 채널/목록 말고 개별 영상 링크를 넣어주세요.");
        }
        if (isInstagramProfile(host, path)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_LINK",
                    "인스타 프로필 말고 개별 게시물이나 릴 링크를 넣어주세요.");
        }
        if (host.contains("tistory.com") && (path.isBlank() || "/".equals(path))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_LINK",
                    "티스토리 홈 말고 개별 글 링크를 넣어주세요.");
        }
    }

    private boolean isYouTubeProfileOrChannel(String host, String path) {
        if (!host.contains("youtube.com")) {
            return false;
        }
        return path.startsWith("/@")
                || path.startsWith("/channel/")
                || path.startsWith("/c/")
                || path.startsWith("/user/")
                || path.startsWith("/playlist");
    }

    private boolean isInstagramProfile(String host, String path) {
        if (!host.contains("instagram.com")) {
            return false;
        }
        if (path.startsWith("/p/") || path.startsWith("/reel/") || path.startsWith("/tv/")) {
            return false;
        }
        String[] parts = path.split("/");
        int nonBlank = 0;
        for (String part : parts) {
            if (!part.isBlank()) {
                nonBlank++;
            }
        }
        return nonBlank <= 1;
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url.trim();
        }
        return "https://" + url.trim();
    }
}
