package com.example._04_backend.global.dev;

import com.example._04_backend.domain.user.entity.User;
import com.example._04_backend.domain.user.repository.UserRepository;
import com.example._04_backend.domain.wish.entity.Wish;
import com.example._04_backend.domain.wish.repository.WishRepository;
import com.example._04_backend.global.auth.LoginUser;
import com.example._04_backend.global.common.enums.Category;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 개발/테스트 전용 컨트롤러 — prod 프로파일에서는 비활성화
 */
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Profile("!prod")
public class DevController {

    private final UserRepository userRepository;
    private final WishRepository wishRepository;
    private final EntityManager em;

    /**
     * 로그인된 사용자에게 24시간이 지난 테스트 위시를 생성한다.
     * 반환된 wishId로 구매 숙려 API를 바로 테스트할 수 있다.
     *
     * POST /api/dev/wishes/test
     */
    @PostMapping("/wishes/test")
    @Transactional
    public ResponseEntity<Map<String, String>> createTestWish(
            @AuthenticationPrincipal LoginUser loginUser) {

        User user = userRepository.findById(loginUser.getId())
                .orElseThrow(() -> new RuntimeException("로그인 유저를 찾을 수 없음"));

        Wish wish = Wish.builder()
                .user(user)
                .title("테스트 상품 (에어팟 프로)")
                .price(350000)
                .category(Category.ELECTRONICS)
                .build();
        wishRepository.save(wish);

        // JPA Auditing이 set한 created_at을 25시간 전으로 덮어써서 숙려 조건 통과
        em.createNativeQuery(
                "UPDATE wishes SET created_at = :ts WHERE id = :id")
                .setParameter("ts", LocalDateTime.now().minusHours(25))
                .setParameter("id", wish.getId().toString())
                .executeUpdate();

        return ResponseEntity.ok(Map.of(
                "wishId", wish.getId().toString(),
                "tip", "GET /api/wishes/{wishId}/deliberation 으로 숙려 화면 조회 가능"
        ));
    }
}
