package com.fourohfour.backend.modules.content.application;

import com.fourohfour.backend.modules.content.domain.ActionCardCategory;
import com.fourohfour.backend.modules.practice.domain.EnergyLevel;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class HeuristicActionCardGenerator implements ActionCardGenerator {

    private static final Pattern NUMBERED_STEP_PATTERN = Pattern.compile("(?:\\d+[.)]|[0-9]+️⃣)\\s*([^\\n]+?)(?=(?:\\d+[.)]|[0-9]+️⃣)|$)", Pattern.DOTALL);
    private static final Pattern DATE_PATTERN = Pattern.compile("20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2}");
    private static final Pattern EVENT_ENTRY_PATTERN = Pattern.compile("(?:\\d+[.)]|[0-9]+️⃣)\\s*([^📅\\n]+?)\\s*(?:📅\\s*)?([^\\n]{0,120})", Pattern.DOTALL);

    @Override
    public GeneratedPracticeCard generate(ActionCardGenerationSource source, LocalDate today) {
        String normalized = normalize(source);
        String headlineNormalized = normalizeHeadline(source);
        String titleNormalized = normalizeTitle(source);
        String domainNormalized = normalizeDomain(source.sourceDomain());
        String urlNormalized = sanitize(source.url()).toLowerCase(Locale.ROOT);
        String safeTitle = fallbackTitle(source.effectiveTitle(), source.url());
        List<String> orderedSteps = extractOrderedSteps(source);
        List<String> toolIdeas = extractToolIdeas(normalized);
        List<String> topicalIdeas = extractTopicalIdeas(source, normalized);

        if (isTravelEventPlatform(domainNormalized)) {
            String dueDateText = extractDateText(source);
            return new GeneratedPracticeCard(
                    ActionCardCategory.EVENT,
                    buildFestivalActionTitle(headlineNormalized, dueDateText),
                    buildFestivalActionDetail(headlineNormalized, dueDateText),
                    "여행·행사 정보를 바로 일정으로 바꾸는 방법",
                    "이런 페이지는 저장보다 일정화가 더 중요해요. 먼저 갈 날짜나 확인할 일정을 한 줄로 정해두세요.",
                    List.of(),
                    List.of(
                            "갈 날짜 1개 정하기",
                            "장소나 시간 1개 확인하기",
                            "함께 갈 사람 1명 떠올리기"
                    ),
                    "정보는 일정으로 바꿔야 실제 행동이 돼요.",
                    "\"" + safeTitle + "\"를 일정 카드로 바꿨어요.",
                    10,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (isAppleSupportPlatform(domainNormalized)) {
            return buildLearningGuideCard(today, safeTitle);
        }

        if (isFitnessPlatform(domainNormalized)) {
            return buildFitnessCard(today, safeTitle);
        }

        if (isRecipePlatform(domainNormalized)) {
            return buildCookingCard(source, today, safeTitle, orderedSteps);
        }

        if (isEventDocumentPlatform(domainNormalized)) {
            String dueDateText = extractDateText(source);
            return new GeneratedPracticeCard(
                    ActionCardCategory.EVENT,
                    buildFestivalActionTitle(urlNormalized + " " + titleNormalized, dueDateText),
                    buildFestivalActionDetail(urlNormalized + " " + titleNormalized, dueDateText),
                    "축제·관광 문서를 바로 일정으로 바꾸는 방법",
                    "문서형 링크는 저장만 하면 다시 안 보기 쉬워요. 갈 날짜나 확인할 일정 한 줄만 먼저 정해두세요.",
                    List.of(),
                    List.of(
                            "갈 날짜 1개 정하기",
                            "장소나 일정 1개 확인하기",
                            "같이 볼 사람 1명 떠올리기"
                    ),
                    "문서형 정보도 일정으로 바꿔야 실제 행동이 돼요.",
                    "\"" + safeTitle + "\"를 일정 카드로 바꿨어요.",
                    10,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (isTravelEventDocument(domainNormalized, urlNormalized, normalized, titleNormalized)) {
            String dueDateText = extractDateText(source);
            return new GeneratedPracticeCard(
                    ActionCardCategory.EVENT,
                    buildFestivalActionTitle(titleNormalized + " " + normalized + " " + urlNormalized, dueDateText),
                    buildFestivalActionDetail(titleNormalized + " " + normalized + " " + urlNormalized, dueDateText),
                    "축제·관광 문서를 바로 일정으로 바꾸는 방법",
                    "문서형 링크는 저장만 하면 다시 안 보기 쉬워요. 오늘은 갈 날짜나 확인할 일정 한 줄만 먼저 정해두세요.",
                    List.of(),
                    List.of(
                            "갈 날짜 1개 정하기",
                            "장소나 일정 1개 확인하기",
                            "같이 볼 사람 1명 떠올리기"
                    ),
                    "문서형 정보도 일정으로 바꿔야 실제 행동이 돼요.",
                    "\"" + safeTitle + "\"를 일정 카드로 바꿨어요.",
                    10,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (isExplainerPlatform(domainNormalized)
                && isExplainerContent(headlineNormalized)
                && !containsAny(normalized,
                "how to start running", "start running", "started with running", "running guide", "beginner's guide to get started with running")) {
            return buildExplainerCard(today, safeTitle);
        }

        if (containsAny(headlineNormalized, "팝업", "전시", "전시회", "미술관", "페스티벌", "스토어")) {
            List<String> eventIdeas = extractEventIdeas(source);
            String dueDateText = extractDateText(source);
            return new GeneratedPracticeCard(
                    ActionCardCategory.EVENT,
                    buildEventActionTitle(headlineNormalized, dueDateText),
                    buildEventActionDetail(headlineNormalized, dueDateText),
                    "행사 링크를 일정형 카드로 쓰는 방법",
                    buildEventDetailBody(eventIdeas, dueDateText),
                    List.of(
                            new PracticeCardSection("일정", dueDateText == null ? "운영 기간을 먼저 확인해보세요." : dueDateText + " 일정이 확인됐어요.", List.of()),
                            new PracticeCardSection("후보 행사", null, !eventIdeas.isEmpty() ? eventIdeas : List.of("관심 가는 행사 1개를 먼저 고르기"))
                    ),
                    !eventIdeas.isEmpty() ? eventIdeas : List.of(
                            "운영 시간 먼저 확인하기",
                            "이번 주 갈 수 있는 날짜 1개 정하기",
                            "같이 갈 사람에게 링크 공유하기"
                    ),
                    "좋은 행사 링크는 저장만 하지 말고 날짜까지 잡아두는 게 중요해요.",
                    "\"" + safeTitle + "\"를 방문 가능한 일정형 실천 카드로 바꿨어요.",
                    10,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (containsAny(headlineNormalized, "청소", "변기", "욕실", "살림", "과탄산소다", "세제")) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.CLEANING,
                    containsAny(headlineNormalized, "변기") ? "이번 주 변기 청소 1회 해보기" : "오늘 청소 루틴 1개 따라하기",
                    containsAny(headlineNormalized, "변기")
                            ? "준비물을 챙기고 안내된 순서대로 변기 청소를 1회 진행해보세요."
                            : "콘텐츠에서 소개한 청소 순서 중 하나를 골라 오늘 바로 실행해보세요.",
                    "청소 순서를 정확히 따라보는 방법",
                    buildInstructionDetailBody(
                            orderedSteps,
                            "청소 콘텐츠는 순서와 준비물이 중요해요. 순서를 건너뛰지 말고, 준비물부터 맞춘 뒤 한 번만 정확하게 따라해보세요."
                    ),
                    List.of(
                            new PracticeCardSection("준비물과 주의사항", "준비물을 먼저 갖추고 환기처럼 안전 관련 순서를 먼저 확인하세요.", List.of()),
                            new PracticeCardSection("청소 순서", null, orderedSteps.isEmpty() ? List.of("준비물부터 먼저 꺼내두기") : limitIdeas(orderedSteps))
                    ),
                    orderedSteps.isEmpty()
                            ? List.of(
                            "준비물부터 먼저 꺼내두기",
                            "청소 시간을 20분만 확보하기",
                            "끝난 뒤 다음 청소 주기 메모해두기"
                    )
                            : limitIdeas(orderedSteps),
                    "정확한 순서대로 한 번만 해도 청소 난이도가 훨씬 내려가요.",
                    "\"" + safeTitle + "\"에서 소개한 청소 흐름을 오늘 실천 가능한 카드로 바꿨어요.",
                    20,
                    EnergyLevel.MEDIUM,
                    today
            );
        }

        if (isLanguageLearningContent(headlineNormalized)) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.LEARNING,
                    "오늘 표현 1문장 소리내어 말해보기",
                    "오늘 본 표현이나 문장 1개를 골라 소리내어 3번 말하고, 내 상황에 맞는 예문 1개를 직접 써보세요.",
                    "언어 표현을 바로 익히는 방법",
                    "언어 표현은 눈으로만 보면 금방 잊혀요. 소리내어 말하고 내 문장으로 한 번 바꿔보면 실제로 써먹기 쉬워져요.",
                    List.of(),
                    List.of(
                            "표현 1개를 3번 소리내기",
                            "내 상황 예문 1개 적기",
                            "내일 다시 볼 체크 문장 만들기"
                    ),
                    "짧은 표현 하나도 입에 붙이면 바로 쓸 수 있어요.",
                    "\"" + safeTitle + "\"를 바로 써먹는 표현 연습으로 바꿨어요.",
                    5,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (isTravelPhraseContent(headlineNormalized)) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.LEARNING,
                    "여행 표현 3개 소리내어 말해보기",
                    "여행에서 바로 쓸 표현 3개를 골라 소리내어 읽고, 가장 먼저 쓸 표현 1개를 따로 적어보세요.",
                    "여행 표현을 바로 익히는 방법",
                    "표현은 보기만 하면 금방 잊혀요. 소리내어 말하고 실제 상황 하나를 떠올려 연결하면 훨씬 오래 남아요.",
                    List.of(),
                    List.of(
                            "표현 3개 소리내기",
                            "내 상황 문장 1개 만들기",
                            "가장 먼저 쓸 표현 표시하기"
                    ),
                    "짧은 표현도 입에 붙이면 바로 써먹을 수 있어요.",
                    "\"" + safeTitle + "\"를 여행 표현 연습으로 바꿨어요.",
                    5,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (containsAny(headlineNormalized, "intermittent fasting", "16/8 intermittent fasting", "fasting")) {
            return buildExplainerCard(today, safeTitle);
        }

        if (containsAny(normalized,
                "how to start running", "start running", "started with running",
                "how to run faster", "run faster", "running guide",
                "beginner's guide to get started with running")) {
            return buildFitnessCard(today, safeTitle);
        }

        if (isTravelFestivalHeadline(titleNormalized)) {
            String dueDateText = extractDateText(source);
            return new GeneratedPracticeCard(
                    ActionCardCategory.EVENT,
                    buildFestivalActionTitle(titleNormalized, dueDateText),
                    buildFestivalActionDetail(titleNormalized, dueDateText),
                    "축제·여행 정보를 바로 일정으로 바꾸는 방법",
                    "이런 정보형 페이지는 저장보다 일정화가 더 중요해요. 갈 날짜나 확인할 일정을 먼저 한 줄로 정해두세요.",
                    List.of(),
                    List.of(
                            "갈 날짜 1개 정하기",
                            "장소나 시간 1개 확인하기",
                            "같이 갈 사람 1명 떠올리기"
                    ),
                    "일정으로 바꿔야 실제 행동이 돼요.",
                    "\"" + safeTitle + "\"를 일정 카드로 바꿨어요.",
                    10,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (containsAny(normalized,
                "요리", "recipe", "cook", "cooking", "meal", "식단", "레시피", "vegetable", "vegetables")) {
            return buildCookingCard(source, today, safeTitle, orderedSteps);
        }

        if (isTravelGuideContent(normalized)) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.EVENT,
                    "여행 일정 1개 초안 짜보기",
                    "가고 싶은 도시나 장소를 기준으로 하루 일정 1개만 먼저 짜보고, 가장 가고 싶은 장소 1곳을 캘린더나 메모에 적어보세요.",
                    "여행 가이드를 실제 계획으로 바꾸는 방법",
                    "여행 가이드는 정보가 많아서 저장만 하고 끝나기 쉬워요. 먼저 하루 일정 하나만 짜면 실제 계획으로 이어지기 쉬워집니다.",
                    List.of(),
                    List.of(
                            "가고 싶은 장소 1곳 고르기",
                            "하루 일정 1개 적기",
                            "예상 이동 시간 1개 확인하기"
                    ),
                    "여행은 일정 한 줄만 잡아도 훨씬 현실감이 생겨요.",
                    "\"" + safeTitle + "\"를 실제 여행 계획 카드로 바꿨어요.",
                    10,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (isFestivalContent(normalized)) {
            String dueDateText = extractDateText(source);
            return new GeneratedPracticeCard(
                    ActionCardCategory.EVENT,
                    buildFestivalActionTitle(normalized, dueDateText),
                    buildFestivalActionDetail(normalized, dueDateText),
                    "축제 정보를 바로 일정으로 바꾸는 방법",
                    "축제 정보는 저장만 해두면 놓치기 쉬워요. 일정, 장소, 라인업 중 하나라도 마음에 들면 실제 갈 날짜부터 먼저 정해두세요.",
                    List.of(
                            new PracticeCardSection("먼저 확인할 것", null, List.of(
                                    "행사 날짜와 장소 확인하기",
                                    "같이 갈 사람 1명 떠올리기"
                            ))
                    ),
                    List.of(
                            "갈 날짜 1개 캘린더에 적기",
                            "라인업이나 볼거리 1개 저장하기",
                            "같이 갈 사람에게 링크 보내기"
                    ),
                    "축제 정보는 날짜까지 정해야 진짜 내 일정이 돼요.",
                    "\"" + safeTitle + "\"를 방문 가능한 일정 카드로 바꿨어요.",
                    10,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (isExplainerContent(headlineNormalized)) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.LEARNING,
                    "핵심 개념 1개를 내 말로 설명해보기",
                    "오늘 본 설명글에서 가장 중요한 개념 1개를 고르고, 그 뜻을 내 말로 2문장 안에 다시 써보세요.",
                    "설명형 콘텐츠를 이해로 바꾸는 방법",
                    "개념 설명은 읽고 끝내면 금방 흐려져요. 정의와 핵심 포인트를 내 문장으로 다시 적어보면 훨씬 오래 남아요.",
                    List.of(),
                    List.of(
                            "핵심 개념 1개 고르기",
                            "내 말로 2문장 적기",
                            "예시 1개 붙이기"
                    ),
                    "개념은 설명할 수 있어야 내 것이 돼요.",
                    "\"" + safeTitle + "\"를 개념 이해 카드로 바꿨어요.",
                    5,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (isTutorialContent(headlineNormalized)) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.LEARNING,
                    "핵심 단계 1개 바로 따라해보기",
                    "가이드에서 가장 먼저 할 수 있는 단계 1개를 골라 지금 바로 따라하고, 막힌 부분 1개를 메모해보세요.",
                    "튜토리얼을 바로 실행으로 바꾸는 방법",
                    "가이드는 끝까지 다 보기보다 첫 단계 하나를 직접 해보는 게 훨씬 효과적이에요. 작은 실행이 이해를 빠르게 만듭니다.",
                    List.of(),
                    List.of(
                            "첫 단계 1개 실행하기",
                            "막힌 점 1개 메모하기",
                            "다음에 볼 단계 1개 적기"
                    ),
                    "직접 한 번 해보면 이해가 훨씬 빨라져요.",
                    "\"" + safeTitle + "\"를 바로 실행하는 학습 카드로 바꿨어요.",
                    10,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (isTravelGuideContent(headlineNormalized)) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.EVENT,
                    "여행 일정 1개 초안 짜보기",
                    "가고 싶은 도시나 장소를 기준으로 하루 일정 1개만 먼저 짜보고, 가장 가고 싶은 장소 1곳을 캘린더나 메모에 적어보세요.",
                    "여행 가이드를 실제 계획으로 바꾸는 방법",
                    "여행 가이드는 정보가 많아서 저장만 하고 끝나기 쉬워요. 먼저 하루 일정 하나만 짜면 실제 계획으로 이어지기 쉬워집니다.",
                    List.of(),
                    List.of(
                            "가고 싶은 장소 1곳 고르기",
                            "하루 일정 1개 적기",
                            "예상 이동 시간 1개 확인하기"
                    ),
                    "여행은 일정 한 줄만 잡아도 훨씬 현실감이 생겨요.",
                    "\"" + safeTitle + "\"를 실제 여행 계획 카드로 바꿨어요.",
                    10,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (isSocialDesignContent(headlineNormalized)) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.LEARNING,
                    "템플릿 1개 골라 초안 만들어보기",
                    "오늘 본 도구나 템플릿에서 가장 마음에 드는 스타일 1개를 골라 제목과 이미지 자리만 넣은 초안을 만들어보세요.",
                    "디자인 도구를 바로 써보는 방법",
                    "디자인 도구는 설명을 읽는 것보다 빈 템플릿 하나를 바로 채워보는 게 훨씬 빨라요. 초안 수준이어도 충분합니다.",
                    List.of(),
                    List.of(
                            "템플릿 1개 고르기",
                            "제목 1줄 넣기",
                            "첫 초안 저장하기"
                    ),
                    "도구는 눌러봐야 내 것이 돼요.",
                    "\"" + safeTitle + "\"를 바로 만드는 카드로 바꿨어요.",
                    10,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (isMarketingGuideContent(headlineNormalized)) {
            if (containsAny(headlineNormalized, "instagram tips", "photographers", "top photographers")) {
                return new GeneratedPracticeCard(
                        ActionCardCategory.LEARNING,
                        "오늘 바로 써볼 인스타 팁 1개 적용하기",
                        "콘텐츠에서 가장 바로 적용할 수 있는 팁 1개를 골라 오늘 게시물이나 촬영에 바로 써보고, 결과를 한 줄로 메모해보세요.",
                        "소셜 팁을 바로 실행으로 바꾸는 방법",
                        "팁 콘텐츠는 읽고 끝나기 쉬워요. 바로 써볼 한 가지를 정하고 실제 화면이나 게시물에 적용해보면 훨씬 빨리 내 것이 됩니다.",
                        List.of(),
                        List.of(
                                "적용할 팁 1개 고르기",
                                "오늘 게시물이나 촬영에 바로 써보기",
                                "달라진 점 1줄 적기"
                        ),
                        "팁은 바로 적용해봐야 내 방식으로 바뀌어요.",
                        "\"" + safeTitle + "\"를 바로 실행하는 소셜 학습 카드로 바꿨어요.",
                        8,
                        EnergyLevel.LOW,
                        today
                );
            }

            return new GeneratedPracticeCard(
                    ActionCardCategory.PRODUCTIVITY,
                    "실행할 마케팅 아이디어 1개 정하기",
                    "가이드에서 가장 바로 적용할 수 있는 아이디어 1개를 골라 오늘 할 일로 한 줄 정리해보세요.",
                    "마케팅 가이드를 실행으로 바꾸는 방법",
                    "마케팅 글은 읽고 끝나기 쉬워요. 바로 적용할 한 가지를 정리해 두면 실제 실행 가능성이 높아집니다.",
                    List.of(),
                    List.of(
                            "적용할 아이디어 1개 고르기",
                            "오늘 할 일 한 줄 적기",
                            "성과 확인 기준 1개 적기"
                    ),
                    "아이디어는 실행 문장으로 바꾸면 살아나요.",
                    "\"" + safeTitle + "\"를 바로 적용하는 실무 카드로 바꿨어요.",
                    8,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (isProductReviewContent(headlineNormalized)) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.GENERAL,
                    "비교 기준 3개 먼저 적어보기",
                    "추천 글을 보기 전에 내가 가장 중요하게 보는 기준 3개를 적고, 후보 1개만 먼저 골라보세요.",
                    "추천 글을 선택으로 바꾸는 방법",
                    "제품 추천은 정보가 많아 결정이 늦어지기 쉬워요. 내 기준 3개만 먼저 정하면 후보를 좁히기 쉬워집니다.",
                    List.of(),
                    List.of(
                            "비교 기준 3개 적기",
                            "후보 1개 표시하기",
                            "예산 범위 1줄 적기"
                    ),
                    "기준이 생기면 선택이 훨씬 빨라져요.",
                    "\"" + safeTitle + "\"를 비교 기준 카드로 바꿨어요.",
                    5,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (isFoodIngredientContent(headlineNormalized)) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.COOKING,
                    "오늘 낯선 식재료 1가지 찾아보기",
                    "링크에서 눈에 띈 식재료나 음식 1가지를 골라 어떤 맛과 쓰임이 있는지 찾아보고, 먹어볼 방법 1개를 적어보세요.",
                    "식재료 콘텐츠를 바로 써먹는 방법",
                    "식재료 정보는 읽고 끝내기보다 하나를 골라 실제 요리나 장보기로 연결할 때 더 오래 남아요.",
                    List.of(),
                    List.of(
                            "식재료 특징 1줄 적기",
                            "어울리는 요리 1개 찾기",
                            "다음 장볼 때 살지 메모하기"
                    ),
                    "낯선 식재료도 하나만 이해하면 요리가 훨씬 재미있어져요.",
                    "\"" + safeTitle + "\"를 음식 탐색 행동으로 바꿨어요.",
                    5,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (containsAny(headlineNormalized, "감사", "grateful", "gratitude", "thanks", "thankful")) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.GRATITUDE,
                    "오늘 감사한 일 3가지 떠올려보기",
                    "지금 떠오르는 감사한 장면이나 사람을 3가지 적어보고, 그중 하나에는 짧게 이유도 덧붙여보세요.",
                    "감사 카드를 더 깊게 쓰는 방법",
                    "감사한 일 3가지를 적을 때는 장면, 사람, 이유를 함께 적으면 더 오래 남아요. 가능하면 오늘 실제로 있었던 순간을 기준으로 써보세요.",
                    List.of(),
                    List.of(
                            "오늘 고마웠던 사람 1명에게 짧게 메시지 보내기",
                            "감사한 장면 중 가장 선명한 순간 1개를 2문장으로 기록하기",
                            "내일도 반복하고 싶은 감사 루틴 시간을 정하기"
                    ),
                    "작게 시작해도 오늘의 기분이 바로 달라질 수 있어요.",
                    "\"" + safeTitle + "\"에서 느낀 감정을 바로 행동으로 바꾸기 좋은 카드예요.",
                    5,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (isFestivalContent(headlineNormalized)) {
            String dueDateText = extractDateText(source);
            return new GeneratedPracticeCard(
                    ActionCardCategory.EVENT,
                    buildFestivalActionTitle(headlineNormalized, dueDateText),
                    buildFestivalActionDetail(headlineNormalized, dueDateText),
                    "축제 정보를 바로 일정으로 바꾸는 방법",
                    "축제 정보는 저장만 해두면 놓치기 쉬워요. 일정, 장소, 라인업 중 하나라도 마음에 들면 실제 갈 날짜부터 먼저 정해두세요.",
                    List.of(
                            new PracticeCardSection("먼저 확인할 것", null, List.of(
                                    "행사 날짜와 장소 확인하기",
                                    "같이 갈 사람 1명 떠올리기"
                            ))
                    ),
                    List.of(
                            "갈 날짜 1개 캘린더에 적기",
                            "라인업이나 볼거리 1개 저장하기",
                            "같이 갈 사람에게 링크 보내기"
                    ),
                    "축제 정보는 날짜까지 정해야 진짜 내 일정이 돼요.",
                    "\"" + safeTitle + "\"를 방문 가능한 일정 카드로 바꿨어요.",
                    10,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (isCreatorContent(headlineNormalized)) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.PRODUCTIVITY,
                    "내가 계속 만들 이유 1문장 적기",
                    "오늘 본 콘텐츠를 떠올리며 내가 꾸준히 만들거나 이어가고 싶은 일의 이유를 1문장으로 적어보세요.",
                    "창작 동기를 행동으로 바꾸는 방법",
                    "채널 운영이나 창작 이유에 공감했다면 남의 이유를 보는 데서 멈추지 말고 내 이유를 언어로 적어보는 게 가장 좋아요.",
                    List.of(),
                    List.of(
                            "내가 만들고 싶은 주제 1개 적기",
                            "그걸 계속할 이유 1문장 적기",
                            "이번 주 올릴 작은 시도 1개 정하기"
                    ),
                    "이유를 적으면 꾸준함이 훨씬 쉬워져요.",
                    "\"" + safeTitle + "\"를 내 동기 정리 카드로 바꿨어요.",
                    5,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (isMusicCultureContent(titleNormalized)) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.GENERAL,
                    "오늘 인상 깊은 곡 1개 저장해보기",
                    "콘텐츠에서 흥미로웠던 아티스트나 곡 1개를 골라 바로 저장하고, 왜 좋았는지 한 줄만 적어보세요.",
                    "음악 콘텐츠를 취향 기록으로 바꾸는 방법",
                    "랭킹이나 추천 콘텐츠는 보고 끝나기 쉬워요. 오늘은 하나만 저장하고 내 취향 이유를 짧게 남겨보세요.",
                    List.of(),
                    List.of(
                            "곡 1개 저장하기",
                            "좋았던 이유 1줄 적기",
                            "다음에 들을 아티스트 1명 메모하기"
                    ),
                    "취향은 저장하고 기록해야 내 것이 돼요.",
                    "\"" + safeTitle + "\"를 바로 해볼 취향 기록 행동으로 바꿨어요.",
                    3,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (containsAny(headlineNormalized,
                "운동", "fitness", "run", "running", "runner", "workout", "헬스", "스트레칭", "걷기",
                "exercise", "training", "train", "5k", "rope", "band", "calisthenics",
                "push-up", "pushup", "plank", "squat", "lunge", "curl", "deadlift", "press", "start running", "started with running")) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.FITNESS,
                    "오늘 10분만 몸 움직이기",
                    "산책, 스트레칭, 스쿼트처럼 바로 시작할 수 있는 움직임 하나를 골라 10분만 실천해보세요.",
                    "운동 자극 영상을 행동으로 바꾸는 방법",
                    "운동 자극 콘텐츠는 의욕을 올려주지만, 실제 행동으로 이어지려면 시작 장벽을 낮춰야 해요. 운동복 갈아입기, 물 마시기, 10분 걷기처럼 바로 가능한 동작을 하나 고르세요.",
                    List.of(),
                    List.of(
                            "운동복만 갈아입고 5분 스트레칭 하기",
                            "집 밖으로 나가 10분 걷기",
                            "스쿼트 15회 3세트처럼 짧은 루틴 하나 정하기"
                    ),
                    "완벽한 운동보다 지금 시작하는 움직임이 더 큰 변화를 만들어요.",
                    "\"" + safeTitle + "\"를 저장한 흐름을 오늘의 몸 사용으로 연결했어요.",
                    10,
                    EnergyLevel.MEDIUM,
                    today
            );
        }

        if (isFoodDiscoveryContent(headlineNormalized) && !containsAny(headlineNormalized, "레시피", "요리", "recipe", "cook", "cooking")) {
            if (containsAny(headlineNormalized, "맛집", "식당", "카페", "콩물국수", "국수")) {
                return new GeneratedPracticeCard(
                        ActionCardCategory.EVENT,
                        "이번 주 맛집 1곳 가보기",
                        "링크에 나온 후보 중 가장 끌리는 곳 1곳을 골라 이번 주에 갈 날짜나 시간부터 정해보세요.",
                        "맛집 콘텐츠를 실제 방문으로 바꾸는 방법",
                        "맛집 정보는 저장만 하면 금방 묻혀요. 메뉴보다 먼저 갈 수 있는 시간과 동선을 정하면 실제 방문 확률이 높아져요.",
                        List.of(
                                new PracticeCardSection("방문 체크", null, List.of(
                                        "가게 위치 확인하기",
                                        "대표 메뉴 1개 정하기"
                                ))
                        ),
                        List.of(
                                "이번 주 갈 시간 1개 정하기",
                                "대표 메뉴 1개 저장하기",
                                "같이 갈 사람에게 링크 보내기"
                        ),
                        "먹고 싶은 곳은 시간까지 정해야 진짜 가게 돼요.",
                        "\"" + safeTitle + "\"를 저장형 정보가 아니라 방문 계획으로 바꿨어요.",
                        10,
                        EnergyLevel.LOW,
                        today
                );
            }

            return new GeneratedPracticeCard(
                    ActionCardCategory.COOKING,
                    "오늘 낯선 식재료 1가지 찾아보기",
                    "링크에서 눈에 띈 식재료나 음식 1가지를 골라 어떤 맛과 쓰임이 있는지 찾아보고, 먹어볼 방법 1개를 적어보세요.",
                    "식재료 콘텐츠를 바로 써먹는 방법",
                    "식재료 정보는 읽고 끝내기보다 하나를 골라 실제 요리나 장보기로 연결할 때 더 오래 남아요.",
                    List.of(),
                    List.of(
                            "식재료 특징 1줄 적기",
                            "어울리는 요리 1개 찾기",
                            "다음 장볼 때 살지 메모하기"
                    ),
                    "낯선 식재료도 하나만 이해하면 요리가 훨씬 재미있어져요.",
                    "\"" + safeTitle + "\"를 음식 탐색 행동으로 바꿨어요.",
                    5,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (containsAny(headlineNormalized, "루틴", "habit", "routine", "습관", "daily", "아침", "저녁")) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.ROUTINE,
                    "오늘 같은 시간에 1번만 시작해보기",
                    "지속하고 싶은 행동 하나를 정하고, 오늘 딱 한 번 같은 시간에 시작해보세요. 기록은 한 줄이면 충분해요.",
                    "루틴 카드를 더 구체적으로 쓰는 방법",
                    "루틴은 거창한 결심보다 반복 가능한 신호가 중요해요. 시간, 장소, 행동 순서를 함께 정하면 오늘 한 번의 실행이 내일의 반복으로 이어지기 쉬워져요.",
                    List.of(),
                    List.of(
                            "실행 시간을 아침 7시처럼 한 시각으로 고정하기",
                            "루틴 시작 전에 할 신호 행동 1개 정하기",
                            "실행 후 체크할 한 줄 기록 문장 만들기"
                    ),
                    "루틴은 결심보다 반복 신호가 만들어요.",
                    "\"" + safeTitle + "\"를 오늘의 반복 가능한 행동으로 압축했어요.",
                    7,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (containsAny(headlineNormalized, "관계", "relationship", "친구", "가족", "parent", "대화", "소통", "연락", "girlfriend", "boyfriend")) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.RELATIONSHIP,
                    "오늘 한 사람에게 먼저 안부 보내기",
                    "고마웠던 사람이나 오래 연락 못 한 사람 한 명을 떠올리고, 짧은 안부 메시지를 먼저 보내보세요.",
                    "관계 카드를 더 깊게 실천하는 방법",
                    "안부는 길 필요가 없어요. 상대를 떠올린 이유와 지금 전하고 싶은 한 문장을 붙이면 진심이 더 잘 전달돼요.",
                    List.of(),
                    List.of(
                            "고마웠던 일 하나를 같이 적어 보내기",
                            "이번 주 중 통화 가능한 시간을 함께 제안하기",
                            "답장을 기다리지 않고 먼저 연결한 것 자체를 체크하기"
                    ),
                    "관계는 생각보다 먼저 손 내미는 작은 행동에서 움직여요.",
                    "\"" + safeTitle + "\"를 사람과의 연결로 바로 이어지게 만들었어요.",
                    5,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (containsAny(normalized, "피그마", "figma", "캡컷", "vllo", "노션", "툴", "tool", "포트폴리오")) {
            String primaryTool = containsAny(normalized, "피그마", "figma") ? "피그마" : "추천 툴";
            List<String> toolOptions = !toolIdeas.isEmpty()
                    ? toolIdeas
                    : List.of(
                    "튜토리얼 영상 1개 보고 바로 따라하기",
                    "간단한 개인 프로젝트에 바로 써보기",
                    "팀플이나 과제에 공동 작업으로 적용해보기"
            );

            return new GeneratedPracticeCard(
                    ActionCardCategory.LEARNING,
                    primaryTool + "에서 화면 1개 직접 만들어보기",
                    primaryTool + "를 열고 텍스트와 도형만으로 아주 간단한 화면이나 보드 1개를 만들어보세요.",
                    "추천 툴을 실제로 써보는 방법",
                    primaryTool + " 같은 툴 추천 콘텐츠는 여러 개를 한 번에 다 배우기보다, 하나를 골라 직접 켜보는 게 가장 빨라요. 먼저 손으로 하나 만들어보고 나머지 툴은 비교 관점으로 저장하세요.",
                    List.of(
                            new PracticeCardSection("대표 툴", primaryTool + "를 오늘 직접 실행해보는 것이 핵심이에요.", List.of(primaryTool + " 켜보기")),
                            new PracticeCardSection("다른 추천 툴", null, toolOptions)
                    ),
                    toolOptions,
                    "툴은 보기보다 직접 한 번 눌러봐야 내 것이 돼요.",
                    "\"" + safeTitle + "\"에서 나온 추천 툴 중 하나를 오늘 바로 써보는 행동으로 압축했어요.",
                    20,
                    EnergyLevel.MEDIUM,
                    today
            );
        }

        if (containsAny(normalized, "집중", "focus", "deep work", "생산성", "productivity", "몰입", "할일")) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.PRODUCTIVITY,
                    "가장 중요한 일 1개를 25분만 몰입하기",
                    "지금 가장 중요한 작업 1개만 정해서 타이머 25분을 켜고, 다른 탭 없이 끝까지 밀어보세요.",
                    "집중 카드를 더 구체적으로 쓰는 방법",
                    "집중은 해야 할 일을 많이 적는 것보다, 시작 지점을 선명하게 만드는 게 더 중요해요. 첫 25분 안에 끝낼 수 있는 단위로 잘라보세요.",
                    List.of(),
                    List.of(
                            "가장 중요한 일 1개의 시작 작업만 적기",
                            "25분 동안 닫아둘 앱이나 탭 3개 정하기",
                            "끝나면 바로 이어갈 다음 행동 1줄 적어두기"
                    ),
                    "정보를 더 모으는 것보다 한 번의 몰입이 하루를 바꿔요.",
                    "\"" + safeTitle + "\"를 바로 실행 가능한 집중 행동으로 바꿨어요.",
                    25,
                    EnergyLevel.MEDIUM,
                    today
            );
        }

        if (isJournalingContent(normalized)) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.ROUTINE,
                    "오늘 모닝페이지 10분 써보기",
                    "지금 머릿속에 떠오르는 생각을 10분만 멈추지 말고 적어보세요. 마지막에는 지금 내가 진짜 원하는 것 1문장을 덧붙여보세요.",
                    "모닝페이지를 바로 시작하는 방법",
                    "모닝페이지는 잘 쓰는 것보다 멈추지 않고 적는 게 중요해요. 복잡한 생각, 불안한 이유, 진짜 원하는 것을 검열 없이 적고 마지막에 오늘 바로 옮길 행동 하나를 골라보세요.",
                    List.of(
                            new PracticeCardSection("시작 문장", "첫 줄에 지금 머릿속이 어떤지 그대로 적어보세요.", List.of(
                                    "지금 가장 복잡한 생각은 무엇인지 쓰기",
                                    "지금 불안한 이유를 짧게 적기"
                            )),
                            new PracticeCardSection("마무리 질문", "마지막 줄에는 원하는 방향을 한 문장으로 남겨보세요.", List.of(
                                    "지금 내가 진짜 원하는 것 1문장 쓰기",
                                    "오늘 바로 할 수 있는 행동 1개 적기"
                            ))
                    ),
                    List.of(
                            "불안한 이유를 3줄로 적기",
                            "지금 진짜 원하는 것 1문장 쓰기",
                            "오늘 옮길 행동 1개 정하기"
                    ),
                    "생각을 적기 시작하면 막연한 불안이 행동으로 바뀌기 쉬워져요.",
                    "\"" + safeTitle + "\"의 핵심을 호흡이 아니라 쓰기 실천으로 연결했어요.",
                    10,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (isCampusOnboardingContent(headlineNormalized)) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.ROUTINE,
                    "학사 공지사항 오늘 1번 확인하기",
                    "학교 홈페이지나 학사 공지사항 게시판을 열어 오늘 올라온 안내를 1번만 확인하고, 바로 필요한 일정이나 할 일을 한 줄 메모해보세요.",
                    "새내기 체크리스트를 바로 쓰는 방법",
                    "대학생활 꿀팁 콘텐츠는 다 외우려 하기보다 지금 바로 챙길 한 가지를 정하는 게 중요해요. 학사 공지 확인을 시작점으로 잡고, 캠퍼스 동선이나 필수 앱 설치는 체크리스트로 이어가보세요.",
                    List.of(
                            new PracticeCardSection("오늘 바로 할 일", "공지사항 확인을 끝낸 뒤 바로 체크리스트 한 줄만 남겨보세요.", List.of(
                                    "오늘 올라온 공지 1개 확인하기",
                                    "중요한 일정 1개 메모하기"
                            )),
                            new PracticeCardSection("이어서 챙길 것", null, List.of(
                                    "캠퍼스 자주 가는 건물 위치 확인하기",
                                    "에브리타임 같은 필수 앱 2개 설치하기",
                                    "이번 학기 챙길 스펙 항목 1개 적기"
                            ))
                    ),
                    List.of(
                            "캠퍼스 자주 가는 건물 위치 확인하기",
                            "필수 앱 2개 설치하기",
                            "이번 학기 체크리스트 3개 적기"
                    ),
                    "학교 정보는 한 번에 다 챙기기보다 오늘 필요한 것부터 확인하면 훨씬 덜 막막해져요.",
                    "\"" + safeTitle + "\"의 핵심을 대학생활 적응 체크리스트로 바꿨어요.",
                    10,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (containsAny(normalized, "명상", "mindfulness", "마음", "anxiety", "stress", "불안", "호흡", "meditation")) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.MINDFULNESS,
                    "호흡에 3분만 집중해보기",
                    "앉은 자리에서 3분만 천천히 숨을 세며 들이쉬고 내쉬어보세요. 생각이 떠오르면 호흡으로 돌아오면 돼요.",
                    "마음관리 카드를 더 깊게 실천하는 방법",
                    "호흡 집중은 잘하려고 애쓰기보다 잠깐 멈추는 경험 자체가 중요해요. 짧게 멈추고, 몸의 감각 하나만 붙잡아도 충분해요.",
                    List.of(),
                    List.of(
                            "눈을 감고 호흡 횟수 10번 세기",
                            "불안한 생각이 올라오면 한 단어로 이름 붙이기",
                            "끝난 뒤 지금 감정 상태를 한 단어로 적기"
                    ),
                    "짧은 멈춤이 감정의 속도를 낮춰줘요.",
                    "\"" + safeTitle + "\"를 오늘 바로 체감 가능한 안정 행동으로 정리했어요.",
                    3,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (containsAny(headlineNormalized, "study", "learn", "학습", "공부", "book", "독서", "lecture", "강의")) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.LEARNING,
                    "핵심 한 문장을 내 말로 정리하기",
                    "오늘 저장한 내용에서 가장 남는 포인트 1개를 골라, 내 상황에 맞는 한 문장으로 다시 써보세요.",
                    "배운 내용을 실천으로 옮기는 방법",
                    "학습 콘텐츠는 정리 없이 넘기면 금방 사라져요. 핵심 문장을 내 상황에 맞게 바꾸고, 바로 적용할 작은 실험 하나를 붙이면 실행 가능성이 커져요.",
                    List.of(),
                    List.of(
                            "가장 기억나는 문장을 내 말로 다시 쓰기",
                            "오늘 적용해볼 예시 1개 적기",
                            "내일 다시 볼 체크 질문 1개 만들기"
                    ),
                    "배움은 소비보다 재정리에서 오래 남아요.",
                    "\"" + safeTitle + "\"를 지식 소비에서 내 지식으로 바꾸는 카드예요.",
                    8,
                    EnergyLevel.LOW,
                    today
            );
        }

        if (containsAny(headlineNormalized, "money", "finance", "budget", "재테크", "소비", "저축", "가계부")) {
            return new GeneratedPracticeCard(
                    ActionCardCategory.FINANCE,
                    "오늘 지출 1건만 돌아보기",
                    "오늘 혹은 최근 지출 한 건을 떠올리고, 이 소비가 필요했는지 한 줄로 점검해보세요.",
                    "재무 카드를 더 구체적으로 실천하는 방법",
                    "지출 점검은 전체 예산표를 다시 만드는 것보다, 방금 쓴 돈 한 건을 보는 데서 시작하는 편이 훨씬 쉬워요. 이유와 대안을 함께 적어보세요.",
                    List.of(),
                    List.of(
                            "최근 결제 1건의 이유를 한 줄로 적기",
                            "같은 금액으로 가능한 다른 선택지 1개 떠올리기",
                            "다음 비슷한 소비 전에 볼 체크 문장 만들기"
                    ),
                    "재무 습관은 거창한 계획보다 작은 복기에서 시작돼요.",
                    "\"" + safeTitle + "\"를 당장 실행할 수 있는 돈 점검 행동으로 바꿨어요.",
                    5,
                    EnergyLevel.LOW,
                    today
            );
        }

        return new GeneratedPracticeCard(
                ActionCardCategory.GENERAL,
                "오늘 바로 해볼 1가지 정하고 실행하기",
                "\"" + safeTitle + "\"를 보고 떠오른 행동 중 가장 쉬운 1가지를 정해서 지금 바로 10분 안에 시작해보세요.",
                "이 카드를 더 자세히 활용하는 방법",
                "콘텐츠를 저장했다면 이미 끌리는 지점이 있었다는 뜻이에요. 그 포인트를 오늘 할 수 있는 가장 작은 행동으로 잘라내면 실천 확률이 높아져요.",
                List.of(),
                topicalIdeas.isEmpty() ? List.of(
                        "지금 떠오른 행동 후보 3개를 적기",
                        "그중 10분 안에 가능한 1개만 고르기",
                        "실행 시간을 바로 캘린더나 메모에 적기"
                ) : topicalIdeas,
                "읽는 것으로 끝내지 않고 행동으로 마무리해봐요.",
                "저장한 콘텐츠를 오늘의 실천으로 연결하는 기본 카드예요.",
                10,
                EnergyLevel.LOW,
                today
        );
    }

    private String normalize(ActionCardGenerationSource source) {
        return String.join(" ",
                        sanitize(source.requestedTitle()),
                        sanitize(source.requestedNote()),
                        sanitize(source.scrapedTitle()),
                        sanitize(source.scrapedDescription()),
                        sanitize(source.scrapedText()),
                        sanitize(source.sourceType()),
                        sanitize(String.join(" ", source.tags() == null ? java.util.List.of() : source.tags())))
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeHeadline(ActionCardGenerationSource source) {
        return String.join(" ",
                        sanitize(source.requestedTitle()),
                        sanitize(source.requestedNote()),
                        sanitize(source.scrapedTitle()),
                        sanitize(source.scrapedDescription()),
                        sanitize(String.join(" ", source.tags() == null ? java.util.List.of() : source.tags())))
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeTitle(ActionCardGenerationSource source) {
        return String.join(" ",
                        sanitize(source.requestedTitle()),
                        sanitize(source.scrapedTitle()))
                .toLowerCase(Locale.ROOT);
    }

    private String fallbackTitle(String title, String url) {
        if (title != null && !title.isBlank()) {
            return trimTitle(title);
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return host.replace("www.", "");
            }
        } catch (IllegalArgumentException ignored) {
        }
        return "저장한 콘텐츠";
    }

    private String trimTitle(String title) {
        String normalized = title.trim();
        if (normalized.length() <= 26) {
            return normalized;
        }
        return normalized.substring(0, 26) + "...";
    }

    private String sanitize(String value) {
        return value == null ? "" : value;
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractOrderedSteps(ActionCardGenerationSource source) {
        String corpus = sanitize(source.scrapedText());
        if (corpus.isBlank()) {
            corpus = sanitize(source.scrapedDescription());
        }
        Matcher matcher = NUMBERED_STEP_PATTERN.matcher(corpus);
        List<String> steps = new ArrayList<>();
        while (matcher.find()) {
            String step = matcher.group(1).replaceAll("\\s+", " ").trim();
            if (!step.isBlank()) {
                steps.add(step);
            }
            if (steps.size() >= 4) {
                break;
            }
        }
        return steps;
    }

    private String buildInstructionDetailBody(List<String> orderedSteps, String fallback) {
        if (orderedSteps.isEmpty()) {
            return fallback;
        }
        StringBuilder builder = new StringBuilder("콘텐츠에 나온 순서를 최대한 유지해서 따라가보세요.");
        for (int i = 0; i < orderedSteps.size(); i++) {
            builder.append(' ').append(i + 1).append("단계: ").append(orderedSteps.get(i)).append('.');
        }
        return builder.toString();
    }

    private List<String> extractToolIdeas(String normalized) {
        List<String> ideas = new ArrayList<>();
        if (containsAny(normalized, "피그마", "figma")) {
            ideas.add("피그마에서 프레임 1개와 텍스트 1개 넣어보기");
        }
        if (containsAny(normalized, "캡컷", "capcut")) {
            ideas.add("캡컷으로 15초짜리 영상 1개 잘라보기");
        }
        if (containsAny(normalized, "vllo")) {
            ideas.add("VLLO로 자막 1개 넣어보며 비교해보기");
        }
        if (containsAny(normalized, "노션", "notion")) {
            ideas.add("노션에 과제나 프로젝트 페이지 1개 만들어보기");
        }
        if (ideas.size() > 4) {
            return ideas.subList(0, 4);
        }
        return ideas;
    }

    private List<String> extractTopicalIdeas(ActionCardGenerationSource source, String normalized) {
        List<String> ideas = new ArrayList<>();
        if (containsAny(normalized, "무료 전시", "전시", "미술관", "행사", "팝업")) {
            ideas.add("관심 가는 전시 1곳의 운영 시간 확인하기");
            ideas.add("이번 주에 갈 수 있는 날짜 1개 캘린더에 적기");
            ideas.add("같이 갈 사람 1명에게 링크 보내기");
        }
        if (ideas.isEmpty() && source.scrapedDescription() != null && source.scrapedDescription().contains(",")) {
            for (String part : source.scrapedDescription().split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isBlank()) {
                    ideas.add(trimmed);
                }
                if (ideas.size() >= 4) {
                    break;
                }
            }
        }
        return limitIdeas(ideas);
    }

    private List<String> limitIdeas(List<String> ideas) {
        if (ideas == null || ideas.isEmpty()) {
            return List.of();
        }
        List<String> limited = new ArrayList<>();
        for (String idea : ideas) {
            if (idea == null || idea.isBlank()) {
                continue;
            }
            limited.add(idea.trim());
            if (limited.size() >= 4) {
                break;
            }
        }
        return limited;
    }

    private GeneratedPracticeCard buildCookingCard(
            ActionCardGenerationSource source,
            LocalDate today,
            String safeTitle,
            List<String> orderedSteps
    ) {
        return new GeneratedPracticeCard(
                ActionCardCategory.COOKING,
                "오늘 이 레시피의 핵심 한 가지 해보기",
                "재료 손질, 소스 준비, 간단한 한 끼 만들기 중 하나만 골라 오늘 바로 실행해보세요.",
                "요리 콘텐츠를 바로 실천하는 방법",
                buildInstructionDetailBody(
                        orderedSteps,
                        "요리는 전체 과정을 한 번에 다 하려 하면 부담이 커져요. 재료 준비, 양념 만들기, 한 메뉴만 따라 하기처럼 단계 하나만 떼어내면 실행이 쉬워져요."
                ),
                buildRecipeSections(source, orderedSteps),
                orderedSteps.isEmpty()
                        ? List.of(
                        "필요한 재료를 지금 메모장에 적기",
                        "오늘 만들 핵심 소스만 먼저 준비하기",
                        "전체 레시피 중 가장 간단한 1단계만 실행하기"
                )
                        : limitIdeas(orderedSteps),
                "전부 하지 않아도 한 단계만 시작하면 요리가 일상으로 들어와요.",
                "\"" + safeTitle + "\"에서 행동 장벽이 가장 낮은 첫 단계를 골랐어요.",
                15,
                EnergyLevel.MEDIUM,
                today
        );
    }

    private GeneratedPracticeCard buildFitnessCard(LocalDate today, String safeTitle) {
        return new GeneratedPracticeCard(
                ActionCardCategory.FITNESS,
                "오늘 10분만 몸 움직이기",
                "산책, 스트레칭, 스쿼트처럼 바로 시작할 수 있는 움직임 하나를 골라 10분만 실천해보세요.",
                "운동 자극 영상을 행동으로 바꾸는 방법",
                "운동 자극 콘텐츠는 의욕을 올려주지만, 실제 행동으로 이어지려면 시작 장벽을 낮춰야 해요. 운동복 갈아입기, 물 마시기, 10분 걷기처럼 바로 가능한 동작을 하나 고르세요.",
                List.of(),
                List.of(
                        "운동복만 갈아입고 5분 스트레칭 하기",
                        "집 밖으로 나가 10분 걷기",
                        "스쿼트 15회 3세트처럼 짧은 루틴 하나 정하기"
                ),
                "완벽한 운동보다 지금 시작하는 움직임이 더 큰 변화를 만들어요.",
                "\"" + safeTitle + "\"를 저장한 흐름을 오늘의 몸 사용으로 연결했어요.",
                10,
                EnergyLevel.MEDIUM,
                today
        );
    }

    private GeneratedPracticeCard buildLearningGuideCard(LocalDate today, String safeTitle) {
        return new GeneratedPracticeCard(
                ActionCardCategory.LEARNING,
                "핵심 단계 1개 바로 따라해보기",
                "가이드에서 가장 먼저 할 수 있는 단계 1개를 골라 지금 바로 따라하고, 막힌 부분 1개를 메모해보세요.",
                "튜토리얼을 바로 실행으로 바꾸는 방법",
                "가이드는 끝까지 다 보기보다 첫 단계 하나를 직접 해보는 게 훨씬 효과적이에요. 작은 실행이 이해를 빠르게 만듭니다.",
                List.of(),
                List.of(
                        "첫 단계 1개 실행하기",
                        "막힌 점 1개 메모하기",
                        "다음에 볼 단계 1개 적기"
                ),
                "직접 한 번 해보면 이해가 훨씬 빨라져요.",
                "\"" + safeTitle + "\"를 바로 실행하는 학습 카드로 바꿨어요.",
                10,
                EnergyLevel.LOW,
                today
        );
    }

    private GeneratedPracticeCard buildExplainerCard(LocalDate today, String safeTitle) {
        return new GeneratedPracticeCard(
                ActionCardCategory.LEARNING,
                "핵심 개념 1개를 내 말로 설명해보기",
                "오늘 본 설명글에서 가장 중요한 개념 1개를 고르고, 그 뜻을 내 말로 2문장 안에 다시 써보세요.",
                "설명형 콘텐츠를 이해로 바꾸는 방법",
                "개념 설명은 읽고 끝내면 금방 흐려져요. 정의와 핵심 포인트를 내 문장으로 다시 적어보면 훨씬 오래 남아요.",
                List.of(),
                List.of(
                        "핵심 개념 1개 고르기",
                        "내 말로 2문장 적기",
                        "예시 1개 붙이기"
                ),
                "개념은 설명할 수 있어야 내 것이 돼요.",
                "\"" + safeTitle + "\"를 개념 이해 카드로 바꿨어요.",
                5,
                EnergyLevel.LOW,
                today
        );
    }

    private boolean isJournalingContent(String normalized) {
        boolean explicitJournaling = containsAny(normalized,
                "모닝페이지", "morning pages", "morning page", "저널", "journaling", "journal");
        boolean reflectiveWriting = containsAny(normalized, "글쓰기", "쓰기", "적기", "기록", "노트")
                && containsAny(normalized, "원하는", "원하는걸", "원하는 걸", "불안", "머릿속", "정리", "생각");
        return explicitJournaling || reflectiveWriting;
    }

    private boolean isCampusOnboardingContent(String normalized) {
        boolean freshmanContext = containsAny(normalized,
                "새내기", "신입생", "24학번", "대학생활", "캠퍼스", "학사공지", "학사 공지", "교내 프로그램");
        boolean practicalStudentTips = containsAny(normalized,
                "에브리타임", "캠퍼스픽", "한국장학재단", "강의실", "실습실", "과사", "기숙사", "스펙", "학점");
        return freshmanContext || practicalStudentTips;
    }

    private boolean isLanguageLearningContent(String normalized) {
        return containsAny(normalized,
                "영어표현", "영어 표현", "미드영어", "회화", "표현", "단어", "문장", "영어 공부", "영어");
    }

    private boolean isTravelPhraseContent(String normalized) {
        return containsAny(normalized, "must-know phrases", "phrases when traveling", "travel phrases", "여행 필수 표현");
    }

    private boolean isTutorialContent(String normalized) {
        return !containsAny(normalized,
                "workout", "fitness", "run", "exercise", "training",
                "travel guide", "itinerary", "festival", "festa", "fireworks", "busker",
                "벚꽃", "축제", "워터밤", "라인업")
                && containsAny(normalized,
                "tutorial", "guide", "beginners", "beginner", "full course", "using promises", "what is javascript",
                "javascript guide", "camera basics", "night mode", "portrait mode", "css grid", "how to use", "basics");
    }

    private boolean isTravelGuideContent(String normalized) {
        return containsAny(normalized,
                "travel guide", "itinerary", "how to spend 14 days", "ultimate machu picchu", "iceland travel guide",
                "things to do", "where to stay", "must visit", "travel itinerary", "trip guide", "destination guide",
                "여행 가이드", "여행지 추천", "국내 여행", "해외여행", "여름휴가", "숙소 추천", "렌트카 여행", "피서지",
                "관광", "관광상품화", "효도 여행", "후쿠오카 여행", "휴가 여행지");
    }

    private boolean isTravelFestivalHeadline(String normalized) {
        return containsAny(normalized,
                "축제", "festival", "여행 가이드", "여행지 추천", "국내 여행", "해외여행", "효도 여행",
                "휴가 여행지", "피서지", "숙소 추천", "관광", "관광상품화", "홍보 캠페인", "방문의 해",
                "입장료", "여름 축제", "가을 축제", "춘천 막국수 닭갈비 축제", "청보리밭 축제", "슬로우걷기 축제");
    }

    private boolean isExplainerContent(String normalized) {
        return !containsAny(normalized, "recipe", "festival", "travel guide", "workout", "exercise", "template")
                && containsAny(normalized,
                "what is", "how does", "explained", "explainer", "beginner's guide", "beginners guide",
                "easy-to-understand", "in easy-to-understand terms", "guide to");
    }

    private boolean isSocialDesignContent(String normalized) {
        return containsAny(normalized,
                "instagram post creator", "instagram story templates", "instagram story ideas", "story covers",
                "instagram planner", "feed aesthetic", "design wiki", "template");
    }

    private boolean isMarketingGuideContent(String normalized) {
        return containsAny(normalized,
                "email marketing", "newsletter marketing", "instagram tips", "brands driving meaningful change",
                "productivity tips", "social listening");
    }

    private boolean isProductReviewContent(String normalized) {
        return containsAny(normalized,
                "best monitors", "best qled tvs", "the 6 best", "the 4 best");
    }

    private boolean isFestivalContent(String normalized) {
        return containsAny(normalized,
                "축제", "festival", "festa", "워터밤", "벚꽃", "개화", "라인업", "빛초롱", "페스타", "매화", "잠수교",
                "fireworks", "busker", "friendship festival", "gugak", "film festival",
                "fair", "expo", "show", "illustration fair", "art fair", "dance festival", "beer festival");
    }

    private boolean isFoodDiscoveryContent(String normalized) {
        return containsAny(normalized,
                "맛집", "식당", "카페", "콩물국수", "국수", "맛집 추천", "top3", "top 3");
    }

    private boolean isFoodIngredientContent(String normalized) {
        return containsAny(normalized,
                "식재료", "먹는 식재료", "닭발", "떡볶이", "한국에서만 안 먹는");
    }

    private boolean isCreatorContent(String normalized) {
        return containsAny(normalized,
                "채널 운영", "유튜브 채널", "운영 이유", "콘텐츠를 운영", "구독자", "채널을 운영", "운영하고 있는 이유");
    }

    private boolean isMusicCultureContent(String normalized) {
        return containsAny(normalized,
                "래퍼", "노래", "음악", "가수", "아티스트", "플레이리스트", "top3 래퍼");
    }

    private boolean isTravelEventPlatform(String domain) {
        return containsAny(domain,
                "visitkorea.or.kr", "visitseoul.net", "visitbusan.net", "visitjeju.net", "travelandleisure.com");
    }

    private boolean isFitnessPlatform(String domain) {
        return containsAny(domain,
                "acefitness.org", "fitnessblender.com");
    }

    private boolean isRecipePlatform(String domain) {
        return containsAny(domain,
                "bbcgoodfood.com", "bonappetit.com", "allrecipes.com", "foodnetwork.com", "10000recipe.com");
    }

    private boolean isEventDocumentPlatform(String domain) {
        return containsAny(domain,
                "bomnae.chuncheon.go.kr",
                "culture.seoul.go.kr",
                "inu.ac.kr",
                "media-cdn.linkareer.com",
                "summer-morning-rain.tistory.com",
                "welcome77.tistory.com",
                "dealsfor.me",
                "dongtuni.com",
                "g2b.go.kr",
                "scfmc.or.kr");
    }

    private boolean isTravelEventDocument(String domain, String url, String normalized, String titleNormalized) {
        boolean documentLike = containsAny(url, ".pdf", "download.do", "filedown.do", "fileupload.do", "upload/webzine", "upload/board/files")
                || containsAny(domain, "v.daum.net", "g2b.go.kr", "dongtuni.com", "scfmc.or.kr", "inu.ac.kr", "culture.seoul.go.kr");
        boolean travelOrFestivalSignal = containsAny(titleNormalized + " " + normalized + " " + url,
                "festival", "event", "travel", "tour", "guide", "campaign", "promotion",
                "축제", "여행", "관광", "홍보", "캠페인", "소식지", "공모전", "방문의 해", "일정", "입장료",
                "인천9경", "gangwon", "chugje", "gaideu");
        return documentLike && travelOrFestivalSignal;
    }

    private boolean isExplainerPlatform(String domain) {
        return containsAny(domain,
                "healthline.com", "britannica.com");
    }

    private boolean isAppleSupportPlatform(String domain) {
        return containsAny(domain, "support.apple.com");
    }

    private String normalizeDomain(String domain) {
        return domain == null ? "" : domain.toLowerCase(Locale.ROOT).trim();
    }

    private List<String> extractEventIdeas(ActionCardGenerationSource source) {
        String corpus = sanitize(source.scrapedText());
        Matcher matcher = EVENT_ENTRY_PATTERN.matcher(corpus);
        List<String> items = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1).replaceAll("\\s+", " ").trim();
            String date = matcher.group(2).replaceAll("\\s+", " ").trim();
            if (name.isBlank()) {
                continue;
            }
            String item = date.isBlank() ? name : name + " " + date;
            items.add(item.trim());
            if (items.size() >= 4) {
                break;
            }
        }
        return limitIdeas(items);
    }

    private String extractDateText(ActionCardGenerationSource source) {
        String corpus = sanitize(source.scrapedText()) + " " + sanitize(source.scrapedDescription());
        Matcher matcher = DATE_PATTERN.matcher(corpus);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private String buildEventActionTitle(String normalized, String dueDateText) {
        if (containsAny(normalized, "전시", "전시회", "미술관")) {
            return dueDateText == null ? "이번 주 전시 1곳 방문 계획 세우기" : dueDateText + " 전까지 전시 1곳 일정 잡기";
        }
        if (containsAny(normalized, "팝업", "스토어")) {
            return dueDateText == null ? "이번 주 팝업 1곳 방문 계획 세우기" : dueDateText + " 전까지 팝업 방문 일정 정하기";
        }
        return dueDateText == null ? "관심 행사 1개 일정 정하기" : dueDateText + " 전까지 행사 일정 정하기";
    }

    private String buildEventActionDetail(String normalized, String dueDateText) {
        if (containsAny(normalized, "전시", "전시회", "미술관")) {
            return dueDateText == null
                    ? "오늘 링크에 나온 전시 중 가장 끌리는 1곳을 골라 방문 날짜를 정해보세요."
                    : "링크에 나온 전시 중 1곳을 골라 " + dueDateText + " 전에 갈 날짜를 정해보세요.";
        }
        if (containsAny(normalized, "팝업", "스토어")) {
            return dueDateText == null
                    ? "링크에 나온 팝업 중 1곳을 골라 이번 주에 갈 시간을 정해보세요."
                    : "링크에 나온 팝업 중 1곳을 골라 " + dueDateText + " 전에 갈 시간을 정해보세요.";
        }
        return "저장한 행사 링크를 그냥 두지 말고 실제로 갈 날짜나 결정 시점을 정해보세요.";
    }

    private String buildFestivalActionTitle(String normalized, String dueDateText) {
        if (containsAny(normalized, "벚꽃", "매화", "개화")) {
            return dueDateText == null ? "이번 주 꽃축제 일정 정하기" : dueDateText + " 전에 꽃축제 일정 정하기";
        }
        if (containsAny(normalized, "워터밤", "라인업")) {
            return dueDateText == null ? "워터밤 일정 1개 정하기" : dueDateText + " 전에 워터밤 일정 정하기";
        }
        return dueDateText == null ? "관심 축제 1개 일정 정하기" : dueDateText + " 전에 축제 일정 정하기";
    }

    private String buildFestivalActionDetail(String normalized, String dueDateText) {
        if (containsAny(normalized, "벚꽃", "매화", "개화")) {
            return dueDateText == null
                    ? "개화 시기와 축제 정보를 보고 가장 가고 싶은 곳 1곳의 방문 날짜를 정해보세요."
                    : dueDateText + " 전에 가장 가고 싶은 꽃축제 1곳의 날짜를 정해보세요.";
        }
        if (containsAny(normalized, "워터밤", "라인업")) {
            return dueDateText == null
                    ? "라인업과 개최 도시를 보고 가장 가고 싶은 일정 1개를 골라보세요."
                    : dueDateText + " 전에 가장 가고 싶은 워터밤 일정 1개를 골라보세요.";
        }
        return "축제 정보를 저장만 하지 말고 실제로 갈 일정 하나를 정해보세요.";
    }

    private String buildEventDetailBody(List<String> eventIdeas, String dueDateText) {
        if (!eventIdeas.isEmpty()) {
            return "행사형 콘텐츠는 저장보다 일정화가 중요해요. 아래 후보 중 하나를 골라 "
                    + (dueDateText == null ? "이번 주 안에" : dueDateText + " 전에")
                    + " 갈 날짜를 정해보세요.";
        }
        return "행사 링크는 나중에 다시 보려고만 하면 놓치기 쉬워요. 기한과 장소를 먼저 확인하고, 실제 방문 가능한 날짜를 지금 정해두세요.";
    }

    private List<PracticeCardSection> buildRecipeSections(ActionCardGenerationSource source, List<String> orderedSteps) {
        List<String> ingredients = extractRecipeIngredients(source);
        String time = extractRecipeTime(source);
        List<PracticeCardSection> sections = new ArrayList<>();
        if (!ingredients.isEmpty()) {
            sections.add(new PracticeCardSection("준비 재료", null, ingredients));
        }
        if (!orderedSteps.isEmpty()) {
            sections.add(new PracticeCardSection("조리 순서", null, limitIdeas(orderedSteps)));
        }
        if (time != null) {
            sections.add(new PracticeCardSection("예상 시간", time, List.of()));
        }
        return sections;
    }

    private List<String> extractRecipeIngredients(ActionCardGenerationSource source) {
        String corpus = sanitize(source.scrapedText());
        List<String> ingredients = new ArrayList<>();
        for (String token : corpus.split("[,\\n]")) {
            String trimmed = token.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.contains("리뷰별점") || trimmed.contains("포토 후기")) {
                continue;
            }
            if (trimmed.matches(".*(두부|고기|양파|대파|마늘|된장|간장|소금|후추|고추가루|고춧가루|두반장).*")) {
                ingredients.add(trimmed);
            }
            if (ingredients.size() >= 5) {
                break;
            }
        }
        return ingredients;
    }

    private String extractRecipeTime(ActionCardGenerationSource source) {
        String corpus = sanitize(source.scrapedText()) + " " + sanitize(source.scrapedDescription());
        Matcher matcher = Pattern.compile("(\\d+\\s*(?:분|minute|min))").matcher(corpus);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
