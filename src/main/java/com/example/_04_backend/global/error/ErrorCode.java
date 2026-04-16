package com.example._04_backend.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "요청 파라미터가 유효하지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "해당 유저를 찾을 수 없습니다."),

    // Social
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "해당 게시글을 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "해당 댓글을 찾을 수 없습니다."),

    // Wish
    WISH_NOT_FOUND(HttpStatus.NOT_FOUND, "WISH_NOT_FOUND", "해당 위시를 찾을 수 없습니다."),
    DELIBERATION_NOT_READY(HttpStatus.FORBIDDEN, "DELIBERATION_NOT_READY", "24시간 미경과로 구매 결정이 불가합니다."),
    ALREADY_DECIDED(HttpStatus.CONFLICT, "ALREADY_DECIDED", "이미 결정된 위시입니다."),
    BUDGET_NOT_SET(HttpStatus.FORBIDDEN, "BUDGET_NOT_SET", "예산이 설정되지 않았습니다."),

    // Link
    LINK_PARSE_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "LINK_PARSE_FAILED", "링크 파싱에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
