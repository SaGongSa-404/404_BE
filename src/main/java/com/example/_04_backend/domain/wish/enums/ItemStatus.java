package com.example._04_backend.domain.wish.enums;

/**
 * 아이템 상태
 * SAVED    : 저장됨 (구매 결정 전)
 * GO       : 구매 결정 (살게요)
 * STOP     : 절제 결정 (참을게요)
 * DROPPED  : 관심 없어짐
 */
public enum ItemStatus {
    SAVED,
    GO,
    STOP,
    DROPPED
}
