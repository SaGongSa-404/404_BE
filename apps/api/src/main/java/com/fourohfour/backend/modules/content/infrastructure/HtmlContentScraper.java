package com.fourohfour.backend.modules.content.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fourohfour.backend.modules.content.application.ContentScraper;
import com.fourohfour.backend.modules.content.application.ScrapedContent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

@Component
public class HtmlContentScraper implements ContentScraper {

    private static final int MAX_TEXT_LENGTH = 3000;
    private static final Pattern CAPTION_TRACKS_PATTERN = Pattern.compile("\"captionTracks\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern BASE_URL_PATTERN = Pattern.compile("\"baseUrl\"\\s*:\\s*\"(.*?)\"");
    private static final Pattern SHORT_DESCRIPTION_PATTERN = Pattern.compile("\"shortDescription\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern KEYWORDS_PATTERN = Pattern.compile("\"keywords\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern QUOTED_VALUE_PATTERN = Pattern.compile("\"(.*?)\"");
    private static final Pattern INSTAGRAM_AUTHOR_PATTERN = Pattern.compile("^([^:]+?)\\s+on\\s+Instagram", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTAGRAM_DESCRIPTION_PATTERN = Pattern.compile("^[^:]{1,120}:\\s*(.*)$", Pattern.DOTALL);

    private final HttpClient httpClient;
    private final ScraperProperties scraperProperties;
    private final ObjectMapper objectMapper;

    public HtmlContentScraper(HttpClient httpClient, ScraperProperties scraperProperties, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.scraperProperties = scraperProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ScrapedContent scrape(String url) {
        String normalizedUrl = normalizeUrl(url);
        try {
            PageResponse pageResponse = fetchPage(normalizedUrl);
            Document document = Jsoup.parse(pageResponse.body(), pageResponse.resolvedUrl());
            document = maybeResolveEmbeddedFrame(document, pageResponse.resolvedUrl());

            String domain = extractDomain(pageResponse.resolvedUrl());
            String sourceType = inferSourceType(domain);
            String title = firstNonBlank(
                    attr(document, "meta[property=og:title]", "content"),
                    attr(document, "meta[name=twitter:title]", "content"),
                    extractYoutubeTitle(document),
                    document.title()
            );
            String description = firstNonBlank(
                    attr(document, "meta[property=og:description]", "content"),
                    attr(document, "meta[name=description]", "content"),
                    attr(document, "meta[name=twitter:description]", "content")
            );
            String author = firstNonBlank(
                    attr(document, "meta[name=author]", "content"),
                    attr(document, "meta[property=article:author]", "content"),
                    text(document, ".nick, .blog2_series .txt_info")
            );
            String siteName = firstNonBlank(
                    attr(document, "meta[property=og:site_name]", "content"),
                    attr(document, "meta[name=application-name]", "content")
            );
            String text = extractReadableText(document, sourceType, description);
            List<String> imageUrls = extractImageUrls(document);

            if (isYoutubeDomain(domain)) {
                title = firstNonBlank(title, fetchYoutubeOEmbedTitle(pageResponse.resolvedUrl()));
                text = mergeText(
                        extractYoutubeTranscript(pageResponse.body()),
                        extractYoutubeStructuredText(pageResponse.body()),
                        text
                );
            }
            if (isInstagramDomain(domain)) {
                InstagramMetadata instagramMetadata = extractInstagramMetadata(document, pageResponse.body());
                title = firstNonBlank(instagramMetadata.title(), title, deriveTitleFromUrl(pageResponse.resolvedUrl()));
                description = firstNonBlank(instagramMetadata.description(), description);
                author = firstNonBlank(instagramMetadata.author(), author);
                siteName = firstNonBlank(instagramMetadata.siteName(), siteName, "Instagram");
                text = mergeText(instagramMetadata.text(), description, text);
            }

            return new ScrapedContent(
                    url,
                    pageResponse.resolvedUrl(),
                    domain,
                    sourceType,
                    normalizeWhitespace(title),
                    normalizeWhitespace(description),
                    normalizeWhitespace(text),
                    normalizeWhitespace(author),
                    normalizeWhitespace(siteName),
                    imageUrls
            );
        } catch (Exception exception) {
            return new ScrapedContent(
                    url,
                    url,
                    extractDomain(url),
                    inferSourceType(extractDomain(url)),
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of()
            );
        }
    }

    private Document maybeResolveEmbeddedFrame(Document document, String resolvedUrl) throws IOException, InterruptedException {
        String domain = extractDomain(resolvedUrl);
        if (!domain.contains("naver.com")) {
            return document;
        }
        Element mainFrame = document.selectFirst("iframe#mainFrame");
        if (mainFrame == null) {
            return document;
        }
        String frameUrl = mainFrame.absUrl("src");
        if (frameUrl == null || frameUrl.isBlank()) {
            return document;
        }
        PageResponse framePage = fetchPage(frameUrl);
        return Jsoup.parse(framePage.body(), framePage.resolvedUrl());
    }

    private PageResponse fetchPage(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofMillis(scraperProperties.timeoutMillis()))
                .header("User-Agent", scraperProperties.userAgent())
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new PageResponse(response.uri().toString(), response.body());
    }

    private String fetchYoutubeOEmbedTitle(String url) {
        try {
            String encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8);
            PageResponse response = fetchPage("https://www.youtube.com/oembed?url=" + encodedUrl + "&format=json");
            JsonNode jsonNode = objectMapper.readTree(response.body());
            return jsonNode.path("title").asText(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    String extractYoutubeTranscript(String pageHtml) {
        try {
            String captionTrackUrl = findYoutubeCaptionTrackUrl(pageHtml);
            if (captionTrackUrl == null) {
                return null;
            }

            PageResponse transcriptResponse = fetchPage(captionTrackUrl);
            Document xmlDocument = Jsoup.parse(transcriptResponse.body(), "", Parser.xmlParser());
            String transcript = xmlDocument.select("text").stream()
                    .map(Element::text)
                    .map(this::normalizeWhitespace)
                    .filter(line -> line != null && !line.isBlank())
                    .reduce((left, right) -> left + " " + right)
                    .orElse(null);
            if (transcript == null) {
                return null;
            }
            if (transcript.length() > MAX_TEXT_LENGTH) {
                return transcript.substring(0, MAX_TEXT_LENGTH);
            }
            return transcript;
        } catch (Exception ignored) {
            return null;
        }
    }

    String extractYoutubeStructuredText(String pageHtml) {
        String shortDescription = extractYoutubeShortDescription(pageHtml);
        String keywords = extractYoutubeKeywords(pageHtml);
        return joinNonBlank(shortDescription, keywords);
    }

    private String findYoutubeCaptionTrackUrl(String pageHtml) {
        Matcher captionTracksMatcher = CAPTION_TRACKS_PATTERN.matcher(pageHtml);
        if (!captionTracksMatcher.find()) {
            return null;
        }
        String captionTracksBlock = captionTracksMatcher.group(1);
        Matcher baseUrlMatcher = BASE_URL_PATTERN.matcher(captionTracksBlock);
        if (!baseUrlMatcher.find()) {
            return null;
        }
        return decodeYoutubeEscapedUrl(baseUrlMatcher.group(1));
    }

    private String extractYoutubeShortDescription(String pageHtml) {
        Matcher matcher = SHORT_DESCRIPTION_PATTERN.matcher(pageHtml);
        if (!matcher.find()) {
            return null;
        }
        return decodeYoutubeEscapedText(matcher.group(1));
    }

    private String extractYoutubeKeywords(String pageHtml) {
        Matcher matcher = KEYWORDS_PATTERN.matcher(pageHtml);
        if (!matcher.find()) {
            return null;
        }

        Matcher valueMatcher = QUOTED_VALUE_PATTERN.matcher(matcher.group(1));
        StringBuilder builder = new StringBuilder();
        while (valueMatcher.find()) {
            String value = decodeYoutubeEscapedText(valueMatcher.group(1));
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(value);
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private String decodeYoutubeEscapedUrl(String value) {
        return value
                .replace("\\u0026", "&")
                .replace("\\/", "/");
    }

    private String decodeYoutubeEscapedText(String value) {
        try {
            String escaped = value
                    .replace("\\/", "/")
                    .replace("\"", "\\\"");
            return objectMapper.readValue("\"" + escaped + "\"", String.class);
        } catch (Exception exception) {
            return value;
        }
    }

    InstagramMetadata extractInstagramMetadata(Document document, String pageHtml) {
        JsonNode ldJson = extractJsonLd(document);
        String ogTitle = normalizeWhitespace(attr(document, "meta[property=og:title]", "content"));
        String ogDescription = normalizeWhitespace(attr(document, "meta[property=og:description]", "content"));
        String metaDescription = normalizeWhitespace(attr(document, "meta[name=description]", "content"));

        String author = firstNonBlank(
                textValue(ldJson, "/author/alternateName"),
                textValue(ldJson, "/author/name"),
                authorFromInstagramTitle(ogTitle),
                authorFromInstagramTitle(metaDescription)
        );

        String description = firstNonBlank(
                textValue(ldJson, "/caption"),
                textValue(ldJson, "/articleBody"),
                descriptionFromInstagramMeta(ogDescription),
                descriptionFromInstagramMeta(metaDescription),
                ogDescription,
                metaDescription
        );

        String title = firstNonBlank(
                textValue(ldJson, "/headline"),
                textValue(ldJson, "/name"),
                titleFromInstagramMeta(ogTitle, author),
                titleFromInstagramMeta(metaDescription, author)
        );

        String text = mergeText(
                description,
                normalizeWhitespace(textValue(ldJson, "/description")),
                normalizeWhitespace(textValue(ldJson, "/articleBody"))
        );

        String siteName = firstNonBlank(
                textValue(ldJson, "/publisher/name"),
                attr(document, "meta[property=og:site_name]", "content"),
                "Instagram"
        );

        return new InstagramMetadata(title, description, text, author, siteName);
    }

    private JsonNode extractJsonLd(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                JsonNode jsonNode = objectMapper.readTree(script.html());
                if (jsonNode.isArray()) {
                    for (JsonNode child : jsonNode) {
                        if (child.has("@type")) {
                            return child;
                        }
                    }
                }
                if (jsonNode.has("@type")) {
                    return jsonNode;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String textValue(JsonNode node, String pointer) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.at(pointer);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        String value = child.asText(null);
        return normalizeWhitespace(value);
    }

    private String authorFromInstagramTitle(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = INSTAGRAM_AUTHOR_PATTERN.matcher(value.trim());
        if (!matcher.find()) {
            return null;
        }
        return normalizeWhitespace(matcher.group(1));
    }

    private String descriptionFromInstagramMeta(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = INSTAGRAM_DESCRIPTION_PATTERN.matcher(value.trim());
        if (matcher.find()) {
            return normalizeWhitespace(matcher.group(1));
        }
        return normalizeWhitespace(value);
    }

    private String titleFromInstagramMeta(String value, String author) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (author != null && normalized.toLowerCase(Locale.ROOT).startsWith(author.toLowerCase(Locale.ROOT))) {
            return author + " Instagram";
        }
        if ("instagram".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalizeWhitespace(normalized);
    }

    private String extractReadableText(Document document, String sourceType, String fallbackDescription) {
        List<String> selectors = switch (sourceType) {
            case "youtube" -> List.of("meta[name=description]", "yt-formatted-string.content");
            case "instagram" -> List.of("meta[property=og:description]", "meta[name=description]");
            case "pinterest" -> List.of("meta[property=og:description]", "meta[name=description]", "article", "main");
            case "recipe" -> List.of(".view_step_cont", ".view_step", ".ready_ingre3", ".view2_summary");
            case "naver-blog" -> List.of(".se-main-container", "#postViewArea", ".post_view", ".contents_style");
            case "tistory" -> List.of(".tt_article_useless_p_margin", ".article-view", ".entry-content", "article");
            default -> List.of("article", "main", ".entry-content", ".post-content", ".content", "body");
        };

        Set<String> chunks = new LinkedHashSet<>();
        for (String selector : selectors) {
            for (Element element : document.select(selector)) {
                String candidate;
                if (selector.startsWith("meta[")) {
                    candidate = element.attr("content");
                } else {
                    candidate = element.text();
                }
                if (candidate != null && !candidate.isBlank()) {
                    chunks.add(normalizeWhitespace(candidate));
                }
            }
            if (!chunks.isEmpty()) {
                break;
            }
        }

        String combined = String.join("\n", chunks);
        String text = firstNonBlank(
                combined,
                fallbackDescription,
                attr(document, "meta[name=keywords]", "content"),
                document.body() != null ? document.body().text() : null
        );
        if (text == null) {
            return null;
        }
        text = normalizeWhitespace(text);
        if (text.length() > MAX_TEXT_LENGTH) {
            return text.substring(0, MAX_TEXT_LENGTH);
        }
        return text;
    }

    private String mergeText(String... texts) {
        String merged = joinNonBlank(texts);
        if (merged == null) {
            merged = firstNonBlank(texts);
        }
        if (merged == null) {
            return null;
        }
        String normalized = normalizeWhitespace(merged);
        if (normalized.length() > MAX_TEXT_LENGTH) {
            return normalized.substring(0, MAX_TEXT_LENGTH);
        }
        return normalized;
    }

    private String extractYoutubeTitle(Document document) {
        String value = attr(document, "meta[itemprop=name]", "content");
        if (value != null && !value.isBlank()) {
            return value;
        }
        return text(document, "title");
    }

    private String attr(Document document, String selector, String attribute) {
        Element element = document.selectFirst(selector);
        if (element == null) {
            return null;
        }
        return element.attr(attribute);
    }

    private String text(Document document, String selector) {
        Element element = document.selectFirst(selector);
        return element == null ? null : element.text();
    }

    private String inferSourceType(String domain) {
        if (domain.contains("youtube.com") || domain.contains("youtu.be")) {
            return "youtube";
        }
        if (domain.contains("instagram.com")) {
            return "instagram";
        }
        if (domain.contains("pinterest.com")) {
            return "pinterest";
        }
        if (domain.contains("10000recipe.com")) {
            return "recipe";
        }
        if (domain.contains("blog.naver.com") || domain.contains("m.blog.naver.com")) {
            return "naver-blog";
        }
        if (domain.contains("tistory.com")) {
            return "tistory";
        }
        return "generic";
    }

    private boolean isYoutubeDomain(String domain) {
        return domain.contains("youtube.com") || domain.contains("youtu.be");
    }

    private boolean isInstagramDomain(String domain) {
        return domain.contains("instagram.com");
    }

    private String deriveTitleFromUrl(String url) {
        try {
            URI uri = new URI(normalizeUrl(url));
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            String[] segments = path.split("/");
            for (int i = segments.length - 1; i >= 0; i--) {
                if (!segments[i].isBlank()) {
                    return segments[i];
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private List<String> extractImageUrls(Document document) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (String selector : List.of("meta[property=og:image]", "meta[name=twitter:image]", "img[src]")) {
            for (Element element : document.select(selector)) {
                String url = selector.startsWith("meta[") ? element.attr("content") : element.absUrl("src");
                if (url == null || url.isBlank()) {
                    continue;
                }
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    continue;
                }
                urls.add(url);
                if (urls.size() >= 2) {
                    return List.copyOf(urls);
                }
            }
        }
        return List.copyOf(urls);
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return "https://" + url;
    }

    private String extractDomain(String url) {
        try {
            URI uri = new URI(normalizeUrl(url));
            String host = uri.getHost();
            if (host == null) {
                return "direct";
            }
            return host.replace("www.", "").toLowerCase(Locale.ROOT);
        } catch (URISyntaxException exception) {
            return "direct";
        }
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String joinNonBlank(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(value);
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private record PageResponse(String resolvedUrl, String body) {
    }

    record InstagramMetadata(
            String title,
            String description,
            String text,
            String author,
            String siteName
    ) {
    }
}
