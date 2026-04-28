package com.example._04_backend.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 개발용 초기 데이터 — 실제 OAuth 로그인 기반으로 전환되어 고정 UUID 사용 불가
 * 필요시 /api/dev/wishes/test 엔드포인트로 테스트 데이터 생성
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    @Override
    public void run(String... args) {
        // no-op
    }
}
