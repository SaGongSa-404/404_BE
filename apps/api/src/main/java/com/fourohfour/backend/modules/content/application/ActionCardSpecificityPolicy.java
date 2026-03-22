package com.fourohfour.backend.modules.content.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ActionCardSpecificityPolicy {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHangul}\\p{IsAlphabetic}\\d]+");

    private static final Set<String> STOPWORDS = Set.of(
            "오늘", "영상", "모음", "강사", "정리", "방법", "진짜", "바로", "시작", "하기",
            "how", "best", "video", "youtube", "shorts", "zip", "the", "and"
    );

    private static final List<String> GENERIC_PATTERNS = List.of(
            "우선순위 3가지",
            "목표 3가지",
            "할 일 3가지",
            "오늘의 목표 3가지",
            "오늘의 우선순위 3가지"
    );

    private static final List<String> ACTION_MARKERS = List.of(
            "해보기", "해보자", "정하기", "확인하기", "써보기", "적기", "기록하기",
            "만들기", "가보기", "방문", "설치하기", "설치해보기", "공유하기",
            "보내기", "정리하기", "실행하기", "켜보기", "준비하기", "돌아보기"
    );

    public boolean isSpecificEnough(ActionCardGenerationSource source, GeneratedPracticeCard generatedPracticeCard) {
        String actionTitle = normalize(safe(generatedPracticeCard.actionTitle()));
        String actionCorpus = normalize(String.join(" ",
                safe(generatedPracticeCard.actionTitle()),
                safe(generatedPracticeCard.actionDetail()),
                safe(generatedPracticeCard.rationale())
        ));
        String sourceCorpus = normalize(String.join(" ",
                safe(source.requestedTitle()),
                safe(source.requestedNote()),
                safe(source.scrapedTitle()),
                safe(source.scrapedDescription()),
                safe(source.scrapedText()),
                safe(String.join(" ", source.tags() == null ? List.of() : source.tags()))
        ));

        if (looksLikeGenericGoalCard(actionCorpus, sourceCorpus)) {
            return false;
        }
        if (!looksLikeActionTitle(actionTitle, source)) {
            return false;
        }
        if (mentionsFitness(sourceCorpus) && !"FITNESS".equals(generatedPracticeCard.category().name())) {
            return false;
        }
        if (mentionsRoutine(sourceCorpus) && !Set.of("ROUTINE", "GENERAL").contains(generatedPracticeCard.category().name())) {
            return false;
        }
        if (mentionsCleaning(sourceCorpus) && !Set.of("CLEANING", "GENERAL", "ROUTINE").contains(generatedPracticeCard.category().name())) {
            return false;
        }
        if (mentionsCooking(sourceCorpus) && !"COOKING".equals(generatedPracticeCard.category().name())) {
            return false;
        }
        if (mentionsEvent(sourceCorpus) && !"EVENT".equals(generatedPracticeCard.category().name())) {
            return false;
        }
        if (mentionsFinance(sourceCorpus) && !"FINANCE".equals(generatedPracticeCard.category().name())) {
            return false;
        }
        if (mentionsTooling(sourceCorpus) && !Set.of("LEARNING", "PRODUCTIVITY").contains(generatedPracticeCard.category().name())) {
            return false;
        }
        if (mentionsJournaling(sourceCorpus) && !mentionsJournaling(actionCorpus)) {
            return false;
        }
        if (mentionsCampusOnboarding(sourceCorpus) && !mentionsCampusOnboarding(actionCorpus)) {
            return false;
        }

        Set<String> sourceKeywords = extractKeywords(sourceCorpus);
        if (sourceKeywords.isEmpty()) {
            return true;
        }

        for (String keyword : sourceKeywords) {
            if (keyword.length() >= 2 && actionCorpus.contains(keyword)) {
                return true;
            }
        }

        return !sourceKeywords.stream().anyMatch(keyword -> keyword.length() >= 3);
    }

    private boolean looksLikeGenericGoalCard(String actionCorpus, String sourceCorpus) {
        boolean containsGenericPattern = GENERIC_PATTERNS.stream().anyMatch(actionCorpus::contains);
        if (!containsGenericPattern) {
            return false;
        }
        return !sourceCorpus.contains("우선순위")
                && !sourceCorpus.contains("목표")
                && !sourceCorpus.contains("할일")
                && !sourceCorpus.contains("할 일");
    }

    private boolean mentionsFitness(String sourceCorpus) {
        return sourceCorpus.contains("운동")
                || sourceCorpus.contains("헬스")
                || sourceCorpus.contains("러닝")
                || sourceCorpus.contains("자극")
                || sourceCorpus.contains("스트레칭");
    }

    private boolean mentionsRoutine(String sourceCorpus) {
        return sourceCorpus.contains("습관")
                || sourceCorpus.contains("루틴")
                || sourceCorpus.contains("반복");
    }

    private boolean mentionsCleaning(String sourceCorpus) {
        return sourceCorpus.contains("청소")
                || sourceCorpus.contains("변기")
                || sourceCorpus.contains("욕실")
                || sourceCorpus.contains("세제")
                || sourceCorpus.contains("과탄산소다");
    }

    private boolean mentionsCooking(String sourceCorpus) {
        return sourceCorpus.contains("레시피")
                || sourceCorpus.contains("마파두부")
                || sourceCorpus.contains("두부")
                || sourceCorpus.contains("요리")
                || sourceCorpus.contains("덮밥")
                || sourceCorpus.contains("조리");
    }

    private boolean mentionsEvent(String sourceCorpus) {
        return sourceCorpus.contains("팝업")
                || sourceCorpus.contains("팝업스토어")
                || sourceCorpus.contains("전시")
                || sourceCorpus.contains("행사")
                || sourceCorpus.contains("부산")
                || sourceCorpus.contains("일정");
    }

    private boolean mentionsFinance(String sourceCorpus) {
        return sourceCorpus.contains("재테크")
                || sourceCorpus.contains("소비")
                || sourceCorpus.contains("저축")
                || sourceCorpus.contains("가계부")
                || sourceCorpus.contains("지출")
                || sourceCorpus.contains("프롬프트")
                || sourceCorpus.contains("카테고리 투표");
    }

    private boolean mentionsTooling(String sourceCorpus) {
        return sourceCorpus.contains("피그마")
                || sourceCorpus.contains("캡컷")
                || sourceCorpus.contains("vllo")
                || sourceCorpus.contains("툴")
                || sourceCorpus.contains("포트폴리오")
                || sourceCorpus.contains("대학생 필수 툴");
    }

    private boolean mentionsJournaling(String corpus) {
        return corpus.contains("모닝페이지")
                || corpus.contains("morning page")
                || corpus.contains("저널")
                || corpus.contains("journaling")
                || corpus.contains("글쓰기")
                || corpus.contains("기록")
                || corpus.contains("적기");
    }

    private boolean mentionsCampusOnboarding(String corpus) {
        return corpus.contains("새내기")
                || corpus.contains("신입생")
                || corpus.contains("24학번")
                || corpus.contains("대학생활")
                || corpus.contains("캠퍼스")
                || corpus.contains("학사공지")
                || corpus.contains("학사 공지")
                || corpus.contains("에브리타임")
                || corpus.contains("캠퍼스픽")
                || corpus.contains("장학재단")
                || corpus.contains("강의실")
                || corpus.contains("과사");
    }

    private Set<String> extractKeywords(String text) {
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        Set<String> keywords = new LinkedHashSet<>();
        while (matcher.find()) {
            String token = matcher.group().toLowerCase(Locale.ROOT);
            if (token.length() < 2) {
                continue;
            }
            if (STOPWORDS.contains(token)) {
                continue;
            }
            keywords.add(token);
            if (keywords.size() >= 12) {
                break;
            }
        }
        return keywords;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private boolean looksLikeActionTitle(String actionTitle, ActionCardGenerationSource source) {
        if (actionTitle.isBlank()) {
            return false;
        }
        if (ACTION_MARKERS.stream().anyMatch(actionTitle::contains)) {
            return true;
        }

        String effectiveTitle = normalize(source.effectiveTitle());
        if (!effectiveTitle.isBlank() && (effectiveTitle.contains(actionTitle) || actionTitle.contains(effectiveTitle))) {
            return false;
        }

        return actionTitle.contains("오늘 ")
                || actionTitle.contains("이번 주 ")
                || actionTitle.contains("전까지 ")
                || actionTitle.contains("1회 ")
                || actionTitle.contains("1개 ");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
