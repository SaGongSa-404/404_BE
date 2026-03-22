package com.fourohfour.backend.modules.content.application;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicActionCardGeneratorTest {

    private final HeuristicActionCardGenerator generator = new HeuristicActionCardGenerator();

    @Test
    void createsToolFocusedCardAndPreservesOtherToolIdeas() {
        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://www.youtube.com/shorts/example",
                        "youtube.com",
                        null,
                        null,
                        List.of(),
                        "대학생 필수 툴 추천",
                        "피그마, 캡컷, VLLO 추천",
                        "전공 상관없이 배워두면 좋은 툴로 피그마, 캡컷, VLLO를 소개한다.",
                        "youtube",
                        null,
                        "YouTube",
                        List.of()
                ),
                LocalDate.of(2026, 3, 21)
        );

        assertThat(card.actionTitle()).contains("피그마");
        assertThat(card.ideaOptions()).anyMatch(item -> item.contains("캡컷"));
        assertThat(card.ideaOptions()).anyMatch(item -> item.contains("VLLO"));
    }

    @Test
    void createsCleaningCardFromOrderedCleaningSteps() {
        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://www.instagram.com/reel/example",
                        "instagram.com",
                        null,
                        null,
                        List.of(),
                        "변기 청소 루틴",
                        "1년에 1번만 하세요 변기 청소 루틴",
                        "1️⃣ 고무장갑과 마스크를 쓰고 환풍기를 켜줘요 2️⃣ 과탄산소다와 주방세제를 뜨거운 물과 섞어줘요 3️⃣ 변기 하단 밸브를 잠그고 세제를 부어줘요",
                        "instagram",
                        "리빙츄",
                        "Instagram",
                        List.of()
                ),
                LocalDate.of(2026, 3, 21)
        );

        assertThat(card.category().name()).isEqualTo("CLEANING");
        assertThat(card.actionTitle()).contains("변기");
        assertThat(card.detailBody()).contains("1단계");
        assertThat(card.ideaOptions().getFirst()).contains("고무장갑");
    }

    @Test
    void createsMorningPagesCardForReflectiveInstagramReel() {
        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://www.instagram.com/reel/example",
                        "instagram.com",
                        null,
                        null,
                        List.of(),
                        "Instagram의 시머 에세이툰",
                        "(효과보장 변화보장) 내가 진짜 원하는걸 아는 방법 알려드림. 그 방법은 모닝페이지!",
                        "지난 5년간 썼는데 복잡하고 불안한 머릿속을 정리시켜줬고, 내가 진짜 뭘 원하는지 언어로 정리하고 행동으로 옮길 수 있게 해준 팁을 소개한다.",
                        "instagram",
                        "시머 에세이툰",
                        "Instagram",
                        List.of()
                ),
                LocalDate.of(2026, 3, 21)
        );

        assertThat(card.category().name()).isEqualTo("ROUTINE");
        assertThat(card.actionTitle()).contains("모닝페이지");
        assertThat(card.actionDetail()).contains("원하는 것");
        assertThat(card.ideaOptions()).anyMatch(item -> item.contains("불안"));
    }

    @Test
    void createsCampusOnboardingCardForFreshmanStudentTipsPost() {
        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://blog.naver.com/example",
                        "blog.naver.com",
                        null,
                        null,
                        List.of(),
                        "24학번 새내기 신입생에게만 알려주는 대학 생활 꿀팁 공유",
                        "새내기에게 필요한 대학 생활 꿀팁",
                        "24학번 새내기를 위한 대학생활 꿀팁을 소개한다. 캠퍼스 파악, 학사 공지사항 체크, 교내 프로그램, 학점과 스펙 관리, 에브리타임과 한국장학재단 같은 앱 활용법을 안내한다.",
                        "naver-blog",
                        "경기과학기술대학교",
                        "네이버 블로그",
                        List.of()
                ),
                LocalDate.of(2026, 3, 21)
        );

        assertThat(card.category().name()).isEqualTo("ROUTINE");
        assertThat(card.actionTitle()).contains("학사 공지사항");
        assertThat(card.actionDetail()).contains("공지사항");
        assertThat(card.ideaOptions()).anyMatch(item -> item.contains("앱"));
    }

    @Test
    void createsLanguageLearningCardForEnglishExpressionPost() {
        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://blog.naver.com/example",
                        "blog.naver.com",
                        null,
                        null,
                        List.of(),
                        "미드영어표현 75 I'm all set 난 준비 끝났어",
                        "영어 표현 설명",
                        "영어 표현과 예문을 소개하며 실제로 말해보는 연습을 돕는다.",
                        "naver-blog",
                        "AnkiKorea",
                        "네이버 블로그",
                        List.of()
                ),
                LocalDate.of(2026, 3, 21)
        );

        assertThat(card.category().name()).isEqualTo("LEARNING");
        assertThat(card.actionTitle()).contains("표현");
        assertThat(card.actionDetail()).contains("예문");
    }

    @Test
    void createsFestivalCardForCherryBlossomFestivalInfo() {
        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://blog.naver.com/example2",
                        "blog.naver.com",
                        null,
                        null,
                        List.of(),
                        "속초 벚꽃 개화시기 언제? 2025 영랑호 벚꽃 축제 정보",
                        "벚꽃 축제 일정과 개화 시기 안내",
                        "벚꽃 개화시기와 축제 정보, 방문 포인트를 정리한다.",
                        "naver-blog",
                        "bella",
                        "네이버 블로그",
                        List.of()
                ),
                LocalDate.of(2026, 3, 21)
        );

        assertThat(card.category().name()).isEqualTo("EVENT");
        assertThat(card.actionTitle()).contains("축제");
    }

    @Test
    void createsFoodDiscoveryCardForRestaurantRecommendationPost() {
        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://blog.naver.com/example3",
                        "blog.naver.com",
                        null,
                        null,
                        List.of(),
                        "광주 콩물국수 맛집 TOP3 추천",
                        "광주 맛집 추천",
                        "콩물국수 맛집 세 곳과 대표 메뉴, 방문 팁을 소개한다.",
                        "naver-blog",
                        "thanks",
                        "네이버 블로그",
                        List.of()
                ),
                LocalDate.of(2026, 3, 21)
        );

        assertThat(card.category().name()).isEqualTo("EVENT");
        assertThat(card.actionTitle()).contains("맛집");
    }

    @Test
    void createsCreatorMotivationCardForCreatorChannelStory() {
        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://www.youtube.com/watch?v=creator",
                        "youtube.com",
                        null,
                        null,
                        List.of(),
                        "10만 심리 유튜브 채널을 운영하고 있는 이유",
                        "채널 운영 이유",
                        "심리 유튜브 채널을 운영하는 이유와 꾸준히 콘텐츠를 만드는 동기를 말한다.",
                        "youtube",
                        null,
                        "YouTube",
                        List.of()
                ),
                LocalDate.of(2026, 3, 21)
        );

        assertThat(card.category().name()).isEqualTo("PRODUCTIVITY");
        assertThat(card.actionTitle()).contains("이유");
    }

    @Test
    void createsMusicCultureCardForRapperRankingVideo() {
        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://www.youtube.com/watch?v=music",
                        "youtube.com",
                        null,
                        null,
                        List.of(),
                        "노아주다의 한국 TOP3 래퍼",
                        "한국 래퍼 추천",
                        "좋아하는 한국 래퍼와 음악 취향 이야기를 소개한다.",
                        "youtube",
                        null,
                        "YouTube",
                        List.of()
                ),
                LocalDate.of(2026, 3, 21)
        );

        assertThat(card.category().name()).isEqualTo("GENERAL");
        assertThat(card.actionTitle()).contains("곡");
    }

    @Test
    void createsFestivalCardForDamyangFestivalGuide() {
        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://bujarang.tistory.com/entry/festival",
                        "bujarang.tistory.com",
                        null,
                        null,
                        List.of(),
                        "2025년 담양 대나무 축제 일정, 행사내용, 맛집, 주변 관광 추천",
                        "축제 일정과 행사 내용 정리",
                        "담양 대나무 축제 일정과 행사 내용, 맛집과 주변 관광 정보를 소개한다.",
                        "tistory",
                        null,
                        "Tistory",
                        List.of()
                ),
                LocalDate.of(2026, 3, 22)
        );

        assertThat(card.category().name()).isEqualTo("EVENT");
        assertThat(card.actionTitle()).contains("축제");
    }

    @Test
    void createsTutorialCardForJavascriptGuide() {
        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide",
                        "developer.mozilla.org",
                        null,
                        null,
                        List.of(),
                        "JavaScript Guide",
                        "Guide",
                        "JavaScript를 배우기 위한 가이드 문서",
                        "generic",
                        null,
                        "MDN",
                        List.of()
                ),
                LocalDate.of(2026, 3, 22)
        );

        assertThat(card.category().name()).isEqualTo("LEARNING");
        assertThat(card.actionTitle()).contains("단계");
    }

    @Test
    void createsTravelGuideCardForItineraryContent() {
        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://www.youtube.com/watch?v=travel",
                        "youtube.com",
                        null,
                        null,
                        List.of(),
                        "How to Spend 14 Days in Japan - A Japan Travel Itinerary",
                        "Japan Travel Itinerary",
                        "일본 14일 여행 일정 가이드",
                        "youtube",
                        null,
                        "YouTube",
                        List.of()
                ),
                LocalDate.of(2026, 3, 22)
        );

        assertThat(card.category().name()).isEqualTo("EVENT");
        assertThat(card.actionTitle()).contains("일정");
    }

    @Test
    void createsProductReviewCardForBestMonitorArticle() {
        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://www.rtings.com/monitor/reviews/best/monitors",
                        "rtings.com",
                        null,
                        null,
                        List.of(),
                        "The 6 Best Monitors of 2026",
                        "Best monitor reviews",
                        "모니터 추천 비교 글",
                        "generic",
                        null,
                        "RTINGS",
                        List.of()
                ),
                LocalDate.of(2026, 3, 22)
        );

        assertThat(card.actionTitle()).contains("기준");
    }

    @Test
    void createsFitnessCardForWorkoutGuideTitle() {
        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://www.youtube.com/watch?v=fit",
                        "youtube.com",
                        null,
                        null,
                        List.of(),
                        "Beginner Battle Rope Workout",
                        "workout guide",
                        "초보자를 위한 배틀 로프 운동 가이드",
                        "youtube",
                        null,
                        "YouTube",
                        List.of()
                ),
                LocalDate.of(2026, 3, 22)
        );

        assertThat(card.category().name()).isEqualTo("FITNESS");
        assertThat(card.actionTitle()).contains("몸");
    }

    @Test
    void createsTravelGuideCardForDestinationGuideTitle() {
        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://www.travelandleisure.com/travel-guide/iceland",
                        "travelandleisure.com",
                        null,
                        null,
                        List.of(),
                        "Iceland Travel Guide",
                        "Travel Guide",
                        "아이슬란드 여행 정보 가이드",
                        "generic",
                        null,
                        "Travel + Leisure",
                        List.of()
                ),
                LocalDate.of(2026, 3, 22)
        );

        assertThat(card.category().name()).isEqualTo("EVENT");
        assertThat(card.actionTitle()).contains("일정");
    }

    @Test
    void createsFestivalCardForEnglishFestivalTitle() {
        GeneratedPracticeCard card = generator.generate(
                new ActionCardGenerationSource(
                        "https://english.visitseoul.net/events/sample",
                        "english.visitseoul.net",
                        null,
                        null,
                        List.of(),
                        "Seoul Friendship Festival 2025 – Global Food & Culture",
                        "festival guide",
                        "festival and culture event",
                        "generic",
                        null,
                        "Visit Seoul",
                        List.of()
                ),
                LocalDate.of(2026, 3, 22)
        );

        assertThat(card.category().name()).isEqualTo("EVENT");
        assertThat(card.actionTitle()).contains("축제");
    }
}
