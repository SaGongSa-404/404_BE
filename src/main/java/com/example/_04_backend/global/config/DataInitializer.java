package com.example._04_backend.global.config;

import com.example._04_backend.domain.social.entity.SocialPost;
import com.example._04_backend.domain.social.repository.SocialPostRepository;
import com.example._04_backend.global.common.enums.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SocialPostRepository socialPostRepository;

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    public void run(String... args) {
        socialPostRepository.save(SocialPost.builder()
                .userId(TEST_USER_ID)
                .title("이 신발 살까 말까?")
                .body("나이키 에어맥스 신상인데 너무 예쁜데 가격이... 고민됩니다")
                .price(189000)
                .category(Category.FASHION)
                .imageUrl("https://picsum.photos/seed/shoes/600/900")
                .productUrl("https://example.com/nike-airmax")
                .build());

        socialPostRepository.save(SocialPost.builder()
                .userId(TEST_USER_ID)
                .title("애플워치 울트라2 지금 사도 될까요?")
                .body("다음달에 신제품 나온다는 소문이 있는데...")
                .price(999000)
                .category(Category.ELECTRONICS)
                .imageUrl("https://picsum.photos/seed/watch/600/900")
                .build());

        socialPostRepository.save(SocialPost.builder()
                .userId(TEST_USER_ID)
                .title("편의점 신상 디저트 추천!")
                .body("이거 진짜 맛있어요 근데 매일 사먹으면 한달에 얼마지...")
                .price(3500)
                .category(Category.FOOD)
                .imageUrl("https://picsum.photos/seed/dessert/600/900")
                .build());

        socialPostRepository.save(SocialPost.builder()
                .userId(TEST_USER_ID)
                .title("레고 스타워즈 세트 고민중")
                .body("취미로 레고 모으는데 이번 한정판은 진짜 갖고 싶다")
                .price(320000)
                .category(Category.HOBBY)
                .imageUrl("https://picsum.photos/seed/lego/600/900")
                .productUrl("https://example.com/lego")
                .build());
    }
}
