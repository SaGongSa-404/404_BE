package com.fourohfour.backend.modules.content.application;

import com.fourohfour.backend.modules.content.domain.ActionCardCategory;
import com.fourohfour.backend.modules.practice.domain.EnergyLevel;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionCardSpecificityPolicyTest {

    private final ActionCardSpecificityPolicy policy = new ActionCardSpecificityPolicy();

    @Test
    void rejectsGenericGoalCardWhenSourceIsExerciseMotivation() {
        ActionCardGenerationSource source = new ActionCardGenerationSource(
                "https://youtube.com/watch?v=1",
                "youtube.com",
                "BEST 동기부여 영상 모음 | 운동자극 영상",
                null,
                List.of(),
                "BEST 동기부여 영상 모음 | 운동자극 영상",
                "운동 시작을 자극하는 영상",
                "운동을 미루지 말고 몸을 움직이라는 메시지",
                "youtube",
                null,
                "YouTube",
                List.of()
        );

        GeneratedPracticeCard genericCard = new GeneratedPracticeCard(
                ActionCardCategory.PRODUCTIVITY,
                "오늘의 목표 3가지 설정하기",
                "오늘 하루 동안 달성하고 싶은 구체적인 목표 3가지를 적고 시작해보세요.",
                "목표 카드 설명",
                "상세 설명",
                List.of(),
                List.of("아이디어 1"),
                "작게 시작해보세요.",
                "생산성을 높이는 기본 카드예요.",
                5,
                EnergyLevel.LOW,
                LocalDate.of(2026, 3, 21)
        );

        assertThat(policy.isSpecificEnough(source, genericCard)).isFalse();
    }

    @Test
    void acceptsFitnessCardWhenSourceIsExerciseMotivation() {
        ActionCardGenerationSource source = new ActionCardGenerationSource(
                "https://youtube.com/watch?v=2",
                "youtube.com",
                "BEST 동기부여 영상 모음 | 운동자극 영상",
                null,
                List.of(),
                "BEST 동기부여 영상 모음 | 운동자극 영상",
                "운동 시작을 자극하는 영상",
                "운동화를 신고 10분이라도 움직이라고 말한다",
                "youtube",
                null,
                "YouTube",
                List.of()
        );

        GeneratedPracticeCard fitnessCard = new GeneratedPracticeCard(
                ActionCardCategory.FITNESS,
                "오늘 10분만 몸 움직이기",
                "운동화를 신고 산책이나 스트레칭을 10분만 해보세요.",
                "운동 카드 설명",
                "상세 설명",
                List.of(),
                List.of("아이디어 1"),
                "몸을 한번 움직이면 흐름이 붙어요.",
                "운동 자극 콘텐츠를 바로 행동으로 옮기는 카드예요.",
                10,
                EnergyLevel.MEDIUM,
                LocalDate.of(2026, 3, 21)
        );

        assertThat(policy.isSpecificEnough(source, fitnessCard)).isTrue();
    }

    @Test
    void rejectsMindfulnessCardWhenSourceIsAboutMorningPagesWriting() {
        ActionCardGenerationSource source = new ActionCardGenerationSource(
                "https://www.instagram.com/reel/example",
                "instagram.com",
                "내가 진짜 원하는 걸 아는 방법",
                null,
                List.of(),
                "모닝페이지 소개 릴스",
                "모닝페이지로 불안과 원하는 것을 정리하는 방법",
                "복잡한 머릿속과 불안을 글쓰기로 정리하고 원하는 것을 언어화하는 방법을 소개한다.",
                "instagram",
                "시머 에세이툰",
                "Instagram",
                List.of()
        );

        GeneratedPracticeCard genericMindfulnessCard = new GeneratedPracticeCard(
                ActionCardCategory.MINDFULNESS,
                "호흡에 3분만 집중해보기",
                "앉은 자리에서 3분만 천천히 숨을 세며 들이쉬고 내쉬어보세요.",
                "마음관리 카드를 더 깊게 실천하는 방법",
                "짧게 멈추고 몸의 감각을 바라보세요.",
                List.of(),
                List.of("호흡 10번 세기"),
                "짧은 멈춤이 감정의 속도를 낮춰줘요.",
                "오늘 바로 체감 가능한 안정 행동으로 정리했어요.",
                3,
                EnergyLevel.LOW,
                LocalDate.of(2026, 3, 21)
        );

        assertThat(policy.isSpecificEnough(source, genericMindfulnessCard)).isFalse();
    }

    @Test
    void rejectsGenericLearningCardWhenSourceIsFreshmanCampusTips() {
        ActionCardGenerationSource source = new ActionCardGenerationSource(
                "https://blog.naver.com/example",
                "blog.naver.com",
                "24학번 새내기 신입생에게만 알려주는 대학 생활 꿀팁 공유",
                null,
                List.of(),
                "24학번 새내기 신입생에게만 알려주는 대학 생활 꿀팁 공유",
                "캠퍼스 파악, 학사 공지사항, 앱 활용법 안내",
                "새내기에게 필요한 캠퍼스 적응 팁, 학사 공지 확인, 에브리타임 활용법을 소개한다.",
                "naver-blog",
                "경기과학기술대학교",
                "네이버 블로그",
                List.of()
        );

        GeneratedPracticeCard genericLearningCard = new GeneratedPracticeCard(
                ActionCardCategory.LEARNING,
                "핵심 한 문장을 내 말로 정리하기",
                "오늘 저장한 내용에서 가장 남는 포인트 1개를 골라, 내 상황에 맞는 한 문장으로 다시 써보세요.",
                "배운 내용을 실천으로 옮기는 방법",
                "학습 콘텐츠는 정리 없이 넘기면 금방 사라져요.",
                List.of(),
                List.of("가장 기억나는 문장을 내 말로 다시 쓰기"),
                "배움은 소비보다 재정리에서 오래 남아요.",
                "지식 소비에서 내 지식으로 바꾸는 카드예요.",
                8,
                EnergyLevel.LOW,
                LocalDate.of(2026, 3, 21)
        );

        assertThat(policy.isSpecificEnough(source, genericLearningCard)).isFalse();
    }

    @Test
    void rejectsNonActionLikeTitleForPopupSource() {
        ActionCardGenerationSource source = new ActionCardGenerationSource(
                "https://www.instagram.com/p/example",
                "instagram.com",
                "3월 부산 팝업 모음집",
                null,
                List.of(),
                "3월 부산 팝업 모음집",
                "부산 팝업스토어 일정 소개",
                "부산에서 열리는 팝업스토어와 일정, 방문 정보를 소개한다.",
                "instagram",
                "컴바인",
                "Instagram",
                List.of()
        );

        GeneratedPracticeCard copiedHeadlineCard = new GeneratedPracticeCard(
                ActionCardCategory.RELATIONSHIP,
                "3월 부산 팝업 모음집",
                "부산 팝업스토어 일정을 소개합니다.",
                "상세",
                "상세",
                List.of(),
                List.of(),
                "가보세요.",
                "팝업 정보예요.",
                10,
                EnergyLevel.LOW,
                LocalDate.of(2026, 3, 21)
        );

        assertThat(policy.isSpecificEnough(source, copiedHeadlineCard)).isFalse();
    }

    @Test
    void rejectsWrongCategoryForRecipeSource() {
        ActionCardGenerationSource source = new ActionCardGenerationSource(
                "https://m.10000recipe.com/recipe/6881454",
                "10000recipe.com",
                "마파두부 레시피",
                null,
                List.of(),
                "두반장이 필요없다! 쉽고 맛있는 마파두부",
                "마파두부 레시피 소개",
                "두부와 양념으로 마파두부를 만드는 조리법을 소개한다.",
                "recipe",
                "마이쏭",
                "10000recipe",
                List.of()
        );

        GeneratedPracticeCard wrongCategory = new GeneratedPracticeCard(
                ActionCardCategory.FITNESS,
                "오늘 10분만 몸 움직이기",
                "산책을 10분 해보세요.",
                "상세",
                "상세",
                List.of(),
                List.of(),
                "시작해보세요.",
                "운동 카드예요.",
                10,
                EnergyLevel.LOW,
                LocalDate.of(2026, 3, 21)
        );

        assertThat(policy.isSpecificEnough(source, wrongCategory)).isFalse();
    }

    @Test
    void rejectsWrongCategoryForToolRecommendationSource() {
        ActionCardGenerationSource source = new ActionCardGenerationSource(
                "https://www.youtube.com/shorts/example",
                "youtube.com",
                "대학생 필수 툴 추천",
                null,
                List.of(),
                "대학생 필수 툴 추천",
                "피그마 캡컷 VLLO 추천",
                "전공 상관없이 배워두면 좋은 툴로 피그마와 캡컷을 추천한다.",
                "youtube",
                null,
                "YouTube",
                List.of()
        );

        GeneratedPracticeCard wrongCategory = new GeneratedPracticeCard(
                ActionCardCategory.FITNESS,
                "3가지 툴 추천!",
                "대학생 필수 툴 추천 콘텐츠입니다.",
                "상세",
                "상세",
                List.of(),
                List.of(),
                "좋아요.",
                "툴 추천이에요.",
                10,
                EnergyLevel.LOW,
                LocalDate.of(2026, 3, 21)
        );

        assertThat(policy.isSpecificEnough(source, wrongCategory)).isFalse();
    }
}
