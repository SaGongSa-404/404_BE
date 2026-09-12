package com.sagongsa.backend.purchase;

import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PurchaseController.class)
public class PurchaseExceptionHandler {
  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> requestError(
      org.springframework.web.server.ResponseStatusException exception) {
    return ResponseEntity.status(exception.getStatusCode())
        .body(
            Map.of(
                "code",
                "PURCHASE_REQUEST_ERROR",
                "message",
                exception.getReason() == null ? "요청을 확인해 주세요." : exception.getReason()));
  }

  @ExceptionHandler(DuplicateKeyException.class)
  public ResponseEntity<Map<String, String>> conflict(DuplicateKeyException exception) {
    return ResponseEntity.status(409)
        .body(
            Map.of(
                "code",
                "PURCHASE_CONFLICT",
                "message",
                "같은 링크의 상품이나 변경 요청이 이미 있어요. 새로고침 후 확인해 주세요."));
  }
}
