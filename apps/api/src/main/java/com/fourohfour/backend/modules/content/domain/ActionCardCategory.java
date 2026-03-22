package com.fourohfour.backend.modules.content.domain;

public enum ActionCardCategory {
    GRATITUDE("감사"),
    ROUTINE("루틴·습관"),
    FITNESS("운동"),
    RELATIONSHIP("인간관계"),
    COOKING("요리"),
    CLEANING("청소·정리"),
    EVENT("행사·전시"),
    PRODUCTIVITY("집중"),
    MINDFULNESS("마음관리"),
    LEARNING("학습"),
    FINANCE("재무"),
    GENERAL("실천");

    private final String displayName;

    ActionCardCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
